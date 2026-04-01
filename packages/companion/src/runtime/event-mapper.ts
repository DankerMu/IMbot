import type { EventType } from "@imbot/wire";

export type RuntimeMappedMessage =
  | {
      readonly kind: "provider_session_id";
      readonly providerSessionId: string;
    }
  | {
      readonly kind: "event";
      readonly eventType: EventType;
      readonly payload: unknown;
    };

export function mapRuntimeEvent(raw: unknown): RuntimeMappedMessage | null {
  if (raw == null || typeof raw !== "object" || Array.isArray(raw)) {
    return null;
  }

  const record = raw as Record<string, unknown>;
  const type = typeof record.type === "string" ? record.type : null;

  if (!type) {
    return null;
  }

  if (type === "system" && typeof record.session_id === "string") {
    return {
      kind: "provider_session_id",
      providerSessionId: record.session_id
    };
  }

  if (type === "assistant") {
    const text = extractEventText(record);
    if (!text) {
      return null;
    }

    const subtype = getString(record.subtype);
    return {
      kind: "event",
      eventType: subtype === "message" || subtype === "complete" ? "assistant_message" : "assistant_delta",
      payload: {
        text
      }
    };
  }

  if (type === "assistant_message") {
    const text = extractEventText(record);
    if (!text) {
      return null;
    }

    return {
      kind: "event",
      eventType: "assistant_message",
      payload: {
        text
      }
    };
  }

  if (type === "tool_use") {
    return {
      kind: "event",
      eventType: "tool_call_started",
      payload: {
        tool: getString(record.tool) ?? getString(record.name),
        input: record.input ?? null
      }
    };
  }

  if (type === "tool_result") {
    return {
      kind: "event",
      eventType: "tool_call_completed",
      payload: {
        tool: getString(record.tool) ?? getString(record.name),
        result: record.result ?? record.output ?? null
      }
    };
  }

  if (type === "result") {
    return {
      kind: "event",
      eventType: "session_result",
      payload: {
        result: record.result ?? record.output ?? null
      }
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

  if (type === "user" || type === "user_message") {
    const text = extractEventText(record);
    if (!text) {
      return null;
    }

    return {
      kind: "event",
      eventType: "user_message",
      payload: {
        text
      }
    };
  }

  if (type === "approval_required" || type === "approval_resolved") {
    const { type: _ignoredType, ...payload } = record;
    return {
      kind: "event",
      eventType: type,
      payload
    };
  }

  return null;
}

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

function extractStructuredMessageText(value: unknown): string | null {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }

  const record = value as Record<string, unknown>;
  return extractStructuredContentText(record.content);
}

function extractStructuredContentText(value: unknown): string | null {
  if (!Array.isArray(value)) {
    return null;
  }

  const text = value
    .flatMap((item) => {
      if (item == null || typeof item !== "object" || Array.isArray(item)) {
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
