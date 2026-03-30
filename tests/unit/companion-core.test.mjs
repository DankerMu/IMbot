import assert from "node:assert/strict";
import { spawn as spawnChildProcess } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  utimesSync,
  writeFileSync
} from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const companion = require("../../packages/companion/dist/index.js");

const silentLogger = {
  debug() {},
  info() {},
  warn() {},
  error() {}
};

function delay(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function waitFor(condition, timeoutMs = 500) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (condition()) {
      return;
    }

    await delay(10);
  }

  throw new Error("Timed out waiting for condition");
}

function createRuntimeConfig(tempDir) {
  return {
    configPath: path.join(tempDir, "companion.json"),
    relayUrl: "ws://127.0.0.1:3010",
    token: "test-token",
    hostId: "macbook-1",
    providers: {
      claude: {
        binary: "claude"
      },
      book: {
        binary: "book"
      }
    },
    sessionIndexPath: path.join(tempDir, "sessions.json")
  };
}

function encodeProjectPath(projectPath) {
  return projectPath.replace(/^\/+/, "").replace(/\//g, "-");
}

function createDiscoveredSessionFile(cwd, sessionId, createdAt, contents = '{"ok":true}\n') {
  const projectDir = path.join(os.homedir(), ".claude", "projects", encodeProjectPath(cwd));
  mkdirSync(projectDir, { recursive: true });

  const sessionFile = path.join(projectDir, `${sessionId}.jsonl`);
  writeFileSync(sessionFile, contents, "utf8");

  const timestamp = new Date(createdAt);
  utimesSync(sessionFile, timestamp, timestamp);

  return {
    projectDir,
    sessionFile
  };
}

test("loadCompanionConfig reads env overrides and validates configured providers", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-"));
  const configPath = path.join(tempDir, "companion.json");
  const sessionIndexPath = path.join(tempDir, "sessions.json");

  writeFileSync(
    configPath,
    JSON.stringify(
      {
        relay_url: "ws://127.0.0.1:3010",
        token: "test-token",
        host_id: "macbook-1",
        providers: {
          claude: {
            binary: "/usr/local/bin/claude"
          }
        }
      },
      null,
      2
    )
  );

  try {
    const config = companion.loadCompanionConfig({
      COMPANION_CONFIG: configPath,
      COMPANION_SESSION_INDEX_PATH: sessionIndexPath
    });

    assert.equal(config.configPath, configPath);
    assert.equal(config.relayUrl, "ws://127.0.0.1:3010");
    assert.equal(config.token, "test-token");
    assert.equal(config.hostId, "macbook-1");
    assert.deepEqual(Object.keys(config.providers), ["claude"]);
    assert.equal(config.providers.claude.binary, "/usr/local/bin/claude");
    assert.equal(config.sessionIndexPath, sessionIndexPath);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

function createAdapterHarness(tempDir, isAllowedDirectory) {
  const sessionIndexPath = path.join(tempDir, "sessions.json");
  const sessionIndex = new companion.SessionIndex({
    filePath: sessionIndexPath,
    logger: silentLogger
  });
  const spawnCalls = [];
  const runtimeScript = [
    "process.stdout.write(JSON.stringify({ type: 'system', session_id: 'provider-session-test' }) + '\\n');",
    "process.stdout.write(JSON.stringify({ type: 'result', result: 'done' }) + '\\n');"
  ].join("");

  const adapter = new companion.ClaudeRuntimeAdapter({
    providers: {
      claude: {
        binary: "claude"
      },
      book: {
        binary: "book"
      }
    },
    sessionIndex,
    logger: silentLogger,
    sendEvent: () => {},
    isAllowedDirectory,
    spawn: (binary, args, options) => {
      spawnCalls.push({
        binary,
        args,
        cwd: options.cwd
      });

      return spawnChildProcess(process.execPath, ["-e", runtimeScript], {
        cwd: options.cwd,
        stdio: ["pipe", "pipe", "pipe"]
      });
    }
  });

  return {
    adapter,
    sessionIndex,
    spawnCalls
  };
}

test("ClaudeRuntimeAdapter allows create_session for book when cwd is under a configured root", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-adapter-book-allowed-"));
  const bookRoot = path.join(tempDir, "novel");
  const bookProject = path.join(bookRoot, "project-1");
  mkdirSync(bookProject, { recursive: true });

  try {
    const { adapter, spawnCalls } = createAdapterHarness(
      tempDir,
      (provider, cwd) => provider !== "book" || cwd.startsWith(bookRoot)
    );

    const result = await adapter.createSession({
      cmd: "create_session",
      req_id: "req-book-allowed",
      session_id: "relay-book-1",
      provider: "book",
      cwd: bookProject,
      prompt: "hello",
      permission_mode: "bypassPermissions"
    });

    assert.deepEqual(result, {
      provider_session_id: "provider-session-test"
    });
    assert.equal(spawnCalls.length, 1);
    assert.equal(spawnCalls[0].binary, "book");

    await adapter.shutdown();
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ClaudeRuntimeAdapter rejects create_session for book outside configured roots", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-adapter-book-forbidden-"));
  const bookRoot = path.join(tempDir, "novel");
  const otherProject = path.join(tempDir, "AI-vault");
  mkdirSync(bookRoot, { recursive: true });
  mkdirSync(otherProject, { recursive: true });

  try {
    const { adapter, spawnCalls } = createAdapterHarness(
      tempDir,
      (provider, cwd) => provider !== "book" || cwd.startsWith(bookRoot)
    );

    await assert.rejects(
      () =>
        adapter.createSession({
          cmd: "create_session",
          req_id: "req-book-forbidden",
          session_id: "relay-book-2",
          provider: "book",
          cwd: otherProject,
          prompt: "hello",
          permission_mode: "bypassPermissions"
        }),
      {
        code: "forbidden",
        message: `Directory ${otherProject} is not allowed for provider book`
      }
    );
    assert.equal(spawnCalls.length, 0);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ClaudeRuntimeAdapter keeps claude create_session unrestricted", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-adapter-claude-open-"));
  const claudeProject = path.join(tempDir, "AI-vault");
  mkdirSync(claudeProject, { recursive: true });

  try {
    const { adapter, spawnCalls } = createAdapterHarness(
      tempDir,
      (provider, cwd) => provider !== "book" || cwd.startsWith(path.join(tempDir, "novel"))
    );

    const result = await adapter.createSession({
      cmd: "create_session",
      req_id: "req-claude-open",
      session_id: "relay-claude-1",
      provider: "claude",
      cwd: claudeProject,
      prompt: "hello",
      permission_mode: "bypassPermissions"
    });

    assert.deepEqual(result, {
      provider_session_id: "provider-session-test"
    });
    assert.equal(spawnCalls.length, 1);
    assert.equal(spawnCalls[0].binary, "claude");

    await adapter.shutdown();
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ClaudeRuntimeAdapter rejects resume_session for book outside configured roots", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-adapter-book-resume-"));
  const bookRoot = path.join(tempDir, "novel");
  const validProject = path.join(bookRoot, "project-1");
  const invalidProject = path.join(tempDir, "AI-vault");
  mkdirSync(validProject, { recursive: true });
  mkdirSync(invalidProject, { recursive: true });

  try {
    const { adapter, sessionIndex, spawnCalls } = createAdapterHarness(
      tempDir,
      (provider, cwd) => provider !== "book" || cwd.startsWith(bookRoot)
    );

    sessionIndex.set("relay-book-resume", {
      provider_session_id: "provider-session-existing",
      cwd: validProject,
      provider: "book",
      created_at: "2026-03-30T00:00:00.000Z"
    });

    await assert.rejects(
      () =>
        adapter.resumeSession({
          cmd: "resume_session",
          req_id: "req-book-resume",
          session_id: "relay-book-resume",
          provider_session_id: "provider-session-existing",
          cwd: invalidProject
        }),
      {
        code: "forbidden",
        message: `Directory ${invalidProject} is not allowed for provider book`
      }
    );
    assert.equal(spawnCalls.length, 0);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("HeartbeatTimer emits immediate and scheduled heartbeats and stops cleanly", async () => {
  const sent = [];
  let uptime = 0;
  const timer = new companion.HeartbeatTimer({
    hostId: "macbook-1",
    providers: ["claude"],
    intervalMs: 20,
    getUptimeSeconds: () => uptime++,
    send: (message) => {
      sent.push(message);
    }
  });

  timer.start();
  await delay(55);
  timer.stop();

  const countAfterStop = sent.length;
  await delay(30);

  assert.equal(countAfterStop >= 2, true);
  assert.deepEqual(sent[0], {
    type: "heartbeat",
    host_id: "macbook-1",
    providers: ["claude"],
    uptime: 0
  });
  assert.equal(sent.length, countAfterStop);
});

test("CommandDispatcher handles success, malformed commands, unknown commands, and handler timeouts", async () => {
  const acknowledgements = [];
  const dispatcher = new companion.CommandDispatcher({
    logger: silentLogger,
    timeoutMs: 25,
    sendAck: (message) => {
      acknowledgements.push(message);
    }
  });

  dispatcher.register("create_session", async (command) => ({
    provider_session_id: `${command.session_id}-provider`
  }));
  dispatcher.register("send_message", async () => {
    await delay(50);
  });

  await dispatcher.dispatch({
    cmd: "create_session",
    req_id: "req-1",
    session_id: "relay-1",
    provider: "claude",
    cwd: "/tmp/project",
    prompt: "hello",
    permission_mode: "bypassPermissions"
  });
  await dispatcher.dispatch({
    req_id: "req-2"
  });
  await dispatcher.dispatch({
    cmd: "unknown_command",
    req_id: "req-3"
  });
  await dispatcher.dispatch({
    cmd: "send_message",
    req_id: "req-4",
    session_id: "relay-1",
    text: "slow"
  });

  assert.deepEqual(acknowledgements, [
    {
      type: "ack",
      req_id: "req-1",
      status: "ok",
      data: {
        provider_session_id: "relay-1-provider"
      }
    },
    {
      type: "ack",
      req_id: "req-2",
      status: "error",
      error_code: "invalid_request",
      message: "Missing cmd field"
    },
    {
      type: "ack",
      req_id: "req-3",
      status: "error",
      error_code: "unknown_command",
      message: "Unknown command: unknown_command"
    },
    {
      type: "ack",
      req_id: "req-4",
      status: "error",
      error_code: "handler_timeout",
      message: "Handler exceeded 0s timeout"
    }
  ]);
});

test("SessionIndex persists mappings and tolerates corrupt files", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-index-"));
  const filePath = path.join(tempDir, "sessions.json");

  try {
    const index = new companion.SessionIndex({
      filePath,
      logger: silentLogger
    });

    index.set("relay-1", {
      provider_session_id: "provider-1",
      cwd: "/tmp/project",
      provider: "claude",
      created_at: "2026-03-29T00:00:00.000Z"
    });

    assert.deepEqual(index.get("relay-1"), {
      provider_session_id: "provider-1",
      cwd: "/tmp/project",
      provider: "claude",
      created_at: "2026-03-29T00:00:00.000Z"
    });
    assert.equal(existsSync(`${filePath}.${process.pid}.tmp`), false);

    const reloaded = new companion.SessionIndex({
      filePath,
      logger: silentLogger
    });
    assert.deepEqual(reloaded.findByProviderSessionId("provider-1"), {
      relay_session_id: "relay-1",
      provider_session_id: "provider-1",
      cwd: "/tmp/project",
      provider: "claude",
      created_at: "2026-03-29T00:00:00.000Z"
    });

    writeFileSync(filePath, "{not-json");
    const corrupted = new companion.SessionIndex({
      filePath,
      logger: silentLogger
    });
    assert.equal(corrupted.get("relay-1"), null);
    assert.equal(readFileSync(filePath, "utf8"), "{not-json");
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("browseDirectory returns subdirectories only and rejects missing targets", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-browse-"));
  const rootDir = path.join(tempDir, "workspace");
  const nestedDir = path.join(rootDir, "nested");
  const emptyDir = path.join(rootDir, "empty");
  const linkedDir = path.join(tempDir, "workspace-link");
  const outsideDir = path.join(tempDir, "outside");
  const escapeLink = path.join(rootDir, "escape-link");

  try {
    mkdirSync(rootDir);
    mkdirSync(nestedDir);
    mkdirSync(emptyDir);
    mkdirSync(outsideDir);
    symlinkSync(rootDir, linkedDir);
    symlinkSync(outsideDir, escapeLink);
    writeFileSync(path.join(rootDir, "README.md"), "file");
    const canonicalRootDir = realpathSync(rootDir);
    const canonicalNestedDir = realpathSync(nestedDir);
    const canonicalEmptyDir = realpathSync(emptyDir);

    const result = await companion.browseDirectory(rootDir);
    assert.deepEqual(result, {
      path: canonicalRootDir,
      directories: [
        {
          name: "empty",
          path: canonicalEmptyDir
        },
        {
          name: "nested",
          path: canonicalNestedDir
        }
      ]
    });

    const linkedResult = await companion.browseDirectory(linkedDir);
    assert.deepEqual(linkedResult, {
      path: canonicalRootDir,
      directories: [
        {
          name: "empty",
          path: canonicalEmptyDir
        },
        {
          name: "nested",
          path: canonicalNestedDir
        }
      ]
    });

    await assert.rejects(() => companion.browseDirectory(path.join(tempDir, "missing")), {
      code: "not_found",
      message: `Directory ${path.join(tempDir, "missing")} not found`
    });

    await assert.rejects(
      () =>
        companion.browseDirectory(escapeLink, {
          allowedRoots: [rootDir]
        }),
      {
        code: "forbidden",
        message: `Directory ${escapeLink} is not under any workspace root`
      }
    );
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("CompanionRuntime list_sessions handler dispatches correctly", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-runtime-list-"));
  const cwd = path.join(tempDir, "workspace");
  const sentMessages = [];
  const { projectDir } = createDiscoveredSessionFile(cwd, "session-runtime", "2026-03-30T02:00:00.000Z");
  mkdirSync(cwd, { recursive: true });

  let runtime;
  try {
    runtime = await companion.createCompanionRuntime({
      config: createRuntimeConfig(tempDir),
      logger: silentLogger
    });
    runtime.relayClient.send = (message) => {
      sentMessages.push(message);
    };

    runtime.relayClient.emit("message", {
      cmd: "list_sessions",
      req_id: "req-list",
      cwd,
      provider: "claude"
    });

    await waitFor(() => sentMessages.length === 1);

    assert.equal(sentMessages[0].type, "ack");
    assert.equal(sentMessages[0].status, "ok");
    assert.equal(Array.isArray(sentMessages[0].data), true);
    assert.deepEqual(sentMessages[0].data, [
      {
        provider_session_id: "session-runtime",
        cwd,
        created_at: "2026-03-30T02:00:00.000Z",
        status: "completed"
      }
    ]);
  } finally {
    if (runtime) {
      await runtime.close();
    }

    rmSync(projectDir, { recursive: true, force: true });
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("CompanionRuntime add_root and remove_root handlers dispatch correctly", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-runtime-roots-"));
  const rootPath = path.join(tempDir, "novel");
  const sentMessages = [];
  mkdirSync(rootPath, { recursive: true });

  let runtime;
  try {
    const canonicalRootPath = realpathSync(rootPath);
    runtime = await companion.createCompanionRuntime({
      config: createRuntimeConfig(tempDir),
      logger: silentLogger
    });
    runtime.relayClient.send = (message) => {
      sentMessages.push(message);
    };

    runtime.relayClient.emit("message", {
      cmd: "add_root",
      req_id: "req-add-root",
      provider: "book",
      path: rootPath,
      label: "Novel"
    });

    await waitFor(() => sentMessages.length === 1);
    assert.equal(sentMessages[0].status, "ok");
    assert.deepEqual(runtime.configManager.getRoots("book"), [
      {
        provider: "book",
        path: canonicalRootPath,
        label: "Novel",
        added_at: runtime.configManager.getRoots("book")[0].added_at
      }
    ]);

    runtime.relayClient.emit("message", {
      cmd: "remove_root",
      req_id: "req-remove-root",
      provider: "book",
      path: rootPath
    });

    await waitFor(() => sentMessages.length === 2);
    assert.equal(sentMessages[1].status, "ok");
    assert.deepEqual(runtime.configManager.getRoots("book"), []);
  } finally {
    if (runtime) {
      await runtime.close();
    }

    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("CompanionRuntime reports running sessions after reconnect", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-runtime-reconnect-"));
  const sentMessages = [];

  let runtime;
  try {
    runtime = await companion.createCompanionRuntime({
      config: createRuntimeConfig(tempDir),
      logger: silentLogger
    });
    runtime.relayClient.send = (message) => {
      sentMessages.push(message);
    };
    runtime.adapter.getActiveSessionIds = () => ["relay-running-1", "relay-running-2"];

    runtime.relayClient.emit("connected");

    await waitFor(() => sentMessages.length === 3);
    assert.deepEqual(sentMessages[0], {
      type: "heartbeat",
      host_id: "macbook-1",
      providers: ["claude", "book"],
      uptime: sentMessages[0].uptime
    });
    assert.equal(typeof sentMessages[0].uptime, "number");
    assert.deepEqual(sentMessages.slice(1), [
      {
        type: "event",
        session_id: "relay-running-1",
        event_type: "session_status_changed",
        payload: {
          status: "running"
        }
      },
      {
        type: "event",
        session_id: "relay-running-2",
        event_type: "session_status_changed",
        payload: {
          status: "running"
        }
      }
    ]);
  } finally {
    if (runtime) {
      await runtime.close();
    }

    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("CompanionRuntime does not kill running CLI processes when the relay WS disconnects", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-runtime-survive-"));
  const cancelCalls = [];
  const shutdownCalls = [];

  let runtime;
  try {
    runtime = await companion.createCompanionRuntime({
      config: createRuntimeConfig(tempDir),
      logger: silentLogger
    });

    const originalCancel = runtime.adapter.cancel.bind(runtime.adapter);
    runtime.adapter.cancel = async (sessionId) => {
      cancelCalls.push(sessionId);
      return originalCancel(sessionId);
    };
    const originalShutdown = runtime.adapter.shutdown.bind(runtime.adapter);
    runtime.adapter.shutdown = async () => {
      shutdownCalls.push("shutdown");
      return originalShutdown();
    };
    runtime.adapter.getActiveSessionIds = () => ["relay-1", "relay-2"];

    runtime.relayClient.emit("disconnected", 1006, "abnormal");
    await delay(50);

    assert.deepEqual(cancelCalls, []);
    assert.deepEqual(shutdownCalls, []);
    assert.deepEqual(runtime.adapter.getActiveSessionIds(), ["relay-1", "relay-2"]);
  } finally {
    if (runtime) {
      await runtime.close();
    }

    rmSync(tempDir, { recursive: true, force: true });
  }
});
