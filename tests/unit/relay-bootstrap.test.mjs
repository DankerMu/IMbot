import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");

test("relay loads defaults, initializes schema, and protects REST routes", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-bootstrap-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error"
  });

  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  assert.equal(config.host, "0.0.0.0");
  assert.equal(config.port, 3000);
  assert.equal(config.companionTimeoutMs, 30000);
  assert.equal(config.heartbeatIntervalMs, 60000);
  assert.equal(config.heartbeatStaleMs, 90000);

  const healthResponse = await runtime.app.inject({
    method: "GET",
    url: "/healthz"
  });
  assert.equal(healthResponse.statusCode, 200);
  assert.equal(healthResponse.json().status, "ok");
  assert.equal(healthResponse.json().db, "ok");
  assert.equal(healthResponse.json().companion, "offline");

  const unauthorizedResponse = await runtime.app.inject({
    method: "GET",
    url: "/v1/sessions"
  });
  assert.equal(unauthorizedResponse.statusCode, 401);
  assert.deepEqual(unauthorizedResponse.json(), { error: "unauthenticated" });

  const originalPrepare = runtime.db.prepare.bind(runtime.db);
  let prepareCalls = 0;
  runtime.db.prepare = (...args) => {
    prepareCalls += 1;
    return originalPrepare(...args);
  };

  const unauthorizedPostResponse = await runtime.app.inject({
    method: "POST",
    url: "/v1/sessions",
    payload: {
      provider: "claude",
      host_id: "macbook-1",
      cwd: "/tmp/project",
      prompt: "help me refactor"
    }
  });
  assert.equal(unauthorizedPostResponse.statusCode, 401);
  assert.deepEqual(unauthorizedPostResponse.json(), { error: "unauthenticated" });
  assert.equal(prepareCalls, 0);

  runtime.db.prepare = originalPrepare;

  const authorizedResponse = await runtime.app.inject({
    method: "GET",
    url: "/v1/sessions",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  assert.equal(authorizedResponse.statusCode, 200);
  assert.deepEqual(authorizedResponse.json(), {
    sessions: [],
    total: 0,
    limit: 50,
    offset: 0
  });

  const tableNames = runtime.db
    .prepare("SELECT name FROM sqlite_master WHERE type = 'table'")
    .all()
    .map((row) => row.name);

  assert.ok(tableNames.includes("hosts"));
  assert.ok(tableNames.includes("workspace_roots"));
  assert.ok(tableNames.includes("sessions"));
  assert.ok(tableNames.includes("session_events"));
  assert.ok(tableNames.includes("approvals"));
  assert.ok(tableNames.includes("push_subscriptions"));
  assert.ok(tableNames.includes("audit_logs"));

  const relayLocal = runtime.db
    .prepare("SELECT id, status FROM hosts WHERE id = 'relay-local'")
    .get();
  assert.deepEqual(relayLocal, {
    id: "relay-local",
    status: "online"
  });
});

test("relay does not insert a session when the target host is offline", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-host-offline-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error"
  });

  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const response = await runtime.app.inject({
    method: "POST",
    url: "/v1/sessions",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    payload: {
      provider: "claude",
      host_id: "macbook-1",
      cwd: "/tmp/project",
      prompt: "help me refactor"
    }
  });

  assert.equal(response.statusCode, 502);
  assert.deepEqual(response.json(), { error: "host_offline" });

  const sessionCount = runtime.db.prepare("SELECT COUNT(*) AS count FROM sessions").get();
  assert.deepEqual(sessionCount, { count: 0 });
});

test("relay requires RELAY_STATIC_TOKEN", () => {
  assert.throws(() => {
    relay.loadConfig({});
  }, /RELAY_STATIC_TOKEN/);
});

test("relay rejects non-positive timeout and heartbeat values", () => {
  assert.throws(() => {
    relay.loadConfig({
      RELAY_STATIC_TOKEN: "t".repeat(64),
      RELAY_COMPANION_TIMEOUT_MS: "0"
    });
  }, /RELAY_COMPANION_TIMEOUT_MS/);

  assert.throws(() => {
    relay.loadConfig({
      RELAY_STATIC_TOKEN: "t".repeat(64),
      RELAY_WS_PING_INTERVAL_MS: "-1"
    });
  }, /RELAY_WS_PING_INTERVAL_MS/);
});

test("relay rejects malformed events limit values", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-events-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error"
  });

  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const response = await runtime.app.inject({
    method: "GET",
    url: "/v1/sessions/does-not-matter/events?since_seq=0&limit=abc",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  assert.equal(response.statusCode, 400);
  assert.deepEqual(response.json(), { error: "invalid_request" });
});

test("relay normalizes unknown session_error payload codes before persisting session state", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-session-error-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error"
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
      VALUES (?, ?, 'macbook', 'online', ?, ?, ?)
      `
    )
    .run("macbook-1", "macbook-1", now, now, now);

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
      "sess-normalize",
      "claude",
      "provider-session-1",
      "macbook-1",
      null,
      "/tmp/project",
      "help me refactor",
      null,
      "bypassPermissions",
      "running",
      null,
      null,
      now,
      now,
      now
    );

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-normalize",
    event_type: "session_error",
    payload: {
      error_code: "unexpected_error_code",
      message: "Companion exploded"
    }
  });

  const failedSession = runtime.db
    .prepare("SELECT status, error_code, error_message FROM sessions WHERE id = ?")
    .get("sess-normalize");

  assert.deepEqual(failedSession, {
    status: "failed",
    error_code: "provider_unreachable",
    error_message: "Companion exploded"
  });
});
