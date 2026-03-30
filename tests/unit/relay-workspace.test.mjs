import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const {
  isPathWithinRoot,
  validateWorkspacePath
} = require("../../packages/relay/dist/util/path-security.js");

function insertRunningSession(db, sessionId, hostId, now) {
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
    ) VALUES (?, 'claude', 'provider-session-1', ?, NULL, ?, ?, NULL, 'bypassPermissions', 'running', NULL, NULL, ?, ?, ?)
    `
  ).run(sessionId, hostId, "/tmp/project", "hello", now, now, now);
}

test("validateWorkspacePath rejects traversal and enforces root containment", () => {
  const rootPath = "/tmp/workspaces";

  assert.equal(isPathWithinRoot("/tmp/workspaces/project-a", rootPath), true);
  assert.equal(isPathWithinRoot("/tmp/other/project-a", rootPath), false);
  assert.deepEqual(validateWorkspacePath("/tmp/workspaces/project-a", [rootPath]), {
    ok: true,
    resolvedPath: "/tmp/workspaces/project-a"
  });
  assert.deepEqual(validateWorkspacePath("/tmp/workspaces/../secrets", [rootPath]), {
    ok: false,
    resolvedPath: "/tmp/secrets"
  });
});

test("stale heartbeat sweep marks hosts offline and fails running sessions", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-workspace-unit-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error",
    RELAY_HEARTBEAT_INTERVAL_MS: "60000",
    RELAY_HEARTBEAT_STALE_MS: "90000",
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

  const staleHeartbeatAt = "2026-03-30T00:00:00.000Z";
  runtime.db
    .prepare(
      `
      INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
      VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', ?, ?, ?)
      `
    )
    .run(staleHeartbeatAt, staleHeartbeatAt, staleHeartbeatAt);
  insertRunningSession(runtime.db, "sess-stale", "macbook-1", staleHeartbeatAt);

  const staleHosts = await runtime.companionManager.markStaleHostsOffline(
    new Date("2026-03-30T00:02:00.000Z")
  );

  assert.deepEqual(staleHosts, ["macbook-1"]);
  assert.deepEqual(
    runtime.db.prepare("SELECT status FROM hosts WHERE id = 'macbook-1'").get(),
    {
      status: "offline"
    }
  );
  assert.deepEqual(
    runtime.db
      .prepare("SELECT status, error_code, error_message FROM sessions WHERE id = 'sess-stale'")
      .get(),
    {
      status: "failed",
      error_code: "host_disconnected",
      error_message: "Host companion disconnected unexpectedly"
    }
  );
  assert.deepEqual(
    JSON.parse(
      runtime.db
        .prepare(
          `
          SELECT detail
          FROM audit_logs
          WHERE action = 'host.offline' AND host_id = 'macbook-1'
          ORDER BY created_at DESC
          LIMIT 1
          `
        )
        .get().detail
    ),
    {
      reason: "heartbeat_timeout"
    }
  );
  assert.deepEqual(
    runtime.db.prepare("SELECT status FROM hosts WHERE id = 'relay-local'").get(),
    {
      status: "online"
    }
  );
});
