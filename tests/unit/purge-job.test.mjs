import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { RelayPurgeJob } from "../../packages/relay/dist/index.js";
import { initializeDatabase } from "../../packages/relay/dist/db/init.js";

function createTestDatabase(prefix) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), prefix));
  const db = initializeDatabase(path.join(tempDir, "imbot.db"));

  const cleanup = () => {
    db.close();
    rmSync(tempDir, { recursive: true, force: true });
  };

  return {
    db,
    cleanup
  };
}

function insertMacbookHost(db, hostId = "macbook-1") {
  db.prepare(
    `
    INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
    VALUES (?, ?, 'macbook', 'online', datetime('now'), datetime('now'), datetime('now'))
    `
  ).run(hostId, hostId);
}

function insertSession(db, sessionId, { status, lastActiveAt, hostId = "macbook-1" }) {
  db.prepare(
    `
    INSERT INTO sessions (
      id,
      provider,
      provider_session_id,
      host_id,
      workspace_root,
      workspace_cwd,
      initial_prompt,
      model,
      permission_mode,
      status,
      error_message,
      error_code,
      created_at,
      updated_at,
      last_active_at
    ) VALUES (?, 'claude', ?, ?, NULL, ?, ?, NULL, 'bypassPermissions', ?, NULL, NULL, ?, ?, ?)
    `
  ).run(
    sessionId,
    `provider-${sessionId}`,
    hostId,
    `/tmp/${sessionId}`,
    `prompt-${sessionId}`,
    status,
    lastActiveAt,
    lastActiveAt,
    lastActiveAt
  );
}

function insertSessionEvent(db, eventId, sessionId, seq = 1) {
  db.prepare(
    `
    INSERT INTO session_events (id, session_id, seq, type, payload, created_at)
    VALUES (?, ?, ?, 'assistant_message', '{}', datetime('now'))
    `
  ).run(eventId, sessionId, seq);
}

function createPurgeJob(db, logs) {
  return new RelayPurgeJob(
    { purgeDays: 30 },
    db,
    {
      info: (message) => logs.push(String(message))
    }
  );
}

test("RelayPurgeJob purges completed, failed, and cancelled sessions older than 30 days", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-purge-statuses-");
  t.after(cleanup);
  insertMacbookHost(db);

  insertSession(db, "sess-completed", {
    status: "completed",
    lastActiveAt: "2026-02-28T00:00:00.000Z"
  });
  insertSession(db, "sess-failed", {
    status: "failed",
    lastActiveAt: "2026-02-28T00:00:00.000Z"
  });
  insertSession(db, "sess-cancelled", {
    status: "cancelled",
    lastActiveAt: "2026-02-28T00:00:00.000Z"
  });

  const logs = [];
  const purgedCount = await createPurgeJob(db, logs).runOnce(new Date("2026-03-31T00:00:00.000Z"));

  assert.equal(purgedCount, 3);
  assert.equal(
    db.prepare(
      "SELECT COUNT(*) AS count FROM sessions WHERE id IN ('sess-completed', 'sess-failed', 'sess-cancelled')"
    ).get().count,
    0
  );
  assert.match(logs[0], /purged 3 sessions/);
});

test("RelayPurgeJob keeps recent completed sessions and stale running sessions", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-purge-boundary-");
  t.after(cleanup);
  insertMacbookHost(db);

  insertSession(db, "sess-recent", {
    status: "completed",
    lastActiveAt: "2026-03-02T00:00:00.000Z"
  });
  insertSession(db, "sess-running", {
    status: "running",
    lastActiveAt: "2026-02-28T00:00:00.000Z"
  });

  const logs = [];
  const purgedCount = await createPurgeJob(db, logs).runOnce(new Date("2026-03-31T00:00:00.000Z"));

  assert.equal(purgedCount, 0);
  assert.deepEqual(
    db.prepare("SELECT id, status FROM sessions ORDER BY id").all(),
    [
      { id: "sess-recent", status: "completed" },
      { id: "sess-running", status: "running" }
    ]
  );
  assert.match(logs[0], /purged 0 sessions/);
});

test("RelayPurgeJob cascades session event deletion when a stale session is purged", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-purge-cascade-");
  t.after(cleanup);
  insertMacbookHost(db);

  insertSession(db, "sess-cascade", {
    status: "completed",
    lastActiveAt: "2026-02-28T00:00:00.000Z"
  });
  insertSessionEvent(db, "evt-cascade-1", "sess-cascade");
  insertSessionEvent(db, "evt-cascade-2", "sess-cascade", 2);

  const logs = [];
  await createPurgeJob(db, logs).runOnce(new Date("2026-03-31T00:00:00.000Z"));

  assert.equal(db.prepare("SELECT COUNT(*) AS count FROM sessions WHERE id = 'sess-cascade'").get().count, 0);
  assert.equal(
    db.prepare("SELECT COUNT(*) AS count FROM session_events WHERE session_id = 'sess-cascade'").get().count,
    0
  );
});

test("RelayPurgeJob processes stale sessions in batches until all qualifying rows are removed", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-purge-batches-");
  t.after(cleanup);
  insertMacbookHost(db);

  for (let index = 1; index <= 150; index += 1) {
    insertSession(db, `sess-batch-${index}`, {
      status: "completed",
      lastActiveAt: "2026-02-28T00:00:00.000Z"
    });
  }

  const logs = [];
  const purgedCount = await createPurgeJob(db, logs).runOnce(new Date("2026-03-31T00:00:00.000Z"));

  assert.equal(purgedCount, 150);
  assert.equal(db.prepare("SELECT COUNT(*) AS count FROM sessions").get().count, 0);
  assert.match(logs[0], /purged 150 sessions/);
});

test("RelayPurgeJob logs zero when no sessions qualify for deletion", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-purge-zero-");
  t.after(cleanup);
  insertMacbookHost(db);

  insertSession(db, "sess-running", {
    status: "running",
    lastActiveAt: "2026-03-31T00:00:00.000Z"
  });

  const logs = [];
  const purgedCount = await createPurgeJob(db, logs).runOnce(new Date("2026-03-31T00:00:00.000Z"));

  assert.equal(purgedCount, 0);
  assert.deepEqual(logs, ["[purge] 2026-03-31T00:00:00.000Z - purged 0 sessions"]);
});
