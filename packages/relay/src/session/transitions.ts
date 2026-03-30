import type { SessionStatus } from "@imbot/wire";

export const TRANSITIONS: Readonly<Record<SessionStatus, readonly SessionStatus[]>> = {
  queued: ["running", "failed"],
  running: ["completed", "failed", "cancelled"],
  completed: ["running"],
  failed: ["running"],
  cancelled: []
};

export function isValidTransition(from: SessionStatus, to: SessionStatus): boolean {
  return TRANSITIONS[from]?.includes(to) ?? false;
}
