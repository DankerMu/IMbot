import assert from "node:assert/strict";
import { spawn as spawnChildProcess } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const companion = require("../../packages/companion/dist/index.js");
const { initializeDatabase } = require("../../packages/relay/dist/db/init.js");

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

function sendHeartbeat(ws, hostId = "macbook-1", providers = ["claude", "book"]) {
  ws.send(
    JSON.stringify({
      type: "heartbeat",
      host_id: hostId,
      providers,
      uptime: 1
    })
  );
}

async function createRelayHarness(t, prefix) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), prefix));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_LOG_LEVEL: "error",
    RELAY_COMPANION_TIMEOUT_MS: "2000",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1"
  });

  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  await runtime.app.listen({
    host: "127.0.0.1",
    port: 0
  });

  const address = runtime.app.server.address();
  const port = typeof address === "object" && address ? address.port : 0;

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  return {
    runtime,
    config,
    baseUrl: `http://127.0.0.1:${port}`,
    baseWsUrl: `ws://127.0.0.1:${port}`
  };
}

async function connectCompanion(t, harness) {
  const ws = new WebSocket(
    `${harness.baseWsUrl}/v1/companion?token=${harness.config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(ws, "companion");
  sendHeartbeat(ws);

  t.after(() => {
    ws.close();
  });

  return ws;
}

function authHeaders(config) {
  return {
    authorization: `Bearer ${config.staticToken}`,
    "content-type": "application/json"
  };
}

function createAdapterHarness(tempDir) {
  const sessionIndex = new companion.SessionIndex({
    filePath: path.join(tempDir, "sessions.json"),
    logger: silentLogger
  });
  const spawnCalls = [];
  const runtimeScript = [
    "process.stdout.write(JSON.stringify({ type: 'system', session_id: process.env.TEST_PROVIDER_SESSION_ID }) + '\\n');",
    "process.stdout.write(JSON.stringify({ type: 'result', result: 'done' }) + '\\n');"
  ].join("");

  const adapter = new companion.ClaudeRuntimeAdapter({
    providers: {
      claude: {
        binary: "claude"
      }
    },
    sessionIndex,
    logger: silentLogger,
    sendEvent: () => {},
    spawn: (binary, args, options) => {
      spawnCalls.push({
        binary,
        args,
        cwd: options.cwd
      });

      return spawnChildProcess(process.execPath, ["-e", runtimeScript], {
        cwd: options.cwd,
        stdio: ["pipe", "pipe", "pipe"],
        env: {
          ...process.env,
          TEST_PROVIDER_SESSION_ID: `provider-session-${spawnCalls.length}`
        }
      });
    }
  });

  return {
    adapter,
    spawnCalls
  };
}

test("POST /v1/sessions preserves explicit default permission mode, forwards it to companion, and returns it from GET", async (t) => {
  const harness = await createRelayHarness(t, "imbot-permission-mode-default-");
  const companionWs = await connectCompanion(t, harness);

  const createResponsePromise = fetch(`${harness.baseUrl}/v1/sessions`, {
    method: "POST",
    headers: authHeaders(harness.config),
    body: JSON.stringify({
      provider: "claude",
      host_id: "macbook-1",
      cwd: "/tmp/project-default",
      prompt: "preserve default mode",
      permission_mode: "default"
    })
  });

  const createCommand = await waitForJsonMessage(
    companionWs,
    (message) => message.cmd === "create_session",
    "create_session command"
  );

  assert.equal(createCommand.permission_mode, "default");

  companionWs.send(
    JSON.stringify({
      type: "ack",
      req_id: createCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: "provider-session-default"
      }
    })
  );

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 201);
  assert.equal(createPayload.session.permission_mode, "default");

  const sessionId = createPayload.session.id;
  const storedSession = harness.runtime.db
    .prepare("SELECT permission_mode FROM sessions WHERE id = ?")
    .get(sessionId);
  assert.deepEqual(storedSession, {
    permission_mode: "default"
  });

  const getResponse = await fetch(`${harness.baseUrl}/v1/sessions/${sessionId}`, {
    headers: {
      authorization: `Bearer ${harness.config.staticToken}`
    }
  });
  assert.equal(getResponse.status, 200);

  const fetchedSession = await getResponse.json();
  assert.equal(fetchedSession.permission_mode, "default");
});

test("POST /v1/sessions preserves explicit bypassPermissions mode", async (t) => {
  const harness = await createRelayHarness(t, "imbot-permission-mode-bypass-");
  const companionWs = await connectCompanion(t, harness);

  const createResponsePromise = fetch(`${harness.baseUrl}/v1/sessions`, {
    method: "POST",
    headers: authHeaders(harness.config),
    body: JSON.stringify({
      provider: "claude",
      host_id: "macbook-1",
      cwd: "/tmp/project-bypass",
      prompt: "preserve bypass mode",
      permission_mode: "bypassPermissions"
    })
  });

  const createCommand = await waitForJsonMessage(
    companionWs,
    (message) => message.cmd === "create_session",
    "create_session command"
  );

  assert.equal(createCommand.permission_mode, "bypassPermissions");

  companionWs.send(
    JSON.stringify({
      type: "ack",
      req_id: createCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: "provider-session-bypass"
      }
    })
  );

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 201);
  assert.equal(createPayload.session.permission_mode, "bypassPermissions");
});

test("POST /v1/sessions defaults missing permission_mode to bypassPermissions and forwards that default", async (t) => {
  const harness = await createRelayHarness(t, "imbot-permission-mode-defaulted-");
  const companionWs = await connectCompanion(t, harness);

  const createResponsePromise = fetch(`${harness.baseUrl}/v1/sessions`, {
    method: "POST",
    headers: authHeaders(harness.config),
    body: JSON.stringify({
      provider: "claude",
      host_id: "macbook-1",
      cwd: "/tmp/project-implicit",
      prompt: "use default mode"
    })
  });

  const createCommand = await waitForJsonMessage(
    companionWs,
    (message) => message.cmd === "create_session",
    "create_session command"
  );

  assert.equal(createCommand.permission_mode, "bypassPermissions");

  companionWs.send(
    JSON.stringify({
      type: "ack",
      req_id: createCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: "provider-session-implicit"
      }
    })
  );

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 201);
  assert.equal(createPayload.session.permission_mode, "bypassPermissions");
});

test("ClaudeRuntimeAdapter spawns the CLI with a matching --permission-mode flag", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-adapter-permission-mode-"));
  const projectDir = path.join(tempDir, "project");
  mkdirSync(projectDir, { recursive: true });

  const { adapter, spawnCalls } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const result = await adapter.createSession({
    cmd: "create_session",
    req_id: "req-default-mode",
    session_id: "relay-session-default",
    provider: "claude",
    cwd: projectDir,
    prompt: "hello",
    permission_mode: "default"
  });

  assert.deepEqual(result, {
    provider_session_id: "provider-session-1"
  });
  assert.equal(spawnCalls.length, 1);
  assert.equal(spawnCalls[0].binary, "claude");
  assert.deepEqual(
    spawnCalls[0].args.slice(
      spawnCalls[0].args.indexOf("--permission-mode"),
      spawnCalls[0].args.indexOf("--permission-mode") + 2
    ),
    ["--permission-mode", "default"]
  );
});

test("legacy-style inserts that omit permission_mode still read back with the DB default", (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-permission-mode-legacy-"));
  const db = initializeDatabase(path.join(tempDir, "imbot.db"));

  t.after(() => {
    db.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

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
      status,
      error_message,
      error_code,
      created_at,
      updated_at,
      last_active_at
    ) VALUES (?, 'claude', 'provider-session-legacy', 'macbook-1', NULL, ?, ?, NULL, 'running', NULL, NULL, ?, ?, ?)
    `
  ).run("legacy-session-1", "/tmp/legacy", "hello", now, now, now);

  const storedSession = db
    .prepare("SELECT permission_mode, status FROM sessions WHERE id = ?")
    .get("legacy-session-1");

  assert.deepEqual(storedSession, {
    permission_mode: "bypassPermissions",
    status: "running"
  });
});
