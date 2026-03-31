import { createTask, type ScheduledTask } from "node-cron";

import type { RelayConfig } from "../config";
import type { RelayDatabase } from "../db/init";

type LoggerLike = {
  readonly error?: (...args: unknown[]) => void;
  readonly info?: (...args: unknown[]) => void;
};

type ScheduledTaskLike = Pick<ScheduledTask, "start" | "stop" | "destroy">;

type TaskFactory = (
  expression: string,
  handler: () => void | Promise<void>
) => ScheduledTaskLike;

const PURGE_BATCH_SIZE = 100;
const DAILY_PURGE_CRON = "0 3 * * *";
const DAY_IN_MS = 24 * 60 * 60 * 1000;

function createCronTask(
  expression: string,
  handler: () => void | Promise<void>
): ScheduledTaskLike {
  return createTask(expression, handler, {
    timezone: "UTC",
    noOverlap: true
  });
}

export class RelayPurgeJob {
  private task: ScheduledTaskLike | null = null;

  constructor(
    private readonly config: Pick<RelayConfig, "purgeDays">,
    private readonly db: RelayDatabase,
    private readonly logger: LoggerLike = console,
    private readonly taskFactory: TaskFactory = createCronTask
  ) {}

  start(): void {
    if (this.task) {
      return;
    }

    this.task = this.taskFactory(DAILY_PURGE_CRON, () => {
      void this.runScheduled();
    });
    this.task.start();
  }

  stop(): void {
    if (!this.task) {
      return;
    }

    this.task.stop();
    this.task.destroy();
    this.task = null;
  }

  async runOnce(now = new Date()): Promise<number> {
    const cutoff = new Date(now.getTime() - this.config.purgeDays * DAY_IN_MS).toISOString();
    const deleteBatch = this.db.prepare(
      `
      DELETE FROM sessions
      WHERE id IN (
        SELECT id
        FROM sessions
        WHERE status IN ('completed', 'failed', 'cancelled')
          AND julianday(last_active_at) < julianday(?)
        LIMIT ${PURGE_BATCH_SIZE}
      )
      `
    );

    let purgedCount = 0;

    while (true) {
      const result = deleteBatch.run(cutoff);
      purgedCount += result.changes;
      if (result.changes === 0) {
        break;
      }

      await yieldToEventLoop();
    }

    this.logger.info?.(`[purge] ${now.toISOString()} - purged ${purgedCount} sessions`);
    return purgedCount;
  }

  private async runScheduled(): Promise<void> {
    try {
      await this.runOnce();
    } catch (error) {
      this.logger.error?.(error);
    }
  }
}

export { DAILY_PURGE_CRON, PURGE_BATCH_SIZE };

function yieldToEventLoop(): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, 0);
  });
}
