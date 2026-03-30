import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const { WebSocketServer } = require("ws");

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

async function waitForCondition(check, label, timeoutMs = 5000, intervalMs = 50) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    if (await check()) {
      return;
    }

    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  throw new Error(`Timed out waiting for ${label}`);
}

async function createMockOpenClawGateway() {
  const connections = new Set();
  const requests = [];
  const listeners = new Set();
  const server = new WebSocketServer({
    host: "127.0.0.1",
    port: 0
  });

  await new Promise((resolve) => {
    server.once("listening", resolve);
  });

  server.on("connection", (socket) => {
    connections.add(socket);
    socket.send(
      JSON.stringify({
        type: "event",
        event: "connect.challenge",
        payload: {
          nonce: "test-nonce"
        }
      })
    );

    socket.on("message", (raw) => {
      const frame = JSON.parse(String(raw));

      if (frame.type === "req" && frame.method === "connect") {
        socket.send(
          JSON.stringify({
            type: "res",
            id: frame.id,
            ok: true,
            payload: {
              server: {
                host: "mock-openclaw"
              },
              snapshot: {
                sessionDefaults: {
                  mainSessionKey: "default-main"
                }
              }
            }
          })
        );
        return;
      }

      const entry = {
        socket,
        frame
      };
      requests.push(entry);
      for (const listener of listeners) {
        listener(entry);
      }
    });

    socket.on("close", () => {
      connections.delete(socket);
    });
  });

  const address = server.address();
  const port = typeof address === "object" && address ? address.port : 0;

  return {
    url: `ws://127.0.0.1:${port}`,
    async waitForRequest(predicate, label, timeoutMs = 5000) {
      const existing = requests.find((entry) => predicate(entry.frame));
      if (existing) {
        return existing;
      }

      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
          listeners.delete(onRequest);
          reject(new Error(`Timed out waiting for ${label}`));
        }, timeoutMs);

        const onRequest = (entry) => {
          if (!predicate(entry.frame)) {
            return;
          }

          clearTimeout(timer);
          listeners.delete(onRequest);
          resolve(entry);
        };

        listeners.add(onRequest);
      });
    },
    replyOk(socket, requestId, payload = {}) {
      socket.send(
        JSON.stringify({
          type: "res",
          id: requestId,
          ok: true,
          payload
        })
      );
    },
    replyError(socket, requestId, code, message) {
      socket.send(
        JSON.stringify({
          type: "res",
          id: requestId,
          ok: false,
          error: {
            code,
            message
          }
        })
      );
    },
    emit(event, payload) {
      for (const socket of connections) {
        if (socket.readyState === socket.OPEN) {
          socket.send(
            JSON.stringify({
              type: "event",
              event,
              payload
            })
          );
        }
      }
    },
    closeClientSockets() {
      for (const socket of connections) {
        socket.close();
      }
    },
    async close() {
      for (const socket of connections) {
        socket.close();
      }

      await new Promise((resolve) => {
        server.close(resolve);
      });
    }
  };
}

test("relay bridges openclaw sessions through the gateway, translates events, and recovers after disconnect", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-openclaw-bridge-"));
  const gateway = await createMockOpenClawGateway();
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_LOG_LEVEL: "error",
    RELAY_OPENCLAW_URL: gateway.url,
    RELAY_COMPANION_TIMEOUT_MS: "1000"
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
  const android = new WebSocket(`${baseWsUrl}/v1/ws?token=${config.staticToken}`);
  await waitForOpen(android, "android");

  t.after(async () => {
    android.close();
    await runtime.close();
    await gateway.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForCondition(async () => {
    const response = await fetch(`${baseUrl}/healthz`);
    return response.ok && (await response.json()).openclaw === "online";
  }, "openclaw online");

  const createResponsePromise = fetch(`${baseUrl}/v1/sessions`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      provider: "openclaw",
      host_id: "relay-local",
      cwd: "/srv/project",
      prompt: "hello bridge",
      permission_mode: "bypassPermissions"
    })
  });

  const createRequest = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "session.create",
    "session.create"
  );
  assert.equal(createRequest.frame.params.cwd, "/srv/project");
  assert.equal(createRequest.frame.params.prompt, "hello bridge");
  gateway.replyOk(createRequest.socket, createRequest.frame.id, {
    session_key: "oc-1"
  });

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 201);
  assert.equal(createPayload.session.provider, "openclaw");
  assert.equal(createPayload.session.provider_session_id, "oc-1");
  assert.equal(createPayload.session.status, "running");

  const sessionId = createPayload.session.id;
  android.send(
    JSON.stringify({
      action: "subscribe",
      session_id: sessionId
    })
  );

  const deltaMessagePromise = waitForJsonMessage(
    android,
    (message) =>
      message.type === "event" &&
      message.session_id === sessionId &&
      message.event_type === "assistant_delta",
    "assistant delta broadcast"
  );

  gateway.emit("session.ready", {
    session_key: "oc-1"
  });
  gateway.emit("transcript.text", {
    session_key: "oc-1",
    role: "agent",
    text: "hello",
    complete: false
  });

  const deltaMessage = await deltaMessagePromise;
  assert.equal(deltaMessage.payload.text, "hello");

  gateway.emit("transcript.text", {
    session_key: "oc-1",
    role: "agent",
    text: "hello world",
    complete: true
  });
  gateway.emit("session.complete", {
    session_key: "oc-1",
    result: "done"
  });

  await waitForCondition(() => {
    const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
    return session?.status === "completed";
  }, "completed openclaw session");

  const eventsResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/events?since_seq=0`, {
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  const eventsPayload = await eventsResponse.json();
  const eventTypes = eventsPayload.events.map((event) => event.type);

  assert.equal(eventsResponse.status, 200);
  assert.equal(eventTypes.includes("session_started"), true);
  assert.equal(eventTypes.filter((eventType) => eventType === "session_started").length, 1);
  assert.equal(eventTypes.includes("assistant_delta"), true);
  assert.equal(eventTypes.includes("assistant_message"), true);
  assert.equal(eventTypes.includes("session_result"), true);

  const eventCountBeforeLateMessage = eventsPayload.events.length;
  gateway.emit("transcript.text", {
    session_key: "oc-1",
    role: "agent",
    text: "late event",
    complete: false
  });

  await new Promise((resolve) => setTimeout(resolve, 150));

  const lateEventsResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/events?since_seq=0`, {
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  const lateEventsPayload = await lateEventsResponse.json();
  assert.equal(lateEventsPayload.events.length, eventCountBeforeLateMessage);

  const secondCreateResponsePromise = fetch(`${baseUrl}/v1/sessions`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      provider: "openclaw",
      host_id: "relay-local",
      cwd: "/srv/project",
      prompt: "disconnect me"
    })
  });

  const secondCreateRequest = await gateway.waitForRequest(
    (frame) =>
      frame.type === "req" &&
      frame.method === "session.create" &&
      frame.params.session_id !== sessionId,
    "second session.create"
  );
  gateway.replyOk(secondCreateRequest.socket, secondCreateRequest.frame.id, {
    session_key: "oc-2"
  });

  const secondCreateResponse = await secondCreateResponsePromise;
  const secondCreatePayload = await secondCreateResponse.json();
  assert.equal(secondCreateResponse.status, 201);
  assert.equal(secondCreatePayload.session.status, "running");

  const secondSessionId = secondCreatePayload.session.id;
  gateway.closeClientSockets();

  await waitForCondition(async () => {
    const response = await fetch(`${baseUrl}/healthz`);
    return response.ok && (await response.json()).openclaw === "offline";
  }, "openclaw offline after disconnect");

  await waitForCondition(() => {
    const failedSession = runtime.db
      .prepare("SELECT status, error_code FROM sessions WHERE id = ?")
      .get(secondSessionId);
    return failedSession?.status === "failed" && failedSession?.error_code === "provider_unreachable";
  }, "failed session after gateway disconnect");

  runtime.openClawBridge.connect();

  await waitForCondition(async () => {
    const response = await fetch(`${baseUrl}/healthz`);
    return response.ok && (await response.json()).openclaw === "online";
  }, "openclaw reconnect online");
});

test("relay keeps the session unchanged when openclaw resume is rejected by the gateway", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-openclaw-resume-reject-"));
  const gateway = await createMockOpenClawGateway();
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_LOG_LEVEL: "error",
    RELAY_OPENCLAW_URL: gateway.url,
    RELAY_COMPANION_TIMEOUT_MS: "1000"
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

  t.after(async () => {
    await runtime.close();
    await gateway.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForCondition(async () => {
    const response = await fetch(`${baseUrl}/healthz`);
    return response.ok && (await response.json()).openclaw === "online";
  }, "openclaw online");

  const createResponsePromise = fetch(`${baseUrl}/v1/sessions`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      provider: "openclaw",
      host_id: "relay-local",
      cwd: "/srv/project",
      prompt: "resume me"
    })
  });

  const createRequest = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "session.create",
    "openclaw session.create"
  );
  gateway.replyOk(createRequest.socket, createRequest.frame.id, {
    session_key: "oc-resume"
  });

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 201);

  const sessionId = createPayload.session.id;
  gateway.emit("session.complete", {
    session_key: "oc-resume",
    result: "done"
  });

  await waitForCondition(() => {
    const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
    return session?.status === "completed";
  }, "completed openclaw session");

  const resumeResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/resume`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const resumeRequest = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "session.resume",
    "openclaw session.resume"
  );
  gateway.replyError(
    resumeRequest.socket,
    resumeRequest.frame.id,
    "METHOD_NOT_SUPPORTED",
    "session.resume unsupported"
  );

  const resumeResponse = await resumeResponsePromise;
  assert.equal(resumeResponse.status, 502);
  assert.deepEqual(await resumeResponse.json(), { error: "provider_unreachable" });

  const sessionAfterResume = runtime.db
    .prepare("SELECT status FROM sessions WHERE id = ?")
    .get(sessionId);
  const resumeAuditCount = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM audit_logs WHERE action = 'session.resume' AND session_id = ?")
    .get(sessionId);

  assert.deepEqual(sessionAfterResume, {
    status: "completed"
  });
  assert.deepEqual(resumeAuditCount, {
    count: 0
  });
});

test("relay falls back to legacy openclaw message and cancel methods when newer methods are unsupported", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-openclaw-legacy-fallback-"));
  const gateway = await createMockOpenClawGateway();
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_LOG_LEVEL: "error",
    RELAY_OPENCLAW_URL: gateway.url,
    RELAY_COMPANION_TIMEOUT_MS: "1000"
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

  t.after(async () => {
    await runtime.close();
    await gateway.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForCondition(async () => {
    const response = await fetch(`${baseUrl}/healthz`);
    return response.ok && (await response.json()).openclaw === "online";
  }, "openclaw online");

  const createResponsePromise = fetch(`${baseUrl}/v1/sessions`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      provider: "openclaw",
      host_id: "relay-local",
      cwd: "/srv/project",
      prompt: "legacy me"
    })
  });

  const createRequest = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "session.create",
    "openclaw session.create"
  );
  gateway.replyOk(createRequest.socket, createRequest.frame.id, {
    session_key: "oc-legacy"
  });

  const createResponse = await createResponsePromise;
  const createPayload = await createResponse.json();
  assert.equal(createResponse.status, 201);
  assert.equal(createPayload.session.status, "running");

  const sessionId = createPayload.session.id;
  const messageResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/message`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      text: "fallback message"
    })
  });

  const messageSendRequest = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "message.send",
    "openclaw message.send"
  );
  gateway.replyError(
    messageSendRequest.socket,
    messageSendRequest.frame.id,
    "METHOD_NOT_SUPPORTED",
    "message.send unsupported"
  );

  const chatSendRequest = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "chat.send",
    "openclaw chat.send"
  );
  assert.equal(chatSendRequest.frame.params.session_key, "oc-legacy");
  assert.equal(chatSendRequest.frame.params.message, "fallback message");
  gateway.replyOk(chatSendRequest.socket, chatSendRequest.frame.id, {});

  const messageResponse = await messageResponsePromise;
  assert.equal(messageResponse.status, 200);
  assert.deepEqual(await messageResponse.json(), { ok: true });

  const cancelResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/cancel`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const sessionCancelRequest = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "session.cancel",
    "openclaw session.cancel"
  );
  gateway.replyError(
    sessionCancelRequest.socket,
    sessionCancelRequest.frame.id,
    "METHOD_NOT_SUPPORTED",
    "session.cancel unsupported"
  );

  const chatAbortRequest = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "chat.abort",
    "openclaw chat.abort"
  );
  assert.equal(chatAbortRequest.frame.params.session_key, "oc-legacy");
  gateway.replyOk(chatAbortRequest.socket, chatAbortRequest.frame.id, {});

  const cancelResponse = await cancelResponsePromise;
  const cancelPayload = await cancelResponse.json();
  assert.equal(cancelResponse.status, 200);
  assert.equal(cancelPayload.status, "cancelled");

  const cancelledSession = runtime.db
    .prepare("SELECT status FROM sessions WHERE id = ?")
    .get(sessionId);
  assert.deepEqual(cancelledSession, {
    status: "cancelled"
  });
});
