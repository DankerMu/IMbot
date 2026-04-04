import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import type { InteractiveProvider } from "@imbot/wire";

import { CompanionError } from "./types";

export interface CompanionProviderConfig {
  readonly binary: string;
  readonly configDir: string;
  readonly projectsDir: string;
}

export interface CompanionConfig {
  readonly configPath: string;
  readonly relayUrl: string;
  readonly token: string;
  readonly hostId: string;
  readonly providers: Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>>;
  readonly sessionIndexPath: string;
  readonly idleTimeoutMs: number;
}

type RawCompanionConfig = {
  readonly relay_url?: unknown;
  readonly token?: unknown;
  readonly host_id?: unknown;
  readonly providers?: unknown;
  readonly idle_timeout_ms?: unknown;
};

const INTERACTIVE_PROVIDERS = ["claude", "book"] as const satisfies readonly InteractiveProvider[];

export function getDefaultCompanionConfigPath(): string {
  return path.join(os.homedir(), ".imbot", "companion.json");
}

export function getDefaultSessionIndexPath(): string {
  return path.join(os.homedir(), ".imbot", "sessions.json");
}

export function getConfiguredProviders(config: Pick<CompanionConfig, "providers">): InteractiveProvider[] {
  return INTERACTIVE_PROVIDERS.filter((provider) => config.providers[provider] != null);
}

export function loadCompanionConfig(env: NodeJS.ProcessEnv = process.env): CompanionConfig {
  const configPath = env.COMPANION_CONFIG?.trim() || getDefaultCompanionConfigPath();
  const sessionIndexPath = env.COMPANION_SESSION_INDEX_PATH?.trim() || getDefaultSessionIndexPath();

  let rawText: string;
  try {
    rawText = fs.readFileSync(configPath, "utf8");
  } catch (error) {
    throw new CompanionError(
      "invalid_config",
      `Failed to read companion config at ${configPath}: ${error instanceof Error ? error.message : "unknown error"}`
    );
  }

  let rawConfig: RawCompanionConfig;
  try {
    rawConfig = JSON.parse(rawText) as RawCompanionConfig;
  } catch (error) {
    throw new CompanionError(
      "invalid_config",
      `Failed to parse companion config at ${configPath}: ${error instanceof Error ? error.message : "invalid JSON"}`
    );
  }

  const relayUrl = requireString(rawConfig.relay_url, "relay_url");
  const token = requireString(rawConfig.token, "token");
  const hostId = requireString(rawConfig.host_id, "host_id");
  const providers = parseProviders(rawConfig.providers, env);
  const idleTimeoutMs = parseOptionalPositiveInt(
    rawConfig.idle_timeout_ms,
    env.COMPANION_IDLE_TIMEOUT_MS,
    1800000,
    "idle_timeout_ms"
  );

  if (getConfiguredProviders({ providers }).length === 0) {
    throw new CompanionError(
      "invalid_config",
      "companion config must define at least one provider in providers.claude or providers.book"
    );
  }

  return {
    configPath,
    relayUrl: normalizeRelayUrl(relayUrl),
    token,
    hostId,
    providers,
    sessionIndexPath,
    idleTimeoutMs
  };
}

function requireString(value: unknown, fieldName: string): string {
  if (typeof value !== "string" || value.trim() === "") {
    throw new CompanionError("invalid_config", `companion config field ${fieldName} must be a non-empty string`);
  }

  return value.trim();
}

function parseOptionalPositiveInt(
  rawValue: unknown,
  envValue: string | undefined,
  fallback: number,
  fieldName: string
): number {
  const value = rawValue ?? envValue;
  if (value == null || value === "") {
    return fallback;
  }

  const parsed = typeof value === "number" ? value : Number.parseInt(String(value), 10);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new CompanionError("invalid_config", `companion config field ${fieldName} must be a positive integer`);
  }

  return parsed;
}

function parseProviders(
  value: unknown,
  env: NodeJS.ProcessEnv
): Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>> {
  if (value == null || typeof value !== "object" || Array.isArray(value)) {
    throw new CompanionError("invalid_config", "companion config field providers must be an object");
  }

  const rawProviders = value as Record<string, unknown>;
  const providers: Partial<Record<InteractiveProvider, CompanionProviderConfig>> = {};

  for (const provider of INTERACTIVE_PROVIDERS) {
    const rawProviderConfig = rawProviders[provider];
    if (rawProviderConfig == null) {
      continue;
    }

    if (typeof rawProviderConfig !== "object" || Array.isArray(rawProviderConfig)) {
      throw new CompanionError(
        "invalid_config",
        `companion config field providers.${provider} must be an object`
      );
    }

    const rawProviderRecord = rawProviderConfig as Record<string, unknown>;
    const binary = rawProviderRecord.binary;
    const configuredBinary = typeof binary === "string" && binary.trim() !== "" ? binary.trim() : provider;
    const resolvedBinaryPath = resolveProviderBinary(configuredBinary, env);
    const configDir = resolveProviderConfigDir(rawProviderRecord, resolvedBinaryPath, env);
    providers[provider] = {
      binary: resolvedBinaryPath,
      configDir,
      projectsDir: path.join(configDir, "projects")
    };
  }

  return providers;
}

function resolveProviderConfigDir(
  rawProviderConfig: Record<string, unknown>,
  resolvedBinaryPath: string,
  env: NodeJS.ProcessEnv
): string {
  const configuredConfigDir = rawProviderConfig.config_dir;
  if (typeof configuredConfigDir === "string" && configuredConfigDir.trim() !== "") {
    return expandUserPath(configuredConfigDir, env);
  }

  if (path.isAbsolute(resolvedBinaryPath)) {
    return detectConfigDir(resolvedBinaryPath, env);
  }

  return getDefaultProviderConfigDir(env);
}

function resolveProviderBinary(binary: string, env: NodeJS.ProcessEnv): string {
  const expandedBinary = expandUserPath(binary, env);
  if (path.isAbsolute(expandedBinary) || expandedBinary.includes(path.sep)) {
    return path.normalize(expandedBinary);
  }

  for (const searchPath of collectBinarySearchPaths(env)) {
    const candidate = path.join(searchPath, expandedBinary);
    if (isExecutableFile(candidate)) {
      return candidate;
    }
  }

  return expandedBinary;
}

function detectConfigDir(resolvedBinaryPath: string, env: NodeJS.ProcessEnv): string {
  const defaultDir = getDefaultProviderConfigDir(env);

  try {
    const stat = fs.statSync(resolvedBinaryPath);
    if (!stat.isFile()) {
      return defaultDir;
    }

    const fileDescriptor = fs.openSync(resolvedBinaryPath, "r");
    try {
      const buffer = Buffer.alloc(1024);
      const bytesRead = fs.readSync(fileDescriptor, buffer, 0, buffer.length, 0);
      const content = buffer.toString("utf8", 0, bytesRead);
      const lines = content.split(/\r?\n/).slice(0, 10);
      for (const line of lines) {
        const match = line.match(/CLAUDE_CONFIG_DIR=["']?([^"'\s]+)["']?/);
        if (match) {
          const raw = match[1].replace(/\$HOME/g, "~");
          return expandUserPath(raw, env);
        }
      }
    } finally {
      fs.closeSync(fileDescriptor);
    }
  } catch {}

  return defaultDir;
}

function getDefaultProviderConfigDir(env: NodeJS.ProcessEnv): string {
  return path.join(env.HOME?.trim() || os.homedir(), ".claude");
}

function collectBinarySearchPaths(env: NodeJS.ProcessEnv): string[] {
  const searchPaths = [
    ...(env.PATH?.split(path.delimiter) ?? []),
    "~/.local/bin",
    "~/bin",
    "/opt/homebrew/bin",
    "/usr/local/bin"
  ];
  const deduped = new Set<string>();

  for (const rawSearchPath of searchPaths) {
    const normalizedSearchPath = expandUserPath(rawSearchPath, env).trim();
    if (normalizedSearchPath !== "") {
      deduped.add(normalizedSearchPath);
    }
  }

  return Array.from(deduped);
}

function expandUserPath(value: string, env: NodeJS.ProcessEnv): string {
  const trimmed = value.trim();
  const homeDir = env.HOME?.trim() || os.homedir();
  if (trimmed === "~") {
    return homeDir;
  }

  if (trimmed.startsWith("~/")) {
    return path.join(homeDir, trimmed.slice(2));
  }

  return trimmed;
}

function isExecutableFile(filePath: string): boolean {
  try {
    fs.accessSync(filePath, fs.constants.X_OK);
    return fs.statSync(filePath).isFile();
  } catch {
    return false;
  }
}

function normalizeRelayUrl(rawRelayUrl: string): string {
  let parsed: URL;
  try {
    parsed = new URL(rawRelayUrl);
  } catch (error) {
    throw new CompanionError(
      "invalid_config",
      `companion config field relay_url must be a valid URL: ${error instanceof Error ? error.message : "invalid URL"}`
    );
  }

  const hostname = parsed.hostname.toLowerCase();
  const isLocalhost = hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";

  if (parsed.protocol !== "ws:" && parsed.protocol !== "wss:") {
    throw new CompanionError("invalid_config", "relay_url must use ws:// or wss://");
  }

  if (!isLocalhost && parsed.protocol !== "wss:") {
    throw new CompanionError("invalid_config", "relay_url must use wss:// for non-localhost hosts");
  }

  return parsed.toString().replace(/\/$/, "");
}
