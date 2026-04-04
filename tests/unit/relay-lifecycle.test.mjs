import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const Database = require("better-sqlite3");
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

async function createRelayRuntime(prefix) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), prefix));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error",
    RELAY_COMPANION_TIMEOUT_MS: "2000",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1"
  });
  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  return {
    tempDir,
    runtime
  };
}

function insertHost(db, hostId = "macbook-1", status = "online") {
  const now = new Date().toISOString();
  db.prepare(
    `
    INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
    VALUES (?, ?, 'macbook', ?, ?, ?, ?)
    `
  ).run(hostId, hostId, status, now, now, now);
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
    ) VALUES (?, 'claude', ?, 'macbook-1', NULL, ?, ?, NULL, 'bypassPermissions', ?, NULL, NULL, ?, ?, ?)
    `
  ).run(sessionId, `${sessionId}-provider-session`, "/tmp/project", "hello", status, now, now, now);
}

test("initializeDatabase migrates existing sessions tables so idle becomes a valid status", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-schema-migration-"));
  const dbPath = path.join(tempDir, "imbot.db");
  const legacyDb = new Database(dbPath);

  legacyDb.pragma("foreign_keys = ON");
  legacyDb.exec(`
    CREATE TABLE hosts (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      type TEXT NOT NULL CHECK (type IN ('macbook', 'relay_local')),
      status TEXT NOT NULL DEFAULT 'offline' CHECK (status IN ('online', 'offline')),
      last_heartbeat_at TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE sessions (
      id TEXT PRIMARY KEY,
      provider TEXT NOT NULL CHECK (provider IN ('claude', 'book', 'openclaw')),
      provider_session_id TEXT,
      host_id TEXT NOT NULL REFERENCES hosts(id),
      workspace_root TEXT,
      workspace_cwd TEXT NOT NULL,
      initial_prompt TEXT,
      model TEXT,
      permission_mode TEXT NOT NULL DEFAULT 'bypassPermissions',
      status TEXT NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'running', 'completed', 'failed', 'cancelled')),
      error_message TEXT,
      error_code TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      last_active_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  const now = new Date().toISOString();
  legacyDb
    .prepare(
      `
      INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
      VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', ?, ?, ?)
      `
    )
    .run(now, now, now);
  legacyDb
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
      ) VALUES (?, 'claude', 'provider-session-1', 'macbook-1', NULL, ?, ?, NULL, 'bypassPermissions', 'completed', NULL, NULL, ?, ?, ?)
      `
    )
    .run("sess-legacy", "/tmp/project", "hello", now, now, now);
  legacyDb.close();

  const migratedDb = initializeDatabase(dbPath);

  try {
    const preservedSession = migratedDb
      .prepare("SELECT id, status, local_available FROM sessions WHERE id = ?")
      .get("sess-legacy");
    assert.deepEqual(preservedSession, {
      id: "sess-legacy",
      status: "completed",
      local_available: 1
    });

    migratedDb
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
        ) VALUES (?, 'claude', 'provider-session-2', 'macbook-1', NULL, ?, ?, NULL, 'bypassPermissions', 'idle', NULL, NULL, ?, ?, ?)
        `
      )
      .run("sess-idle", "/tmp/project", "followup", now, now, now);

    const idleSession = migratedDb.prepare("SELECT id, status FROM sessions WHERE id = ?").get("sess-idle");
    assert.deepEqual(idleSession, {
      id: "sess-idle",
      status: "idle"
    });
  } finally {
    migratedDb.close();
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("relay lifecycle transitions match the expected state machine table", () => {
  assert.deepEqual(TRANSITIONS.queued, ["running", "idle", "failed"]);
  assert.deepEqual(TRANSITIONS.running, ["idle", "completed", "failed", "cancelled"]);
  assert.deepEqual(TRANSITIONS.idle, ["running", "completed", "failed", "cancelled"]);
  assert.equal(isValidTransition("completed", "running"), true);
  assert.equal(isValidTransition("failed", "running"), true);
  assert.equal(isValidTransition("cancelled", "running"), true);
  assert.equal(isValidTransition("idle", "failed"), true);
  assert.equal(isValidTransition("queued", "idle"), true);
  assert.equal(isValidTransition("queued", "cancelled"), false);
});

test("orchestrator.create keeps promptless sessions idle and records awaiting_first_message", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-empty-session-create-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  runtime.companionManager.isOnline = () => true;

  let sendCommandCalls = 0;
  runtime.companionManager.sendCommand = async () => {
    sendCommandCalls += 1;
    return {
      type: "ack",
      status: "ok",
      data: {
        provider_session_id: "unexpected-provider-session"
      }
    };
  };

  const session = await runtime.orchestrator.create({
    provider: "claude",
    host_id: "macbook-1",
    cwd: "/tmp/project"
  });

  assert.equal(session.status, "idle");
  assert.equal(session.initial_prompt, null);
  assert.equal(sendCommandCalls, 0);
  assert.equal(runtime.orchestrator.activeLifecycleMutations.has(session.id), false);

  const storedSession = runtime.db
    .prepare("SELECT status, initial_prompt, provider_session_id, local_available FROM sessions WHERE id = ?")
    .get(session.id);
  assert.deepEqual(storedSession, {
    status: "idle",
    initial_prompt: null,
    provider_session_id: null,
    local_available: 0
  });

  const storedEvents = runtime.db
    .prepare("SELECT type, payload FROM session_events WHERE session_id = ? ORDER BY seq ASC")
    .all(session.id);
  assert.deepEqual(
    storedEvents.map((event) => event.type),
    ["session_status_changed", "session_idle"]
  );
  assert.deepEqual(JSON.parse(storedEvents[1].payload), {
    reason: "awaiting_first_message"
  });
});

test("orchestrator.sendMessage starts an empty idle session with create_session on the first message", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-empty-session-first-message-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  runtime.companionManager.isOnline = () => true;

  const commands = [];
  runtime.companionManager.sendCommand = async (hostId, command) => {
    commands.push({ hostId, command });
    return {
      type: "ack",
      status: "ok",
      data: {
        provider_session_id: "provider-session-first-message"
      }
    };
  };

  const session = await runtime.orchestrator.create({
    provider: "claude",
    host_id: "macbook-1",
    cwd: "/tmp/project"
  });

  await runtime.orchestrator.sendMessage(session.id, "hello from the first turn");

  assert.equal(commands.length, 1);
  assert.equal(commands[0].hostId, "macbook-1");
  assert.equal(commands[0].command.cmd, "create_session");
  assert.equal(commands[0].command.session_id, session.id);
  assert.equal(commands[0].command.prompt, "hello from the first turn");

  const storedSession = runtime.db
    .prepare("SELECT status, initial_prompt, provider_session_id, local_available FROM sessions WHERE id = ?")
    .get(session.id);
  assert.deepEqual(storedSession, {
    status: "running",
    initial_prompt: "hello from the first turn",
    provider_session_id: "provider-session-first-message",
    local_available: 1
  });

  const storedEvents = runtime.db
    .prepare("SELECT type, payload FROM session_events WHERE session_id = ? ORDER BY seq ASC")
    .all(session.id);
  assert.equal(storedEvents.some((event) => event.type === "user_message"), true);
  assert.deepEqual(
    JSON.parse(storedEvents.find((event) => event.type === "user_message").payload),
    { text: "hello from the first turn" }
  );
  assert.equal(storedEvents.some((event) => event.type === "session_started"), true);
});

test("orchestrator.create records the initial prompt as a user_message for companion-backed providers", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-create-initial-user-message-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  runtime.companionManager.isOnline = () => true;
  runtime.companionManager.sendCommand = async () => ({
    type: "ack",
    status: "ok",
    data: {
      provider_session_id: "provider-session-create-prompt"
    }
  });

  const session = await runtime.orchestrator.create({
    provider: "book",
    host_id: "macbook-1",
    cwd: "/tmp/novel",
    prompt: "  chapter opening prompt  "
  });

  assert.equal(session.status, "running");

  const storedEvents = runtime.db
    .prepare("SELECT type, payload FROM session_events WHERE session_id = ? ORDER BY seq ASC")
    .all(session.id);

  assert.equal(storedEvents.some((event) => event.type === "user_message"), true);
  assert.deepEqual(
    JSON.parse(storedEvents.find((event) => event.type === "user_message").payload),
    { text: "chapter opening prompt" }
  );
});

test("orchestrator.create does not synthesize a user_message for openclaw sessions", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-create-openclaw-no-synthetic-user-message-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  runtime.openClawBridge.isAvailable = () => true;
  runtime.openClawBridge.createSession = async () => ({
    providerSessionId: "openclaw-session-1",
    model: "openclaw"
  });

  const session = await runtime.orchestrator.create({
    provider: "openclaw",
    host_id: "relay-local",
    cwd: "/tmp/openclaw-demo",
    prompt: "  openclaw prompt should not be echoed synthetically  "
  });

  assert.equal(session.status, "running");

  const storedEvents = runtime.db
    .prepare("SELECT type, payload FROM session_events WHERE session_id = ? ORDER BY seq ASC")
    .all(session.id);

  assert.equal(storedEvents.some((event) => event.type === "user_message"), false);
});

test("orchestrator.sendMessage returns host_offline for empty idle sessions when the host disconnects before the first message", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-empty-session-host-offline-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  runtime.companionManager.isOnline = () => true;

  const session = await runtime.orchestrator.create({
    provider: "claude",
    host_id: "macbook-1",
    cwd: "/tmp/project"
  });

  runtime.companionManager.isOnline = () => false;
  runtime.companionManager.sendCommand = async () => {
    throw new Error("sendCommand should not be called when the host is offline");
  };

  await assert.rejects(runtime.orchestrator.sendMessage(session.id, "hello"), (error) => {
    assert.equal(error.code, "host_offline");
    return true;
  });

  const storedSession = runtime.db
    .prepare("SELECT status, initial_prompt, provider_session_id FROM sessions WHERE id = ?")
    .get(session.id);
  assert.deepEqual(storedSession, {
    status: "idle",
    initial_prompt: null,
    provider_session_id: null
  });
});

test("orchestrator.cancel skips provider commands for empty idle sessions", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-empty-session-cancel-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  runtime.companionManager.isOnline = () => true;

  const session = await runtime.orchestrator.create({
    provider: "claude",
    host_id: "macbook-1",
    cwd: "/tmp/project"
  });

  let sendCommandCalls = 0;
  runtime.companionManager.isOnline = () => false;
  runtime.companionManager.sendCommand = async () => {
    sendCommandCalls += 1;
    throw new Error("sendCommand should not be called for empty idle cancellation");
  };

  const cancelledSession = await runtime.orchestrator.cancel(session.id);

  assert.equal(cancelledSession.status, "cancelled");
  assert.equal(sendCommandCalls, 0);
});

test("orchestrator.complete skips provider commands for empty idle sessions", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-empty-session-complete-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  runtime.companionManager.isOnline = () => true;

  const session = await runtime.orchestrator.create({
    provider: "claude",
    host_id: "macbook-1",
    cwd: "/tmp/project"
  });

  let sendCommandCalls = 0;
  runtime.companionManager.isOnline = () => false;
  runtime.companionManager.sendCommand = async () => {
    sendCommandCalls += 1;
    throw new Error("sendCommand should not be called for empty idle completion");
  };

  const completedSession = await runtime.orchestrator.complete(session.id);

  assert.equal(completedSession.status, "completed");
  assert.equal(sendCommandCalls, 0);
});

test("orchestrator.delete auto-cancels idle interactive sessions before deleting them", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-idle-session-delete-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  insertSession(runtime.db, "sess-idle-delete", "idle");

  const sentCommands = [];
  runtime.companionManager.sendCommand = async (_hostId, command) => {
    sentCommands.push(command);
    return {
      type: "ack",
      status: "ok",
      req_id: command.req_id
    };
  };

  await runtime.orchestrator.delete("sess-idle-delete");

  const deletedSession = runtime.db.prepare("SELECT id FROM sessions WHERE id = ?").get("sess-idle-delete");
  assert.equal(deletedSession, undefined);
  assert.deepEqual(sentCommands.map((command) => command.cmd), ["cancel_session"]);
  assert.equal(sentCommands[0].session_id, "sess-idle-delete");
});

test("fresh database includes local_available in the sessions table schema", (t) => {
  const { db, cleanup } = createTestDatabase("imbot-relay-local-available-schema-");
  t.after(cleanup);

  const columns = db.pragma("table_info(sessions)");
  const localAvailableColumn = columns.find((column) => column.name === "local_available");

  assert.ok(localAvailableColumn);
  assert.equal(localAvailableColumn.type, "INTEGER");
  assert.equal(localAvailableColumn.notnull, 1);
  assert.equal(localAvailableColumn.dflt_value, "0");
});

test("local_available defaults to 0 for manual session inserts that omit the column", (t) => {
  const { db, cleanup } = createTestDatabase("imbot-relay-local-available-default-");
  t.after(cleanup);

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
    ) VALUES (?, 'openclaw', 'openclaw-session-1', 'relay-local', NULL, ?, ?, NULL, 'bypassPermissions', 'running', NULL, NULL, ?, ?, ?)
    `
  ).run("sess-default-local-available", "/srv/project", "hello", now, now, now);

  const storedSession = db
    .prepare("SELECT local_available FROM sessions WHERE id = ?")
    .get("sess-default-local-available");

  assert.deepEqual(storedSession, {
    local_available: 0
  });
});

test("initializeDatabase migrates an existing idle-capable sessions table to add local_available", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-local-available-migration-"));
  const dbPath = path.join(tempDir, "imbot.db");
  const legacyDb = new Database(dbPath);

  legacyDb.pragma("foreign_keys = ON");
  legacyDb.exec(`
    CREATE TABLE hosts (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      type TEXT NOT NULL CHECK (type IN ('macbook', 'relay_local')),
      status TEXT NOT NULL DEFAULT 'offline' CHECK (status IN ('online', 'offline')),
      last_heartbeat_at TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE sessions (
      id TEXT PRIMARY KEY,
      provider TEXT NOT NULL CHECK (provider IN ('claude', 'book', 'openclaw')),
      provider_session_id TEXT,
      host_id TEXT NOT NULL REFERENCES hosts(id),
      workspace_root TEXT,
      workspace_cwd TEXT NOT NULL,
      initial_prompt TEXT,
      model TEXT,
      permission_mode TEXT NOT NULL DEFAULT 'bypassPermissions',
      status TEXT NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'running', 'idle', 'completed', 'failed', 'cancelled')),
      error_message TEXT,
      error_code TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      last_active_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  const now = new Date().toISOString();
  legacyDb
    .prepare(
      `
      INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
      VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', ?, ?, ?)
      `
    )
    .run(now, now, now);
  legacyDb
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
      ) VALUES (?, 'claude', 'provider-session-legacy', 'macbook-1', NULL, ?, ?, NULL, 'bypassPermissions', 'completed', NULL, NULL, ?, ?, ?)
      `
    )
    .run("sess-local-migration", "/tmp/project", "hello", now, now, now);
  legacyDb.close();

  const migratedDb = initializeDatabase(dbPath);

  try {
    const migratedSession = migratedDb
      .prepare("SELECT provider, provider_session_id, local_available FROM sessions WHERE id = ?")
      .get("sess-local-migration");

    assert.deepEqual(migratedSession, {
      provider: "claude",
      provider_session_id: "provider-session-legacy",
      local_available: 1
    });
  } finally {
    migratedDb.close();
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("local_available migration does not rerun its backfill after the column already exists", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-local-available-idempotent-"));
  const dbPath = path.join(tempDir, "imbot.db");

  const firstDb = initializeDatabase(dbPath);
  const now = new Date().toISOString();

  firstDb
    .prepare(
      `
      INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
      VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', ?, ?, ?)
      `
    )
    .run(now, now, now);
  firstDb
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
        local_available,
        created_at,
        updated_at,
        last_active_at
      ) VALUES (?, 'claude', 'provider-session-existing', 'macbook-1', NULL, ?, ?, NULL, 'bypassPermissions', 'completed', NULL, NULL, 0, ?, ?, ?)
      `
    )
    .run("sess-existing-local-available", "/tmp/project", "hello", now, now, now);
  firstDb.close();

  const secondDb = initializeDatabase(dbPath);

  try {
    const columns = secondDb.pragma("table_info(sessions)");
    const localAvailableColumns = columns.filter((column) => column.name === "local_available");
    const preservedSession = secondDb
      .prepare("SELECT local_available FROM sessions WHERE id = ?")
      .get("sess-existing-local-available");

    assert.equal(localAvailableColumns.length, 1);
    assert.deepEqual(preservedSession, {
      local_available: 0
    });
  } finally {
    secondDb.close();
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("initializeDatabase deduplicates provider_session_id rows before enforcing the unique index", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-provider-session-index-"));
  const dbPath = path.join(tempDir, "imbot.db");
  const legacyDb = new Database(dbPath);

  legacyDb.pragma("foreign_keys = ON");
  legacyDb.exec(`
    CREATE TABLE hosts (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      type TEXT NOT NULL CHECK (type IN ('macbook', 'relay_local')),
      status TEXT NOT NULL DEFAULT 'offline' CHECK (status IN ('online', 'offline')),
      last_heartbeat_at TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE sessions (
      id TEXT PRIMARY KEY,
      provider TEXT NOT NULL CHECK (provider IN ('claude', 'book', 'openclaw')),
      provider_session_id TEXT,
      host_id TEXT NOT NULL REFERENCES hosts(id),
      workspace_root TEXT,
      workspace_cwd TEXT NOT NULL,
      initial_prompt TEXT,
      model TEXT,
      permission_mode TEXT NOT NULL DEFAULT 'bypassPermissions',
      status TEXT NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'running', 'idle', 'completed', 'failed', 'cancelled')),
      error_message TEXT,
      error_code TEXT,
      local_available INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      last_active_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  const now = new Date().toISOString();
  legacyDb
    .prepare(
      `
      INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
      VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', ?, ?, ?)
      `
    )
    .run(now, now, now);
  legacyDb
    .prepare(
      `
      INSERT INTO sessions (
        id,
        provider,
        provider_session_id,
        host_id,
        workspace_cwd,
        status,
        local_available,
        created_at,
        updated_at,
        last_active_at
      ) VALUES (?, 'claude', ?, 'macbook-1', ?, 'completed', 1, ?, ?, ?)
      `
    )
    .run("sess-duplicate-older", "provider-session-duplicate", "/tmp/project-older", now, now, now);
  legacyDb
    .prepare(
      `
      INSERT INTO sessions (
        id,
        provider,
        provider_session_id,
        host_id,
        workspace_cwd,
        status,
        local_available,
        created_at,
        updated_at,
        last_active_at
      ) VALUES (?, 'claude', ?, 'macbook-1', ?, 'completed', 1, ?, ?, ?)
      `
    )
    .run("sess-duplicate-newer", "provider-session-duplicate", "/tmp/project-newer", now, now, now);
  legacyDb.close();

  const migratedDb = initializeDatabase(dbPath);

  try {
    const deduplicatedRows = migratedDb
      .prepare("SELECT id, workspace_cwd FROM sessions WHERE provider_session_id = ?")
      .all("provider-session-duplicate");
    const indexes = migratedDb.pragma("index_list(sessions)");
    const providerSessionIndex = indexes.find((index) => index.name === "idx_sessions_provider_session_id");
    const insertTimestamp = new Date().toISOString();

    assert.deepEqual(deduplicatedRows, [
      {
        id: "sess-duplicate-newer",
        workspace_cwd: "/tmp/project-newer"
      }
    ]);
    assert.ok(providerSessionIndex);
    assert.equal(providerSessionIndex.unique, 1);
    assert.equal(providerSessionIndex.partial, 1);
    assert.throws(() => {
      migratedDb
        .prepare(
          `
          INSERT INTO sessions (
            id,
            provider,
            provider_session_id,
            host_id,
            workspace_cwd,
            status,
            local_available,
            created_at,
            updated_at,
            last_active_at
          ) VALUES (?, 'claude', ?, 'macbook-1', ?, 'completed', 1, ?, ?, ?)
          `
        )
        .run(
          "sess-duplicate-third",
          "provider-session-duplicate",
          "/tmp/project-third",
          insertTimestamp,
          insertTimestamp,
          insertTimestamp
        );
    }, /UNIQUE constraint failed: sessions.provider_session_id/);
  } finally {
    migratedDb.close();
    rmSync(tempDir, { recursive: true, force: true });
  }
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

test("handleEvent accepts transcript_sync message events after a session reaches a terminal state", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-late-transcript-events-"));
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
    insertSession(runtime.db, `sess-sync-${status}`, status);
  }

  for (const status of ["completed", "failed", "cancelled"]) {
    await runtime.orchestrator.handleEvent({
      type: "event",
      session_id: `sess-sync-${status}`,
      event_type: "assistant_message",
      payload: {
        text: `late-${status}`
      },
      source: "transcript_sync"
    });
    await runtime.orchestrator.handleEvent({
      type: "event",
      session_id: `sess-sync-${status}`,
      event_type: "session_usage",
      payload: {
        input_tokens: 1,
        output_tokens: 2
      },
      source: "transcript_sync"
    });
  }

  for (const status of ["completed", "failed", "cancelled"]) {
    const eventTypes = runtime.db
      .prepare("SELECT type FROM session_events WHERE session_id = ? ORDER BY seq ASC")
      .all(`sess-sync-${status}`)
      .map((row) => row.type);
    assert.deepEqual(eventTypes, ["assistant_message", "session_usage"]);

    const row = runtime.db
      .prepare("SELECT status FROM sessions WHERE id = ?")
      .get(`sess-sync-${status}`);
    assert.deepEqual(row, {
      status
    });
  }
});

test("handleEvent drops transcript_sync duplicates for the synthetic initial user_message", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-dedupe-synthetic-initial-user-message-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  runtime.companionManager.isOnline = () => true;
  runtime.companionManager.sendCommand = async () => ({
    type: "ack",
    status: "ok",
    data: {
      provider_session_id: "provider-session-create-prompt"
    }
  });

  const session = await runtime.orchestrator.create({
    provider: "book",
    host_id: "macbook-1",
    cwd: "/tmp/novel",
    prompt: "chapter opening prompt"
  });

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: session.id,
    event_type: "user_message",
    payload: {
      text: "chapter opening prompt"
    },
    source: "transcript_sync"
  });

  const userMessages = runtime.db
    .prepare("SELECT type, payload FROM session_events WHERE session_id = ? AND type = 'user_message' ORDER BY seq ASC")
    .all(session.id);

  assert.equal(userMessages.length, 1);
  assert.deepEqual(JSON.parse(userMessages[0].payload), {
    text: "chapter opening prompt"
  });
});

test("handleEvent recovers failed sessions when the companion reconnect reports them as running", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-failed-recovery-"));
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

  insertSession(runtime.db, "sess-recover", "running");

  await runtime.orchestrator.handleHostDisconnected("macbook-1");

  const failedSession = runtime.db
    .prepare("SELECT status, error_code FROM sessions WHERE id = ?")
    .get("sess-recover");
  assert.deepEqual(failedSession, {
    status: "failed",
    error_code: "host_disconnected"
  });

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-recover",
    event_type: "session_status_changed",
    payload: {
      status: "running"
    }
  });

  const recoveredSession = runtime.db
    .prepare("SELECT status, error_code, error_message FROM sessions WHERE id = ?")
    .get("sess-recover");
  assert.deepEqual(recoveredSession, {
    status: "running",
    error_code: null,
    error_message: null
  });

  const recoveryAuditLog = runtime.db
    .prepare("SELECT action, session_id, host_id, detail FROM audit_logs WHERE action = 'session.recover'")
    .get();
  assert.equal(recoveryAuditLog.action, "session.recover");
  assert.equal(recoveryAuditLog.session_id, "sess-recover");
  assert.equal(recoveryAuditLog.host_id, "macbook-1");
  assert.deepEqual(JSON.parse(recoveryAuditLog.detail), {
    previous_status: "failed",
    recovered_status: "running",
    source_event: "session_status_changed"
  });

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-recover",
    event_type: "assistant_message",
    payload: {
      text: "still running"
    }
  });

  const assistantEvents = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM session_events WHERE session_id = ? AND type = 'assistant_message'")
    .get("sess-recover");
  assert.deepEqual(assistantEvents, { count: 1 });
});

test("handleEvent stores and broadcasts session_idle before transitioning the session to idle", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-session-idle-"));
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

  const broadcasts = [];
  const originalBroadcastToSession = runtime.hub.broadcastToSession.bind(runtime.hub);
  runtime.hub.broadcastToSession = (sessionId, message) => {
    broadcasts.push({ sessionId, message });
    return originalBroadcastToSession(sessionId, message);
  };

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

  insertSession(runtime.db, "sess-idle-event", "running");

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-idle-event",
    event_type: "session_idle",
    payload: {
      result: {
        turn: 1
      }
    }
  });

  const updatedSession = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get("sess-idle-event");
  assert.deepEqual(updatedSession, {
    status: "idle"
  });

  const storedEvents = runtime.db
    .prepare("SELECT type, payload FROM session_events WHERE session_id = ? ORDER BY seq ASC")
    .all("sess-idle-event");
  assert.deepEqual(
    storedEvents.map((event) => ({
      type: event.type,
      payload: JSON.parse(event.payload)
    })),
    [
      {
        type: "session_idle",
        payload: {
          result: {
            turn: 1
          }
        }
      },
      {
        type: "session_status_changed",
        payload: {
          status: "idle",
          error_code: null,
          error_message: null
        }
      }
    ]
  );

  assert.equal(
    broadcasts.some(
      (entry) =>
        entry.sessionId === "sess-idle-event" &&
        entry.message.type === "event" &&
        entry.message.event_type === "session_idle"
    ),
    true
  );
  assert.equal(
    broadcasts.some(
      (entry) =>
        entry.sessionId === "sess-idle-event" &&
        entry.message.type === "event" &&
        entry.message.event_type === "session_status_changed" &&
        entry.message.payload.status === "idle"
    ),
    true
  );
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

  await runtime.orchestrator.markSessionStarted("sess-create-result", "provider-session-create-result", "queued");
  await runtime.orchestrator.markSessionStarted("sess-create-error", "provider-session-create-error", "queued");
  await runtime.orchestrator.markSessionStarted("sess-resume-result", "provider-session-resume-result", "completed");
  await runtime.orchestrator.markSessionStarted("sess-resume-error", "provider-session-resume-error", "failed");

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

test("handleEvent defers session_idle during create and resume lifecycle windows until the session reaches running", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-active-mutation-idle-"));
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

  insertSession(runtime.db, "sess-create-idle", "queued");
  insertSession(runtime.db, "sess-resume-idle", "completed");

  runtime.orchestrator.activeLifecycleMutations.set("sess-create-idle", "create");
  runtime.orchestrator.activeLifecycleMutations.set("sess-resume-idle", "resume");

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-create-idle",
    event_type: "session_idle",
    payload: {
      result: "created and immediately idle"
    }
  });

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-resume-idle",
    event_type: "session_idle",
    payload: {
      result: "resumed and immediately idle"
    }
  });

  assert.deepEqual(
    runtime.db
      .prepare("SELECT id, status FROM sessions WHERE id IN (?, ?) ORDER BY id ASC")
      .all("sess-create-idle", "sess-resume-idle"),
    [
      { id: "sess-create-idle", status: "queued" },
      { id: "sess-resume-idle", status: "completed" }
    ]
  );

  await runtime.orchestrator.markSessionStarted("sess-create-idle", "provider-session-create-idle", "queued");
  await runtime.orchestrator.markSessionStarted("sess-resume-idle", "provider-session-resume-idle", "completed");

  await runtime.orchestrator.applyPendingTerminalTransition("sess-create-idle");
  await runtime.orchestrator.applyPendingTerminalTransition("sess-resume-idle");

  assert.deepEqual(
    runtime.db
      .prepare("SELECT id, status FROM sessions WHERE id IN (?, ?) ORDER BY id ASC")
      .all("sess-create-idle", "sess-resume-idle"),
    [
      { id: "sess-create-idle", status: "idle" },
      { id: "sess-resume-idle", status: "idle" }
    ]
  );
});

test("handleHostDisconnected also fails idle sessions", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-idle-host-disconnect-"));
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

  insertSession(runtime.db, "sess-idle-disconnect", "idle");

  await runtime.orchestrator.handleHostDisconnected("macbook-1");

  const failedSession = runtime.db
    .prepare("SELECT status, error_code, error_message FROM sessions WHERE id = ?")
    .get("sess-idle-disconnect");
  assert.deepEqual(failedSession, {
    status: "failed",
    error_code: "host_disconnected",
    error_message: "Host companion disconnected unexpectedly"
  });
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

test("orchestrator normalizes invalid permission_mode to bypassPermissions", async (t) => {
  const { tempDir, runtime } = await createRelayRuntime("imbot-relay-perm-mode-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  insertHost(runtime.db);
  runtime.companionManager.isOnline = () => true;

  const session = await runtime.orchestrator.create({
    provider: "claude",
    host_id: "macbook-1",
    cwd: "/tmp/test-perm",
    permission_mode: "--inject-flag"
  });

  assert.equal(session.status, "idle");
  const row = runtime.db
    .prepare("SELECT permission_mode FROM sessions WHERE id = ?")
    .get(session.id);
  assert.equal(row.permission_mode, "bypassPermissions");
});
