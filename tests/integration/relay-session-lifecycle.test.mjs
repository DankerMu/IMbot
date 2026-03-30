import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

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

test("relay marks running sessions as failed when the companion disconnects unexpectedly", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-host-disconnect-"));
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
  const baseUrl = `http://127.0.0.1:${port}`;
  const companion = new WebSocket(
    `ws://127.0.0.1:${port}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
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
      prompt: "disconnect me",
      permission_mode: "bypassPermissions"
    })
  });

  const createCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "create_session",
    "create_session command"
  );

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: createCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: "provider-session-disconnect"
      }
    })
  );

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 201);
  assert.equal(createPayload.session.status, "running");

  const sessionId = createPayload.session.id;
  companion.close();

  await waitForCondition(() => {
    const session = runtime.db
      .prepare("SELECT status, error_code, error_message FROM sessions WHERE id = ?")
      .get(sessionId);
    return (
      session?.status === "failed" &&
      session?.error_code === "host_disconnected" &&
      session?.error_message === "Host companion disconnected unexpectedly"
    );
  }, "host disconnect cascade");

  const sessionErrorEvent = runtime.db
    .prepare(
      `
      SELECT payload
      FROM session_events
      WHERE session_id = ? AND type = 'session_error'
      ORDER BY seq DESC
      LIMIT 1
      `
    )
    .get(sessionId);
  assert.deepEqual(JSON.parse(sessionErrorEvent.payload), {
    error_code: "host_disconnected",
    message: "Host companion disconnected unexpectedly"
  });

  const hostOnlineAudit = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM audit_logs WHERE action = 'host.online' AND host_id = 'macbook-1'")
    .get();
  const hostOfflineAudit = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM audit_logs WHERE action = 'host.offline' AND host_id = 'macbook-1'")
    .get();

  assert.deepEqual(hostOnlineAudit, { count: 1 });
  assert.deepEqual(hostOfflineAudit, { count: 1 });
});
