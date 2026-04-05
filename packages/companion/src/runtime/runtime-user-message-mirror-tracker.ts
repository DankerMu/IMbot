const DEFAULT_MAX_PENDING_AGE_MS = 5 * 60 * 1000;

type PendingRuntimeUserMessage = {
  text: string;
  recordedAtMs: number;
};

export class RuntimeUserMessageMirrorTracker {
  private readonly pendingByProviderSessionId = new Map<string, PendingRuntimeUserMessage[]>();

  constructor(private readonly maxPendingAgeMs: number = DEFAULT_MAX_PENDING_AGE_MS) {}

  record(providerSessionId: string, text: string): void {
    const normalizedText = text.trim();
    if (!providerSessionId || normalizedText === "") {
      return;
    }

    const pending = this.prune(providerSessionId);
    pending.push({
      text: normalizedText,
      recordedAtMs: Date.now()
    });
    this.pendingByProviderSessionId.set(providerSessionId, pending);
  }

  consume(providerSessionId: string, text: string): boolean {
    const normalizedText = text.trim();
    if (!providerSessionId || normalizedText === "") {
      return false;
    }

    const pending = this.prune(providerSessionId);
    const matchIndex = pending.findIndex((entry) => entry.text === normalizedText);
    if (matchIndex < 0) {
      return false;
    }

    pending.splice(matchIndex, 1);
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
    const cutoffMs = Date.now() - this.maxPendingAgeMs;
    const pending = this.pendingByProviderSessionId.get(providerSessionId) ?? [];
    const fresh = pending.filter((entry) => entry.recordedAtMs >= cutoffMs);
    if (fresh.length === 0) {
      this.pendingByProviderSessionId.delete(providerSessionId);
      return [];
    }

    return fresh;
  }
}
