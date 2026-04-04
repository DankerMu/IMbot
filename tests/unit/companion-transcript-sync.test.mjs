import assert from "node:assert/strict";
import {
  appendFileSync,
  mkdirSync,
  mkdtempSync,
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

function encodeProjectPath(projectPath) {
  return projectPath.replace(/\//g, "-");
}

function createTranscriptFile(projectsDir, cwd, sessionId, contents) {
  const projectDir = path.join(projectsDir, encodeProjectPath(cwd));
  mkdirSync(projectDir, { recursive: true });

  const transcriptPath = path.join(projectDir, `${sessionId}.jsonl`);
  writeFileSync(transcriptPath, contents, "utf8");
  return transcriptPath;
}

function createRuntimeHarness(tempDir, options = {}) {
  const sessionIndex = new companion.SessionIndex({
    filePath: path.join(tempDir, "sessions.json"),
    logger: silentLogger
  });
  const projectsDir = path.join(tempDir, ".claudebook", "projects");
  const cwd = path.join(tempDir, "novel", "session-1");
  mkdirSync(cwd, { recursive: true });

  sessionIndex.set("relay-session-1", {
    provider_session_id: "provider-session-1",
    cwd,
    provider: "book",
    created_at: "2026-04-04T10:00:00.000Z",
    source: "remote",
    initial_prompt: null
  });

  const sentEvents = [];
  const activeRef = {
    value: options.active ?? false
  };
  const lastActiveAtRef = {
    value: options.lastActiveAt ?? "2026-04-04T10:00:30.000Z"
  };
  const syncer = new companion.TranscriptSyncer({
    sessionIndex,
    providers: {
      book: {
        binary: "book",
        configDir: path.join(tempDir, ".claudebook"),
        projectsDir
      }
    },
    relayUrl: "ws://127.0.0.1:3010",
    token: "test-token",
    logger: silentLogger,
    fetchSessionMetadata: async () => ({
      last_active_at: lastActiveAtRef.value
    }),
    isProviderSessionActive: () => activeRef.value,
    sendEvent: (message) => {
      sentEvents.push(message);
    }
  });

  return {
    cwd,
    projectsDir,
    sentEvents,
    syncer,
    activeRef,
    lastActiveAtRef
  };
}

test("TranscriptSyncer only imports transcript entries newer than relay last_active_at", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-cutoff-"));

  try {
    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T10:00:30.000Z"
    });
    createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      [
        '{"type":"user","timestamp":"2026-04-04T10:00:05.000Z","message":{"role":"user","content":"old user"}}',
        '{"type":"assistant","timestamp":"2026-04-04T10:00:06.000Z","message":{"role":"assistant","content":[{"type":"text","text":"old answer"}],"usage":{"input_tokens":12,"output_tokens":4}}}',
        '{"type":"user","timestamp":"2026-04-04T10:01:00.000Z","message":{"role":"user","content":"new user"}}',
        '{"type":"assistant","timestamp":"2026-04-04T10:01:02.000Z","message":{"role":"assistant","model":"glm-5","content":[{"type":"text","text":"new answer"}],"usage":{"input_tokens":42,"output_tokens":9,"cache_read_input_tokens":7}}}'
      ].join("\n") + "\n"
    );

    await syncer.syncNow();

    assert.deepEqual(
      sentEvents.map((event) => ({
        source: event.source,
        type: event.event_type,
        payload: event.payload
      })),
      [
        {
          source: "transcript_sync",
          type: "user_message",
          payload: {
            text: "new user"
          }
        },
        {
          source: "transcript_sync",
          type: "assistant_message",
          payload: {
            text: "new answer"
          }
        },
        {
          source: "transcript_sync",
          type: "session_usage",
          payload: {
            input_tokens: 42,
            output_tokens: 9,
            cache_read_input_tokens: 7,
            model: "glm-5"
          }
        }
      ]
    );
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer only imports appended transcript lines after the initial scan", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-append-"));

  try {
    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T10:00:30.000Z"
    });
    const transcriptPath = createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      [
        '{"type":"user","timestamp":"2026-04-04T10:00:05.000Z","message":{"role":"user","content":"old user"}}',
        '{"type":"assistant","timestamp":"2026-04-04T10:00:06.000Z","message":{"role":"assistant","content":[{"type":"text","text":"old answer"}],"usage":{"input_tokens":12,"output_tokens":4}}}'
      ].join("\n") + "\n"
    );

    await syncer.syncNow();
    assert.deepEqual(sentEvents, []);

    appendFileSync(
      transcriptPath,
      [
        '{"type":"user","timestamp":"2026-04-04T10:02:00.000Z","message":{"role":"user","content":"followup"}}',
        '{"type":"assistant","timestamp":"2026-04-04T10:02:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"followup answer"}],"usage":{"input_tokens":16,"output_tokens":5}}}'
      ].join("\n") + "\n",
      "utf8"
    );

    await syncer.syncNow();

    assert.deepEqual(
      sentEvents.map((event) => event.event_type),
      ["user_message", "assistant_message", "session_usage"]
    );
    assert.deepEqual(sentEvents[0].payload, { text: "followup" });
    assert.deepEqual(sentEvents[1].payload, { text: "followup answer" });
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer rechecks relay last_active_at for active provider sessions", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-active-cutoff-"));

  try {
    const { cwd, projectsDir, sentEvents, syncer, lastActiveAtRef } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T10:02:05.000Z",
      active: true
    });
    const transcriptPath = createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      [
        '{"type":"user","timestamp":"2026-04-04T10:02:00.000Z","message":{"role":"user","content":"runtime user"}}',
        '{"type":"assistant","timestamp":"2026-04-04T10:02:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"runtime answer"}],"usage":{"input_tokens":16,"output_tokens":5}}}'
      ].join("\n") + "\n"
    );

    await syncer.syncNow();
    assert.deepEqual(sentEvents, []);

    appendFileSync(
      transcriptPath,
      [
        '{"type":"user","timestamp":"2026-04-04T10:03:00.000Z","message":{"role":"user","content":"external user"}}',
        '{"type":"assistant","timestamp":"2026-04-04T10:03:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"external answer"}],"usage":{"input_tokens":18,"output_tokens":6}}}'
      ].join("\n") + "\n",
      "utf8"
    );

    lastActiveAtRef.value = "2026-04-04T10:02:05.000Z";
    await syncer.syncNow();

    assert.deepEqual(
      sentEvents.map((event) => event.event_type),
      ["user_message", "assistant_message", "session_usage"]
    );
    assert.deepEqual(sentEvents[0].payload, { text: "external user" });
    assert.deepEqual(sentEvents[1].payload, { text: "external answer" });
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});
