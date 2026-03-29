import { PROVIDERS, type Provider, type Session } from "@imbot/wire";

import { createRelayApp, type RelayRuntime } from "./app";
import { loadConfig, type RelayConfig } from "./config";

export const RELAY_SUPPORTED_PROVIDERS = PROVIDERS;

export interface RelaySessionEnvelope {
  sessionId: Session["id"];
  provider: Provider;
  workspaceCwd: Session["workspace_cwd"];
}

export function createRelaySessionEnvelope(
  session: Pick<Session, "id" | "provider" | "workspace_cwd">
): RelaySessionEnvelope {
  return {
    sessionId: session.id,
    provider: session.provider,
    workspaceCwd: session.workspace_cwd
  };
}

export { createRelayApp, loadConfig };
export type { RelayConfig, RelayRuntime };

export async function startRelayServer(config = loadConfig()): Promise<RelayRuntime> {
  const runtime = await createRelayApp({ config });
  await runtime.app.listen({
    host: config.host,
    port: config.port
  });
  runtime.app.log.info(`Relay server listening on ${config.host}:${config.port}`);

  const shutdown = async (exitCodeOnFailure: number): Promise<void> => {
    const forcedExitTimer = setTimeout(() => {
      process.exit(exitCodeOnFailure);
    }, 10000);

    forcedExitTimer.unref?.();

    try {
      await runtime.close();
      clearTimeout(forcedExitTimer);
      process.exit(0);
    } catch (error) {
      runtime.app.log.error(error);
      clearTimeout(forcedExitTimer);
      process.exit(exitCodeOnFailure);
    }
  };

  process.once("SIGTERM", () => {
    void shutdown(1);
  });
  process.once("SIGINT", () => {
    void shutdown(1);
  });

  return runtime;
}

if (require.main === module) {
  void startRelayServer().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
