import type { FastifyInstance } from "fastify";

import type { RelayDatabase } from "../db/init";
import { getDatabaseStatus } from "../db/init";
import { CompanionManager } from "../companion/manager";

export function registerHealthRoutes(
  app: FastifyInstance,
  deps: {
    readonly db: RelayDatabase;
    readonly companionManager: CompanionManager;
  }
): void {
  app.get("/healthz", async () => ({
    status: "ok",
    uptime: process.uptime(),
    db: getDatabaseStatus(deps.db),
    companion: deps.companionManager.hasOnlineCompanion() ? "online" : "offline",
    openclaw: "offline"
  }));
}

