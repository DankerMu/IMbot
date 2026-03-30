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
import { SessionIndex } from "./runtime/session-index";
import type { LoggerLike } from "./types";
import { browseDirectory } from "./workspace/browser";

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
  dispatcher.register("browse_directory", async (command) => {
    return await browseDirectory(command.path);
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
  browseDirectory
};

if (require.main === module) {
  void startCompanion().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
