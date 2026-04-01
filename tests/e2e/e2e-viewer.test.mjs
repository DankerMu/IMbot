import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import path from "node:path";
import test from "node:test";

import {
  apiPost,
  assertStatus,
  createBookSession,
  getRuntimeConfig,
  LONG_TIMEOUT_MS,
  waitForStatus
} from "./helpers.mjs";

function waitForOutput(predicate, state, timeoutMs, label) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;

    const check = () => {
      if (predicate(state)) {
        resolve();
        return;
      }

      if (Date.now() > deadline) {
        reject(new Error(`Timed out waiting for ${label}`));
        return;
      }

      setTimeout(check, 100);
    };

    check();
  });
}

test("E2E-37: viewer formats live session events without session prefixes when filtered", { timeout: 180_000 }, async (t) => {
  const fixture = await createBookSession(t, {
    prompt: "viewer test",
    cleanup: true
  });
  if (!fixture) {
    return;
  }
  const { session } = fixture;
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  const { baseUrl, token } = getRuntimeConfig();
  const viewerPath = path.join(process.cwd(), "packages", "viewer", "dist", "index.js");
  const child = spawn(process.execPath, [viewerPath, "--relay", baseUrl, "--token", token, "--session", session.id], {
    stdio: ["ignore", "pipe", "pipe"]
  });
  const output = {
    stdout: "",
    stderr: ""
  };

  child.stdout.on("data", (chunk) => {
    output.stdout += chunk.toString();
  });
  child.stderr.on("data", (chunk) => {
    output.stderr += chunk.toString();
  });

  t.after(() => {
    child.kill("SIGINT");
  });

  await waitForOutput(
    (state) => state.stderr.includes("Connected. Streaming events..."),
    output,
    LONG_TIMEOUT_MS,
    "viewer readiness"
  );

  assertStatus(await apiPost(`/sessions/${session.id}/message`, { text: "viewer second turn" }), 200);
  await waitForStatus(session.id, ["idle"], LONG_TIMEOUT_MS);

  await waitForOutput(
    (state) =>
      state.stdout.includes("\u001b[34m--- session idle ---\u001b[0m") &&
      (state.stdout.includes("viewer second turn") || state.stdout.includes("[user]")),
    output,
    LONG_TIMEOUT_MS,
    "formatted viewer output"
  );

  child.kill("SIGINT");
  const exitCode = await new Promise((resolve) => {
    child.once("exit", (code) => resolve(code));
  });

  assert.ok(output.stdout.includes("\u001b[34m--- session idle ---\u001b[0m"));
  assert.ok(output.stdout.includes("\u001b[36m[user]\u001b[0m viewer second turn") || output.stdout.includes("viewer second turn"));
  assert.ok(!output.stdout.includes(`[${session.id.slice(0, 8)}]`));
  assert.ok(exitCode === 0 || exitCode === null);
});
