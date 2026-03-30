import type { CompanionMessage } from "@imbot/wire";

export class EventBuffer {
  private buffer: CompanionMessage[] = [];

  constructor(private readonly maxSize: number = 10_000) {}

  push(event: CompanionMessage): void {
    if (this.buffer.length >= this.maxSize) {
      this.buffer.shift();
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
