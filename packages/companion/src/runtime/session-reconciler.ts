import type {
  CompanionReportLocalSessionsMessage,
  InteractiveProvider,
  LocalSessionInfo
} from "@imbot/wire";
import { randomUUID } from "node:crypto";

import type { CompanionProviderConfig } from "../config";
import type { LoggerLike } from "../types";
import { ConfigManager } from "../workspace/config-manager";
import { discoverAllSessions } from "./session-discovery";
import { SessionIndex, type SessionIndexEntry } from "./session-index";

const DEFAULT_MAX_REPORTED_SESSIONS = 10;
const DEFAULT_RECENT_ACTIVITY_WINDOW_MS = 24 * 60 * 60 * 1000;

export interface SessionReconcilerOptions {
  readonly sessionIndex: SessionIndex;
  readonly configManager: ConfigManager;
  readonly providers: Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>>;
  readonly sendMessage: (
    message: CompanionReportLocalSessionsMessage
  ) => Promise<ReportLocalSessionsAckData | void> | ReportLocalSessionsAckData | void;
  readonly hostId: string;
  readonly maxReportedSessions?: number;
  readonly logger?: LoggerLike;
  readonly discoverAllSessionsFn?: typeof discoverAllSessions;
}

type DiscoveredSessionRecord = LocalSessionInfo & {
  readonly provider: InteractiveProvider;
};

type ReportLocalSessionsAckData = {
  readonly sessions?: Array<{
    readonly relay_session_id: string;
    readonly provider_session_id: string;
    readonly created_at?: string;
    readonly last_active_at?: string;
    readonly initial_prompt?: string | null;
    readonly has_transcript_events?: boolean;
  }>;
};

export class SessionReconciler {
  private readonly logger: LoggerLike;
  private readonly discoverAllSessionsFn: typeof discoverAllSessions;
  private readonly recentActivityWindowMs: number;
  private readonly maxReportedSessions: number;
  private readonly bootstrappedProviderSessionIds = new Set<string>();
  private running = false;

  constructor(private readonly options: SessionReconcilerOptions) {
    this.logger = options.logger ?? console;
    this.discoverAllSessionsFn = options.discoverAllSessionsFn ?? discoverAllSessions;
    this.recentActivityWindowMs = DEFAULT_RECENT_ACTIVITY_WINDOW_MS;
    this.maxReportedSessions = options.maxReportedSessions ?? DEFAULT_MAX_REPORTED_SESSIONS;
  }

  async reconcile(): Promise<{ reported: number; skipped: number }> {
    if (this.running) {
      return { reported: 0, skipped: 0 };
    }

    this.running = true;
    try {
      return await this.doReconcile();
    } finally {
      this.running = false;
    }
  }

  private async doReconcile(): Promise<{ reported: number; skipped: number }> {
    const roots = this.options.configManager.getRoots();
    if (roots.length === 0) {
      return { reported: 0, skipped: 0 };
    }

    const pendingProviderSessionIds = new Set<string>();
    const sessionsToReport: DiscoveredSessionRecord[] = [];
    const indexUpdates = new Map<string, SessionIndexEntry>();
    let skipped = 0;
    const providers = new Set(roots.map((root) => root.provider));

    for (const provider of providers) {
      const providerConfig = this.options.providers[provider];
      if (!providerConfig) {
        this.logger.warn?.(`Skipping session reconciliation for unconfigured provider ${provider}`);
        continue;
      }

      const knownCwds = roots
        .filter((root) => root.provider === provider)
        .map((root) => root.path);

      let discovered: LocalSessionInfo[];
      try {
        discovered = await this.discoverAllSessionsFn(provider, {
          claudeProjectsDir: providerConfig.projectsDir,
          logger: this.logger,
          limit: this.maxReportedSessions,
          knownCwds
        });
      } catch (error) {
        this.logger.warn?.(
          `Failed to reconcile local sessions for provider ${provider}: ${formatError(error)}`
        );
        continue;
      }

      for (const session of discovered) {
        // Skip empty/unreadable JSONL files (status "unknown") — only report sessions
        // that have actual conversation data to avoid phantom shadow records in relay.
        if (session.status !== "completed") {
          continue;
        }

        if (pendingProviderSessionIds.has(session.provider_session_id)) {
          continue;
        }

        const indexed = this.options.sessionIndex.findByProviderSessionId(session.provider_session_id);
        const shouldBootstrapIndexedSession =
          indexed ? this.shouldBootstrapIndexedSession(indexed, session) : false;
        if (indexed && !shouldBootstrapIndexedSession && !this.shouldRefreshIndexedSession(indexed, session)) {
          skipped += 1;
          continue;
        }

        pendingProviderSessionIds.add(session.provider_session_id);
        const discoveredRecord = {
          ...session,
          provider
        };
        sessionsToReport.push(discoveredRecord);
        if (shouldBootstrapIndexedSession) {
          this.bootstrappedProviderSessionIds.add(session.provider_session_id);
        }

        if (indexed) {
          indexUpdates.set(indexed.relay_session_id, {
            provider_session_id: indexed.provider_session_id,
            cwd: discoveredRecord.cwd,
            provider: indexed.provider,
            created_at: indexed.created_at,
            last_observed_at: discoveredRecord.last_active_at,
            source: indexed.source,
            initial_prompt: indexed.initial_prompt
          });
          continue;
        }

        indexUpdates.set(`local:${discoveredRecord.provider_session_id}`, {
          provider_session_id: discoveredRecord.provider_session_id,
          cwd: discoveredRecord.cwd,
          provider: discoveredRecord.provider,
          created_at: discoveredRecord.created_at,
          last_observed_at: discoveredRecord.last_active_at,
          source: "local"
        });
      }
    }

    if (sessionsToReport.length === 0) {
      return { reported: 0, skipped };
    }

    sessionsToReport.sort(
      (left, right) => Date.parse(right.last_active_at) - Date.parse(left.last_active_at)
    );
    const reportedSessions = sessionsToReport.slice(0, this.maxReportedSessions);

    const reportedProviderSessionIds = new Set(
      reportedSessions.map((session) => session.provider_session_id)
    );
    const filteredIndexUpdates = new Map<string, SessionIndexEntry>();
    for (const [key, value] of indexUpdates) {
      if (reportedProviderSessionIds.has(value.provider_session_id)) {
        filteredIndexUpdates.set(key, value);
      }
    }
    this.options.sessionIndex.setMany(filteredIndexUpdates);

    let ackData: ReportLocalSessionsAckData | null = null;
    try {
      const reqId = randomUUID();
      ackData =
        (await Promise.resolve(
          this.options.sendMessage({
            type: "report_local_sessions",
            req_id: reqId,
            host_id: this.options.hostId,
            sessions: reportedSessions.map((session) => ({
              provider_session_id: session.provider_session_id,
              provider: session.provider,
              cwd: session.cwd,
              created_at: session.created_at,
              last_active_at: session.last_active_at
            }))
          })
        )) ?? null;
    } catch (error) {
      this.logger.warn?.(`Failed to promote local sessions from relay ack: ${formatError(error)}`);
    }

    this.promoteAckedSessions(ackData, reportedProviderSessionIds);

    return {
      reported: reportedSessions.length,
      skipped
    };
  }

  private promoteAckedSessions(
    ackData: ReportLocalSessionsAckData | null,
    reportedProviderSessionIds: ReadonlySet<string>
  ): void {
    const sessions = ackData?.sessions;
    if (!Array.isArray(sessions) || sessions.length === 0) {
      return;
    }

    for (const session of sessions) {
      if (!reportedProviderSessionIds.has(session.provider_session_id)) {
        continue;
      }

      const indexed = this.options.sessionIndex.findByProviderSessionId(session.provider_session_id);
      if (!indexed) {
        continue;
      }

      this.options.sessionIndex.set(session.relay_session_id, {
        provider_session_id: indexed.provider_session_id,
        cwd: indexed.cwd,
        provider: indexed.provider,
        created_at: session.created_at ?? indexed.created_at,
        ...(session.last_active_at || indexed.last_observed_at
          ? {
              last_observed_at: session.last_active_at ?? indexed.last_observed_at
            }
          : {}),
        source: "remote",
        initial_prompt: session.initial_prompt ?? indexed.initial_prompt ?? null,
        ...(session.has_transcript_events === false
          ? {
              initial_transcript_sync_pending: true
            }
          : {})
      });

      if (indexed.relay_session_id !== session.relay_session_id) {
        this.options.sessionIndex.remove(indexed.relay_session_id);
      }
    }
  }

  private shouldRefreshIndexedSession(indexed: SessionIndexEntry, session: LocalSessionInfo): boolean {
    const lastActiveMs = parseTimestampMs(session.last_active_at);
    if (lastActiveMs == null) {
      return false;
    }

    if (lastActiveMs < Date.now() - this.recentActivityWindowMs) {
      return false;
    }

    const lastObservedMs = parseTimestampMs(indexed.last_observed_at ?? indexed.created_at);
    return lastObservedMs == null || lastActiveMs > lastObservedMs;
  }

  private shouldBootstrapIndexedSession(indexed: SessionIndexEntry, session: LocalSessionInfo): boolean {
    if (indexed.source !== "remote") {
      return false;
    }

    if (this.bootstrappedProviderSessionIds.has(session.provider_session_id)) {
      return false;
    }

    const lastActiveMs = parseTimestampMs(session.last_active_at);
    if (lastActiveMs == null) {
      return false;
    }

    return lastActiveMs >= Date.now() - this.recentActivityWindowMs;
  }
}

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : "unknown error";
}

function parseTimestampMs(value: string | null | undefined): number | null {
  if (!value) {
    return null;
  }

  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}
