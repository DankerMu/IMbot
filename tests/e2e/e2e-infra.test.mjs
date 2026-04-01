import assert from "node:assert/strict";
import test from "node:test";

import {
  apiGet,
  assertError,
  assertStatus,
  getRuntimeConfig,
  request
} from "./helpers.mjs";

test("E2E-01: healthz returns relay health without auth", async () => {
  const response = await request("GET", "/healthz", { absolute: true, auth: false });
  const body = assertStatus(response, 200);

  assert.equal(body.status, "ok");
  assert.equal(body.db, "ok");
  assert.equal(body.companion, "online");
  assert.equal(typeof body.uptime, "number");
  assert.ok(body.uptime > 0);
});

test("E2E-02: session APIs reject missing or invalid bearer tokens", async () => {
  const unauthenticated = await apiGet("/sessions", { auth: false });
  assertError(unauthenticated, 401, "unauthenticated");

  const { token } = getRuntimeConfig();
  const wrongToken = token.slice(0, -1) + (token.endsWith("a") ? "b" : "a");
  const wrongTokenResponse = await apiGet("/sessions", {
    headers: {
      Authorization: `Bearer ${wrongToken}`
    },
    auth: false
  });
  assertError(wrongTokenResponse, 401, "unauthenticated");
});
