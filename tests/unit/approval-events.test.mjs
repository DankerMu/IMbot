import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import { PassThrough } from "node:stream";
import test from "node:test";

const require = createRequire(import.meta.url);
const companion = require("../../packages/companion/dist/index.js");
const { initializeDatabase } = require("../../packages/relay/dist/db/init.js");
const { WsHub } = require("../../packages/relay/dist/ws/hub.js");
const { CompanionManager } = require("../../packages/relay/dist/companion/manager.js");
const { SessionOrchestrator } = require("../../packages/relay/dist/session/orchestrator.js");
const { AuditLogger } = require("../../packages/relay/dist/audit/logger.js");
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

const silentLogger = {
  debug() {},
  info() {},
  warn() {},
  error() {}
};

class MockChildProcess extends EventEmitter {
  constructor() {
    super();
    this.stdin = new PassThrough();
    this.stdout = new PassThrough();
    this.stderr = new PassThrough();
    this.exitCode = null;
  }

  emitJson(message) {
    this.stdout.write(`${JSON.stringify(message)}\n`);
  }

  kill(signal = "SIGTERM") {
    queueMicrotask(() => {
      this.close(signal === "SIGINT" ? 130 : 0, null);
    });
    return true;
  }

  close(code = 0, signal = null) {
    this.exitCode = code;
    this.stdout.end();
    this.stderr.end();
    this.stdin.end();
    this.emit("close", code, signal);
  }
}

function createAdapterHarness(tempDir) {
  const sessionIndex = new companion.SessionIndex({
    filePath: path.join(tempDir, "sessions.json"),
    logger: silentLogger
  });
  const events = [];
  const children = [];
  const adapter = new companion.ClaudeRuntimeAdapter({
    providers: {
      claude: {
        binary: "claude"
      }
    },
    sessionIndex,
    logger: silentLogger,
    sendEvent: (message) => {
      events.push(message);
    },
    spawn: () => {
      const child = new MockChildProcess();
      children.push(child);
      queueMicrotask(() => {
        child.emitJson({
          type: "system",
          subtype: "init",
          session_id: "provider-session-1"
        });
      });
      return child;
    }
  });

  return {
    adapter,
    children,
    events
  };
}

function createTestHarness(t, prefix) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), prefix));
  const db = initializeDatabase(path.join(tempDir, "imbot.db"));
  const hub = new WsHub(600000);
  const config = {
    companionTimeoutMs: 2000,
    heartbeatIntervalMs: 600000,
    heartbeatStaleMs: 600000
  };
  const companionManager = new CompanionManager(config, db, hub, silentLogger);
  const auditLogger = new AuditLogger(db, silentLogger);
  const orchestrator = new SessionOrchestrator(
    config,
    db,
    hub,
    companionManager,
    { sendMessage() {}, cancelSession() {}, isProviderSession() { return false; }, shutdown() {}, connect() {} },
    auditLogger,
    { notifySessionEvent() {}, notifyHostOffline() {}, init() {} },
    silentLogger
  );

  t.after(() => {
    companionManager.shutdown();
    hub.closeAll(1001, "test").catch(() => {});
    db.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  return { db, hub, orchestrator };
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

test("mapRuntimeEvent handles minimal approval event with only call_id", () => {
  const minimal = mapRuntimeEvent({
    type: "approval_required",
    call_id: "call-2"
  });

  assert.deepEqual(minimal, {
    kind: "event",
    eventType: "approval_required",
    payload: {
      call_id: "call-2"
    }
  });
});

test("mapRuntimeEvent extracts assistant text from Claude stream-json message content blocks", () => {
  const mapped = mapRuntimeEvent({
    type: "assistant",
    message: {
      role: "assistant",
      content: [
        {
          type: "thinking",
          thinking: "internal only"
        },
        {
          type: "text",
          text: "first line"
        },
        {
          type: "text",
          text: "\nsecond line"
        }
      ]
    },
    session_id: "provider-session-1"
  });

  assert.deepEqual(mapped, {
    kind: "event",
    eventType: "assistant_delta",
    payload: {
      text: "first line\nsecond line"
    }
  });
});

test("ClaudeRuntimeAdapter emits session_idle instead of session_result when the process remains alive", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-approval-session-idle-"));
  const projectDir = path.join(tempDir, "project");
  const { adapter, children, events } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await adapter.createSession({
    cmd: "create_session",
    req_id: "req-session-idle",
    session_id: "relay-session-1",
    provider: "claude",
    cwd: projectDir,
    prompt: "hello",
    permission_mode: "bypassPermissions"
  });

  children[0].emitJson({
    type: "result",
    result: "turn complete"
  });
  await Promise.resolve();
  await Promise.resolve();

  assert.deepEqual(events.at(-1), {
    type: "event",
    session_id: "relay-session-1",
    event_type: "session_idle",
    payload: {
      result: "turn complete"
    }
  });
  assert.equal(events.some((event) => event.event_type === "session_result"), false);
});

test("relay stores and broadcasts approval events in order without changing session status", async (t) => {
  const { db, hub, orchestrator } = createTestHarness(t, "imbot-approval-events-");
  insertRunningSession(db, "sess-approval");

  const socket = new MockAndroidSocket();
  const clientId = hub.addAndroidClient(socket);
  hub.subscribe(clientId, "sess-approval");

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

  await orchestrator.handleEvent({
    type: "event",
    session_id: "sess-approval",
    event_type: "approval_required",
    payload: approvalRequiredPayload
  });
  await orchestrator.handleEvent({
    type: "event",
    session_id: "sess-approval",
    event_type: "approval_resolved",
    payload: approvalResolvedPayload
  });

  const storedEvents = db
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

  const session = db
    .prepare("SELECT status FROM sessions WHERE id = ?")
    .get("sess-approval");
  assert.deepEqual(session, {
    status: "running"
  });
});
