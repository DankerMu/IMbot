import assert from "node:assert/strict";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  writeFileSync
} from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const companion = require("../../packages/companion/dist/index.js");
const nodeFs = require("node:fs");

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
    const canonicalRootPath = realpathSync(rootPath);
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
      path: canonicalRootPath,
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
        path: canonicalRootPath,
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

test("ConfigManager auto-creates config file and directory on first persist", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-create-"));
  const configPath = path.join(tempDir, "nested", "config", "companion.json");
  const rootPath = path.join(tempDir, "workspace");
  mkdirSync(rootPath);

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });

    manager.addRoot("claude", rootPath, "AI vault");

    assert.equal(existsSync(path.dirname(configPath)), true);
    assert.equal(existsSync(configPath), true);
    assert.deepEqual(JSON.parse(readFileSync(configPath, "utf8")).workspace_roots, manager.getRoots("claude"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ConfigManager tolerates corrupt JSON config files and starts with empty roots", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-corrupt-"));
  const configPath = path.join(tempDir, "companion.json");
  const warnings = [];
  writeFileSync(configPath, "{not-json", "utf8");

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: {
        ...silentLogger,
        warn(message) {
          warnings.push(String(message));
        }
      }
    });

    assert.deepEqual(manager.getRoots(), []);
    assert.equal(warnings.length > 0, true);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ConfigManager loads legacy roots that omit added_at", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-legacy-"));
  const configPath = path.join(tempDir, "companion.json");
  const rootPath = path.join(tempDir, "workspace");
  mkdirSync(rootPath);
  writeFileSync(
    configPath,
    `${JSON.stringify(
      {
        ...createBaseConfig(),
        workspace_roots: [
          {
            id: "root-ai-vault",
            provider: "claude",
            path: rootPath,
            label: "AI-vault"
          }
        ]
      },
      null,
      2
    )}\n`,
    "utf8"
  );

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });

    assert.deepEqual(manager.getRoots("claude"), [
      {
        provider: "claude",
        path: path.resolve(rootPath),
        label: "AI-vault",
        added_at: "1970-01-01T00:00:00.000Z"
      }
    ]);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ConfigManager addRoot rejects regular files", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-file-"));
  const configPath = path.join(tempDir, "companion.json");
  const filePath = path.join(tempDir, "workspace.txt");
  writeFileSync(filePath, "not-a-directory", "utf8");
  writeFileSync(configPath, `${JSON.stringify(createBaseConfig(), null, 2)}\n`, "utf8");

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });

    assert.throws(() => manager.addRoot("claude", filePath), {
      code: "not_found",
      message: `Workspace root ${filePath} not found`
    });
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

test("ConfigManager allows different providers to share the same path", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-shared-"));
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
    manager.addRoot("book", rootPath, "Novel");

    assert.equal(manager.getRoots("claude").length, 1);
    assert.equal(manager.getRoots("book").length, 1);
    assert.equal(manager.getRoots().length, 2);
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

test("ConfigManager blocks symlink escapes in isPathUnderRoot", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-symlink-"));
  const configPath = path.join(tempDir, "companion.json");
  const rootPath = path.join(tempDir, "novel");
  const nestedPath = path.join(rootPath, "chapter-1");
  const outsidePath = path.join(tempDir, "outside", "secret");
  const sneakyPath = path.join(rootPath, "sneaky");
  mkdirSync(nestedPath, { recursive: true });
  mkdirSync(outsidePath, { recursive: true });
  symlinkSync(outsidePath, sneakyPath);
  writeFileSync(configPath, `${JSON.stringify(createBaseConfig(), null, 2)}\n`, "utf8");

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });

    manager.addRoot("book", rootPath, "Novel");

    assert.equal(manager.isPathUnderRoot("book", nestedPath), true);
    assert.equal(manager.isPathUnderRoot("book", sneakyPath), false);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("ConfigManager addRoot rolls back in-memory state when persist fails", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-config-manager-rollback-"));
  const configPath = path.join(tempDir, "companion.json");
  const firstRootPath = path.join(tempDir, "workspace-1");
  const secondRootPath = path.join(tempDir, "workspace-2");
  mkdirSync(firstRootPath);
  mkdirSync(secondRootPath);
  writeFileSync(configPath, `${JSON.stringify(createBaseConfig(), null, 2)}\n`, "utf8");

  const originalWriteFileSync = nodeFs.writeFileSync;

  try {
    const manager = new companion.ConfigManager({
      configPath,
      logger: silentLogger
    });
    manager.addRoot("claude", firstRootPath, "First");
    const rootsBeforeFailure = manager.getRoots();

    nodeFs.writeFileSync = () => {
      throw new Error("disk full");
    };

    assert.throws(() => manager.addRoot("claude", secondRootPath, "Second"), {
      message: "disk full"
    });
    assert.deepEqual(manager.getRoots(), rootsBeforeFailure);
  } finally {
    nodeFs.writeFileSync = originalWriteFileSync;
    rmSync(tempDir, { recursive: true, force: true });
  }
});
