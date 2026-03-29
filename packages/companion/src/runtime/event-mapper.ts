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
    const text = getString(record.text) ?? getString(record.message) ?? getString(record.content);
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
    const text = getString(record.text) ?? getString(record.message) ?? getString(record.content);
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
    const text = getString(record.text) ?? getString(record.message);
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

  return null;
}

function getString(value: unknown): string | null {
  return typeof value === "string" && value !== "" ? value : null;
}
