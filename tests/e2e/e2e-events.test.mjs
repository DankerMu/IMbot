import assert from "node:assert/strict";
import test from "node:test";

import {
  apiGet,
  apiPost,
  assertContinuousSeq,
  assertError,
  assertStatus,
  createBookSession,
  LONG_TIMEOUT_MS,
  waitForStatus
} from "./helpers.mjs";

test("E2E-26 and E2E-27: event pagination and since_seq boundaries work", { timeout: 180_000 }, async (t) => {
  const fixture = await createBookSession(t, {
    prompt: "event pagination",
    cleanup: true
  });
  if (!fixture) {
    return;
  }
  const { session } = fixture;

  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
  assertStatus(await apiPost(`/sessions/${session.id}/message`, { text: "第二轮" }), 200);
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
  assertStatus(await apiPost(`/sessions/${session.id}/complete`), 200);
  await waitForStatus(session.id, ["completed"], LONG_TIMEOUT_MS);

  const allEvents = assertStatus(
    await apiGet(`/sessions/${session.id}/events?since_seq=0&limit=500`),
    200
  ).events;

  const pagedEvents = [];
  let sinceSeq = 0;
  let hasMore = true;
  while (hasMore) {
    const page = assertStatus(
      await apiGet(`/sessions/${session.id}/events?since_seq=${sinceSeq}&limit=2`),
      200
    );
    assert.ok(page.events.length <= 2);
    if (page.events.length > 0) {
      sinceSeq = page.events.at(-1).seq;
      pagedEvents.push(...page.events);
    }
    hasMore = page.has_more;
  }

  assert.deepEqual(
    pagedEvents.map((event) => ({ seq: event.seq, type: event.type })),
    allEvents.map((event) => ({ seq: event.seq, type: event.type }))
  );
  assertContinuousSeq(allEvents);

  const beyondRange = assertStatus(
    await apiGet(`/sessions/${session.id}/events?since_seq=999999`),
    200
  );
  assert.deepEqual(beyondRange.events, []);
  assert.equal(beyondRange.has_more, false);
});

test("E2E-28: unknown sessions return 404 for event catch-up", async () => {
  const response = await apiGet("/sessions/nonexistent-id/events?since_seq=0");
  assertError(response, 404, "not_found");
});
