import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const { mapRuntimeEvent } = require("../../packages/companion/dist/runtime/event-mapper.js");

class MockAndroidSocket {
  constructor() {
    this.readyState = 1;
    this.messages = [];
  }

  send(raw) {
    this.messages.push(JSON.parse(String(raw)));
  }

  on() {}

  off() {}

  once() {}

  ping() {}

  close() {}
}

async function createRelayRuntime(t, prefix) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), prefix));
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

  return runtime;
}

function insertRunningSession(db, sessionId) {
  const now = new Date().toISOString();

  db.prepare(
    `
    INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
    VALUES ('macbook-1', 'macbook-1', 'macbook', 'online', ?, ?, ?)
    `
  ).run(now, now, now);

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
    ) VALUES (?, 'claude', 'provider-session-1', 'macbook-1', NULL, ?, ?, NULL, 'default', 'running', NULL, NULL, ?, ?, ?)
    `
  ).run(sessionId, "/tmp/project", "approval flow", now, now, now);
}

test("mapRuntimeEvent preserves approval event payloads for the reserved path", () => {
  const approvalRequired = mapRuntimeEvent({
    type: "approval_required",
    call_id: "call-1",
    tool_name: "bash",
    description: "Run a shell command"
  });
  const approvalResolved = mapRuntimeEvent({
    type: "approval_resolved",
    call_id: "call-1",
    tool_name: "bash",
    description: "Run a shell command",
    resolution: "approved"
  });

  assert.deepEqual(approvalRequired, {
    kind: "event",
    eventType: "approval_required",
    payload: {
      call_id: "call-1",
      tool_name: "bash",
      description: "Run a shell command"
    }
  });
  assert.deepEqual(approvalResolved, {
    kind: "event",
    eventType: "approval_resolved",
    payload: {
      call_id: "call-1",
      tool_name: "bash",
      description: "Run a shell command",
      resolution: "approved"
    }
  });
});

test("relay stores and broadcasts approval events in order without changing session status", async (t) => {
  const runtime = await createRelayRuntime(t, "imbot-approval-events-");
  insertRunningSession(runtime.db, "sess-approval");

  const socket = new MockAndroidSocket();
  const clientId = runtime.hub.addAndroidClient(socket);
  runtime.hub.subscribe(clientId, "sess-approval");

  const approvalRequiredPayload = {
    call_id: "call-approval-1",
    tool_name: "bash",
    description: "Run a shell command"
  };
  const approvalResolvedPayload = {
    call_id: "call-approval-1",
    tool_name: "bash",
    description: "Run a shell command",
    resolution: "approved"
  };

  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-approval",
    event_type: "approval_required",
    payload: approvalRequiredPayload
  });
  await runtime.orchestrator.handleEvent({
    type: "event",
    session_id: "sess-approval",
    event_type: "approval_resolved",
    payload: approvalResolvedPayload
  });

  const storedEvents = runtime.db
    .prepare(
      `
      SELECT seq, type, payload
      FROM session_events
      WHERE session_id = ?
      ORDER BY seq ASC
      `
    )
    .all("sess-approval");

  assert.deepEqual(
    storedEvents.map((event) => event.type),
    ["approval_required", "approval_resolved"]
  );
  assert.deepEqual(JSON.parse(storedEvents[0].payload), approvalRequiredPayload);
  assert.deepEqual(JSON.parse(storedEvents[1].payload), approvalResolvedPayload);
  assert.equal(JSON.parse(storedEvents[0].payload).tool_name, "bash");
  assert.equal(JSON.parse(storedEvents[0].payload).description, "Run a shell command");
  assert.equal(JSON.parse(storedEvents[0].payload).call_id, "call-approval-1");

  assert.deepEqual(
    socket.messages.map((message) => message.event_type),
    ["approval_required", "approval_resolved"]
  );
  assert.deepEqual(socket.messages[0].payload, approvalRequiredPayload);
  assert.deepEqual(socket.messages[1].payload, approvalResolvedPayload);

  const session = runtime.db
    .prepare("SELECT status FROM sessions WHERE id = ?")
    .get("sess-approval");
  assert.deepEqual(session, {
    status: "running"
  });
});
