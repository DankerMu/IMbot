import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, realpathSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");

function waitForOpen(ws, label) {
  return new Promise((resolve, reject) => {
    const cleanup = () => {
      ws.removeEventListener("open", onOpen);
      ws.removeEventListener("error", onError);
    };

    const onOpen = () => {
      cleanup();
      resolve();
    };

    const onError = (event) => {
      cleanup();
      reject(new Error(`${label} websocket failed to open: ${event.message ?? "unknown error"}`));
    };

    ws.addEventListener("open", onOpen, { once: true });
    ws.addEventListener("error", onError, { once: true });
  });
}

function waitForJsonMessage(ws, predicate, label, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`Timed out waiting for ${label}`));
    }, timeoutMs);

    const cleanup = () => {
      clearTimeout(timer);
      ws.removeEventListener("message", onMessage);
      ws.removeEventListener("close", onClose);
      ws.removeEventListener("error", onError);
    };

    const onMessage = (event) => {
      const message = JSON.parse(String(event.data));
      if (!predicate(message)) {
        return;
      }

      cleanup();
      resolve(message);
    };

    const onClose = () => {
      cleanup();
      reject(new Error(`WebSocket closed while waiting for ${label}`));
    };

    const onError = (event) => {
      cleanup();
      reject(new Error(`WebSocket error while waiting for ${label}: ${event.message ?? "unknown"}`));
    };

    ws.addEventListener("message", onMessage);
    ws.addEventListener("close", onClose, { once: true });
    ws.addEventListener("error", onError, { once: true });
  });
}

function sendHeartbeat(ws, hostId = "macbook-1", providers = ["claude", "book"]) {
  ws.send(
    JSON.stringify({
      type: "heartbeat",
      host_id: hostId,
      providers,
      uptime: 1
    })
  );
}

function authHeaders(token, extra = {}) {
  return {
    authorization: `Bearer ${token}`,
    ...extra
  };
}

test("relay workspace API manages hosts, roots, browse, and host status broadcasts", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-workspace-contract-"));
  const relayRoot = path.join(tempDir, "relay-root");
  const relayAlpha = path.join(relayRoot, "alpha");
  const relayBeta = path.join(relayRoot, "beta");
  const relayOutside = path.join(tempDir, "relay-outside");
  const relayEscapeLink = path.join(relayRoot, "escape-link");
  mkdirSync(relayRoot);
  mkdirSync(relayAlpha);
  mkdirSync(relayBeta);
  mkdirSync(relayOutside);
  symlinkSync(relayOutside, relayEscapeLink);
  writeFileSync(path.join(relayRoot, "README.md"), "file");
  const canonicalRelayRoot = realpathSync(relayRoot);
  const canonicalRelayAlpha = realpathSync(relayAlpha);
  const canonicalRelayBeta = realpathSync(relayBeta);

  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_HOST: "127.0.0.1",
    RELAY_LOG_LEVEL: "error",
    RELAY_COMPANION_TIMEOUT_MS: "2000",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1"
  });
  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  await runtime.app.listen({
    host: "127.0.0.1",
    port: 0
  });

  const address = runtime.app.server.address();
  const port = typeof address === "object" && address ? address.port : 0;
  const baseUrl = `http://127.0.0.1:${port}`;
  const baseWsUrl = `ws://127.0.0.1:${port}`;

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const initialHostsResponse = await fetch(`${baseUrl}/v1/hosts`, {
    headers: authHeaders(config.staticToken)
  });
  assert.equal(initialHostsResponse.status, 200);
  assert.deepEqual(await initialHostsResponse.json(), {
    hosts: [
      {
        id: "relay-local",
        name: "Relay VPS",
        type: "relay_local",
        status: "online",
        last_heartbeat_at: null,
        created_at: runtime.db.prepare("SELECT created_at FROM hosts WHERE id = 'relay-local'").get().created_at,
        updated_at: runtime.db.prepare("SELECT updated_at FROM hosts WHERE id = 'relay-local'").get().updated_at,
        providers: ["openclaw"]
      }
    ]
  });

  const addRelayRootResponse = await fetch(`${baseUrl}/v1/hosts/relay-local/roots`, {
    method: "POST",
    headers: authHeaders(config.staticToken, {
      "content-type": "application/json"
    }),
    body: JSON.stringify({
      provider: "openclaw",
      path: relayRoot
    })
  });
  const addRelayRootPayload = await addRelayRootResponse.json();
  assert.equal(addRelayRootResponse.status, 201);
  assert.equal(addRelayRootPayload.root.path, canonicalRelayRoot);
  assert.equal(addRelayRootPayload.root.label, "relay-root");

  const duplicateRelayRootResponse = await fetch(`${baseUrl}/v1/hosts/relay-local/roots`, {
    method: "POST",
    headers: authHeaders(config.staticToken, {
      "content-type": "application/json"
    }),
    body: JSON.stringify({
      provider: "openclaw",
      path: relayRoot
    })
  });
  assert.equal(duplicateRelayRootResponse.status, 409);
  assert.deepEqual(await duplicateRelayRootResponse.json(), {
    error: "state_conflict"
  });

  const relayRootsResponse = await fetch(`${baseUrl}/v1/hosts/relay-local/roots`, {
    headers: authHeaders(config.staticToken)
  });
  assert.equal(relayRootsResponse.status, 200);
  assert.equal((await relayRootsResponse.json()).roots.length, 1);

  const relayBrowseResponse = await fetch(
    `${baseUrl}/v1/hosts/relay-local/browse?path=${encodeURIComponent(relayRoot)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  assert.equal(relayBrowseResponse.status, 200);
  assert.deepEqual(await relayBrowseResponse.json(), {
    path: canonicalRelayRoot,
    directories: [
      {
        name: "alpha",
        path: canonicalRelayAlpha
      },
      {
        name: "beta",
        path: canonicalRelayBeta
      }
    ]
  });

  const relayOutsideResponse = await fetch(
    `${baseUrl}/v1/hosts/relay-local/browse?path=${encodeURIComponent(tempDir)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  assert.equal(relayOutsideResponse.status, 403);
  assert.deepEqual(await relayOutsideResponse.json(), {
    error: "forbidden"
  });

  const relayTraversalResponse = await fetch(
    `${baseUrl}/v1/hosts/relay-local/browse?path=${encodeURIComponent(`${relayRoot}/../escape`)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  assert.equal(relayTraversalResponse.status, 403);
  assert.deepEqual(await relayTraversalResponse.json(), {
    error: "forbidden"
  });

  const relaySymlinkEscapeResponse = await fetch(
    `${baseUrl}/v1/hosts/relay-local/browse?path=${encodeURIComponent(relayEscapeLink)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  assert.equal(relaySymlinkEscapeResponse.status, 403);
  assert.deepEqual(await relaySymlinkEscapeResponse.json(), {
    error: "forbidden"
  });

  const android = new WebSocket(`${baseWsUrl}/v1/ws?token=${config.staticToken}`);
  await waitForOpen(android, "android");

  const companion = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companion, "companion");

  const hostOnlinePromise = waitForJsonMessage(
    android,
    (message) =>
      message.type === "host_status" &&
      message.host_id === "macbook-1" &&
      message.status === "online",
    "host online"
  );
  sendHeartbeat(companion);
  await hostOnlinePromise;

  const hostsResponse = await fetch(`${baseUrl}/v1/hosts`, {
    headers: authHeaders(config.staticToken)
  });
  assert.equal(hostsResponse.status, 200);
  const hostsPayload = await hostsResponse.json();
  const macbookHost = hostsPayload.hosts.find((host) => host.id === "macbook-1");
  assert.equal(macbookHost.status, "online");
  assert.deepEqual(macbookHost.providers, ["claude", "book"]);
  assert.equal(typeof macbookHost.last_heartbeat_at, "string");

  const macbookRootPath = "/Users/example/Projects";
  const addMacbookRootPromise = fetch(`${baseUrl}/v1/hosts/macbook-1/roots`, {
    method: "POST",
    headers: authHeaders(config.staticToken, {
      "content-type": "application/json"
    }),
    body: JSON.stringify({
      provider: "claude",
      path: macbookRootPath
    })
  });

  const rootValidationCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "browse_directory" && message.path === macbookRootPath,
    "macbook root validation"
  );
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: rootValidationCommand.req_id,
      status: "ok",
      data: {
        path: macbookRootPath,
        directories: []
      }
    })
  );

  const addMacbookRootResponse = await addMacbookRootPromise;
  const addMacbookRootPayload = await addMacbookRootResponse.json();
  assert.equal(addMacbookRootResponse.status, 201);
  assert.equal(addMacbookRootPayload.root.provider, "claude");
  assert.equal(addMacbookRootPayload.root.path, macbookRootPath);

  const macbookBrowsePromise = fetch(
    `${baseUrl}/v1/hosts/macbook-1/browse?path=${encodeURIComponent(macbookRootPath)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  const macbookBrowseCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "browse_directory" && message.path === macbookRootPath,
    "macbook browse"
  );
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: macbookBrowseCommand.req_id,
      status: "ok",
      data: {
        path: macbookRootPath,
        directories: [
          {
            name: "IMbot",
            path: `${macbookRootPath}/IMbot`
          },
          {
            name: "notes",
            path: `${macbookRootPath}/notes`
          }
        ]
      }
    })
  );

  const macbookBrowseResponse = await macbookBrowsePromise;
  assert.equal(macbookBrowseResponse.status, 200);
  assert.deepEqual(await macbookBrowseResponse.json(), {
    path: macbookRootPath,
    directories: [
      {
        name: "IMbot",
        path: `${macbookRootPath}/IMbot`
      },
      {
        name: "notes",
        path: `${macbookRootPath}/notes`
      }
    ]
  });

  const macbookOutsideResponsePromise = fetch(
    `${baseUrl}/v1/hosts/macbook-1/browse?path=${encodeURIComponent("/etc")}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  const macbookOutsideCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "browse_directory" && message.path === "/etc",
    "macbook outside browse"
  );
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: macbookOutsideCommand.req_id,
      status: "ok",
      data: {
        path: "/etc",
        directories: [
          {
            name: "ssh",
            path: "/etc/ssh"
          }
        ]
      }
    })
  );
  const macbookOutsideResponse = await macbookOutsideResponsePromise;
  assert.equal(macbookOutsideResponse.status, 403);
  assert.deepEqual(await macbookOutsideResponse.json(), {
    error: "forbidden"
  });

  const macbookTraversalResponse = await fetch(
    `${baseUrl}/v1/hosts/macbook-1/browse?path=${encodeURIComponent(`${macbookRootPath}/../Secrets`)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  assert.equal(macbookTraversalResponse.status, 403);
  assert.deepEqual(await macbookTraversalResponse.json(), {
    error: "forbidden"
  });

  const macbookSymlinkBrowsePromise = fetch(
    `${baseUrl}/v1/hosts/macbook-1/browse?path=${encodeURIComponent(`${macbookRootPath}/escape-link`)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  const macbookSymlinkBrowseCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "browse_directory" && message.path === `${macbookRootPath}/escape-link`,
    "macbook symlink browse"
  );
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: macbookSymlinkBrowseCommand.req_id,
      status: "ok",
      data: {
        path: "/Users/example/Outside",
        directories: []
      }
    })
  );

  const macbookSymlinkBrowseResponse = await macbookSymlinkBrowsePromise;
  assert.equal(macbookSymlinkBrowseResponse.status, 403);
  assert.deepEqual(await macbookSymlinkBrowseResponse.json(), {
    error: "forbidden"
  });

  const hostOfflinePromise = waitForJsonMessage(
    android,
    (message) =>
      message.type === "host_status" &&
      message.host_id === "macbook-1" &&
      message.status === "offline",
    "host offline"
  );
  companion.close();
  await hostOfflinePromise;

  const offlineBrowseResponse = await fetch(
    `${baseUrl}/v1/hosts/macbook-1/browse?path=${encodeURIComponent(macbookRootPath)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  assert.equal(offlineBrowseResponse.status, 502);
  assert.deepEqual(await offlineBrowseResponse.json(), {
    error: "host_offline"
  });

  const removeRelayRootResponse = await fetch(
    `${baseUrl}/v1/hosts/relay-local/roots/${addRelayRootPayload.root.id}`,
    {
      method: "DELETE",
      headers: authHeaders(config.staticToken)
    }
  );
  assert.equal(removeRelayRootResponse.status, 204);

  const relayRootsAfterDelete = await fetch(`${baseUrl}/v1/hosts/relay-local/roots`, {
    headers: authHeaders(config.staticToken)
  });
  assert.deepEqual(await relayRootsAfterDelete.json(), {
    roots: []
  });

  android.close();
});
