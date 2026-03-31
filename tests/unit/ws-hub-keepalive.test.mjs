import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import { mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { CompanionManager } from "../../packages/relay/dist/companion/manager.js";
import { initializeDatabase } from "../../packages/relay/dist/db/init.js";
import { WsHub } from "../../packages/relay/dist/ws/hub.js";

const WS_OPEN = 1;
const WS_CLOSED = 3;

function wait(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function createTestDatabase(prefix) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), prefix));
  const db = initializeDatabase(path.join(tempDir, "imbot.db"));

  const cleanup = () => {
    db.close();
    rmSync(tempDir, { recursive: true, force: true });
  };

  return {
    db,
    cleanup
  };
}

function insertMacbookHost(db, { status = "online", lastHeartbeatAt = null } = {}) {
  const createdAt = "2026-03-31T00:00:00.000Z";
  db.prepare(
    `
    INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
    VALUES ('macbook-1', 'macbook-1', 'macbook', ?, ?, ?, ?)
    `
  ).run(status, lastHeartbeatAt, createdAt, createdAt);
}

class FakeWebSocket extends EventEmitter {
  constructor({ autoPong = false } = {}) {
    super();
    this.autoPong = autoPong;
    this.readyState = WS_OPEN;
    this.sent = [];
    this.pingCount = 0;
    this.closeCalls = [];
  }

  send(payload, callback) {
    this.sent.push(payload);
    callback?.();
  }

  ping() {
    this.pingCount += 1;
    if (this.autoPong) {
      this.emit("pong");
    }
  }

  close(code, reason) {
    this.closeCalls.push({
      code,
      reason
    });

    if (this.readyState === WS_CLOSED) {
      return;
    }

    this.readyState = WS_CLOSED;
    this.emit("close", code, reason);
  }
}

function createLogger() {
  return {
    error() {},
    warn() {},
    info() {}
  };
}

test("WsHub sends protocol pings to tracked clients on each keepalive interval", async (t) => {
  const hub = new WsHub(20);
  t.after(async () => {
    await hub.closeAll(1001, "test shutdown");
  });

  const androidClient = new FakeWebSocket();
  const companionClient = new FakeWebSocket();

  hub.addAndroidClient(androidClient);
  hub.setCompanionClient("macbook-1", companionClient);

  await wait(50);

  assert.ok(androidClient.pingCount >= 2);
  assert.ok(companionClient.pingCount >= 2);
  assert.equal(androidClient.closeCalls.length, 0);
  assert.equal(companionClient.closeCalls.length, 0);
});

test("WsHub closes idle clients with code 1001 after more than two missed pongs", async (t) => {
  const hub = new WsHub(20);
  t.after(async () => {
    await hub.closeAll(1001, "test shutdown");
  });

  const client = new FakeWebSocket();
  hub.addAndroidClient(client);

  await wait(80);

  assert.deepEqual(client.closeCalls[0], {
    code: 1001,
    reason: "Going Away"
  });
});

test("WsHub keeps a connection alive when pongs continue to reset the missed counter", async (t) => {
  const hub = new WsHub(20);
  t.after(async () => {
    await hub.closeAll(1001, "test shutdown");
  });

  const client = new FakeWebSocket({ autoPong: true });
  hub.addAndroidClient(client);

  await wait(90);

  assert.ok(client.pingCount >= 3);
  assert.equal(client.closeCalls.length, 0);
  assert.equal(client.readyState, WS_OPEN);
});

test("CompanionManager marks stale hosts offline and broadcasts host_status offline", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-companion-stale-");
  t.after(cleanup);
  insertMacbookHost(db, {
    status: "online",
    lastHeartbeatAt: "2026-03-31T00:00:00.000Z"
  });

  const broadcasts = [];
  const manager = new CompanionManager(
    {
      companionTimeoutMs: 1000,
      heartbeatIntervalMs: 60000,
      heartbeatStaleMs: 90000
    },
    db,
    {
      broadcastHostStatus: (hostId, status) => {
        broadcasts.push({ hostId, status });
      }
    },
    createLogger()
  );

  t.after(() => {
    manager.shutdown();
  });

  const staleHosts = await manager.markStaleHostsOffline(new Date("2026-03-31T00:02:00.000Z"));

  assert.deepEqual(staleHosts, ["macbook-1"]);
  assert.deepEqual(broadcasts, [{ hostId: "macbook-1", status: "offline" }]);
  assert.deepEqual(db.prepare("SELECT status FROM hosts WHERE id = 'macbook-1'").get(), {
    status: "offline"
  });
});

test("CompanionManager broadcasts host_status online when the next heartbeat arrives after a stale timeout", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-companion-recover-");
  t.after(cleanup);
  insertMacbookHost(db, {
    status: "online",
    lastHeartbeatAt: "2026-03-31T00:00:00.000Z"
  });

  const broadcasts = [];
  const manager = new CompanionManager(
    {
      companionTimeoutMs: 1000,
      heartbeatIntervalMs: 60000,
      heartbeatStaleMs: 90000
    },
    db,
    {
      broadcastHostStatus: (hostId, status) => {
        broadcasts.push({ hostId, status });
      }
    },
    createLogger()
  );

  t.after(() => {
    manager.shutdown();
  });

  await manager.markStaleHostsOffline(new Date("2026-03-31T00:02:00.000Z"));
  manager.handleHeartbeat("macbook-1", {
    type: "heartbeat",
    providers: ["claude", "book"],
    uptime: 10
  });

  assert.deepEqual(broadcasts, [
    { hostId: "macbook-1", status: "offline" },
    { hostId: "macbook-1", status: "online" }
  ]);
  assert.deepEqual(db.prepare("SELECT status FROM hosts WHERE id = 'macbook-1'").get(), {
    status: "online"
  });
  assert.deepEqual(manager.getDeclaredProviders("macbook-1"), ["claude", "book"]);
});
