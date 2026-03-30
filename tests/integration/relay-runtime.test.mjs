import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");

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

function waitForClose(ws, label, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out waiting for ${label} close`));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      ws.removeEventListener("close", onClose);
      ws.removeEventListener("error", onError);
    };

    const onClose = (event) => {
      cleanup();
      resolve(event);
    };

    const onError = (event) => {
      cleanup();
      reject(new Error(`WebSocket error while waiting for ${label} close: ${event.message ?? "unknown"}`));
    };

    ws.addEventListener("close", onClose, { once: true });
    ws.addEventListener("error", onError, { once: true });
  });
}

test("relay converts companion ack timeout into a failed session", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-timeout-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_LOG_LEVEL: "error",
    RELAY_COMPANION_TIMEOUT_MS: "500"
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
  const baseUrl = `http://127.0.0.1:${port}`;
  const broadcastCalls = [];
  const originalBroadcastToSession = runtime.hub.broadcastToSession.bind(runtime.hub);
  runtime.hub.broadcastToSession = (sessionId, message) => {
    broadcastCalls.push({ sessionId, message });
    return originalBroadcastToSession(sessionId, message);
  };
  const companion = new WebSocket(
    `ws://127.0.0.1:${port}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const createResponsePromise = fetch(`${baseUrl}/v1/sessions`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      provider: "claude",
      host_id: "macbook-1",
      cwd: "/tmp/project",
      prompt: "timeout me",
      permission_mode: "bypassPermissions"
    })
  });

  const createCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "create_session",
    "create_session command"
  );
  assert.equal(typeof createCommand.session_id, "string");

  const createResponse = await createResponsePromise;
  assert.equal(createResponse.status, 504);
  assert.deepEqual(await createResponse.json(), {
    error: "command_timeout"
  });

  const failedSession = runtime.db
    .prepare("SELECT id, status, error_code FROM sessions ORDER BY created_at DESC LIMIT 1")
    .get();
  assert.deepEqual(failedSession, {
    id: failedSession.id,
    status: "failed",
    error_code: "command_timeout"
  });

  const sessionErrorEvent = runtime.db
    .prepare("SELECT type, payload FROM session_events WHERE session_id = ? AND type = 'session_error' LIMIT 1")
    .get(failedSession.id);
  const sessionErrorPayload = JSON.parse(sessionErrorEvent.payload);
  assert.equal(sessionErrorEvent.type, "session_error");
  assert.equal(sessionErrorPayload.error_code, "command_timeout");
  assert.match(sessionErrorPayload.message, /timed out/);

  assert.equal(
    broadcastCalls.some(
      (entry) =>
        entry.sessionId === failedSession.id &&
        entry.message.type === "event" &&
        entry.message.event_type === "session_error" &&
        entry.message.payload.error_code === "command_timeout"
    ),
    true
  );

  companion.close();
});

test("relay shutdown closes active websockets with going-away semantics", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-shutdown-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_LOG_LEVEL: "error"
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
  const android = new WebSocket(`ws://127.0.0.1:${port}/v1/ws?token=${config.staticToken}`);
  const companion = new WebSocket(
    `ws://127.0.0.1:${port}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );

  await waitForOpen(android, "android");
  await waitForOpen(companion, "companion");

  const androidClosePromise = waitForClose(android, "android");
  const companionClosePromise = waitForClose(companion, "companion");

  t.after(() => {
    rmSync(tempDir, { recursive: true, force: true });
  });

  await runtime.close();

  const [androidClose, companionClose] = await Promise.all([
    androidClosePromise,
    companionClosePromise
  ]);

  assert.equal(androidClose.code, 1001);
  assert.equal(companionClose.code, 1001);
});
