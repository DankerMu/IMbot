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

async function createRelayRuntime(prefix) {
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

  return {
    tempDir,
    config,
    runtime,
    baseUrl: `http://127.0.0.1:${port}`,
    baseWsUrl: `ws://127.0.0.1:${port}`
  };
}

async function createRunningSession({ baseUrl, config }, companion, overrides = {}) {
  companion.send(
    JSON.stringify({
      type: "heartbeat",
      host_id: "macbook-1",
      providers: ["claude"],
      uptime: 1
    })
  );

  const responsePromise = fetch(`${baseUrl}/v1/sessions`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      provider: "claude",
      host_id: "macbook-1",
      cwd: "/tmp/project",
      prompt: "answer endpoint",
      permission_mode: "bypassPermissions",
      ...overrides
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
        provider_session_id: "provider-session-1"
      }
    })
  );

  const response = await responsePromise;
  const payload = await response.json();
  assert.equal(response.status, 201);
  assert.equal(payload.session.local_available, true);

  return {
    sessionId: payload.session.id
  };
}

async function markSessionStatus(runtime, sessionId, status) {
  const now = new Date().toISOString();
  const result = runtime.db
    .prepare("UPDATE sessions SET status = ?, updated_at = ?, last_active_at = ? WHERE id = ?")
    .run(status, now, now, sessionId);
  assert.equal(result.changes, 1);
}

function authHeaders(config, withJson = false) {
  return withJson
    ? {
        authorization: `Bearer ${config.staticToken}`,
        "content-type": "application/json"
      }
    : {
        authorization: `Bearer ${config.staticToken}`
      };
}

test("POST /sessions/:id/answer returns 200 for running session with mocked companion", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-answer-ok-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId } = await createRunningSession({ baseUrl, config }, companion);

  const answerResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/answer`, {
    method: "POST",
    headers: authHeaders(config, true),
    body: JSON.stringify({
      call_id: "req-ask-1",
      answer: "Alpha",
      question_index: 0
    })
  });

  const answerCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "answer_interactive_tool" && message.session_id === sessionId,
    "answer_interactive_tool command"
  );
  assert.equal(answerCommand.call_id, "req-ask-1");
  assert.equal(answerCommand.answer, "Alpha");
  assert.equal(answerCommand.question_index, 0);

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: answerCommand.req_id,
      status: "ok"
    })
  );

  const answerResponse = await answerResponsePromise;
  assert.equal(answerResponse.status, 200);
  assert.deepEqual(await answerResponse.json(), { ok: true });
});

test("POST /sessions/:id/answer returns 404 for non-existent session", async (t) => {
  const { tempDir, config, runtime, baseUrl } = await createRelayRuntime("imbot-relay-answer-404-");

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const response = await fetch(`${baseUrl}/v1/sessions/nonexistent/answer`, {
    method: "POST",
    headers: authHeaders(config, true),
    body: JSON.stringify({
      call_id: "req-ask-1",
      answer: "Alpha"
    })
  });

  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), { error: "not_found" });
});

test("POST /sessions/:id/answer returns 409 for non-running session", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-answer-409-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId } = await createRunningSession({ baseUrl, config }, companion);
  await markSessionStatus(runtime, sessionId, "idle");

  const response = await fetch(`${baseUrl}/v1/sessions/${sessionId}/answer`, {
    method: "POST",
    headers: authHeaders(config, true),
    body: JSON.stringify({
      call_id: "req-ask-1",
      answer: "Alpha"
    })
  });

  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), { error: "state_conflict" });
});

test("POST /sessions/:id/answer returns 400 for missing required fields", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-answer-400-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId } = await createRunningSession({ baseUrl, config }, companion);

  const emptyBodyResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/answer`, {
    method: "POST",
    headers: authHeaders(config, true),
    body: JSON.stringify({})
  });
  assert.equal(emptyBodyResponse.status, 400);
  assert.deepEqual(await emptyBodyResponse.json(), { error: "invalid_request" });

  const missingCallIdResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/answer`, {
    method: "POST",
    headers: authHeaders(config, true),
    body: JSON.stringify({
      answer: "Alpha"
    })
  });
  assert.equal(missingCallIdResponse.status, 400);
  assert.deepEqual(await missingCallIdResponse.json(), { error: "invalid_request" });
});

test("POST /sessions/:id/answer returns 502 when companion is offline", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-answer-502-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId } = await createRunningSession({ baseUrl, config }, companion);

  runtime.db
    .prepare("UPDATE hosts SET status = 'offline', updated_at = ? WHERE id = 'macbook-1'")
    .run(new Date().toISOString());

  const response = await fetch(`${baseUrl}/v1/sessions/${sessionId}/answer`, {
    method: "POST",
    headers: authHeaders(config, true),
    body: JSON.stringify({
      call_id: "req-ask-1",
      answer: "Alpha"
    })
  });

  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: "host_offline" });
});
