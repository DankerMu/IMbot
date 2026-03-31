import { randomUUID } from "node:crypto";

import type { FastifyInstance } from "fastify";

import type { RelayDatabase } from "../db/init";
import { RelayError } from "../errors";

const registerPushBodySchema = {
  type: "object",
  required: ["fcm_token"],
  additionalProperties: false,
  properties: {
    fcm_token: { type: "string", minLength: 1 }
  }
} as const;

export function registerPushRoutes(
  app: FastifyInstance,
  deps: {
    readonly db: RelayDatabase;
  }
): void {
  app.post("/push/register", { schema: { body: registerPushBodySchema } }, async (request) => {
    const body = (request.body ?? {}) as Record<string, unknown>;
    const fcmToken = typeof body.fcm_token === "string" ? body.fcm_token.trim() : "";
    if (!fcmToken) {
      throw new RelayError("invalid_request", "fcm_token is required");
    }

    deps.db
      .prepare(
        `
        INSERT OR REPLACE INTO push_subscriptions (id, fcm_token, created_at, updated_at)
        VALUES (?, ?, datetime('now'), datetime('now'))
        `
      )
      .run(randomUUID(), fcmToken);

    return { status: "ok" };
  });
}
