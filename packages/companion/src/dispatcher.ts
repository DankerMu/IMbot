import type { CompanionAckError, CompanionAckOk, CompanionCommand } from "@imbot/wire";

import { CompanionError, type LoggerLike, toCompanionError } from "./types";

type CommandHandler = (command: CompanionCommand) => Promise<unknown> | unknown;

export interface CommandDispatcherOptions {
  readonly sendAck: (message: CompanionAckOk | CompanionAckError) => Promise<void> | void;
  readonly logger?: LoggerLike;
  readonly timeoutMs?: number;
}

export class CommandDispatcher {
  private readonly handlers = new Map<string, CommandHandler>();
  private readonly logger: LoggerLike;
  private readonly timeoutMs: number;

  constructor(private readonly options: CommandDispatcherOptions) {
    this.logger = options.logger ?? console;
    this.timeoutMs = options.timeoutMs ?? 60000;
  }

  register<TCmd extends CompanionCommand["cmd"]>(
    cmd: TCmd,
    handler: (command: Extract<CompanionCommand, { cmd: TCmd }>) => Promise<unknown> | unknown
  ): void {
    this.handlers.set(cmd, handler as CommandHandler);
  }

  async dispatch(message: unknown): Promise<void> {
    const record = asRecord(message);
    const reqId = typeof record?.req_id === "string" ? record.req_id : null;
    const cmd = typeof record?.cmd === "string" ? record.cmd : null;

    if (!cmd) {
      if (reqId) {
        await this.sendError(reqId, "invalid_request", "Missing cmd field");
      } else {
        this.logger.warn?.("Discarding relay command without cmd field");
      }

      return;
    }

    if (!reqId) {
      this.logger.warn?.(`Discarding ${cmd} command without req_id`);
      return;
    }

    const handler = this.handlers.get(cmd);
    if (!handler) {
      await this.sendError(reqId, "unknown_command", `Unknown command: ${cmd}`);
      return;
    }

    try {
      const result = await withTimeout(Promise.resolve(handler(record as CompanionCommand)), this.timeoutMs);
      await Promise.resolve(
        this.options.sendAck({
          type: "ack",
          req_id: reqId,
          status: "ok",
          ...(result === undefined ? {} : { data: result })
        })
      );
    } catch (error) {
      const normalized =
        error instanceof CompanionError
          ? error
          : toCompanionError(error, "handler_failed", "Command handler failed");
      this.logger.error?.(error);
      await this.sendError(reqId, normalized.code, normalized.message);
    }
  }

  private async sendError(reqId: string, code: string, message: string): Promise<void> {
    await Promise.resolve(
      this.options.sendAck({
        type: "ack",
        req_id: reqId,
        status: "error",
        error_code: code,
        message
      })
    );
  }
}

async function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  let timeoutHandle: NodeJS.Timeout | null = null;

  try {
    return await Promise.race([
      promise,
      new Promise<T>((_, reject) => {
        timeoutHandle = setTimeout(() => {
          reject(new CompanionError("handler_timeout", `Handler exceeded ${Math.floor(timeoutMs / 1000)}s timeout`));
        }, timeoutMs);
        timeoutHandle.unref?.();
      })
    ]);
  } finally {
    if (timeoutHandle) {
      clearTimeout(timeoutHandle);
    }
  }
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }

  return value as Record<string, unknown>;
}
