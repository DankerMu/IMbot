export function formatEvent(msg: Record<string, unknown>): string | null {
  const eventType = msg.event_type as string;
  const payload = (msg.payload ?? {}) as Record<string, unknown>;

  switch (eventType) {
    case "assistant_delta":
    case "assistant_message": {
      return typeof payload.text === "string" ? payload.text : null;
    }

    case "user_message": {
      const text = typeof payload.text === "string" ? payload.text : null;
      return text ? `\x1b[36m[user]\x1b[0m ${text}` : null;
    }

    case "tool_call_started": {
      const tool = typeof payload.tool === "string" ? payload.tool : "unknown";
      const input = payload.input;
      const inputRecord = input && typeof input === "object" ? (input as Record<string, unknown>) : null;
      const detailValue =
        typeof inputRecord?.file_path === "string"
          ? inputRecord.file_path
          : typeof inputRecord?.file === "string"
            ? inputRecord.file
            : typeof inputRecord?.command === "string"
              ? inputRecord.command
              : null;
      const detail = detailValue ? `: ${detailValue}` : "";
      return `\x1b[33m[tool]\x1b[0m ${tool}${detail}`;
    }

    case "tool_call_completed": {
      const tool = typeof payload.tool === "string" ? payload.tool : "unknown";
      return `\x1b[32m[done]\x1b[0m ${tool}`;
    }

    case "session_status_changed": {
      const status = typeof payload.status === "string" ? payload.status : "?";
      return `\x1b[90m--- ${status} ---\x1b[0m`;
    }

    case "session_idle":
      return "\x1b[34m--- session idle ---\x1b[0m";

    case "session_started":
      return "\x1b[90m--- session started ---\x1b[0m";

    case "session_result":
      return "\x1b[32m--- session complete ---\x1b[0m";

    case "session_error": {
      const message = typeof payload.message === "string" ? payload.message : "unknown error";
      return `\x1b[31m[error]\x1b[0m ${message}`;
    }

    default:
      return null;
  }
}
