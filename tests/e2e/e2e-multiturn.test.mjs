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
  waitForWsMessage
} from "./helpers.mjs";

test("E2E-33: three-turn persistent conversation stays ordered across WS and REST", { timeout: 300_000 }, async (t) => {
  const connection = await connectWs();
  t.after(() => {
    connection.ws.close();
  });
  connection.ws.send(JSON.stringify({ action: "auth", token: "already-authenticated-via-query" }));

  const fixture = await createBookSession(t, {
    prompt: "你好",
    cleanup: true
  });
  if (!fixture) {
    return;
  }
  const { session } = fixture;
  const startIndex = connection.messages.length;
  connection.ws.send(JSON.stringify({ action: "subscribe", session_id: session.id }));

  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  assertStatus(await apiPost(`/sessions/${session.id}/message`, { text: "第二轮" }), 200);
  await waitForWsMessage(
    connection,
    (message) =>
      message.type === "event" &&
      message.session_id === session.id &&
      message.event_type === "user_message" &&
      message.payload?.text === "第二轮",
    { fromIndex: startIndex, label: "second turn user_message", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForWsMessage(
    connection,
    (message) =>
      message.type === "event" &&
      message.session_id === session.id &&
      ["assistant_delta", "assistant_message"].includes(message.event_type),
    { fromIndex: startIndex, label: "second turn assistant response", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForWsMessage(
    connection,
    (message) => message.type === "event" && message.session_id === session.id && message.event_type === "session_idle",
    { fromIndex: startIndex, label: "second turn idle", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  assertStatus(await apiPost(`/sessions/${session.id}/message`, { text: "第三轮" }), 200);
  await waitForWsMessage(
    connection,
    (message) =>
      message.type === "event" &&
      message.session_id === session.id &&
      message.event_type === "user_message" &&
      message.payload?.text === "第三轮",
    { fromIndex: startIndex, label: "third turn user_message", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForWsMessage(
    connection,
    (message) =>
      message.type === "event" &&
      message.session_id === session.id &&
      ["assistant_delta", "assistant_message"].includes(message.event_type),
    { fromIndex: startIndex, label: "third turn assistant response", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForWsMessage(
    connection,
    (message) => message.type === "event" && message.session_id === session.id && message.event_type === "session_idle",
    { fromIndex: startIndex, label: "third turn idle", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  assertStatus(await apiPost(`/sessions/${session.id}/complete`), 200);
  await waitForWsMessage(
    connection,
    (message) => message.type === "event" && message.session_id === session.id && message.event_type === "session_result",
    { fromIndex: startIndex, label: "session_result", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForWsMessage(
    connection,
    (message) => message.type === "status" && message.session_id === session.id && message.status === "completed",
    { fromIndex: startIndex, label: "completed status", timeoutMs: LONG_TIMEOUT_MS }
  );
  await waitForStatus(session.id, ["completed"], LONG_TIMEOUT_MS);

  const wsEvents = connection.messages
    .slice(startIndex)
    .filter((message) => message.type === "event" && message.session_id === session.id);
  assert.ok(wsEvents.every((message) => message.session_id === session.id));
  for (let index = 1; index < wsEvents.length; index += 1) {
    assert.ok(wsEvents[index].seq > wsEvents[index - 1].seq);
  }
  assert.ok(
    wsEvents.filter((message) => ["assistant_delta", "assistant_message"].includes(message.event_type)).length >= 2
  );
  assert.ok(
    wsEvents.some((message) => message.event_type === "user_message" && message.payload?.text === "第二轮")
  );
  assert.ok(
    wsEvents.some((message) => message.event_type === "user_message" && message.payload?.text === "第三轮")
  );

  const restEvents = assertStatus(await apiGet(`/sessions/${session.id}/events?since_seq=0`), 200).events;
  const restBySeq = new Map(restEvents.map((event) => [event.seq, event.type]));
  for (const wsEvent of wsEvents) {
    assert.equal(restBySeq.get(wsEvent.seq), wsEvent.event_type);
  }
});

test.skip("E2E-34: idle timeout validation requires a shortened IDLE_TIMEOUT_MS companion configuration");
