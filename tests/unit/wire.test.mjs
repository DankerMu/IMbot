import assert from "node:assert/strict";
import test from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const wire = require("../../packages/wire/dist/index.js");

function withMockedRandom(sequence, callback) {
  const originalRandom = Math.random;
  const values = Array.isArray(sequence) ? [...sequence] : [sequence];

  Math.random = () => {
    if (values.length === 0) {
      return 0;
    }

    return values.shift();
  };

  try {
    return callback();
  } finally {
    Math.random = originalRandom;
  }
}

test("wire exports the expected protocol constants", () => {
  assert.deepEqual(wire.PROVIDERS, ["claude", "book", "openclaw"]);
  assert.equal(wire.EVENT_TYPES.length, 12);
  assert.deepEqual(wire.VALID_TRANSITIONS.running, [
    "idle",
    "completed",
    "failed",
    "cancelled"
  ]);
  assert.equal(wire.ERROR_HTTP_STATUS.command_timeout, 504);
});

test("SESSION_STATUSES includes idle", () => {
  assert.ok(wire.SESSION_STATUSES.includes("idle"));
  assert.deepEqual(wire.SESSION_STATUSES, [
    "queued",
    "running",
    "idle",
    "completed",
    "failed",
    "cancelled"
  ]);
});

test("EVENT_TYPES includes session_idle", () => {
  assert.ok(wire.EVENT_TYPES.includes("session_idle"));
});

test("VALID_TRANSITIONS allows running to idle", () => {
  assert.ok(wire.VALID_TRANSITIONS.running.includes("idle"));
});

test("VALID_TRANSITIONS defines idle outbound edges", () => {
  assert.deepEqual(wire.VALID_TRANSITIONS.idle, ["running", "completed", "cancelled"]);
});

test("VALID_TRANSITIONS idle cannot go to failed directly", () => {
  assert.ok(!wire.VALID_TRANSITIONS.idle.includes("failed"));
});

test("VALID_TRANSITIONS idle cannot go to queued", () => {
  assert.ok(!wire.VALID_TRANSITIONS.idle.includes("queued"));
});

test("VALID_TRANSITIONS preserves existing transitions unchanged", () => {
  assert.deepEqual(wire.VALID_TRANSITIONS.queued, ["running", "failed"]);
  assert.deepEqual(wire.VALID_TRANSITIONS.completed, ["running"]);
  assert.deepEqual(wire.VALID_TRANSITIONS.failed, ["running"]);
  assert.deepEqual(wire.VALID_TRANSITIONS.cancelled, []);
});

test("ExponentialBackoff returns increasing delays up to the max", () => {
  const delays = withMockedRandom(0, () => {
    const backoff = new wire.ExponentialBackoff(1000, 30000, 1000);
    return Array.from({ length: 7 }, () => backoff.nextDelay());
  });

  assert.deepEqual(delays, [1000, 2000, 4000, 8000, 16000, 30000, 30000]);
});

test("ExponentialBackoff delay never exceeds max plus jitter", () => {
  withMockedRandom(0.999, () => {
    const maxMs = 5000;
    const jitterMs = 200;
    const backoff = new wire.ExponentialBackoff(500, maxMs, jitterMs);

    for (let index = 0; index < 12; index += 1) {
      const delay = backoff.nextDelay();
      assert.equal(delay <= maxMs + jitterMs, true);
    }
  });
});

test("ExponentialBackoff jitter stays within the configured range", () => {
  const [firstDelay, secondDelay] = withMockedRandom([0, 0.75], () => {
    const backoff = new wire.ExponentialBackoff(500, 5000, 200);
    return [backoff.nextDelay(), backoff.nextDelay()];
  });

  assert.equal(firstDelay - 500 >= 0, true);
  assert.equal(firstDelay - 500 < 200, true);
  assert.equal(secondDelay - 1000 >= 0, true);
  assert.equal(secondDelay - 1000 < 200, true);
});

test("ExponentialBackoff reset restarts the delay sequence", () => {
  withMockedRandom(0, () => {
    const backoff = new wire.ExponentialBackoff(1000, 30000, 1000);
    backoff.nextDelay();
    backoff.nextDelay();
    assert.equal(backoff.attempts, 2);

    backoff.reset();
    assert.equal(backoff.attempts, 0);
    assert.equal(backoff.nextDelay(), 1000);
  });
});

test("ExponentialBackoff supports custom parameters", () => {
  const delays = withMockedRandom(0, () => {
    const backoff = new wire.ExponentialBackoff(500, 5000, 200);
    return Array.from({ length: 5 }, () => backoff.nextDelay());
  });

  assert.deepEqual(delays, [500, 1000, 2000, 4000, 5000]);
});

test("ExponentialBackoff tracks attempts", () => {
  withMockedRandom(0, () => {
    const backoff = new wire.ExponentialBackoff();
    assert.equal(backoff.attempts, 0);
    backoff.nextDelay();
    backoff.nextDelay();
    assert.equal(backoff.attempts, 2);
    backoff.reset();
    assert.equal(backoff.attempts, 0);
  });
});
