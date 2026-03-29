"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ERROR_HTTP_STATUS = exports.ERROR_CODES = exports.PROVIDERS = exports.VALID_TRANSITIONS = exports.SESSION_STATUSES = exports.EVENT_TYPES = void 0;
exports.EVENT_TYPES = [
    "session_started",
    "assistant_delta",
    "assistant_message",
    "tool_call_started",
    "tool_call_completed",
    "approval_required",
    "approval_resolved",
    "session_status_changed",
    "session_result",
    "session_error",
    "user_message"
];
exports.SESSION_STATUSES = [
    "queued",
    "running",
    "completed",
    "failed",
    "cancelled"
];
exports.VALID_TRANSITIONS = {
    queued: ["running", "failed"],
    running: ["completed", "failed", "cancelled"],
    completed: ["running"],
    failed: ["running"],
    cancelled: []
};
exports.PROVIDERS = ["claude", "book", "openclaw"];
exports.ERROR_CODES = [
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
    "seq_gap_detected"
];
exports.ERROR_HTTP_STATUS = {
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
    seq_gap_detected: 500
};
