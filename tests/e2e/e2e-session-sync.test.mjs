import assert from "node:assert/strict";
import test from "node:test";

import {
  apiGet,
  apiPost,
  assertStatus,
  cleanupSession,
  createBookSession,
  LONG_TIMEOUT_MS,
  waitForStatus
} from "./helpers.mjs";

async function createTrackedBookSession(t, prompt) {
  const fixture = await createBookSession(t, {
    prompt,
    cleanup: false
  });
  if (!fixture) {
    return null;
  }

  t.after(async () => {
    await cleanupSession(fixture.session.id);
  });

  return fixture;
}

test("E2E-SYNC-1: GET /sessions returns local_available field for all sessions", { timeout: LONG_TIMEOUT_MS }, async (t) => {
  const fixture = await createTrackedBookSession(t, "sync list local_available");
  if (!fixture) {
    return;
  }

  const { session } = fixture;
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  const response = assertStatus(await apiGet("/sessions"), 200);
  assert.ok(Array.isArray(response.sessions));
  assert.ok(response.sessions.length > 0);
  assert.ok(response.sessions.every((item) => typeof item.local_available === "boolean"));

  const createdSession = response.sessions.find((item) => item.id === session.id);
  assert.ok(createdSession);
  assert.equal(createdSession.local_available, true);
});

test("E2E-SYNC-2: GET /sessions/:id returns local_available for individual session", { timeout: LONG_TIMEOUT_MS }, async (t) => {
  const fixture = await createTrackedBookSession(t, "sync detail local_available");
  if (!fixture) {
    return;
  }

  const { session } = fixture;
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  const response = assertStatus(await apiGet(`/sessions/${session.id}`), 200);
  assert.equal(response.id, session.id);
  assert.equal(response.local_available, true);
});

test("E2E-SYNC-3: completed session retains local_available true", { timeout: LONG_TIMEOUT_MS }, async (t) => {
  const fixture = await createTrackedBookSession(t, "sync completed local_available");
  if (!fixture) {
    return;
  }

  const { session } = fixture;
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  assertStatus(await apiPost(`/sessions/${session.id}/complete`), 200);
  await waitForStatus(session.id, ["completed"], LONG_TIMEOUT_MS);

  const response = assertStatus(await apiGet(`/sessions/${session.id}`), 200);
  assert.equal(response.status, "completed");
  assert.equal(response.local_available, true);
});

test("E2E-SYNC-4: session list includes local_available for mixed providers", { timeout: LONG_TIMEOUT_MS }, async (t) => {
  const fixture = await createTrackedBookSession(t, "sync mixed providers local_available");
  if (!fixture) {
    return;
  }

  const { session } = fixture;
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  const response = assertStatus(await apiGet("/sessions"), 200);
  assert.ok(Array.isArray(response.sessions));
  assert.ok(response.sessions.every((item) => typeof item.local_available === "boolean"));

  const createdSession = response.sessions.find((item) => item.id === session.id);
  assert.ok(createdSession);
  assert.equal(createdSession.provider, "book");
  assert.equal(createdSession.local_available, true);
});
