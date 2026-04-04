import { spawn as spawnChildProcess, type ChildProcessWithoutNullStreams } from "node:child_process";
import { createInterface, type Interface as ReadLineInterface } from "node:readline";

import type {
  CompanionEventMessage,
  CreateSessionCommand,
  InteractiveProvider,
  ResumeSessionCommand
} from "@imbot/wire";

import type { CompanionProviderConfig } from "../config";
import { CompanionError, type LoggerLike } from "../types";
import { RuntimeEventMapper } from "./event-mapper";
import { SessionIndex, type SessionIndexEntry } from "./session-index";

type SpawnFunction = typeof spawnChildProcess;

interface RuntimeSession {
  readonly relaySessionId: string;
  readonly provider: InteractiveProvider;
  readonly cwd: string;
  readonly createdAt: string;
  initialPrompt: string | null;
  readonly child: ChildProcessWithoutNullStreams;
  readonly stdout: ReadLineInterface;
  readonly providerSessionIdPromise: Promise<string>;
  readonly resolveProviderSessionId: (providerSessionId: string) => void;
  readonly rejectProviderSessionId: (error: unknown) => void;
  providerSessionIdSettled: boolean;
  providerSessionId: string | null;
  stderrTail: string;
  resultEmitted: boolean;
  cancelled: boolean;
  completing: boolean;
  closed: boolean;
  idleTimer: NodeJS.Timeout | null;
  pendingControlTimer: NodeJS.Timeout | null;
  isIdle: boolean;
  emitIdleOnReady: boolean;
  exitPromise: Promise<void>;
  resolveExit: () => void;
  readonly eventMapper: RuntimeEventMapper;
  readonly emittedToolCallIds: Set<string>;
  pendingControlResponse: {
    requestId: string;
    callId: string;
    originalInput: Record<string, unknown>;
    resolve: (answer: string, questionIndex: number) => void;
    reject: (reason: unknown) => void;
  } | null;
}

export interface ClaudeRuntimeAdapterOptions {
  readonly providers: Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>>;
  readonly sessionIndex: SessionIndex;
  readonly sendEvent: (message: CompanionEventMessage) => Promise<void> | void;
  readonly isAllowedDirectory?: (provider: InteractiveProvider, cwd: string) => boolean;
  readonly logger?: LoggerLike;
  readonly spawn?: SpawnFunction;
  readonly killGraceMs?: number;
  readonly idleTimeoutMs?: number;
}

export class ClaudeRuntimeAdapter {
  private readonly logger: LoggerLike;
  private readonly spawn: SpawnFunction;
  private readonly activeByProviderSessionId = new Map<string, RuntimeSession>();
  private readonly activeByRelaySessionId = new Map<string, RuntimeSession>();
  private readonly killGraceMs: number;
  private readonly idleTimeoutMs: number;

  constructor(private readonly options: ClaudeRuntimeAdapterOptions) {
    this.logger = options.logger ?? console;
    this.spawn = options.spawn ?? spawnChildProcess;
    this.killGraceMs = options.killGraceMs ?? 5000;
    this.idleTimeoutMs = options.idleTimeoutMs ?? 1800000;
  }

  async createSession(command: CreateSessionCommand): Promise<{ provider_session_id: string }> {
    if (this.activeByRelaySessionId.has(command.session_id)) {
      throw new CompanionError("state_conflict", `Session ${command.session_id} is already active`);
    }

    const providerConfig = this.getProviderConfig(command.provider);
    if (this.options.isAllowedDirectory && !this.options.isAllowedDirectory(command.provider, command.cwd)) {
      throw new CompanionError(
        "forbidden",
        `Directory ${command.cwd} is not allowed for provider ${command.provider}`
      );
    }
    const args = [
      "--input-format",
      "stream-json",
      "--output-format",
      "stream-json",
      "--verbose",
      "--permission-prompt-tool",
      "stdio",
      "--permission-mode",
      command.permission_mode
    ];

    if (command.model) {
      args.push("--model", command.model);
    }

    const session = this.spawnSession({
      relaySessionId: command.session_id,
      provider: command.provider,
      cwd: command.cwd,
      binary: providerConfig.binary,
      args,
      initialPrompt: command.prompt.slice(0, 200)
    });

    const providerSessionId = await session.providerSessionIdPromise;
    await this.writeUserMessage(session, command.prompt);
    return {
      provider_session_id: providerSessionId
    };
  }

  async resumeSession(command: ResumeSessionCommand): Promise<{ provider_session_id: string }> {
    const indexed = this.options.sessionIndex.get(command.session_id);
    if (!indexed || indexed.provider_session_id !== command.provider_session_id) {
      throw new CompanionError(
        "session_not_resumable",
        `No resumable session mapping for relay session ${command.session_id}`
      );
    }

    if (this.activeByRelaySessionId.has(command.session_id)) {
      throw new CompanionError("state_conflict", `Session ${command.session_id} is already running`);
    }

    const providerConfig = this.getProviderConfig(indexed.provider);
    if (this.options.isAllowedDirectory && !this.options.isAllowedDirectory(indexed.provider, command.cwd)) {
      throw new CompanionError(
        "forbidden",
        `Directory ${command.cwd} is not allowed for provider ${indexed.provider}`
      );
    }
    this.spawnSession({
      relaySessionId: command.session_id,
      provider: indexed.provider,
      cwd: command.cwd,
      binary: providerConfig.binary,
      args: [
        "--input-format",
        "stream-json",
        "--output-format",
        "stream-json",
        "--verbose",
        "--permission-prompt-tool",
        "stdio",
        "-r",
        command.provider_session_id
      ],
      knownProviderSessionId: command.provider_session_id,
      emitIdleOnReady: true,
      indexEntry: {
        provider_session_id: command.provider_session_id,
        cwd: command.cwd,
        provider: indexed.provider,
        created_at: indexed.created_at,
        source: indexed.source,
        initial_prompt: indexed.initial_prompt
      }
    });

    return {
      provider_session_id: command.provider_session_id
    };
  }

  async sendMessage(relaySessionId: string, text: string): Promise<void> {
    const session = this.requireActiveSession(relaySessionId);
    session.isIdle = false;
    this.clearIdleTimer(session);
    await this.writeUserMessage(session, text, {
      emitUserMessageEvent: true
    });
  }

  async completeSession(relaySessionId: string): Promise<void> {
    const session = this.activeByRelaySessionId.get(relaySessionId);
    if (!session) {
      throw new CompanionError("session_not_found", `No active process for session ${relaySessionId}`);
    }

    this.rejectPendingControlResponse(session, "Interactive tool was interrupted by session completion", {
      writeControlResponse: true
    });
    session.completing = true;
    session.isIdle = false;
    this.clearIdleTimer(session);
    session.child.kill("SIGTERM");

    const didExit = await waitForExit(session.exitPromise, this.killGraceMs);
    if (didExit) {
      return;
    }

    this.logger.warn?.(`Session ${relaySessionId} did not exit after SIGTERM; sending SIGKILL`);
    session.child.kill("SIGKILL");
    await session.exitPromise;
  }

  private async writeUserMessage(
    session: RuntimeSession,
    text: string,
    options?: {
      readonly emitUserMessageEvent?: boolean;
    }
  ): Promise<void> {
    if (!session.child.stdin.writable) {
      throw new CompanionError(
        "session_not_found",
        `No running process for session ${session.providerSessionId ?? session.relaySessionId}`
      );
    }

    await new Promise<void>((resolve, reject) => {
      const payload =
        JSON.stringify({
          type: "user",
          message: {
            role: "user",
            content: text
          }
        }) + "\n";
      let settled = false;

      const finish = (callback: () => void) => {
        if (settled) {
          return;
        }

        settled = true;
        session.child.stdin.off("error", onError);
        callback();
      };

      const onError = (error: Error) => {
        finish(() => {
          reject(new CompanionError("provider_unreachable", error.message));
        });
      };

      session.child.stdin.on("error", onError);
      const flushed = session.child.stdin.write(payload, (error) => {
        if (error) {
          finish(() => {
            reject(new CompanionError("provider_unreachable", error.message));
          });
          return;
        }

        finish(resolve);
      });

      if (flushed) {
        return;
      }

      session.child.stdin.once("drain", () => {
        finish(resolve);
      });
    });

    if (options?.emitUserMessageEvent) {
      await Promise.resolve(
        this.options.sendEvent({
          type: "event",
          session_id: session.relaySessionId,
          event_type: "user_message",
          payload: {
            text
          }
        })
      );
    }
  }

  async cancel(relaySessionId: string): Promise<void> {
    const session = this.requireActiveSession(relaySessionId);

    if (session.cancelled) {
      await session.exitPromise;
      return;
    }

    this.rejectPendingControlResponse(session, "Interactive tool was interrupted by session cancellation", {
      writeControlResponse: true
    });
    session.cancelled = true;
    session.child.kill("SIGINT");

    const didExitGracefully = await waitForExit(session.exitPromise, this.killGraceMs);
    if (didExitGracefully) {
      return;
    }

    this.logger.warn?.(`Session ${relaySessionId} did not exit after SIGINT; sending SIGKILL`);
    session.child.kill("SIGKILL");
    await session.exitPromise;
  }

  async shutdown(): Promise<void> {
    const sessions = Array.from(this.activeByRelaySessionId.values());
    for (const session of sessions) {
      this.clearIdleTimer(session);
      this.clearPendingControlTimer(session);
      await Promise.resolve(
        this.options.sendEvent({
          type: "event",
          session_id: session.relaySessionId,
          event_type: "session_error",
          payload: {
            error_code: "companion_restart",
            message: "Companion shutting down; session process will be lost"
          }
        })
      ).catch(() => {});
      session.cancelled = true;
      session.child.kill("SIGTERM");
    }
    await Promise.allSettled(sessions.map((s) => waitForExit(s.exitPromise, this.killGraceMs)));
    for (const session of sessions) {
      if (session.child.exitCode === null) {
        session.child.kill("SIGKILL");
      }
    }
  }

  getActiveSessionCount(): number {
    return this.activeByRelaySessionId.size;
  }

  getActiveSessionIds(): string[] {
    return Array.from(this.activeByRelaySessionId.keys());
  }

  rejectAllPendingControlResponses(reason: string): void {
    for (const session of this.activeByRelaySessionId.values()) {
      this.rejectPendingControlResponse(session, reason, {
        writeControlResponse: true,
        emitCompletion: true
      });
    }
  }

  answerInteractiveTool(relaySessionId: string, callId: string, answer: string, questionIndex = 0): void {
    const session = this.requireActiveSession(relaySessionId);
    const pending = session.pendingControlResponse;
    if (!pending) {
      throw new CompanionError(
        "no_pending_control_request",
        `No pending interactive tool request for session ${relaySessionId}`
      );
    }

    if (pending.callId !== callId) {
      throw new CompanionError(
        "call_id_mismatch",
        `Pending control request ${pending.callId} does not match ${callId}`
      );
    }

    pending.resolve(answer, questionIndex);
  }

  getActiveSessions(): Array<{
    readonly sessionId: string;
    readonly status: "running" | "idle";
  }> {
    return Array.from(this.activeByRelaySessionId.values()).map((session) => ({
      sessionId: session.relaySessionId,
      status: session.isIdle ? "idle" : "running"
    }));
  }

  private spawnSession(params: {
    readonly relaySessionId: string;
    readonly provider: InteractiveProvider;
    readonly cwd: string;
    readonly binary: string;
    readonly args: string[];
    readonly knownProviderSessionId?: string;
    readonly emitIdleOnReady?: boolean;
    readonly initialPrompt?: string | null;
    readonly indexEntry?: SessionIndexEntry;
  }): RuntimeSession {
    const child = this.spawn(params.binary, params.args, {
      cwd: params.cwd,
      stdio: ["pipe", "pipe", "pipe"]
    }) as ChildProcessWithoutNullStreams;

    let resolveProviderSessionId!: (providerSessionId: string) => void;
    let rejectProviderSessionId!: (error: unknown) => void;
    const providerSessionIdPromise = new Promise<string>((resolve, reject) => {
      resolveProviderSessionId = resolve;
      rejectProviderSessionId = reject;
    });

    let resolveExit!: () => void;
    const exitPromise = new Promise<void>((resolve) => {
      resolveExit = resolve;
    });

    const stdout = createInterface({
      input: child.stdout,
      crlfDelay: Infinity
    });

    const session: RuntimeSession = {
      relaySessionId: params.relaySessionId,
      provider: params.provider,
      cwd: params.cwd,
      createdAt: params.indexEntry?.created_at ?? new Date().toISOString(),
      initialPrompt: params.initialPrompt ?? null,
      child,
      stdout,
      providerSessionIdPromise,
      resolveProviderSessionId,
      rejectProviderSessionId,
      providerSessionIdSettled: false,
      providerSessionId: null,
      stderrTail: "",
      resultEmitted: false,
      cancelled: false,
      completing: false,
      closed: false,
      idleTimer: null,
      pendingControlTimer: null,
      isIdle: false,
      emitIdleOnReady: params.emitIdleOnReady ?? false,
      exitPromise,
      resolveExit,
      eventMapper: new RuntimeEventMapper(),
      emittedToolCallIds: new Set<string>(),
      pendingControlResponse: null
    };

    if (params.knownProviderSessionId) {
      this.registerProviderSessionId(session, params.knownProviderSessionId, params.indexEntry);
    }

    stdout.on("line", (line) => {
      void this.handleStdoutLine(session, line).catch((error) => {
        this.logger.error?.(error);
      });
    });

    child.stderr.on("data", (chunk: Buffer | string) => {
      session.stderrTail = appendTail(session.stderrTail, chunk.toString(), 500);
    });

    child.once("error", (error) => {
      if (!session.providerSessionIdSettled) {
        session.providerSessionIdSettled = true;
        session.rejectProviderSessionId(new CompanionError("spawn_error", error.message));
      }
    });

    child.once("close", (code, signal) => {
      void this.handleClose(session, code, signal).finally(() => {
        session.resolveExit();
      });
    });

    return session;
  }

  private async handleStdoutLine(session: RuntimeSession, line: string): Promise<void> {
    let parsed: unknown;
    try {
      parsed = JSON.parse(line) as unknown;
    } catch {
      this.logger.debug?.(`Skipping non-JSON runtime output: ${line}`);
      return;
    }

    const record = asRecord(parsed);
    const type = typeof record?.type === "string" ? record.type : null;
    if (type === "control_request") {
      await this.handleControlRequest(session, parsed);
      return;
    }

    if (type === "control_cancel_request") {
      await this.handleControlCancelRequest(session, parsed);
      return;
    }

    if (type === "control_response") {
      return;
    }

    const mapped = session.eventMapper.map(parsed);
    if (!mapped) {
      return;
    }

    if (mapped.kind === "provider_session_id") {
      if (!session.providerSessionId) {
        this.registerProviderSessionId(session, mapped.providerSessionId);
      }

      if (session.emitIdleOnReady) {
        session.emitIdleOnReady = false;
        session.isIdle = true;
        this.startIdleTimer(session);
        await Promise.resolve(
          this.options.sendEvent({
            type: "event",
            session_id: session.relaySessionId,
            event_type: "session_idle",
            payload: {
              result: null
            }
          })
        );
      }

      return;
    }

    if (mapped.eventType === "session_result") {
      if (session.child.exitCode === null && !session.completing) {
        session.isIdle = true;
        session.resultEmitted = false;
        this.startIdleTimer(session);
        await Promise.resolve(
          this.options.sendEvent({
            type: "event",
            session_id: session.relaySessionId,
            event_type: "session_idle",
            payload: mapped.payload
          })
        );
        return;
      }

      session.resultEmitted = true;
    }

    if (mapped.eventType === "tool_call_started") {
      const payload = asRecord(mapped.payload);
      const callId = typeof payload?.call_id === "string" ? payload.call_id : null;
      if (callId) {
        if (session.emittedToolCallIds.has(callId)) {
          return;
        }
        session.emittedToolCallIds.add(callId);
      }
    }

    await Promise.resolve(
      this.options.sendEvent({
        type: "event",
        session_id: session.relaySessionId,
        event_type: mapped.eventType,
        payload: mapped.payload
      })
    );
  }

  private async handleControlRequest(session: RuntimeSession, raw: unknown): Promise<void> {
    const record = asRecord(raw);
    const requestId = typeof record?.request_id === "string" ? record.request_id : null;
    const request = asRecord(record?.request);
    const subtype = typeof request?.subtype === "string" ? request.subtype : null;
    const toolName = typeof request?.tool_name === "string" ? request.tool_name : null;
    const callId =
      typeof request?.tool_use_id === "string" && request.tool_use_id !== "" ? request.tool_use_id : requestId;
    const input = asRecord(request?.input) ?? {};

    if (!requestId || !callId) {
      return;
    }

    if (subtype !== "can_use_tool") {
      this.writeControlErrorResponse(session, requestId, `Unsupported control request subtype: ${subtype ?? "unknown"}`);
      return;
    }

    if (toolName !== "AskUserQuestion") {
      this.writeControlSuccessResponse(session, requestId, input);
      return;
    }

    if (session.pendingControlResponse) {
      this.rejectPendingControlResponse(session, "Interactive tool request was superseded", {
        writeControlResponse: true,
        emitCompletion: true
      });
    }

    session.pendingControlResponse = {
      requestId,
      callId,
      originalInput: input,
      resolve: (answer, questionIndex) => {
        this.clearPendingControlTimer(session);
        const updatedInput = buildUpdatedControlInput(input, answer, questionIndex);
        this.writeControlSuccessResponse(session, requestId, updatedInput);
        session.pendingControlResponse = null;
      },
      reject: (reason) => {
        this.clearPendingControlTimer(session);
        const message = getErrorMessage(reason, "Interactive tool request was cancelled");
        this.writeControlErrorResponse(session, requestId, message);
        session.pendingControlResponse = null;
      }
    };
    this.startPendingControlTimer(session);
    session.eventMapper.markToolEmitted(callId, "AskUserQuestion");

    if (session.emittedToolCallIds.has(callId)) {
      return;
    }
    session.emittedToolCallIds.add(callId);

    await Promise.resolve(
      this.options.sendEvent({
        type: "event",
        session_id: session.relaySessionId,
        event_type: "tool_call_started",
        payload: {
          call_id: callId,
          tool: "AskUserQuestion",
          input
        }
      })
    );
  }

  private async handleControlCancelRequest(session: RuntimeSession, raw: unknown): Promise<void> {
    const record = asRecord(raw);
    const requestId = typeof record?.request_id === "string" ? record.request_id : null;
    if (!requestId || session.pendingControlResponse?.requestId !== requestId) {
      return;
    }

    this.rejectPendingControlResponse(session, "Interactive tool request was cancelled", {
      writeControlResponse: false,
      emitCompletion: true
    });
  }

  private async handleClose(session: RuntimeSession, code: number | null, signal: NodeJS.Signals | null): Promise<void> {
    this.clearIdleTimer(session);
    this.clearPendingControlTimer(session);
    session.closed = true;
    session.stdout.close();
    this.rejectPendingControlResponse(session, "Runtime exited while waiting for interactive tool input", {
      writeControlResponse: false
    });

    if (session.providerSessionId) {
      this.activeByProviderSessionId.delete(session.providerSessionId);
      this.activeByRelaySessionId.delete(session.relaySessionId);
    }

    if (!session.providerSessionIdSettled) {
      session.providerSessionIdSettled = true;
      session.rejectProviderSessionId(
        new CompanionError("spawn_error", `Runtime exited before providing a session id (code=${code}, signal=${signal})`)
      );
      return;
    }

    if (session.cancelled) {
      await Promise.resolve(
        this.options.sendEvent({
          type: "event",
          session_id: session.relaySessionId,
          event_type: "session_status_changed",
          payload: {
            status: "cancelled"
          }
        })
      );
      return;
    }

    if (session.completing) {
      if (!session.resultEmitted) {
        session.resultEmitted = true;
        await Promise.resolve(
          this.options.sendEvent({
            type: "event",
            session_id: session.relaySessionId,
            event_type: "session_result",
            payload: {
              result: null
            }
          })
        );
      }

      return;
    }

    if (code === 0) {
      if (!session.resultEmitted) {
        session.resultEmitted = true;
        await Promise.resolve(
          this.options.sendEvent({
            type: "event",
            session_id: session.relaySessionId,
            event_type: "session_result",
            payload: {
              result: null
            }
          })
        );
      }

      return;
    }

    const payload =
      signal && signal !== "SIGINT"
        ? {
            error_code: "process_killed",
            message: `CLI process was killed by signal ${signal}`,
            stderr: session.stderrTail
          }
        : {
            error_code: "process_crash",
            message: `CLI exited with code ${code ?? "unknown"}`,
            stderr: session.stderrTail
          };

    await Promise.resolve(
      this.options.sendEvent({
        type: "event",
        session_id: session.relaySessionId,
        event_type: "session_error",
        payload
      })
    );
  }

  private registerProviderSessionId(
    session: RuntimeSession,
    providerSessionId: string,
    indexEntry?: SessionIndexEntry
  ): void {
    session.providerSessionId = providerSessionId;
    this.activeByProviderSessionId.set(providerSessionId, session);
    this.activeByRelaySessionId.set(session.relaySessionId, session);

    this.options.sessionIndex.set(
      session.relaySessionId,
      indexEntry ?? {
        provider_session_id: providerSessionId,
        cwd: session.cwd,
        provider: session.provider,
        created_at: session.createdAt,
        source: "remote",
        initial_prompt: session.initialPrompt ?? null
      }
    );

    if (!session.providerSessionIdSettled) {
      session.providerSessionIdSettled = true;
      session.resolveProviderSessionId(providerSessionId);
    }
  }

  private requireActiveSession(relaySessionId: string): RuntimeSession {
    const active = this.activeByRelaySessionId.get(relaySessionId);
    if (active) {
      return active;
    }

    const indexed = this.options.sessionIndex.get(relaySessionId);
    if (indexed) {
      const byProviderSessionId = this.activeByProviderSessionId.get(indexed.provider_session_id);
      if (byProviderSessionId) {
        return byProviderSessionId;
      }
    }

    throw new CompanionError("session_not_found", `No running process for session ${relaySessionId}`);
  }

  private getProviderConfig(provider: InteractiveProvider): CompanionProviderConfig {
    const providerConfig = this.options.providers[provider];
    if (!providerConfig) {
      throw new CompanionError("invalid_request", `Provider ${provider} is not configured on this companion`);
    }

    return providerConfig;
  }

  private startIdleTimer(session: RuntimeSession): void {
    this.clearIdleTimer(session);
    session.idleTimer = setTimeout(() => {
      this.rejectPendingControlResponse(session, "Interactive tool answer timeout", {
        writeControlResponse: true
      });
      this.logger.warn?.(`Session ${session.relaySessionId} idle timeout; completing`);
      void this.completeSession(session.relaySessionId).catch((error) => {
        this.logger.error?.(error);
      });
    }, this.idleTimeoutMs);
    session.idleTimer.unref?.();
  }

  private startPendingControlTimer(session: RuntimeSession): void {
    this.clearPendingControlTimer(session);
    session.pendingControlTimer = setTimeout(() => {
      this.rejectPendingControlResponse(session, "Interactive tool answer timeout", {
        writeControlResponse: true,
        emitCompletion: true
      });
    }, this.idleTimeoutMs);
    session.pendingControlTimer.unref?.();
  }

  private clearIdleTimer(session: RuntimeSession): void {
    if (session.idleTimer) {
      clearTimeout(session.idleTimer);
      session.idleTimer = null;
    }
  }

  private clearPendingControlTimer(session: RuntimeSession): void {
    if (session.pendingControlTimer) {
      clearTimeout(session.pendingControlTimer);
      session.pendingControlTimer = null;
    }
  }

  private rejectPendingControlResponse(
    session: RuntimeSession,
    reason: unknown,
    options?: {
      readonly writeControlResponse?: boolean;
      readonly emitCompletion?: boolean;
    }
  ): void {
    const pending = session.pendingControlResponse;
    if (!pending) {
      this.clearPendingControlTimer(session);
      return;
    }

    this.clearPendingControlTimer(session);
    session.pendingControlResponse = null;
    const message = getErrorMessage(reason, "Interactive tool request was cancelled");

    if (options?.writeControlResponse) {
      if (session.child.stdin.writable) {
        pending.reject(message);
      }
    }

    if (options?.emitCompletion) {
      void Promise.resolve(
        this.options.sendEvent({
          type: "event",
          session_id: session.relaySessionId,
          event_type: "tool_call_completed",
          payload: {
            call_id: pending.callId,
            tool: "AskUserQuestion",
            result: null,
            cancelled: true
          }
        })
      ).catch((error) => {
        this.logger.error?.(error);
      });
    }
  }

  private writeControlSuccessResponse(
    session: RuntimeSession,
    requestId: string,
    updatedInput: Record<string, unknown>
  ): void {
    this.writeControlMessage(session, {
      type: "control_response",
      response: {
        subtype: "success",
        request_id: requestId,
        response: {
          behavior: "allow",
          updatedInput
        }
      }
    });
  }

  private writeControlErrorResponse(session: RuntimeSession, requestId: string, error: string): void {
    this.writeControlMessage(session, {
      type: "control_response",
      response: {
        subtype: "error",
        request_id: requestId,
        error
      }
    });
  }

  private writeControlMessage(session: RuntimeSession, payload: Record<string, unknown>): void {
    if (!session.child.stdin.writable) {
      return;
    }

    const onError = (error: Error) => {
      this.logger.warn?.(`Failed to write control message: ${error.message}`);
    };

    session.child.stdin.once("error", onError);

    try {
      session.child.stdin.write(`${JSON.stringify(payload)}\n`, (error) => {
        session.child.stdin.off("error", onError);
        if (error) {
          this.logger.warn?.(`Failed to write control message: ${error.message}`);
        }
      });
    } catch {
      session.child.stdin.off("error", onError);
    }
  }
}

async function waitForExit(exitPromise: Promise<void>, timeoutMs: number): Promise<boolean> {
  let timeoutHandle: NodeJS.Timeout | null = null;

  try {
    await Promise.race([
      exitPromise,
      new Promise<never>((_, reject) => {
        timeoutHandle = setTimeout(() => {
          reject(new Error("timeout"));
        }, timeoutMs);
        timeoutHandle.unref?.();
      })
    ]);
    return true;
  } catch {
    return false;
  } finally {
    if (timeoutHandle) {
      clearTimeout(timeoutHandle);
    }
  }
}

function appendTail(current: string, next: string, maxLength: number): string {
  const combined = `${current}${next}`;
  return combined.length > maxLength ? combined.slice(combined.length - maxLength) : combined;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }

  return value as Record<string, unknown>;
}

function buildUpdatedControlInput(
  originalInput: Record<string, unknown>,
  answer: string,
  questionIndex: number
): Record<string, unknown> {
  const answers = asRecord(originalInput.answers) ?? {};
  return {
    ...originalInput,
    answers: {
      ...answers,
      [String(questionIndex)]: answer
    }
  };
}

function getErrorMessage(reason: unknown, fallback: string): string {
  if (reason instanceof Error && reason.message) {
    return reason.message;
  }

  if (typeof reason === "string" && reason) {
    return reason;
  }

  return fallback;
}
