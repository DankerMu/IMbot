import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");

function createConfig(tempDir) {
  return relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1"
  });
}

async function createRuntime(t, prefix) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), prefix));
  const config = createConfig(tempDir);
  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  return { config, runtime };
}

function insertHost(db, hostId = "macbook-1") {
  const now = new Date().toISOString();
  db.prepare(
    `
    INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
    VALUES (?, ?, 'macbook', 'online', ?, ?, ?)
    `
  ).run(hostId, hostId, now, now, now);
}

function insertSession(
  db,
  {
    id = "sess-existing",
    provider = "claude",
    providerSessionId = "provider-session-existing",
    hostId = "macbook-1",
    cwd = "/tmp/project",
    status = "queued",
    localAvailable = 0,
    createdAt = new Date().toISOString()
  } = {}
) {
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
      local_available,
      created_at,
      updated_at,
      last_active_at
    ) VALUES (?, ?, ?, ?, NULL, ?, NULL, NULL, 'bypassPermissions', ?, NULL, NULL, ?, ?, ?, ?)
    `
  ).run(id, provider, providerSessionId, hostId, cwd, status, localAvailable, createdAt, createdAt, createdAt);
}

function buildMessage(sessions, hostId = "ignored-by-relay") {
  return {
    type: "report_local_sessions",
    host_id: hostId,
    sessions
  };
}

test("handleReportLocalSessions creates shadow records for unknown sessions", async (t) => {
  const { runtime } = await createRuntime(t, "imbot-relay-local-sync-create-");
  insertHost(runtime.db);

  await runtime.orchestrator.handleReportLocalSessions(
    buildMessage([
      {
        provider_session_id: "provider-session-shadow-1",
        provider: "claude",
        cwd: "/tmp/project-a",
        created_at: "2026-04-01T00:00:00.000Z"
      },
      {
        provider_session_id: "provider-session-shadow-2",
        provider: "book",
        cwd: "/tmp/project-b",
        created_at: "2026-04-01T01:00:00.000Z"
      }
    ]),
    "macbook-1"
  );

  const rows = runtime.db
    .prepare(
      `
      SELECT provider, provider_session_id, host_id, workspace_cwd, status, local_available
      FROM sessions
      ORDER BY provider_session_id ASC
      `
    )
    .all();

  assert.deepEqual(rows, [
    {
      provider: "claude",
      provider_session_id: "provider-session-shadow-1",
      host_id: "macbook-1",
      workspace_cwd: "/tmp/project-a",
      status: "completed",
      local_available: 1
    },
    {
      provider: "book",
      provider_session_id: "provider-session-shadow-2",
      host_id: "macbook-1",
      workspace_cwd: "/tmp/project-b",
      status: "completed",
      local_available: 1
    }
  ]);
});

test("handleReportLocalSessions is idempotent when provider_session_id repeats", async (t) => {
  const { runtime } = await createRuntime(t, "imbot-relay-local-sync-idempotent-");
  insertHost(runtime.db);

  const message = buildMessage([
    {
      provider_session_id: "provider-session-dup",
      provider: "claude",
      cwd: "/tmp/project",
      created_at: "2026-04-01T00:00:00.000Z"
    }
  ]);

  await runtime.orchestrator.handleReportLocalSessions(message, "macbook-1");
  await runtime.orchestrator.handleReportLocalSessions(message, "macbook-1");

  const count = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM sessions WHERE provider_session_id = ?")
    .get("provider-session-dup");

  assert.deepEqual(count, { count: 1 });
});

test("handleReportLocalSessions updates local_available from 0 to 1 for existing sessions", async (t) => {
  const { runtime } = await createRuntime(t, "imbot-relay-local-sync-update-");
  insertHost(runtime.db);
  insertSession(runtime.db, {
    id: "sess-existing-local",
    providerSessionId: "provider-session-existing",
    localAvailable: 0,
    status: "queued"
  });

  await runtime.orchestrator.handleReportLocalSessions(
    buildMessage([
      {
        provider_session_id: "provider-session-existing",
        provider: "claude",
        cwd: "/tmp/project",
        created_at: "2026-04-01T02:00:00.000Z"
      }
    ]),
    "macbook-1"
  );

  const row = runtime.db
    .prepare("SELECT id, status, local_available FROM sessions WHERE provider_session_id = ?")
    .get("provider-session-existing");

  assert.deepEqual(row, {
    id: "sess-existing-local",
    status: "queued",
    local_available: 1
  });
});

test("handleReportLocalSessions skips sessions with empty provider_session_id", async (t) => {
  const { runtime } = await createRuntime(t, "imbot-relay-local-sync-empty-");
  insertHost(runtime.db);

  await runtime.orchestrator.handleReportLocalSessions(
    buildMessage([
      {
        provider_session_id: "",
        provider: "claude",
        cwd: "/tmp/project",
        created_at: "2026-04-01T03:00:00.000Z"
      }
    ]),
    "macbook-1"
  );

  const count = runtime.db.prepare("SELECT COUNT(*) AS count FROM sessions").get();
  assert.deepEqual(count, { count: 0 });
});

test("handleReportLocalSessions drops the message for an unknown host", async (t) => {
  const { runtime } = await createRuntime(t, "imbot-relay-local-sync-unknown-host-");

  await runtime.orchestrator.handleReportLocalSessions(
    buildMessage([
      {
        provider_session_id: "provider-session-missing-host",
        provider: "claude",
        cwd: "/tmp/project",
        created_at: "2026-04-01T04:00:00.000Z"
      }
    ]),
    "macbook-missing"
  );

  const count = runtime.db.prepare("SELECT COUNT(*) AS count FROM sessions").get();
  assert.deepEqual(count, { count: 0 });
});

test("handleReportLocalSessions handles a batch of 50 sessions", async (t) => {
  const { runtime } = await createRuntime(t, "imbot-relay-local-sync-batch-");
  insertHost(runtime.db);

  const sessions = Array.from({ length: 50 }, (_, index) => ({
    provider_session_id: `provider-session-${index + 1}`,
    provider: index % 2 === 0 ? "claude" : "book",
    cwd: `/tmp/project-${index + 1}`,
    created_at: `2026-04-01T00:${String(index).padStart(2, "0")}:00.000Z`
  }));

  await runtime.orchestrator.handleReportLocalSessions(buildMessage(sessions), "macbook-1");

  const count = runtime.db.prepare("SELECT COUNT(*) AS count FROM sessions").get();
  assert.deepEqual(count, { count: 50 });
});

test("handleReportLocalSessions writes an audit log when sync changes sessions", async (t) => {
  const { runtime } = await createRuntime(t, "imbot-relay-local-sync-audit-");
  insertHost(runtime.db);
  insertSession(runtime.db, {
    id: "sess-audit-existing",
    providerSessionId: "provider-session-audit-existing",
    localAvailable: 0,
    status: "completed"
  });

  await runtime.orchestrator.handleReportLocalSessions(
    buildMessage([
      {
        provider_session_id: "provider-session-audit-new",
        provider: "claude",
        cwd: "/tmp/project-new",
        created_at: "2026-04-01T05:00:00.000Z"
      },
      {
        provider_session_id: "provider-session-audit-existing",
        provider: "claude",
        cwd: "/tmp/project-existing",
        created_at: "2026-04-01T05:01:00.000Z"
      },
      {
        provider_session_id: "",
        provider: "book",
        cwd: "/tmp/project-skip",
        created_at: "2026-04-01T05:02:00.000Z"
      }
    ]),
    "macbook-1"
  );

  const auditRow = runtime.db
    .prepare("SELECT action, host_id, detail FROM audit_logs WHERE action = 'session.local_sync' ORDER BY created_at DESC LIMIT 1")
    .get();

  assert.equal(auditRow.action, "session.local_sync");
  assert.equal(auditRow.host_id, "macbook-1");
  assert.deepEqual(JSON.parse(auditRow.detail), {
    created: 1,
    updated: 1,
    skipped: 1
  });
});

test("shadow sessions are queryable via GET /sessions", async (t) => {
  const { config, runtime } = await createRuntime(t, "imbot-relay-local-sync-list-");
  insertHost(runtime.db);

  await runtime.orchestrator.handleReportLocalSessions(
    buildMessage([
      {
        provider_session_id: "provider-session-list-shadow",
        provider: "claude",
        cwd: "/tmp/project-shadow",
        created_at: "2026-04-01T06:00:00.000Z"
      }
    ]),
    "macbook-1"
  );

  const response = await runtime.app.inject({
    method: "GET",
    url: "/v1/sessions",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  assert.equal(response.statusCode, 200);
  const payload = response.json();
  const shadowSession = payload.sessions.find(
    (session) => session.provider_session_id === "provider-session-list-shadow"
  );

  assert.ok(shadowSession);
  assert.equal(shadowSession.status, "completed");
  assert.equal(shadowSession.local_available, true);
  assert.equal(shadowSession.host_id, "macbook-1");
});
