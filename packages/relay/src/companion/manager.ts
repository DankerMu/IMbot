import type {
  ErrorCode,
  CompanionCommand,
  CompanionHeartbeatMessage,
  CompanionMessage,
  Provider
} from "@imbot/wire";
import { ERROR_CODES, PROVIDERS } from "@imbot/wire";
import { randomUUID } from "node:crypto";
import type WebSocket from "ws";

import type { RelayConfig } from "../config";
import type { RelayDatabase } from "../db/init";
import { RelayError } from "../errors";
import type { PushAdapter } from "../push/fcm-adapter";
import { WsHub } from "../ws/hub";
import { AuditLogger } from "../audit/logger";

type PendingAck = {
  readonly resolve: (message: CompanionMessage) => void;
  readonly reject: (error: unknown) => void;
  readonly timeout: NodeJS.Timeout;
};

type LoggerLike = {
  readonly debug?: (...args: unknown[]) => void;
  readonly error: (...args: unknown[]) => void;
  readonly info?: (...args: unknown[]) => void;
  readonly warn: (...args: unknown[]) => void;
};

export class CompanionManager {
  private readonly pendingByHost = new Map<string, Map<string, PendingAck>>();
  private readonly providersByHost = new Map<string, Provider[]>();
  private readonly awaitingOnlineAudit = new Set<string>();
  private readonly staleHeartbeatTimer: NodeJS.Timeout;
  private isShuttingDown = false;

  constructor(
    private readonly config: RelayConfig,
    private readonly db: RelayDatabase,
    private readonly hub: WsHub,
    private readonly logger: LoggerLike,
    private readonly options?: {
      readonly auditLogger?: AuditLogger;
      readonly onHostDisconnected?: (hostId: string) => Promise<void> | void;
      readonly pushAdapter?: PushAdapter;
    }
  ) {
    this.staleHeartbeatTimer = setInterval(() => {
      void this.markStaleHostsOffline().catch((error) => {
        this.logger.error(error);
      });
    }, this.config.heartbeatIntervalMs);
    this.staleHeartbeatTimer.unref?.();
  }

  registerConnection(hostId: string, ws: WebSocket): void {
    const replaced = this.hub.setCompanionClient(hostId, ws);
    if (replaced && replaced !== ws) {
      replaced.close(4003, "replaced");
    }

    this.pendingByHost.set(hostId, this.pendingByHost.get(hostId) ?? new Map());
    const previousStatus = this.getStoredHostStatus(hostId);
    this.ensureHost(hostId);
    if (previousStatus !== "online") {
      this.awaitingOnlineAudit.add(hostId);
    } else {
      this.awaitingOnlineAudit.delete(hostId);
    }
  }

  unregisterConnection(hostId: string, ws: WebSocket): void {
    const removedActiveConnection = this.hub.removeCompanionClient(hostId, ws);
    if (!removedActiveConnection) {
      return;
    }

    if (this.isShuttingDown) {
      this.rejectPendingForHost(hostId, new RelayError("host_offline", "Relay shutting down"));
      return;
    }

    void this.markHostOffline(hostId, "disconnect");
  }

  handleHeartbeat(hostId: string, message: CompanionHeartbeatMessage): void {
    const previousStatus = this.getStoredHostStatus(hostId);
    const providers = normalizeProviders(message.providers);
    this.providersByHost.set(hostId, providers);
    this.markHostOnline(hostId);

    if (previousStatus !== "online") {
      this.hub.broadcastHostStatus(hostId, "online");
    }

    if (this.awaitingOnlineAudit.has(hostId) || previousStatus !== "online") {
      this.awaitingOnlineAudit.delete(hostId);
      this.options?.auditLogger?.write("host.online", {
        host_id: hostId,
        detail: {
          providers: [...providers]
        }
      });
    }
  }

  handleAck(hostId: string, message: CompanionMessage): void {
    if (message.type !== "ack") {
      return;
    }

    const pendingForHost = this.pendingByHost.get(hostId);
    const pending = pendingForHost?.get(message.req_id);

    if (!pending) {
      this.logger.warn(`Received unexpected companion ack for host ${hostId}`);
      return;
    }

    clearTimeout(pending.timeout);
    pendingForHost?.delete(message.req_id);
    pending.resolve(message);
  }

  isOnline(hostId: string): boolean {
    return this.hub.getCompanionClient(hostId) != null && this.getStoredHostStatus(hostId) === "online";
  }

  hasOnlineCompanion(): boolean {
    const row = this.db
      .prepare("SELECT COUNT(*) AS count FROM hosts WHERE type = 'macbook' AND status = 'online'")
      .get() as { count: number };
    return row.count > 0;
  }

  getDeclaredProviders(hostId: string): Provider[] {
    return [...(this.providersByHost.get(hostId) ?? [])];
  }

  async browseDirectory(
    hostId: string,
    requestedPath: string,
    options?: {
      readonly roots?: readonly string[];
    }
  ): Promise<BrowseDirectoryResult> {
    const ack = await this.sendCommand(hostId, {
      cmd: "browse_directory",
      req_id: this.createRequestId(),
      path: requestedPath,
      roots: options?.roots
    });

    return this.extractBrowseResult(ack);
  }

  async sendCommand(hostId: string, command: CompanionCommand): Promise<CompanionMessage> {
    if (!this.isOnline(hostId)) {
      throw new RelayError("host_offline", `Companion host ${hostId} is offline`);
    }
    const ws = this.hub.getCompanionClient(hostId);
    if (!ws) {
      throw new RelayError("host_offline", `Companion host ${hostId} is offline`);
    }

    const pendingForHost = this.pendingByHost.get(hostId) ?? new Map<string, PendingAck>();
    this.pendingByHost.set(hostId, pendingForHost);

    const ackPromise = new Promise<CompanionMessage>((resolve, reject) => {
      const timeout = setTimeout(() => {
        pendingForHost.delete(command.req_id);
        reject(new RelayError("command_timeout", `Command ${command.req_id} timed out`));
      }, this.config.companionTimeoutMs);

      timeout.unref?.();

      pendingForHost.set(command.req_id, {
        resolve,
        reject,
        timeout
      });
    });

    try {
      ws.send(JSON.stringify(command), (error) => {
        if (!error) {
          return;
        }

        this.rejectPendingCommand(hostId, command.req_id, this.createSendError(error));
      });
    } catch (error) {
      this.rejectPendingCommand(hostId, command.req_id, this.createSendError(error));
    }

    return ackPromise;
  }

  shutdown(): void {
    this.isShuttingDown = true;
    clearInterval(this.staleHeartbeatTimer);
    for (const hostId of this.pendingByHost.keys()) {
      this.rejectPendingForHost(hostId, new RelayError("host_offline", "Relay shutting down"));
    }
  }

  createRequestId(): string {
    return randomUUID();
  }

  async markStaleHostsOffline(now = new Date()): Promise<string[]> {
    const cutoff = new Date(now.getTime() - this.config.heartbeatStaleMs).toISOString();
    const rows = this.db
      .prepare(
        `
        SELECT id
        FROM hosts
        WHERE type = 'macbook' AND status = 'online' AND last_heartbeat_at IS NOT NULL AND last_heartbeat_at < ?
        `
      )
      .all(cutoff) as Array<{ id: string }>;

    for (const row of rows) {
      await this.markHostOffline(row.id, "heartbeat_timeout");
    }

    return rows.map((row) => row.id);
  }

  private rejectPendingForHost(hostId: string, error: unknown): void {
    const pendingForHost = this.pendingByHost.get(hostId);
    if (!pendingForHost) {
      return;
    }

    for (const pending of pendingForHost.values()) {
      clearTimeout(pending.timeout);
      pending.reject(error);
    }

    pendingForHost.clear();
  }

  private rejectPendingCommand(hostId: string, requestId: string, error: unknown): void {
    const pendingForHost = this.pendingByHost.get(hostId);
    const pending = pendingForHost?.get(requestId);
    if (!pending) {
      return;
    }

    clearTimeout(pending.timeout);
    pendingForHost?.delete(requestId);
    pending.reject(error);
  }

  private createSendError(error: unknown): RelayError {
    const detail = error instanceof Error && error.message ? `: ${error.message}` : "";
    return new RelayError("provider_unreachable", `Failed to send companion command${detail}`);
  }

  private ensureHost(hostId: string): void {
    const now = new Date().toISOString();
    this.db
      .prepare(
        `
        INSERT OR IGNORE INTO hosts (
          id,
          name,
          type,
          status,
          last_heartbeat_at,
          created_at,
          updated_at
        ) VALUES (?, ?, 'macbook', 'offline', NULL, ?, ?)
        `
      )
      .run(hostId, hostId, now, now);
  }

  private markHostOnline(hostId: string): void {
    const now = new Date().toISOString();

    this.ensureHost(hostId);
    this.db
      .prepare(
        `
        UPDATE hosts
        SET status = ?, last_heartbeat_at = ?, updated_at = ?
        WHERE id = ?
        `
      )
      .run("online", now, now, hostId);
  }

  private getStoredHostStatus(hostId: string): "online" | "offline" | null {
    const row = this.db.prepare("SELECT status FROM hosts WHERE id = ?").get(hostId) as
      | { status: "online" | "offline" }
      | undefined;

    return row?.status ?? null;
  }

  private getStoredHostName(hostId: string): string | null {
    const row = this.db.prepare("SELECT name FROM hosts WHERE id = ?").get(hostId) as
      | { name: string }
      | undefined;

    return row?.name ?? null;
  }

  private async markHostOffline(hostId: string, reason: "disconnect" | "heartbeat_timeout"): Promise<void> {
    const previousStatus = this.getStoredHostStatus(hostId);
    this.awaitingOnlineAudit.delete(hostId);
    this.db
      .prepare(
        `
        UPDATE hosts
        SET status = 'offline', updated_at = ?
        WHERE id = ? AND status != 'offline'
        `
      )
      .run(new Date().toISOString(), hostId);

    this.rejectPendingForHost(
      hostId,
      new RelayError(
        "host_offline",
        reason === "disconnect" ? "Companion disconnected" : "Companion heartbeat timed out"
      )
    );

    if (previousStatus === "online") {
      this.options?.auditLogger?.write("host.offline", {
        host_id: hostId,
        detail: {
          reason
        }
      });
      this.hub.broadcastHostStatus(hostId, "offline");
      const hostName = this.getStoredHostName(hostId) ?? hostId;
      void this.options?.pushAdapter?.notifyHostOffline(hostId, hostName);
      const disconnectResult = this.options?.onHostDisconnected?.(hostId);
      if (disconnectResult && typeof (disconnectResult as Promise<void>).catch === "function") {
        void (disconnectResult as Promise<void>).catch((error) => {
          this.logger.error(error);
        });
      }
    }
  }

  private extractBrowseResult(message: CompanionMessage): BrowseDirectoryResult {
    const data = this.extractAckData(message);
    if (!data || typeof data !== "object" || Array.isArray(data)) {
      throw new RelayError("provider_unreachable", "Companion browse response was malformed");
    }

    const responsePath = "path" in data && typeof data.path === "string" ? data.path : null;
    const directories = "directories" in data && Array.isArray(data.directories) ? data.directories : null;
    if (!responsePath || !directories) {
      throw new RelayError("provider_unreachable", "Companion browse response was malformed");
    }

    return {
      path: responsePath,
      directories: directories.flatMap((entry) => {
        if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
          return [];
        }

        const name = "name" in entry && typeof entry.name === "string" ? entry.name : null;
        const targetPath = "path" in entry && typeof entry.path === "string" ? entry.path : null;
        if (!name || !targetPath) {
          return [];
        }

        return [
          {
            name,
            path: targetPath
          }
        ];
      })
    };
  }

  private extractAckData(message: CompanionMessage): unknown {
    if (message.type !== "ack") {
      throw new RelayError("provider_unreachable", "Unexpected companion response");
    }

    if (message.status === "error") {
      throw new RelayError(
        this.normalizeErrorCode(message.error_code),
        message.message || "Companion command failed"
      );
    }

    return message.data;
  }

  private normalizeErrorCode(errorCode: string | undefined): ErrorCode {
    if (errorCode && ERROR_CODES.includes(errorCode as ErrorCode)) {
      return errorCode as ErrorCode;
    }

    return "provider_unreachable";
  }
}

function normalizeProviders(providers: readonly string[]): Provider[] {
  const provided = new Set(providers);
  return PROVIDERS.filter((provider) => provided.has(provider));
}

export interface BrowseDirectoryResult {
  readonly path: string;
  readonly directories: Array<{
    readonly name: string;
    readonly path: string;
  }>;
}
