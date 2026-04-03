import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
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

function createProviderConfigs(tempDir) {
  return {
    claude: {
      binary: "claude",
      configDir: path.join(tempDir, ".claude"),
      projectsDir: path.join(tempDir, ".claude", "projects")
    },
    book: {
      binary: "book",
      configDir: path.join(tempDir, ".claudebook"),
      projectsDir: path.join(tempDir, ".claudebook", "projects")
    }
  };
}

function createRoot(provider, rootPath, addedAt = "2026-01-01T00:00:00.000Z") {
  return {
    provider,
    path: rootPath,
    added_at: addedAt
  };
}

function createSession(providerSessionId, cwd, createdAt, status = "completed") {
  return {
    provider_session_id: providerSessionId,
    cwd,
    created_at: createdAt,
    status
  };
}

function createHarness(t, options = {}) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-reconciler-"));
  const sessionIndexPath = path.join(tempDir, "sessions.json");
  const configPath = path.join(tempDir, "companion.json");
  const providers = options.providers ?? createProviderConfigs(tempDir);
  const roots = options.roots ?? [];
  const configDocument = {
    workspace_roots: roots,
    ...(options.configDocument ?? {})
  };

  writeFileSync(configPath, `${JSON.stringify(configDocument, null, 2)}\n`, "utf8");

  const sessionIndex = new companion.SessionIndex({
    filePath: sessionIndexPath,
    logger: silentLogger
  });
  for (const entry of options.indexedEntries ?? []) {
    sessionIndex.set(entry.relay_session_id, entry.entry);
  }

  const configManager = new companion.ConfigManager({
    configPath,
    logger: silentLogger
  });
  const sentMessages = [];
  const discoverCalls = [];

  const reconciler = new companion.SessionReconciler({
    sessionIndex,
    configManager,
    providers,
    hostId: options.hostId ?? "macbook-1",
    logger: silentLogger,
    sendMessage: (message) => {
      sentMessages.push(message);
      options.onSendMessage?.(message);
    },
    discoverSessionsFn: async (cwd, provider, discoverOptions) => {
      discoverCalls.push({
        cwd,
        provider,
        options: discoverOptions
      });

      if (options.discoverSessionsFn) {
        return await options.discoverSessionsFn(cwd, provider, discoverOptions);
      }

      return [];
    }
  });

  t.after(() => {
    rmSync(tempDir, { recursive: true, force: true });
  });

  return {
    tempDir,
    sessionIndexPath,
    configPath,
    providers,
    sessionIndex,
    configManager,
    sentMessages,
    discoverCalls,
    reconciler
  };
}

test("SessionReconciler scans all configured workspace roots", async (t) => {
  const rootClaude = "/workspace/project-a";
  const rootBook = "/workspace/project-b";
  const { providers, discoverCalls, sentMessages, reconciler } = createHarness(t, {
    roots: [
      createRoot("claude", rootClaude),
      createRoot("book", rootBook)
    ]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 0, skipped: 0 });
  assert.equal(sentMessages.length, 0);
  assert.deepEqual(discoverCalls, [
    {
      cwd: rootClaude,
      provider: "claude",
      options: {
        claudeProjectsDir: providers.claude.projectsDir,
        logger: silentLogger,
        limit: 200
      }
    },
    {
      cwd: rootBook,
      provider: "book",
      options: {
        claudeProjectsDir: providers.book.projectsDir,
        logger: silentLogger,
        limit: 200
      }
    }
  ]);
});

test("SessionReconciler filters indexed sessions and reports only the diff", async (t) => {
  const rootClaude = "/workspace/project-a";
  const { sentMessages, sessionIndex, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    indexedEntries: [
      {
        relay_session_id: "relay-known",
        entry: {
          provider_session_id: "provider-known",
          cwd: rootClaude,
          provider: "claude",
          created_at: "2026-01-01T00:00:00.000Z",
          source: "remote",
          initial_prompt: "known prompt"
        }
      }
    ],
    discoverSessionsFn: async () => [
      createSession("provider-known", rootClaude, "2026-01-02T00:00:00.000Z"),
      createSession("provider-new", rootClaude, "2026-01-03T00:00:00.000Z")
    ]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 1, skipped: 1 });
  assert.deepEqual(sentMessages, [
    {
      type: "report_local_sessions",
      host_id: "macbook-1",
      sessions: [
        {
          provider_session_id: "provider-new",
          provider: "claude",
          cwd: rootClaude,
          created_at: "2026-01-03T00:00:00.000Z"
        }
      ]
    }
  ]);
  assert.deepEqual(sessionIndex.get("local:provider-new"), {
    provider_session_id: "provider-new",
    cwd: rootClaude,
    provider: "claude",
    created_at: "2026-01-03T00:00:00.000Z",
    source: "local"
  });
});

test("SessionReconciler filters out sessions with unknown status", async (t) => {
  const rootClaude = "/workspace/project-a";
  const { sentMessages, sessionIndex, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    discoverSessionsFn: async () => [
      createSession("provider-completed", rootClaude, "2026-01-03T00:00:00.000Z", "completed"),
      createSession("provider-unknown", rootClaude, "2026-01-04T00:00:00.000Z", "unknown")
    ]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 1, skipped: 0 });
  assert.deepEqual(sentMessages, [
    {
      type: "report_local_sessions",
      host_id: "macbook-1",
      sessions: [
        {
          provider_session_id: "provider-completed",
          provider: "claude",
          cwd: rootClaude,
          created_at: "2026-01-03T00:00:00.000Z"
        }
      ]
    }
  ]);
  assert.deepEqual(sessionIndex.get("local:provider-completed"), {
    provider_session_id: "provider-completed",
    cwd: rootClaude,
    provider: "claude",
    created_at: "2026-01-03T00:00:00.000Z",
    source: "local"
  });
  assert.equal(sessionIndex.get("local:provider-unknown"), null);
});

test("SessionReconciler does not scan or send when workspace roots are empty", async (t) => {
  const { discoverCalls, sentMessages, reconciler } = createHarness(t);

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 0, skipped: 0 });
  assert.equal(discoverCalls.length, 0);
  assert.equal(sentMessages.length, 0);
});

test("SessionReconciler continues after a single root scan failure", async (t) => {
  const rootClaude = "/workspace/project-a";
  const rootBook = "/workspace/project-b";
  const { sentMessages, discoverCalls, reconciler } = createHarness(t, {
    roots: [
      createRoot("claude", rootClaude),
      createRoot("book", rootBook)
    ],
    discoverSessionsFn: async (cwd, provider) => {
      if (provider === "claude") {
        throw new Error(`boom:${cwd}`);
      }

      return [createSession("provider-book-1", rootBook, "2026-01-02T00:00:00.000Z")];
    }
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 1, skipped: 0 });
  assert.equal(discoverCalls.length, 2);
  assert.deepEqual(sentMessages[0], {
    type: "report_local_sessions",
    host_id: "macbook-1",
    sessions: [
      {
        provider_session_id: "provider-book-1",
        provider: "book",
        cwd: rootBook,
        created_at: "2026-01-02T00:00:00.000Z"
      }
    ]
  });
});

test("SessionReconciler skips sending when the diff is empty", async (t) => {
  const rootClaude = "/workspace/project-a";
  const { sentMessages, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    indexedEntries: [
      {
        relay_session_id: "relay-known",
        entry: {
          provider_session_id: "provider-known",
          cwd: rootClaude,
          provider: "claude",
          created_at: "2026-01-01T00:00:00.000Z",
          source: "remote",
          initial_prompt: null
        }
      }
    ],
    discoverSessionsFn: async () => [createSession("provider-known", rootClaude, "2026-01-02T00:00:00.000Z")]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 0, skipped: 1 });
  assert.equal(sentMessages.length, 0);
});

test("SessionReconciler guards concurrent runs", async (t) => {
  const rootClaude = "/workspace/project-a";
  let releaseScan;
  const scanStarted = new Promise((resolve) => {
    releaseScan = resolve;
  });
  let finishScan;
  const scanFinished = new Promise((resolve) => {
    finishScan = resolve;
  });

  const { sentMessages, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    discoverSessionsFn: async () => {
      releaseScan();
      await scanFinished;
      return [createSession("provider-new", rootClaude, "2026-01-03T00:00:00.000Z")];
    }
  });

  const firstRun = reconciler.reconcile();
  await scanStarted;
  const secondRun = await reconciler.reconcile();
  finishScan();
  const firstResult = await firstRun;

  assert.deepEqual(secondRun, { reported: 0, skipped: 0 });
  assert.deepEqual(firstResult, { reported: 1, skipped: 0 });
  assert.equal(sentMessages.length, 1);
});

test("SessionReconciler persists reported local sessions into SessionIndex", async (t) => {
  const rootBook = "/workspace/project-b";
  const { sessionIndexPath, sessionIndex, reconciler } = createHarness(t, {
    roots: [createRoot("book", rootBook)],
    discoverSessionsFn: async () => [createSession("provider-book-1", rootBook, "2026-01-02T00:00:00.000Z")]
  });

  await reconciler.reconcile();

  assert.deepEqual(sessionIndex.get("local:provider-book-1"), {
    provider_session_id: "provider-book-1",
    cwd: rootBook,
    provider: "book",
    created_at: "2026-01-02T00:00:00.000Z",
    source: "local"
  });
  const persisted = JSON.parse(readFileSync(sessionIndexPath, "utf8"));
  assert.deepEqual(persisted["local:provider-book-1"], {
    provider_session_id: "provider-book-1",
    cwd: rootBook,
    provider: "book",
    created_at: "2026-01-02T00:00:00.000Z",
    source: "local"
  });
});

test("SessionReconciler enforces the 200 session report limit", async (t) => {
  const rootClaude = "/workspace/project-a";
  const sessions = Array.from({ length: 250 }, (_, index) => {
    const createdAt = new Date(Date.UTC(2026, 0, 1, 0, 0, index)).toISOString();
    return createSession(`provider-${index}`, rootClaude, createdAt);
  });
  const { sentMessages, sessionIndex, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    discoverSessionsFn: async () => sessions
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 200, skipped: 0 });
  assert.equal(sentMessages.length, 1);
  assert.equal(sentMessages[0].sessions.length, 200);
  assert.equal(sentMessages[0].sessions[0].provider_session_id, "provider-249");
  assert.equal(sentMessages[0].sessions.at(-1).provider_session_id, "provider-50");
  assert.equal(sessionIndex.get("local:provider-249").source, "local");
  assert.equal(sessionIndex.get("local:provider-49"), null);
});

test("SessionReconciler sends the expected report_local_sessions message structure", async (t) => {
  const rootClaude = "/workspace/project-a";
  const { sentMessages, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    hostId: "macbook-9",
    discoverSessionsFn: async () => [createSession("provider-1", rootClaude, "2026-01-03T00:00:00.000Z")]
  });

  await reconciler.reconcile();

  assert.deepEqual(sentMessages, [
    {
      type: "report_local_sessions",
      host_id: "macbook-9",
      sessions: [
        {
          provider_session_id: "provider-1",
          provider: "claude",
          cwd: rootClaude,
          created_at: "2026-01-03T00:00:00.000Z"
        }
      ]
    }
  ]);
});
