import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { WebSocketServer } from "ws";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const { OpenClawBridge } = require("../../packages/relay/dist/openclaw/bridge.js");

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

async function waitFor(condition, timeoutMs, label) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (condition()) {
      return;
    }

    await delay(10);
  }

  throw new Error(`Timed out waiting for ${label}`);
}

async function createMockGateway() {
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });
  const requests = [];
  const sockets = new Set();

  server.on("connection", (socket) => {
    sockets.add(socket);
    socket.on("close", () => {
      sockets.delete(socket);
    });
    socket.on("message", (raw) => {
      try {
        requests.push({
          socket,
          frame: JSON.parse(raw.toString()),
          consumed: false
        });
      } catch {
        // ignore malformed frames in tests
      }
    });
  });

  await new Promise((resolve) => {
    server.once("listening", resolve);
  });

  const port = server.address().port;
  return {
    url: `ws://127.0.0.1:${port}`,
    async waitForRequest(predicate, label, timeoutMs = 1500) {
      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        const match = requests.find((entry) => !entry.consumed && predicate(entry.frame));
        if (match) {
          match.consumed = true;
          return match;
        }

        await delay(10);
      }

      throw new Error(`Timed out waiting for ${label}`);
    },
    replyOk(socket, requestId, payload) {
      socket.send(
        JSON.stringify({
          type: "res",
          id: requestId,
          ok: true,
          payload
        })
      );
    },
    closeClientSockets() {
      for (const socket of sockets) {
        socket.close();
      }
    },
    async close() {
      for (const socket of sockets) {
        socket.close();
      }

      await new Promise((resolve) => {
        server.close(resolve);
      });
    }
  };
}

test("relay reports openclaw offline and rejects openclaw session creation before inserting a session", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-openclaw-offline-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1",
    RELAY_COMPANION_TIMEOUT_MS: "500"
  });

  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const healthResponse = await runtime.app.inject({
    method: "GET",
    url: "/healthz"
  });
  assert.equal(healthResponse.statusCode, 200);
  assert.equal(healthResponse.json().openclaw, "offline");

  const createResponse = await runtime.app.inject({
    method: "POST",
    url: "/v1/sessions",
    headers: {
      authorization: `Bearer ${config.staticToken}`,
      "content-type": "application/json"
    },
    payload: {
      provider: "openclaw",
      host_id: "relay-local",
      cwd: "/srv/project",
      prompt: "hello openclaw"
    }
  });

  assert.equal(createResponse.statusCode, 502);
  assert.deepEqual(createResponse.json(), { error: "provider_unreachable" });

  const sessionCount = runtime.db.prepare("SELECT COUNT(*) AS count FROM sessions").get();
  assert.deepEqual(sessionCount, {
    count: 0
  });
});

test("OpenClawBridge uses backoff delays for reconnect instead of a fixed 30 second wait", async (t) => {
  const gateway = await createMockGateway();
  const bridge = new OpenClawBridge(
    {
      openClawUrl: gateway.url,
      openClawToken: "",
      companionTimeoutMs: 200
    },
    {
      hub: {
        broadcastHostStatus() {}
      },
      logger: silentLogger,
      onRelayEvent: async () => {}
    }
  );

  let nextDelayCalls = 0;
  bridge.backoff = {
    attempts: 0,
    nextDelay() {
      nextDelayCalls += 1;
      this.attempts += 1;
      return 25;
    },
    reset() {
      this.attempts = 0;
    }
  };

  t.after(async () => {
    bridge.shutdown();
    await gateway.close();
  });

  bridge.connect();
  const initialConnect = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "connect",
    "initial connect"
  );
  gateway.replyOk(initialConnect.socket, initialConnect.frame.id, {
    snapshot: {}
  });

  await waitFor(() => bridge.isAvailable(), 500, "bridge availability");

  const reconnectConnectPromise = gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "connect",
    "reconnect connect request"
  );

  gateway.closeClientSockets();
  await waitFor(() => nextDelayCalls === 1, 500, "backoff delay scheduling");

  const reconnectConnect = await reconnectConnectPromise;
  gateway.replyOk(reconnectConnect.socket, reconnectConnect.frame.id, {
    snapshot: {}
  });

  await waitFor(() => bridge.isAvailable(), 500, "bridge availability after reconnect");
  assert.equal(nextDelayCalls, 1);
});

test("OpenClawBridge resets backoff after each successful connect handshake", async (t) => {
  const gateway = await createMockGateway();
  const bridge = new OpenClawBridge(
    {
      openClawUrl: gateway.url,
      openClawToken: "",
      companionTimeoutMs: 200
    },
    {
      hub: {
        broadcastHostStatus() {}
      },
      logger: silentLogger,
      onRelayEvent: async () => {}
    }
  );

  let resetCalls = 0;
  bridge.backoff = {
    attempts: 0,
    nextDelay() {
      this.attempts += 1;
      return 25;
    },
    reset() {
      resetCalls += 1;
      this.attempts = 0;
    }
  };

  t.after(async () => {
    bridge.shutdown();
    await gateway.close();
  });

  bridge.connect();
  const firstConnect = await gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "connect",
    "first connect request"
  );
  gateway.replyOk(firstConnect.socket, firstConnect.frame.id, {
    snapshot: {
      sessionDefaults: {
        mainSessionKey: "oc-main"
      }
    }
  });

  await waitFor(() => bridge.isAvailable(), 500, "first bridge availability");
  assert.equal(resetCalls, 1);

  const secondConnectPromise = gateway.waitForRequest(
    (frame) => frame.type === "req" && frame.method === "connect",
    "second connect request"
  );

  gateway.closeClientSockets();

  const secondConnect = await secondConnectPromise;
  gateway.replyOk(secondConnect.socket, secondConnect.frame.id, {
    snapshot: {}
  });

  await waitFor(() => bridge.isAvailable(), 500, "second bridge availability");
  assert.equal(resetCalls, 2);
});

test("OpenClawBridge recovery log does not expose the gateway session key when a relay mapping is recovered", () => {
  const infoLogs = [];
  const bridge = new OpenClawBridge(
    {
      openClawUrl: "ws://127.0.0.1:1",
      openClawToken: "",
      companionTimeoutMs: 200
    },
    {
      hub: {
        broadcastHostStatus() {}
      },
      logger: {
        ...silentLogger,
        info(message) {
          infoLogs.push(String(message));
        }
      },
      onRelayEvent: async () => {}
    }
  );

  bridge.openClawToRelay.set("oc-secret-key", "relay-123");
  bridge.lastDisconnectedSessionCount = 1;
  bridge.logRecoveryStatus({
    snapshot: {
      sessionDefaults: {
        mainSessionKey: "oc-secret-key"
      }
    }
  });

  assert.deepEqual(infoLogs, ["OpenClaw bridge reconnected; gateway has an active session; matched relay session relay-123"]);
  assert.equal(infoLogs[0].includes("oc-secret-key"), false);
});

test("OpenClawBridge recovery log omits the gateway session key when no relay mapping can be recovered", () => {
  const infoLogs = [];
  const bridge = new OpenClawBridge(
    {
      openClawUrl: "ws://127.0.0.1:1",
      openClawToken: "",
      companionTimeoutMs: 200
    },
    {
      hub: {
        broadcastHostStatus() {}
      },
      logger: {
        ...silentLogger,
        info(message) {
          infoLogs.push(String(message));
        }
      },
      onRelayEvent: async () => {}
    }
  );

  bridge.lastDisconnectedSessionCount = 1;
  bridge.logRecoveryStatus({
    snapshot: {
      sessionDefaults: {
        mainSessionKey: "oc-secret-key"
      }
    }
  });

  assert.deepEqual(infoLogs, ["OpenClaw bridge reconnected; gateway has an active session; no relay mapping could be recovered"]);
  assert.equal(infoLogs[0].includes("oc-secret-key"), false);
});
