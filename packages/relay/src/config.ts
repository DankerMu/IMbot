import path from "node:path";

import dotenv from "dotenv";

import { RelayError } from "./errors";

export interface RelayConfig {
  readonly host: string;
  readonly port: number;
  readonly staticToken: string;
  readonly dbPath: string;
  readonly fcmProjectId: string | null;
  readonly fcmServiceAccount: string | null;
  readonly openClawUrl: string;
  readonly openClawToken: string | null;
  readonly logLevel: "debug" | "info" | "warn" | "error";
  readonly companionTimeoutMs: number;
  readonly heartbeatIntervalMs: number;
  readonly heartbeatStaleMs: number;
  readonly purgeDays: number;
  readonly wsPingIntervalMs: number;
}

function loadDotenvFiles(): void {
  const packageRoot = path.resolve(__dirname, "..");
  const repoRoot = path.resolve(packageRoot, "..", "..");

  dotenv.config({ path: path.join(packageRoot, ".env"), quiet: true });
  dotenv.config({ path: path.join(repoRoot, ".env"), quiet: true });
}

function parseInteger(
  key: string,
  rawValue: string | undefined,
  fallback: number,
  minimum = Number.NEGATIVE_INFINITY
): number {
  if (rawValue == null || rawValue.trim() === "") {
    return fallback;
  }

  const parsed = Number.parseInt(rawValue, 10);
  if (!Number.isFinite(parsed)) {
    throw new RelayError("invalid_request", `${key} must be a valid integer`);
  }

  if (parsed < minimum) {
    throw new RelayError("invalid_request", `${key} must be at least ${minimum}`);
  }

  return parsed;
}

function parseLogLevel(value: string | undefined): RelayConfig["logLevel"] {
  if (value == null || value.trim() === "") {
    return "info";
  }

  if (value === "debug" || value === "info" || value === "warn" || value === "error") {
    return value;
  }

  throw new RelayError("invalid_request", "RELAY_LOG_LEVEL must be debug, info, warn, or error");
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): RelayConfig {
  loadDotenvFiles();

  const staticToken = env.RELAY_STATIC_TOKEN?.trim() ?? "";
  if (!staticToken) {
    throw new RelayError("invalid_request", "RELAY_STATIC_TOKEN is required");
  }

  const port = parseInteger("RELAY_PORT", env.RELAY_PORT, 3000);
  if (port <= 0 || port > 65535) {
    throw new RelayError("invalid_request", "RELAY_PORT must be between 1 and 65535");
  }

  const companionTimeoutMs = parseInteger(
    "RELAY_COMPANION_TIMEOUT_MS",
    env.RELAY_COMPANION_TIMEOUT_MS,
    30000,
    1
  );
  const heartbeatIntervalMs = parseInteger(
    "RELAY_HEARTBEAT_INTERVAL_MS",
    env.RELAY_HEARTBEAT_INTERVAL_MS,
    60000,
    1
  );
  const heartbeatStaleMs = parseInteger(
    "RELAY_HEARTBEAT_STALE_MS",
    env.RELAY_HEARTBEAT_STALE_MS,
    90000,
    1
  );
  const purgeDays = parseInteger("RELAY_PURGE_DAYS", env.RELAY_PURGE_DAYS, 30, 1);
  const wsPingIntervalMs = parseInteger(
    "RELAY_WS_PING_INTERVAL_MS",
    env.RELAY_WS_PING_INTERVAL_MS,
    30000,
    1
  );

  return {
    host: env.RELAY_HOST?.trim() || "0.0.0.0",
    port,
    staticToken,
    dbPath: env.RELAY_DB_PATH?.trim() || "./data/imbot.db",
    fcmProjectId: env.RELAY_FCM_PROJECT_ID?.trim() || null,
    fcmServiceAccount: env.RELAY_FCM_SERVICE_ACCOUNT?.trim() || null,
    openClawUrl: env.RELAY_OPENCLAW_URL?.trim() || "ws://127.0.0.1:18789",
    openClawToken: env.RELAY_OPENCLAW_TOKEN?.trim() || null,
    logLevel: parseLogLevel(env.RELAY_LOG_LEVEL),
    companionTimeoutMs,
    heartbeatIntervalMs,
    heartbeatStaleMs,
    purgeDays,
    wsPingIntervalMs
  };
}
