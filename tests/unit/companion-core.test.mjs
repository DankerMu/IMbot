import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
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
