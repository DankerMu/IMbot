import test from "node:test";

import {
  apiPost,
  assertError,
  assertStatus
} from "./helpers.mjs";

test("E2E-35: FCM token registration is idempotent", async () => {
  const token = `e2e-test-token-${Date.now()}`;
  const first = await apiPost("/push/register", { fcm_token: token });
  const firstBody = assertStatus(first, 200);
  if (firstBody && typeof firstBody === "object" && "status" in firstBody) {
    if (firstBody.status !== "ok") {
      throw new Error(`Expected push registration status=ok, got ${JSON.stringify(firstBody)}`);
    }
  }

  const second = await apiPost("/push/register", { fcm_token: token });
  const body = assertStatus(second, 200);
  if (body && typeof body === "object") {
    if ("status" in body) {
      if (body.status !== "ok") {
        throw new Error(`Expected push registration status=ok, got ${JSON.stringify(body)}`);
      }
    }
  }
});

test("E2E-36: invalid FCM registration payloads are rejected", async () => {
  const emptyToken = await apiPost("/push/register", { fcm_token: "" });
  assertError(emptyToken, 400, "invalid_request");

  const missingToken = await apiPost("/push/register", {});
  assertError(missingToken, 400, "invalid_request");
});
