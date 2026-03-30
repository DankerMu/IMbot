import { PROVIDERS, type Provider } from "@imbot/wire";

import {
  getConfiguredProviders,
  loadCompanionConfig,
  type CompanionConfig
} from "./config";
import { CommandDispatcher } from "./dispatcher";
import { HeartbeatTimer } from "./heartbeat";
import { RelayClient } from "./relay-client";
import { ClaudeRuntimeAdapter } from "./runtime/claude-adapter";
import { discoverSessions } from "./runtime/session-discovery";
import { SessionIndex } from "./runtime/session-index";
import type { LoggerLike } from "./types";
import { browseDirectory } from "./workspace/browser";
import { ConfigManager } from "./workspace/config-manager";

export const COMPANION_SUPPORTED_PROVIDERS = PROVIDERS;

export function supportsInteractiveProvider(
  provider: Provider
): provider is Exclude<Provider, "openclaw"> {
  return provider === "claude" || provider === "book";
}

export interface CompanionRuntime {
  readonly config: CompanionConfig;
  readonly relayClient: RelayClient;
  readonly heartbeat: HeartbeatTimer;
  readonly dispatcher: CommandDispatcher;
  readonly adapter: ClaudeRuntimeAdapter;
  readonly sessionIndex: SessionIndex;
  readonly configManager: ConfigManager;
  connect(): void;
  close(): Promise<void>;
}

export async function createCompanionRuntime(options?: {
  readonly config?: CompanionConfig;
  readonly logger?: LoggerLike;
  readonly heartbeatIntervalMs?: number;
  readonly reconnectDelaysMs?: readonly number[];
  readonly killGraceMs?: number;
}): Promise<CompanionRuntime> {
  const config = options?.config ?? loadCompanionConfig();
  const logger = options?.logger ?? console;
  const sessionIndex = new SessionIndex({
    filePath: config.sessionIndexPath,
    logger
  });
  const configManager = new ConfigManager({
    configPath: config.configPath,
    logger
  });
  const relayClient = new RelayClient({
    relayUrl: config.relayUrl,
    token: config.token,
    hostId: config.hostId,
    logger,
    backoffDelaysMs: options?.reconnectDelaysMs
  });
  const heartbeat = new HeartbeatTimer({
    hostId: config.hostId,
    providers: getConfiguredProviders(config),
    intervalMs: options?.heartbeatIntervalMs,
    send: (message) => {
      relayClient.send(message);
    }
  });
  const adapter = new ClaudeRuntimeAdapter({
    providers: config.providers,
    sessionIndex,
    logger,
    killGraceMs: options?.killGraceMs,
    isAllowedDirectory: (provider, cwd) => {
      if (provider !== "book") {
        return true;
      }

      return configManager.isPathUnderRoot("book", cwd);
    },
    sendEvent: (message) => {
      relayClient.send(message);
    }
  });
  const dispatcher = new CommandDispatcher({
    logger,
    sendAck: (message) => {
      relayClient.send(message);
    }
  });

  dispatcher.register("create_session", async (command) => adapter.createSession(command));
  dispatcher.register("resume_session", async (command) => adapter.resumeSession(command));
  dispatcher.register("send_message", async (command) => {
    await adapter.sendMessage(command.session_id, command.text);
  });
  dispatcher.register("cancel_session", async (command) => {
    await adapter.cancel(command.session_id);
  });
  dispatcher.register("list_sessions", async (command) => {
    return await discoverSessions(command.cwd, command.provider, {
      logger
    });
  });
  dispatcher.register("browse_directory", async (command) => {
    return await browseDirectory(command.path, {
      allowedRoots: command.roots
    });
  });
  dispatcher.register("add_root", async (command) => {
    configManager.addRoot(command.provider, command.path, command.label);
  });
  dispatcher.register("remove_root", async (command) => {
    configManager.removeRoot(command.provider, command.path);
  });

  relayClient.on("message", (message) => {
    void dispatcher.dispatch(message);
  });
  relayClient.on("connected", () => {
    heartbeat.start();
  });
  relayClient.on("disconnected", () => {
    heartbeat.stop();
  });

  return {
    config,
    relayClient,
    heartbeat,
    dispatcher,
    adapter,
    sessionIndex,
    configManager,
    connect: () => {
      relayClient.connect();
    },
    close: async () => {
      heartbeat.stop();
      await adapter.shutdown();
      relayClient.close();
    }
  };
}

export async function startCompanion(config = loadCompanionConfig()): Promise<CompanionRuntime> {
  const runtime = await createCompanionRuntime({
    config
  });

  const logUnhandledRejection = (error: unknown) => {
    console.error?.("Unhandled rejection in companion runtime", error);
  };

  const shutdown = async (signal: NodeJS.Signals) => {
    try {
      console.info?.(`Received ${signal}; shutting down companion`);
      await runtime.close();
      process.exit(0);
    } catch (error) {
      console.error?.(error);
      process.exit(1);
    }
  };

  process.on("unhandledRejection", logUnhandledRejection);
  process.once("SIGTERM", () => {
    void shutdown("SIGTERM");
  });
  process.once("SIGINT", () => {
    void shutdown("SIGINT");
  });

  runtime.connect();
  return runtime;
}

export {
  loadCompanionConfig,
  RelayClient,
  HeartbeatTimer,
  CommandDispatcher,
  ClaudeRuntimeAdapter,
  SessionIndex,
  browseDirectory,
  ConfigManager,
  discoverSessions
};

if (require.main === module) {
  void startCompanion().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
