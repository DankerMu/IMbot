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

test("relay creates a session, persists events, and broadcasts companion traffic", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-flow-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_PORT: "3017",
    RELAY_LOG_LEVEL: "error",
    RELAY_COMPANION_TIMEOUT_MS: "2000"
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
  const baseWsUrl = `ws://127.0.0.1:${port}`;

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const android = new WebSocket(`${baseWsUrl}/v1/ws?token=${config.staticToken}`);
  await waitForOpen(android, "android");

  const hostOnlinePromise = waitForJsonMessage(
    android,
    (message) =>
      message.type === "host_status" &&
      message.host_id === "macbook-1" &&
      message.status === "online",
    "host online broadcast"
  );

  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");
  await hostOnlinePromise;

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
      prompt: "help me refactor",
      model: "opus",
      permission_mode: "bypassPermissions"
    })
  });

  const createCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "create_session",
    "create_session command"
  );

  assert.equal(createCommand.provider, "claude");
  assert.equal(createCommand.cwd, "/tmp/project");
  assert.equal(createCommand.prompt, "help me refactor");

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: createCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: "provider-session-1"
      }
    })
  );

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 201);
  assert.equal(createPayload.session.provider, "claude");
  assert.equal(createPayload.session.host_id, "macbook-1");

  const sessionId = createPayload.session.id;
  android.send(
    JSON.stringify({
      action: "subscribe",
      session_id: sessionId
    })
  );

  const eventPromise = waitForJsonMessage(
    android,
    (message) =>
      message.type === "event" &&
      message.session_id === sessionId &&
      message.event_type === "assistant_delta",
    "assistant delta event"
  );

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "assistant_delta",
      payload: {
        text: "hello from companion"
      }
    })
  );

  const eventMessage = await eventPromise;
  assert.equal(eventMessage.payload.text, "hello from companion");
  assert.equal(eventMessage.seq >= 1, true);

  const listResponse = await fetch(`${baseUrl}/v1/sessions?provider=claude&status=running`, {
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  const listPayload = await listResponse.json();
  assert.equal(listResponse.status, 200);
  assert.equal(listPayload.total, 1);
  assert.equal(listPayload.sessions[0].id, sessionId);

  const eventsResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/events?since_seq=0`, {
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  const eventsPayload = await eventsResponse.json();
  assert.equal(eventsResponse.status, 200);
  assert.equal(eventsPayload.has_more, false);
  assert.equal(
    eventsPayload.events.some((event) => event.type === "assistant_delta"),
    true
  );

  android.close();
  companion.close();
});

