import assert from "node:assert/strict";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync
} from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const companion = require("../../packages/companion/dist/index.js");

const silentLogger = {
  debug() {},
  info() {},
  warn() {},
  error() {}
};

function createBaseConfig() {
  return {
    relay_url: "wss://relay.example.com",
    token: "static-token",
    host_id: "macbook-1",
    providers: {
      claude: {
        binary: "claude"
      },
      book: {
        binary: "book"
      }
    }
  };
}

test("ConfigManager addRoot persists roots, preserves config keys, and reloads cleanly", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-"));
  const configPath = path.join(tempDir, "companion.json");
  const rootPath = path.join(tempDir, "workspace");
  mkdirSync(rootPath);
  writeFileSync(configPath, `${JSON.stringify(createBaseConfig(), null, 2)}\n`, "utf8");

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });

    manager.addRoot("claude", rootPath, "AI vault");

    const persisted = JSON.parse(readFileSync(configPath, "utf8"));
    assert.equal(persisted.relay_url, "wss://relay.example.com");
    assert.equal(persisted.token, "static-token");
    assert.equal(persisted.host_id, "macbook-1");
    assert.equal(persisted.workspace_roots.length, 1);
    assert.deepEqual(persisted.workspace_roots[0], {
      provider: "claude",
      path: rootPath,
      label: "AI vault",
      added_at: persisted.workspace_roots[0].added_at
    });
    assert.equal(existsSync(`${configPath}.${process.pid}.tmp`), false);

    const reloaded = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });
    assert.deepEqual(reloaded.getRoots("claude"), [
      {
        provider: "claude",
        path: rootPath,
        label: "AI vault",
        added_at: persisted.workspace_roots[0].added_at
      }
    ]);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ConfigManager addRoot rejects missing paths and ignores duplicates", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-add-"));
  const configPath = path.join(tempDir, "companion.json");
  const rootPath = path.join(tempDir, "workspace");
  mkdirSync(rootPath);
  writeFileSync(configPath, `${JSON.stringify(createBaseConfig(), null, 2)}\n`, "utf8");

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });

    assert.throws(() => manager.addRoot("claude", path.join(tempDir, "missing")), {
      code: "not_found"
    });

    manager.addRoot("claude", rootPath, "AI vault");
    manager.addRoot("claude", rootPath, "AI vault");
    assert.equal(manager.getRoots("claude").length, 1);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ConfigManager removeRoot persists deletions and reports missing roots", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-remove-"));
  const configPath = path.join(tempDir, "companion.json");
  const rootPath = path.join(tempDir, "workspace");
  mkdirSync(rootPath);
  writeFileSync(configPath, `${JSON.stringify(createBaseConfig(), null, 2)}\n`, "utf8");

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });

    manager.addRoot("book", rootPath, "Novel");
    manager.removeRoot("book", rootPath);

    assert.deepEqual(manager.getRoots("book"), []);
    assert.deepEqual(JSON.parse(readFileSync(configPath, "utf8")).workspace_roots, []);
    assert.throws(() => manager.removeRoot("book", rootPath), {
      code: "not_found",
      message: "Workspace root not found"
    });
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ConfigManager isPathUnderRoot matches nested directories only for the same provider", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-match-"));
  const configPath = path.join(tempDir, "companion.json");
  const rootPath = path.join(tempDir, "AI-vault");
  const nestedPath = path.join(rootPath, "IMbot");
  const otherPath = path.join(tempDir, "other");
  mkdirSync(nestedPath, { recursive: true });
  mkdirSync(otherPath);
  writeFileSync(configPath, `${JSON.stringify(createBaseConfig(), null, 2)}\n`, "utf8");

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });

    manager.addRoot("claude", rootPath, "AI vault");

    assert.equal(manager.isPathUnderRoot("claude", nestedPath), true);
    assert.equal(manager.isPathUnderRoot("claude", otherPath), false);
    assert.equal(manager.isPathUnderRoot("book", nestedPath), false);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});
