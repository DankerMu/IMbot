import type { FastifyInstance } from "fastify";

import type { RelayDatabase } from "../db/init";
import { getDatabaseStatus } from "../db/init";
import { CompanionManager } from "../companion/manager";
import { OpenClawBridge } from "../openclaw/bridge";

export function registerHealthRoutes(
  app: FastifyInstance,
  deps: {
    readonly db: RelayDatabase;
    readonly companionManager: CompanionManager;
    readonly openClawBridge: OpenClawBridge;
  }
): void {
  app.get("/healthz", async () => ({
    status: "ok",
    uptime: process.uptime(),
    db: getDatabaseStatus(deps.db),
    companion: deps.companionManager.hasOnlineCompanion() ? "online" : "offline",
    openclaw: deps.openClawBridge.isAvailable() ? "online" : "offline"
  }));
}
