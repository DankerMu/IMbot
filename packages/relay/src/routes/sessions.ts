import { PROVIDERS, SESSION_STATUSES, type Session } from "@imbot/wire";
import type { FastifyInstance } from "fastify";

import type { RelayDatabase } from "../db/init";
import { RelayError } from "../errors";
import { SessionOrchestrator } from "../session/orchestrator";

function parseLimit(value: string | undefined, fallback: number, max: number): number {
  if (!value) {
    return fallback;
  }

  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new RelayError("invalid_request", "limit must be a non-negative integer");
  }

  return Math.min(parsed, max);
}

function parseOffset(value: string | undefined): number {
  if (!value) {
    return 0;
  }

  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new RelayError("invalid_request", "offset must be a non-negative integer");
  }

  return parsed;
}

export function registerSessionRoutes(
  app: FastifyInstance,
  deps: {
    readonly db: RelayDatabase;
    readonly orchestrator: SessionOrchestrator;
  }
): void {
  app.post("/sessions", async (request, reply) => {
    const body = (request.body ?? {}) as Record<string, unknown>;
    const session = await deps.orchestrator.create({
      provider: typeof body.provider === "string" ? body.provider : undefined,
      host_id: typeof body.host_id === "string" ? body.host_id : undefined,
      cwd: typeof body.cwd === "string" ? body.cwd : undefined,
      prompt: typeof body.prompt === "string" ? body.prompt : undefined,
      model: typeof body.model === "string" ? body.model : undefined,
      permission_mode:
        typeof body.permission_mode === "string" ? body.permission_mode : undefined
    });

    reply.code(201).send({ session });
  });

  app.get("/sessions", async (request) => {
    const query = request.query as Record<string, string | undefined>;
    const limit = parseLimit(query.limit, 50, 200);
    const offset = parseOffset(query.offset);
    const filters: string[] = [];
    const params: unknown[] = [];

    if (query.provider) {
      if (!PROVIDERS.includes(query.provider as (typeof PROVIDERS)[number])) {
        throw new RelayError("invalid_request", "provider filter is invalid");
      }
      filters.push("provider = ?");
      params.push(query.provider);
    }

    if (query.status) {
      if (!SESSION_STATUSES.includes(query.status as (typeof SESSION_STATUSES)[number])) {
        throw new RelayError("invalid_request", "status filter is invalid");
      }
      filters.push("status = ?");
      params.push(query.status);
    }

    if (query.host_id) {
      filters.push("host_id = ?");
      params.push(query.host_id);
    }

    const whereClause = filters.length > 0 ? `WHERE ${filters.join(" AND ")}` : "";
    const sessions = deps.db
      .prepare(
        `
        SELECT *
        FROM sessions
        ${whereClause}
        ORDER BY created_at DESC
        LIMIT ? OFFSET ?
        `
      )
      .all(...params, limit, offset) as Session[];

    const total = deps.db
      .prepare(`SELECT COUNT(*) AS count FROM sessions ${whereClause}`)
      .get(...params) as { count: number };

    return {
      sessions,
      total: total.count,
      limit,
      offset
    };
  });

  app.get("/sessions/:id", async (request) => {
    const { id } = request.params as { id: string };
    const session =
      (deps.db.prepare("SELECT * FROM sessions WHERE id = ?").get(id) as Session | undefined) ?? null;

    if (!session) {
      throw new RelayError("not_found", `Session ${id} not found`);
    }

    return session;
  });
}

