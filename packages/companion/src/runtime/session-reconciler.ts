import type {
  CompanionReportLocalSessionsMessage,
  InteractiveProvider,
  LocalSessionInfo
} from "@imbot/wire";

import type { CompanionProviderConfig } from "../config";
import type { LoggerLike } from "../types";
import { ConfigManager } from "../workspace/config-manager";
import { discoverAllSessions } from "./session-discovery";
import { SessionIndex, type SessionIndexEntry } from "./session-index";

const MAX_REPORTED_SESSIONS = 200;

export interface SessionReconcilerOptions {
  readonly sessionIndex: SessionIndex;
  readonly configManager: ConfigManager;
  readonly providers: Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>>;
  readonly sendMessage: (message: CompanionReportLocalSessionsMessage) => void;
  readonly hostId: string;
  readonly logger?: LoggerLike;
  readonly discoverAllSessionsFn?: typeof discoverAllSessions;
}

type DiscoveredSessionRecord = LocalSessionInfo & {
  readonly provider: InteractiveProvider;
};

export class SessionReconciler {
  private readonly logger: LoggerLike;
  private readonly discoverAllSessionsFn: typeof discoverAllSessions;
  private running = false;

  constructor(private readonly options: SessionReconcilerOptions) {
    this.logger = options.logger ?? console;
    this.discoverAllSessionsFn = options.discoverAllSessionsFn ?? discoverAllSessions;
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
    const newSessions: DiscoveredSessionRecord[] = [];
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
          limit: MAX_REPORTED_SESSIONS,
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

        if (this.options.sessionIndex.hasProviderSessionId(session.provider_session_id)) {
          skipped += 1;
          continue;
        }

        if (pendingProviderSessionIds.has(session.provider_session_id)) {
          continue;
        }

        pendingProviderSessionIds.add(session.provider_session_id);
        newSessions.push({
          ...session,
          provider
        });
      }
    }

    if (newSessions.length === 0) {
      return { reported: 0, skipped };
    }

    newSessions.sort((left, right) => Date.parse(right.created_at) - Date.parse(left.created_at));
    const reportedSessions = newSessions.slice(0, MAX_REPORTED_SESSIONS);

    this.options.sendMessage({
      type: "report_local_sessions",
      host_id: this.options.hostId,
      sessions: reportedSessions.map((session) => ({
        provider_session_id: session.provider_session_id,
        provider: session.provider,
        cwd: session.cwd,
        created_at: session.created_at
      }))
    });

    const indexUpdates = new Map<string, SessionIndexEntry>();
    for (const session of reportedSessions) {
      indexUpdates.set(`local:${session.provider_session_id}`, {
        provider_session_id: session.provider_session_id,
        cwd: session.cwd,
        provider: session.provider,
        created_at: session.created_at,
        source: "local"
      });
    }
    this.options.sessionIndex.setMany(indexUpdates);

    return {
      reported: reportedSessions.length,
      skipped
    };
  }
}

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : "unknown error";
}
