import assert from "node:assert/strict";
import {
  chmodSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  realpathSync,
  rmSync,
  utimesSync,
  writeFileSync
} from "node:fs";
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

function waitForPathExists(targetPath, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;

    const poll = () => {
      if (existsSync(targetPath)) {
        resolve();
        return;
      }

      if (Date.now() >= deadline) {
        reject(new Error(`Timed out waiting for ${targetPath} to exist`));
        return;
      }

      setTimeout(poll, 25);
    };

    poll();
  });
}

function createPersistentMockCliBinary(tempDir) {
  const scriptPath = path.join(tempDir, "mock-persistent-cli.js");
  writeFileSync(
    scriptPath,
    `#!/usr/bin/env node
const fs = require("node:fs");
const path = require("node:path");

const args = process.argv.slice(2);
const resumeIndex = args.indexOf("-r");
const providerSessionId =
  process.env.MOCK_CLI_PROVIDER_SESSION_ID ||
  (resumeIndex >= 0 ? args[resumeIndex + 1] : "provider-session-1");
const resultDelayMs = Number(process.env.MOCK_CLI_RESULT_DELAY_MS || "30");
let stdinBuffer = "";
let turn = 0;

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

emit({ type: "system", subtype: "init", session_id: providerSessionId });

const projectsDir = process.env.MOCK_CLI_PROJECTS_DIR;
if (projectsDir) {
  const encodedCwd = process.cwd().replace(/^\\/+/, "").replace(/\\//g, "-");
  const projectDir = path.join(projectsDir, encodedCwd);
  fs.mkdirSync(projectDir, { recursive: true });
  fs.writeFileSync(
    path.join(projectDir, providerSessionId + ".jsonl"),
    JSON.stringify({ type: "system", session_id: providerSessionId }) + "\\n",
    "utf8"
  );
}

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

    const prompt = extractText(message?.message?.content);
    turn += 1;
    const currentTurn = turn;

    emit({
      type: "assistant",
      session_id: providerSessionId,
      message: {
        role: "assistant",
        content: [{ type: "text", text: "turn:" + currentTurn + ":" + prompt }]
      }
    });

    setTimeout(() => {
      emit({ type: "result", result: "done:" + currentTurn + ":" + prompt });
    }, resultDelayMs);
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

function encodeProjectPath(projectPath) {
  return projectPath.replace(/^\/+/, "").replace(/\//g, "-");
}

function createSessionFile(projectsDir, cwd, sessionId, createdAt) {
  const projectDir = path.join(projectsDir, encodeProjectPath(cwd));
  mkdirSync(projectDir, { recursive: true });

  const sessionFile = path.join(projectDir, `${sessionId}.jsonl`);
  writeFileSync(sessionFile, `{"type":"system","session_id":"${sessionId}"}\n`, "utf8");

  if (createdAt) {
    const timestamp = new Date(createdAt);
    utimesSync(sessionFile, timestamp, timestamp);
  }

  return sessionFile;
}

async function closeServer(server) {
  await new Promise((resolve, reject) => {
    server.close((error) => {
      if (error) {
        reject(error);
        return;
      }

      resolve();
    });
  });
}

async function createConnectedRuntime(t, options = {}) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot_session_persistence_"));
  const projectsDir = path.join(tempDir, "projects");
  const sessionIndexPath = path.join(tempDir, "sessions.json");
  let runtime = null;
  let restoreEnv = null;
  let server = null;
  mkdirSync(projectsDir, { recursive: true });

  t.after(async () => {
    if (runtime) {
      await runtime.close();
    }

    if (restoreEnv) {
      restoreEnv();
    }

    if (server) {
      await closeServer(server);
    }

    rmSync(tempDir, { recursive: true, force: true });
  });

  if (typeof options.setup === "function") {
    await options.setup({
      tempDir,
      projectsDir,
      sessionIndexPath
    });
  }

  server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const envOverrides = {
    MOCK_CLI_RESULT_DELAY_MS: String(options.resultDelayMs ?? 20),
    ...(options.writeJsonlOnStartup === false ? {} : { MOCK_CLI_PROJECTS_DIR: projectsDir })
  };
  if (options.providerSessionId) {
    envOverrides.MOCK_CLI_PROVIDER_SESSION_ID = options.providerSessionId;
  }
  restoreEnv = setMockCliEnv(envOverrides);
  const binaryPath = createPersistentMockCliBinary(tempDir);

  await waitForListening(server);
  const port = server.address().port;
  runtime = await companion.createCompanionRuntime({
    config: {
      configPath: path.join(tempDir, "companion.json"),
      relayUrl: `ws://127.0.0.1:${port}`,
      token: "static-token",
      hostId: "macbook-1",
      providers: {
        claude: {
          binary: binaryPath,
          configDir: path.join(tempDir, ".claude"),
          projectsDir
        }
      },
      sessionIndexPath,
      idleTimeoutMs: 1800000
    },
    logger: silentLogger,
    heartbeatIntervalMs: 25,
    reconnectDelaysMs: [20, 20],
    killGraceMs: 50
  });

  const connectionPromise = waitForConnection(server);
  runtime.connect();
  const { socket, request } = await connectionPromise;
  assert.match(request.url, /token=static-token/);
  assert.match(request.url, /host_id=macbook-1/);
  await waitForJsonMessage(
    socket,
    (message) => message.type === "heartbeat" && message.host_id === "macbook-1",
    "initial heartbeat"
  );

  return {
    tempDir,
    projectsDir,
    sessionIndexPath,
    runtime,
    socket
  };
}

async function createSessionOverRelay(socket, options = {}) {
  const reqId = options.reqId ?? "req-create-1";
  const relaySessionId = options.relaySessionId ?? "relay-session-1";
  const cwd = options.cwd;

  const ackPromise = waitForJsonMessage(
    socket,
    (message) => message.type === "ack" && message.req_id === reqId,
    `${reqId} ack`
  );

  socket.send(
    JSON.stringify({
      cmd: "create_session",
      req_id: reqId,
      session_id: relaySessionId,
      provider: "claude",
      cwd,
      prompt: options.prompt ?? "hello",
      permission_mode: "bypassPermissions"
    })
  );

  return await ackPromise;
}

test(
  "companion-created sessions persist JSONL files through lifecycle",
  { timeout: 30000 },
  async (t) => {
    await t.test("create_session leaves a JSONL file in CLI projects directory", async (subtest) => {
      const fixture = await createConnectedRuntime(subtest, {
        providerSessionId: "provider-session-create"
      });
      const { tempDir, projectsDir, socket } = fixture;
      const sessionCwd = realpathSync(tempDir);
      const sessionFile = path.join(projectsDir, encodeProjectPath(sessionCwd), "provider-session-create.jsonl");

      const ack = await createSessionOverRelay(socket, {
        reqId: "req-create-file",
        relaySessionId: "relay-session-create",
        cwd: sessionCwd,
        prompt: "persist me"
      });
      assert.deepEqual(ack, {
        type: "ack",
        req_id: "req-create-file",
        status: "ok",
        data: {
          provider_session_id: "provider-session-create"
        }
      });

      await waitForPathExists(sessionFile);
      assert.equal(existsSync(sessionFile), true);

      const discovered = await companion.discoverSessions(sessionCwd, "claude", {
        claudeProjectsDir: projectsDir,
        logger: silentLogger
      });
      const discoveredSession = discovered.find(
        (session) => session.provider_session_id === "provider-session-create"
      );
      assert.ok(discoveredSession);
      assert.equal(discoveredSession.cwd, sessionCwd);
      assert.equal(discoveredSession.status, "completed");
    });

    await t.test("JSONL file persists after complete_session", async (subtest) => {
      const fixture = await createConnectedRuntime(subtest, {
        providerSessionId: "provider-session-complete"
      });
      const { tempDir, projectsDir, socket } = fixture;
      const sessionCwd = realpathSync(tempDir);
      const sessionFile = path.join(projectsDir, encodeProjectPath(sessionCwd), "provider-session-complete.jsonl");
      const sessionIdlePromise = waitForJsonMessage(
        socket,
        (message) =>
          message.type === "event" &&
          message.session_id === "relay-session-complete" &&
          message.event_type === "session_idle",
        "session idle before complete"
      );

      await createSessionOverRelay(socket, {
        reqId: "req-create-complete",
        relaySessionId: "relay-session-complete",
        cwd: sessionCwd,
        prompt: "complete me"
      });
      await waitForPathExists(sessionFile);
      await sessionIdlePromise;

      const completeAckPromise = waitForJsonMessage(
        socket,
        (message) => message.type === "ack" && message.req_id === "req-complete-file",
        "complete_session ack"
      );
      const resultPromise = waitForJsonMessage(
        socket,
        (message) =>
          message.type === "event" &&
          message.session_id === "relay-session-complete" &&
          message.event_type === "session_result",
        "session result after complete"
      );

      socket.send(
        JSON.stringify({
          cmd: "complete_session",
          req_id: "req-complete-file",
          session_id: "relay-session-complete"
        })
      );

      await completeAckPromise;
      await resultPromise;
      assert.equal(existsSync(sessionFile), true);
    });

    await t.test("JSONL file persists after cancel_session", async (subtest) => {
      const fixture = await createConnectedRuntime(subtest, {
        providerSessionId: "provider-session-cancel",
        resultDelayMs: 5000
      });
      const { tempDir, projectsDir, socket } = fixture;
      const sessionCwd = realpathSync(tempDir);
      const sessionFile = path.join(projectsDir, encodeProjectPath(sessionCwd), "provider-session-cancel.jsonl");
      const assistantDeltaPromise = waitForJsonMessage(
        socket,
        (message) =>
          message.type === "event" &&
          message.session_id === "relay-session-cancel" &&
          message.event_type === "assistant_delta",
        "assistant delta before cancel"
      );

      await createSessionOverRelay(socket, {
        reqId: "req-create-cancel",
        relaySessionId: "relay-session-cancel",
        cwd: sessionCwd,
        prompt: "cancel me"
      });
      await waitForPathExists(sessionFile);
      await assistantDeltaPromise;

      const cancelAckPromise = waitForJsonMessage(
        socket,
        (message) => message.type === "ack" && message.req_id === "req-cancel-file",
        "cancel_session ack"
      );
      const cancelledPromise = waitForJsonMessage(
        socket,
        (message) =>
          message.type === "event" &&
          message.session_id === "relay-session-cancel" &&
          message.event_type === "session_status_changed" &&
          message.payload?.status === "cancelled",
        "cancelled event"
      );

      socket.send(
        JSON.stringify({
          cmd: "cancel_session",
          req_id: "req-cancel-file",
          session_id: "relay-session-cancel"
        })
      );

      await cancelAckPromise;
      await cancelledPromise;
      assert.equal(existsSync(sessionFile), true);
    });

    await t.test("resumed session JSONL file still exists after resume exits", async (subtest) => {
      const createdAt = "2026-01-02T00:00:00.000Z";
      const fixture = await createConnectedRuntime(subtest, {
        writeJsonlOnStartup: false,
        setup: ({ tempDir, projectsDir, sessionIndexPath }) => {
          const sessionCwd = realpathSync(tempDir);
          createSessionFile(projectsDir, sessionCwd, "provider-session-resume", createdAt);
          writeFileSync(
            sessionIndexPath,
            `${JSON.stringify(
              {
                "relay-session-resume": {
                  provider_session_id: "provider-session-resume",
                  cwd: sessionCwd,
                  provider: "claude",
                  created_at: createdAt,
                  source: "remote",
                  initial_prompt: "resume me"
                }
              },
              null,
              2
            )}\n`,
            "utf8"
          );
        }
      });
      const { tempDir, projectsDir, socket } = fixture;
      const sessionCwd = realpathSync(tempDir);
      const sessionFile = path.join(projectsDir, encodeProjectPath(sessionCwd), "provider-session-resume.jsonl");

      const resumeAckPromise = waitForJsonMessage(
        socket,
        (message) => message.type === "ack" && message.req_id === "req-resume-file",
        "resume_session ack"
      );
      const idlePromise = waitForJsonMessage(
        socket,
        (message) =>
          message.type === "event" &&
          message.session_id === "relay-session-resume" &&
          message.event_type === "session_idle",
        "session idle after resume"
      );

      socket.send(
        JSON.stringify({
          cmd: "resume_session",
          req_id: "req-resume-file",
          session_id: "relay-session-resume",
          provider_session_id: "provider-session-resume",
          cwd: sessionCwd
        })
      );

      await resumeAckPromise;
      await idlePromise;

      const completeAckPromise = waitForJsonMessage(
        socket,
        (message) => message.type === "ack" && message.req_id === "req-complete-resume",
        "complete_session ack after resume"
      );
      const resultPromise = waitForJsonMessage(
        socket,
        (message) =>
          message.type === "event" &&
          message.session_id === "relay-session-resume" &&
          message.event_type === "session_result",
        "session result after resumed complete"
      );

      socket.send(
        JSON.stringify({
          cmd: "complete_session",
          req_id: "req-complete-resume",
          session_id: "relay-session-resume"
        })
      );

      await completeAckPromise;
      await resultPromise;
      assert.equal(existsSync(sessionFile), true);
    });
  }
);
