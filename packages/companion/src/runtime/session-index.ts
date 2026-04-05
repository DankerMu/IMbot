import fs from "node:fs";
import path from "node:path";

import type { InteractiveProvider } from "@imbot/wire";

import { getDefaultSessionIndexPath } from "../config";
import type { LoggerLike } from "../types";

export interface SessionIndexEntry {
  readonly provider_session_id: string;
  readonly cwd: string;
  readonly provider: InteractiveProvider;
  readonly created_at: string;
  readonly last_observed_at?: string;
  readonly source?: "remote" | "local";
  readonly initial_prompt?: string | null;
  readonly initial_transcript_sync_pending?: boolean;
}

export interface SessionIndexRecord extends SessionIndexEntry {
  readonly relay_session_id: string;
}

export interface SessionIndexOptions {
  readonly filePath?: string;
  readonly logger?: LoggerLike;
}

export class SessionIndex {
  private readonly filePath: string;
  private readonly logger: LoggerLike;
  private readonly entries = new Map<string, SessionIndexEntry>();

  constructor(options: SessionIndexOptions = {}) {
    this.filePath = options.filePath ?? getDefaultSessionIndexPath();
    this.logger = options.logger ?? console;
    this.load();
  }

  load(): void {
    this.entries.clear();

    if (!fs.existsSync(this.filePath)) {
      return;
    }

    try {
      const rawText = fs.readFileSync(this.filePath, "utf8");
      const parsed = JSON.parse(rawText) as unknown;
      if (parsed == null || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("session index must be a JSON object");
      }

      for (const [relaySessionId, rawEntry] of Object.entries(parsed as Record<string, unknown>)) {
        const entry = normalizeEntry(rawEntry);
        if (!entry) {
          continue;
        }

        this.entries.set(relaySessionId, entry);
      }
    } catch (error) {
      this.logger.warn?.(
        `Failed to load session index ${this.filePath}; starting with an empty index: ${error instanceof Error ? error.message : "unknown error"}`
      );
      this.entries.clear();
    }
  }

  get(relaySessionId: string): SessionIndexEntry | null {
    const entry = this.entries.get(relaySessionId);
    return entry ? { ...entry } : null;
  }

  findByProviderSessionId(providerSessionId: string): SessionIndexRecord | null {
    for (const [relaySessionId, entry] of this.entries.entries()) {
      if (entry.provider_session_id !== providerSessionId) {
        continue;
      }

      return {
        relay_session_id: relaySessionId,
        ...entry
      };
    }

    return null;
  }

  hasProviderSessionId(providerSessionId: string): boolean {
    for (const entry of this.entries.values()) {
      if (entry.provider_session_id === providerSessionId) {
        return true;
      }
    }

    return false;
  }

  list(): SessionIndexRecord[] {
    return Array.from(this.entries.entries()).map(([relaySessionId, entry]) => ({
      relay_session_id: relaySessionId,
      ...entry
    }));
  }

  set(relaySessionId: string, entry: SessionIndexEntry): void {
    this.entries.set(relaySessionId, { ...entry });
    this.persist();
  }

  setMany(entries: ReadonlyMap<string, SessionIndexEntry>): void {
    for (const [relaySessionId, entry] of entries) {
      this.entries.set(relaySessionId, { ...entry });
    }

    if (entries.size > 0) {
      this.persist();
    }
  }

  remove(relaySessionId: string): void {
    if (!this.entries.delete(relaySessionId)) {
      return;
    }

    this.persist();
  }

  private persist(): void {
    fs.mkdirSync(path.dirname(this.filePath), {
      recursive: true
    });

    const tmpPath = `${this.filePath}.${process.pid}.tmp`;
    const document = Object.fromEntries(this.entries.entries());
    fs.writeFileSync(tmpPath, `${JSON.stringify(document, null, 2)}\n`, "utf8");
    fs.renameSync(tmpPath, this.filePath);
  }
}

function normalizeEntry(value: unknown): SessionIndexEntry | null {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }

  const record = value as Record<string, unknown>;
  const providerSessionId = typeof record.provider_session_id === "string" ? record.provider_session_id : null;
  const cwd = typeof record.cwd === "string" ? record.cwd : null;
  const provider = record.provider;
  const createdAt = typeof record.created_at === "string" ? record.created_at : null;
  const lastObservedAt =
    typeof record.last_observed_at === "string" ? record.last_observed_at : undefined;
  const source = record.source === "local" ? "local" : "remote";
  const initialPrompt = typeof record.initial_prompt === "string" ? record.initial_prompt : null;
  const initialTranscriptSyncPending =
    record.initial_transcript_sync_pending === true ? true : undefined;

  if (
    !providerSessionId ||
    !cwd ||
    !createdAt ||
    (provider !== "claude" && provider !== "book")
  ) {
    return null;
  }

  return {
    provider_session_id: providerSessionId,
    cwd,
    provider,
    created_at: createdAt,
    ...(lastObservedAt ? { last_observed_at: lastObservedAt } : {}),
    source,
    initial_prompt: initialPrompt,
    ...(initialTranscriptSyncPending ? { initial_transcript_sync_pending: true } : {})
  };
}
