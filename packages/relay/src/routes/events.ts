import type { FastifyInstance } from "fastify";

import type { RelayDatabase } from "../db/init";
import { RelayError } from "../errors";

const sessionEventsParamsSchema = {
  type: "object",
  required: ["id"],
  properties: {
    id: { type: "string", minLength: 1 }
  }
} as const;

const sessionEventsQuerySchema = {
  type: "object",
  required: ["since_seq"],
  additionalProperties: false,
  properties: {
    since_seq: { type: "integer", minimum: 0 },
    limit: { type: "integer", minimum: 1 }
  }
} as const;

export function registerEventRoutes(
  app: FastifyInstance,
  deps: {
    readonly db: RelayDatabase;
  }
): void {
  app.get(
    "/sessions/:id/events",
    {
      schema: {
        params: sessionEventsParamsSchema,
        querystring: sessionEventsQuerySchema
      }
    },
    async (request) => {
      const { id } = request.params as { id: string };
      const query = request.query as Record<string, string | number | undefined>;
      const sinceSeqRaw = query.since_seq;
      const limitRaw = query.limit;

      const sessionExists = deps.db.prepare("SELECT 1 AS ok FROM sessions WHERE id = ?").get(id) as
        | { ok: number }
        | undefined;
      if (!sessionExists) {
        throw new RelayError("not_found", `Session ${id} not found`);
      }

      if (sinceSeqRaw === undefined || sinceSeqRaw === null || sinceSeqRaw === "") {
        throw new RelayError("invalid_request", "since_seq is required");
      }

      const sinceSeq =
        typeof sinceSeqRaw === "number" ? sinceSeqRaw : Number.parseInt(String(sinceSeqRaw), 10);
      if (!Number.isFinite(sinceSeq) || sinceSeq < 0) {
        throw new RelayError("invalid_request", "since_seq must be a non-negative integer");
      }

      const limit =
        limitRaw == null || limitRaw === ""
          ? 500
          : (() => {
              const parsedLimit =
                typeof limitRaw === "number" ? limitRaw : Number.parseInt(String(limitRaw), 10);
              if (!Number.isFinite(parsedLimit) || parsedLimit < 1) {
                throw new RelayError("invalid_request", "limit must be a positive integer");
              }

              return Math.min(parsedLimit, 500);
            })();

      const rows = deps.db
        .prepare(
          `
          SELECT id, session_id, seq, type, payload, created_at
          FROM session_events
          WHERE session_id = ? AND seq > ?
          ORDER BY seq ASC
          LIMIT ?
          `
        )
        .all(id, sinceSeq, limit + 1) as Array<{
        id: string;
        session_id: string;
        seq: number;
        type: string;
        payload: string;
        created_at: string;
      }>;

      const hasMore = rows.length > limit;
      const events = rows.slice(0, limit).map((row) => ({
        id: row.id,
        session_id: row.session_id,
        seq: row.seq,
        type: row.type,
        payload: JSON.parse(row.payload),
        created_at: row.created_at
      }));

      return {
        events,
        has_more: hasMore
      };
    }
  );
}
