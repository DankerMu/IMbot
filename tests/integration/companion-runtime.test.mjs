import assert from "node:assert/strict";
import {
  chmodSync,
  mkdtempSync,
  readFileSync,
  rmSync,
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
const promptIndex = args.indexOf("-p");
const sessionIdFlagIndex = args.indexOf("--session-id");
const providerSessionId =
  process.env.MOCK_CLI_PROVIDER_SESSION_ID ||
  (sessionIdFlagIndex >= 0 ? args[sessionIdFlagIndex + 1] : "provider-session-1");
const behavior = process.env.MOCK_CLI_BEHAVIOR || "complete";
const resultDelayMs = Number(process.env.MOCK_CLI_RESULT_DELAY_MS || "30");
let stdinBuffer = "";

function emit(message) {
  process.stdout.write(JSON.stringify(message) + "\\n");
}

emit({ type: "system", session_id: providerSessionId });

if (promptIndex >= 0 && args[promptIndex + 1]) {
  emit({ type: "assistant", subtype: "text", text: "prompt:" + args[promptIndex + 1] });
}

process.stdin.on("data", (chunk) => {
  stdinBuffer += chunk.toString();
  const lines = stdinBuffer.split(/\\r?\\n/);
  stdinBuffer = lines.pop() || "";

  for (const line of lines) {
    if (!line) {
      continue;
    }

    emit({ type: "assistant", subtype: "text", text: "echo:" + line });
  }
});

process.on("SIGINT", () => {
  setTimeout(() => {
    process.exit(130);
  }, 10);
});

if (behavior === "complete") {
  setTimeout(() => {
    emit({ type: "result", result: "done" });
    process.exit(0);
  }, resultDelayMs);
}
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

test("companion connects to relay, creates a session, forwards events, and persists session index", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-runtime-"));
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const restoreEnv = setMockCliEnv({
    MOCK_CLI_BEHAVIOR: "complete",
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
      sessionIndexPath
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
    "assistant delta"
  );
  const sessionResultPromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-1" &&
      message.event_type === "session_result",
    "session result"
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
  assert.equal(assistantDelta.payload.text, "prompt:hello");

  const sessionResult = await sessionResultPromise;
  assert.deepEqual(sessionResult.payload, {
    result: "done"
  });

  const sessionIndex = JSON.parse(readFileSync(sessionIndexPath, "utf8"));
  assert.deepEqual(sessionIndex["relay-session-1"], {
    provider_session_id: "provider-session-1",
    cwd: tempDir,
    provider: "claude",
    created_at: sessionIndex["relay-session-1"].created_at
  });
});

test("companion reconnects after relay disconnect and supports send_message plus cancel_session", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-reconnect-"));
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const restoreEnv = setMockCliEnv({
    MOCK_CLI_BEHAVIOR: "linger",
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
      sessionIndexPath: path.join(tempDir, "sessions.json")
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
      message.payload.text === "echo:followup",
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
  assert.equal(echoedMessage.payload.text, "echo:followup");

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
