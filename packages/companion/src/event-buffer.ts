import type { CompanionMessage } from "@imbot/wire";

type LoggerLike = {
  readonly warn?: (...args: unknown[]) => void;
};

export class EventBuffer {
  private buffer: CompanionMessage[] = [];
  private readonly logger: LoggerLike | null;

  constructor(
    private readonly maxSize: number = 10_000,
    logger?: LoggerLike
  ) {
    this.logger = logger ?? null;
  }

  push(event: CompanionMessage): void {
    if (this.buffer.length >= this.maxSize) {
      this.buffer.shift();
      this.logger?.warn?.(`Event buffer overflow (max ${this.maxSize}), dropping oldest message`);
    }

    this.buffer.push(event);
  }

  flush(): CompanionMessage[] {
    const events = [...this.buffer];
    this.buffer = [];
    return events;
  }

  get size(): number {
    return this.buffer.length;
  }

  clear(): void {
    this.buffer = [];
  }
}
