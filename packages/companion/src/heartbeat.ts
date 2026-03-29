import type { CompanionHeartbeatMessage, InteractiveProvider } from "@imbot/wire";

export interface HeartbeatTimerOptions {
  readonly hostId: string;
  readonly providers: readonly InteractiveProvider[];
  readonly send: (message: CompanionHeartbeatMessage) => void;
  readonly intervalMs?: number;
  readonly getUptimeSeconds?: () => number;
}

export class HeartbeatTimer {
  private readonly intervalMs: number;
  private readonly getUptimeSeconds: () => number;
  private intervalHandle: NodeJS.Timeout | null = null;

  constructor(private readonly options: HeartbeatTimerOptions) {
    this.intervalMs = options.intervalMs ?? 30000;
    this.getUptimeSeconds = options.getUptimeSeconds ?? (() => Math.floor(process.uptime()));
  }

  start(): void {
    if (this.intervalHandle) {
      return;
    }

    this.sendHeartbeat();
    this.intervalHandle = setInterval(() => {
      this.sendHeartbeat();
    }, this.intervalMs);
    this.intervalHandle.unref?.();
  }

  stop(): void {
    if (!this.intervalHandle) {
      return;
    }

    clearInterval(this.intervalHandle);
    this.intervalHandle = null;
  }

  sendHeartbeat(): void {
    this.options.send({
      type: "heartbeat",
      host_id: this.options.hostId,
      providers: [...this.options.providers],
      uptime: Math.max(0, Math.floor(this.getUptimeSeconds()))
    });
  }
}
