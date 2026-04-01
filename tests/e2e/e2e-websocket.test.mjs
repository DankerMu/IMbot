import assert from "node:assert/strict";
import test from "node:test";

import {
  apiGet,
  apiPost,
  assertStatus,
  connectWs,
  createBookSession,
  LONG_TIMEOUT_MS,
  waitForStatus,
  waitForWsClose,
  waitForWsMessage
} from "./helpers.mjs";

test("E2E-29: WS clients can authenticate, subscribe, and receive live session events", { timeout: 180_000 }, async (t) => {
  const connection = await connectWs();
  t.after(() => {
    connection.ws.close();
  });
  connection.ws.send(JSON.stringify({ action: "auth", token: "already-authenticated-via-query" }));

  const fixture = await createBookSession(t, {
    prompt: "ws subscription",
    cleanup: true
  });
  if (!fixture) {
    return;
  }
  const { session } = fixture;

  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);
  const startIndex = connection.messages.length;
  connection.ws.send(JSON.stringify({ action: "subscribe", session_id: session.id }));
  connection.ws.send(JSON.stringify({ action: "ping" }));
  await waitForWsMessage(connection, (message) => message.type === "pong", {
    fromIndex: startIndex,
    label: "subscription ready pong"
  });

  assertStatus(await apiPost(`/sessions/${session.id}/message`, { text: "通过 WS 看这一轮" }), 200);

  const eventMessage = await waitForWsMessage(
    connection,
    (message) =>
      message.type === "event" &&
      message.session_id === session.id &&
      ["assistant_delta", "assistant_message", "user_message", "session_idle"].includes(message.event_type),
    { fromIndex: startIndex, label: "content-bearing session event" }
  );
  const statusMessage = await waitForWsMessage(
    connection,
    (message) => message.type === "status" && message.session_id === session.id,
    { fromIndex: startIndex, label: "session status update" }
  );
  await waitForWsMessage(
    connection,
    (message) => message.type === "event" && message.session_id === session.id && message.event_type === "session_idle",
    { fromIndex: startIndex, label: "session_idle event", timeoutMs: LONG_TIMEOUT_MS }
  );

  assert.equal(eventMessage.session_id, session.id);
  assert.equal(typeof eventMessage.seq, "number");
  assert.equal(typeof eventMessage.timestamp, "string");
  assert.equal(statusMessage.session_id, session.id);
  assert.ok(["running", "idle"].includes(statusMessage.status));

  const wsEvents = connection.messages
    .slice(startIndex)
    .filter((message) => message.type === "event" && message.session_id === session.id)
    .filter((message) => message.seq >= eventMessage.seq);
  const restEvents = assertStatus(await apiGet(`/sessions/${session.id}/events?since_seq=0`), 200).events;
  const expectedBySeq = new Map(restEvents.map((event) => [event.seq, event.type]));
  for (const wsEvent of wsEvents) {
    assert.equal(expectedBySeq.get(wsEvent.seq), wsEvent.event_type);
  }
});

test("E2E-30: WS subscriptions can receive events from multiple sessions", { timeout: 240_000 }, async (t) => {
  const connection = await connectWs();
  t.after(() => {
    connection.ws.close();
  });
  connection.ws.send(JSON.stringify({ action: "auth", token: "already-authenticated-via-query" }));

  const first = await createBookSession(t, {
    prompt: "只回复 first",
    cleanup: true
  });
  if (!first) {
    return;
  }
  await waitForStatus(first.session.id, ["idle"], LONG_TIMEOUT_MS);

  const second = await createBookSession(t, {
    prompt: "只回复 second",
    cleanup: true
  });
  if (!second) {
    return;
  }
  await waitForStatus(second.session.id, ["idle"], LONG_TIMEOUT_MS);

  const startIndex = connection.messages.length;
  connection.ws.send(JSON.stringify({ action: "subscribe", session_id: first.session.id }));
  connection.ws.send(JSON.stringify({ action: "subscribe", session_id: second.session.id }));
  connection.ws.send(JSON.stringify({ action: "ping" }));
  await waitForWsMessage(connection, (message) => message.type === "pong", {
    fromIndex: startIndex,
    label: "multi-session subscription ready pong"
  });

  assertStatus(await apiPost(`/sessions/${first.session.id}/message`, { text: "session a" }), 200);

  await waitForWsMessage(
    connection,
    (message) =>
      message.type === "event" &&
      message.session_id === first.session.id &&
      message.event_type === "user_message",
    { fromIndex: startIndex, label: "first session user_message", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForStatus(first.session.id, ["idle"], LONG_TIMEOUT_MS);

  assertStatus(await apiPost(`/sessions/${second.session.id}/message`, { text: "session b" }), 200);
  await waitForWsMessage(
    connection,
    (message) =>
      message.type === "event" &&
      message.session_id === second.session.id &&
      message.event_type === "user_message",
    { fromIndex: startIndex, label: "second session user_message", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForStatus(second.session.id, ["idle"], LONG_TIMEOUT_MS);

  const seenSessionIds = new Set(
    connection.messages
      .slice(startIndex)
      .filter((message) => message.type === "event")
      .map((message) => message.session_id)
  );
  assert.ok(seenSessionIds.has(first.session.id));
  assert.ok(seenSessionIds.has(second.session.id));
});

test("E2E-31: authenticated WS clients receive pong for ping", async (t) => {
  const connection = await connectWs();
  t.after(() => {
    connection.ws.close();
  });

  connection.ws.send(JSON.stringify({ action: "ping" }));
  const pong = await waitForWsMessage(connection, (message) => message.type === "pong", {
    label: "pong"
  });
  assert.deepEqual(pong, { type: "pong" });
});

test("E2E-32: WS invalid tokens receive an unauthenticated error and the socket closes", async (t) => {
  const connection = await connectWs({
    includeQueryToken: false
  });
  t.after(() => {
    connection.ws.close();
  });

  connection.ws.send(JSON.stringify({ action: "auth", token: "invalid" }));
  const errorMessage = await waitForWsMessage(
    connection,
    (message) => message.type === "error" && message.code === "unauthenticated",
    { label: "unauthenticated error" }
  );
  const close = await waitForWsClose(connection);

  assert.equal(errorMessage.code, "unauthenticated");
  assert.equal(close.code, 4001);
});
