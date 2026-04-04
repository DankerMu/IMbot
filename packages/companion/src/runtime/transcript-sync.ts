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
  trailingFragment: string;
};

type TranscriptMappedEvent = {
  eventType: CompanionEventMessage["event_type"];
  payload: unknown;
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

    let stat: Awaited<ReturnType<typeof fs.stat>>;
    try {
      stat = await fs.stat(transcriptPath);
    } catch (error) {
      if (isMissingPathError(error)) {
        return;
      }

      this.logger.warn?.(`Failed to stat transcript ${transcriptPath}: ${getErrorMessage(error)}`);
      return;
    }

    if (!stat.isFile()) {
      return;
    }

    if (stat.size < cursor.offset) {
      cursor.offset = 0;
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
    const nextChunk = await readUtf8Delta(transcriptPath, cursor.offset, stat.size);
    cursor.offset = stat.size;

    const merged = `${cursor.trailingFragment}${nextChunk}`;
    const hasTrailingNewline = merged.endsWith("\n") || merged.endsWith("\r");
    const lines = merged.split(/\r?\n/);
    cursor.trailingFragment = hasTrailingNewline ? "" : (lines.pop() ?? "");

    for (const line of lines) {
      const events = mapTranscriptLine(line, cutoffMs);
      if (events.length === 0) {
        continue;
      }

      for (const event of events) {
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
    }

    if (!providerSessionIsActive) {
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

async function readUtf8Delta(transcriptPath: string, fromOffset: number, toOffset: number): Promise<string> {
  if (toOffset <= fromOffset) {
    return "";
  }

  const handle = await fs.open(transcriptPath, "r");
  try {
    const length = toOffset - fromOffset;
    const buffer = Buffer.alloc(length);
    const { bytesRead } = await handle.read(buffer, 0, length, fromOffset);
    return buffer.toString("utf8", 0, bytesRead);
  } finally {
    await handle.close();
  }
}

function mapTranscriptLine(line: string, cutoffMs: number | null): TranscriptMappedEvent[] {
  const trimmed = line.trim();
  if (trimmed === "") {
    return [];
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed) as unknown;
  } catch {
    return [];
  }

  const record = asRecord(parsed);
  if (!record) {
    return [];
  }

  const timestampMs = parseTimestampMs(getString(record.timestamp));
  if (timestampMs != null && cutoffMs != null && timestampMs <= cutoffMs) {
    return [];
  }

  const type = getString(record.type);
  if (type === "user") {
    const text = extractTranscriptText(record);
    return text ? [{ eventType: "user_message", payload: { text } }] : [];
  }

  if (type === "assistant") {
    const text = extractTranscriptText(record);
    if (!text) {
      return [];
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

    return events;
  }

  return [];
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
  const model = getString(message?.model) ?? undefined;
  return {
    input_tokens: inputTokens,
    output_tokens: outputTokens,
    ...(hasNumber(usage.cache_creation_input_tokens)
      ? { cache_creation_input_tokens: getNumber(usage.cache_creation_input_tokens) }
      : {}),
    ...(hasNumber(usage.cache_read_input_tokens)
      ? { cache_read_input_tokens: getNumber(usage.cache_read_input_tokens) }
      : {}),
    ...(model ? { model } : {})
  };
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
  return path.join(projectsDir, encodeProjectPath(cwd), `${providerSessionId}.jsonl`);
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
