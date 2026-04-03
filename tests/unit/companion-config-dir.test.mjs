import assert from "node:assert/strict";
import { chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const require = createRequire(import.meta.url);
const companion = require("../../packages/companion/dist/index.js");

function writeCompanionConfig(configPath, providers) {
  writeFileSync(
    configPath,
    `${JSON.stringify(
      {
        relay_url: "ws://127.0.0.1:3010",
        token: "test-token",
        host_id: "macbook-1",
        providers
      },
      null,
      2
    )}\n`,
    "utf8"
  );
}

function createEnv(configPath, homeDir, extra = {}) {
  return {
    COMPANION_CONFIG: configPath,
    HOME: homeDir,
    PATH: "",
    ...extra
  };
}

function writeExecutable(filePath, contents) {
  writeFileSync(filePath, contents, "utf8");
  chmodSync(filePath, 0o755);
}

test("loadCompanionConfig sets default configDir ~/.claude for claude provider", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-default-"));
  const homeDir = path.join(tempDir, "home");
  const binDir = path.join(tempDir, "bin");
  const configPath = path.join(tempDir, "companion.json");

  mkdirSync(homeDir, { recursive: true });
  mkdirSync(binDir, { recursive: true });
  writeExecutable(path.join(binDir, "claude"), "#!/bin/sh\nexec true\n");
  writeCompanionConfig(configPath, {
    claude: {
      binary: "claude"
    }
  });

  try {
    const config = companion.loadCompanionConfig(
      createEnv(configPath, homeDir, {
        PATH: binDir
      })
    );

    assert.equal(config.providers.claude.configDir, path.join(homeDir, ".claude"));
    assert.equal(config.providers.claude.projectsDir, path.join(homeDir, ".claude", "projects"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("loadCompanionConfig uses explicit config_dir from companion.json", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-explicit-"));
  const homeDir = path.join(tempDir, "home");
  const configPath = path.join(tempDir, "companion.json");

  mkdirSync(homeDir, { recursive: true });
  writeCompanionConfig(configPath, {
    book: {
      binary: "book",
      config_dir: "~/.claudebook"
    }
  });

  try {
    const config = companion.loadCompanionConfig(createEnv(configPath, homeDir));

    assert.equal(config.providers.book.configDir, path.join(homeDir, ".claudebook"));
    assert.equal(config.providers.book.projectsDir, path.join(homeDir, ".claudebook", "projects"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("loadCompanionConfig auto-detects CLAUDE_CONFIG_DIR from book binary wrapper", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-detect-"));
  const homeDir = path.join(tempDir, "home");
  const configPath = path.join(tempDir, "companion.json");
  const bookBinary = path.join(tempDir, "book");

  mkdirSync(homeDir, { recursive: true });
  writeExecutable(
    bookBinary,
    "#!/bin/bash\nexport CLAUDE_CONFIG_DIR=\"$HOME/.claudebook\"\nexec claude \"$@\"\n"
  );
  writeCompanionConfig(configPath, {
    book: {
      binary: bookBinary
    }
  });

  try {
    const config = companion.loadCompanionConfig(createEnv(configPath, homeDir));

    assert.equal(config.providers.book.configDir, path.join(homeDir, ".claudebook"));
    assert.equal(config.providers.book.projectsDir, path.join(homeDir, ".claudebook", "projects"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("loadCompanionConfig skips auto-detection for unresolved bare binary name", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-unresolved-"));
  const homeDir = path.join(tempDir, "home");
  const configPath = path.join(tempDir, "companion.json");
  const binaryName = "nonexistent-book-binary";
  const originalCwd = process.cwd();

  mkdirSync(homeDir, { recursive: true });
  writeExecutable(
    path.join(tempDir, binaryName),
    "#!/bin/bash\nexport CLAUDE_CONFIG_DIR=\"$HOME/.evil\"\nexec claude \"$@\"\n"
  );
  writeCompanionConfig(configPath, {
    book: {
      binary: binaryName
    }
  });

  try {
    process.chdir(tempDir);
    const config = companion.loadCompanionConfig(createEnv(configPath, homeDir));

    assert.equal(config.providers.book.configDir, path.join(homeDir, ".claude"));
    assert.equal(config.providers.book.projectsDir, path.join(homeDir, ".claude", "projects"));
  } finally {
    process.chdir(originalCwd);
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("loadCompanionConfig only scans the first 1024 bytes when auto-detecting config dir", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-bounded-"));
  const homeDir = path.join(tempDir, "home");
  const configPath = path.join(tempDir, "companion.json");
  const bookBinary = path.join(tempDir, "book");

  mkdirSync(homeDir, { recursive: true });
  writeExecutable(
    bookBinary,
    `#!/bin/bash\n${"x".repeat(1100)}\nexport CLAUDE_CONFIG_DIR="$HOME/.claudebook"\nexec claude "$@"\n`
  );
  writeCompanionConfig(configPath, {
    book: {
      binary: bookBinary
    }
  });

  try {
    const config = companion.loadCompanionConfig(createEnv(configPath, homeDir));

    assert.equal(config.providers.book.configDir, path.join(homeDir, ".claude"));
    assert.equal(config.providers.book.projectsDir, path.join(homeDir, ".claude", "projects"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("loadCompanionConfig falls back to ~/.claude when binary is not a shell script", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-binary-"));
  const homeDir = path.join(tempDir, "home");
  const configPath = path.join(tempDir, "companion.json");
  const binaryPath = path.join(tempDir, "book.bin");

  mkdirSync(homeDir, { recursive: true });
  writeFileSync(binaryPath, Buffer.from([0xcf, 0xfa, 0xed, 0xfe, 0x00, 0x01]));
  writeCompanionConfig(configPath, {
    book: {
      binary: binaryPath
    }
  });

  try {
    const config = companion.loadCompanionConfig(createEnv(configPath, homeDir));

    assert.equal(config.providers.book.configDir, path.join(homeDir, ".claude"));
    assert.equal(config.providers.book.projectsDir, path.join(homeDir, ".claude", "projects"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("loadCompanionConfig falls back to ~/.claude when binary does not contain CLAUDE_CONFIG_DIR", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-no-match-"));
  const homeDir = path.join(tempDir, "home");
  const configPath = path.join(tempDir, "companion.json");
  const bookBinary = path.join(tempDir, "book");

  mkdirSync(homeDir, { recursive: true });
  writeExecutable(bookBinary, "#!/bin/bash\nexec claude \"$@\"\n");
  writeCompanionConfig(configPath, {
    book: {
      binary: bookBinary
    }
  });

  try {
    const config = companion.loadCompanionConfig(createEnv(configPath, homeDir));

    assert.equal(config.providers.book.configDir, path.join(homeDir, ".claude"));
    assert.equal(config.providers.book.projectsDir, path.join(homeDir, ".claude", "projects"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("explicit config_dir takes precedence over auto-detection", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-precedence-"));
  const homeDir = path.join(tempDir, "home");
  const configPath = path.join(tempDir, "companion.json");
  const bookBinary = path.join(tempDir, "book");

  mkdirSync(homeDir, { recursive: true });
  writeExecutable(
    bookBinary,
    "#!/bin/bash\nexport CLAUDE_CONFIG_DIR=\"$HOME/.claudebook\"\nexec claude \"$@\"\n"
  );
  writeCompanionConfig(configPath, {
    book: {
      binary: bookBinary,
      config_dir: "~/.custom-book-dir"
    }
  });

  try {
    const config = companion.loadCompanionConfig(createEnv(configPath, homeDir));

    assert.equal(config.providers.book.configDir, path.join(homeDir, ".custom-book-dir"));
    assert.equal(config.providers.book.projectsDir, path.join(homeDir, ".custom-book-dir", "projects"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("configDir expands ~ to home directory", () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-companion-config-dir-expand-"));
  const homeDir = path.join(tempDir, "home");
  const configPath = path.join(tempDir, "companion.json");

  mkdirSync(homeDir, { recursive: true });
  writeCompanionConfig(configPath, {
    book: {
      binary: "book",
      config_dir: "~/.claudebook"
    }
  });

  try {
    const config = companion.loadCompanionConfig(createEnv(configPath, homeDir));

    assert.equal(path.isAbsolute(config.providers.book.configDir), true);
    assert.equal(config.providers.book.configDir.includes("~"), false);
    assert.equal(config.providers.book.configDir, path.join(homeDir, ".claudebook"));
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});
