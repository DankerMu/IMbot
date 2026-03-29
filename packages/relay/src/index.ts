import { PROVIDERS, type Provider, type Session } from "@imbot/wire";

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
