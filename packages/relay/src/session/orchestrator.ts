import {
  ERROR_CODES,
  type CompanionEventMessage,
  type EventType,
  type Session,
  type ErrorCode,
  type SessionStatus
} from "@imbot/wire";
import { randomUUID } from "node:crypto";

import { AuditLogger } from "../audit/logger";
import type { RelayConfig } from "../config";
import type { RelayDatabase } from "../db/init";
import { RelayError } from "../errors";
import { allocateSeq } from "./seq";
import { WsHub } from "../ws/hub";
import { CompanionManager } from "../companion/manager";
import { OpenClawBridge } from "../openclaw/bridge";
import { isValidTransition } from "./transitions";

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

type SessionErrorContext = {
  error_code?: string;
  error_message?: string;
};

export class SessionOrchestrator {
  private readonly activeLifecycleMutations = new Set<string>();

  constructor(
    private readonly _config: RelayConfig,
    private readonly db: RelayDatabase,
    private readonly hub: WsHub,
    private readonly companionManager: CompanionManager,
    private readonly openClawBridge: OpenClawBridge,
    private readonly auditLogger: AuditLogger,
    private readonly logger: LoggerLike
  ) {}

  async create(input: CreateSessionInput): Promise<Session> {
    if (!input.provider || !input.host_id || !input.cwd || !input.prompt) {
      throw new RelayError("invalid_request", "provider, host_id, cwd, and prompt are required");
    }

    if (input.provider !== "claude" && input.provider !== "book" && input.provider !== "openclaw") {
      throw new RelayError("invalid_request", "provider must be claude, book, or openclaw");
    }

    if (input.provider !== "openclaw" && !this.companionManager.isOnline(input.host_id)) {
      throw new RelayError("host_offline", `Companion host ${input.host_id} is offline`);
    }

    if (input.provider === "openclaw" && input.host_id !== "relay-local") {
      throw new RelayError("invalid_request", "OpenClaw sessions must target the relay-local host");
    }

    const provider = input.provider as "claude" | "book" | "openclaw";
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

    try {
      const providerSessionId = await this.dispatchCreate(session);
      await this.markSessionStarted(session.id, providerSessionId);
      this.auditLogger.write("session.create", {
        session_id: session.id,
        host_id: session.host_id,
        detail: this.buildCreateAuditDetail(session)
      });

      return this.getSession(session.id) ?? session;
    } catch (error) {
      const relayError =
        error instanceof RelayError ? error : new RelayError("provider_unreachable", "Companion command failed");

      this.insertAndBroadcastEvent(session.id, "session_error", {
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

  async resume(sessionId: string): Promise<Session> {
    return await this.runWithLifecycleLock(sessionId, async () => {
      const session = this.requireSession(sessionId);
      if (session.status !== "completed" && session.status !== "failed") {
        throw new RelayError("state_conflict", `Session ${sessionId} is not resumable from ${session.status}`);
      }

      if (!session.provider_session_id) {
        throw new RelayError("session_not_resumable", `Session ${sessionId} has no provider session id`);
      }

      this.assertProviderAvailable(session);

      const previousStatus = session.status;
      const providerSessionId = await this.dispatchResume(session);
      await this.markSessionStarted(session.id, providerSessionId);
      this.auditLogger.write("session.resume", {
        session_id: session.id,
        host_id: session.host_id,
        detail: {
          previous_status: previousStatus
        }
      });
      return this.requireSession(session.id);
    });
  }

  async sendMessage(sessionId: string, text: string): Promise<void> {
    const session = this.requireSession(sessionId);
    if (session.status !== "running") {
      throw new RelayError("state_conflict", `Session ${sessionId} is not running`);
    }

    this.assertProviderAvailable(session);
    await this.dispatchSendMessage(session, text);
  }

  async cancel(sessionId: string): Promise<Session> {
    return await this.runWithLifecycleLock(sessionId, async () => {
      const session = this.requireSession(sessionId);
      if (session.status !== "running") {
        throw new RelayError("state_conflict", `Session ${sessionId} cannot be cancelled from ${session.status}`);
      }

      if (session.provider !== "openclaw") {
        this.assertProviderAvailable(session);
      }

      const previousStatus = session.status;
      await this.dispatchCancel(session);
      await this.transition(session.id, "cancelled");
      this.auditLogger.write("session.cancel", {
        session_id: session.id,
        host_id: session.host_id,
        detail: {
          previous_status: previousStatus
        }
      });
      return this.requireSession(session.id);
    });
  }

  async delete(sessionId: string): Promise<void> {
    await this.runWithLifecycleLock(sessionId, async () => {
      const session = this.requireSession(sessionId);
      if (session.status === "queued" || session.status === "running") {
        throw new RelayError("state_conflict", `Session ${sessionId} cannot be deleted from ${session.status}`);
      }

      const result = this.db.prepare("DELETE FROM sessions WHERE id = ?").run(sessionId);
      if (result.changes === 0) {
        throw new RelayError("not_found", `Session ${sessionId} not found`);
      }

      this.auditLogger.write("session.delete", {
        session_id: sessionId,
        host_id: session.host_id,
        detail: {
          provider: session.provider,
          status: session.status
        }
      });
    });
  }

  async handleEvent(message: CompanionEventMessage): Promise<void> {
    const session = this.getSession(message.session_id);
    if (!session) {
      this.logger.warn(`Dropping event for unknown session ${message.session_id}`);
      return;
    }

    if (session.status !== "running") {
      this.logger.warn(
        `Dropping ${message.event_type} for non-running session ${message.session_id} (${session.status})`
      );
      return;
    }

    const payload = this.sanitizeIncomingPayload(message.payload);
    if (message.event_type === "session_started") {
      return;
    }

    this.insertAndBroadcastEvent(session.id, message.event_type, payload);

    if (message.event_type === "session_result") {
      await this.transitionWithConflictTolerance(session.id, "completed");
      return;
    }

    if (message.event_type === "session_error") {
      const context =
        payload && typeof payload === "object"
          ? {
              error_code: this.normalizeErrorCode(
                "error_code" in payload && typeof payload.error_code === "string"
                  ? payload.error_code
                  : undefined
              ),
              error_message:
                "message" in payload && typeof payload.message === "string"
                  ? payload.message
                  : "Session failed"
            }
          : {
              error_code: "provider_unreachable",
              error_message: "Session failed"
            };

      await this.transitionWithConflictTolerance(session.id, "failed", context);
    }
  }

  async handleHostDisconnected(hostId: string): Promise<void> {
    const sessions = this.db
      .prepare(
        `
        SELECT *
        FROM sessions
        WHERE host_id = ? AND status = 'running'
        ORDER BY created_at ASC
        `
      )
      .all(hostId) as Session[];

    for (const session of sessions) {
      const context = {
        error_code: "host_disconnected",
        error_message: "Host companion disconnected unexpectedly"
      };

      this.insertAndBroadcastEvent(session.id, "session_error", {
        error_code: context.error_code,
        message: context.error_message
      });
      await this.transitionWithConflictTolerance(session.id, "failed", context);
    }
  }

  async transition(
    sessionId: string,
    newStatus: SessionStatus,
    context?: SessionErrorContext
  ): Promise<void> {
    const session = this.requireSession(sessionId);
    if (!isValidTransition(session.status, newStatus)) {
      throw new RelayError(
        "state_conflict",
        `Cannot transition session ${sessionId} from ${session.status} to ${newStatus}`
      );
    }

    const now = new Date().toISOString();
    const nextErrorCode = newStatus === "failed" ? context?.error_code ?? null : null;
    const nextErrorMessage = newStatus === "failed" ? context?.error_message ?? null : null;

    const result = this.db
      .prepare(
        `
        UPDATE sessions
        SET status = ?, error_code = ?, error_message = ?, updated_at = ?, last_active_at = ?
        WHERE id = ? AND status = ?
        `
      )
      .run(newStatus, nextErrorCode, nextErrorMessage, now, now, sessionId, session.status);

    if (result.changes !== 1) {
      throw new RelayError(
        "state_conflict",
        `Session ${sessionId} changed while transitioning from ${session.status} to ${newStatus}`
      );
    }

    const updatedSession = this.getSession(sessionId);
    if (!updatedSession || updatedSession.status !== newStatus) {
      throw new RelayError(
        "state_conflict",
        `Session ${sessionId} changed while transitioning from ${session.status} to ${newStatus}`
      );
    }

    this.insertAndBroadcastEvent(sessionId, "session_status_changed", {
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

  private requireSession(sessionId: string): Session {
    const session = this.getSession(sessionId);
    if (!session) {
      throw new RelayError("not_found", `Session ${sessionId} does not exist`);
    }

    return session;
  }

  private insertEvent(sessionId: string, eventType: EventType, payload: unknown) {
    const seq = allocateSeq(this.db, sessionId, this.logger);
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

  private insertAndBroadcastEvent(sessionId: string, eventType: EventType, payload: unknown) {
    const storedEvent = this.insertEvent(sessionId, eventType, payload);
    this.hub.broadcastToSession(sessionId, {
      type: "event",
      session_id: sessionId,
      seq: storedEvent.seq,
      event_type: storedEvent.type,
      payload: storedEvent.payload,
      timestamp: storedEvent.created_at
    });
    return storedEvent;
  }

  private normalizeErrorCode(errorCode: string | undefined): ErrorCode {
    if (errorCode && ERROR_CODES.includes(errorCode as ErrorCode)) {
      return errorCode as ErrorCode;
    }

    return "provider_unreachable";
  }

  private async dispatchCreate(session: Session): Promise<string | null> {
    if (session.provider === "openclaw") {
      return (
        await this.openClawBridge.createSession(session.id, session.workspace_cwd, session.initial_prompt ?? "", {
          model: session.model,
          permissionMode: session.permission_mode
        })
      ).sessionKey;
    }

    return await this.createCompanionSession(session);
  }

  private async dispatchResume(session: Session): Promise<string> {
    if (!session.provider_session_id) {
      throw new RelayError("session_not_resumable", `Session ${session.id} has no provider session id`);
    }

    if (session.provider === "openclaw") {
      await this.openClawBridge.resumeSession(session.id, session.provider_session_id);
      return session.provider_session_id;
    }

    const ack = await this.companionManager.sendCommand(session.host_id, {
      cmd: "resume_session",
      req_id: this.companionManager.createRequestId(),
      session_id: session.id,
      provider_session_id: session.provider_session_id,
      cwd: session.workspace_cwd
    });

    return this.extractProviderSessionIdFromAck(ack) ?? session.provider_session_id;
  }

  private async dispatchSendMessage(session: Session, text: string): Promise<void> {
    if (session.provider === "openclaw") {
      await this.openClawBridge.sendMessage(session.id, text);
      return;
    }

    const ack = await this.companionManager.sendCommand(session.host_id, {
      cmd: "send_message",
      req_id: this.companionManager.createRequestId(),
      session_id: session.id,
      text
    });

    this.assertAckOk(ack);
  }

  private async dispatchCancel(session: Session): Promise<void> {
    if (session.provider === "openclaw") {
      await this.openClawBridge.cancelSession(session.id);
      return;
    }

    const ack = await this.companionManager.sendCommand(session.host_id, {
      cmd: "cancel_session",
      req_id: this.companionManager.createRequestId(),
      session_id: session.id
    });

    this.assertAckOk(ack);
  }

  private async createCompanionSession(session: Session): Promise<string | null> {
    const command = {
      cmd: "create_session" as const,
      req_id: this.companionManager.createRequestId(),
      session_id: session.id,
      provider: session.provider as "claude" | "book",
      cwd: session.workspace_cwd,
      prompt: session.initial_prompt ?? "",
      model: session.model ?? undefined,
      permission_mode: session.permission_mode
    };

    const ack = await this.companionManager.sendCommand(session.host_id, command);
    return this.extractProviderSessionIdFromAck(ack);
  }

  private assertAckOk(ack: unknown): asserts ack is { type: "ack"; status: "ok"; data?: unknown } {
    if (!ack || typeof ack !== "object" || !("type" in ack) || ack.type !== "ack") {
      throw new RelayError("state_conflict", "Unexpected companion acknowledgement");
    }

    if ("status" in ack && ack.status === "error") {
      const errorCode = this.normalizeErrorCode(
        "error_code" in ack && typeof ack.error_code === "string" ? ack.error_code : undefined
      );
      throw new RelayError(errorCode, "message" in ack && typeof ack.message === "string" ? ack.message : undefined);
    }
  }

  private extractProviderSessionIdFromAck(ack: unknown): string | null {
    this.assertAckOk(ack);

    return ack.data &&
      typeof ack.data === "object" &&
      "provider_session_id" in ack.data &&
      typeof ack.data.provider_session_id === "string"
      ? ack.data.provider_session_id
      : null;
  }

  private async markSessionStarted(sessionId: string, providerSessionId: string | null): Promise<void> {
    const now = new Date().toISOString();
    const result = this.db
      .prepare(
        `
        UPDATE sessions
        SET provider_session_id = ?, updated_at = ?, last_active_at = ?
        WHERE id = ?
        `
      )
      .run(providerSessionId, now, now, sessionId);

    if (result.changes !== 1) {
      throw new RelayError("state_conflict", `Session ${sessionId} changed before it could enter running`);
    }

    if (!this.isLatestEventType(sessionId, "session_started")) {
      this.insertAndBroadcastEvent(sessionId, "session_started", {
        provider_session_id: providerSessionId
      });
    }
    await this.transition(sessionId, "running");
  }

  private assertProviderAvailable(session: Session): void {
    if (session.provider === "openclaw") {
      if (!this.openClawBridge.isAvailable()) {
        throw new RelayError("provider_unreachable", "OpenClaw gateway is offline");
      }
      return;
    }

    if (!this.companionManager.isOnline(session.host_id)) {
      throw new RelayError("host_offline", `Companion host ${session.host_id} is offline`);
    }
  }

  private sanitizeIncomingPayload(payload: unknown): unknown {
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
      return payload;
    }

    const { seq: _ignoredSeq, ...rest } = payload as Record<string, unknown>;
    return rest;
  }

  private buildCreateAuditDetail(session: Session): {
    provider: Session["provider"];
    host_id: string;
    cwd: string;
    prompt: string;
  } {
    return {
      provider: session.provider,
      host_id: session.host_id,
      cwd: session.workspace_cwd,
      prompt: (session.initial_prompt ?? "").slice(0, 100)
    };
  }

  private isLatestEventType(sessionId: string, eventType: EventType): boolean {
    const row = this.db
      .prepare(
        `
        SELECT type
        FROM session_events
        WHERE session_id = ?
        ORDER BY seq DESC
        LIMIT 1
        `
      )
      .get(sessionId) as { type: EventType } | undefined;

    return row?.type === eventType;
  }

  private async runWithLifecycleLock<T>(sessionId: string, action: () => Promise<T>): Promise<T> {
    if (this.activeLifecycleMutations.has(sessionId)) {
      throw new RelayError("state_conflict", `Session ${sessionId} already has a lifecycle mutation in flight`);
    }

    this.activeLifecycleMutations.add(sessionId);
    try {
      return await action();
    } finally {
      this.activeLifecycleMutations.delete(sessionId);
    }
  }

  private async transitionWithConflictTolerance(
    sessionId: string,
    newStatus: SessionStatus,
    context?: SessionErrorContext
  ): Promise<void> {
    try {
      await this.transition(sessionId, newStatus, context);
    } catch (error) {
      if (error instanceof RelayError && error.code === "state_conflict") {
        this.logger.warn(`Ignoring terminal transition conflict for session ${sessionId}: ${error.message}`);
        return;
      }

      throw error;
    }
  }
}
