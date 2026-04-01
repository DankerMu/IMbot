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
      prompt: "hello lifecycle",
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

  return {
    sessionId: payload.session.id,
    providerSessionId: "provider-session-1"
  };
}

async function waitForSessionStatus(runtime, sessionId, status, label = `session ${status}`) {
  await waitForCondition(() => {
    const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
    return session?.status === status;
  }, label);
}

test("relay supports the idle multi-turn lifecycle and completes idle sessions through the companion", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-idle-lifecycle-");
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

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_idle",
      payload: {
        result: "turn one complete"
      }
    })
  );

  await waitForSessionStatus(runtime, sessionId, "idle", "session idle after first turn");

  const messageResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/message`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      text: "followup"
    })
  });

  const sendCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "send_message" && message.session_id === sessionId,
    "idle send_message command"
  );
  assert.equal(sendCommand.text, "followup");

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: sendCommand.req_id,
      status: "ok"
    })
  );

  const messageResponse = await messageResponsePromise;
  assert.equal(messageResponse.status, 200);
  assert.deepEqual(await messageResponse.json(), { ok: true });
  assert.deepEqual(runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId), {
    status: "running"
  });

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_idle",
      payload: {
        result: "turn two complete"
      }
    })
  );

  await waitForSessionStatus(runtime, sessionId, "idle", "session idle after followup");

  const completeResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/complete`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const completeCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "complete_session" && message.session_id === sessionId,
    "idle complete_session command"
  );

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_result",
      payload: {
        result: "completed from idle"
      }
    })
  );
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: completeCommand.req_id,
      status: "ok"
    })
  );

  const completeResponse = await completeResponsePromise;
  const completedSession = await completeResponse.json();
  assert.equal(completeResponse.status, 200);
  assert.equal(completedSession.status, "completed");
  await waitForSessionStatus(runtime, sessionId, "completed", "completed after idle complete");

  const completeAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.complete' AND session_id = ?")
    .get(sessionId);
  assert.deepEqual(JSON.parse(completeAudit.detail), {
    previous_status: "idle"
  });
});

test("relay dispatches complete_session for running sessions", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-running-complete-");
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

  const completeResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/complete`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const completeCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "complete_session" && message.session_id === sessionId,
    "running complete_session command"
  );

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_result",
      payload: {
        result: "completed from running"
      }
    })
  );
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: completeCommand.req_id,
      status: "ok"
    })
  );

  const completeResponse = await completeResponsePromise;
  const completedSession = await completeResponse.json();
  assert.equal(completeResponse.status, 200);
  assert.equal(completedSession.status, "completed");
  await waitForSessionStatus(runtime, sessionId, "completed", "completed after running complete");

  const completeAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.complete' AND session_id = ?")
    .get(sessionId);
  assert.deepEqual(JSON.parse(completeAudit.detail), {
    previous_status: "running"
  });
});

test("relay rejects complete on terminal sessions with state_conflict", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-complete-terminal-");
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

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_result",
      payload: {
        result: "done"
      }
    })
  );

  await waitForSessionStatus(runtime, sessionId, "completed", "completed session");

  const completeResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/complete`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  assert.equal(completeResponse.status, 409);
  assert.deepEqual(await completeResponse.json(), { error: "state_conflict" });
});

test("relay cancels idle sessions through the existing cancel endpoint", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-idle-cancel-");
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

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_idle",
      payload: {
        result: "idle before cancel"
      }
    })
  );

  await waitForSessionStatus(runtime, sessionId, "idle", "session idle before cancel");

  const cancelResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/cancel`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const cancelCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "cancel_session" && message.session_id === sessionId,
    "idle cancel_session command"
  );

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: cancelCommand.req_id,
      status: "ok"
    })
  );

  const cancelResponse = await cancelResponsePromise;
  const cancelledSession = await cancelResponse.json();
  assert.equal(cancelResponse.status, 200);
  assert.equal(cancelledSession.status, "cancelled");
  await waitForSessionStatus(runtime, sessionId, "cancelled", "cancelled session");

  const cancelAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.cancel' AND session_id = ?")
    .get(sessionId);
  assert.deepEqual(JSON.parse(cancelAudit.detail), {
    previous_status: "idle"
  });
});

test("relay resumes cancelled sessions when the provider session mapping still exists", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime(
    "imbot-relay-cancelled-resume-"
  );
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId, providerSessionId } = await createRunningSession({ baseUrl, config }, companion);

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_idle",
      payload: {
        result: "idle before cancelled resume"
      }
    })
  );

  await waitForSessionStatus(runtime, sessionId, "idle", "session idle before cancelled resume");

  const cancelResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/cancel`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const cancelCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "cancel_session" && message.session_id === sessionId,
    "cancel_session before cancelled resume"
  );

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: cancelCommand.req_id,
      status: "ok"
    })
  );

  const cancelResponse = await cancelResponsePromise;
  const cancelledSession = await cancelResponse.json();
  assert.equal(cancelResponse.status, 200);
  assert.equal(cancelledSession.status, "cancelled");
  await waitForSessionStatus(runtime, sessionId, "cancelled", "cancelled session before resume");

  const resumeResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/resume`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const resumeCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "resume_session" && message.session_id === sessionId,
    "resume_session after cancel"
  );
  assert.equal(resumeCommand.provider_session_id, providerSessionId);

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: resumeCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: providerSessionId
      }
    })
  );

  const resumeResponse = await resumeResponsePromise;
  const resumedSession = await resumeResponse.json();
  assert.equal(resumeResponse.status, 200);
  assert.equal(resumedSession.status, "running");
  await waitForSessionStatus(runtime, sessionId, "running", "running after cancelled resume");

  const resumeAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.resume' AND session_id = ? ORDER BY created_at DESC LIMIT 1")
    .get(sessionId);
  assert.deepEqual(JSON.parse(resumeAudit.detail), {
    previous_status: "cancelled"
  });
});

test("relay rejects resuming a cancelled session without provider_session_id", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime(
    "imbot-relay-cancelled-no-provider-"
  );
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

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_idle",
      payload: { result: "idle before cancel" }
    })
  );

  await waitForSessionStatus(runtime, sessionId, "idle", "session idle");

  const cancelResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/cancel`, {
    method: "POST",
    headers: { authorization: `Bearer ${config.staticToken}` }
  });

  const cancelCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "cancel_session" && message.session_id === sessionId,
    "cancel_session command"
  );

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: cancelCommand.req_id,
      status: "ok"
    })
  );

  const cancelResponse = await cancelResponsePromise;
  assert.equal(cancelResponse.status, 200);
  await waitForSessionStatus(runtime, sessionId, "cancelled", "cancelled session");

  // Clear the provider_session_id to simulate a session that lost its mapping
  runtime.db
    .prepare("UPDATE sessions SET provider_session_id = NULL WHERE id = ?")
    .run(sessionId);

  const resumeResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/resume`, {
    method: "POST",
    headers: { authorization: `Bearer ${config.staticToken}` }
  });

  assert.equal(resumeResponse.status, 409);
  assert.deepEqual(await resumeResponse.json(), { error: "session_not_resumable" });

  // Session status should remain cancelled
  const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
  assert.deepEqual(session, { status: "cancelled" });
});

test("relay supports resume, message, cancel, delete, catch-up, and lifecycle audit logging", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-lifecycle-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const auditPrompt = "lifecycle prompt ".repeat(10);
  const { sessionId, providerSessionId } = await createRunningSession({ baseUrl, config }, companion, {
    prompt: auditPrompt
  });

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_result",
      payload: {
        result: "done"
      }
    })
  );

  await waitForCondition(() => {
    const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
    return session?.status === "completed";
  }, "completed session");

  const resumeResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/resume`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const resumeCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "resume_session" && message.session_id === sessionId,
    "resume_session command"
  );
  assert.equal(resumeCommand.provider_session_id, providerSessionId);

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: resumeCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: providerSessionId
      }
    })
  );

  const resumeResponse = await resumeResponsePromise;
  const resumedSession = await resumeResponse.json();
  assert.equal(resumeResponse.status, 200);
  assert.equal(resumedSession.status, "running");

  const invalidMessageResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/message`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({})
  });
  assert.equal(invalidMessageResponse.status, 400);
  assert.deepEqual(await invalidMessageResponse.json(), { error: "invalid_request" });

  const messageResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/message`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      text: "followup"
    })
  });

  const sendCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "send_message" && message.session_id === sessionId,
    "send_message command"
  );
  assert.equal(sendCommand.text, "followup");

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: sendCommand.req_id,
      status: "ok"
    })
  );

  const messageResponse = await messageResponsePromise;
  assert.equal(messageResponse.status, 200);
  assert.deepEqual(await messageResponse.json(), { ok: true });

  const eventsResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/events?since_seq=0&limit=2`, {
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  const eventsPayload = await eventsResponse.json();
  assert.equal(eventsResponse.status, 200);
  assert.equal(eventsPayload.events.length, 2);
  assert.equal(eventsPayload.has_more, true);

  const cancelResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/cancel`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const cancelCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "cancel_session" && message.session_id === sessionId,
    "cancel_session command"
  );

  const messageDuringCancelResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/message`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      text: "during cancel"
    })
  });
  assert.equal(messageDuringCancelResponse.status, 409);
  assert.deepEqual(await messageDuringCancelResponse.json(), { error: "state_conflict" });

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: cancelCommand.req_id,
      status: "ok"
    })
  );

  const cancelResponse = await cancelResponsePromise;
  const cancelledSession = await cancelResponse.json();
  assert.equal(cancelResponse.status, 200);
  assert.equal(cancelledSession.status, "cancelled");

  const cancelledMessageResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/message`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      text: "after cancel"
    })
  });
  assert.equal(cancelledMessageResponse.status, 409);
  assert.deepEqual(await cancelledMessageResponse.json(), { error: "state_conflict" });

  const createAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.create' AND session_id = ?")
    .get(sessionId);
  const resumeAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.resume' AND session_id = ?")
    .get(sessionId);
  const cancelAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.cancel' AND session_id = ?")
    .get(sessionId);

  assert.deepEqual(JSON.parse(createAudit.detail), {
    provider: "claude",
    host_id: "macbook-1",
    cwd: "/tmp/project",
    prompt: auditPrompt.slice(0, 100)
  });
  assert.deepEqual(JSON.parse(resumeAudit.detail), {
    previous_status: "completed"
  });
  assert.deepEqual(JSON.parse(cancelAudit.detail), {
    previous_status: "running"
  });

  const deleteResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}`, {
    method: "DELETE",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  assert.equal(deleteResponse.status, 204);

  const deletedSession = runtime.db.prepare("SELECT * FROM sessions WHERE id = ?").get(sessionId);
  const deletedEvents = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM session_events WHERE session_id = ?")
    .get(sessionId);
  const deleteAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.delete' AND session_id = ?")
    .get(sessionId);

  assert.equal(deletedSession, undefined);
  assert.deepEqual(deletedEvents, { count: 0 });
  assert.deepEqual(JSON.parse(deleteAudit.detail), {
    provider: "claude",
    status: "cancelled"
  });

  const missingEventsResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/events?since_seq=0`, {
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  assert.equal(missingEventsResponse.status, 404);
  assert.deepEqual(await missingEventsResponse.json(), { error: "not_found" });
});

test("relay returns the provider terminal state when it wins the cancel race", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-cancel-race-");
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

  const cancelResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/cancel`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const cancelCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "cancel_session" && message.session_id === sessionId,
    "cancel_session command"
  );

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_result",
      payload: {
        result: "done before cancel ack"
      }
    })
  );

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: cancelCommand.req_id,
      status: "ok"
    })
  );

  const cancelResponse = await cancelResponsePromise;
  const terminalSession = await cancelResponse.json();
  assert.equal(cancelResponse.status, 200);
  assert.equal(terminalSession.status, "completed");

  await waitForCondition(() => {
    const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
    return session?.status === "completed";
  }, "completed session after cancel race");

  const cancelAuditCount = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM audit_logs WHERE action = 'session.cancel' AND session_id = ?")
    .get(sessionId);
  assert.deepEqual(cancelAuditCount, {
    count: 0
  });
});

test("relay resumes failed sessions and clears stored error fields", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-retry-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId, providerSessionId } = await createRunningSession({ baseUrl, config }, companion, {
    prompt: "retry me"
  });

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_error",
      payload: {
        error_code: "provider_unreachable",
        message: "temporary failure"
      }
    })
  );

  await waitForCondition(() => {
    const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
    return session?.status === "failed";
  }, "failed session");

  const resumeResponsePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/resume`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const resumeCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "resume_session" && message.session_id === sessionId,
    "resume_session retry command"
  );
  assert.equal(resumeCommand.provider_session_id, providerSessionId);

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: resumeCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: providerSessionId
      }
    })
  );

  const resumeResponse = await resumeResponsePromise;
  const resumedSession = await resumeResponse.json();
  assert.equal(resumeResponse.status, 200);
  assert.equal(resumedSession.status, "running");
  assert.equal(resumedSession.error_code, null);
  assert.equal(resumedSession.error_message, null);

  const resumeAudit = runtime.db
    .prepare("SELECT detail FROM audit_logs WHERE action = 'session.resume' AND session_id = ?")
    .get(sessionId);
  assert.deepEqual(JSON.parse(resumeAudit.detail), {
    previous_status: "failed"
  });
});

test("relay rejects concurrent lifecycle mutations while a resume is in flight", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-resume-lock-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId, providerSessionId } = await createRunningSession({ baseUrl, config }, companion, {
    prompt: "resume lock"
  });

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_result",
      payload: {
        result: "done"
      }
    })
  );

  await waitForCondition(() => {
    const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
    return session?.status === "completed";
  }, "completed session");

  const firstResumePromise = fetch(`${baseUrl}/v1/sessions/${sessionId}/resume`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  const resumeCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "resume_session" && message.session_id === sessionId,
    "resume_session command"
  );
  assert.equal(resumeCommand.provider_session_id, providerSessionId);

  const secondResumeResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/resume`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  assert.equal(secondResumeResponse.status, 409);
  assert.deepEqual(await secondResumeResponse.json(), { error: "state_conflict" });

  const deleteDuringResumeResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}`, {
    method: "DELETE",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });
  assert.equal(deleteDuringResumeResponse.status, 409);
  assert.deepEqual(await deleteDuringResumeResponse.json(), { error: "state_conflict" });

  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: resumeCommand.req_id,
      status: "ok",
      data: {
        provider_session_id: providerSessionId
      }
    })
  );

  const firstResumeResponse = await firstResumePromise;
  assert.equal(firstResumeResponse.status, 200);

  const sessionStartedCount = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM session_events WHERE session_id = ? AND type = 'session_started'")
    .get(sessionId);
  assert.deepEqual(sessionStartedCount, { count: 2 });
});

test("relay rejects deleting a running session", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-delete-running-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId } = await createRunningSession({ baseUrl, config }, companion, {
    prompt: "delete while running"
  });

  const deleteResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}`, {
    method: "DELETE",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  assert.equal(deleteResponse.status, 409);
  assert.deepEqual(await deleteResponse.json(), { error: "state_conflict" });

  const sessionAfterDelete = runtime.db
    .prepare("SELECT status FROM sessions WHERE id = ?")
    .get(sessionId);
  const deleteAudit = runtime.db
    .prepare("SELECT COUNT(*) AS count FROM audit_logs WHERE action = 'session.delete' AND session_id = ?")
    .get(sessionId);

  assert.deepEqual(sessionAfterDelete, {
    status: "running"
  });
  assert.deepEqual(deleteAudit, { count: 0 });
});

test("relay returns host_offline when resuming a completed session after the companion disconnects", async (t) => {
  const { tempDir, config, runtime, baseUrl, baseWsUrl } = await createRelayRuntime("imbot-relay-host-offline-");
  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  t.after(async () => {
    companion.close();
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const { sessionId } = await createRunningSession({ baseUrl, config }, companion, {
    prompt: "complete then disconnect"
  });

  companion.send(
    JSON.stringify({
      type: "event",
      session_id: sessionId,
      event_type: "session_result",
      payload: {
        result: "done"
      }
    })
  );

  await waitForCondition(() => {
    const session = runtime.db.prepare("SELECT status FROM sessions WHERE id = ?").get(sessionId);
    return session?.status === "completed";
  }, "completed session");

  companion.close();

  await waitForCondition(() => {
    const host = runtime.db.prepare("SELECT status FROM hosts WHERE id = 'macbook-1'").get();
    return host?.status === "offline";
  }, "offline host");

  const resumeResponse = await fetch(`${baseUrl}/v1/sessions/${sessionId}/resume`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${config.staticToken}`
    }
  });

  assert.equal(resumeResponse.status, 502);
  assert.deepEqual(await resumeResponse.json(), { error: "host_offline" });

  const sessionAfterResume = runtime.db
    .prepare("SELECT status FROM sessions WHERE id = ?")
    .get(sessionId);
  assert.deepEqual(sessionAfterResume, {
    status: "completed"
  });
});
