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

    const onClose = () => {
      cleanup();
      resolve();
    };

    const onError = (event) => {
      cleanup();
      reject(new Error(`WebSocket error while waiting for ${label} close: ${event.message ?? "unknown"}`));
    };

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
  const broadcastCalls = [];
  const originalBroadcastToSession = runtime.hub.broadcastToSession.bind(runtime.hub);
  runtime.hub.broadcastToSession = (sessionId, message) => {
    broadcastCalls.push({ sessionId, message });
    return originalBroadcastToSession(sessionId, message);
  };

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
  assert.equal(typeof createCommand.session_id, "string");
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
  assert.equal(createPayload.session.id, createCommand.session_id);
  assert.equal(createPayload.session.status, "running");

  const sessionId = createPayload.session.id;
  assert.equal(
    broadcastCalls.some(
      (entry) =>
        entry.sessionId === sessionId &&
        entry.message.type === "event" &&
        entry.message.event_type === "session_status_changed" &&
        entry.message.payload.status === "running"
    ),
    true
  );

  const createAuditLogCount = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM audit_logs WHERE action = 'session.create' AND session_id = ?")
    .get(sessionId);
  assert.deepEqual(createAuditLogCount, { count: 1 });

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

test("relay persists companion ack errors onto the failed session", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-ack-error-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
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
  const companion = new WebSocket(
    `ws://127.0.0.1:${port}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  const broadcastCalls = [];
  const originalBroadcastToSession = runtime.hub.broadcastToSession.bind(runtime.hub);
  runtime.hub.broadcastToSession = (sessionId, message) => {
    broadcastCalls.push({ sessionId, message });
    return originalBroadcastToSession(sessionId, message);
  };

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
      cwd: "/tmp/missing-project",
      prompt: "help me refactor",
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
      status: "error",
      error_code: "directory_not_found",
      message: "Directory missing"
    })
  );

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 400);
  assert.deepEqual(createPayload, {
    error: "directory_not_found"
  });

  const failedSession = runtime.db
    .prepare(
      "SELECT id, status, error_code, error_message FROM sessions ORDER BY created_at DESC LIMIT 1"
    )
    .get();

  assert.equal(failedSession.status, "failed");
  assert.equal(failedSession.error_code, "directory_not_found");
  assert.equal(failedSession.error_message, "Directory missing");

  const sessionErrorEvent = runtime.db
    .prepare(
      "SELECT type, payload FROM session_events WHERE session_id = ? AND type = 'session_error' LIMIT 1"
    )
    .get(failedSession.id);

  assert.equal(sessionErrorEvent.type, "session_error");
  assert.deepEqual(JSON.parse(sessionErrorEvent.payload), {
    error_code: "directory_not_found",
    message: "Directory missing"
  });

  assert.equal(
    broadcastCalls.some(
      (entry) =>
        entry.sessionId === failedSession.id &&
        entry.message.type === "event" &&
        entry.message.event_type === "session_error" &&
        entry.message.payload.error_code === "directory_not_found"
    ),
    true
  );

  const auditLogCount = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM audit_logs WHERE action = 'session.create' AND session_id = ?")
    .get(failedSession.id);
  assert.deepEqual(auditLogCount, { count: 1 });

  companion.close();
});

test("relay keeps the host online when a same-host companion replaces an older socket", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-replace-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
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

  const firstCompanion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(firstCompanion, "first companion");

  const firstClosePromise = waitForClose(firstCompanion, "first companion");
  const secondCompanion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(secondCompanion, "second companion");
  await firstClosePromise;

  const hostRow = runtime.db
    .prepare("SELECT status FROM hosts WHERE id = 'macbook-1'")
    .get();
  assert.deepEqual(hostRow, {
    status: "online"
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
      prompt: "help me refactor",
      permission_mode: "bypassPermissions"
    })
  });

  const createCommand = await waitForJsonMessage(
    secondCompanion,
    (message) => message.cmd === "create_session",
    "replacement companion create_session command"
  );

  secondCompanion.send(
    JSON.stringify({
      type: "ack",
      req_id: createCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: "replacement-provider-session"
      }
    })
  );

  const createResponse = await createResponsePromise;
  assert.equal(createResponse.status, 201);

  const healthResponse = await fetch(`${baseUrl}/healthz`);
  const healthPayload = await healthResponse.json();
  assert.equal(healthPayload.companion, "online");

  secondCompanion.close();
});

test("relay clears pending ack state when sending a companion command fails", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-send-fail-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
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
  const companion = new WebSocket(
    `ws://127.0.0.1:${port}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const serverCompanion = runtime.hub.getCompanionClient("macbook-1");
  assert.ok(serverCompanion);

  serverCompanion.send = (_payload, callback) => {
    callback?.(new Error("socket write failed"));
    return undefined;
  };

  const createResponse = await fetch(`${baseUrl}/v1/sessions`, {
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
      permission_mode: "bypassPermissions"
    })
  });

  assert.equal(createResponse.status, 502);
  assert.deepEqual(await createResponse.json(), {
    error: "provider_unreachable"
  });

  const failedSession = runtime.db
    .prepare("SELECT id, status, error_code, error_message FROM sessions ORDER BY created_at DESC LIMIT 1")
    .get();

  assert.equal(failedSession.status, "failed");
  assert.equal(failedSession.error_code, "provider_unreachable");
  assert.match(failedSession.error_message, /socket write failed/);

  const pendingForHost = runtime.companionManager.pendingByHost.get("macbook-1");
  assert.equal(pendingForHost?.size ?? 0, 0);

  companion.close();
});
