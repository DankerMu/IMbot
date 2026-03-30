import assert from "node:assert/strict";
import { once } from "node:events";
import { createRequire } from "node:module";
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

function waitForEvent(emitter, eventName, timeoutMs = 2000) {
  return Promise.race([
    once(emitter, eventName),
    new Promise((_, reject) => {
      const handle = setTimeout(() => {
        reject(new Error(`Timed out waiting for ${eventName}`));
      }, timeoutMs);
      handle.unref?.();
    })
  ]);
}

async function waitForSocketJsonMessage(socket, label, timeoutMs = 2000) {
  const [raw] = await Promise.race([
    once(socket, "message"),
    new Promise((_, reject) => {
      const handle = setTimeout(() => {
        reject(new Error(`Timed out waiting for ${label}`));
      }, timeoutMs);
      handle.unref?.();
    })
  ]);

  return JSON.parse(raw.toString());
}

async function waitForSocketJsonMessages(socket, count, label, timeoutMs = 2000) {
  return await new Promise((resolve, reject) => {
    const messages = [];
    const handle = setTimeout(() => {
      socket.off("message", onMessage);
      reject(new Error(`Timed out waiting for ${label}`));
    }, timeoutMs);
    handle.unref?.();

    const onMessage = (raw) => {
      messages.push(JSON.parse(raw.toString()));
      if (messages.length < count) {
        return;
      }

      clearTimeout(handle);
      socket.off("message", onMessage);
      resolve(messages);
    };

    socket.on("message", onMessage);
  });
}

test("RelayClient connects with token auth query parameters and parses incoming JSON frames", async (t) => {
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });

  t.after(() => {
    server.close();
  });

  await waitForEvent(server, "listening");
  const port = server.address().port;
  const client = new companion.RelayClient({
    relayUrl: `ws://127.0.0.1:${port}`,
    token: "static-token",
    hostId: "macbook-1",
    logger: silentLogger
  });

  t.after(() => {
    client.close();
  });

  const connectionPromise = waitForEvent(server, "connection");
  const messagePromise = waitForEvent(client, "message");

  client.connect();

  const [socket, request] = await connectionPromise;
  assert.match(request.url, /token=static-token/);
  assert.match(request.url, /host_id=macbook-1/);

  socket.send(JSON.stringify({ cmd: "create_session", req_id: "req-1" }));
  const [message] = await messagePromise;

  assert.deepEqual(message, {
    cmd: "create_session",
    req_id: "req-1"
  });
});

test("EventBuffer pushes and flushes buffered messages", () => {
  const buffer = new companion.EventBuffer();
  const first = {
    type: "heartbeat",
    host_id: "macbook-1",
    providers: ["claude"],
    uptime: 10
  };
  const second = {
    type: "event",
    session_id: "relay-1",
    event_type: "assistant_delta",
    payload: {
      text: "hello"
    }
  };

  buffer.push(first);
  buffer.push(second);

  assert.equal(buffer.size, 2);
  assert.deepEqual(buffer.flush(), [first, second]);
  assert.equal(buffer.size, 0);
});

test("EventBuffer evicts the oldest message when it overflows", () => {
  const buffer = new companion.EventBuffer(2);
  const first = {
    type: "heartbeat",
    host_id: "macbook-1",
    providers: ["claude"],
    uptime: 10
  };
  const second = {
    type: "event",
    session_id: "relay-2",
    event_type: "assistant_delta",
    payload: {
      text: "second"
    }
  };
  const third = {
    type: "event",
    session_id: "relay-3",
    event_type: "assistant_delta",
    payload: {
      text: "third"
    }
  };

  buffer.push(first);
  buffer.push(second);
  buffer.push(third);

  assert.deepEqual(buffer.flush(), [second, third]);
});

test("EventBuffer warns when it evicts the oldest message on overflow", () => {
  const warnings = [];
  const buffer = new companion.EventBuffer(2, {
    warn: (message) => warnings.push(String(message))
  });

  buffer.push({
    type: "heartbeat",
    host_id: "macbook-1",
    providers: ["claude"],
    uptime: 10
  });
  buffer.push({
    type: "event",
    session_id: "relay-2",
    event_type: "assistant_delta",
    payload: {
      text: "second"
    }
  });
  buffer.push({
    type: "event",
    session_id: "relay-3",
    event_type: "assistant_delta",
    payload: {
      text: "third"
    }
  });

  assert.deepEqual(warnings, ["Event buffer overflow (max 2), dropping oldest message"]);
});

test("EventBuffer clear removes buffered messages", () => {
  const buffer = new companion.EventBuffer();
  buffer.push({
    type: "heartbeat",
    host_id: "macbook-1",
    providers: ["claude"],
    uptime: 10
  });

  buffer.clear();

  assert.equal(buffer.size, 0);
  assert.deepEqual(buffer.flush(), []);
});

test("RelayClient buffers outbound messages while disconnected instead of dropping them", async (t) => {
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });

  t.after(() => {
    server.close();
  });

  await waitForEvent(server, "listening");
  const port = server.address().port;
  const client = new companion.RelayClient({
    relayUrl: `ws://127.0.0.1:${port}`,
    token: "static-token",
    hostId: "macbook-1",
    backoff: {
      baseMs: 20,
      maxMs: 20,
      jitterMs: 0
    },
    logger: silentLogger
  });

  t.after(() => {
    client.close();
  });

  const bufferedMessage = {
    type: "heartbeat",
    host_id: "macbook-1",
    providers: ["claude"],
    uptime: 0
  };
  client.send(bufferedMessage);

  const receivedBufferedMessage = new Promise((resolve, reject) => {
    server.once("connection", (socket) => {
      void waitForSocketJsonMessage(socket, "buffered outbound message").then(resolve, reject);
    });
  });

  client.connect();
  const message = await receivedBufferedMessage;

  assert.deepEqual(message, bufferedMessage);
});

test("RelayClient flushes buffered messages on reconnect", async (t) => {
  const server = new WebSocketServer({
    port: 0,
    host: "127.0.0.1"
  });

  t.after(() => {
    server.close();
  });

  await waitForEvent(server, "listening");
  const port = server.address().port;
  const client = new companion.RelayClient({
    relayUrl: `ws://127.0.0.1:${port}`,
    token: "static-token",
    hostId: "macbook-1",
    backoff: {
      baseMs: 50,
      maxMs: 50,
      jitterMs: 0
    },
    logger: silentLogger
  });

  t.after(() => {
    client.close();
  });

  const firstConnectionPromise = waitForEvent(server, "connection");
  client.connect();
  const [firstSocket] = await firstConnectionPromise;

  const reconnectMessagesPromise = new Promise((resolve, reject) => {
    server.once("connection", (socket) => {
      void waitForSocketJsonMessages(socket, 2, "flushed buffered messages").then(resolve, reject);
    });
  });

  firstSocket.close(1012, "restart");
  await waitForEvent(client, "disconnected");

  const bufferedMessages = [
    {
      type: "event",
      session_id: "relay-1",
      event_type: "assistant_delta",
      payload: {
        text: "first"
      }
    },
    {
      type: "event",
      session_id: "relay-1",
      event_type: "assistant_message",
      payload: {
        text: "second"
      }
    }
  ];

  client.send(bufferedMessages[0]);
  client.send(bufferedMessages[1]);
  const flushedMessages = await reconnectMessagesPromise;

  assert.deepEqual(flushedMessages, bufferedMessages);
});

test("RelayClient reconnect uses exponential backoff delays instead of constant intervals", async (t) => {
  // Use a port with nothing listening so every connection attempt fails,
  // producing consecutive backoff delays that must escalate.
  const retryLogs = [];
  const logCapture = {
    ...silentLogger,
    info(message) {
      const match = String(message).match(/retrying relay connection attempt (\d+) in ([\d.]+)ms/);
      if (match) {
        retryLogs.push({ attempt: Number(match[1]), delayMs: Number(match[2]) });
      }
    }
  };

  const client = new companion.RelayClient({
    relayUrl: "ws://127.0.0.1:1",
    token: "static-token",
    hostId: "macbook-1",
    backoff: { baseMs: 20, maxMs: 320, jitterMs: 0 },
    logger: logCapture
  });

  client.on("error", () => {}); // suppress unhandled ECONNREFUSED

  t.after(() => {
    client.close();
  });

  client.connect();

  // Wait until at least 4 retry logs accumulate (20+40+80+160 = 300ms + overhead).
  // Do NOT unref timers — they must keep the event loop alive in CI.
  await new Promise((resolve) => {
    const check = setInterval(() => {
      if (retryLogs.length >= 4) {
        clearInterval(check);
        resolve();
      }
    }, 30);
    const bail = setTimeout(() => { clearInterval(check); resolve(); }, 3000);
    void bail;
  });

  assert.equal(retryLogs.length >= 4, true, `Expected at least 4 retry logs, got ${retryLogs.length}`);

  // Delays must be strictly increasing: 20, 40, 80, 160
  for (let i = 1; i < 4; i++) {
    assert.equal(
      retryLogs[i].delayMs > retryLogs[i - 1].delayMs,
      true,
      `Delay at attempt ${retryLogs[i].attempt} (${retryLogs[i].delayMs}ms) should exceed attempt ${retryLogs[i - 1].attempt} (${retryLogs[i - 1].delayMs}ms)`
    );
  }

  assert.equal(retryLogs[0].delayMs, 20, "First reconnect delay should equal baseMs");
  assert.equal(retryLogs[1].delayMs, 40, "Second delay should be 2x baseMs");
  assert.equal(retryLogs[2].delayMs, 80, "Third delay should be 4x baseMs");
  assert.equal(retryLogs[3].delayMs, 160, "Fourth delay should be 8x baseMs");
});
