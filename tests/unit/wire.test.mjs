import assert from "node:assert/strict";
import test from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const wire = require("../../packages/wire/dist/index.js");

test("wire exports the expected protocol constants", () => {
  assert.deepEqual(wire.PROVIDERS, ["claude", "book", "openclaw"]);
  assert.equal(wire.EVENT_TYPES.length, 11);
  assert.deepEqual(wire.VALID_TRANSITIONS.running, [
    "completed",
    "failed",
    "cancelled"
  ]);
  assert.equal(wire.ERROR_HTTP_STATUS.command_timeout, 504);
});
