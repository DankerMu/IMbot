import assert from "node:assert/strict";
import {
  chmodSync,
  mkdirSync,
  mkdtempSync,
  realpathSync,
  rmSync,
  symlinkSync,
  writeFileSync
} from "node:fs";
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

function assertNoJsonMessage(ws, predicate, label, timeoutMs = 250) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      resolve();
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
      reject(new Error(`Received unexpected ${label}`));
    };

    const onClose = () => {
      cleanup();
      reject(new Error(`WebSocket closed while confirming absence of ${label}`));
    };

    const onError = (event) => {
      cleanup();
      reject(new Error(`WebSocket error while confirming absence of ${label}: ${event.message ?? "unknown"}`));
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
  const relayUnreadable = path.join(tempDir, "relay-unreadable");
  mkdirSync(relayRoot);
  mkdirSync(relayAlpha);
  mkdirSync(relayBeta);
  mkdirSync(relayOutside);
  mkdirSync(relayUnreadable);
  symlinkSync(relayOutside, relayEscapeLink);
  writeFileSync(path.join(relayRoot, "README.md"), "file");
  const canonicalRelayRoot = realpathSync(relayRoot);
  const canonicalRelayAlpha = realpathSync(relayAlpha);
  const canonicalRelayBeta = realpathSync(relayBeta);
  chmodSync(relayUnreadable, 0o000);

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
    try {
      chmodSync(relayUnreadable, 0o755);
    } catch {}
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

  const unreadableRelayRootResponse = await fetch(`${baseUrl}/v1/hosts/relay-local/roots`, {
    method: "POST",
    headers: authHeaders(config.staticToken, {
      "content-type": "application/json"
    }),
    body: JSON.stringify({
      provider: "openclaw",
      path: relayUnreadable
    })
  });
  assert.equal(unreadableRelayRootResponse.status, 403);
  assert.deepEqual(await unreadableRelayRootResponse.json(), {
    error: "forbidden"
  });

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

  const addRootCommand = await waitForJsonMessage(
    companion,
    (message) =>
      message.cmd === "add_root" &&
      message.provider === "claude" &&
      message.path === macbookRootPath,
    "macbook add root sync"
  );
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: addRootCommand.req_id,
      status: "ok"
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
  assert.deepEqual(macbookBrowseCommand.roots, [macbookRootPath]);
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

  runtime.db
    .prepare(
      `
      INSERT INTO workspace_roots (id, host_id, provider, path, label, created_at)
      VALUES ('legacy-root', 'macbook-1', 'claude', ?, 'legacy-root', datetime('now'))
      `
    )
    .run("/var/tmp/IMbotLegacy");

  const legacyRootBrowsePath = "/var/tmp/IMbotLegacy";
  const canonicalLegacyRootPath = "/private/var/tmp/IMbotLegacy";
  const legacyChildPath = `${canonicalLegacyRootPath}/project-a`;
  const legacyRootBrowsePromise = fetch(
    `${baseUrl}/v1/hosts/macbook-1/browse?path=${encodeURIComponent(legacyRootBrowsePath)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  const legacyRootBrowseCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "browse_directory" && message.path === legacyRootBrowsePath,
    "macbook legacy root browse"
  );
  assert.equal(legacyRootBrowseCommand.roots.includes(legacyRootBrowsePath), true);
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: legacyRootBrowseCommand.req_id,
      status: "ok",
      data: {
        path: canonicalLegacyRootPath,
        directories: [
          {
            name: "project-a",
            path: legacyChildPath
          }
        ]
      }
    })
  );
  const legacyRootBrowseResponse = await legacyRootBrowsePromise;
  assert.equal(legacyRootBrowseResponse.status, 200);
  assert.deepEqual(await legacyRootBrowseResponse.json(), {
    path: canonicalLegacyRootPath,
    directories: [
      {
        name: "project-a",
        path: legacyChildPath
      }
    ]
  });
  assert.deepEqual(
    runtime.db.prepare("SELECT path FROM workspace_roots WHERE id = 'legacy-root'").get(),
    {
      path: canonicalLegacyRootPath
    }
  );

  const legacyChildBrowsePromise = fetch(
    `${baseUrl}/v1/hosts/macbook-1/browse?path=${encodeURIComponent(legacyChildPath)}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  const legacyChildBrowseCommand = await waitForJsonMessage(
    companion,
    (message) => message.cmd === "browse_directory" && message.path === legacyChildPath,
    "macbook canonical child browse"
  );
  assert.equal(legacyChildBrowseCommand.roots.includes(canonicalLegacyRootPath), true);
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: legacyChildBrowseCommand.req_id,
      status: "ok",
      data: {
        path: legacyChildPath,
        directories: []
      }
    })
  );
  const legacyChildBrowseResponse = await legacyChildBrowsePromise;
  assert.equal(legacyChildBrowseResponse.status, 200);
  assert.deepEqual(await legacyChildBrowseResponse.json(), {
    path: legacyChildPath,
    directories: []
  });

  const macbookOutsideResponsePromise = fetch(
    `${baseUrl}/v1/hosts/macbook-1/browse?path=${encodeURIComponent("/etc")}`,
    {
      headers: authHeaders(config.staticToken)
    }
  );
  const [macbookOutsideResponse] = await Promise.all([
    macbookOutsideResponsePromise,
    assertNoJsonMessage(
      companion,
      (message) => message.cmd === "browse_directory" && message.path === "/etc",
      "macbook outside browse command"
    )
  ]);
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
  assert.equal(macbookSymlinkBrowseCommand.roots.includes(macbookRootPath), true);
  assert.equal(macbookSymlinkBrowseCommand.roots.includes(canonicalLegacyRootPath), true);
  companion.send(
    JSON.stringify({
      type: "ack",
      req_id: macbookSymlinkBrowseCommand.req_id,
      status: "error",
      error_code: "forbidden",
      message: `Directory ${macbookRootPath}/escape-link is not under any workspace root`
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

  const removeMacbookRootResponse = await fetch(
    `${baseUrl}/v1/hosts/macbook-1/roots/${addMacbookRootPayload.root.id}`,
    {
      method: "DELETE",
      headers: authHeaders(config.staticToken)
    }
  );
  assert.equal(removeMacbookRootResponse.status, 502);
  assert.deepEqual(await removeMacbookRootResponse.json(), {
    error: "host_offline"
  });

  const macbookRootsAfterFailedDelete = await fetch(`${baseUrl}/v1/hosts/macbook-1/roots`, {
    headers: authHeaders(config.staticToken)
  });
  const macbookRootsPayload = await macbookRootsAfterFailedDelete.json();
  assert.equal(
    macbookRootsPayload.roots.some((root) => root.id === addMacbookRootPayload.root.id),
    true
  );

  const companionReconnect = new WebSocket(
    `${baseWsUrl}/v1/companion?token=${config.staticToken}&host_id=macbook-1`
  );
  await waitForOpen(companionReconnect, "companion reconnect");
  const hostOnlineAgainPromise = waitForJsonMessage(
    android,
    (message) =>
      message.type === "host_status" &&
      message.host_id === "macbook-1" &&
      message.status === "online",
    "host online again"
  );
  sendHeartbeat(companionReconnect);
  await hostOnlineAgainPromise;

  const removeMacbookRootPromise = fetch(
    `${baseUrl}/v1/hosts/macbook-1/roots/${addMacbookRootPayload.root.id}`,
    {
      method: "DELETE",
      headers: authHeaders(config.staticToken)
    }
  );
  const removeRootCommand = await waitForJsonMessage(
    companionReconnect,
    (message) =>
      message.cmd === "remove_root" &&
      message.provider === "claude" &&
      message.path === macbookRootPath,
    "macbook remove root sync"
  );
  companionReconnect.send(
    JSON.stringify({
      type: "ack",
      req_id: removeRootCommand.req_id,
      status: "ok"
    })
  );

  const removeMacbookRootSyncedResponse = await removeMacbookRootPromise;
  assert.equal(removeMacbookRootSyncedResponse.status, 204);

  const macbookRootsAfterDelete = await fetch(`${baseUrl}/v1/hosts/macbook-1/roots`, {
    headers: authHeaders(config.staticToken)
  });
  const macbookRootsAfterDeletePayload = await macbookRootsAfterDelete.json();
  assert.equal(
    macbookRootsAfterDeletePayload.roots.some((root) => root.id === addMacbookRootPayload.root.id),
    false
  );

  companionReconnect.close();

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
