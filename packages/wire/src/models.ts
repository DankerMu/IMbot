import type { EventType, Provider, SessionStatus } from "./enums";

export type HostType = "macbook" | "relay_local";
export type HostAvailability = "online" | "offline";

export interface Session {
  id: string;
  provider: Provider;
  provider_session_id: string | null;
  host_id: string;
  workspace_root: string | null;
  workspace_cwd: string;
  initial_prompt: string | null;
  model: string | null;
  permission_mode: string;
  status: SessionStatus;
  error_message: string | null;
  error_code: string | null;
  input_tokens?: number | null;
  output_tokens?: number | null;
  context_window?: number | null;
  local_available: boolean;
  created_at: string;
  updated_at: string;
  last_active_at: string;
}

export interface Host {
  id: string;
  name: string;
  type: HostType;
  status: HostAvailability;
  last_heartbeat_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface WorkspaceRoot {
  id: string;
  host_id: string;
  provider: Provider;
  path: string;
  label: string | null;
  created_at: string;
}

export interface SessionEvent {
  id: string;
  session_id: string;
  seq: number;
  type: EventType;
  payload: unknown;
  created_at: string;
}

export interface SessionUsagePayload {
  input_tokens: number;
  output_tokens: number;
  cache_creation_input_tokens?: number;
  cache_read_input_tokens?: number;
  total_cost_usd?: number;
  context_window?: number;
  model?: string;
}
