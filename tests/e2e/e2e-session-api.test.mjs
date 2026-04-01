import assert from "node:assert/strict";
import test from "node:test";

import {
  apiGet,
  apiPost,
  assertError,
  assertStatus,
  createBookSession,
  LONG_TIMEOUT_MS,
  waitForStatus
} from "./helpers.mjs";

test("E2E-19: session list supports basic queries, filtering, and pagination", { timeout: 180_000 }, async (t) => {
  const completedFixture = await createBookSession(t, {
    prompt: "list completed",
    cleanup: true
  });
  if (!completedFixture) {
    return;
  }
  await waitForStatus(completedFixture.session.id, ["idle"], LONG_TIMEOUT_MS);
  assertStatus(await apiPost(`/sessions/${completedFixture.session.id}/complete`), 200);
  await waitForStatus(completedFixture.session.id, ["completed"], LONG_TIMEOUT_MS);

  const cancelledFixture = await createBookSession(t, {
    prompt: "list cancelled",
    cleanup: true
  });
  if (!cancelledFixture) {
    return;
  }
  await waitForStatus(cancelledFixture.session.id, ["idle"], LONG_TIMEOUT_MS);
  assertStatus(await apiPost(`/sessions/${cancelledFixture.session.id}/cancel`), 200);
  await waitForStatus(cancelledFixture.session.id, ["cancelled"], LONG_TIMEOUT_MS);

  const defaultList = assertStatus(await apiGet("/sessions"), 200);
  assert.ok(Array.isArray(defaultList.sessions));
  assert.equal(typeof defaultList.total, "number");
  assert.equal(typeof defaultList.limit, "number");
  assert.equal(typeof defaultList.offset, "number");

  const providerList = assertStatus(await apiGet("/sessions?provider=book"), 200);
  assert.ok(providerList.sessions.every((session) => session.provider === "book"));

  const completedList = assertStatus(await apiGet("/sessions?status=completed"), 200);
  assert.ok(completedList.sessions.every((session) => session.status === "completed"));
  assert.ok(completedList.sessions.some((session) => session.id === completedFixture.session.id));

  const firstPage = assertStatus(await apiGet("/sessions?limit=1&offset=0"), 200);
  assert.equal(firstPage.sessions.length, 1);

  const secondPage = assertStatus(await apiGet("/sessions?limit=1&offset=1"), 200);
  if (secondPage.sessions.length === 1) {
    assert.notEqual(firstPage.sessions[0].id, secondPage.sessions[0].id);
  } else {
    t.diagnostic("Only one session was available for pagination after filtering live data.");
  }
});

test("E2E-20: invalid session filters are rejected", async () => {
  const invalidProvider = await apiGet("/sessions?provider=invalid_provider");
  assertError(invalidProvider, 400, "invalid_request");

  const invalidStatus = await apiGet("/sessions?status=invalid_status");
  assertError(invalidStatus, 400, "invalid_request");
});
