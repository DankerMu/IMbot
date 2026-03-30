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

test("handleEvent accepts provider events during create and resume lifecycle windows", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-active-mutation-events-"));
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

  insertSession(runtime.db, "sess-create-window", "queued");
  insertSession(runtime.db, "sess-resume-window", "completed");

  runtime.orchestrator.activeLifecycleMutations.set("sess-create-window", "create");
  runtime.orchestrator.activeLifecycleMutations.set("sess-resume-window", "resume");

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-create-window",
    event_type: "assistant_delta",
    payload: {
      text: "first create event"
    }
  });

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-resume-window",
    event_type: "assistant_delta",
    payload: {
      text: "first resume event"
    }
  });

  const createWindowEvents = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM session_events WHERE session_id = ?")
    .get("sess-create-window");
  const resumeWindowEvents = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM session_events WHERE session_id = ?")
    .get("sess-resume-window");

  assert.deepEqual(createWindowEvents, { count: 1 });
  assert.deepEqual(resumeWindowEvents, { count: 1 });
});

test("handleEvent defers terminal provider events during create and resume lifecycle windows until running", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-active-mutation-terminals-"));
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

  insertSession(runtime.db, "sess-create-result", "queued");
  insertSession(runtime.db, "sess-create-error", "queued");
  insertSession(runtime.db, "sess-resume-result", "completed");
  insertSession(runtime.db, "sess-resume-error", "failed");

  runtime.orchestrator.activeLifecycleMutations.set("sess-create-result", "create");
  runtime.orchestrator.activeLifecycleMutations.set("sess-create-error", "create");
  runtime.orchestrator.activeLifecycleMutations.set("sess-resume-result", "resume");
  runtime.orchestrator.activeLifecycleMutations.set("sess-resume-error", "resume");

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-create-result",
    event_type: "session_result",
    payload: {
      result: "done"
    }
  });

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-create-error",
    event_type: "session_error",
    payload: {
      error_code: "directory_not_found",
      message: "Missing project"
    }
  });

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-resume-result",
    event_type: "session_result",
    payload: {
      result: "done again"
    }
  });

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-resume-error",
    event_type: "session_error",
    payload: {
      error_code: "provider_unreachable",
      message: "Gateway lost"
    }
  });

  assert.deepEqual(
    runtime.db
      .prepare("SELECT id, status FROM sessions WHERE id IN (?, ?, ?, ?) ORDER BY id ASC")
      .all("sess-create-error", "sess-create-result", "sess-resume-error", "sess-resume-result"),
    [
      { id: "sess-create-error", status: "queued" },
      { id: "sess-create-result", status: "queued" },
      { id: "sess-resume-error", status: "failed" },
      { id: "sess-resume-result", status: "completed" }
    ]
  );

  await runtime.orchestrator.markSessionStarted("sess-create-result", "provider-session-1", "queued");
  await runtime.orchestrator.markSessionStarted("sess-create-error", "provider-session-1", "queued");
  await runtime.orchestrator.markSessionStarted("sess-resume-result", "provider-session-1", "completed");
  await runtime.orchestrator.markSessionStarted("sess-resume-error", "provider-session-1", "failed");

  await runtime.orchestrator.applyPendingTerminalTransition("sess-create-result");
  await runtime.orchestrator.applyPendingTerminalTransition("sess-create-error");
  await runtime.orchestrator.applyPendingTerminalTransition("sess-resume-result");
  await runtime.orchestrator.applyPendingTerminalTransition("sess-resume-error");

  assert.deepEqual(
    runtime.db
      .prepare(
        "SELECT id, status, error_code, error_message FROM sessions WHERE id IN (?, ?, ?, ?) ORDER BY id ASC"
      )
      .all("sess-create-error", "sess-create-result", "sess-resume-error", "sess-resume-result"),
    [
      {
        id: "sess-create-error",
        status: "failed",
        error_code: "directory_not_found",
        error_message: "Missing project"
      },
      {
        id: "sess-create-result",
        status: "completed",
        error_code: null,
        error_message: null
      },
      {
        id: "sess-resume-error",
        status: "failed",
        error_code: "provider_unreachable",
        error_message: "Gateway lost"
      },
      {
        id: "sess-resume-result",
        status: "completed",
        error_code: null,
        error_message: null
      }
    ]
  );
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
