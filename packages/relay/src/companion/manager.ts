import type {
  CompanionCommand,
  CompanionHeartbeatMessage,
  CompanionMessage
} from "@imbot/wire";
import { randomUUID } from "node:crypto";
import type WebSocket from "ws";

import type { RelayConfig } from "../config";
import type { RelayDatabase } from "../db/init";
import { RelayError } from "../errors";
import { WsHub } from "../ws/hub";

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
  private isShuttingDown = false;

  constructor(
    private readonly config: RelayConfig,
    private readonly db: RelayDatabase,
    private readonly hub: WsHub,
    private readonly logger: LoggerLike
  ) {}

  registerConnection(hostId: string, ws: WebSocket): void {
    const replaced = this.hub.setCompanionClient(hostId, ws);
    if (replaced && replaced !== ws) {
      replaced.close(4003, "replaced");
    }

    this.pendingByHost.set(hostId, this.pendingByHost.get(hostId) ?? new Map());
    this.upsertHost(hostId, "online");
    this.hub.broadcastHostStatus(hostId, "online");
  }

  unregisterConnection(hostId: string, ws: WebSocket): void {
    this.hub.removeCompanionClient(hostId, ws);

    if (this.isShuttingDown) {
      this.rejectPendingForHost(hostId, new RelayError("host_offline", "Relay shutting down"));
      return;
    }

    this.upsertHost(hostId, "offline");
    this.rejectPendingForHost(hostId, new RelayError("host_offline", "Companion disconnected"));
    this.hub.broadcastHostStatus(hostId, "offline");
  }

  handleHeartbeat(hostId: string, _message: CompanionHeartbeatMessage): void {
    this.upsertHost(hostId, "online");
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
    return this.hub.getCompanionClient(hostId) != null;
  }

  hasOnlineCompanion(): boolean {
    return this.hub.hasOnlineCompanion();
  }

  async sendCommand(hostId: string, command: CompanionCommand): Promise<CompanionMessage> {
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

    ws.send(JSON.stringify(command));
    return ackPromise;
  }

  shutdown(): void {
    this.isShuttingDown = true;
    for (const hostId of this.pendingByHost.keys()) {
      this.rejectPendingForHost(hostId, new RelayError("host_offline", "Relay shutting down"));
    }
  }

  createRequestId(): string {
    return randomUUID();
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

  private upsertHost(hostId: string, status: "online" | "offline"): void {
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
        ) VALUES (?, ?, 'macbook', ?, ?, ?, ?)
        `
      )
      .run(hostId, hostId, status, now, now, now);

    this.db
      .prepare(
        `
        UPDATE hosts
        SET status = ?, last_heartbeat_at = ?, updated_at = ?
        WHERE id = ?
        `
      )
      .run(status, now, now, hostId);
  }
}
