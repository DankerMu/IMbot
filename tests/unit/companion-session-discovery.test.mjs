import assert from "node:assert/strict";
import { mkdtempSync, rmSync, utimesSync, writeFileSync, mkdirSync } from "node:fs";
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

function encodeProjectPath(projectPath) {
  return projectPath.replace(/^\/+/, "").replace(/\//g, "-");
}

function createSessionFile(projectsDir, cwd, sessionId, createdAt, contents = '{"ok":true}\n') {
  const projectDir = path.join(projectsDir, encodeProjectPath(cwd));
  mkdirSync(projectDir, { recursive: true });

  const sessionFile = path.join(projectDir, `${sessionId}.jsonl`);
  writeFileSync(sessionFile, contents, "utf8");

  const timestamp = new Date(createdAt);
  utimesSync(sessionFile, timestamp, timestamp);
  return sessionFile;
}

test("discoverSessions returns matching sessions sorted by mtime descending", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-"));
  const cwd = "/Users/danker/Desktop/AI-vault";

  try {
    createSessionFile(projectsDir, cwd, "session-1", "2026-03-29T10:00:00.000Z");
    createSessionFile(projectsDir, cwd, "session-3", "2026-03-28T10:00:00.000Z");
    createSessionFile(projectsDir, cwd, "session-2", "2026-03-30T10:00:00.000Z");

    const results = await companion.discoverSessions(`${cwd}/`, "claude", {
      claudeProjectsDir: projectsDir,
      logger: silentLogger
    });

    assert.equal(results.length, 3);
    assert.deepEqual(
      results.map((entry) => entry.provider_session_id),
      ["session-2", "session-1", "session-3"]
    );
    assert.deepEqual(
      results.map((entry) => entry.cwd),
      [cwd, cwd, cwd]
    );
    assert.deepEqual(
      results.map((entry) => entry.status),
      ["completed", "completed", "completed"]
    );
  } finally {
    rmSync(projectsDir, { recursive: true, force: true });
  }
});

test("discoverSessions returns an empty array for empty or missing projects directories", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-empty-"));

  try {
    assert.deepEqual(
      await companion.discoverSessions("/Users/danker/Desktop/AI-vault", "claude", {
        claudeProjectsDir: projectsDir,
        logger: silentLogger
      }),
      []
    );

    assert.deepEqual(
      await companion.discoverSessions("/Users/danker/Desktop/AI-vault", "claude", {
        claudeProjectsDir: path.join(projectsDir, "missing"),
        logger: silentLogger
      }),
      []
    );
  } finally {
    rmSync(projectsDir, { recursive: true, force: true });
  }
});

test("discoverSessions skips unreadable project directories and keeps remaining sessions", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-unreadable-"));
  const readableCwd = "/Users/danker/Desktop/AI-vault";
  const unreadableCwd = "/Users/danker/Desktop/OtherProject";
  const unreadableProjectDir = path.join(projectsDir, encodeProjectPath(unreadableCwd));
  const warnings = [];
  const originalReaddir = nodeFs.promises.readdir;

  try {
    createSessionFile(projectsDir, readableCwd, "session-good", "2026-03-30T09:00:00.000Z");
    createSessionFile(projectsDir, unreadableCwd, "session-bad", "2026-03-30T08:00:00.000Z");

    nodeFs.promises.readdir = async function patchedReaddir(targetPath, options) {
      if (String(targetPath) === unreadableProjectDir) {
        const error = new Error("permission denied");
        error.code = "EACCES";
        throw error;
      }

      return await originalReaddir.call(this, targetPath, options);
    };

    const results = await companion.discoverSessions("/Users/danker/Desktop", "claude", {
      claudeProjectsDir: projectsDir,
      logger: {
        ...silentLogger,
        warn(message) {
          warnings.push(String(message));
        }
      }
    });

    assert.deepEqual(
      results.map((entry) => entry.provider_session_id),
      ["session-good"]
    );
    assert.equal(warnings.length > 0, true);
  } finally {
    nodeFs.promises.readdir = originalReaddir;
    rmSync(projectsDir, { recursive: true, force: true });
  }
});

test("discoverSessions uses custom claudeProjectsDir for book provider", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-book-provider-"));
  const cwd = "/Users/danker/Desktop/AI-vault";

  try {
    createSessionFile(projectsDir, cwd, "book-session-1", "2026-03-30T07:00:00.000Z");

    const bookResults = await companion.discoverSessions(cwd, "book", {
      claudeProjectsDir: projectsDir,
      logger: silentLogger
    });

    assert.deepEqual(bookResults, [
      {
        provider_session_id: "book-session-1",
        cwd,
        created_at: "2026-03-30T07:00:00.000Z",
        status: "completed"
      }
    ]);
  } finally {
    rmSync(projectsDir, { recursive: true, force: true });
  }
});

test("discoverSessions matches only the requested cwd prefix", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-match-"));
  const matchingCwd = "/Users/danker/Desktop/AI-vault";
  const otherCwd = "/Users/danker/Desktop/other";

  try {
    createSessionFile(projectsDir, matchingCwd, "session-match", "2026-03-30T06:00:00.000Z");

    const matchingResults = await companion.discoverSessions(matchingCwd, "claude", {
      claudeProjectsDir: projectsDir,
      logger: silentLogger
    });
    const otherResults = await companion.discoverSessions(otherCwd, "claude", {
      claudeProjectsDir: projectsDir,
      logger: silentLogger
    });

    assert.equal(matchingResults.length, 1);
    assert.deepEqual(otherResults, []);
  } finally {
    rmSync(projectsDir, { recursive: true, force: true });
  }
});

test("discoverSessions includes nested cwd history when querying a parent cwd", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-nested-"));
  const parentCwd = "/Users/danker/Desktop/a/b";
  const nestedCwd = path.join(parentCwd, "c");

  try {
    createSessionFile(projectsDir, parentCwd, "session-parent", "2026-03-30T04:00:00.000Z");
    createSessionFile(projectsDir, nestedCwd, "session-child", "2026-03-30T05:00:00.000Z");

    const results = await companion.discoverSessions(parentCwd, "claude", {
      claudeProjectsDir: projectsDir,
      logger: silentLogger
    });

    assert.deepEqual(
      results.map((entry) => ({
        provider_session_id: entry.provider_session_id,
        cwd: entry.cwd
      })),
      [
        {
          provider_session_id: "session-child",
          cwd: nestedCwd
        },
        {
          provider_session_id: "session-parent",
          cwd: parentCwd
        }
      ]
    );
  } finally {
    rmSync(projectsDir, { recursive: true, force: true });
  }
});

test("discoverSessions skips non-jsonl files and nested directories inside project dirs", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-non-jsonl-"));
  const cwd = "/Users/danker/Desktop/AI-vault";
  const projectDir = path.join(projectsDir, encodeProjectPath(cwd));

  try {
    createSessionFile(projectsDir, cwd, "session-real", "2026-03-30T03:00:00.000Z");
    writeFileSync(path.join(projectDir, "session-ignored.json"), "{\"ok\":true}\n", "utf8");
    mkdirSync(path.join(projectDir, "subdir"), { recursive: true });

    const results = await companion.discoverSessions(cwd, "claude", {
      claudeProjectsDir: projectsDir,
      logger: silentLogger
    });

    assert.deepEqual(
      results.map((entry) => entry.provider_session_id),
      ["session-real"]
    );
  } finally {
    rmSync(projectsDir, { recursive: true, force: true });
  }
});

test("discoverSessions applies the configured result limit after sorting newest first", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-limit-"));
  const cwd = "/Users/danker/Desktop/AI-vault";

  try {
    createSessionFile(projectsDir, cwd, "session-1", "2026-03-30T01:00:00.000Z");
    createSessionFile(projectsDir, cwd, "session-2", "2026-03-30T02:00:00.000Z");
    createSessionFile(projectsDir, cwd, "session-3", "2026-03-30T03:00:00.000Z");
    createSessionFile(projectsDir, cwd, "session-4", "2026-03-30T04:00:00.000Z");
    createSessionFile(projectsDir, cwd, "session-5", "2026-03-30T05:00:00.000Z");

    const results = await companion.discoverSessions(cwd, "claude", {
      claudeProjectsDir: projectsDir,
      logger: silentLogger,
      limit: 2
    });

    assert.deepEqual(
      results.map((entry) => entry.provider_session_id),
      ["session-5", "session-4"]
    );
  } finally {
    rmSync(projectsDir, { recursive: true, force: true });
  }
});

test("discoverSessions marks empty session files as unknown", async () => {
  const projectsDir = mkdtempSync(path.join(os.tmpdir(), "imbot-session-discovery-empty-file-"));
  const cwd = "/Users/danker/Desktop/AI-vault";

  try {
    createSessionFile(projectsDir, cwd, "session-empty", "2026-03-30T05:00:00.000Z", "");

    const results = await companion.discoverSessions(cwd, "claude", {
      claudeProjectsDir: projectsDir,
      logger: silentLogger
    });

    assert.deepEqual(results, [
      {
        provider_session_id: "session-empty",
        cwd,
        created_at: "2026-03-30T05:00:00.000Z",
        status: "unknown"
      }
    ]);
  } finally {
    rmSync(projectsDir, { recursive: true, force: true });
  }
});
