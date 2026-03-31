import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";

import type { SessionStatus } from "@imbot/wire";
import { cert, initializeApp, type App } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";

import type { RelayConfig } from "../config";
import type { RelayDatabase } from "../db/init";

type LoggerLike = {
  readonly error: (...args: unknown[]) => void;
  readonly warn: (...args: unknown[]) => void;
};

type PushSubscriptionRow = {
  readonly fcm_token: string;
};

type SessionPromptRow = {
  readonly initial_prompt: string | null;
};

type ServiceAccountShape = Exclude<Parameters<typeof cert>[0], string>;

export class PushAdapter {
  private app: App | null = null;
  private enabled = false;

  constructor(
    private readonly config: RelayConfig,
    private readonly db: RelayDatabase,
    private readonly logger: LoggerLike
  ) {}

  async init(): Promise<void> {
    const serviceAccountSource = this.config.fcmServiceAccount?.trim() ?? "";
    if (!serviceAccountSource) {
      this.enabled = false;
      this.logger.warn("FCM push disabled: RELAY_FCM_SERVICE_ACCOUNT is not configured");
      return;
    }

    try {
      const serviceAccount = this.parseServiceAccount(serviceAccountSource);
      this.app = initializeApp(
        {
          credential: cert(serviceAccount),
          projectId: this.config.fcmProjectId ?? this.getServiceAccountProjectId(serviceAccount)
        },
        `imbot-relay-push-${randomUUID()}`
      );
      this.enabled = true;
    } catch (error) {
      this.enabled = false;
      this.app = null;
      this.logger.warn("FCM push disabled: failed to initialize firebase-admin");
      this.logger.error(error);
    }
  }

  async notify(
    sessionId: string,
    status: Extract<SessionStatus, "completed" | "failed">,
    errorMessage?: string
  ): Promise<void> {
    try {
      if (!this.enabled || !this.app) {
        return;
      }

      const session =
        (this.db.prepare("SELECT initial_prompt FROM sessions WHERE id = ?").get(sessionId) as
          | SessionPromptRow
          | undefined) ?? null;
      if (!session) {
        this.logger.warn(`Skipping push for unknown session ${sessionId}`);
        return;
      }

      const subscriptions = this.listSubscriptions();
      if (subscriptions.length === 0) {
        return;
      }

      const promptSummary = this.truncatePrompt(session.initial_prompt ?? "会话", 30);
      const notification =
        status === "completed"
          ? {
              title: `✓ ${promptSummary} 已完成`,
              body: ""
            }
          : {
              title: `✗ ${promptSummary} 失败`,
              body: errorMessage?.trim() || "未知错误"
            };

      await Promise.all(
        subscriptions.map(async ({ fcm_token: token }) => {
          await this.sendToToken(token, {
            token,
            notification,
            data: {
              action: "open_session",
              session_id: sessionId
            }
          });
        })
      );
    } catch (error) {
      this.logger.error(error);
    }
  }

  async notifyHostOffline(hostId: string, hostName: string): Promise<void> {
    try {
      if (!this.enabled || !this.app) {
        return;
      }

      const subscriptions = this.listSubscriptions();
      if (subscriptions.length === 0) {
        return;
      }

      await Promise.all(
        subscriptions.map(async ({ fcm_token: token }) => {
          await this.sendToToken(token, {
            token,
            notification: {
              title: `${hostName} 已离线`,
              body: "设备连接中断"
            },
            data: {
              action: "open_home",
              host_id: hostId
            }
          });
        })
      );
    } catch (error) {
      this.logger.error(error);
    }
  }

  private truncatePrompt(text: string, maxLen: number): string {
    const normalizedText = text.trim() || "会话";
    if (maxLen <= 0) {
      return "...";
    }

    if (normalizedText.length <= maxLen) {
      return normalizedText;
    }

    return `${normalizedText.slice(0, maxLen)}...`;
  }

  private parseServiceAccount(rawValue: string): ServiceAccountShape {
    if (rawValue.startsWith("{")) {
      return JSON.parse(rawValue) as ServiceAccountShape;
    }

    const fileContents = readFileSync(rawValue, "utf8");
    return JSON.parse(fileContents) as ServiceAccountShape;
  }

  private getServiceAccountProjectId(serviceAccount: ServiceAccountShape): string | undefined {
    if (typeof serviceAccount.projectId === "string" && serviceAccount.projectId.trim()) {
      return serviceAccount.projectId;
    }

    const rawProjectId = (serviceAccount as Record<string, unknown>).project_id;
    return typeof rawProjectId === "string" && rawProjectId.trim() ? rawProjectId : undefined;
  }

  private listSubscriptions(): PushSubscriptionRow[] {
    return this.db
      .prepare(
        `
        SELECT fcm_token
        FROM push_subscriptions
        ORDER BY created_at ASC
        `
      )
      .all() as PushSubscriptionRow[];
  }

  private async sendToToken(
    token: string,
    message: {
      readonly token: string;
      readonly notification: {
        readonly title: string;
        readonly body: string;
      };
      readonly data: Record<string, string>;
    }
  ): Promise<void> {
    if (!this.app) {
      return;
    }

    try {
      await getMessaging(this.app).send(message);
    } catch (error) {
      if (this.isUnregisteredTokenError(error)) {
        this.db.prepare("DELETE FROM push_subscriptions WHERE fcm_token = ?").run(token);
        this.logger.warn(
          `Deleted stale FCM token after unregistered send failure: ${token.slice(0, 8)}***`
        );
        return;
      }

      this.logger.error(error);
    }
  }

  private isUnregisteredTokenError(error: unknown): boolean {
    if (!error || typeof error !== "object") {
      return false;
    }

    const code =
      "code" in error && typeof error.code === "string"
        ? error.code
        : "errorInfo" in error &&
            error.errorInfo &&
            typeof error.errorInfo === "object" &&
            "code" in error.errorInfo &&
            typeof error.errorInfo.code === "string"
          ? error.errorInfo.code
          : null;

    return code === "messaging/registration-token-not-registered";
  }
}
