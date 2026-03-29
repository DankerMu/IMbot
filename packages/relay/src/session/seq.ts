import type { RelayDatabase } from "../db/init";

export function allocateSeq(db: RelayDatabase, sessionId: string): number {
  const row = db
    .prepare(
      `
      SELECT COALESCE(MAX(seq), 0) + 1 AS next_seq
      FROM session_events
      WHERE session_id = ?
      `
    )
    .get(sessionId) as { next_seq: number };

  return row.next_seq;
}

