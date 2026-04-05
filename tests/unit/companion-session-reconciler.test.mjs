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

function omitReqId(message) {
  const { req_id, ...rest } = message;
  return rest;
}

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

function createSession(
  providerSessionId,
  cwd,
  createdAt,
  status = "completed",
  lastActiveAt = createdAt
) {
  return {
    provider_session_id: providerSessionId,
    cwd,
    created_at: createdAt,
    last_active_at: lastActiveAt,
    status
  };
}

function createHarness(t, options = {}) {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-reconciler-"));
  const sessionIndexPath = path.join(tempDir, "sessions.json");
  const configPath = path.join(tempDir, "companion.json");
  const providers = options.providers ?? createProviderConfigs(tempDir);
  const roots = options.roots ?? [];
  const warnings = [];
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
  const logger = {
    ...silentLogger,
    warn(message) {
      warnings.push(String(message));
      options.onWarn?.(String(message));
    }
  };

  const reconciler = new companion.SessionReconciler({
    sessionIndex,
    configManager,
    providers,
    hostId: options.hostId ?? "macbook-1",
    maxReportedSessions: options.maxReportedSessions,
    logger,
    sendMessage: async (message) => {
      sentMessages.push(message);
      options.onSendMessage?.(message);
      if (options.sendMessage) {
        return await options.sendMessage(message);
      }
    },
    discoverAllSessionsFn: async (provider, discoverOptions) => {
      discoverCalls.push({
        provider,
        options: discoverOptions
      });

      if (options.discoverAllSessionsFn) {
        return await options.discoverAllSessionsFn(provider, discoverOptions);
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
    warnings,
    reconciler
  };
}

test("SessionReconciler deduplicates discovery per provider", async (t) => {
  const { providers, discoverCalls, sentMessages, reconciler } = createHarness(t, {
    roots: [
      createRoot("book", "/workspace/project-a"),
      createRoot("book", "/workspace/project-b")
    ]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 0, skipped: 0 });
  assert.equal(sentMessages.length, 0);
  assert.deepEqual(
    discoverCalls.map((call) => ({
        provider: call.provider,
        claudeProjectsDir: call.options.claudeProjectsDir,
        limit: call.options.limit
      })),
    [
      {
        provider: "book",
        claudeProjectsDir: providers.book.projectsDir,
        limit: 10
      }
    ]
  );
});

test("SessionReconciler calls discovery once per distinct provider", async (t) => {
  const { providers, discoverCalls, sentMessages, reconciler } = createHarness(t, {
    roots: [
      createRoot("claude", "/workspace/project-a"),
      createRoot("book", "/workspace/project-b")
    ]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 0, skipped: 0 });
  assert.equal(sentMessages.length, 0);
  assert.deepEqual(
    discoverCalls.map((call) => ({
        provider: call.provider,
        claudeProjectsDir: call.options.claudeProjectsDir,
        limit: call.options.limit
      })),
    [
      {
        provider: "claude",
        claudeProjectsDir: providers.claude.projectsDir,
        limit: 10
      },
      {
        provider: "book",
        claudeProjectsDir: providers.book.projectsDir,
        limit: 10
      }
    ]
  );
});

test("SessionReconciler skips sessions already in index", async (t) => {
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
    discoverAllSessionsFn: async () => [
      createSession("provider-known", rootClaude, "2026-01-02T00:00:00.000Z"),
      createSession("provider-new", rootClaude, "2026-01-03T00:00:00.000Z")
    ]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 1, skipped: 1 });
  assert.deepEqual(sentMessages.map(omitReqId), [
    {
      type: "report_local_sessions",
      host_id: "macbook-1",
      sessions: [
        {
          provider_session_id: "provider-new",
          provider: "claude",
          cwd: rootClaude,
          created_at: "2026-01-03T00:00:00.000Z",
          last_active_at: "2026-01-03T00:00:00.000Z"
        }
      ]
    }
  ]);
  assert.deepEqual(sessionIndex.get("local:provider-new"), {
    provider_session_id: "provider-new",
    cwd: rootClaude,
    provider: "claude",
    created_at: "2026-01-03T00:00:00.000Z",
    last_observed_at: "2026-01-03T00:00:00.000Z",
    source: "local"
  });
});

test("SessionReconciler skips non-completed sessions", async (t) => {
  const rootClaude = "/workspace/project-a";
  const { sentMessages, sessionIndex, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    discoverAllSessionsFn: async () => [
      createSession("provider-completed", rootClaude, "2026-01-03T00:00:00.000Z", "completed"),
      createSession("provider-unknown", rootClaude, "2026-01-04T00:00:00.000Z", "unknown")
    ]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 1, skipped: 0 });
  assert.deepEqual(sentMessages.map(omitReqId), [
    {
      type: "report_local_sessions",
      host_id: "macbook-1",
      sessions: [
        {
          provider_session_id: "provider-completed",
          provider: "claude",
          cwd: rootClaude,
          created_at: "2026-01-03T00:00:00.000Z",
          last_active_at: "2026-01-03T00:00:00.000Z"
        }
      ]
    }
  ]);
  assert.deepEqual(sessionIndex.get("local:provider-completed"), {
    provider_session_id: "provider-completed",
    cwd: rootClaude,
    provider: "claude",
    created_at: "2026-01-03T00:00:00.000Z",
    last_observed_at: "2026-01-03T00:00:00.000Z",
    source: "local"
  });
  assert.equal(sessionIndex.get("local:provider-unknown"), null);
});

test("SessionReconciler reports new completed sessions", async (t) => {
  const rootClaude = "/workspace/project-a";
  const { sentMessages, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    hostId: "macbook-9",
    discoverAllSessionsFn: async () => [createSession("provider-1", rootClaude, "2026-01-03T00:00:00.000Z")]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 1, skipped: 0 });
  assert.deepEqual(sentMessages.map(omitReqId), [
    {
      type: "report_local_sessions",
      host_id: "macbook-9",
      sessions: [
        {
          provider_session_id: "provider-1",
          provider: "claude",
          cwd: rootClaude,
          created_at: "2026-01-03T00:00:00.000Z",
          last_active_at: "2026-01-03T00:00:00.000Z"
        }
      ]
    }
  ]);
});

test("SessionReconciler does not scan or send when workspace roots are empty", async (t) => {
  const { discoverCalls, sentMessages, reconciler } = createHarness(t);

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 0, skipped: 0 });
  assert.equal(discoverCalls.length, 0);
  assert.equal(sentMessages.length, 0);
});

test("SessionReconciler continues after a single provider scan failure", async (t) => {
  const rootBook = "/workspace/project-b";
  const { sentMessages, discoverCalls, warnings, reconciler } = createHarness(t, {
    roots: [
      createRoot("claude", "/workspace/project-a"),
      createRoot("book", rootBook)
    ],
    discoverAllSessionsFn: async (provider) => {
      if (provider === "claude") {
        throw new Error("boom");
      }

      return [createSession("provider-book-1", rootBook, "2026-01-02T00:00:00.000Z")];
    }
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 1, skipped: 0 });
  assert.deepEqual(
    discoverCalls.map((call) => call.provider),
    ["claude", "book"]
  );
  assert.equal(
    warnings.some((warning) => warning.includes("Failed to reconcile local sessions for provider claude: boom")),
    true
  );
  assert.deepEqual(omitReqId(sentMessages[0]), {
    type: "report_local_sessions",
    host_id: "macbook-1",
    sessions: [
      {
        provider_session_id: "provider-book-1",
        provider: "book",
        cwd: rootBook,
        created_at: "2026-01-02T00:00:00.000Z",
        last_active_at: "2026-01-02T00:00:00.000Z"
      }
    ]
  });
});

test("SessionReconciler re-reports indexed sessions when local activity becomes fresher", async (t) => {
  const rootClaude = "/workspace/project-a";
  const lastObservedAt = "2026-01-01T00:00:00.000Z";
  const recentLastActiveAt = new Date(Date.now() + 60_000).toISOString();
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
          last_observed_at: lastObservedAt,
          source: "remote",
          initial_prompt: "known prompt"
        }
      }
    ],
    discoverAllSessionsFn: async () => [
      createSession("provider-known", rootClaude, "2026-01-01T00:00:00.000Z", "completed", recentLastActiveAt)
    ]
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 1, skipped: 0 });
  assert.deepEqual(sentMessages.map(omitReqId), [
    {
      type: "report_local_sessions",
      host_id: "macbook-1",
      sessions: [
        {
          provider_session_id: "provider-known",
          provider: "claude",
          cwd: rootClaude,
          created_at: "2026-01-01T00:00:00.000Z",
          last_active_at: recentLastActiveAt
        }
      ]
    }
  ]);
  assert.deepEqual(sessionIndex.get("relay-known"), {
    provider_session_id: "provider-known",
    cwd: rootClaude,
    provider: "claude",
    created_at: "2026-01-01T00:00:00.000Z",
    last_observed_at: recentLastActiveAt,
    source: "remote",
    initial_prompt: "known prompt"
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
    discoverAllSessionsFn: async () => [createSession("provider-known", rootClaude, "2026-01-02T00:00:00.000Z")]
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
    discoverAllSessionsFn: async () => {
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
    discoverAllSessionsFn: async () => [createSession("provider-book-1", rootBook, "2026-01-02T00:00:00.000Z")]
  });

  await reconciler.reconcile();

  assert.deepEqual(sessionIndex.get("local:provider-book-1"), {
    provider_session_id: "provider-book-1",
    cwd: rootBook,
    provider: "book",
    created_at: "2026-01-02T00:00:00.000Z",
    last_observed_at: "2026-01-02T00:00:00.000Z",
    source: "local"
  });
  const persisted = JSON.parse(readFileSync(sessionIndexPath, "utf8"));
  assert.deepEqual(persisted["local:provider-book-1"], {
    provider_session_id: "provider-book-1",
    cwd: rootBook,
    provider: "book",
    created_at: "2026-01-02T00:00:00.000Z",
    last_observed_at: "2026-01-02T00:00:00.000Z",
    source: "local"
  });
});

test("SessionReconciler marks promoted shadow sessions for initial transcript bootstrap", async (t) => {
  const rootBook = "/workspace/project-b";
  const { sessionIndex, reconciler } = createHarness(t, {
    roots: [createRoot("book", rootBook)],
    discoverAllSessionsFn: async () => [createSession("provider-book-1", rootBook, "2026-01-02T00:00:00.000Z")],
    sendMessage: async () => ({
      sessions: [
        {
          relay_session_id: "relay-book-1",
          provider_session_id: "provider-book-1",
          created_at: "2026-01-02T00:00:00.000Z",
          last_active_at: "2026-01-02T00:00:00.000Z",
          initial_prompt: null,
          has_transcript_events: false
        }
      ]
    })
  });

  await reconciler.reconcile();

  assert.equal(sessionIndex.get("local:provider-book-1"), null);
  assert.deepEqual(sessionIndex.get("relay-book-1"), {
    provider_session_id: "provider-book-1",
    cwd: rootBook,
    provider: "book",
    created_at: "2026-01-02T00:00:00.000Z",
    last_observed_at: "2026-01-02T00:00:00.000Z",
    source: "remote",
    initial_prompt: null,
    initial_transcript_sync_pending: true
  });
});

test("SessionReconciler bootstraps recent remote sessions once even when local activity is unchanged", async (t) => {
  const rootBook = "/workspace/project-b";
  const recentLastActiveAt = new Date().toISOString();
  const { sentMessages, reconciler } = createHarness(t, {
    roots: [createRoot("book", rootBook)],
    indexedEntries: [
      {
        relay_session_id: "relay-book-existing",
        entry: {
          provider_session_id: "provider-book-existing",
          cwd: rootBook,
          provider: "book",
          created_at: "2026-01-02T00:00:00.000Z",
          last_observed_at: recentLastActiveAt,
          source: "remote",
          initial_prompt: null
        }
      }
    ],
    discoverAllSessionsFn: async () => [
      createSession("provider-book-existing", rootBook, "2026-01-02T00:00:00.000Z", "completed", recentLastActiveAt)
    ]
  });

  const first = await reconciler.reconcile();
  const second = await reconciler.reconcile();

  assert.deepEqual(first, { reported: 1, skipped: 0 });
  assert.deepEqual(second, { reported: 0, skipped: 1 });
  assert.equal(sentMessages.length, 1);
  assert.deepEqual(omitReqId(sentMessages[0]), {
    type: "report_local_sessions",
    host_id: "macbook-1",
    sessions: [
      {
        provider_session_id: "provider-book-existing",
        provider: "book",
        cwd: rootBook,
        created_at: "2026-01-02T00:00:00.000Z",
        last_active_at: recentLastActiveAt
      }
    ]
  });
});

test("SessionReconciler enforces the default 10 session report limit", async (t) => {
  const rootClaude = "/workspace/project-a";
  const sessions = Array.from({ length: 250 }, (_, index) => {
    const createdAt = new Date(Date.UTC(2026, 0, 1, 0, 0, index)).toISOString();
    return createSession(`provider-${index}`, rootClaude, createdAt);
  });
  const { sentMessages, sessionIndex, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    discoverAllSessionsFn: async () => sessions
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 10, skipped: 0 });
  assert.equal(sentMessages.length, 1);
  assert.equal(sentMessages[0].sessions.length, 10);
  assert.equal(sentMessages[0].sessions[0].provider_session_id, "provider-249");
  assert.equal(sentMessages[0].sessions.at(-1).provider_session_id, "provider-240");
  assert.equal(sessionIndex.get("local:provider-249").source, "local");
  assert.equal(sessionIndex.get("local:provider-239"), null);
});

test("SessionReconciler honors an explicit maxReportedSessions override", async (t) => {
  const rootClaude = "/workspace/project-a";
  const sessions = Array.from({ length: 50 }, (_, index) => {
    const createdAt = new Date(Date.UTC(2026, 0, 1, 0, 0, index)).toISOString();
    return createSession(`provider-${index}`, rootClaude, createdAt);
  });
  const { sentMessages, reconciler } = createHarness(t, {
    roots: [createRoot("claude", rootClaude)],
    maxReportedSessions: 3,
    discoverAllSessionsFn: async () => sessions
  });

  const result = await reconciler.reconcile();

  assert.deepEqual(result, { reported: 3, skipped: 0 });
  assert.equal(sentMessages.length, 1);
  assert.equal(sentMessages[0].sessions.length, 3);
  assert.deepEqual(
    sentMessages[0].sessions.map((session) => session.provider_session_id),
    ["provider-49", "provider-48", "provider-47"]
  );
});
