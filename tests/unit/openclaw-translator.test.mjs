import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";

const require = createRequire(import.meta.url);
const {
  extractOpenClawSessionKey,
  translateOpenClawEvent
} = require("../../packages/relay/dist/openclaw/event-translator.js");

test("openclaw translator maps supported gateway events into relay event types", () => {
  assert.deepEqual(
    translateOpenClawEvent({
      event: "transcript.text",
      payload: {
        session_key: "oc-1",
        role: "agent",
        text: "hello",
        complete: false
      }
    }),
    {
      type: "assistant_delta",
      payload: {
        text: "hello"
      }
    }
  );

  assert.deepEqual(
    translateOpenClawEvent({
      event: "transcript.text",
      payload: {
        session_key: "oc-1",
        role: "agent",
        text: "hello world",
        complete: true
      }
    }),
    {
      type: "assistant_message",
      payload: {
        text: "hello world"
      }
    }
  );

  assert.deepEqual(
    translateOpenClawEvent({
      event: "tool.start",
      payload: {
        sessionKey: "oc-1",
        tool_name: "bash",
        tool_input: {
          cmd: "pwd"
        }
      }
    }),
    {
      type: "tool_call_started",
      payload: {
        name: "bash",
        input: {
          cmd: "pwd"
        }
      }
    }
  );

  assert.deepEqual(
    translateOpenClawEvent({
      event: "session.error",
      payload: {
        session_key: "oc-1",
        error_code: "provider_unreachable",
        message: "gateway down"
      }
    }),
    {
      type: "session_error",
      payload: {
        error_code: "provider_unreachable",
        message: "gateway down"
      }
    }
  );
});

test("openclaw translator extracts session keys and discards malformed or unknown events", () => {
  assert.equal(
    extractOpenClawSessionKey({
      session: {
        key: "oc-nested"
      }
    }),
    "oc-nested"
  );

  assert.equal(
    translateOpenClawEvent({
      event: "internal.debug",
      payload: {
        session_key: "oc-1"
      }
    }),
    null
  );

  assert.equal(
    translateOpenClawEvent({
      event: "transcript.text",
      payload: null
    }),
    null
  );
});
