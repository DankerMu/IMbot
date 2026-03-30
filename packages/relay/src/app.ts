import cors from "@fastify/cors";
import websocket from "@fastify/websocket";
import Fastify, { type FastifyInstance } from "fastify";

import { AuditLogger } from "./audit/logger";
import { createAuthGuard } from "./auth/guard";
import { CompanionManager } from "./companion/manager";
import type { RelayConfig } from "./config";
import { loadConfig } from "./config";
import type { RelayDatabase } from "./db/init";
import { initializeDatabase } from "./db/init";
import { isRelayError } from "./errors";
import { registerEventRoutes } from "./routes/events";
import { registerHealthRoutes } from "./routes/health";
import { registerSessionRoutes } from "./routes/sessions";
import { OpenClawBridge } from "./openclaw/bridge";
import { SessionOrchestrator } from "./session/orchestrator";
import { registerAndroidWebSocketRoute } from "./ws/android";
import { registerCompanionWebSocketRoute } from "./ws/companion";
import { WsHub } from "./ws/hub";

export interface RelayRuntime {
  readonly app: FastifyInstance;
  readonly config: RelayConfig;
  readonly db: RelayDatabase;
  readonly hub: WsHub;
  readonly companionManager: CompanionManager;
  readonly openClawBridge: OpenClawBridge;
  readonly orchestrator: SessionOrchestrator;
  close(): Promise<void>;
}

export async function createRelayApp(options?: {
  readonly config?: RelayConfig;
  readonly logger?: boolean;
}): Promise<RelayRuntime> {
  const config = options?.config ?? loadConfig();
  const db = initializeDatabase(config.dbPath);
  const app = Fastify({
    logger:
      options?.logger === false
        ? false
        : {
            level: config.logLevel
          }
  });

  const hub = new WsHub(config.wsPingIntervalMs);
  const auditLogger = new AuditLogger(db, app.log);
  let companionManager!: CompanionManager;
  let orchestrator!: SessionOrchestrator;
  companionManager = new CompanionManager(config, db, hub, app.log, {
    auditLogger,
    onHostDisconnected: async (hostId) => {
      await orchestrator.handleHostDisconnected(hostId);
    }
  });
  const openClawBridge = new OpenClawBridge(config, {
    hub,
    logger: app.log,
    onRelayEvent: async (message) => {
      await orchestrator.handleEvent(message);
    }
  });
  orchestrator = new SessionOrchestrator(
    config,
    db,
    hub,
    companionManager,
    openClawBridge,
    auditLogger,
    app.log
  );
  let shutdownComplete = false;

  const performShutdown = async (): Promise<void> => {
    if (shutdownComplete) {
      return;
    }

    shutdownComplete = true;
    companionManager.shutdown();
    openClawBridge.shutdown();
    await hub.closeAll(1001, "Going Away");
    db.close();
  };

  app.setErrorHandler((error, _request, reply) => {
    if (
      typeof error === "object" &&
      error !== null &&
      "validation" in error &&
      Array.isArray((error as { validation?: unknown }).validation)
    ) {
      reply.code(400).send({ error: "invalid_request" });
      return;
    }

    if (isRelayError(error)) {
      reply.code(error.statusCode).send({ error: error.code });
      return;
    }

    app.log.error(error);
    reply.code(500).send({ error: "internal_error" });
  });

  await app.register(cors, {
    origin: true,
    methods: ["GET", "POST", "DELETE", "OPTIONS"],
    allowedHeaders: ["Authorization", "Content-Type"]
  });
  await app.register(websocket);

  registerHealthRoutes(app, {
    db,
    companionManager,
    openClawBridge
  });

  registerAndroidWebSocketRoute(app, {
    config,
    hub
  });

  registerCompanionWebSocketRoute(app, {
    config,
    companionManager,
    orchestrator
  });

  await app.register(
    async (securedApp) => {
      securedApp.addHook("preHandler", createAuthGuard(config));
      registerSessionRoutes(securedApp, {
        db,
        orchestrator
      });
      registerEventRoutes(securedApp, {
        db
      });
    },
    {
      prefix: "/v1"
    }
  );

  app.addHook("onClose", async () => {
    await performShutdown();
  });

  openClawBridge.connect();

  return {
    app,
    config,
    db,
    hub,
    companionManager,
    openClawBridge,
    orchestrator,
    close: async () => {
      await performShutdown();
      await app.close();
    }
  };
}
