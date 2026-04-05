import assert from "node:assert/strict";
import {
  chmodSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  utimesSync,
  writeFileSync
} from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { WebSocketServer } from "ws";

const require = createRequire(import.meta.url);
const companion = require("../../packages/companion/dist/index.js");

const silentLogger = {
  debug() {},
  info() {},
  warn() {},
  error() {}
};

function delay(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function waitFor(condition, timeoutMs = 1000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (condition()) {
      return;
    }

    await delay(10);
  }

  throw new Error("Timed out waiting for condition");
}

function waitForListening(server, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    if (server.address()) {
      resolve();
      return;
    }

    const timer = setTimeout(() => {
      cleanup();
      reject(new Error("Timed out waiting for relay test websocket server"));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      server.off("listening", onListening);
      server.off("error", onError);
    };

    const onListening = () => {
      cleanup();
      resolve();
    };

    const onError = (error) => {
      cleanup();
      reject(error);
    };

    server.once("listening", onListening);
    server.once("error", onError);
  });
}

function waitForConnection(server, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error("Timed out waiting for companion websocket connection"));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      server.off("connection", onConnection);
    };

    const onConnection = (socket, request) => {
      cleanup();
      resolve({ socket, request });
    };

    server.on("connection", onConnection);
  });
}

function waitForJsonMessage(socket, predicate, label, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out waiting for ${label}`));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      socket.off("message", onMessage);
      socket.off("close", onClose);
      socket.off("error", onError);
    };

    const onMessage = (raw) => {
      const message = JSON.parse(raw.toString());
      if (!predicate(message)) {
        return;
      }

      cleanup();
      resolve(message);
    };

    const onClose = () => {
      cleanup();
      reject(new Error(`Socket closed while waiting for ${label}`));
    };

    const onError = (error) => {
      cleanup();
      reject(error);
    };

    socket.on("message", onMessage);
    socket.once("close", onClose);
    socket.once("error", onError);
  });
}

function createMockCliBinary(tempDir) {
  const scriptPath = path.join(tempDir, "mock-cli.js");
  writeFileSync(
    scriptPath,
    `#!/usr/bin/env node
const args = process.argv.slice(2);
const resumeIndex = args.indexOf("-r");
const providerSessionId =
  process.env.MOCK_CLI_PROVIDER_SESSION_ID ||
  (resumeIndex >= 0 ? args[resumeIndex + 1] : "provider-session-1");
const resultDelayMs = Number(process.env.MOCK_CLI_RESULT_DELAY_MS || "30");
let stdinBuffer = "";
let turn = 0;

function emit(message) {
  process.stdout.write(JSON.stringify(message) + "\\n");
}

function extractText(content) {
  if (typeof content === "string") {
    return content;
  }

  if (Array.isArray(content)) {
    return content
      .map((item) => (item && item.type === "text" && typeof item.text === "string" ? item.text : ""))
      .join("");
  }

  return "";
}

emit({ type: "system", subtype: "init", session_id: providerSessionId });

process.stdin.on("data", (chunk) => {
  stdinBuffer += chunk.toString();
  const lines = stdinBuffer.split(/\\r?\\n/);
  stdinBuffer = lines.pop() || "";

  for (const line of lines) {
    if (!line) {
      continue;
    }

    let message;
    try {
      message = JSON.parse(line);
    } catch {
      emit({ type: "error", error_code: "invalid_json", message: "Input must be JSON" });
      continue;
    }

    const prompt = extractText(message?.message?.content);
    turn += 1;
    const currentTurn = turn;

    emit({
      type: "assistant",
      session_id: providerSessionId,
      message: {
        role: "assistant",
        content: [{ type: "text", text: "turn:" + currentTurn + ":" + prompt }]
      }
    });

    setTimeout(() => {
      emit({ type: "result", result: "done:" + currentTurn + ":" + prompt });
    }, resultDelayMs);
  }
});

process.on("SIGTERM", () => {
  process.exit(0);
});

process.on("SIGINT", () => {
  process.exit(130);
});
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

function encodeProjectPath(projectPath) {
  return projectPath.replace(/^\/+/, "").replace(/\//g, "-");
}

function createDiscoveredSessionFile(projectsDir, cwd, sessionId, createdAt, contents = '{"ok":true}\n') {
  const projectDir = path.join(projectsDir, encodeProjectPath(cwd));
  mkdirSync(projectDir, { recursive: true });

  const sessionFile = path.join(projectDir, `${sessionId}.jsonl`);
  writeFileSync(sessionFile, contents, "utf8");

  const timestamp = new Date(createdAt);
  utimesSync(sessionFile, timestamp, timestamp);

  return {
    projectDir,
    sessionFile
  };
}

test("companion runs a persistent multi-turn stream-json session and completes it explicitly", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot_companion_runtime_"));
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const restoreEnv = setMockCliEnv({
    MOCK_CLI_PROVIDER_SESSION_ID: "provider-session-1",
    MOCK_CLI_RESULT_DELAY_MS: "20"
  });
  const binaryPath = createMockCliBinary(tempDir);

  t.after(async () => {
    restoreEnv();
    server.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForListening(server);
  const port = server.address().port;
  const sessionIndexPath = path.join(tempDir, "sessions.json");
  const runtime = await companion.createCompanionRuntime({
    config: {
      configPath: path.join(tempDir, "companion.json"),
      relayUrl: `ws://127.0.0.1:${port}`,
      token: "static-token",
      hostId: "macbook-1",
      providers: {
        claude: {
          binary: binaryPath
        }
      },
      sessionIndexPath,
      idleTimeoutMs: 1800000
    },
    logger: silentLogger,
    heartbeatIntervalMs: 25,
    reconnectDelaysMs: [20, 20],
    killGraceMs: 50
  });

  t.after(async () => {
    await runtime.close();
  });

  const connectionPromise = waitForConnection(server);
  runtime.connect();
  const { socket, request } = await connectionPromise;

  assert.match(request.url, /token=static-token/);
  assert.match(request.url, /host_id=macbook-1/);

  const heartbeat = await waitForJsonMessage(
    socket,
    (message) => message.type === "heartbeat" && message.host_id === "macbook-1",
    "initial heartbeat"
  );
  assert.deepEqual(heartbeat.providers, ["claude"]);

  const ackPromise = waitForJsonMessage(
    socket,
    (message) => message.type === "ack" && message.req_id === "req-create-1",
    "create_session ack"
  );
  const assistantDeltaPromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-1" &&
      message.event_type === "assistant_delta",
    "first assistant delta"
  );
  const sessionIdlePromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-1" &&
      message.event_type === "session_idle",
    "first session idle"
  );

  socket.send(
    JSON.stringify({
      cmd: "create_session",
      req_id: "req-create-1",
      session_id: "relay-session-1",
      provider: "claude",
      cwd: tempDir,
      prompt: "hello",
      permission_mode: "bypassPermissions"
    })
  );

  const ack = await ackPromise;
  assert.deepEqual(ack, {
    type: "ack",
    req_id: "req-create-1",
    status: "ok",
    data: {
      provider_session_id: "provider-session-1"
    }
  });

  const assistantDelta = await assistantDeltaPromise;
  assert.equal(assistantDelta.payload.text, "turn:1:hello");

  const sessionIdle = await sessionIdlePromise;
  assert.deepEqual(sessionIdle.payload, {
    result: "done:1:hello"
  });

  const sendAckPromise = waitForJsonMessage(
    socket,
    (message) => message.type === "ack" && message.req_id === "req-send-1",
    "send_message ack"
  );
  const secondAssistantDeltaPromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-1" &&
      message.event_type === "assistant_delta" &&
      message.payload?.text === "turn:2:followup",
    "second assistant delta"
  );
  const secondSessionIdlePromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-1" &&
      message.event_type === "session_idle" &&
      message.payload?.result === "done:2:followup",
    "second session idle"
  );

  socket.send(
    JSON.stringify({
      cmd: "send_message",
      req_id: "req-send-1",
      session_id: "relay-session-1",
      text: "followup"
    })
  );

  const sendAck = await sendAckPromise;
  assert.deepEqual(sendAck, {
    type: "ack",
    req_id: "req-send-1",
    status: "ok"
  });

  const secondAssistantDelta = await secondAssistantDeltaPromise;
  assert.equal(secondAssistantDelta.payload.text, "turn:2:followup");

  const secondSessionIdle = await secondSessionIdlePromise;
  assert.deepEqual(secondSessionIdle.payload, {
    result: "done:2:followup"
  });

  const completeAckPromise = waitForJsonMessage(
    socket,
    (message) => message.type === "ack" && message.req_id === "req-complete-1",
    "complete_session ack"
  );
  const completeResultPromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-1" &&
      message.event_type === "session_result",
    "session result after complete"
  );

  socket.send(
    JSON.stringify({
      cmd: "complete_session",
      req_id: "req-complete-1",
      session_id: "relay-session-1"
    })
  );

  const completeAck = await completeAckPromise;
  assert.deepEqual(completeAck, {
    type: "ack",
    req_id: "req-complete-1",
    status: "ok"
  });

  const completeResult = await completeResultPromise;
  assert.deepEqual(completeResult.payload, {
    result: null
  });

  const sessionIndex = JSON.parse(readFileSync(sessionIndexPath, "utf8"));
  assert.deepEqual(sessionIndex["relay-session-1"], {
    provider_session_id: "provider-session-1",
    cwd: tempDir,
    provider: "claude",
    created_at: sessionIndex["relay-session-1"].created_at,
    last_observed_at: sessionIndex["relay-session-1"].last_observed_at,
    source: "remote",
    initial_prompt: "hello"
  });
});

test("companion triggers session reconciliation on connect and reconnect", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot_companion_reconcile_runtime_"));
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const binaryPath = createMockCliBinary(tempDir);
  const workspaceRoot = path.join(tempDir, "workspace");
  const projectsDir = path.join(tempDir, "projects");
  const configPath = path.join(tempDir, "companion.json");
  mkdirSync(workspaceRoot, { recursive: true });
  mkdirSync(projectsDir, { recursive: true });
  writeFileSync(
    configPath,
    `${JSON.stringify(
      {
        workspace_roots: [
          {
            provider: "claude",
            path: workspaceRoot,
            added_at: "2026-01-01T00:00:00.000Z"
          }
        ]
      },
      null,
      2
    )}\n`,
    "utf8"
  );
  createDiscoveredSessionFile(projectsDir, workspaceRoot, "provider-local-1", "2026-01-02T00:00:00.000Z");

  t.after(async () => {
    server.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForListening(server);
  const port = server.address().port;
  const runtime = await companion.createCompanionRuntime({
    config: {
      configPath,
      relayUrl: `ws://127.0.0.1:${port}`,
      token: "static-token",
      hostId: "macbook-1",
      providers: {
        claude: {
          binary: binaryPath,
          projectsDir
        }
      },
      sessionIndexPath: path.join(tempDir, "sessions.json"),
      idleTimeoutMs: 1800000
    },
    logger: silentLogger,
    heartbeatIntervalMs: 25,
    reconnectDelaysMs: [20, 20],
    killGraceMs: 50
  });

  t.after(async () => {
    await runtime.close();
  });

  const firstConnectionPromise = waitForConnection(server);
  runtime.connect();
  const { socket: firstSocket } = await firstConnectionPromise;
  await waitForJsonMessage(firstSocket, (message) => message.type === "heartbeat", "first reconciliation heartbeat");
  const firstReport = await waitForJsonMessage(
    firstSocket,
    (message) => message.type === "report_local_sessions",
    "first local session report"
  );

  assert.equal(typeof firstReport.req_id, "string");
  assert.deepEqual(
    {
      ...firstReport,
      req_id: "<req_id>"
    },
    {
      type: "report_local_sessions",
      req_id: "<req_id>",
      host_id: "macbook-1",
      sessions: [
        {
          provider_session_id: "provider-local-1",
          provider: "claude",
          cwd: workspaceRoot,
          created_at: "2026-01-02T00:00:00.000Z",
          last_active_at: "2026-01-02T00:00:00.000Z"
        }
      ]
    }
  );
  firstSocket.send(JSON.stringify({
    type: "ack",
    req_id: firstReport.req_id,
    status: "ok",
    data: {
      sessions: [
        {
          relay_session_id: "relay-local-1",
          provider_session_id: "provider-local-1",
          created_at: "2026-01-02T00:00:00.000Z",
          last_active_at: "2026-01-02T00:00:00.000Z",
          initial_prompt: null
        }
      ]
    }
  }));
  await waitFor(() => runtime.sessionIndex.get("relay-local-1") != null, 1000);

  assert.deepEqual(runtime.sessionIndex.get("relay-local-1"), {
    provider_session_id: "provider-local-1",
    cwd: workspaceRoot,
    provider: "claude",
    created_at: "2026-01-02T00:00:00.000Z",
    last_observed_at: "2026-01-02T00:00:00.000Z",
    source: "remote",
    initial_prompt: null
  });
  assert.equal(runtime.sessionIndex.get("local:provider-local-1"), null);

  createDiscoveredSessionFile(projectsDir, workspaceRoot, "provider-local-2", "2026-01-03T00:00:00.000Z");
  const secondConnectionPromise = waitForConnection(server);
  firstSocket.close(1012, "relay restart");
  const { socket: secondSocket } = await secondConnectionPromise;
  await waitForJsonMessage(secondSocket, (message) => message.type === "heartbeat", "second reconciliation heartbeat");
  const secondReport = await waitForJsonMessage(
    secondSocket,
    (message) =>
      message.type === "report_local_sessions" &&
      message.sessions?.some((session) => session.provider_session_id === "provider-local-2"),
    "second local session report"
  );

  assert.equal(typeof secondReport.req_id, "string");
  assert.deepEqual(
    {
      ...secondReport,
      req_id: "<req_id>"
    },
    {
      type: "report_local_sessions",
      req_id: "<req_id>",
      host_id: "macbook-1",
      sessions: [
        {
          provider_session_id: "provider-local-2",
          provider: "claude",
          cwd: workspaceRoot,
          created_at: "2026-01-03T00:00:00.000Z",
          last_active_at: "2026-01-03T00:00:00.000Z"
        }
      ]
    }
  );
  secondSocket.send(JSON.stringify({
    type: "ack",
    req_id: secondReport.req_id,
    status: "ok",
    data: {
      sessions: [
        {
          relay_session_id: "relay-local-2",
          provider_session_id: "provider-local-2",
          created_at: "2026-01-03T00:00:00.000Z",
          last_active_at: "2026-01-03T00:00:00.000Z",
          initial_prompt: null
        }
      ]
    }
  }));
  await waitFor(() => runtime.sessionIndex.get("relay-local-2") != null, 1000);

  assert.deepEqual(runtime.sessionIndex.get("relay-local-2"), {
    provider_session_id: "provider-local-2",
    cwd: workspaceRoot,
    provider: "claude",
    created_at: "2026-01-03T00:00:00.000Z",
    last_observed_at: "2026-01-03T00:00:00.000Z",
    source: "remote",
    initial_prompt: null
  });
});

test("companion handles browse_directory commands and returns subdirectories only", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot_companion_browse_runtime_"));
  const workspaceRoot = path.join(tempDir, "workspace");
  const workspaceLink = path.join(tempDir, "workspace-link");
  const projectDir = path.join(workspaceRoot, "project");
  const notesDir = path.join(workspaceRoot, "notes");
  mkdirSync(workspaceRoot);
  mkdirSync(projectDir);
  mkdirSync(notesDir);
  symlinkSync(workspaceRoot, workspaceLink);
  writeFileSync(path.join(workspaceRoot, "README.md"), "file");
  const canonicalWorkspaceRoot = realpathSync(workspaceRoot);
  const canonicalProjectDir = realpathSync(projectDir);
  const canonicalNotesDir = realpathSync(notesDir);

  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const binaryPath = createMockCliBinary(tempDir);

  t.after(async () => {
    server.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForListening(server);
  const port = server.address().port;
  const runtime = await companion.createCompanionRuntime({
    config: {
      configPath: path.join(tempDir, "companion.json"),
      relayUrl: `ws://127.0.0.1:${port}`,
      token: "static-token",
      hostId: "macbook-1",
      providers: {
        claude: {
          binary: binaryPath
        }
      },
      sessionIndexPath: path.join(tempDir, "sessions.json"),
      idleTimeoutMs: 1800000
    },
    logger: silentLogger,
    heartbeatIntervalMs: 25,
    reconnectDelaysMs: [20, 20],
    killGraceMs: 50
  });

  t.after(async () => {
    await runtime.close();
  });

  const connectionPromise = waitForConnection(server);
  runtime.connect();
  const { socket } = await connectionPromise;

  socket.send(
    JSON.stringify({
      cmd: "browse_directory",
      req_id: "browse-1",
      path: workspaceLink,
      roots: [workspaceRoot]
    })
  );

  const browseAck = await waitForJsonMessage(
    socket,
    (message) => message.type === "ack" && message.req_id === "browse-1",
    "browse_directory ack"
  );
  assert.deepEqual(browseAck, {
    type: "ack",
    req_id: "browse-1",
    status: "ok",
    data: {
      path: canonicalWorkspaceRoot,
      directories: [
        {
          name: "notes",
          path: canonicalNotesDir
        },
        {
          name: "project",
          path: canonicalProjectDir
        }
      ]
    }
  });
});

test("companion rejects browse_directory when canonical target escapes provided roots", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot_companion_browse_guard_"));
  const workspaceRoot = path.join(tempDir, "workspace");
  const outsideDir = path.join(tempDir, "outside");
  const escapeLink = path.join(workspaceRoot, "escape-link");
  mkdirSync(workspaceRoot);
  mkdirSync(outsideDir);
  symlinkSync(outsideDir, escapeLink);

  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const binaryPath = createMockCliBinary(tempDir);

  t.after(async () => {
    server.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForListening(server);
  const port = server.address().port;
  const runtime = await companion.createCompanionRuntime({
    config: {
      configPath: path.join(tempDir, "companion.json"),
      relayUrl: `ws://127.0.0.1:${port}`,
      token: "static-token",
      hostId: "macbook-1",
      providers: {
        claude: {
          binary: binaryPath
        }
      },
      sessionIndexPath: path.join(tempDir, "sessions.json"),
      idleTimeoutMs: 1800000
    },
    logger: silentLogger,
    heartbeatIntervalMs: 25,
    reconnectDelaysMs: [20, 20],
    killGraceMs: 50
  });

  t.after(async () => {
    await runtime.close();
  });

  const connectionPromise = waitForConnection(server);
  runtime.connect();
  const { socket } = await connectionPromise;

  socket.send(
    JSON.stringify({
      cmd: "browse_directory",
      req_id: "browse-guard-1",
      path: escapeLink,
      roots: [workspaceRoot]
    })
  );

  const browseAck = await waitForJsonMessage(
    socket,
    (message) => message.type === "ack" && message.req_id === "browse-guard-1",
    "browse_directory guard ack"
  );
  assert.deepEqual(browseAck, {
    type: "ack",
    req_id: "browse-guard-1",
    status: "error",
    error_code: "forbidden",
    message: `Directory ${escapeLink} is not under any workspace root`
  });
});

test("companion reconnects after relay disconnect and supports send_message plus cancel_session", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot_companion_reconnect_"));
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const restoreEnv = setMockCliEnv({
    MOCK_CLI_PROVIDER_SESSION_ID: "provider-session-2"
  });
  const binaryPath = createMockCliBinary(tempDir);

  t.after(async () => {
    restoreEnv();
    server.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForListening(server);
  const port = server.address().port;
  const runtime = await companion.createCompanionRuntime({
    config: {
      configPath: path.join(tempDir, "companion.json"),
      relayUrl: `ws://127.0.0.1:${port}`,
      token: "static-token",
      hostId: "macbook-1",
      providers: {
        claude: {
          binary: binaryPath
        }
      },
      sessionIndexPath: path.join(tempDir, "sessions.json"),
      idleTimeoutMs: 1800000
    },
    logger: silentLogger,
    heartbeatIntervalMs: 25,
    reconnectDelaysMs: [20, 20],
    killGraceMs: 50
  });

  t.after(async () => {
    await runtime.close();
  });

  const firstConnectionPromise = waitForConnection(server);
  runtime.connect();
  const { socket: firstSocket } = await firstConnectionPromise;
  await waitForJsonMessage(firstSocket, (message) => message.type === "heartbeat", "first heartbeat");

  const secondConnectionPromise = waitForConnection(server);
  firstSocket.close(1012, "relay restart");
  const { socket: secondSocket } = await secondConnectionPromise;
  await waitForJsonMessage(secondSocket, (message) => message.type === "heartbeat", "reconnect heartbeat");

  const createAckPromise = waitForJsonMessage(
    secondSocket,
    (message) => message.type === "ack" && message.req_id === "req-create-2",
    "create ack after reconnect"
  );
  secondSocket.send(
    JSON.stringify({
      cmd: "create_session",
      req_id: "req-create-2",
      session_id: "relay-session-2",
      provider: "claude",
      cwd: tempDir,
      prompt: "linger",
      permission_mode: "bypassPermissions"
    })
  );

  const createAck = await createAckPromise;
  assert.equal(createAck.status, "ok");

  const sendAckPromise = waitForJsonMessage(
    secondSocket,
    (message) => message.type === "ack" && message.req_id === "req-send-2",
    "send_message ack"
  );
  const echoedMessagePromise = waitForJsonMessage(
    secondSocket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-2" &&
      message.event_type === "assistant_delta" &&
      message.payload.text === "turn:2:followup",
    "echoed assistant delta"
  );

  secondSocket.send(
    JSON.stringify({
      cmd: "send_message",
      req_id: "req-send-2",
      session_id: "relay-session-2",
      text: "followup"
    })
  );

  const sendAck = await sendAckPromise;
  assert.equal(sendAck.status, "ok");

  const echoedMessage = await echoedMessagePromise;
  assert.equal(echoedMessage.payload.text, "turn:2:followup");

  const cancelAckPromise = waitForJsonMessage(
    secondSocket,
    (message) => message.type === "ack" && message.req_id === "req-cancel-2",
    "cancel ack"
  );
  const cancelledEventPromise = waitForJsonMessage(
    secondSocket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-2" &&
      message.event_type === "session_status_changed",
    "cancelled event"
  );

  secondSocket.send(
    JSON.stringify({
      cmd: "cancel_session",
      req_id: "req-cancel-2",
      session_id: "relay-session-2"
    })
  );

  const cancelAck = await cancelAckPromise;
  assert.equal(cancelAck.status, "ok");

  const cancelledEvent = await cancelledEventPromise;
  assert.deepEqual(cancelledEvent.payload, {
    status: "cancelled"
  });
});
