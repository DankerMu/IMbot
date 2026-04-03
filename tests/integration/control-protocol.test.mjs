import assert from "node:assert/strict";
import { chmodSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { WebSocketServer } from "ws";

const require = createRequire(import.meta.url);
const companion = require("../../packages/companion/dist/index.js");

const silentLogger = {
  debug() {},
  info() {},
  warn() {},
  error() {}
};

function waitForListening(server, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    if (server.address()) {
      resolve();
      return;
    }

    const timer = setTimeout(() => {
      cleanup();
      reject(new Error("Timed out waiting for relay test websocket server"));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      server.off("listening", onListening);
      server.off("error", onError);
    };

    const onListening = () => {
      cleanup();
      resolve();
    };

    const onError = (error) => {
      cleanup();
      reject(error);
    };

    server.once("listening", onListening);
    server.once("error", onError);
  });
}

function waitForConnection(server, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error("Timed out waiting for companion websocket connection"));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      server.off("connection", onConnection);
    };

    const onConnection = (socket, request) => {
      cleanup();
      resolve({ socket, request });
    };

    server.on("connection", onConnection);
  });
}

function waitForJsonMessage(socket, predicate, label, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out waiting for ${label}`));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      socket.off("message", onMessage);
      socket.off("close", onClose);
      socket.off("error", onError);
    };

    const onMessage = (raw) => {
      const message = JSON.parse(raw.toString());
      if (!predicate(message)) {
        return;
      }

      cleanup();
      resolve(message);
    };

    const onClose = () => {
      cleanup();
      reject(new Error(`Socket closed while waiting for ${label}`));
    };

    const onError = (error) => {
      cleanup();
      reject(error);
    };

    socket.on("message", onMessage);
    socket.once("close", onClose);
    socket.once("error", onError);
  });
}

function createControlProtocolMockCliBinary(tempDir) {
  const scriptPath = path.join(tempDir, "mock-control-cli.js");
  writeFileSync(
    scriptPath,
    `#!/usr/bin/env node
const args = process.argv.slice(2);
const resumeIndex = args.indexOf("-r");
const providerSessionId =
  process.env.MOCK_CLI_PROVIDER_SESSION_ID ||
  (resumeIndex >= 0 ? args[resumeIndex + 1] : "provider-session-ask-1");
const resultDelayMs = Number(process.env.MOCK_CLI_RESULT_DELAY_MS || "30");
const askRequest = {
  type: "control_request",
  request_id: "ctrl-req-1",
  request: {
    subtype: "can_use_tool",
    tool_use_id: "toolu_ask_integ_1",
    tool_name: "AskUserQuestion",
    input: {
      questions: [
        {
          question: "Pick a color",
          options: [{ label: "Red" }, { label: "Blue" }]
        }
      ]
    }
  }
};
let stdinBuffer = "";
let waitingForControlResponse = false;

function emit(message) {
  process.stdout.write(JSON.stringify(message) + "\\n");
}

function extractText(content) {
  if (typeof content === "string") {
    return content;
  }

  if (Array.isArray(content)) {
    return content
      .map((item) => (item && item.type === "text" && typeof item.text === "string" ? item.text : ""))
      .join("");
  }

  return "";
}

function extractAnswer(message) {
  const answers = message?.response?.response?.updatedInput?.answers;
  if (!answers || typeof answers !== "object") {
    return null;
  }

  if (typeof answers["0"] === "string") {
    return answers["0"];
  }

  for (const value of Object.values(answers)) {
    if (typeof value === "string") {
      return value;
    }
  }

  return null;
}

emit({ type: "system", subtype: "init", session_id: providerSessionId });

process.stdin.on("data", (chunk) => {
  stdinBuffer += chunk.toString();
  const lines = stdinBuffer.split(/\\r?\\n/);
  stdinBuffer = lines.pop() || "";

  for (const line of lines) {
    if (!line) {
      continue;
    }

    let message;
    try {
      message = JSON.parse(line);
    } catch {
      emit({ type: "error", error_code: "invalid_json", message: "Input must be JSON" });
      continue;
    }

    if (message?.type === "user") {
      const prompt = extractText(message?.message?.content);
      if (prompt.includes("trigger-ask")) {
        waitingForControlResponse = true;
        emit(askRequest);
        continue;
      }

      emit({
        type: "assistant",
        session_id: providerSessionId,
        message: {
          role: "assistant",
          content: [{ type: "text", text: "echo:" + prompt }]
        }
      });
      setTimeout(() => {
        emit({ type: "result", result: "done:" + prompt });
      }, resultDelayMs);
      continue;
    }

    if (message?.type === "control_response" && waitingForControlResponse) {
      waitingForControlResponse = false;
      if (message?.response?.subtype === "success") {
        const answer = extractAnswer(message) || "unknown";
        emit({
          type: "user",
          session_id: providerSessionId,
          message: {
            role: "user",
            content: [
              {
                type: "tool_result",
                tool_use_id: "toolu_ask_integ_1",
                tool_name: "AskUserQuestion",
                content: answer
              }
            ]
          }
        });
        emit({
          type: "assistant",
          session_id: providerSessionId,
          message: {
            role: "assistant",
            content: [{ type: "text", text: "received answer: " + answer }]
          }
        });
        setTimeout(() => {
          emit({ type: "result", result: "done:" + answer });
        }, resultDelayMs);
        continue;
      }

      emit({
        type: "error",
        error_code: "control_response_error",
        message: typeof message?.response?.error === "string" ? message.response.error : "Control response failed"
      });
    }
  }
});

process.on("SIGTERM", () => {
  process.exit(0);
});

process.on("SIGINT", () => {
  process.exit(130);
});
`,
    "utf8"
  );
  chmodSync(scriptPath, 0o755);
  return scriptPath;
}

function setMockCliEnv(overrides) {
  const previous = new Map();
  for (const [key, value] of Object.entries(overrides)) {
    previous.set(key, process.env[key]);
    process.env[key] = value;
  }

  return () => {
    for (const [key, value] of previous.entries()) {
      if (value == null) {
        delete process.env[key];
        continue;
      }

      process.env[key] = value;
    }
  };
}

test("companion completes AskUserQuestion control flow end to end", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-control-protocol-runtime-"));
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const restoreEnv = setMockCliEnv({
    MOCK_CLI_PROVIDER_SESSION_ID: "provider-session-ask-1",
    MOCK_CLI_RESULT_DELAY_MS: "20"
  });
  const binaryPath = createControlProtocolMockCliBinary(tempDir);

  t.after(async () => {
    restoreEnv();
    server.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  await waitForListening(server);
  const port = server.address().port;
  const runtime = await companion.createCompanionRuntime({
    config: {
      configPath: path.join(tempDir, "companion.json"),
      relayUrl: `ws://127.0.0.1:${port}`,
      token: "static-token",
      hostId: "macbook-1",
      providers: {
        claude: {
          binary: binaryPath
        }
      },
      sessionIndexPath: path.join(tempDir, "sessions.json"),
      idleTimeoutMs: 1800000
    },
    logger: silentLogger,
    heartbeatIntervalMs: 25,
    reconnectDelaysMs: [20, 20],
    killGraceMs: 50
  });

  t.after(async () => {
    await runtime.close();
  });

  const connectionPromise = waitForConnection(server);
  runtime.connect();
  const { socket, request } = await connectionPromise;

  assert.match(request.url, /token=static-token/);
  assert.match(request.url, /host_id=macbook-1/);

  const heartbeat = await waitForJsonMessage(
    socket,
    (message) => message.type === "heartbeat" && message.host_id === "macbook-1",
    "initial heartbeat"
  );
  assert.deepEqual(heartbeat.providers, ["claude"]);

  const createAckPromise = waitForJsonMessage(
    socket,
    (message) => message.type === "ack" && message.req_id === "req-create-ask-1",
    "create_session ack"
  );
  const toolCallStartedPromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-ask-1" &&
      message.event_type === "tool_call_started" &&
      message.payload?.call_id === "toolu_ask_integ_1",
    "AskUserQuestion tool_call_started"
  );

  socket.send(
    JSON.stringify({
      cmd: "create_session",
      req_id: "req-create-ask-1",
      session_id: "relay-session-ask-1",
      provider: "claude",
      cwd: tempDir,
      prompt: "trigger-ask",
      permission_mode: "bypassPermissions"
    })
  );

  const createAck = await createAckPromise;
  assert.deepEqual(createAck, {
    type: "ack",
    req_id: "req-create-ask-1",
    status: "ok",
    data: {
      provider_session_id: "provider-session-ask-1"
    }
  });

  const toolCallStarted = await toolCallStartedPromise;
  assert.deepEqual(toolCallStarted, {
    type: "event",
    session_id: "relay-session-ask-1",
    event_type: "tool_call_started",
    payload: {
      call_id: "toolu_ask_integ_1",
      tool: "AskUserQuestion",
      input: {
        questions: [
          {
            question: "Pick a color",
            options: [{ label: "Red" }, { label: "Blue" }]
          }
        ]
      }
    }
  });

  const answerAckPromise = waitForJsonMessage(
    socket,
    (message) => message.type === "ack" && message.req_id === "req-answer-ask-1",
    "answer_interactive_tool ack"
  );
  const toolCallCompletedPromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-ask-1" &&
      message.event_type === "tool_call_completed" &&
      message.payload?.call_id === "toolu_ask_integ_1",
    "AskUserQuestion tool_call_completed"
  );
  const assistantDeltaPromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-ask-1" &&
      message.event_type === "assistant_delta" &&
      message.payload?.text === "received answer: Red",
    "assistant delta confirming interactive answer"
  );
  const sessionIdlePromise = waitForJsonMessage(
    socket,
    (message) =>
      message.type === "event" &&
      message.session_id === "relay-session-ask-1" &&
      message.event_type === "session_idle" &&
      message.payload?.result === "done:Red",
    "session idle after AskUserQuestion answer"
  );

  socket.send(
    JSON.stringify({
      cmd: "answer_interactive_tool",
      req_id: "req-answer-ask-1",
      session_id: "relay-session-ask-1",
      call_id: "toolu_ask_integ_1",
      answer: "Red",
      question_index: 0
    })
  );

  const answerAck = await answerAckPromise;
  assert.deepEqual(answerAck, {
    type: "ack",
    req_id: "req-answer-ask-1",
    status: "ok"
  });

  const toolCallCompleted = await toolCallCompletedPromise;
  assert.deepEqual(toolCallCompleted.payload, {
    call_id: "toolu_ask_integ_1",
    tool: "AskUserQuestion",
    result: "Red"
  });

  const assistantDelta = await assistantDeltaPromise;
  assert.equal(assistantDelta.payload.text, "received answer: Red");

  const sessionIdle = await sessionIdlePromise;
  assert.deepEqual(sessionIdle.payload, {
    result: "done:Red"
  });
});
