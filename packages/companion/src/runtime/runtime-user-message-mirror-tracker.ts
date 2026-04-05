const DEFAULT_MAX_PENDING_AGE_MS = 5 * 60 * 1000;
const MAX_MIRROR_TIMESTAMP_SKEW_MS = 15 * 1000;
const MAX_TIMESTAMPED_MIRROR_LAG_MS = 2 * 60 * 1000;

type PendingRuntimeUserMessage = {
  text: string;
  recordedAtMs: number;
};

export class RuntimeUserMessageMirrorTracker {
  private readonly pendingByProviderSessionId = new Map<string, PendingRuntimeUserMessage[]>();

  constructor(
    private readonly maxPendingAgeMs: number = DEFAULT_MAX_PENDING_AGE_MS,
    private readonly now: () => number = () => Date.now(),
  ) {}

  record(providerSessionId: string, text: string): void {
    const normalizedText = text.trim();
    if (!providerSessionId || normalizedText === "") {
      return;
    }

    const pending = this.prune(providerSessionId);
    pending.push({
      text: normalizedText,
      recordedAtMs: this.now(),
    });
    this.pendingByProviderSessionId.set(providerSessionId, pending);
  }

  consume(
    providerSessionId: string,
    text: string,
    transcriptTimestampMs: number | null,
  ): boolean {
    const normalizedText = text.trim();
    if (!providerSessionId || normalizedText === "") {
      return false;
    }

    const pending = this.prune(providerSessionId);
    const candidateIndex = pending.findIndex(
      (candidate) =>
        candidate.text === normalizedText &&
        this.isTimestampCompatible(candidate, transcriptTimestampMs),
    );
    if (candidateIndex === -1) {
      return false;
    }

    pending.splice(candidateIndex, 1);
    if (pending.length === 0) {
      this.pendingByProviderSessionId.delete(providerSessionId);
    } else {
      this.pendingByProviderSessionId.set(providerSessionId, pending);
    }
    return true;
  }

  clear(providerSessionId: string): void {
    if (!providerSessionId) {
      return;
    }

    this.pendingByProviderSessionId.delete(providerSessionId);
  }

  private prune(providerSessionId: string): PendingRuntimeUserMessage[] {
    const cutoffMs = this.now() - this.maxPendingAgeMs;
    const pending = this.pendingByProviderSessionId.get(providerSessionId) ?? [];
    const fresh = pending.filter((entry) => entry.recordedAtMs >= cutoffMs);
    if (fresh.length === 0) {
      this.pendingByProviderSessionId.delete(providerSessionId);
      return [];
    }

    if (fresh.length !== pending.length) {
      this.pendingByProviderSessionId.set(providerSessionId, fresh);
    }

    return fresh;
  }

  private isTimestampCompatible(
    candidate: PendingRuntimeUserMessage,
    transcriptTimestampMs: number | null,
  ): boolean {
    if (transcriptTimestampMs == null) {
      return this.now() - candidate.recordedAtMs <= this.maxPendingAgeMs;
    }

    const earliestAcceptedTimestamp = candidate.recordedAtMs - MAX_MIRROR_TIMESTAMP_SKEW_MS;
    const latestAcceptedTimestamp = candidate.recordedAtMs + MAX_TIMESTAMPED_MIRROR_LAG_MS;
    return transcriptTimestampMs >= earliestAcceptedTimestamp && transcriptTimestampMs <= latestAcceptedTimestamp;
  }
}
