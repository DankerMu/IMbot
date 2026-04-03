import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { test } from "node:test";

const require = createRequire(import.meta.url);
const { RuntimeEventMapper } = require("../../packages/companion/dist/runtime/event-mapper.js");

test("AskUserQuestion tool_result is NOT suppressed", () => {
  const mapper = new RuntimeEventMapper();

  mapper.map({
    type: "assistant",
    message: {
      role: "assistant",
      content: [
        {
          type: "tool_use",
          id: "toolu-ask-1",
          name: "AskUserQuestion",
          input: {
            questions: [{ question: "Pick one", options: [{ label: "A" }, { label: "B" }] }]
          }
        }
      ]
    }
  });

  const mapped = mapper.map({
    type: "user",
    message: {
      role: "user",
      content: [
        {
          type: "tool_result",
          tool_use_id: "toolu-ask-1",
          tool_name: "AskUserQuestion",
          content: "A"
        }
      ]
    }
  });

  assert.deepEqual(mapped, {
    kind: "event",
    eventType: "tool_call_completed",
    payload: {
      call_id: "toolu-ask-1",
      tool: "AskUserQuestion",
      result: "A"
    }
  });
});

test("assistant text after AskUserQuestion is NOT suppressed", () => {
  const mapper = new RuntimeEventMapper();

  mapper.map({
    type: "tool_use",
    id: "toolu-ask-2",
    tool: "AskUserQuestion",
    input: {
      questions: [{ question: "Pick one" }]
    }
  });

  const mapped = mapper.map({
    type: "assistant",
    message: {
      role: "assistant",
      content: [{ type: "text", text: "Thanks, continuing with your answer." }]
    }
  });

  assert.deepEqual(mapped, {
    kind: "event",
    eventType: "assistant_delta",
    payload: {
      text: "Thanks, continuing with your answer."
    }
  });
});

test("Skill user message is still suppressed", () => {
  const mapper = new RuntimeEventMapper();

  mapper.map({
    type: "assistant",
    message: {
      role: "assistant",
      content: [
        {
          type: "tool_use",
          id: "toolu-skill-1",
          name: "Skill",
          input: { skill: "novel:dashboard" }
        }
      ]
    }
  });

  const mapped = mapper.map({
    type: "user",
    message: {
      role: "user",
      content: [{ type: "text", text: "Expanded skill prompt" }]
    }
  });

  assert.equal(mapped, null);
});

test("tool dedup still works", () => {
  const mapper = new RuntimeEventMapper();

  const first = mapper.map({
    type: "tool_use",
    id: "toolu-dedup-1",
    tool: "Read",
    input: { file_path: "/tmp/a.txt" }
  });
  const second = mapper.map({
    type: "tool_use",
    id: "toolu-dedup-1",
    tool: "Read",
    input: { file_path: "/tmp/a.txt" }
  });

  assert.equal(first.kind, "event");
  assert.equal(first.eventType, "tool_call_started");
  assert.equal(second, null);
});
