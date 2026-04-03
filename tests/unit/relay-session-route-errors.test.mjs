import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");

test("unknown session lifecycle endpoints return not_found", async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-relay-session-errors-"));
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
    authorization: `Bearer ${config.staticToken}`
  };
  const unknownId = "nonexistent-id";
  const requests = [
    {
      method: "GET",
      url: `/v1/sessions/${unknownId}`
    },
    {
      method: "POST",
      url: `/v1/sessions/${unknownId}/message`,
      payload: {
        text: "hello"
      }
    },
    {
      method: "POST",
      url: `/v1/sessions/${unknownId}/complete`
    },
    {
      method: "POST",
      url: `/v1/sessions/${unknownId}/answer`,
      payload: {
        call_id: "req-1",
        answer: "Alpha"
      }
    },
    {
      method: "POST",
      url: `/v1/sessions/${unknownId}/cancel`
    },
    {
      method: "POST",
      url: `/v1/sessions/${unknownId}/resume`
    },
    {
      method: "DELETE",
      url: `/v1/sessions/${unknownId}`
    }
  ];

  for (const request of requests) {
    const response = await runtime.app.inject({
      ...request,
      headers
    });
    assert.equal(response.statusCode, 404);
    assert.deepEqual(response.json(), {
      error: "not_found"
    });
  }
});
