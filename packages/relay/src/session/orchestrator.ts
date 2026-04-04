import {
  ERROR_CODES,
  type CompanionEventMessage,
  type CompanionReportLocalSessionsMessage,
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
import type { PushAdapter } from "../push/fcm-adapter";
import { allocateSeq } from "./seq";
import { WsHub } from "../ws/hub";
import { CompanionManager } from "../companion/manager";
import { OpenClawBridge } from "../openclaw/bridge";
import { isValidTransition } from "./transitions";

type LoggerLike = {
  readonly error: (...args: unknown[]) => void;
  readonly warn: (...args: unknown[]) => void;
};

const MAX_REPORT_LOCAL_SESSIONS_BATCH_SIZE = 200;
const VALID_PERMISSION_MODES = new Set(["bypassPermissions", "default", "plan", "acceptEdits", "auto"]);

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

type LifecycleMutation = "create" | "resume" | "cancel" | "complete" | "delete";

type PendingTerminalTransition = {
  readonly status: "idle" | "completed" | "failed";
  readonly context?: SessionErrorContext;
};

type SessionStartMetadata = {
  readonly providerSessionId: string | null;
  readonly model: string | null;
};

function toBooleanFields(session: Session): Session {
  return {
    ...session,
    local_available: Boolean(session.local_available)
  };
}

export class SessionOrchestrator {
  private readonly activeLifecycleMutations = new Map<string, LifecycleMutation>();
  private readonly pendingTerminalTransitions = new Map<string, PendingTerminalTransition>();

  constructor(
    private readonly _config: RelayConfig,
    private readonly db: RelayDatabase,
    private readonly hub: WsHub,
    private readonly companionManager: CompanionManager,
    private readonly openClawBridge: OpenClawBridge,
    private readonly auditLogger: AuditLogger,
    private readonly pushAdapter: PushAdapter,
    private readonly logger: LoggerLike
  ) {}

  async create(input: CreateSessionInput): Promise<Session> {
    if (!input.provider || !input.host_id || !input.cwd) {
      throw new RelayError("invalid_request", "provider, host_id, cwd are required");
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

    if (input.provider === "openclaw" && !this.openClawBridge.isAvailable()) {
      throw new RelayError("provider_unreachable", "OpenClaw gateway is offline");
    }

    const provider = input.provider as "claude" | "book" | "openclaw";
    const hasPrompt = !!input.prompt?.trim();
    const now = new Date().toISOString();
    const sessionId = randomUUID();
    const session: Session = {
      id: sessionId,
      provider,
      provider_session_id: null,
      host_id: input.host_id,
      workspace_root: null,
      workspace_cwd: input.cwd,
      initial_prompt: hasPrompt ? input.prompt ?? null : null,
      model: input.model ?? null,
      permission_mode: VALID_PERMISSION_MODES.has(input.permission_mode ?? "bypassPermissions")
        ? (input.permission_mode ?? "bypassPermissions")
        : "bypassPermissions",
      status: "queued",
      error_message: null,
      error_code: null,
      local_available: false,
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
          local_available,
          created_at,
          updated_at,
          last_active_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        session.local_available ? 1 : 0,
        session.created_at,
        session.updated_at,
        session.last_active_at
      );

    this.auditLogger.write("session.create", {
      session_id: session.id,
      host_id: session.host_id,
      detail: this.buildCreateAuditDetail(session)
    });

    this.activeLifecycleMutations.set(session.id, "create");
    try {
      if (hasPrompt) {
        const startMetadata = await this.dispatchCreate(session);
        await this.markSessionStarted(session.id, startMetadata, "queued");
        await this.applyPendingTerminalTransition(session.id);
      } else {
        await this.transitionWithConflictTolerance(session.id, "idle");
        this.insertAndBroadcastEvent(session.id, "session_idle", {
          reason: "awaiting_first_message"
        });
      }

      return this.getSession(session.id) ?? session;
    } catch (error) {
      const relayError =
        error instanceof RelayError ? error : new RelayError("provider_unreachable", "Companion command failed");

      this.insertAndBroadcastEvent(session.id, "session_error", {
        error_code: relayError.code,
        message: relayError.message
      });
      await this.transitionWithConflictTolerance(session.id, "failed", {
        error_code: relayError.code,
        error_message: relayError.message
      });
      throw relayError;
    } finally {
      this.activeLifecycleMutations.delete(session.id);
      this.pendingTerminalTransitions.delete(session.id);
    }
  }

  async resume(sessionId: string): Promise<Session> {
    return await this.runWithLifecycleLock(sessionId, "resume", async () => {
      const session = this.requireSession(sessionId);
      if (session.status !== "completed" && session.status !== "failed" && session.status !== "cancelled") {
        throw new RelayError("state_conflict", `Session ${sessionId} is not resumable from ${session.status}`);
      }

      if (!session.provider_session_id) {
        throw new RelayError("session_not_resumable", `Session ${sessionId} has no provider session id`);
      }

      this.assertProviderAvailable(session);

      const previousStatus = session.status;
      const startMetadata = await this.dispatchResume(session);
      await this.markSessionStarted(session.id, startMetadata, previousStatus);
      this.auditLogger.write("session.resume", {
        session_id: session.id,
        host_id: session.host_id,
        detail: {
          previous_status: previousStatus
        }
      });
      await this.applyPendingTerminalTransition(session.id);
      return this.requireSession(session.id);
    });
  }

  async sendMessage(sessionId: string, text: string): Promise<void> {
    const session = this.requireSession(sessionId);
    if (session.status !== "running" && session.status !== "idle") {
      throw new RelayError("state_conflict", `Session ${sessionId} is not running or idle`);
    }

    if (session.status === "idle" && !session.provider_session_id) {
      return await this.runWithLifecycleLock(sessionId, "create", async () => {
        const currentSession = this.requireSession(sessionId);
        if (currentSession.status !== "idle" || currentSession.provider_session_id) {
          throw new RelayError("state_conflict", `Session ${sessionId} is no longer awaiting its first message`);
        }

        this.assertProviderAvailable(currentSession);
        const sessionWithPrompt = {
          ...currentSession,
          initial_prompt: text
        };
        const startMetadata = await this.dispatchCreate(sessionWithPrompt);
        this.db
          .prepare("UPDATE sessions SET initial_prompt = ?, updated_at = datetime('now') WHERE id = ?")
          .run(text, currentSession.id);
        await this.markSessionStarted(currentSession.id, startMetadata, "idle");
        await this.applyPendingTerminalTransition(currentSession.id);
      });
    }

    if (this.activeLifecycleMutations.has(sessionId)) {
      throw new RelayError("state_conflict", `Session ${sessionId} already has a lifecycle mutation in flight`);
    }

    this.assertProviderAvailable(session);
    if (session.status === "idle") {
      await this.transition(session.id, "running");
      try {
        await this.dispatchSendMessage(session, text);
      } catch (error) {
        await this.transitionWithConflictTolerance(session.id, "idle");
        throw error;
      }
      return;
    }

    await this.dispatchSendMessage(session, text);
  }

  async answerInteractiveTool(
    sessionId: string,
    callId: string,
    answer: string,
    questionIndex: number = 0
  ): Promise<void> {
    const session = this.requireSession(sessionId);
    if (session.status !== "running") {
      throw new RelayError(
        "state_conflict",
        `Session ${sessionId} is not running (current: ${session.status})`
      );
    }
    this.assertProviderAvailable(session);

    const ack = await this.companionManager.sendCommand(session.host_id, {
      cmd: "answer_interactive_tool",
      req_id: this.companionManager.createRequestId(),
      session_id: sessionId,
      call_id: callId,
      answer,
      question_index: questionIndex
    });
    this.assertAckOk(ack);
  }

  async cancel(sessionId: string): Promise<Session> {
    return await this.runWithLifecycleLock(sessionId, "cancel", async () => {
      const session = this.requireSession(sessionId);
      if (session.status !== "running" && session.status !== "idle") {
        throw new RelayError("state_conflict", `Session ${sessionId} cannot be cancelled from ${session.status}`);
      }

      const shouldDispatchCancel = Boolean(session.provider_session_id);
      if (shouldDispatchCancel && session.provider !== "openclaw") {
        this.assertProviderAvailable(session);
      }

      const previousStatus = session.status;
      if (shouldDispatchCancel) {
        await this.dispatchCancel(session);
      }

      const terminalStateWon = await this.applyPendingTerminalTransition(session.id);
      if (!terminalStateWon) {
        await this.transition(session.id, "cancelled");
        this.auditLogger.write("session.cancel", {
          session_id: session.id,
          host_id: session.host_id,
          detail: {
            previous_status: previousStatus
          }
        });
      }

      return this.requireSession(session.id);
    });
  }

  async complete(sessionId: string): Promise<Session> {
    return await this.runWithLifecycleLock(sessionId, "complete", async () => {
      const session = this.requireSession(sessionId);
      if (session.status !== "running" && session.status !== "idle") {
        throw new RelayError("state_conflict", `Session ${sessionId} cannot be completed from ${session.status}`);
      }

      if (session.provider === "openclaw") {
        throw new RelayError("state_conflict", `Session ${sessionId} cannot be completed for provider openclaw`);
      }

      const shouldDispatchComplete = Boolean(session.provider_session_id);
      if (shouldDispatchComplete) {
        this.assertProviderAvailable(session);
      }

      const previousStatus = session.status;
      if (shouldDispatchComplete) {
        await this.dispatchComplete(session);
      }

      const terminalStateWon = await this.applyPendingTerminalTransition(session.id);
      if (!terminalStateWon) {
        await this.transition(session.id, "completed");
      }

      const finalSession = this.requireSession(session.id);
      if (finalSession.status === "completed") {
        this.auditLogger.write("session.complete", {
          session_id: session.id,
          host_id: session.host_id,
          detail: {
            previous_status: previousStatus
          }
        });
      }

      return this.requireSession(session.id);
    });
  }

  async delete(sessionId: string): Promise<void> {
    await this.runWithLifecycleLock(sessionId, "delete", async () => {
      const session = this.requireSession(sessionId);
      const previousStatus = session.status;

      if (session.status === "queued") {
        throw new RelayError("state_conflict", `Session ${sessionId} cannot be deleted from ${session.status}`);
      }

      if ((session.status === "running" || session.status === "idle") && Boolean(session.provider_session_id)) {
        try {
          await this.dispatchCancel(session);
        } catch (cancelError) {
          // Companion may be offline or may no longer have an active process for an idle session.
          // Deletion still proceeds so the relay-side record can be removed.
          this.logger.warn(`Session ${sessionId} pre-delete cancel failed (proceeding): ${cancelError}`);
        }
        await this.transitionWithConflictTolerance(session.id, "cancelled");
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
          status: previousStatus
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

    const payload = this.sanitizeIncomingPayload(message.payload);
    if (
      message.event_type === "session_status_changed" &&
      session.status === "failed" &&
      payload &&
      typeof payload === "object" &&
      "status" in payload &&
      payload.status === "running"
    ) {
      await this.transition(session.id, "running");
      this.auditLogger.write("session.recover", {
        session_id: session.id,
        host_id: session.host_id,
        detail: {
          previous_status: session.status,
          recovered_status: "running",
          source_event: "session_status_changed"
        }
      });
      return;
    }

    const activeMutation = this.activeLifecycleMutations.get(message.session_id);
    const acceptsTranscriptSyncLateMessage =
      message.source === "transcript_sync" &&
      (message.event_type === "user_message" ||
        message.event_type === "assistant_message" ||
        message.event_type === "session_usage") &&
      (session.status === "completed" || session.status === "failed" || session.status === "cancelled");
    const acceptsEvents =
      session.status === "running" ||
      session.status === "idle" ||
      activeMutation === "create" ||
      activeMutation === "resume" ||
      acceptsTranscriptSyncLateMessage;
    if (!acceptsEvents) {
      this.logger.warn(
        `Dropping ${message.event_type} for non-active session ${message.session_id} (${session.status})`
      );
      return;
    }

    if (message.event_type === "session_started") {
      return;
    }

    if (
      message.event_type === "session_status_changed" &&
      activeMutation === "cancel" &&
      payload &&
      typeof payload === "object" &&
      "status" in payload &&
      payload.status === "cancelled"
    ) {
      return;
    }

    this.insertAndBroadcastEvent(session.id, message.event_type, payload);

    const pendingTerminalTransition = this.buildPendingTerminalTransition(activeMutation, message.event_type, payload);
    if (pendingTerminalTransition) {
      this.pendingTerminalTransitions.set(session.id, pendingTerminalTransition);
      return;
    }

    if (message.event_type === "session_idle") {
      await this.transitionWithConflictTolerance(session.id, "idle");
      return;
    }

    if (message.event_type === "session_result") {
      await this.transitionWithConflictTolerance(session.id, "completed");
      return;
    }

    if (message.event_type === "session_error") {
      await this.transitionWithConflictTolerance(session.id, "failed", this.buildSessionErrorContext(payload));
    }
  }

  async handleReportLocalSessions(
    message: CompanionReportLocalSessionsMessage,
    authenticatedHostId: string
  ): Promise<void> {
    const host = this.db.prepare("SELECT id FROM hosts WHERE id = ?").get(authenticatedHostId) as
      | { id: string }
      | undefined;
    if (!host) {
      this.logger.warn(`Dropping report_local_sessions for unknown host ${authenticatedHostId}`);
      return;
    }

    if (message.host_id !== authenticatedHostId) {
      this.logger.warn(
        `report_local_sessions host_id mismatch: message=${message.host_id} authenticated=${authenticatedHostId}; using authenticated host`
      );
    }

    if (!Array.isArray(message.sessions)) {
      this.logger.warn("report_local_sessions: sessions is not an array");
      return;
    }

    const sessions = message.sessions.slice(0, MAX_REPORT_LOCAL_SESSIONS_BATCH_SIZE);
    if (message.sessions.length > MAX_REPORT_LOCAL_SESSIONS_BATCH_SIZE) {
      this.logger.warn(
        `report_local_sessions: truncated ${message.sessions.length} sessions to ${MAX_REPORT_LOCAL_SESSIONS_BATCH_SIZE}`
      );
    }

    const now = new Date().toISOString();
    const insertStmt = this.db.prepare(`
      INSERT INTO sessions (
        id,
        provider,
        provider_session_id,
        host_id,
        workspace_cwd,
        status,
        local_available,
        permission_mode,
        created_at,
        updated_at,
        last_active_at
      )
      SELECT ?, ?, ?, ?, ?, 'completed', 1, 'bypassPermissions', ?, ?, ?
      WHERE NOT EXISTS (SELECT 1 FROM sessions WHERE provider_session_id = ?)
    `);
    const updateStmt = this.db.prepare(`
      UPDATE sessions
      SET local_available = 1
      WHERE provider_session_id = ? AND local_available = 0
    `);

    const transaction = this.db.transaction((): { created: number; updated: number; skipped: number } => {
      let created = 0;
      let updated = 0;
      let skipped = 0;

      for (const session of sessions) {
        if (!session.provider_session_id || session.provider_session_id.trim() === "") {
          this.logger.warn("Skipping local session with empty provider_session_id");
          skipped += 1;
          continue;
        }

        const result = insertStmt.run(
          randomUUID(),
          session.provider,
          session.provider_session_id,
          authenticatedHostId,
          session.cwd,
          session.created_at || now,
          now,
          now,
          session.provider_session_id
        );

        if (result.changes > 0) {
          created += 1;
          continue;
        }

        const updateResult = updateStmt.run(session.provider_session_id);
        if (updateResult.changes > 0) {
          updated += 1;
        } else {
          skipped += 1;
        }
      }

      return { created, updated, skipped };
    });

    const stats = transaction();
    if (stats.created > 0 || stats.updated > 0) {
      this.auditLogger.write("session.local_sync", {
        host_id: authenticatedHostId,
        detail: stats
      });
    }
  }

  async handleHostDisconnected(hostId: string): Promise<void> {
    const sessions = this.db
      .prepare(
        `
        SELECT *
        FROM sessions
        WHERE host_id = ? AND status IN ('running', 'idle')
        ORDER BY created_at ASC
        `
      )
      .all(hostId) as Session[];

    for (const session of sessions.map(toBooleanFields)) {
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

    if (newStatus === "completed" || newStatus === "failed") {
      void this.pushAdapter.notify(sessionId, newStatus, context?.error_message);
    }
  }

  private getSession(sessionId: string): Session | null {
    const session =
      (this.db.prepare("SELECT * FROM sessions WHERE id = ?").get(sessionId) as Session | undefined) ?? null;

    return session ? toBooleanFields(session) : null;
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

  private buildSessionErrorContext(payload: unknown): Required<SessionErrorContext> {
    return payload && typeof payload === "object"
      ? {
          error_code: this.normalizeErrorCode(
            "error_code" in payload && typeof payload.error_code === "string" ? payload.error_code : undefined
          ),
          error_message:
            "message" in payload && typeof payload.message === "string" ? payload.message : "Session failed"
        }
      : {
          error_code: "provider_unreachable",
          error_message: "Session failed"
        };
  }

  private buildPendingTerminalTransition(
    activeMutation: LifecycleMutation | undefined,
    eventType: EventType,
    payload: unknown
  ): PendingTerminalTransition | null {
    if (eventType === "session_idle" && (activeMutation === "create" || activeMutation === "resume")) {
      return {
        status: "idle"
      };
    }

    if (
      activeMutation !== "create" &&
      activeMutation !== "resume" &&
      activeMutation !== "cancel" &&
      activeMutation !== "complete"
    ) {
      return null;
    }

    if (eventType === "session_result") {
      return {
        status: "completed"
      };
    }

    if (eventType === "session_error") {
      return {
        status: "failed",
        context: this.buildSessionErrorContext(payload)
      };
    }

    return null;
  }

  private async dispatchCreate(session: Session): Promise<SessionStartMetadata> {
    if (session.provider === "openclaw") {
      const openClawSession = await this.openClawBridge.createSession(
        session.id,
        session.workspace_cwd,
        session.initial_prompt ?? "",
        {
          model: session.model,
          permissionMode: session.permission_mode
        }
      );
      return {
        providerSessionId: openClawSession.sessionKey,
        model: session.model
      };
    }

    return await this.createCompanionSession(session);
  }

  private async dispatchResume(session: Session): Promise<SessionStartMetadata> {
    if (!session.provider_session_id) {
      throw new RelayError("session_not_resumable", `Session ${session.id} has no provider session id`);
    }

    if (session.provider === "openclaw") {
      await this.openClawBridge.resumeSession(session.id, session.provider_session_id);
      return {
        providerSessionId: session.provider_session_id,
        model: session.model
      };
    }

    const ack = await this.companionManager.sendCommand(session.host_id, {
      cmd: "resume_session",
      req_id: this.companionManager.createRequestId(),
      session_id: session.id,
      provider_session_id: session.provider_session_id,
      cwd: session.workspace_cwd
    });

    const startMetadata = this.extractStartMetadataFromAck(ack);
    return {
      providerSessionId: startMetadata.providerSessionId ?? session.provider_session_id,
      model: startMetadata.model ?? session.model
    };
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

  private async dispatchComplete(session: Session): Promise<void> {
    const ack = await this.companionManager.sendCommand(session.host_id, {
      cmd: "complete_session",
      req_id: this.companionManager.createRequestId(),
      session_id: session.id
    });

    this.assertAckOk(ack);
  }

  private async createCompanionSession(session: Session): Promise<SessionStartMetadata> {
    const command = {
      cmd: "create_session" as const,
      req_id: this.companionManager.createRequestId(),
      session_id: session.id,
      provider: session.provider as "claude" | "book",
      cwd: session.workspace_cwd,
      prompt: session.initial_prompt ?? "",
      model: session.model ?? undefined,
      // Preserve non-default modes instead of normalizing them away so the future approval path
      // can be exercised without changing relay orchestration behavior later.
      permission_mode: session.permission_mode
    };

    const ack = await this.companionManager.sendCommand(session.host_id, command);
    const startMetadata = this.extractStartMetadataFromAck(ack);
    return {
      providerSessionId: startMetadata.providerSessionId,
      model: startMetadata.model ?? session.model
    };
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

  private extractStartMetadataFromAck(ack: unknown): SessionStartMetadata {
    this.assertAckOk(ack);

    const data = ack.data && typeof ack.data === "object" ? ack.data : null;
    return {
      providerSessionId:
        data && "provider_session_id" in data && typeof data.provider_session_id === "string"
          ? data.provider_session_id
          : null,
      model: data && "model" in data && typeof data.model === "string" ? data.model : null
    };
  }

  private async markSessionStarted(
    sessionId: string,
    startMetadata: SessionStartMetadata,
    expectedStatus: "queued" | "idle" | "completed" | "failed" | "cancelled"
  ): Promise<void> {
    const session = this.requireSession(sessionId);
    const model = startMetadata.model ?? session.model ?? null;
    const now = new Date().toISOString();
    const result = this.db
      .prepare(
        `
        UPDATE sessions
        SET provider_session_id = ?,
            model = ?,
            status = 'running',
            error_code = NULL,
            error_message = NULL,
            local_available = CASE WHEN provider IN ('claude', 'book') THEN 1 ELSE 0 END,
            updated_at = ?,
            last_active_at = ?
        WHERE id = ? AND status = ?
        `
      )
      .run(startMetadata.providerSessionId, model, now, now, sessionId, expectedStatus);

    if (result.changes !== 1) {
      throw new RelayError(
        "state_conflict",
        `Session ${sessionId} changed before it could enter running from ${expectedStatus}`
      );
    }

    if (!this.isLatestEventType(sessionId, "session_started")) {
      this.insertAndBroadcastEvent(sessionId, "session_started", {
        provider_session_id: startMetadata.providerSessionId,
        ...(model ? { model } : {})
      });
    }

    this.insertAndBroadcastEvent(sessionId, "session_status_changed", {
      status: "running",
      error_code: null,
      error_message: null
    });

    this.hub.broadcastToSession(sessionId, {
      type: "status",
      session_id: sessionId,
      status: "running"
    });
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

  private async runWithLifecycleLock<T>(
    sessionId: string,
    mutation: LifecycleMutation,
    action: () => Promise<T>
  ): Promise<T> {
    if (this.activeLifecycleMutations.has(sessionId)) {
      throw new RelayError("state_conflict", `Session ${sessionId} already has a lifecycle mutation in flight`);
    }

    this.activeLifecycleMutations.set(sessionId, mutation);
    try {
      return await action();
    } finally {
      this.activeLifecycleMutations.delete(sessionId);
      this.pendingTerminalTransitions.delete(sessionId);
    }
  }

  private async applyPendingTerminalTransition(sessionId: string): Promise<boolean> {
    const pending = this.pendingTerminalTransitions.get(sessionId);
    if (!pending) {
      return false;
    }

    this.pendingTerminalTransitions.delete(sessionId);
    await this.transitionWithConflictTolerance(sessionId, pending.status, pending.context);
    return true;
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
