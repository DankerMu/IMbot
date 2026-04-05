import { promises as fs } from "node:fs";
import path from "node:path";

import type { CompanionEventMessage, InteractiveProvider, SessionUsagePayload } from "@imbot/wire";

import type { CompanionProviderConfig } from "../config";
import type { LoggerLike } from "../types";
import type { SessionIndex, SessionIndexRecord } from "./session-index";

const DEFAULT_POLL_INTERVAL_MS = 1500;

type TranscriptCursor = {
  cutoffMs: number | null;
  offset: number;
  pendingBeforeCutoff: TranscriptLineMapping[];
  trailingFragment: string;
};

type TranscriptMappedEvent = {
  eventType: CompanionEventMessage["event_type"];
  payload: unknown;
};

type TranscriptLineMapping = {
  events: TranscriptMappedEvent[];
  timestampMs: number | null;
};

type RelaySessionMetadata = {
  last_active_at: string;
};

export interface TranscriptSyncerOptions {
  readonly sessionIndex: SessionIndex;
  readonly providers: Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>>;
  readonly relayUrl: string;
  readonly token: string;
  readonly sendEvent: (message: CompanionEventMessage) => Promise<void> | void;
  readonly consumeRuntimeUserMessageMirror?: (
    providerSessionId: string,
    text: string,
    timestampMs: number | null,
  ) => boolean;
  readonly isProviderSessionActive?: (providerSessionId: string) => boolean;
  readonly logger?: LoggerLike;
  readonly pollIntervalMs?: number;
  readonly fetchSessionMetadata?: (relaySessionId: string) => Promise<RelaySessionMetadata | null>;
}

export class TranscriptSyncer {
  private readonly logger: LoggerLike;
  private readonly pollIntervalMs: number;
  private readonly fetchSessionMetadata: (relaySessionId: string) => Promise<RelaySessionMetadata | null>;
  private readonly cursors = new Map<string, TranscriptCursor>();
  private intervalHandle: NodeJS.Timeout | null = null;
  private syncing = false;

  constructor(private readonly options: TranscriptSyncerOptions) {
    this.logger = options.logger ?? console;
    this.pollIntervalMs = options.pollIntervalMs ?? DEFAULT_POLL_INTERVAL_MS;
    this.fetchSessionMetadata = options.fetchSessionMetadata ?? ((relaySessionId) => this.fetchRelaySessionMetadata(relaySessionId));
  }

  start(): void {
    if (this.intervalHandle) {
      return;
    }

    this.intervalHandle = setInterval(() => {
      void this.syncNow().catch((error) => {
        this.logger.warn?.(`Transcript sync failed: ${getErrorMessage(error)}`);
      });
    }, this.pollIntervalMs);
    this.intervalHandle.unref?.();
  }

  stop(): void {
    if (!this.intervalHandle) {
      return;
    }

    clearInterval(this.intervalHandle);
    this.intervalHandle = null;
  }

  async syncNow(): Promise<void> {
    if (this.syncing) {
      return;
    }

    this.syncing = true;
    try {
      const entries = this.options.sessionIndex
        .list()
        .filter((entry) => entry.source === "remote")
        .sort((left, right) => Date.parse(right.created_at) - Date.parse(left.created_at));

      // Evict cursors for sessions no longer in the index
      const activeRelayIds = new Set(entries.map((entry) => entry.relay_session_id));
      for (const key of this.cursors.keys()) {
        if (!activeRelayIds.has(key)) {
          this.cursors.delete(key);
        }
      }

      for (const entry of entries) {
        await this.syncEntry(entry);
      }
    } finally {
      this.syncing = false;
    }
  }

  private async syncEntry(entry: SessionIndexRecord): Promise<void> {
    const providerSessionIsActive = this.options.isProviderSessionActive?.(entry.provider_session_id) ?? false;

    const providerConfig = this.options.providers[entry.provider];
    if (!providerConfig) {
      return;
    }

    const cursor = await this.getOrCreateCursor(entry);
    const transcriptPath = buildTranscriptPath(providerConfig.projectsDir, entry.cwd, entry.provider_session_id);

    let stat: Awaited<ReturnType<typeof fs.lstat>>;
    try {
      stat = await fs.lstat(transcriptPath);
    } catch (error) {
      if (isMissingPathError(error)) {
        return;
      }

      this.logger.warn?.(`Failed to stat transcript ${transcriptPath}: ${getErrorMessage(error)}`);
      return;
    }

    if (!stat.isFile() || stat.isSymbolicLink()) {
      return;
    }

    if (stat.size < cursor.offset) {
      cursor.offset = 0;
      cursor.pendingBeforeCutoff = [];
      cursor.trailingFragment = "";
      cursor.cutoffMs = await this.loadCutoffMs(entry.relay_session_id);
    }

    if (stat.size === cursor.offset && cursor.trailingFragment === "") {
      return;
    }

    // Active runtime sessions need a fresh relay cutoff on every delta scan so we can still
    // import turns appended by an external native CLI without duplicating turns already relayed
    // by the companion-managed runtime path.
    const cutoffMs = providerSessionIsActive ? await this.loadCutoffMs(entry.relay_session_id) : cursor.cutoffMs;
    const scanningFromStart = cursor.offset === 0 && cursor.trailingFragment === "";
    const { text: nextChunk, bytesRead } = await readUtf8Delta(transcriptPath, cursor.offset, stat.size);
    cursor.offset += bytesRead;

    const merged = `${cursor.trailingFragment}${nextChunk}`;
    const hasTrailingNewline = merged.endsWith("\n") || merged.endsWith("\r");
    const lines = merged.split(/\r?\n/);
    cursor.trailingFragment = hasTrailingNewline ? "" : (lines.pop() ?? "");
    let cutoffSatisfied = cutoffMs == null || (!scanningFromStart && cursor.pendingBeforeCutoff.length === 0);
    const pendingBeforeCutoff = cursor.pendingBeforeCutoff;
    let bufferedRowsOriginatedBeforeThisScan = pendingBeforeCutoff.length > 0;

    const emitMapping = async (mapping: TranscriptLineMapping): Promise<void> => {
      const shouldSuppressRuntimeMirror =
        mapping.events.length === 1 &&
        mapping.events[0].eventType === "user_message" &&
        typeof mapping.events[0].payload === "object" &&
        mapping.events[0].payload !== null &&
        "text" in mapping.events[0].payload &&
        typeof mapping.events[0].payload.text === "string" &&
        this.options.consumeRuntimeUserMessageMirror?.(
          entry.provider_session_id,
          mapping.events[0].payload.text,
          mapping.timestampMs,
        ) === true;
      if (shouldSuppressRuntimeMirror) {
        return;
      }

      for (const event of mapping.events) {
        await Promise.resolve(
          this.options.sendEvent({
            type: "event",
            session_id: entry.relay_session_id,
            event_type: event.eventType,
            payload: event.payload,
            source: "transcript_sync"
          })
        );
      }
    };

    for (const line of lines) {
      const mapping = mapTranscriptLine(line);
      if (mapping.events.length === 0) {
        continue;
      }

      if (!cutoffSatisfied && cutoffMs != null) {
        if (mapping.timestampMs == null) {
          pendingBeforeCutoff.push(mapping);
          continue;
        }

        if (mapping.timestampMs <= cutoffMs) {
          bufferedRowsOriginatedBeforeThisScan = false;
          pendingBeforeCutoff.length = 0;
          continue;
        }

        cutoffSatisfied = true;
        if (bufferedRowsOriginatedBeforeThisScan) {
          while (pendingBeforeCutoff.length > 0) {
            const buffered = pendingBeforeCutoff.shift();
            if (buffered) {
              await emitMapping(buffered);
            }
          }
        }
        pendingBeforeCutoff.length = 0;
      }

      await emitMapping(mapping);
    }

    if (!cutoffSatisfied && cutoffMs != null) {
      cursor.pendingBeforeCutoff = pendingBeforeCutoff;
    } else {
      cursor.pendingBeforeCutoff = [];
    }

    if (!providerSessionIsActive && cursor.pendingBeforeCutoff.length === 0) {
      cursor.cutoffMs = null;
    }
  }

  private async getOrCreateCursor(entry: SessionIndexRecord): Promise<TranscriptCursor> {
    const existing = this.cursors.get(entry.relay_session_id);
    if (existing) {
      return existing;
    }

    const cursor: TranscriptCursor = {
      cutoffMs: await this.loadCutoffMs(entry.relay_session_id),
      offset: 0,
      pendingBeforeCutoff: [],
      trailingFragment: ""
    };
    this.cursors.set(entry.relay_session_id, cursor);
    return cursor;
  }

  private async loadCutoffMs(relaySessionId: string): Promise<number | null> {
    const metadata = await this.fetchSessionMetadata(relaySessionId);
    return parseTimestampMs(metadata?.last_active_at ?? null);
  }

  private async fetchRelaySessionMetadata(relaySessionId: string): Promise<RelaySessionMetadata | null> {
    const relayBaseUrl = toHttpBaseUrl(this.options.relayUrl);
    const sessionUrl = new URL(`/v1/sessions/${encodeURIComponent(relaySessionId)}`, relayBaseUrl);
    const response = await fetch(sessionUrl, {
      headers: {
        authorization: `Bearer ${this.options.token}`
      }
    });

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw new Error(`relay session lookup failed (${response.status})`);
    }

    const payload = (await response.json()) as Record<string, unknown>;
    const lastActiveAt = typeof payload.last_active_at === "string" ? payload.last_active_at : null;
    return lastActiveAt ? { last_active_at: lastActiveAt } : null;
  }
}

async function readUtf8Delta(transcriptPath: string, fromOffset: number, toOffset: number): Promise<{ text: string; bytesRead: number }> {
  if (toOffset <= fromOffset) {
    return { text: "", bytesRead: 0 };
  }

  const handle = await fs.open(transcriptPath, "r");
  try {
    const length = toOffset - fromOffset;
    const buffer = Buffer.alloc(length);
    const { bytesRead } = await handle.read(buffer, 0, length, fromOffset);
    return { text: buffer.toString("utf8", 0, bytesRead), bytesRead };
  } finally {
    await handle.close();
  }
}

function mapTranscriptLine(line: string): TranscriptLineMapping {
  const trimmed = line.trim();
  if (trimmed === "") {
    return { events: [], timestampMs: null };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed) as unknown;
  } catch {
    return { events: [], timestampMs: null };
  }

  const record = asRecord(parsed);
  if (!record) {
    return { events: [], timestampMs: null };
  }

  const timestampMs = parseTimestampMs(getString(record.timestamp));

  const type = getString(record.type);
  if (type === "user") {
    const text = extractTranscriptText(record);
    return {
      events: text ? [{ eventType: "user_message", payload: { text } }] : [],
      timestampMs
    };
  }

  if (type === "assistant") {
    const text = extractTranscriptText(record);
    if (!text) {
      return { events: [], timestampMs };
    }

    const events: TranscriptMappedEvent[] = [
      {
        eventType: "assistant_message",
        payload: {
          text
        }
      }
    ];

    const usagePayload = extractUsagePayload(record);
    if (usagePayload) {
      events.push({
        eventType: "session_usage",
        payload: usagePayload
      });
    }

    return { events, timestampMs };
  }

  return { events: [], timestampMs };
}

function extractUsagePayload(record: Record<string, unknown>): SessionUsagePayload | null {
  const message = asRecord(record.message);
  const usage = asRecord(message?.usage);
  if (!usage) {
    return null;
  }

  const outputTokens = getNumber(usage.output_tokens);
  if (outputTokens == null || outputTokens <= 0) {
    return null;
  }

  const inputTokens = getNumber(usage.input_tokens) ?? 0;
  const [modelName, modelUsageEntry] = firstRecordEntry(
    asRecord(record.modelUsage) ?? asRecord(message?.modelUsage)
  );
  const modelUsage = asRecord(modelUsageEntry);
  const model = modelName ?? getString(message?.model) ?? undefined;
  return {
    input_tokens: inputTokens,
    output_tokens: outputTokens,
    ...(hasNumber(usage.cache_creation_input_tokens)
      ? { cache_creation_input_tokens: getNumber(usage.cache_creation_input_tokens) }
      : {}),
    ...(hasNumber(usage.cache_read_input_tokens)
      ? { cache_read_input_tokens: getNumber(usage.cache_read_input_tokens) }
      : {}),
    ...(hasNumber(modelUsage?.contextWindow) ? { context_window: getNumber(modelUsage?.contextWindow) } : {}),
    ...(model ? { model } : {})
  };
}

function firstRecordEntry(
  value: Record<string, unknown> | null
): [string | undefined, unknown] {
  if (!value) {
    return [undefined, undefined];
  }

  const [key, entry] = Object.entries(value)[0] ?? [];
  return [key, entry];
}

function extractTranscriptText(record: Record<string, unknown>): string | null {
  return (
    getString(record.text) ??
    getString(record.content) ??
    extractStructuredMessageText(record.message)
  );
}

function extractStructuredMessageText(value: unknown): string | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const content = (value as Record<string, unknown>).content;
  if (typeof content === "string") {
    return content;
  }

  if (!Array.isArray(content)) {
    return null;
  }

  const text = content
    .flatMap((item) => {
      if (!isPlainObject(item)) {
        return [];
      }

      const record = item as Record<string, unknown>;
      if (record.type !== "text") {
        return [];
      }

      const chunk = getString(record.text);
      return chunk ? [chunk] : [];
    })
    .join("");

  return text !== "" ? text : null;
}

function buildTranscriptPath(projectsDir: string, cwd: string, providerSessionId: string): string {
  const sanitizedId = providerSessionId.replace(/[/\\]/g, "_").replace(/\.\./g, "_");
  return path.join(projectsDir, encodeProjectPath(cwd), `${sanitizedId}.jsonl`);
}

function encodeProjectPath(cwd: string): string {
  return path
    .resolve(cwd)
    .replace(/\\/g, "/")
    .replace(/\//g, "-");
}

function toHttpBaseUrl(relayUrl: string): string {
  const url = new URL(relayUrl);
  if (url.protocol === "ws:") {
    url.protocol = "http:";
  } else if (url.protocol === "wss:") {
    url.protocol = "https:";
  }
  return url.toString();
}

function parseTimestampMs(value: string | null): number | null {
  if (!value) {
    return null;
  }

  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }

  return value as Record<string, unknown>;
}

function isPlainObject(value: unknown): boolean {
  return value != null && typeof value === "object" && !Array.isArray(value);
}

function getString(value: unknown): string | null {
  return typeof value === "string" && value !== "" ? value : null;
}

function getNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function hasNumber(value: unknown): value is number {
  return getNumber(value) != null;
}

function isMissingPathError(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error != null &&
    "code" in error &&
    (error as { code?: unknown }).code === "ENOENT"
  );
}

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "unknown error";
}
