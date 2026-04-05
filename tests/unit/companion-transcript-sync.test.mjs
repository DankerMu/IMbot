import assert from "node:assert/strict";
import {
  appendFileSync,
  mkdirSync,
  mkdtempSync,
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
const { RuntimeUserMessageMirrorTracker } = require("../../packages/companion/dist/runtime/runtime-user-message-mirror-tracker.js");

const silentLogger = {
  debug() {},
  info() {},
  warn() {},
  error() {}
};

function encodeProjectPath(projectPath) {
  return path.resolve(projectPath).replace(/\//g, "-");
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
    consumeRuntimeUserMessageMirror: (providerSessionId, text, timestampMs) =>
      options.consumeRuntimeUserMessageMirror?.(providerSessionId, text, timestampMs) ?? false,
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
        '{"type":"assistant","timestamp":"2026-04-04T10:01:02.000Z","modelUsage":{"glm-5":{"contextWindow":200000}},"message":{"role":"assistant","model":"glm-5","content":[{"type":"text","text":"new answer"}],"usage":{"input_tokens":42,"output_tokens":9,"cache_read_input_tokens":7}}}'
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
            context_window: 200000,
            model: "glm-5"
          }
        }
      ]
    );
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer skips leading untimestamped transcript rows until it reaches the cutoff", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-untimestamped-cutoff-"));

  try {
    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T10:00:30.000Z"
    });
    createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      [
        '{"type":"user","message":{"role":"user","content":"old untimestamped user"}}',
        '{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"old untimestamped answer"}],"usage":{"input_tokens":12,"output_tokens":4}}}',
        '{"type":"user","timestamp":"2026-04-04T10:01:00.000Z","message":{"role":"user","content":"new user"}}',
        '{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"new untimestamped answer"}],"usage":{"input_tokens":42,"output_tokens":9}}}'
      ].join("\n") + "\n"
    );

    await syncer.syncNow();

    assert.deepEqual(
      sentEvents.map((event) => ({
        type: event.event_type,
        payload: event.payload
      })),
      [
        {
          type: "user_message",
          payload: {
            text: "new user"
          }
        },
        {
          type: "assistant_message",
          payload: {
            text: "new untimestamped answer"
          }
        },
        {
          type: "session_usage",
          payload: {
            input_tokens: 42,
            output_tokens: 9
          }
        }
      ]
    );
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer suppresses transcript user rows that mirror runtime-emitted user messages", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-runtime-mirror-"));

  try {
    let consumedMirrorCount = 0;
    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T09:00:00.000Z",
      consumeRuntimeUserMessageMirror: (_providerSessionId, text) => {
        if (text !== "repeat after me") {
          return false;
        }

        consumedMirrorCount += 1;
        return true;
      }
    });
    createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      [
        '{"type":"user","timestamp":"2026-04-04T10:01:00.000Z","message":{"role":"user","content":"repeat after me"}}',
        '{"type":"assistant","timestamp":"2026-04-04T10:01:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"ack"}],"usage":{"input_tokens":12,"output_tokens":4}}}'
      ].join("\n") + "\n"
    );

    await syncer.syncNow();

    assert.equal(consumedMirrorCount, 1);
    assert.deepEqual(
      sentEvents.map((event) => event.event_type),
      ["assistant_message", "session_usage"]
    );
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer still suppresses a mirrored user row after the runtime has already exited", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-runtime-close-mirror-"));

  try {
    let nowMs = Date.parse("2026-04-04T10:01:00.000Z");
    const tracker = new RuntimeUserMessageMirrorTracker(5 * 60 * 1000, () => nowMs);
    tracker.record("provider-session-1", "repeat after me");

    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T09:00:00.000Z",
      active: false,
      consumeRuntimeUserMessageMirror: (providerSessionId, text, timestampMs) =>
        tracker.consume(providerSessionId, text, timestampMs)
    });
    createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      '{"type":"user","message":{"role":"user","content":"repeat after me"}}\n'
    );

    nowMs += 15_000;
    await syncer.syncNow();

    assert.deepEqual(sentEvents, []);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer does not suppress earlier same-text transcript rows before the pending runtime mirror", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-mixed-origin-user-text-"));

  try {
    let nowMs = Date.parse("2026-04-04T10:01:30.000Z");
    const tracker = new RuntimeUserMessageMirrorTracker(5 * 60 * 1000, () => nowMs);
    tracker.record("provider-session-1", "same text");

    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T09:00:00.000Z",
      consumeRuntimeUserMessageMirror: (providerSessionId, text, timestampMs) =>
        tracker.consume(providerSessionId, text, timestampMs)
    });
    createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      [
        '{"type":"user","timestamp":"2026-04-04T10:01:00.000Z","message":{"role":"user","content":"same text"}}',
        '{"type":"user","timestamp":"2026-04-04T10:01:31.000Z","message":{"role":"user","content":"same text"}}'
      ].join("\n") + "\n"
    );

    await syncer.syncNow();

    assert.deepEqual(
      sentEvents.map((event) => event.payload),
      [
        {
          text: "same text"
        }
      ]
    );
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer suppresses a later mirrored user row even when an older pending mirror remains queued", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-queued-mirror-order-"));

  try {
    let nowMs = Date.parse("2026-04-04T10:01:00.000Z");
    const tracker = new RuntimeUserMessageMirrorTracker(5 * 60 * 1000, () => nowMs);
    tracker.record("provider-session-1", "repeat after me");
    nowMs = Date.parse("2026-04-04T10:05:00.000Z");
    tracker.record("provider-session-1", "repeat after me");

    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T09:00:00.000Z",
      consumeRuntimeUserMessageMirror: (providerSessionId, text, timestampMs) =>
        tracker.consume(providerSessionId, text, timestampMs)
    });
    createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      '{"type":"user","timestamp":"2026-04-04T10:05:01.000Z","message":{"role":"user","content":"repeat after me"}}\n'
    );

    await syncer.syncNow();

    assert.deepEqual(sentEvents, []);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer still suppresses delayed untimestamped mirrored user rows within pending retention", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-delayed-untimestamped-mirror-"));

  try {
    let nowMs = Date.parse("2026-04-04T10:01:00.000Z");
    const tracker = new RuntimeUserMessageMirrorTracker(5 * 60 * 1000, () => nowMs);
    tracker.record("provider-session-1", "repeat after me");

    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T09:00:00.000Z",
      active: false,
      consumeRuntimeUserMessageMirror: (providerSessionId, text, timestampMs) =>
        tracker.consume(providerSessionId, text, timestampMs)
    });
    createTranscriptFile(
      projectsDir,
      cwd,
      "provider-session-1",
      '{"type":"user","message":{"role":"user","content":"repeat after me"}}\n'
    );

    nowMs += 3 * 60 * 1000;
    await syncer.syncNow();

    assert.deepEqual(sentEvents, []);
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

test("TranscriptSyncer evicts cursors for sessions no longer in the index", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-evict-"));

  try {
    const sessionIndex = new companion.SessionIndex({
      filePath: path.join(tempDir, "sessions.json"),
      logger: silentLogger
    });
    const projectsDir = path.join(tempDir, ".claudebook", "projects");
    const cwd = path.join(tempDir, "novel", "evict-test");
    mkdirSync(cwd, { recursive: true });

    sessionIndex.set("relay-evict-1", {
      provider_session_id: "provider-evict-1",
      cwd,
      provider: "book",
      created_at: "2026-04-04T10:00:00.000Z",
      source: "remote",
      initial_prompt: null
    });
    sessionIndex.set("relay-evict-2", {
      provider_session_id: "provider-evict-2",
      cwd,
      provider: "book",
      created_at: "2026-04-04T10:00:01.000Z",
      source: "remote",
      initial_prompt: null
    });

    createTranscriptFile(
      projectsDir, cwd, "provider-evict-1",
      '{"type":"user","timestamp":"2026-04-04T10:01:00.000Z","message":{"role":"user","content":"a"}}\n'
    );
    createTranscriptFile(
      projectsDir, cwd, "provider-evict-2",
      '{"type":"user","timestamp":"2026-04-04T10:01:00.000Z","message":{"role":"user","content":"b"}}\n'
    );

    const syncer = new companion.TranscriptSyncer({
      sessionIndex,
      providers: { book: { binary: "book", configDir: path.join(tempDir, ".claudebook"), projectsDir } },
      relayUrl: "ws://127.0.0.1:3010",
      token: "test-token",
      logger: silentLogger,
      fetchSessionMetadata: async () => ({ last_active_at: "2026-04-04T09:00:00.000Z" }),
      sendEvent: () => {}
    });

    await syncer.syncNow();
    assert.equal(syncer.cursors.size, 2);

    sessionIndex.remove("relay-evict-1");
    await syncer.syncNow();
    assert.equal(syncer.cursors.size, 1);
    assert.equal(syncer.cursors.has("relay-evict-2"), true);
    assert.equal(syncer.cursors.has("relay-evict-1"), false);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});

test("TranscriptSyncer skips symlinked transcript files", async () => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), "imbot-transcript-sync-symlink-"));

  try {
    const { cwd, projectsDir, sentEvents, syncer } = createRuntimeHarness(tempDir, {
      lastActiveAt: "2026-04-04T09:00:00.000Z"
    });

    const projectDir = path.join(projectsDir, encodeProjectPath(cwd));
    mkdirSync(projectDir, { recursive: true });

    const realFile = path.join(tempDir, "real-transcript.jsonl");
    writeFileSync(
      realFile,
      '{"type":"user","timestamp":"2026-04-04T10:01:00.000Z","message":{"role":"user","content":"symlink user"}}\n',
      "utf8"
    );
    symlinkSync(realFile, path.join(projectDir, "provider-session-1.jsonl"));

    await syncer.syncNow();
    assert.deepEqual(sentEvents, []);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});
