import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import { mkdirSync, mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import { PassThrough } from "node:stream";
import { test } from "node:test";

const require = createRequire(import.meta.url);
const companion = require("../../packages/companion/dist/index.js");

const silentLogger = {
  debug() {},
  info() {},
  warn() {},
  error() {}
};

class MockChildProcess extends EventEmitter {
  constructor() {
    super();
    this.stdin = new PassThrough();
    this.stdout = new PassThrough();
    this.stderr = new PassThrough();
    this.exitCode = null;
    this.stdinBuffer = "";
    this.stdin.setEncoding("utf8");
    this.stdin.on("data", (chunk) => {
      this.stdinBuffer += String(chunk);
    });
  }

  emitJson(message) {
    this.stdout.write(`${JSON.stringify(message)}\n`);
  }

  getWrittenMessages() {
    return this.stdinBuffer
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => JSON.parse(line));
  }

  kill(signal = "SIGTERM") {
    queueMicrotask(() => {
      if (signal === "SIGINT") {
        this.close(130, null);
        return;
      }

      if (signal === "SIGTERM") {
        this.close(0, null);
        return;
      }

      this.close(null, signal);
    });
    return true;
  }

  close(code = 0, signal = null) {
    this.exitCode = code;
    this.stdout.end();
    this.stderr.end();
    this.stdin.end();
    this.emit("close", code, signal);
  }
}

function createAdapterHarness(tempDir, harnessOptions = {}) {
  const sessionIndex = new companion.SessionIndex({
    filePath: path.join(tempDir, "sessions.json"),
    logger: silentLogger
  });
  const events = [];
  const children = [];
  const spawnCalls = [];

  const adapter = new companion.ClaudeRuntimeAdapter({
    providers: {
      claude: {
        binary: "claude"
      }
    },
    sessionIndex,
    logger: silentLogger,
    idleTimeoutMs: harnessOptions.idleTimeoutMs,
    sendEvent: (message) => {
      events.push(message);
    },
    spawn: (binary, args, spawnOptions) => {
      const child = new MockChildProcess();
      const childIndex = children.length + 1;
      children.push(child);
      spawnCalls.push({
        binary,
        args,
        cwd: spawnOptions.cwd
      });

      queueMicrotask(() => {
        if (harnessOptions.autoInit !== false) {
          child.emitJson({
            type: "system",
            subtype: "init",
            session_id: harnessOptions.providerSessionId ?? `provider-session-${childIndex}`
          });
        }
      });

      return child;
    }
  });

  return {
    adapter,
    children,
    events,
    spawnCalls
  };
}

function getSession(adapter, relaySessionId) {
  return adapter.activeByRelaySessionId.get(relaySessionId);
}

async function createSession(adapter, cwd, sessionId = "relay-control-1") {
  return await adapter.createSession({
    cmd: "create_session",
    req_id: `req-${sessionId}`,
    session_id: sessionId,
    provider: "claude",
    cwd,
    prompt: "hello",
    permission_mode: "bypassPermissions"
  });
}

async function flushRuntime() {
  await Promise.resolve();
  await Promise.resolve();
}

async function delay(ms) {
  await new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

test("control_request is NOT passed to eventMapper", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-map-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  const session = getSession(adapter, "relay-control-1");
  let mapCalls = 0;
  const originalMap = session.eventMapper.map.bind(session.eventMapper);
  session.eventMapper.map = (...args) => {
    mapCalls += 1;
    return originalMap(...args);
  };

  children[0].emitJson({
    type: "control_request",
    request_id: "req-tool-1",
    request: {
      subtype: "can_use_tool",
      tool_name: "Bash",
      input: {
        command: "ls"
      }
    }
  });
  await flushRuntime();

  assert.equal(mapCalls, 0);
});

test("non-interactive control_request auto-allowed", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-auto-allow-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-tool-2",
    request: {
      subtype: "can_use_tool",
      tool_name: "Read",
      input: {
        file_path: "/tmp/test.txt"
      }
    }
  });
  await flushRuntime();

  assert.deepEqual(children[0].getWrittenMessages().at(-1), {
    type: "control_response",
    response: {
      subtype: "success",
      request_id: "req-tool-2",
      response: {
        behavior: "allow",
        updatedInput: {
          file_path: "/tmp/test.txt"
        }
      }
    }
  });
});

test("AskUserQuestion control_request emits tool_call_started and blocks", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-ask-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children, events } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-1",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_1",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one", options: [{ label: "A" }, { label: "B" }] }]
      }
    }
  });
  await flushRuntime();

  assert.deepEqual(events.at(-1), {
    type: "event",
    session_id: "relay-control-1",
    event_type: "tool_call_started",
    payload: {
      call_id: "toolu_ask_1",
      tool: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one", options: [{ label: "A" }, { label: "B" }] }]
      }
    }
  });
  assert.equal(children[0].getWrittenMessages().length, 1);
  assert.equal(getSession(adapter, "relay-control-1").pendingControlResponse.requestId, "req-ask-1");
  assert.equal(getSession(adapter, "relay-control-1").pendingControlResponse.callId, "toolu_ask_1");
  assert.notEqual(getSession(adapter, "relay-control-1").pendingControlTimer, null);
});

test("tool_call_started uses tool_use_id not request_id", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-call-id-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children, events } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-call-id",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_call_id_1",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one" }]
      }
    }
  });
  await flushRuntime();

  assert.equal(events.at(-1).payload.call_id, "toolu_call_id_1");
});

test("answerInteractiveTool resolves pending and writes control_response", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-answer-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-2",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_2",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one", options: [{ label: "Alpha" }, { label: "Beta" }] }]
      }
    }
  });
  await flushRuntime();

  adapter.answerInteractiveTool("relay-control-1", "toolu_ask_2", "Alpha");

  assert.equal(getSession(adapter, "relay-control-1").pendingControlResponse, null);
  assert.equal(getSession(adapter, "relay-control-1").pendingControlTimer, null);
  assert.deepEqual(children[0].getWrittenMessages().at(-1), {
    type: "control_response",
    response: {
      subtype: "success",
      request_id: "req-ask-2",
      response: {
        behavior: "allow",
        updatedInput: {
          questions: [{ question: "Pick one", options: [{ label: "Alpha" }, { label: "Beta" }] }],
          answers: {
            "0": "Alpha"
          }
        }
      }
    }
  });
});

test("answerInteractiveTool with mismatched callId throws", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-mismatch-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-3",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_3",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one" }]
      }
    }
  });
  await flushRuntime();

  assert.throws(() => {
    adapter.answerInteractiveTool("relay-control-1", "wrong-id", "A");
  }, {
    code: "call_id_mismatch"
  });
});

test("answerInteractiveTool with no pending throws", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-no-pending-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);

  assert.throws(() => {
    adapter.answerInteractiveTool("relay-control-1", "req-ask-4", "A");
  }, {
    code: "no_pending_control_request"
  });
});

test("cancel clears pending control response", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-cancel-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-5",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_5",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one" }]
      }
    }
  });
  await flushRuntime();

  await adapter.cancel("relay-control-1");

  assert.equal(getSession(adapter, "relay-control-1"), undefined);
  assert.deepEqual(children[0].getWrittenMessages().at(-1), {
    type: "control_response",
    response: {
      subtype: "error",
      request_id: "req-ask-5",
      error: "Interactive tool was interrupted by session cancellation"
    }
  });
});

test("control_cancel_request rejects pending", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-cancel-request-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children, events } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-6",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_6",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one" }]
      }
    }
  });
  await flushRuntime();

  children[0].emitJson({
    type: "control_cancel_request",
    request_id: "req-ask-6"
  });
  await flushRuntime();

  assert.equal(getSession(adapter, "relay-control-1").pendingControlResponse, null);
  assert.equal(children[0].getWrittenMessages().length, 1);
  assert.deepEqual(events.at(-1), {
    type: "event",
    session_id: "relay-control-1",
    event_type: "tool_call_completed",
    payload: {
      call_id: "toolu_ask_6",
      tool: "AskUserQuestion",
      result: null,
      cancelled: true
    }
  });
});

test("process exit cleans up pending", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-process-exit-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-7",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_7",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one" }]
      }
    }
  });
  await flushRuntime();

  children[0].close(0, null);
  await flushRuntime();

  assert.equal(children[0].getWrittenMessages().length, 1);
  assert.equal(getSession(adapter, "relay-control-1"), undefined);
});

test("AskUserQuestion times out after idleTimeoutMs", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-timeout-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children, events } = createAdapterHarness(tempDir, {
    idleTimeoutMs: 50
  });

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-timeout",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_timeout",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one" }]
      }
    }
  });
  await flushRuntime();
  await delay(80);
  await flushRuntime();

  assert.equal(getSession(adapter, "relay-control-1").pendingControlResponse, null);
  assert.equal(getSession(adapter, "relay-control-1").pendingControlTimer, null);
  assert.deepEqual(children[0].getWrittenMessages().at(-1), {
    type: "control_response",
    response: {
      subtype: "error",
      request_id: "req-ask-timeout",
      error: "Interactive tool answer timeout"
    }
  });
  assert.deepEqual(events.at(-1), {
    type: "event",
    session_id: "relay-control-1",
    event_type: "tool_call_completed",
    payload: {
      call_id: "toolu_ask_timeout",
      tool: "AskUserQuestion",
      result: null,
      cancelled: true
    }
  });
});

test("rejectAllPendingControlResponses clears all pending sessions", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-reject-all-"));
  const cwd1 = path.join(tempDir, "project-1");
  const cwd2 = path.join(tempDir, "project-2");
  mkdirSync(cwd1, { recursive: true });
  mkdirSync(cwd2, { recursive: true });
  const { adapter, children, events } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd1, "relay-control-1");
  await createSession(adapter, cwd2, "relay-control-2");
  children[0].emitJson({
    type: "control_request",
    request_id: "req-ask-a",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_a",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one" }]
      }
    }
  });
  children[1].emitJson({
    type: "control_request",
    request_id: "req-ask-b",
    request: {
      subtype: "can_use_tool",
      tool_use_id: "toolu_ask_b",
      tool_name: "AskUserQuestion",
      input: {
        questions: [{ question: "Pick one" }]
      }
    }
  });
  await flushRuntime();

  adapter.rejectAllPendingControlResponses("Relay disconnected");
  await flushRuntime();

  assert.equal(getSession(adapter, "relay-control-1").pendingControlResponse, null);
  assert.equal(getSession(adapter, "relay-control-2").pendingControlResponse, null);
  assert.equal(getSession(adapter, "relay-control-1").pendingControlTimer, null);
  assert.equal(getSession(adapter, "relay-control-2").pendingControlTimer, null);
  assert.deepEqual(children[0].getWrittenMessages().at(-1), {
    type: "control_response",
    response: {
      subtype: "error",
      request_id: "req-ask-a",
      error: "Relay disconnected"
    }
  });
  assert.deepEqual(children[1].getWrittenMessages().at(-1), {
    type: "control_response",
    response: {
      subtype: "error",
      request_id: "req-ask-b",
      error: "Relay disconnected"
    }
  });
  assert.deepEqual(
    events.filter((event) => event.event_type === "tool_call_completed").map((event) => event.payload),
    [
      {
        call_id: "toolu_ask_a",
        tool: "AskUserQuestion",
        result: null,
        cancelled: true
      },
      {
        call_id: "toolu_ask_b",
        tool: "AskUserQuestion",
        result: null,
        cancelled: true
      }
    ]
  );
});

test("writeControlMessage handles destroyed stdin gracefully", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-write-"));
  const cwd = path.join(tempDir, "project");
  mkdirSync(cwd, { recursive: true });
  const { adapter, children } = createAdapterHarness(tempDir);

  t.after(async () => {
    await adapter.shutdown().catch(() => {});
    rmSync(tempDir, { recursive: true, force: true });
  });

  await createSession(adapter, cwd);
  children[0].stdin.write = () => {
    throw new Error("stdin destroyed");
  };

  children[0].emitJson({
    type: "control_request",
    request_id: "req-write-1",
    request: {
      subtype: "can_use_tool",
      tool_name: "Read",
      input: {
        file_path: "/tmp/test.txt"
      }
    }
  });
  await flushRuntime();

  assert.equal(getSession(adapter, "relay-control-1").relaySessionId, "relay-control-1");
});
