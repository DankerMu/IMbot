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

test("RelayClient reconnects after disconnect and drops sends while disconnected", async (t) => {
  const warnings = [];
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
    backoffDelaysMs: [20, 20],
    logger: {
      warn: (message) => {
        warnings.push(String(message));
      }
    }
  });

  t.after(() => {
    client.close();
  });

  client.send({
    type: "heartbeat",
    host_id: "macbook-1",
    providers: ["claude"],
    uptime: 0
  });
  assert.equal(warnings.some((message) => message.includes("Dropping outbound heartbeat")), true);

  const firstConnectionPromise = waitForEvent(server, "connection");
  client.connect();
  const [firstSocket] = await firstConnectionPromise;

  const secondConnectionPromise = waitForEvent(server, "connection");
  firstSocket.close(1012, "restart");
  const [secondSocket] = await secondConnectionPromise;

  assert.ok(secondSocket);
});
