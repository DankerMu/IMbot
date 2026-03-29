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
import { mapRuntimeEvent } from "./event-mapper";
import { SessionIndex, type SessionIndexEntry } from "./session-index";

type SpawnFunction = typeof spawnChildProcess;

interface RuntimeSession {
  readonly relaySessionId: string;
  readonly provider: InteractiveProvider;
  readonly cwd: string;
  readonly createdAt: string;
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
  closed: boolean;
  exitPromise: Promise<void>;
  resolveExit: () => void;
}

export interface ClaudeRuntimeAdapterOptions {
  readonly providers: Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>>;
  readonly sessionIndex: SessionIndex;
  readonly sendEvent: (message: CompanionEventMessage) => Promise<void> | void;
  readonly logger?: LoggerLike;
  readonly spawn?: SpawnFunction;
  readonly killGraceMs?: number;
}

export class ClaudeRuntimeAdapter {
  private readonly logger: LoggerLike;
  private readonly spawn: SpawnFunction;
  private readonly activeByProviderSessionId = new Map<string, RuntimeSession>();
  private readonly activeByRelaySessionId = new Map<string, RuntimeSession>();
  private readonly killGraceMs: number;

  constructor(private readonly options: ClaudeRuntimeAdapterOptions) {
    this.logger = options.logger ?? console;
    this.spawn = options.spawn ?? spawnChildProcess;
    this.killGraceMs = options.killGraceMs ?? 5000;
  }

  async createSession(command: CreateSessionCommand): Promise<{ provider_session_id: string }> {
    const providerConfig = this.getProviderConfig(command.provider);
    const args = [
      "--output-format",
      "stream-json",
      "--print-session-id",
      "-p",
      command.prompt,
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
      args
    });

    const providerSessionId = await session.providerSessionIdPromise;
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
    this.spawnSession({
      relaySessionId: command.session_id,
      provider: indexed.provider,
      cwd: command.cwd,
      binary: providerConfig.binary,
      args: ["--resume", "--session-id", command.provider_session_id, "--output-format", "stream-json"],
      knownProviderSessionId: command.provider_session_id,
      indexEntry: {
        provider_session_id: command.provider_session_id,
        cwd: command.cwd,
        provider: indexed.provider,
        created_at: indexed.created_at
      }
    });

    return {
      provider_session_id: command.provider_session_id
    };
  }

  async sendMessage(relaySessionId: string, text: string): Promise<void> {
    const session = this.requireActiveSession(relaySessionId);

    if (!session.child.stdin.writable) {
      throw new CompanionError(
        "session_not_found",
        `No running process for session ${session.providerSessionId ?? relaySessionId}`
      );
    }

    await new Promise<void>((resolve, reject) => {
      const payload = `${text}\n`;
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
  }

  async cancel(relaySessionId: string): Promise<void> {
    const session = this.requireActiveSession(relaySessionId);

    if (session.cancelled) {
      await session.exitPromise;
      return;
    }

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
    const relaySessionIds = Array.from(this.activeByRelaySessionId.keys());
    await Promise.allSettled(relaySessionIds.map((relaySessionId) => this.cancel(relaySessionId)));
  }

  getActiveSessionCount(): number {
    return this.activeByRelaySessionId.size;
  }

  private spawnSession(params: {
    readonly relaySessionId: string;
    readonly provider: InteractiveProvider;
    readonly cwd: string;
    readonly binary: string;
    readonly args: string[];
    readonly knownProviderSessionId?: string;
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
      closed: false,
      exitPromise,
      resolveExit
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

    const mapped = mapRuntimeEvent(parsed);
    if (!mapped) {
      return;
    }

    if (mapped.kind === "provider_session_id") {
      if (!session.providerSessionId) {
        this.registerProviderSessionId(session, mapped.providerSessionId);
      }

      return;
    }

    if (mapped.eventType === "session_result") {
      session.resultEmitted = true;
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

  private async handleClose(session: RuntimeSession, code: number | null, signal: NodeJS.Signals | null): Promise<void> {
    session.closed = true;
    session.stdout.close();

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

    if (code === 0) {
      if (!session.resultEmitted) {
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
        created_at: session.createdAt
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
