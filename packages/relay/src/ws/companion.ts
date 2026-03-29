import type { CompanionMessage } from "@imbot/wire";
import type { FastifyInstance } from "fastify";
import WebSocket from "ws";
import type { RawData } from "ws";

import type { RelayConfig } from "../config";
import { getWsQueryParam, isValidToken } from "../auth/guard";
import { CompanionManager } from "../companion/manager";
import { SessionOrchestrator } from "../session/orchestrator";

function safeParseMessage(raw: RawData): CompanionMessage | null {
  try {
    return JSON.parse(raw.toString()) as CompanionMessage;
  } catch {
    return null;
  }
}

export function registerCompanionWebSocketRoute(
  app: FastifyInstance,
  deps: {
    readonly config: RelayConfig;
    readonly companionManager: CompanionManager;
    readonly orchestrator: SessionOrchestrator;
  }
): void {
  app.get("/v1/companion", { websocket: true }, (ws, request) => {
    const hostId = getWsQueryParam(request.url, "host_id");
    if (!hostId) {
      ws.close(4002, "missing host_id");
      return;
    }

    const token = getWsQueryParam(request.url, "token");
    if (!isValidToken(token, deps.config.staticToken)) {
      ws.close(4001, "unauthenticated");
      return;
    }

    deps.companionManager.registerConnection(hostId, ws);

    ws.on("message", (raw: RawData) => {
      const message = safeParseMessage(raw);
      if (!message) {
        return;
      }

      if (message.type === "ack") {
        deps.companionManager.handleAck(hostId, message);
        return;
      }

      if (message.type === "heartbeat") {
        deps.companionManager.handleHeartbeat(hostId, message);
        return;
      }

      if (message.type === "event") {
        void deps.orchestrator.handleEvent(message).catch((error) => {
          app.log.error(error);
        });
      }
    });

    ws.on("close", () => {
      deps.companionManager.unregisterConnection(hostId, ws);
    });
  });
}
