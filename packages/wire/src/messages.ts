import type { ErrorCode, EventType, SessionStatus } from "./enums";

export type CompanionEventSource = "runtime" | "transcript_sync";

export type CompanionAckOk = {
  type: "ack";
  req_id: string;
  status: "ok";
  data?: unknown;
};

export type CompanionAckError = {
  type: "ack";
  req_id: string;
  status: "error";
  error_code: ErrorCode | string;
  message: string;
};

export type CompanionEventMessage = {
  type: "event";
  session_id: string;
  event_type: EventType;
  payload: unknown;
  source?: CompanionEventSource;
};

export type CompanionHeartbeatMessage = {
  type: "heartbeat";
  host_id: string;
  providers: string[];
  uptime: number;
};

export type CompanionReportLocalSessionsMessage = {
  type: "report_local_sessions";
  req_id?: string;
  host_id: string;
  sessions: Array<{
    provider_session_id: string;
    provider: "claude" | "book";
    cwd: string;
    created_at: string;
    last_active_at: string;
  }>;
};

export type CompanionMessage =
  | CompanionAckOk
  | CompanionAckError
  | CompanionEventMessage
  | CompanionHeartbeatMessage
  | CompanionReportLocalSessionsMessage;

export type ServerMessage =
  | {
      type: "event";
      session_id: string;
      seq: number;
      event_type: EventType;
      payload: unknown;
      timestamp: string;
    }
  | { type: "status"; session_id: string; status: SessionStatus }
  | { type: "host_status"; host_id: string; status: "online" | "offline" }
  | { type: "sessions_changed"; reason: "local_sync"; host_id?: string }
  | { type: "error"; code: ErrorCode | string; message: string }
  | { type: "pong" };

export type ClientMessage =
  | { action: "auth"; token: string }
  | { action: "subscribe"; session_id: string }
  | { action: "unsubscribe"; session_id: string }
  | { action: "ping" };
