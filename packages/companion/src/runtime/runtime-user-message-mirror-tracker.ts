const DEFAULT_MAX_PENDING_AGE_MS = 5 * 60 * 1000;
const MAX_MIRROR_TIMESTAMP_SKEW_MS = 15 * 1000;
const MAX_UNTIMESTAMPED_MIRROR_AGE_MS = 2 * 60 * 1000;

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
    const candidate = pending[0];
    if (!candidate || candidate.text != normalizedText) {
      return false;
    }

    if (!this.isTimestampCompatible(candidate, transcriptTimestampMs)) {
      return false;
    }

    pending.shift();
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

    return fresh;
  }

  private isTimestampCompatible(
    candidate: PendingRuntimeUserMessage,
    transcriptTimestampMs: number | null,
  ): boolean {
    if (transcriptTimestampMs == null) {
      return this.now() - candidate.recordedAtMs <= MAX_UNTIMESTAMPED_MIRROR_AGE_MS;
    }

    const earliestAcceptedTimestamp = candidate.recordedAtMs - MAX_MIRROR_TIMESTAMP_SKEW_MS;
    const latestAcceptedTimestamp = candidate.recordedAtMs + MAX_UNTIMESTAMPED_MIRROR_AGE_MS;
    return transcriptTimestampMs >= earliestAcceptedTimestamp && transcriptTimestampMs <= latestAcceptedTimestamp;
  }
}
