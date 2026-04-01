import assert from "node:assert/strict";
import test from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { formatEvent } = require("../../packages/viewer/dist/formatter.js");

test("formatEvent renders assistant_delta as plain text", () => {
  const result = formatEvent({
    event_type: "assistant_delta",
    payload: { text: "Hello world" }
  });
  assert.equal(result, "Hello world");
});

test("formatEvent renders assistant_message as plain text", () => {
  const result = formatEvent({
    event_type: "assistant_message",
    payload: { text: "Full response" }
  });
  assert.equal(result, "Full response");
});

test("formatEvent renders user_message with cyan prefix", () => {
  const result = formatEvent({
    event_type: "user_message",
    payload: { text: "What files?" }
  });
  assert.ok(result.includes("[user]"));
  assert.ok(result.includes("What files?"));
});

test("formatEvent renders tool_call_started with file_path", () => {
  const result = formatEvent({
    event_type: "tool_call_started",
    payload: { tool: "Edit", input: { file_path: "foo.ts" } }
  });
  assert.ok(result.includes("[tool]"));
  assert.ok(result.includes("Edit"));
  assert.ok(result.includes("foo.ts"));
});

test("formatEvent renders tool_call_started with command", () => {
  const result = formatEvent({
    event_type: "tool_call_started",
    payload: { tool: "Bash", input: { command: "npm test" } }
  });
  assert.ok(result.includes("Bash"));
  assert.ok(result.includes("npm test"));
});

test("formatEvent renders tool_call_completed", () => {
  const result = formatEvent({
    event_type: "tool_call_completed",
    payload: { tool: "Bash" }
  });
  assert.ok(result.includes("[done]"));
  assert.ok(result.includes("Bash"));
});

test("formatEvent renders session_status_changed with status", () => {
  const result = formatEvent({
    event_type: "session_status_changed",
    payload: { status: "running" }
  });
  assert.ok(result.includes("running"));
});

test("formatEvent renders session_idle", () => {
  const result = formatEvent({
    event_type: "session_idle",
    payload: {}
  });
  assert.ok(result.includes("idle"));
});

test("formatEvent renders session_started", () => {
  const result = formatEvent({
    event_type: "session_started",
    payload: {}
  });
  assert.ok(result.includes("started"));
});

test("formatEvent renders session_result as complete", () => {
  const result = formatEvent({
    event_type: "session_result",
    payload: {}
  });
  assert.ok(result.includes("complete"));
});

test("formatEvent renders session_error with message", () => {
  const result = formatEvent({
    event_type: "session_error",
    payload: { message: "Process crashed" }
  });
  assert.ok(result.includes("[error]"));
  assert.ok(result.includes("Process crashed"));
});

test("formatEvent returns null for unknown event types", () => {
  const result = formatEvent({
    event_type: "rate_limit_event",
    payload: {}
  });
  assert.equal(result, null);
});

test("formatEvent returns null for assistant_delta with no text", () => {
  const result = formatEvent({
    event_type: "assistant_delta",
    payload: {}
  });
  assert.equal(result, null);
});
