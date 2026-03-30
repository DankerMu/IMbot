import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import type { InteractiveProvider } from "@imbot/wire";

import { CompanionError } from "./types";

export interface CompanionProviderConfig {
  readonly binary: string;
}

export interface CompanionConfig {
  readonly configPath: string;
  readonly relayUrl: string;
  readonly token: string;
  readonly hostId: string;
  readonly providers: Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>>;
  readonly sessionIndexPath: string;
}

type RawCompanionConfig = {
  readonly relay_url?: unknown;
  readonly token?: unknown;
  readonly host_id?: unknown;
  readonly providers?: unknown;
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
  const providers = parseProviders(rawConfig.providers);

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
    sessionIndexPath
  };
}

function requireString(value: unknown, fieldName: string): string {
  if (typeof value !== "string" || value.trim() === "") {
    throw new CompanionError("invalid_config", `companion config field ${fieldName} must be a non-empty string`);
  }

  return value.trim();
}

function parseProviders(value: unknown): Readonly<Partial<Record<InteractiveProvider, CompanionProviderConfig>>> {
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

    const binary = (rawProviderConfig as Record<string, unknown>).binary;
    providers[provider] = {
      binary: typeof binary === "string" && binary.trim() !== "" ? binary.trim() : provider
    };
  }

  return providers;
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
