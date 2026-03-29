import { randomUUID } from "node:crypto";

import type { ServerMessage } from "@imbot/wire";
import WebSocket from "ws";

type TrackedConnection = {
  readonly missedPongs: { value: number };
};

export class WsHub {
  private readonly androidClients = new Map<string, WebSocket>();
  private readonly androidClientIds = new Map<WebSocket, string>();
  private readonly companionClients = new Map<string, WebSocket>();
  private readonly subscriptions = new Map<string, Set<string>>();
  private readonly trackedConnections = new Map<WebSocket, TrackedConnection>();
  private readonly keepaliveTimer: NodeJS.Timeout;

  constructor(private readonly pingIntervalMs: number) {
    this.keepaliveTimer = setInterval(() => {
      this.tickKeepalive();
    }, pingIntervalMs);
    this.keepaliveTimer.unref?.();
  }

  addAndroidClient(ws: WebSocket): string {
    const existingId = this.androidClientIds.get(ws);
    if (existingId) {
      return existingId;
    }

    const clientId = randomUUID();
    this.androidClients.set(clientId, ws);
    this.androidClientIds.set(ws, clientId);
    this.trackConnection(ws);
    return clientId;
  }

  removeAndroidClient(ws: WebSocket): void {
    const clientId = this.androidClientIds.get(ws);
    if (!clientId) {
      return;
    }

    this.androidClientIds.delete(ws);
    this.androidClients.delete(clientId);
    this.untrackConnection(ws);

    for (const subscribers of this.subscriptions.values()) {
      subscribers.delete(clientId);
    }
  }

  setCompanionClient(hostId: string, ws: WebSocket): WebSocket | undefined {
    const existing = this.companionClients.get(hostId);

    this.companionClients.set(hostId, ws);
    this.trackConnection(ws);

    return existing;
  }

  getCompanionClient(hostId: string): WebSocket | undefined {
    return this.companionClients.get(hostId);
  }

  removeCompanionClient(hostId: string, ws: WebSocket): void {
    const current = this.companionClients.get(hostId);
    if (current !== ws) {
      return;
    }

    this.companionClients.delete(hostId);
    this.untrackConnection(ws);
  }

  hasOnlineCompanion(): boolean {
    return this.companionClients.size > 0;
  }

  subscribe(clientId: string, sessionId: string): void {
    const subscribers = this.subscriptions.get(sessionId) ?? new Set<string>();
    subscribers.add(clientId);
    this.subscriptions.set(sessionId, subscribers);
  }

  unsubscribe(clientId: string, sessionId: string): void {
    const subscribers = this.subscriptions.get(sessionId);
    if (!subscribers) {
      return;
    }

    subscribers.delete(clientId);
    if (subscribers.size === 0) {
      this.subscriptions.delete(sessionId);
    }
  }

  broadcastToSession(sessionId: string, message: ServerMessage): void {
    const subscribers = this.subscriptions.get(sessionId);
    if (!subscribers || subscribers.size === 0) {
      return;
    }

    for (const clientId of subscribers) {
      const ws = this.androidClients.get(clientId);
      if (!ws) {
        continue;
      }

      this.sendJson(ws, message);
    }
  }

  broadcastHostStatus(hostId: string, status: "online" | "offline"): void {
    this.broadcastAll({
      type: "host_status",
      host_id: hostId,
      status
    });
  }

  broadcastAll(message: ServerMessage): void {
    for (const ws of this.androidClients.values()) {
      this.sendJson(ws, message);
    }
  }

  closeAll(code: number, reason: string): void {
    clearInterval(this.keepaliveTimer);

    for (const ws of this.trackedConnections.keys()) {
      if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
        ws.close(code, reason);
      }
    }

    this.trackedConnections.clear();
    this.androidClients.clear();
    this.androidClientIds.clear();
    this.companionClients.clear();
    this.subscriptions.clear();
  }

  private sendJson(ws: WebSocket, message: ServerMessage): void {
    if (ws.readyState !== WebSocket.OPEN) {
      return;
    }

    ws.send(JSON.stringify(message));
  }

  private trackConnection(ws: WebSocket): void {
    if (this.trackedConnections.has(ws)) {
      return;
    }

    const state: TrackedConnection = {
      missedPongs: { value: 0 }
    };

    ws.on("pong", () => {
      state.missedPongs.value = 0;
    });

    this.trackedConnections.set(ws, state);
  }

  private untrackConnection(ws: WebSocket): void {
    this.trackedConnections.delete(ws);
  }

  private tickKeepalive(): void {
    for (const [ws, state] of this.trackedConnections.entries()) {
      if (ws.readyState !== WebSocket.OPEN) {
        this.trackedConnections.delete(ws);
        continue;
      }

      if (state.missedPongs.value >= 2) {
        ws.close(1001, "Going Away");
        this.trackedConnections.delete(ws);
        continue;
      }

      state.missedPongs.value += 1;
      ws.ping();
    }
  }
}
