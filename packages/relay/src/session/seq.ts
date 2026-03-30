import type { RelayDatabase } from "../db/init";

type LoggerLike = {
  readonly warn?: (...args: unknown[]) => void;
};

export function allocateSeq(db: RelayDatabase, sessionId: string, logger?: LoggerLike): number {
  const row = db
    .prepare(
      `
      SELECT
        COALESCE(MAX(seq), 0) + 1 AS next_seq,
        COUNT(*) + 1 AS expected_seq
      FROM session_events
      WHERE session_id = ?
      `
    )
    .get(sessionId) as { next_seq: number; expected_seq: number };

  if (row.next_seq !== row.expected_seq) {
    logger?.warn?.(
      `Seq gap detected for session ${sessionId}: expected ${row.expected_seq}, got ${row.next_seq}`
    );
  }

  return row.next_seq;
}
