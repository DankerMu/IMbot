import assert from "node:assert/strict";
import test from "node:test";

import {
  apiDelete,
  apiGet,
  apiPost,
  assertError,
  assertStatus,
  createBookSession,
  LONG_TIMEOUT_MS,
  waitForStatus
} from "./helpers.mjs";

test("E2E-21: completed sessions reject new messages", { timeout: 120_000 }, async (t) => {
  const fixture = await createBookSession(t, {
    prompt: "complete then reject message",
    cleanup: true
  });
  if (!fixture) {
    return;
  }
  const { session } = fixture;

  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
  await apiPost(`/sessions/${session.id}/complete`);
  await waitForStatus(session.id, ["completed"], LONG_TIMEOUT_MS);

  const response = await apiPost(`/sessions/${session.id}/message`, { text: "hello" });
  assertError(response, 409, "state_conflict");
});

test("E2E-22: complete succeeds while a session is still running", { timeout: 120_000 }, async (t) => {
  const fixture = await createBookSession(t, {
    prompt: "请尽量详细地写一段长回复，让运行时间足够长。",
    cleanup: true
  });
  if (!fixture) {
    return;
  }
  const { session } = fixture;

  const running = await waitForStatus(session.id, ["running", "idle"], 30_000);
  if (running.status !== "running") {
    t.skip("Could not observe a running window before the session became idle.");
  }

  const completed = assertStatus(await apiPost(`/sessions/${session.id}/complete`), 200);
  assert.equal(completed.status, "completed");
  await waitForStatus(session.id, ["completed"], LONG_TIMEOUT_MS);
});

test("E2E-23: cancelled sessions cannot be resumed", { timeout: 120_000 }, async (t) => {
  const fixture = await createBookSession(t, {
    prompt: "cancel then reject resume",
    cleanup: true
  });
  if (!fixture) {
    return;
  }
  const { session } = fixture;

  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
  await apiPost(`/sessions/${session.id}/cancel`);
  await waitForStatus(session.id, ["cancelled"], LONG_TIMEOUT_MS);

  const resume = await apiPost(`/sessions/${session.id}/resume`);
  assertError(resume, 409, "state_conflict");
});

test("E2E-24: unknown sessions return 404 across lifecycle endpoints", async () => {
  const unknownId = "nonexistent-id";
  assertError(await apiGet(`/sessions/${unknownId}`), 404, "not_found");
  assertError(await apiPost(`/sessions/${unknownId}/message`, { text: "x" }), 404, "not_found");
  assertError(await apiPost(`/sessions/${unknownId}/complete`), 404, "not_found");
  assertError(await apiPost(`/sessions/${unknownId}/cancel`), 404, "not_found");
  assertError(await apiPost(`/sessions/${unknownId}/resume`), 404, "not_found");
  assertError(await apiDelete(`/sessions/${unknownId}`), 404, "not_found");
});

test("E2E-25: creating a session against an offline or unknown host fails", async () => {
  const response = await apiPost("/sessions", {
    provider: "book",
    host_id: "nonexistent-host",
    cwd: "/tmp",
    prompt: "test",
    permission_mode: "bypassPermissions"
  });

  assert.ok(
    response.status === 404 || response.status === 502,
    `Expected 404 or 502, got ${response.status} with body ${JSON.stringify(response.body)}`
  );
  assert.ok(
    response.body?.error === "not_found" || response.body?.error === "host_offline",
    `Expected error not_found or host_offline, got ${JSON.stringify(response.body)}`
  );
});
