import { randomUUID } from "node:crypto";

import type { RelayDatabase } from "../db/init";

type LoggerLike = {
  readonly error?: (...args: unknown[]) => void;
};

export type AuditLogPayload = {
  readonly session_id?: string;
  readonly host_id?: string;
  readonly detail?: unknown;
};

export class AuditLogger {
  constructor(
    private readonly db: RelayDatabase,
    private readonly logger: LoggerLike
  ) {}

  write(action: string, payload: AuditLogPayload = {}): void {
    try {
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
          payload.detail === undefined ? null : JSON.stringify(payload.detail),
          new Date().toISOString()
        );
    } catch (error) {
      this.logger.error?.("Failed to write audit log", error);
    }
  }
}
