import assert from "node:assert/strict";
import test from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const relay = require("../../packages/relay/dist/index.js");
const companion = require("../../packages/companion/dist/index.js");

test("relay and companion resolve the shared wire package", () => {
  assert.deepEqual(relay.RELAY_SUPPORTED_PROVIDERS, ["claude", "book", "openclaw"]);
  assert.deepEqual(companion.COMPANION_SUPPORTED_PROVIDERS, [
    "claude",
    "book",
    "openclaw"
  ]);
  assert.equal(companion.supportsInteractiveProvider("claude"), true);
  assert.equal(companion.supportsInteractiveProvider("openclaw"), false);
});
