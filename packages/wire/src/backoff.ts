export class ExponentialBackoff {
  private attempt = 0;

  constructor(
    private readonly baseMs: number = 1000,
    private readonly maxMs: number = 30000,
    private readonly jitterMs: number = 1000
  ) {}

  nextDelay(): number {
    const delay = Math.min(this.baseMs * Math.pow(2, this.attempt), this.maxMs);
    const jitter = Math.random() * this.jitterMs;
    this.attempt += 1;
    return delay + jitter;
  }

  reset(): void {
    this.attempt = 0;
  }

  get attempts(): number {
    return this.attempt;
  }
}
