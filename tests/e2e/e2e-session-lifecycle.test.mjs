import assert from "node:assert/strict";
import test from "node:test";

import {
  apiDelete,
  apiGet,
  apiPost,
  assertContinuousSeq,
  assertError,
  assertOrderedEventTypes,
  assertStatus,
  createBookSession,
  getSession,
  LONG_TIMEOUT_MS,
  waitForStatus
} from "./helpers.mjs";

test("E2E-10 ~ E2E-14: book session create, message, complete, events, and resume", { timeout: 240_000 }, async (t) => {
  const fixture = await createBookSession(t, {
    prompt: "说一句话就好",
    cleanup: false
  });
  if (!fixture) {
    return;
  }
  const { response, session } = fixture;
  t.after(async () => {
    const latest = await getSession(session.id);
    if (latest.status === 200 && latest.body.status === "idle") {
      await apiPost(`/sessions/${session.id}/complete`);
      await waitForStatus(session.id, ["completed", "failed", "cancelled"], LONG_TIMEOUT_MS);
    }

    const current = await getSession(session.id);
    if (current.status === 200 && ["completed", "failed", "cancelled"].includes(current.body.status)) {
      await apiDelete(`/sessions/${session.id}`);
    }
  });

  await t.test("E2E-10: create a book session and wait until idle", async () => {
    assert.equal(response.body.session.provider, "book");
    assert.ok(["queued", "running"].includes(response.body.session.status));
    const idle = await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
    assert.equal(idle.status, "idle");
    assert.equal(idle.initial_prompt, "说一句话就好");
    assert.equal(typeof idle.provider_session_id, "string");
    assert.ok(idle.provider_session_id.length > 0);
  });

  await t.test("E2E-11: send a message from idle and return to idle", async () => {
    const messageResponse = await apiPost(`/sessions/${session.id}/message`, {
      text: "再说一句"
    });
    assert.deepEqual(assertStatus(messageResponse, 200), { ok: true });

    const intermediate = await getSession(session.id);
    assertStatus(intermediate, 200);
    assert.ok(["running", "idle"].includes(intermediate.body.status));

    const idle = await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
    assert.equal(idle.status, "idle");
  });

  await t.test("E2E-12: complete an idle session", async () => {
    const completeResponse = await apiPost(`/sessions/${session.id}/complete`);
    assertStatus(completeResponse, 200);

    const completed = await waitForStatus(session.id, ["completed"], LONG_TIMEOUT_MS);
    assert.equal(completed.status, "completed");
  });

  await t.test("E2E-13: completed session event stream is ordered and contiguous", async () => {
    const eventsResponse = await apiGet(`/sessions/${session.id}/events?since_seq=0`);
    const body = assertStatus(eventsResponse, 200);

    assert.ok(Array.isArray(body.events));
    assert.ok(body.events.length > 0);
    assertContinuousSeq(body.events);
    assertOrderedEventTypes(body.events, [
      "session_started",
      ["assistant_delta", "assistant_message"],
      "session_idle",
      "user_message",
      ["assistant_delta", "assistant_message"],
      "session_idle",
      "session_result"
    ]);

    for (const event of body.events) {
      assert.equal(typeof event.id, "string");
      assert.equal(event.session_id, session.id);
      assert.equal(typeof event.seq, "number");
      assert.equal(typeof event.type, "string");
      assert.equal(typeof event.created_at, "string");
      assert.ok("payload" in event);
    }
  });

  await t.test("E2E-14: completed sessions can be resumed back to idle", async () => {
    const completeBeforeResume = await getSession(session.id);
    assertStatus(completeBeforeResume, 200);
    assert.equal(completeBeforeResume.body.status, "completed");

    const resumeResponse = await apiPost(`/sessions/${session.id}/resume`);
    assertStatus(resumeResponse, 200);

    const idle = await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
    assert.equal(idle.status, "idle");

    const finalComplete = await apiPost(`/sessions/${session.id}/complete`);
    assertStatus(finalComplete, 200);
    const completed = await waitForStatus(session.id, ["completed"], LONG_TIMEOUT_MS);
    assert.equal(completed.status, "completed");
  });
});

test("E2E-15 ~ E2E-18: cancel, delete, and active-session delete protections", { timeout: 240_000 }, async (t) => {
  await t.test("E2E-15: running sessions can be cancelled", async (subtest) => {
    const fixture = await createBookSession(subtest, {
      prompt: "请写一段 500 字左右的故事，内容尽量详细一些。",
      cleanup: true
    });
    if (!fixture) {
      return;
    }
    const { session } = fixture;

    const runningOrIdle = await waitForStatus(session.id, ["running", "idle"], 30_000);
    const cancelResponse = await apiPost(`/sessions/${session.id}/cancel`);
    assertStatus(cancelResponse, 200);

    const terminal = await waitForStatus(session.id, ["cancelled", "completed", "failed"], LONG_TIMEOUT_MS);
    assert.ok(["cancelled", "completed", "failed"].includes(terminal.status));

    if (terminal.status === "cancelled") {
      const resumeResponse = await apiPost(`/sessions/${session.id}/resume`);
      assertError(resumeResponse, 409, "state_conflict");
    } else {
      subtest.diagnostic(`Session reached ${terminal.status} before cancel settled; observed status=${runningOrIdle.status}`);
    }
  });

  await t.test("E2E-16: idle sessions can be cancelled", async (subtest) => {
    const fixture = await createBookSession(subtest, {
      prompt: "hi",
      cleanup: true
    });
    if (!fixture) {
      return;
    }
    const { session } = fixture;

    await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
    const cancelResponse = await apiPost(`/sessions/${session.id}/cancel`);
    assertStatus(cancelResponse, 200);

    const cancelled = await waitForStatus(session.id, ["cancelled"], LONG_TIMEOUT_MS);
    assert.equal(cancelled.status, "cancelled");
  });

  await t.test("E2E-17: terminal sessions can be deleted", async (subtest) => {
    const fixture = await createBookSession(subtest, {
      prompt: "delete me",
      cleanup: false
    });
    if (!fixture) {
      return;
    }
    const { session } = fixture;
    subtest.after(async () => {
      const current = await getSession(session.id);
      if (current.status === 200 && ["completed", "failed", "cancelled"].includes(current.body.status)) {
        await apiDelete(`/sessions/${session.id}`);
      }
    });

    await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
    assertStatus(await apiPost(`/sessions/${session.id}/complete`), 200);
    await waitForStatus(session.id, ["completed"], LONG_TIMEOUT_MS);

    const deleteResponse = await apiDelete(`/sessions/${session.id}`);
    assert.equal(deleteResponse.status, 204);

    const getResponse = await getSession(session.id);
    assertError(getResponse, 404, "not_found");
  });

  await t.test("E2E-18: active sessions cannot be deleted", async (subtest) => {
    const fixture = await createBookSession(subtest, {
      prompt: "keep alive",
      cleanup: true
    });
    if (!fixture) {
      return;
    }
    const { session } = fixture;

    await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
    const deleteResponse = await apiDelete(`/sessions/${session.id}`);
    assertError(deleteResponse, 409, "state_conflict");
  });
});
