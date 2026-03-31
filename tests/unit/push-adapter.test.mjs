import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const { initializeDatabase } = require("../../packages/relay/dist/db/init.js");
const { PushAdapter } = require("../../packages/relay/dist/push/fcm-adapter.js");
const firebaseAdminApp = require("firebase-admin/app");
const firebaseMessaging = require("firebase-admin/messaging");

function createTestDatabase(prefix) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), prefix));
  const db = initializeDatabase(path.join(tempDir, "imbot.db"));

  const cleanup = () => {
    db.close();
    rmSync(tempDir, { recursive: true, force: true });
  };

  return {
    db,
    tempDir,
    cleanup
  };
}

function createTestConfig(overrides = {}) {
  return {
    host: "127.0.0.1",
    port: 3000,
    staticToken: "t".repeat(64),
    dbPath: ":memory:",
    fcmProjectId: null,
    fcmServiceAccount: null,
    openClawUrl: "ws://127.0.0.1:1",
    openClawToken: null,
    logLevel: "error",
    companionTimeoutMs: 30000,
    heartbeatIntervalMs: 60000,
    heartbeatStaleMs: 90000,
    purgeDays: 30,
    wsPingIntervalMs: 30000,
    ...overrides
  };
}

function replaceModuleMethod(moduleObject, methodName, implementation) {
  const originalDescriptor = Object.getOwnPropertyDescriptor(moduleObject, methodName);
  Object.defineProperty(moduleObject, methodName, {
    configurable: true,
    enumerable: true,
    writable: true,
    value: implementation
  });

  return () => {
    if (originalDescriptor) {
      Object.defineProperty(moduleObject, methodName, originalDescriptor);
    }
  };
}

function createLogger() {
  const warnings = [];
  const errors = [];

  return {
    warnings,
    errors,
    logger: {
      warn: (...args) => warnings.push(args.map(String).join(" ")),
      error: (...args) => errors.push(args.map(String).join(" "))
    }
  };
}

function insertHost(db, hostId = "macbook-1", hostName = "MacBook") {
  const now = new Date().toISOString();
  db.prepare(
    `
    INSERT INTO hosts (id, name, type, status, last_heartbeat_at, created_at, updated_at)
    VALUES (?, ?, 'macbook', 'online', ?, ?, ?)
    `
  ).run(hostId, hostName, now, now, now);
}

function insertSession(db, sessionId, initialPrompt) {
  insertHost(db);
  const now = new Date().toISOString();
  db.prepare(
    `
    INSERT INTO sessions (
      id,
      provider,
      provider_session_id,
      host_id,
      workspace_root,
      workspace_cwd,
      initial_prompt,
      model,
      permission_mode,
      status,
      error_message,
      error_code,
      created_at,
      updated_at,
      last_active_at
    ) VALUES (?, 'claude', 'provider-session-1', 'macbook-1', NULL, '/tmp/project', ?, NULL, 'bypassPermissions', 'running', NULL, NULL, ?, ?, ?)
    `
  ).run(sessionId, initialPrompt, now, now, now);
}

function insertPushToken(db, token) {
  db.prepare(
    `
    INSERT INTO push_subscriptions (id, fcm_token, created_at, updated_at)
    VALUES (?, ?, datetime('now'), datetime('now'))
    `
  ).run(`push-${token}`, token);
}

test("PushAdapter init without credentials disables push without throwing", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-push-adapter-missing-");
  t.after(cleanup);

  const { logger, warnings, errors } = createLogger();
  const adapter = new PushAdapter(createTestConfig(), db, logger);

  await adapter.init();

  assert.equal(adapter.enabled, false);
  assert.equal(adapter.app, null);
  assert.equal(errors.length, 0);
  assert.match(warnings[0], /RELAY_FCM_SERVICE_ACCOUNT/);
});

test("PushAdapter init accepts inline JSON credentials", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-push-adapter-inline-");
  t.after(cleanup);

  const fakeApp = { name: "fake-app" };
  const certCalls = [];
  const initializeCalls = [];

  const restoreCert = replaceModuleMethod(firebaseAdminApp, "cert", (serviceAccount) => {
    certCalls.push(serviceAccount);
    return { credential: "fake", serviceAccount };
  });
  const restoreInitializeApp = replaceModuleMethod(firebaseAdminApp, "initializeApp", (options, name) => {
    initializeCalls.push({ options, name });
    return fakeApp;
  });
  t.after(() => {
    restoreCert();
    restoreInitializeApp();
  });

  const { logger } = createLogger();
  const adapter = new PushAdapter(
    createTestConfig({
      fcmProjectId: "configured-project",
      fcmServiceAccount: JSON.stringify({
        project_id: "inline-project",
        client_email: "imbot@example.com",
        private_key: "not-a-real-key"
      })
    }),
    db,
    logger
  );

  await adapter.init();

  assert.equal(adapter.enabled, true);
  assert.equal(adapter.app, fakeApp);
  assert.equal(certCalls.length, 1);
  assert.equal(initializeCalls.length, 1);
  assert.equal(initializeCalls[0].options.projectId, "configured-project");
  assert.match(initializeCalls[0].name, /^imbot-relay-push-/);
});

test("PushAdapter notify sends to all tokens and deletes stale registrations", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-push-adapter-notify-");
  t.after(cleanup);

  insertSession(
    db,
    "sess-push",
    "This is a deliberately long prompt that needs truncation in the push title"
  );
  insertPushToken(db, "token-good");
  insertPushToken(db, "token-stale");

  const sentMessages = [];
  const restoreCert = replaceModuleMethod(firebaseAdminApp, "cert", () => ({ credential: "fake" }));
  const restoreInitializeApp = replaceModuleMethod(firebaseAdminApp, "initializeApp", () => ({
    name: "push-app"
  }));
  const restoreGetMessaging = replaceModuleMethod(firebaseMessaging, "getMessaging", () => ({
    send: async (message) => {
      sentMessages.push(message);
      if (message.token === "token-stale") {
        const error = new Error("stale token");
        error.code = "messaging/registration-token-not-registered";
        throw error;
      }
    }
  }));
  t.after(() => {
    restoreCert();
    restoreInitializeApp();
    restoreGetMessaging();
  });

  const { logger, warnings } = createLogger();
  const adapter = new PushAdapter(
    createTestConfig({
      fcmServiceAccount: JSON.stringify({
        project_id: "push-project",
        client_email: "imbot@example.com",
        private_key: "not-a-real-key"
      })
    }),
    db,
    logger
  );
  await adapter.init();

  await adapter.notify("sess-push", "failed", "Relay exploded");

  assert.equal(sentMessages.length, 2);
  const messagesByToken = new Map(sentMessages.map((message) => [message.token, message]));
  assert.equal(messagesByToken.get("token-good").data.session_id, "sess-push");
  assert.equal(messagesByToken.get("token-good").data.action, "open_session");
  assert.equal(messagesByToken.get("token-good").notification.body, "Relay exploded");
  assert.match(messagesByToken.get("token-good").notification.title, /^✗ /);
  assert.match(messagesByToken.get("token-good").notification.title, /\.\.\. 失败$/);

  const remainingTokens = db
    .prepare("SELECT fcm_token FROM push_subscriptions ORDER BY fcm_token ASC")
    .all()
    .map((row) => row.fcm_token);
  assert.deepEqual(remainingTokens, ["token-good"]);
  assert.equal(warnings.length, 1);
  assert.match(
    warnings[0],
    /Deleted stale FCM token after unregistered send failure: token-st\*\*\*/
  );
});

test("PushAdapter notifyHostOffline sends the host offline payload", async (t) => {
  const { db, cleanup } = createTestDatabase("imbot-push-adapter-host-offline-");
  t.after(cleanup);

  insertPushToken(db, "token-host");

  const sentMessages = [];
  const restoreCert = replaceModuleMethod(firebaseAdminApp, "cert", () => ({ credential: "fake" }));
  const restoreInitializeApp = replaceModuleMethod(firebaseAdminApp, "initializeApp", () => ({
    name: "push-app"
  }));
  const restoreGetMessaging = replaceModuleMethod(firebaseMessaging, "getMessaging", () => ({
    send: async (message) => {
      sentMessages.push(message);
    }
  }));
  t.after(() => {
    restoreCert();
    restoreInitializeApp();
    restoreGetMessaging();
  });

  const { logger } = createLogger();
  const adapter = new PushAdapter(
    createTestConfig({
      fcmServiceAccount: JSON.stringify({
        project_id: "push-project",
        client_email: "imbot@example.com",
        private_key: "not-a-real-key"
      })
    }),
    db,
    logger
  );
  await adapter.init();

  await adapter.notifyHostOffline("macbook-1", "MacBook Pro");

  assert.equal(sentMessages.length, 1);
  assert.deepEqual(sentMessages[0].notification, {
    title: "MacBook Pro 已离线",
    body: "设备连接中断"
  });
  assert.deepEqual(sentMessages[0].data, {
    action: "open_home",
    host_id: "macbook-1"
  });
});

test("PushAdapter truncatePrompt truncates only when needed", () => {
  const { db, cleanup } = createTestDatabase("imbot-push-adapter-truncate-");
  const { logger } = createLogger();
  const adapter = new PushAdapter(createTestConfig(), db, logger);

  try {
    assert.equal(adapter.truncatePrompt("short prompt", 30), "short prompt");
    assert.equal(adapter.truncatePrompt("1234567890", 5), "12345...");
  } finally {
    cleanup();
  }
});

test("POST /v1/push/register upserts the same token into a single row", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-push-route-"));
  const config = relay.loadConfig({
    RELAY_STATIC_TOKEN: "t".repeat(64),
    RELAY_DB_PATH: path.join(tempDir, "imbot.db"),
    RELAY_LOG_LEVEL: "error",
    RELAY_OPENCLAW_URL: "ws://127.0.0.1:1"
  });

  const runtime = await relay.createRelayApp({
    config,
    logger: false
  });

  t.after(async () => {
    await runtime.close();
    rmSync(tempDir, { recursive: true, force: true });
  });

  const headers = {
    authorization: `Bearer ${config.staticToken}`,
    "content-type": "application/json"
  };
  const payload = {
    fcm_token: "same-token"
  };

  const firstResponse = await runtime.app.inject({
    method: "POST",
    url: "/v1/push/register",
    headers,
    payload
  });

  assert.equal(firstResponse.statusCode, 200);
  assert.deepEqual(firstResponse.json(), { status: "ok" });

  const firstRow = runtime.db
    .prepare("SELECT id, created_at, updated_at FROM push_subscriptions WHERE fcm_token = ?")
    .get("same-token");

  await new Promise((resolve) => setTimeout(resolve, 1100));

  const secondResponse = await runtime.app.inject({
    method: "POST",
    url: "/v1/push/register",
    headers,
    payload
  });

  assert.equal(secondResponse.statusCode, 200);
  assert.deepEqual(secondResponse.json(), { status: "ok" });

  const secondRow = runtime.db
    .prepare("SELECT id, created_at, updated_at FROM push_subscriptions WHERE fcm_token = ?")
    .get("same-token");
  const rowCount = runtime.db.prepare("SELECT COUNT(*) AS count FROM push_subscriptions").get();

  assert.deepEqual(rowCount, { count: 1 });
  assert.equal(secondRow.id, firstRow.id);
  assert.equal(secondRow.created_at, firstRow.created_at);
  assert.notEqual(secondRow.updated_at, firstRow.updated_at);
});
