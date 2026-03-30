import { EventEmitter } from "node:events";

import { ExponentialBackoff, type CompanionMessage } from "@imbot/wire";
import WebSocket from "ws";
import type { RawData } from "ws";

import { EventBuffer } from "./event-buffer";
import type { LoggerLike } from "./types";

export interface RelayClientBackoffOptions {
  readonly baseMs?: number;
  readonly maxMs?: number;
  readonly jitterMs?: number;
}

export interface RelayClientOptions {
  readonly relayUrl: string;
  readonly token: string;
  readonly hostId: string;
  readonly logger?: LoggerLike;
  readonly backoff?: RelayClientBackoffOptions;
  readonly createWebSocket?: (url: string) => WebSocket;
}

export class RelayClient extends EventEmitter {
  private readonly logger: LoggerLike;
  private readonly backoff: ExponentialBackoff;
  private readonly createWebSocket: (url: string) => WebSocket;
  private readonly eventBuffer = new EventBuffer();
  private ws: WebSocket | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private closedByUser = false;

  constructor(private readonly options: RelayClientOptions) {
    super();
    this.logger = options.logger ?? console;
    this.createWebSocket = options.createWebSocket ?? ((url) => new WebSocket(url));
    this.backoff = new ExponentialBackoff(
      options.backoff?.baseMs ?? 1000,
      options.backoff?.maxMs ?? 30000,
      options.backoff?.jitterMs ?? 1000
    );
  }

  connect(): void {
    this.closedByUser = false;
    this.openSocket();
  }

  close(): void {
    this.closedByUser = true;
    this.clearReconnectTimer();
    this.eventBuffer.clear();

    const current = this.ws;
    this.ws = null;

    if (current && (current.readyState === WebSocket.CONNECTING || current.readyState === WebSocket.OPEN)) {
      current.close();
    }
  }

  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  send(message: CompanionMessage): void {
    if (!this.sendNow(message)) {
      this.eventBuffer.push(message);
    }
  }

  private sendNow(message: CompanionMessage): boolean {
    const current = this.ws;
    if (!current || current.readyState !== WebSocket.OPEN) {
      return false;
    }

    current.send(JSON.stringify(message), (error) => {
      if (!error) {
        return;
      }

      if (this.ws !== current || current.readyState !== WebSocket.OPEN) {
        this.eventBuffer.push(message);
      }

      this.logger.warn?.(
        `Failed to send outbound ${message.type} message: ${error instanceof Error ? error.message : "unknown error"}`
      );
    });
    return true;
  }

  private flushBufferedEvents(): void {
    const buffered = this.eventBuffer.flush();
    if (buffered.length === 0) {
      return;
    }

    this.logger.info?.(`flushing ${buffered.length} buffered companion message(s)`);
    for (const message of buffered) {
      this.send(message);
    }
  }

  private openSocket(): void {
    if (this.closedByUser) {
      return;
    }

    if (this.ws && (this.ws.readyState === WebSocket.CONNECTING || this.ws.readyState === WebSocket.OPEN)) {
      return;
    }

    this.clearReconnectTimer();
    const socketUrl = buildSocketUrl(this.options.relayUrl, this.options.token, this.options.hostId);
    const socket = this.createWebSocket(socketUrl);
    this.ws = socket;

    socket.on("open", () => {
      if (this.ws !== socket) {
        return;
      }

      this.backoff.reset();
      this.logger.info?.(`connected to relay ${socketUrl}`);
      this.emit("connected");
      this.flushBufferedEvents();
    });

    socket.on("message", (raw: RawData, isBinary: boolean) => {
      if (isBinary) {
        this.logger.warn?.("Discarding unexpected binary frame from relay");
        return;
      }

      const parsed = safeParseJson(raw, this.logger);
      if (parsed == null) {
        return;
      }

      this.emit("message", parsed);
    });

    socket.on("error", (error) => {
      if (this.ws !== socket) {
        return;
      }

      this.logger.warn?.(`relay websocket error: ${error.message}`);
      this.emit("error", error);
    });

    socket.on("close", (code, reasonBuffer) => {
      if (this.ws === socket) {
        this.ws = null;
      }

      const reason = reasonBuffer.toString();
      this.emit("disconnected", code, reason);
      this.logger.warn?.(`relay websocket closed code=${code} reason=${reason || "none"}`);

      if (!this.closedByUser) {
        this.scheduleReconnect();
      }
    });
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer || this.closedByUser) {
      return;
    }

    const delay = this.backoff.nextDelay();
    const attemptNumber = this.backoff.attempts;

    this.logger.info?.(`retrying relay connection attempt ${attemptNumber} in ${delay}ms`);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, delay);
    this.reconnectTimer.unref?.();
  }

  private clearReconnectTimer(): void {
    if (!this.reconnectTimer) {
      return;
    }

    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
  }
}

function buildSocketUrl(relayUrl: string, token: string, hostId: string): string {
  const url = new URL("/v1/companion", relayUrl);
  url.searchParams.set("token", token);
  url.searchParams.set("host_id", hostId);
  return url.toString();
}

function safeParseJson(raw: RawData, logger: LoggerLike): unknown | null {
  const text = raw.toString();
  try {
    return JSON.parse(text) as unknown;
  } catch (error) {
    const truncated = text.length > 200 ? `${text.slice(0, 200)}...` : text;
    logger.warn?.(
      `Discarding invalid JSON frame from relay: ${error instanceof Error ? error.message : "parse error"} raw=${truncated}`
    );
    return null;
  }
}
