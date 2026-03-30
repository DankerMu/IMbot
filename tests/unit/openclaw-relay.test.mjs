import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");

test("relay reports openclaw offline and fails openclaw session creation when gateway is unavailable", async (t) => {
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

  const failedSession = runtime.db
    .prepare(
      `
      SELECT provider, host_id, status, error_code
      FROM sessions
      ORDER BY created_at DESC
      LIMIT 1
      `
    )
    .get();

  assert.deepEqual(failedSession, {
    provider: "openclaw",
    host_id: "relay-local",
    status: "failed",
    error_code: "provider_unreachable"
  });
});
