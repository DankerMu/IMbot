import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
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

function insertSession(db, sessionId) {
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
    ) VALUES (?, 'claude', 'provider-session-1', 'macbook-1', NULL, ?, ?, NULL, 'bypassPermissions', 'running', NULL, NULL, ?, ?, ?)
    `
  ).run(sessionId, "/tmp/project", "hello", now, now, now);
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
