import { randomUUID } from "node:crypto";
import type { EventType } from "@imbot/wire";

export type RuntimeMappedMessage =
  | {
      readonly kind: "provider_session_id";
      readonly providerSessionId: string;
      readonly model?: string;
    }
  | {
      readonly kind: "event";
      readonly eventType: EventType;
      readonly payload: unknown;
    };

const SUPPRESSED_USER_MSG_TOOLS = new Set(["skill"]);

function isLower(name: string | null, set: Set<string>): boolean {
  return name != null && set.has(name.toLowerCase());
}

/**
 * Stateful mapper for Claude Code stream-json output.
 *
 * State:
 *  - `emittedToolIds`: deduplicates tool_call_started (verbose mode sends
 *    partial + final messages with the same tool_use block).
 *  - `suppressUserMessageCount`: after a Skill tool_use, Claude Code injects
 *    the expanded skill prompt as a plain user message — suppress it.
 */
export class RuntimeEventMapper {
  /** Maps tool call id → lowercase tool name for dedup. */
  private readonly emittedTools = new Map<string, string>();
  private suppressUserMessageCount = 0;

  markToolEmitted(callId: string, toolName: string): void {
    this.emittedTools.set(callId, toolName.toLowerCase());
  }

  map(raw: unknown): RuntimeMappedMessage | null {
    if (raw == null || typeof raw !== "object" || Array.isArray(raw)) {
      return null;
    }

    const record = raw as Record<string, unknown>;
    const type = typeof record.type === "string" ? record.type : null;

    if (!type) {
      return null;
    }

    // ── provider session id ──────────────────────────────────────────
    if (type === "system" && typeof record.session_id === "string") {
      const model = getString(record.model);
      return {
        kind: "provider_session_id",
        providerSessionId: record.session_id,
        ...(model ? { model } : {})
      };
    }

    // ── assistant (stream-json content blocks) ───────────────────────
    if (type === "assistant") {
      const toolUse = extractContentToolUse(record);
      if (toolUse) {
        return this.emitToolCallStarted(
          getString(toolUse.id),
          getString(toolUse.name),
          toolUse.input
        );
      }

      const text = extractEventText(record);
      if (!text) {
        return null;
      }

      const subtype = getString(record.subtype);
      return {
        kind: "event",
        eventType: subtype === "message" || subtype === "complete" ? "assistant_message" : "assistant_delta",
        payload: { text }
      };
    }

    if (type === "assistant_message") {
      const text = extractEventText(record);
      if (!text) {
        return null;
      }
      return { kind: "event", eventType: "assistant_message", payload: { text } };
    }

    // ── top-level tool_use (non-stream-json compat) ──────────────────
    if (type === "tool_use") {
      return this.emitToolCallStarted(
        getString(record.id) ?? getString(record.tool_use_id),
        getString(record.tool) ?? getString(record.name),
        record.input
      );
    }

    // ── top-level tool_result (non-stream-json compat) ───────────────
    if (type === "tool_result") {
      return this.emitToolCallCompleted(
        getString(record.id) ?? getString(record.tool_use_id),
        getString(record.tool) ?? getString(record.name),
        record.result ?? record.output ?? null
      );
    }

    // ── result / error ───────────────────────────────────────────────
    if (type === "result") {
      return {
        kind: "event",
        eventType: "session_result",
        payload: { result: record.result ?? record.output ?? null }
      };
    }

    if (type === "error") {
      return {
        kind: "event",
        eventType: "session_error",
        payload: {
          error_code: getString(record.error_code) ?? "provider_unreachable",
          message: getString(record.message) ?? "Runtime error"
        }
      };
    }

    // ── user / user_message ──────────────────────────────────────────
    if (type === "user" || type === "user_message") {
      const toolResult = extractContentToolResult(record);
      if (toolResult) {
        return this.emitToolCallCompleted(
          getString(toolResult.tool_use_id),
          getString(toolResult.tool_name),
          toolResult.content ?? null
        );
      }

      if (this.suppressUserMessageCount > 0) {
        this.suppressUserMessageCount--;
        return null;
      }

      const text = extractEventText(record);
      if (!text) {
        return null;
      }
      return { kind: "event", eventType: "user_message", payload: { text } };
    }

    // ── approval (passthrough) ───────────────────────────────────────
    if (type === "approval_required" || type === "approval_resolved") {
      const { type: _ignoredType, ...payload } = record;
      return { kind: "event", eventType: type, payload };
    }

    return null;
  }

  // ── private helpers ──────────────────────────────────────────────────

  private emitToolCallStarted(
    rawId: string | null,
    toolName: string | null,
    input: unknown
  ): RuntimeMappedMessage | null {
    const callId = rawId ?? randomUUID();
    const lowerName = toolName?.toLowerCase() ?? "";

    // Deduplicate: verbose mode sends partial + final messages
    if (this.emittedTools.has(callId)) {
      return null;
    }
    this.emittedTools.set(callId, lowerName);

    // Skill tool → suppress the injected skill-prompt user message that follows
    if (isLower(toolName, SUPPRESSED_USER_MSG_TOOLS)) {
      this.suppressUserMessageCount++;
    }

    return {
      kind: "event",
      eventType: "tool_call_started",
      payload: {
        call_id: callId,
        tool: toolName,
        input: input ?? null
      }
    };
  }

  private emitToolCallCompleted(
    rawId: string | null,
    toolName: string | null,
    result: unknown
  ): RuntimeMappedMessage | null {
    const callId = rawId ?? randomUUID();
    return {
      kind: "event",
      eventType: "tool_call_completed",
      payload: {
        call_id: callId,
        tool: toolName,
        result
      }
    };
  }
}

/** @deprecated Use `new RuntimeEventMapper().map(raw)` for stateful mapping. */
export function mapRuntimeEvent(raw: unknown): RuntimeMappedMessage | null {
  return new RuntimeEventMapper().map(raw);
}

// ── pure helpers ────────────────────────────────────────────────────────

function getString(value: unknown): string | null {
  return typeof value === "string" && value !== "" ? value : null;
}

function extractEventText(record: Record<string, unknown>): string | null {
  return (
    getString(record.text) ??
    getString(record.message) ??
    getString(record.content) ??
    extractStructuredMessageText(record.message) ??
    extractStructuredContentText(record.content)
  );
}

interface ContentToolUse {
  readonly id: unknown;
  readonly name: unknown;
  readonly input: unknown;
}

function extractContentToolUse(record: Record<string, unknown>): ContentToolUse | null {
  const content = getContentArray(record);
  if (!content) {
    return null;
  }

  for (const item of content) {
    if (isPlainObject(item) && (item as Record<string, unknown>).type === "tool_use") {
      const block = item as Record<string, unknown>;
      return { id: block.id, name: block.name, input: block.input };
    }
  }
  return null;
}

interface ContentToolResult {
  readonly tool_use_id: unknown;
  readonly tool_name: unknown;
  readonly content: unknown;
}

function extractContentToolResult(record: Record<string, unknown>): ContentToolResult | null {
  const content = getContentArray(record);
  if (!content) {
    return null;
  }

  for (const item of content) {
    if (isPlainObject(item) && (item as Record<string, unknown>).type === "tool_result") {
      const block = item as Record<string, unknown>;
      return { tool_use_id: block.tool_use_id, tool_name: block.tool_name, content: block.content };
    }
  }
  return null;
}

function getContentArray(record: Record<string, unknown>): unknown[] | null {
  const message = record.message;
  const content = Array.isArray(message)
    ? message
    : isPlainObject(message)
      ? (message as Record<string, unknown>).content
      : record.content;
  return Array.isArray(content) ? content : null;
}

function isPlainObject(value: unknown): boolean {
  return value != null && typeof value === "object" && !Array.isArray(value);
}

function extractStructuredMessageText(value: unknown): string | null {
  if (!isPlainObject(value)) {
    return null;
  }
  return extractStructuredContentText((value as Record<string, unknown>).content);
}

function extractStructuredContentText(value: unknown): string | null {
  if (!Array.isArray(value)) {
    return null;
  }

  const text = value
    .flatMap((item) => {
      if (!isPlainObject(item)) {
        return [];
      }
      const record = item as Record<string, unknown>;
      if (record.type !== "text") {
        return [];
      }
      const chunk = getString(record.text);
      return chunk ? [chunk] : [];
    })
    .join("");

  return text !== "" ? text : null;
}
