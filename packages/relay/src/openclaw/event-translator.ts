import type { OpenClawGatewayEventMessage, OpenClawTranslatedEvent } from "./types";

type JsonRecord = Record<string, unknown>;

function asRecord(value: unknown): JsonRecord | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }

  return value as JsonRecord;
}

function readString(record: JsonRecord, keys: readonly string[]): string | null {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string") {
      return value;
    }
  }

  return null;
}

function readBoolean(record: JsonRecord, keys: readonly string[]): boolean | null {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "boolean") {
      return value;
    }
  }

  return null;
}

function extractText(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }

  if (Array.isArray(value)) {
    return value
      .map((entry) => extractText(entry))
      .filter((entry) => entry.length > 0)
      .join("");
  }

  const record = asRecord(value);
  if (!record) {
    return "";
  }

  const directText = readString(record, ["text", "delta", "message"]);
  if (directText != null) {
    return directText;
  }

  if ("content" in record) {
    return extractText(record.content);
  }

  return "";
}

export function extractOpenClawSessionKey(payload: unknown): string | null {
  const record = asRecord(payload);
  if (!record) {
    return null;
  }

  const direct = readString(record, ["session_key", "sessionKey", "key"]);
  if (direct) {
    return direct;
  }

  const session = asRecord(record.session);
  if (!session) {
    return null;
  }

  return readString(session, ["session_key", "sessionKey", "key"]);
}

function translateTranscriptText(payload: JsonRecord): OpenClawTranslatedEvent {
  const role = readString(payload, ["role", "speaker", "author"]) ?? "assistant";
  const text = extractText(payload.message ?? payload.content ?? payload);
  const isComplete =
    readBoolean(payload, ["complete", "completed", "is_complete", "isComplete", "final"]) ?? false;

  if (role === "user") {
    return {
      type: "user_message",
      payload: { text }
    };
  }

  return {
    type: isComplete ? "assistant_message" : "assistant_delta",
    payload: { text }
  };
}

function translateChatEvent(payload: JsonRecord): OpenClawTranslatedEvent | null {
  const state = readString(payload, ["state"]);
  if (!state) {
    return null;
  }

  if (state === "started") {
    return {
      type: "session_started",
      payload: {}
    };
  }

  if (state === "delta" || state === "thinking") {
    return {
      type: "assistant_delta",
      payload: {
        text: extractText(payload.delta ?? payload.message ?? payload)
      }
    };
  }

  if (state === "tool") {
    return {
      type: "tool_call_started",
      payload: {
        name: readString(payload, ["tool_name", "toolName"]) ?? "unknown",
        input: payload.tool_input ?? payload.toolArgs ?? {}
      }
    };
  }

  if (state === "final") {
    return {
      type: "assistant_message",
      payload: {
        text: extractText(payload.message ?? payload)
      }
    };
  }

  if (state === "error") {
    return {
      type: "session_error",
      payload: {
        error_code: readString(payload, ["error_code", "errorCode"]) ?? "provider_unreachable",
        message: readString(payload, ["message", "error_message", "errorMessage"]) ?? "OpenClaw session failed"
      }
    };
  }

  return null;
}

export function translateOpenClawEvent(
  message: OpenClawGatewayEventMessage
): OpenClawTranslatedEvent | null {
  const payload = asRecord(message.payload);
  if (!payload) {
    return null;
  }

  switch (message.event) {
    case "transcript.text":
      return translateTranscriptText(payload);
    case "tool.start":
      return {
        type: "tool_call_started",
        payload: {
          name: readString(payload, ["name", "tool_name", "toolName"]) ?? "unknown",
          input: payload.input ?? payload.tool_input ?? payload.toolInput ?? {}
        }
      };
    case "tool.end":
      return {
        type: "tool_call_completed",
        payload: {
          name: readString(payload, ["name", "tool_name", "toolName"]) ?? "unknown",
          output: payload.output ?? payload.result ?? null,
          status:
            readString(payload, ["status"]) ??
            ((readBoolean(payload, ["success"]) ?? true) ? "success" : "error")
        }
      };
    case "session.ready":
      return {
        type: "session_started",
        payload: {
          provider_session_id: extractOpenClawSessionKey(payload)
        }
      };
    case "session.complete":
      return {
        type: "session_result",
        payload: {
          result: payload.result ?? payload.summary ?? null,
          stop_reason: readString(payload, ["stop_reason", "stopReason"])
        }
      };
    case "session.error":
      return {
        type: "session_error",
        payload: {
          error_code: readString(payload, ["error_code", "errorCode"]) ?? "provider_unreachable",
          message: readString(payload, ["message", "error_message", "errorMessage"]) ?? "OpenClaw session failed"
        }
      };
    case "message.user":
      return {
        type: "user_message",
        payload: {
          text: extractText(payload.message ?? payload.content ?? payload)
        }
      };
    case "chat":
      return translateChatEvent(payload);
    default:
      return null;
  }
}
