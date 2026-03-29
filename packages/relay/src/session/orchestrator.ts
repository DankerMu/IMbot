import {
  ERROR_CODES,
  VALID_TRANSITIONS,
  type CompanionEventMessage,
  type EventType,
  type Session,
  type ErrorCode,
  type SessionStatus
} from "@imbot/wire";
import { randomUUID } from "node:crypto";

import type { RelayConfig } from "../config";
import type { RelayDatabase } from "../db/init";
import { RelayError } from "../errors";
import { allocateSeq } from "./seq";
import { WsHub } from "../ws/hub";
import { CompanionManager } from "../companion/manager";

type LoggerLike = {
  readonly error: (...args: unknown[]) => void;
  readonly warn: (...args: unknown[]) => void;
};

export type CreateSessionInput = {
  provider?: string;
  host_id?: string;
  cwd?: string;
  prompt?: string;
  model?: string;
  permission_mode?: string;
};

export class SessionOrchestrator {
  constructor(
    private readonly _config: RelayConfig,
    private readonly db: RelayDatabase,
    private readonly hub: WsHub,
    private readonly companionManager: CompanionManager,
    private readonly logger: LoggerLike
  ) {}

  async create(input: CreateSessionInput): Promise<Session> {
    if (!input.provider || !input.host_id || !input.cwd || !input.prompt) {
      throw new RelayError("invalid_request", "provider, host_id, cwd, and prompt are required");
    }

    if (input.provider === "openclaw") {
      throw new RelayError("provider_unreachable", "OpenClaw bridge is not available yet");
    }

    if (input.provider !== "claude" && input.provider !== "book") {
      throw new RelayError("invalid_request", "provider must be claude, book, or openclaw");
    }

    if (!this.companionManager.isOnline(input.host_id)) {
      throw new RelayError("host_offline", `Companion host ${input.host_id} is offline`);
    }

    const provider = input.provider as "claude" | "book";
    const now = new Date().toISOString();
    const sessionId = randomUUID();
    const session: Session = {
      id: sessionId,
      provider,
      provider_session_id: null,
      host_id: input.host_id,
      workspace_root: null,
      workspace_cwd: input.cwd,
      initial_prompt: input.prompt,
      model: input.model ?? null,
      permission_mode: input.permission_mode ?? "bypassPermissions",
      status: "queued",
      error_message: null,
      error_code: null,
      created_at: now,
      updated_at: now,
      last_active_at: now
    };

    this.db
      .prepare(
        `
        INSERT INTO sessions (
          id,
          provider,
          provider_session_id,
          host_id,
          workspace_root,
          workspace_cwd,
          initial_prompt,
          model,
          permission_mode,
          status,
          error_message,
          error_code,
          created_at,
          updated_at,
          last_active_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `
      )
      .run(
        session.id,
        session.provider,
        session.provider_session_id,
        session.host_id,
        session.workspace_root,
        session.workspace_cwd,
        session.initial_prompt,
        session.model,
        session.permission_mode,
        session.status,
        session.error_message,
        session.error_code,
        session.created_at,
        session.updated_at,
        session.last_active_at
      );

    this.insertAuditLog("session.create", {
      session_id: session.id,
      host_id: session.host_id,
      detail: {
        provider: session.provider,
        cwd: session.workspace_cwd
      }
    });

    const command = {
      cmd: "create_session" as const,
      req_id: this.companionManager.createRequestId(),
      provider,
      cwd: session.workspace_cwd,
      prompt: session.initial_prompt ?? "",
      model: session.model ?? undefined,
      permission_mode: session.permission_mode
    };

    try {
      const ack = await this.companionManager.sendCommand(session.host_id, command);

      if (ack.type !== "ack") {
        throw new RelayError("state_conflict", "Unexpected companion acknowledgement");
      }

      if (ack.status === "error") {
        const errorCode = this.normalizeErrorCode(ack.error_code);
        throw new RelayError(errorCode, ack.message);
      }

      const providerSessionId =
        ack.data &&
        typeof ack.data === "object" &&
        "provider_session_id" in ack.data &&
        typeof ack.data.provider_session_id === "string"
          ? ack.data.provider_session_id
          : null;

      this.db
        .prepare(
          `
          UPDATE sessions
          SET provider_session_id = ?, updated_at = ?, last_active_at = ?
          WHERE id = ?
          `
        )
        .run(providerSessionId, now, now, session.id);

      this.insertEvent(session.id, "session_started", {
        provider_session_id: providerSessionId
      });
      await this.transition(session.id, "running");

      return session;
    } catch (error) {
      const relayError =
        error instanceof RelayError ? error : new RelayError("provider_unreachable", "Companion command failed");

      this.insertEvent(session.id, "session_error", {
        error_code: relayError.code,
        message: relayError.message
      });
      await this.transition(session.id, "failed", {
        error_code: relayError.code,
        error_message: relayError.message
      });
      throw relayError;
    }
  }

  async handleEvent(message: CompanionEventMessage): Promise<void> {
    const session = this.getSession(message.session_id);
    if (!session) {
      this.logger.warn(`Dropping event for unknown session ${message.session_id}`);
      return;
    }

    const storedEvent = this.insertEvent(session.id, message.event_type, message.payload);
    this.hub.broadcastToSession(session.id, {
      type: "event",
      session_id: session.id,
      seq: storedEvent.seq,
      event_type: storedEvent.type,
      payload: storedEvent.payload,
      timestamp: storedEvent.created_at
    });

    if (message.event_type === "session_result") {
      await this.transition(session.id, "completed");
      return;
    }

    if (message.event_type === "session_error") {
      const context =
        message.payload && typeof message.payload === "object"
          ? {
              error_code:
                "error_code" in message.payload && typeof message.payload.error_code === "string"
                  ? message.payload.error_code
                  : "provider_unreachable",
              error_message:
                "message" in message.payload && typeof message.payload.message === "string"
                  ? message.payload.message
                  : "Session failed"
            }
          : {
              error_code: "provider_unreachable",
              error_message: "Session failed"
            };

      await this.transition(session.id, "failed", context);
    }
  }

  async transition(
    sessionId: string,
    newStatus: SessionStatus,
    context?: { error_code?: string; error_message?: string }
  ): Promise<void> {
    const session = this.getSession(sessionId);
    if (!session) {
      throw new RelayError("not_found", `Session ${sessionId} does not exist`);
    }

    const allowedTransitions = VALID_TRANSITIONS[session.status];
    if (!allowedTransitions?.includes(newStatus)) {
      throw new RelayError(
        "state_conflict",
        `Cannot transition session ${sessionId} from ${session.status} to ${newStatus}`
      );
    }

    const now = new Date().toISOString();
    const nextErrorCode = newStatus === "failed" ? context?.error_code ?? null : null;
    const nextErrorMessage = newStatus === "failed" ? context?.error_message ?? null : null;

    this.db
      .prepare(
        `
        UPDATE sessions
        SET status = ?, error_code = ?, error_message = ?, updated_at = ?, last_active_at = ?
        WHERE id = ?
        `
      )
      .run(newStatus, nextErrorCode, nextErrorMessage, now, now, sessionId);

    this.insertEvent(sessionId, "session_status_changed", {
      status: newStatus,
      error_code: nextErrorCode,
      error_message: nextErrorMessage
    });

    this.hub.broadcastToSession(sessionId, {
      type: "status",
      session_id: sessionId,
      status: newStatus
    });
  }

  private getSession(sessionId: string): Session | null {
    return (
      (this.db.prepare("SELECT * FROM sessions WHERE id = ?").get(sessionId) as Session | undefined) ?? null
    );
  }

  private insertEvent(sessionId: string, eventType: EventType, payload: unknown) {
    const seq = allocateSeq(this.db, sessionId);
    const createdAt = new Date().toISOString();
    const event = {
      id: randomUUID(),
      session_id: sessionId,
      seq,
      type: eventType,
      payload,
      created_at: createdAt
    };

    this.db
      .prepare(
        `
        INSERT INTO session_events (id, session_id, seq, type, payload, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        `
      )
      .run(event.id, event.session_id, event.seq, event.type, JSON.stringify(event.payload), event.created_at);

    this.db
      .prepare("UPDATE sessions SET last_active_at = ? WHERE id = ?")
      .run(createdAt, sessionId);

    return event;
  }

  private insertAuditLog(
    action: string,
    payload: {
      session_id?: string;
      host_id?: string;
      detail?: unknown;
    }
  ): void {
    this.db
      .prepare(
        `
        INSERT INTO audit_logs (id, action, session_id, host_id, detail, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        `
      )
      .run(
        randomUUID(),
        action,
        payload.session_id ?? null,
        payload.host_id ?? null,
        payload.detail ? JSON.stringify(payload.detail) : null,
        new Date().toISOString()
      );
  }

  private normalizeErrorCode(errorCode: string | undefined): ErrorCode {
    if (errorCode && ERROR_CODES.includes(errorCode as ErrorCode)) {
      return errorCode as ErrorCode;
    }

    return "provider_unreachable";
  }
}
