import { ERROR_CODES, ExponentialBackoff, type CompanionEventMessage, type ErrorCode } from "@imbot/wire";
import { randomUUID } from "node:crypto";
import WebSocket, { type RawData } from "ws";

import type { RelayConfig } from "../config";
import { RelayError } from "../errors";
import type { WsHub } from "../ws/hub";
import { extractOpenClawSessionKey, translateOpenClawEvent } from "./event-translator";
import type {
  OpenClawConnectResponse,
  OpenClawEventFrame,
  OpenClawFrame,
  OpenClawResponseFrame
} from "./types";

type PendingRequest = {
  readonly method: string;
  readonly resolve: (payload: unknown) => void;
  readonly reject: (error: unknown) => void;
  readonly timeout: NodeJS.Timeout;
};

type LoggerLike = {
  readonly debug?: (...args: unknown[]) => void;
  readonly error: (...args: unknown[]) => void;
  readonly info?: (...args: unknown[]) => void;
  readonly warn: (...args: unknown[]) => void;
};

type OpenClawBridgeDeps = {
  readonly hub: WsHub;
  readonly logger: LoggerLike;
  readonly onRelayEvent: (message: CompanionEventMessage) => Promise<void>;
};

export class OpenClawBridge {
  private ws: WebSocket | null = null;
  private readonly pending = new Map<string, PendingRequest>();
  private readonly relayToOpenClaw = new Map<string, string>();
  private readonly openClawToRelay = new Map<string, string>();
  private readonly sessionQueues = new Map<string, Promise<void>>();
  private readonly backoff = new ExponentialBackoff(1000, 30000, 500);
  private reconnectTimer: NodeJS.Timeout | null = null;
  private connectKickoffTimer: NodeJS.Timeout | null = null;
  private available = false;
  private connectRequested = false;
  private closed = false;
  private defaultSessionKey: string | null = null;
  private lastDisconnectedSessionCount = 0;

  constructor(
    private readonly config: RelayConfig,
    private readonly deps: OpenClawBridgeDeps
  ) {}

  connect(): void {
    if (this.closed) {
      return;
    }

    this.clearReconnectTimer();
    this.clearConnectKickoffTimer();
    this.connectRequested = false;
    this.cleanupSocket();

    try {
      this.ws = new WebSocket(this.config.openClawUrl);
    } catch (error) {
      this.deps.logger.warn?.("Failed to create OpenClaw bridge websocket", error);
      this.handleDisconnect(new RelayError("provider_unreachable", "Failed to connect to OpenClaw gateway"));
      return;
    }

    this.ws.on("open", () => {
      this.deps.logger.info?.(`OpenClaw bridge socket opened to ${this.config.openClawUrl}`);
      this.connectKickoffTimer = setTimeout(() => {
        void this.sendConnectRequest().catch((error) => {
          this.deps.logger.warn?.("OpenClaw connect request failed", error);
        });
      }, 50);
      this.connectKickoffTimer.unref?.();
    });

    this.ws.on("message", (raw) => {
      void this.handleRawMessage(raw).catch((error) => {
        this.deps.logger.error(error);
      });
    });

    this.ws.on("error", (error) => {
      this.deps.logger.warn?.("OpenClaw bridge websocket error", error);
    });

    this.ws.on("close", () => {
      this.handleDisconnect(new RelayError("provider_unreachable", "OpenClaw gateway disconnected"));
    });
  }

  shutdown(): void {
    this.closed = true;
    this.clearReconnectTimer();
    this.clearConnectKickoffTimer();
    this.rejectAllPending(new RelayError("provider_unreachable", "Relay shutting down"));
    this.cleanupSocket();
    this.setAvailability(false);
  }

  isAvailable(): boolean {
    return this.available && this.ws?.readyState === WebSocket.OPEN;
  }

  async createSession(
    relaySessionId: string,
    cwd: string,
    prompt: string,
    options?: {
      readonly model?: string | null;
      readonly permissionMode?: string | null;
    }
  ): Promise<{ sessionKey: string }> {
    this.ensureAvailable();

    const payload = await this.request("session.create", {
      relay_session_id: relaySessionId,
      session_id: relaySessionId,
      cwd,
      prompt,
      model: options?.model ?? undefined,
      permission_mode: options?.permissionMode ?? undefined
    });

    const sessionKey = this.extractAckSessionKey(payload);
    if (!sessionKey) {
      throw new RelayError("provider_unreachable", "OpenClaw gateway did not return a session key");
    }

    this.setSessionMapping(relaySessionId, sessionKey);
    return { sessionKey };
  }

  async resumeSession(relaySessionId: string, openClawSessionKey: string): Promise<void> {
    if (!this.isAvailable()) {
      throw new RelayError("provider_unreachable", "OpenClaw gateway is offline");
    }

    await this.request("session.resume", {
      relay_session_id: relaySessionId,
      session_id: relaySessionId,
      session_key: openClawSessionKey,
      sessionKey: openClawSessionKey
    });

    this.setSessionMapping(relaySessionId, openClawSessionKey);
  }

  async sendMessage(relaySessionId: string, text: string): Promise<void> {
    this.ensureAvailable();

    const sessionKey = this.getMappedSessionKey(relaySessionId);
    try {
      await this.request("message.send", {
        session_key: sessionKey,
        sessionKey: sessionKey,
        text
      });
    } catch (error) {
      if (this.isUnsupportedMethod(error)) {
        await this.request("chat.send", {
          session_key: sessionKey,
          sessionKey: sessionKey,
          message: text,
          text,
          timeoutMs: this.config.companionTimeoutMs
        });
        return;
      }

      throw error;
    }
  }

  async cancelSession(relaySessionId: string): Promise<void> {
    const sessionKey = this.relayToOpenClaw.get(relaySessionId);
    if (!sessionKey) {
      return;
    }

    if (!this.isAvailable()) {
      this.clearSessionMappingByRelaySessionId(relaySessionId);
      return;
    }

    try {
      await this.request("session.cancel", {
        session_key: sessionKey,
        sessionKey: sessionKey
      });
    } catch (error) {
      if (!this.isUnsupportedMethod(error)) {
        throw error;
      }

      await this.request("chat.abort", {
        session_key: sessionKey,
        sessionKey: sessionKey
      });
    }

    this.clearSessionMappingByRelaySessionId(relaySessionId);
  }

  private async sendConnectRequest(): Promise<void> {
    if (this.connectRequested) {
      return;
    }

    const socket = this.ws;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return;
    }

    this.connectRequested = true;

    try {
      const hadReconnectAttempts = this.backoff.attempts > 0 || this.lastDisconnectedSessionCount > 0;
      const response = (await this.request(
        "connect",
        {
          minProtocol: 3,
          maxProtocol: 3,
          client: {
            id: "gateway-client",
            displayName: "IMbot Relay",
            version: "0.1.0",
            platform: process.platform,
            mode: "backend"
          },
          role: "operator",
          scopes: ["operator.admin", "operator.approvals", "operator.pairing"],
          auth: this.config.openClawToken ? { token: this.config.openClawToken } : undefined
        },
        10000,
        { allowDisconnected: true }
      )) as OpenClawConnectResponse;

      this.defaultSessionKey = response.snapshot?.sessionDefaults?.mainSessionKey ?? null;
      this.backoff.reset();
      this.setAvailability(true);

      if (hadReconnectAttempts) {
        this.logRecoveryStatus(response);
      }
    } catch (error) {
      this.connectRequested = false;
      this.deps.logger.warn?.("OpenClaw gateway handshake failed", error);
      this.cleanupSocket();
      this.handleDisconnect(new RelayError("provider_unreachable", "OpenClaw gateway handshake failed"));
    }
  }

  private async handleRawMessage(raw: RawData): Promise<void> {
    const frame = this.parseFrame(raw);
    if (!frame) {
      return;
    }

    if (frame.type === "res") {
      this.handleResponse(frame);
      return;
    }

    if (frame.type === "event") {
      let payload = frame.payload;
      if (payload == null && frame.payloadJSON) {
        try {
          payload = JSON.parse(frame.payloadJSON);
        } catch {
          this.deps.logger.warn?.("Failed to parse OpenClaw payloadJSON", frame.payloadJSON);
          return;
        }
      }

      if (frame.event === "connect.challenge") {
        this.clearConnectKickoffTimer();
        await this.sendConnectRequest();
        return;
      }

      await this.handleGatewayEvent({
        ...frame,
        payload
      });
    }
  }

  private handleResponse(frame: OpenClawResponseFrame): void {
    const pending = this.pending.get(frame.id);
    if (!pending) {
      this.deps.logger.warn?.(`Received unexpected OpenClaw response for ${frame.id}`);
      return;
    }

    clearTimeout(pending.timeout);
    this.pending.delete(frame.id);

    if (frame.ok) {
      pending.resolve(frame.payload);
      return;
    }

    pending.reject(this.createGatewayError(frame.error?.code, frame.error?.message, pending.method));
  }

  private async handleGatewayEvent(frame: OpenClawEventFrame & { payload: unknown }): Promise<void> {
    const sessionKey = extractOpenClawSessionKey(frame.payload);
    if (!sessionKey) {
      this.deps.logger.warn?.(`Received malformed OpenClaw event ${frame.event}`, frame.payload);
      return;
    }

    const relaySessionId = this.openClawToRelay.get(sessionKey);
    if (!relaySessionId) {
      this.deps.logger.warn?.(`Received event for unknown OpenClaw session_key ${sessionKey}`);
      return;
    }

    const translated = translateOpenClawEvent({
      event: frame.event,
      payload: frame.payload
    });
    if (!translated) {
      this.deps.logger.warn?.(`Unknown OpenClaw event type ${frame.event}`, frame.payload);
      return;
    }

    const previous = this.sessionQueues.get(sessionKey) ?? Promise.resolve();
    const next = previous
      .catch(() => undefined)
      .then(async () => {
        await this.deps.onRelayEvent({
          type: "event",
          session_id: relaySessionId,
          event_type: translated.type,
          payload: translated.payload
        });

        if (translated.type === "session_result" || translated.type === "session_error") {
          this.clearSessionMappingBySessionKey(sessionKey);
        }
      })
      .catch((error) => {
        this.deps.logger.error(error);
      })
      .finally(() => {
        if (this.sessionQueues.get(sessionKey) === next) {
          this.sessionQueues.delete(sessionKey);
        }
      });

    this.sessionQueues.set(sessionKey, next);
    await next;
  }

  private async request<T = unknown>(
    method: string,
    params: unknown,
    timeoutMs = this.config.companionTimeoutMs,
    options?: {
      readonly allowDisconnected?: boolean;
    }
  ): Promise<T> {
    if (!options?.allowDisconnected) {
      this.ensureAvailable();
    }

    const socket = this.ws;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      throw new RelayError("provider_unreachable", "OpenClaw gateway is offline");
    }

    const requestId = randomUUID();
    const frame = {
      type: "req",
      id: requestId,
      method,
      params
    } as const;

    const promise = new Promise<T>((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new RelayError("command_timeout", `OpenClaw command ${method} timed out`));
      }, timeoutMs);
      timeout.unref?.();

      this.pending.set(requestId, {
        method,
        resolve: (payload) => resolve(payload as T),
        reject,
        timeout
      });
    });

    try {
      socket.send(JSON.stringify(frame), (error) => {
        if (!error) {
          return;
        }

        this.rejectPendingRequest(
          requestId,
          new RelayError("provider_unreachable", `Failed to send OpenClaw command ${method}: ${error.message}`)
        );
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "unknown";
      this.rejectPendingRequest(
        requestId,
        new RelayError("provider_unreachable", `Failed to send OpenClaw command ${method}: ${message}`)
      );
    }

    return promise;
  }

  private rejectPendingRequest(requestId: string, error: unknown): void {
    const pending = this.pending.get(requestId);
    if (!pending) {
      return;
    }

    clearTimeout(pending.timeout);
    this.pending.delete(requestId);
    pending.reject(error);
  }

  private rejectAllPending(error: unknown): void {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timeout);
      pending.reject(error);
    }

    this.pending.clear();
  }

  private ensureAvailable(): void {
    if (!this.isAvailable()) {
      throw new RelayError("provider_unreachable", "OpenClaw gateway is offline");
    }
  }

  private getMappedSessionKey(relaySessionId: string): string {
    const sessionKey = this.relayToOpenClaw.get(relaySessionId);
    if (!sessionKey) {
      throw new RelayError("state_conflict", `No OpenClaw session mapping found for ${relaySessionId}`);
    }

    return sessionKey;
  }

  private extractAckSessionKey(payload: unknown): string | null {
    return extractOpenClawSessionKey(payload);
  }

  private setSessionMapping(relaySessionId: string, sessionKey: string): void {
    const existingSessionKey = this.relayToOpenClaw.get(relaySessionId);
    if (existingSessionKey) {
      this.openClawToRelay.delete(existingSessionKey);
    }

    const existingRelaySessionId = this.openClawToRelay.get(sessionKey);
    if (existingRelaySessionId) {
      this.relayToOpenClaw.delete(existingRelaySessionId);
    }

    this.relayToOpenClaw.set(relaySessionId, sessionKey);
    this.openClawToRelay.set(sessionKey, relaySessionId);
  }

  private clearSessionMappingByRelaySessionId(relaySessionId: string): void {
    const sessionKey = this.relayToOpenClaw.get(relaySessionId);
    if (!sessionKey) {
      return;
    }

    this.relayToOpenClaw.delete(relaySessionId);
    this.openClawToRelay.delete(sessionKey);
    this.sessionQueues.delete(sessionKey);
  }

  private clearSessionMappingBySessionKey(sessionKey: string): void {
    const relaySessionId = this.openClawToRelay.get(sessionKey);
    if (!relaySessionId) {
      return;
    }

    this.openClawToRelay.delete(sessionKey);
    this.relayToOpenClaw.delete(relaySessionId);
    this.sessionQueues.delete(sessionKey);
  }

  private setAvailability(nextAvailable: boolean): void {
    const previous = this.available;
    this.available = nextAvailable;

    if (previous === nextAvailable) {
      return;
    }

    this.deps.hub.broadcastHostStatus("relay-local", nextAvailable ? "online" : "offline");
  }

  private handleDisconnect(error: RelayError): void {
    if (this.closed) {
      return;
    }

    const activeSessionIds = Array.from(this.relayToOpenClaw.keys());
    this.lastDisconnectedSessionCount = activeSessionIds.length;
    this.clearConnectKickoffTimer();
    this.connectRequested = false;
    this.defaultSessionKey = null;
    this.setAvailability(false);
    this.rejectAllPending(error);

    if (activeSessionIds.length > 0) {
      for (const sessionId of activeSessionIds) {
        void this.deps
          .onRelayEvent({
            type: "event",
            session_id: sessionId,
            event_type: "session_error",
            payload: {
              error_code: "provider_unreachable",
              message: error.message
            }
          })
          .catch((dispatchError) => {
            this.deps.logger.error(dispatchError);
          });
      }

      this.relayToOpenClaw.clear();
      this.openClawToRelay.clear();
      this.sessionQueues.clear();
    }

    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    if (this.closed || this.reconnectTimer) {
      return;
    }

    const delay = this.backoff.nextDelay();
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
    this.reconnectTimer.unref?.();
  }

  private logRecoveryStatus(response: OpenClawConnectResponse): void {
    const gatewaySessionKey = response.snapshot?.sessionDefaults?.mainSessionKey;
    if (gatewaySessionKey) {
      const relaySessionId = this.openClawToRelay.get(gatewaySessionKey);
      const recoveryStatus = relaySessionId
        ? `matched relay session ${relaySessionId}`
        : "no relay mapping could be recovered";
      this.deps.logger.info?.(
        `OpenClaw bridge reconnected; gateway has an active session; ${recoveryStatus}`
      );
      this.lastDisconnectedSessionCount = 0;
      return;
    }

    if (this.lastDisconnectedSessionCount > 0) {
      this.deps.logger.info?.(
        `OpenClaw bridge reconnected; ${this.lastDisconnectedSessionCount} previously active session(s) could not be recovered`
      );
    } else {
      this.deps.logger.info?.("OpenClaw bridge reconnected");
    }

    this.lastDisconnectedSessionCount = 0;
  }

  private clearReconnectTimer(): void {
    if (!this.reconnectTimer) {
      return;
    }

    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
  }

  private clearConnectKickoffTimer(): void {
    if (!this.connectKickoffTimer) {
      return;
    }

    clearTimeout(this.connectKickoffTimer);
    this.connectKickoffTimer = null;
  }

  private cleanupSocket(): void {
    if (!this.ws) {
      return;
    }

    const socket = this.ws;
    this.ws = null;

    socket.removeAllListeners();
    socket.on("error", () => {
      // Ignore shutdown-time socket errors.
    });
    try {
      if (socket.readyState === WebSocket.CONNECTING) {
        socket.terminate();
      } else {
        socket.close();
      }
    } catch {
      // ignore cleanup errors
    }
  }

  private parseFrame(raw: RawData): OpenClawFrame | null {
    try {
      return JSON.parse(raw.toString()) as OpenClawFrame;
    } catch {
      this.deps.logger.warn?.("Failed to parse OpenClaw gateway frame", raw.toString());
      return null;
    }
  }

  private createGatewayError(code: string | undefined, message: string | undefined, method: string): RelayError {
    const normalizedCode = this.normalizeGatewayErrorCode(code, method);
    return new RelayError(normalizedCode, message ?? `OpenClaw command ${method} failed`);
  }

  private normalizeGatewayErrorCode(code: string | undefined, method: string): ErrorCode {
    if (code && ERROR_CODES.includes(code as ErrorCode)) {
      return code as ErrorCode;
    }

    const upperCode = code?.toUpperCase() ?? "";
    if (method === "session.resume" && upperCode.includes("SESSION")) {
      return "session_not_resumable";
    }

    if (upperCode.includes("TIMEOUT")) {
      return "command_timeout";
    }

    return "provider_unreachable";
  }

  private isUnsupportedMethod(error: unknown): boolean {
    const message =
      error instanceof RelayError || error instanceof Error ? error.message : String(error ?? "");
    return /method[_\s-]*not[_\s-]*found|not[_\s-]*implemented|unsupported/i.test(message);
  }
}
