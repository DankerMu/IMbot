import type {
  CompanionReportLocalSessionsMessage,
  InteractiveProvider,
  LocalSessionInfo
} from "@imbot/wire";

import type { CompanionProviderConfig } from "../config";
import type { LoggerLike } from "../types";
import { ConfigManager } from "../workspace/config-manager";
import { discoverSessions } from "./session-discovery";
import { SessionIndex } from "./session-index";

const MAX_REPORTED_SESSIONS = 200;

export interface SessionReconcilerOptions {
  readonly sessionIndex: SessionIndex;
  readonly configManager: ConfigManager;
  readonly providers: Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>>;
  readonly sendMessage: (message: CompanionReportLocalSessionsMessage) => void;
  readonly hostId: string;
  readonly logger?: LoggerLike;
  readonly discoverSessionsFn?: typeof discoverSessions;
}

type DiscoveredSessionRecord = LocalSessionInfo & {
  readonly provider: InteractiveProvider;
};

export class SessionReconciler {
  private readonly logger: LoggerLike;
  private readonly discoverSessionsFn: typeof discoverSessions;
  private running = false;

  constructor(private readonly options: SessionReconcilerOptions) {
    this.logger = options.logger ?? console;
    this.discoverSessionsFn = options.discoverSessionsFn ?? discoverSessions;
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

    for (const root of roots) {
      const providerConfig = this.options.providers[root.provider];
      if (!providerConfig) {
        this.logger.warn?.(`Skipping session reconciliation for unconfigured provider ${root.provider}`);
        continue;
      }

      let discovered: LocalSessionInfo[];
      try {
        discovered = await this.discoverSessionsFn(root.path, root.provider, {
          claudeProjectsDir: providerConfig.projectsDir,
          logger: this.logger,
          limit: MAX_REPORTED_SESSIONS
        });
      } catch (error) {
        this.logger.warn?.(
          `Failed to reconcile local sessions for provider ${root.provider} at ${root.path}: ${formatError(error)}`
        );
        continue;
      }

      for (const session of discovered) {
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
          provider: root.provider
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

    for (const session of reportedSessions) {
      this.options.sessionIndex.set(`local:${session.provider_session_id}`, {
        provider_session_id: session.provider_session_id,
        cwd: session.cwd,
        provider: session.provider,
        created_at: session.created_at,
        source: "local"
      });
    }

    return {
      reported: reportedSessions.length,
      skipped
    };
  }
}

function formatError(error: unknown): string {
  return error instanceof Error ? error.message : "unknown error";
}
