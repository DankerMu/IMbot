import assert from "node:assert/strict";
import {
  chmodSync,
  mkdirSync,
  mkdtempSync,
  rmSync,
  writeFileSync
} from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const companion = require("../../packages/companion/dist/index.js");

const silentLogger = {
  debug() {},
  info() {},
  warn() {},
  error() {}
};

function waitForOpen(ws, label) {
  return new Promise((resolve, reject) => {
    const cleanup = () => {
      ws.removeEventListener("open", onOpen);
      ws.removeEventListener("error", onError);
    };

    const onOpen = () => {
      cleanup();
      resolve();
    };

    const onError = (event) => {
      cleanup();
      reject(new Error(`${label} websocket failed to open: ${event.message ?? "unknown error"}`));
    };

    ws.addEventListener("open", onOpen, { once: true });
    ws.addEventListener("error", onError, { once: true });
  });
}

function waitForJsonMessage(ws, predicate, label, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out waiting for ${label}`));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      ws.removeEventListener("message", onMessage);
      ws.removeEventListener("close", onClose);
      ws.removeEventListener("error", onError);
    };

    const onMessage = (event) => {
      const message = JSON.parse(String(event.data));
      if (!predicate(message)) {
        return;
      }

      cleanup();
      resolve(message);
    };

    const onClose = () => {
      cleanup();
      reject(new Error(`WebSocket closed while waiting for ${label}`));
    };

    const onError = (event) => {
      cleanup();
      reject(new Error(`WebSocket error while waiting for ${label}: ${event.message ?? "unknown"}`));
    };

    ws.addEventListener("message", onMessage);
    ws.addEventListener("close", onClose, { once: true });
    ws.addEventListener("error", onError, { once: true });
  });
}

async function waitForCondition(check, label, timeoutMs = 5000, intervalMs = 25) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    if (await check()) {
      return;
    }

    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  throw new Error(`Timed out waiting for ${label}`);
}

function createMockCliBinary(tempDir) {
  const scriptPath = path.join(tempDir, "mock-approval-cli.js");
  writeFileSync(
    scriptPath,
    `#!/usr/bin/env node
const args = process.argv.slice(2);
const permissionModeIndex = args.indexOf("--permission-mode");
const permissionMode = permissionModeIndex >= 0 ? args[permissionModeIndex + 1] : "bypassPermissions";
const providerSessionId = process.env.MOCK_CLI_PROVIDER_SESSION_ID || "provider-session-1";
const approvalSequence = process.env.MOCK_CLI_APPROVAL_SEQUENCE || "required-resolved";
const requiredDelayMs = Number(process.env.MOCK_CLI_APPROVAL_REQUIRED_DELAY_MS || "250");
const resolvedDelayMs = Number(process.env.MOCK_CLI_APPROVAL_RESOLVED_DELAY_MS || "450");
const resultDelayMs = Number(process.env.MOCK_CLI_RESULT_DELAY_MS || "700");

function emit(message) {
  process.stdout.write(JSON.stringify(message) + "\\n");
}

emit({ type: "system", session_id: providerSessionId });

if (permissionMode !== "bypassPermissions") {
  const payload = {
    call_id: "call-approval-1",
    tool_name: "bash",
    description: "Run a shell command"
  };

  if (approvalSequence === "required" || approvalSequence === "required-resolved") {
    setTimeout(() => {
      emit({ type: "approval_required", ...payload });
    }, requiredDelayMs);
  }

  if (approvalSequence === "required-resolved") {
    setTimeout(() => {
      emit({ type: "approval_resolved", ...payload, resolution: "approved" });
    }, resolvedDelayMs);
  }
}

setTimeout(() => {
  emit({ type: "result", result: "done" });
  process.exit(0);
}, resultDelayMs);
`,
    "utf8"
  );
  chmodSync(scriptPath, 0o755);
  return scriptPath;
}

function setMockCliEnv(overrides) {
  const previous = new Map();

  for (const [key, value] of Object.entries(overrides)) {
    previous.set(key, process.env[key]);
    process.env[key] = value;
  }

  return () => {
    for (const [key, value] of previous.entries()) {
      if (value == null) {
        delete process.env[key];
        continue;
      }

      process.env[key] = value;
    }
  };
}

async function createApprovalHarness(t, options = {}) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-approval-path-"));
  const binaryPath = createMockCliBinary(tempDir);
  const workspaceDir = path.join(tempDir, "workspace");
  mkdirSync(workspaceDir, { recursive: true });
  const restoreEnv = setMockCliEnv({
    MOCK_CLI_PROVIDER_SESSION_ID: options.providerSessionId ?? "provider-session-approval",
    MOCK_CLI_APPROVAL_SEQUENCE: options.approvalSequence ?? "required-resolved",
    MOCK_CLI_APPROVAL_REQUIRED_DELAY_MS: String(options.requiredDelayMs ?? 250),
    MOCK_CLI_APPROVAL_RESOLVED_DELAY_MS: String(options.resolvedDelayMs ?? 450),
    MOCK_CLI_RESULT_DELAY_MS: String(options.resultDelayMs ?? 700)
  });

  const relayConfig = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_LOG_LEVEL: "error",
    RELAY_COMPANION_TIMEOUT_MS: "2000",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1"
  });

  const relayRuntime = await relay.createRelayApp({
    config: relayConfig,
    logger: false
  });
  await relayRuntime.app.listen({
    host: "127.0.0.1",
    port: 0
  });

  const address = relayRuntime.app.server.address();
  const port = typeof address === "object" && address ? address.port : 0;
  const baseUrl = `http://127.0.0.1:${port}`;
  const baseWsUrl = `ws://127.0.0.1:${port}`;

  const companionRuntime = await companion.createCompanionRuntime({
    config: {
      configPath: path.join(tempDir, "companion.json"),
      relayUrl: baseWsUrl,
      token: relayConfig.staticToken,
      hostId: "macbook-1",
      providers: {
        claude: {
          binary: binaryPath
        }
      },
      sessionIndexPath: path.join(tempDir, "sessions.json")
    },
    logger: silentLogger,
    heartbeatIntervalMs: 25,
    reconnectDelaysMs: [20, 20],
    killGraceMs: 50
  });
  companionRuntime.connect();

  const android = new WebSocket(`${baseWsUrl}/v1/ws?token=${relayConfig.staticToken}`);
  await waitForOpen(android, "android");

  const receivedMessages = [];
  android.addEventListener("message", (event) => {
    receivedMessages.push(JSON.parse(String(event.data)));
  });

  await waitForCondition(
    () => relayRuntime.companionManager.isOnline("macbook-1"),
    "companion online"
  );

  t.after(async () => {
    android.close();
    await companionRuntime.close();
    await relayRuntime.close();
    restoreEnv();
    rmSync(tempDir, { recursive: true, force: true });
  });

  return {
    relayRuntime,
    relayConfig,
    baseUrl,
    android,
    receivedMessages,
    workspaceDir
  };
}

function createSessionRequest(baseUrl, config, body) {
  return fetch(`${baseUrl}/v1/sessions`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify(body)
  });
}

test("permission_mode=default sessions propagate approval_required from CLI to Android while staying running", async (t) => {
  const harness = await createApprovalHarness(t, {
    approvalSequence: "required",
    requiredDelayMs: 300,
    resultDelayMs: 900
  });

  const createResponse = await createSessionRequest(harness.baseUrl, harness.relayConfig, {
    provider: "claude",
    host_id: "macbook-1",
    cwd: harness.workspaceDir,
    prompt: "approval flow",
    permission_mode: "default"
  });
  const createPayload = await createResponse.json();

  assert.equal(createResponse.status, 201);
  assert.equal(createPayload.session.permission_mode, "default");

  const sessionId = createPayload.session.id;
  harness.android.send(
    JSON.stringify({
      action: "subscribe",
      session_id: sessionId
    })
  );

  const approvalRequired = await waitForJsonMessage(
    harness.android,
    (message) =>
      message.type === "event" &&
      message.session_id === sessionId &&
      message.event_type === "approval_required",
    "approval_required event"
  );

  assert.deepEqual(approvalRequired.payload, {
    call_id: "call-approval-1",
    tool_name: "bash",
    description: "Run a shell command"
  });

  const runningSession = harness.relayRuntime.db
    .prepare("SELECT status FROM sessions WHERE id = ?")
    .get(sessionId);
  assert.deepEqual(runningSession, {
    status: "running"
  });
});

test("approval_required and approval_resolved are stored and broadcast in order for non-bypass sessions", async (t) => {
  const harness = await createApprovalHarness(t, {
    approvalSequence: "required-resolved",
    requiredDelayMs: 250,
    resolvedDelayMs: 450,
    resultDelayMs: 900
  });

  const createResponse = await createSessionRequest(harness.baseUrl, harness.relayConfig, {
    provider: "claude",
    host_id: "macbook-1",
    cwd: harness.workspaceDir,
    prompt: "approval order",
    permission_mode: "default"
  });
  const createPayload = await createResponse.json();

  assert.equal(createResponse.status, 201);

  const sessionId = createPayload.session.id;
  harness.android.send(
    JSON.stringify({
      action: "subscribe",
      session_id: sessionId
    })
  );

  const approvalRequired = await waitForJsonMessage(
    harness.android,
    (message) =>
      message.type === "event" &&
      message.session_id === sessionId &&
      message.event_type === "approval_required",
    "approval_required event"
  );
  const approvalResolved = await waitForJsonMessage(
    harness.android,
    (message) =>
      message.type === "event" &&
      message.session_id === sessionId &&
      message.event_type === "approval_resolved",
    "approval_resolved event"
  );

  assert.equal(approvalRequired.seq < approvalResolved.seq, true);

  await waitForCondition(() => {
    const rows = harness.relayRuntime.db
      .prepare(
        `
        SELECT type
        FROM session_events
        WHERE session_id = ? AND type IN ('approval_required', 'approval_resolved')
        ORDER BY seq ASC
        `
      )
      .all(sessionId);
    return rows.length === 2;
  }, "stored approval events");

  const storedEvents = harness.relayRuntime.db
    .prepare(
      `
      SELECT type, payload
      FROM session_events
      WHERE session_id = ? AND type IN ('approval_required', 'approval_resolved')
      ORDER BY seq ASC
      `
    )
    .all(sessionId);

  assert.deepEqual(
    storedEvents.map((event) => event.type),
    ["approval_required", "approval_resolved"]
  );
  assert.deepEqual(
    harness.receivedMessages
      .filter(
        (message) =>
          message.type === "event" &&
          message.session_id === sessionId &&
          (message.event_type === "approval_required" || message.event_type === "approval_resolved")
      )
      .map((message) => message.event_type),
    ["approval_required", "approval_resolved"]
  );
  assert.equal(JSON.parse(storedEvents[0].payload).call_id, "call-approval-1");
  assert.equal(JSON.parse(storedEvents[1].payload).call_id, "call-approval-1");
});

test("permission_mode=bypassPermissions sessions complete without approval events", async (t) => {
  const harness = await createApprovalHarness(t, {
    approvalSequence: "required-resolved",
    requiredDelayMs: 250,
    resolvedDelayMs: 450,
    resultDelayMs: 700
  });

  const createResponse = await createSessionRequest(harness.baseUrl, harness.relayConfig, {
    provider: "claude",
    host_id: "macbook-1",
    cwd: harness.workspaceDir,
    prompt: "no approval events",
    permission_mode: "bypassPermissions"
  });
  const createPayload = await createResponse.json();

  assert.equal(createResponse.status, 201);

  const sessionId = createPayload.session.id;
  harness.android.send(
    JSON.stringify({
      action: "subscribe",
      session_id: sessionId
    })
  );

  await waitForCondition(() => {
    const session = harness.relayRuntime.db
      .prepare("SELECT status FROM sessions WHERE id = ?")
      .get(sessionId);
    return session?.status === "completed";
  }, "session completion");

  const approvalCount = harness.relayRuntime.db
    .prepare(
      `
      SELECT COUNT(*) AS count
      FROM session_events
      WHERE session_id = ? AND type IN ('approval_required', 'approval_resolved')
      `
    )
    .get(sessionId);

  assert.deepEqual(approvalCount, {
    count: 0
  });
  assert.deepEqual(
    harness.receivedMessages.filter(
      (message) =>
        message.type === "event" &&
        message.session_id === sessionId &&
        (message.event_type === "approval_required" || message.event_type === "approval_resolved")
    ),
    []
  );
});
