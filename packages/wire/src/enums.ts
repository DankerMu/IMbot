export const EVENT_TYPES = [
  "session_started",
  "assistant_delta",
  "assistant_message",
  "tool_call_started",
  "tool_call_completed",
  // Reserved for future approval UI. The backend should preserve and forward them even while
  // the default product path remains bypassPermissions and Android renders them generically.
  "approval_required",
  "approval_resolved",
  "session_status_changed",
  "session_result",
  "session_idle",
  "session_error",
  "user_message"
] as const;

export type EventType = (typeof EVENT_TYPES)[number];

export const SESSION_STATUSES = [
  "queued",
  "running",
  "idle",
  "completed",
  "failed",
  "cancelled"
] as const;

export type SessionStatus = (typeof SESSION_STATUSES)[number];

export const VALID_TRANSITIONS: Readonly<Record<SessionStatus, readonly SessionStatus[]>> = {
  queued: ["running", "failed"],
  running: ["idle", "completed", "failed", "cancelled"],
  idle: ["running", "completed", "failed", "cancelled"],
  completed: ["running"],
  failed: ["running"],
  cancelled: ["running"]
};

export const PROVIDERS = ["claude", "book", "openclaw"] as const;

export type Provider = (typeof PROVIDERS)[number];

export const ERROR_CODES = [
  "unauthenticated",
  "forbidden",
  "not_found",
  "invalid_request",
  "state_conflict",
  "host_offline",
  "provider_unreachable",
  "directory_not_found",
  "session_not_resumable",
  "command_timeout",
  "seq_gap_detected",
  "no_pending_control_request",
  "call_id_mismatch",
  "session_not_found"
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

export const ERROR_HTTP_STATUS: Readonly<Record<ErrorCode, number>> = {
  unauthenticated: 401,
  forbidden: 403,
  not_found: 404,
  invalid_request: 400,
  state_conflict: 409,
  host_offline: 502,
  provider_unreachable: 502,
  directory_not_found: 400,
  session_not_resumable: 409,
  command_timeout: 504,
  seq_gap_detected: 500,
  no_pending_control_request: 409,
  call_id_mismatch: 409,
  session_not_found: 404
};
