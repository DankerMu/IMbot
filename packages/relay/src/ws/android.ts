import type { ClientMessage } from "@imbot/wire";
import type { FastifyInstance } from "fastify";
import WebSocket from "ws";
import type { RawData } from "ws";

import type { RelayConfig } from "../config";
import { getWsQueryParam, isValidToken } from "../auth/guard";
import { WsHub } from "./hub";

function safeParseMessage(raw: RawData): ClientMessage | null {
  try {
    return JSON.parse(raw.toString()) as ClientMessage;
  } catch {
    return null;
  }
}

export function registerAndroidWebSocketRoute(
  app: FastifyInstance,
  deps: {
    readonly config: RelayConfig;
    readonly hub: WsHub;
  }
): void {
  app.get("/v1/ws", { websocket: true }, (ws, request) => {
    let authenticated = false;
    let clientId: string | null = null;

    const queryToken = getWsQueryParam(request.url, "token");
    if (queryToken) {
      if (!isValidToken(queryToken, deps.config.staticToken)) {
        ws.close(4001, "unauthenticated");
        return;
      }

      authenticated = true;
      clientId = deps.hub.addAndroidClient(ws);
    }

    ws.on("message", (raw: RawData) => {
      const message = safeParseMessage(raw);
      if (!message) {
        ws.send(
          JSON.stringify({
            type: "error",
            code: "invalid_request",
            message: "Invalid JSON message"
          })
        );
        return;
      }

      if (!authenticated) {
        if (message.action === "auth" && isValidToken(message.token, deps.config.staticToken)) {
          authenticated = true;
          clientId = deps.hub.addAndroidClient(ws);
          return;
        }

        ws.send(
          JSON.stringify({
            type: "error",
            code: "unauthenticated",
            message: "Invalid or missing token"
          })
        );
        ws.close(4001, "unauthenticated");
        return;
      }

      if (message.action === "subscribe" && clientId) {
        deps.hub.subscribe(clientId, message.session_id);
        return;
      }

      if (message.action === "unsubscribe" && clientId) {
        deps.hub.unsubscribe(clientId, message.session_id);
        return;
      }

      if (message.action === "ping") {
        ws.send(JSON.stringify({ type: "pong" }));
      }
    });

    ws.on("close", () => {
      deps.hub.removeAndroidClient(ws);
    });
  });
}
