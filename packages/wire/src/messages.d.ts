import type { ErrorCode, EventType, SessionStatus } from "./enums";
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
};
export type CompanionHeartbeatMessage = {
    type: "heartbeat";
    host_id: string;
    providers: string[];
    uptime: number;
};
export type CompanionMessage = CompanionAckOk | CompanionAckError | CompanionEventMessage | CompanionHeartbeatMessage;
export type ServerMessage = {
    type: "event";
    session_id: string;
    seq: number;
    event_type: EventType;
    payload: unknown;
    timestamp: string;
} | {
    type: "status";
    session_id: string;
    status: SessionStatus;
} | {
    type: "host_status";
    host_id: string;
    status: "online" | "offline";
} | {
    type: "error";
    code: ErrorCode | string;
    message: string;
} | {
    type: "pong";
};
export type ClientMessage = {
    action: "auth";
    token: string;
} | {
    action: "subscribe";
    session_id: string;
} | {
    action: "unsubscribe";
    session_id: string;
} | {
    action: "ping";
};
