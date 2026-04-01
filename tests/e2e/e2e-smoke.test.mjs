import assert from "node:assert/strict";
import test from "node:test";

import {
  apiDelete,
  apiGet,
  apiPost,
  assertContinuousSeq,
  assertStatus,
  createBookSession,
  LONG_TIMEOUT_MS,
  request,
  SMOKE_TIMEOUT_MS,
  waitForStatus
} from "./helpers.mjs";

async function measure(stepTimings, label, action) {
  const start = Date.now();
  const result = await action();
  stepTimings.push({
    step: label,
    duration_ms: Date.now() - start
  });
  return result;
}

test("E2E-SMOKE: core node-side relay -> companion -> book flow passes end to end", { timeout: SMOKE_TIMEOUT_MS }, async (t) => {
  const stepTimings = [];

  const health = await measure(stepTimings, "healthz", () => request("GET", "/healthz", { absolute: true, auth: false }));
  const healthBody = assertStatus(health, 200);
  assert.equal(healthBody.companion, "online");

  const hosts = await measure(stepTimings, "hosts", () => apiGet("/hosts"));
  const hostsBody = assertStatus(hosts, 200);
  assert.ok(hostsBody.hosts.some((host) => host.id === "macbook-1" && host.status === "online"));

  const fixture = await measure(stepTimings, "create session", () =>
    createBookSession(t, {
      prompt: "hi",
      cleanup: false
    })
  );
  if (!fixture) {
    return;
  }
  const sessionId = fixture.session.id;

  await measure(stepTimings, "wait idle after create", () => waitForStatus(sessionId, ["idle"], LONG_TIMEOUT_MS));
  assertStatus(await measure(stepTimings, "send follow-up", () => apiPost(`/sessions/${sessionId}/message`, { text: "second turn" })), 200);
  await measure(stepTimings, "wait idle after follow-up", () => waitForStatus(sessionId, ["idle"], LONG_TIMEOUT_MS));

  const events = assertStatus(
    await measure(stepTimings, "fetch events", () => apiGet(`/sessions/${sessionId}/events?since_seq=0`)),
    200
  ).events;
  assertContinuousSeq(events);
  assert.ok(events.some((event) => event.type === "session_started"));
  assert.ok(events.filter((event) => event.type === "session_idle").length >= 2);
  assert.ok(events.some((event) => event.type === "user_message" && event.payload?.text === "second turn"));

  assertStatus(await measure(stepTimings, "complete session", () => apiPost(`/sessions/${sessionId}/complete`)), 200);
  await measure(stepTimings, "wait completed", () => waitForStatus(sessionId, ["completed"], LONG_TIMEOUT_MS));

  assertStatus(await measure(stepTimings, "resume session", () => apiPost(`/sessions/${sessionId}/resume`)), 200);
  await measure(stepTimings, "wait idle after resume", () => waitForStatus(sessionId, ["idle"], LONG_TIMEOUT_MS));

  assertStatus(await measure(stepTimings, "final complete", () => apiPost(`/sessions/${sessionId}/complete`)), 200);
  await measure(stepTimings, "wait final completed", () => waitForStatus(sessionId, ["completed"], LONG_TIMEOUT_MS));

  const deleteResponse = await measure(stepTimings, "delete session", () => apiDelete(`/sessions/${sessionId}`));
  assert.equal(deleteResponse.status, 204);

  const missing = await measure(stepTimings, "confirm deleted", () => apiGet(`/sessions/${sessionId}`));
  assert.equal(missing.status, 404);

  t.diagnostic(JSON.stringify(stepTimings, null, 2));
});
