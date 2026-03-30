import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const { initializeDatabase } = require("../../packages/relay/dist/db/init.js");
const { allocateSeq } = require("../../packages/relay/dist/session/seq.js");
const { TRANSITIONS, isValidTransition } = require("../../packages/relay/dist/session/transitions.js");

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

function insertSession(db, sessionId, status = "running") {
  const now = new Date().toISOString();
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
    ) VALUES (?, 'claude', 'provider-session-1', 'macbook-1', NULL, ?, ?, NULL, 'bypassPermissions', ?, NULL, NULL, ?, ?, ?)
    `
  ).run(sessionId, "/tmp/project", "hello", status, now, now, now);
}

test("relay lifecycle transitions match the expected state machine table", () => {
  assert.deepEqual(TRANSITIONS.queued, ["running", "failed"]);
  assert.deepEqual(TRANSITIONS.running, ["completed", "failed", "cancelled"]);
  assert.equal(isValidTransition("completed", "running"), true);
  assert.equal(isValidTransition("failed", "running"), true);
  assert.equal(isValidTransition("cancelled", "running"), false);
  assert.equal(isValidTransition("queued", "cancelled"), false);
});

test("allocateSeq starts at 1 and increments monotonically", (t) => {
  const { db, cleanup } = createTestDatabase("imbot-relay-seq-");
  t.after(cleanup);

  db.prepare(
    `
    INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
    VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', NULL, datetime('now'), datetime('now'))
    `
  ).run();

  insertSession(db, "sess-seq");

  const first = allocateSeq(db, "sess-seq");
  db.prepare(
    `
    INSERT INTO session_events (id, session_id, seq, type, payload, created_at)
    VALUES ('evt-1', 'sess-seq', ?, 'assistant_delta', '{}', ?)
    `
  ).run(first, new Date().toISOString());
  const second = allocateSeq(db, "sess-seq");

  assert.equal(first, 1);
  assert.equal(second, 2);
});

test("allocateSeq logs a warning when an existing seq gap is detected", (t) => {
  const { db, cleanup } = createTestDatabase("imbot-relay-seq-gap-");
  t.after(cleanup);

  db.prepare(
    `
    INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
    VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', NULL, datetime('now'), datetime('now'))
    `
  ).run();

  insertSession(db, "sess-gap");
  db.prepare(
    `
    INSERT INTO session_events (id, session_id, seq, type, payload, created_at)
    VALUES
      ('evt-gap-1', 'sess-gap', 1, 'assistant_delta', '{}', ?),
      ('evt-gap-3', 'sess-gap', 3, 'assistant_delta', '{}', ?)
    `
  ).run(new Date().toISOString(), new Date().toISOString());

  const warnings = [];
  const nextSeq = allocateSeq(db, "sess-gap", {
    warn: (message) => warnings.push(String(message))
  });

  assert.equal(nextSeq, 4);
  assert.equal(warnings.length, 1);
  assert.match(warnings[0], /Seq gap detected for session sess-gap: expected 3, got 4/);
});

test("handleEvent ignores late provider events after a session reaches a terminal state", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-late-events-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1"
  });

  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const now = new Date().toISOString();
  runtime.db
    .prepare(
      `
      INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
      VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', ?, ?, ?)
      `
    )
    .run(now, now, now);

  for (const status of ["completed", "failed", "cancelled"]) {
    insertSession(runtime.db, `sess-${status}`, status);
  }

  for (const status of ["completed", "failed", "cancelled"]) {
    await runtime.orchestrator.handleEvent({
      type: "event",
      session_id: `sess-${status}`,
      event_type: "assistant_delta",
      payload: {
        text: `late-${status}`
      }
    });
  }

  for (const status of ["completed", "failed", "cancelled"]) {
    const eventCount = runtime.db
      .prepare("SELECT COUNT(*) AS count FROM session_events WHERE session_id = ?")
      .get(`sess-${status}`);
    assert.deepEqual(eventCount, { count: 0 });
  }
});

test("transition rejects a raced same-target update without emitting a duplicate status event", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-transition-race-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1"
  });

  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const now = new Date().toISOString();
  runtime.db
    .prepare(
      `
      INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
      VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', ?, ?, ?)
      `
    )
    .run(now, now, now);

  runtime.db
    .prepare(
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
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `
    )
    .run(
      "sess-race",
      "claude",
      "provider-session-1",
      "macbook-1",
      null,
      "/tmp/project",
      "hello",
      null,
      "bypassPermissions",
      "running",
      null,
      null,
      now,
      now,
      now
    );

  const originalPrepare = runtime.db.prepare.bind(runtime.db);
  let injectedRace = false;
  runtime.db.prepare = (sql) => {
    const statement = originalPrepare(sql);
    if (
      injectedRace ||
      typeof sql !== "string" ||
      !sql.includes("UPDATE sessions") ||
      !sql.includes("WHERE id = ? AND status = ?")
    ) {
      return statement;
    }

    const wrapped = Object.create(statement);
    wrapped.run = (...args) => {
      injectedRace = true;
      const racedAt = new Date().toISOString();
      originalPrepare(
        `
        UPDATE sessions
        SET status = 'cancelled', updated_at = ?, last_active_at = ?
        WHERE id = ?
        `
      ).run(racedAt, racedAt, "sess-race");
      originalPrepare(
        `
        INSERT INTO session_events (id, session_id, seq, type, payload, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        `
      ).run(
        "evt-race-status",
        "sess-race",
        1,
        "session_status_changed",
        JSON.stringify({
          status: "cancelled",
          error_code: null,
          error_message: null
        }),
        racedAt
      );
      return statement.run(...args);
    };
    return wrapped;
  };

  await assert.rejects(
    runtime.orchestrator.transition("sess-race", "cancelled"),
    /changed while transitioning from running to cancelled/
  );

  runtime.db.prepare = originalPrepare;

  const statusEvents = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM session_events WHERE session_id = ? AND type = 'session_status_changed'")
    .get("sess-race");
  assert.deepEqual(statusEvents, { count: 1 });
});
