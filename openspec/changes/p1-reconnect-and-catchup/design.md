# Design: p1-reconnect-and-catchup

## Exponential Backoff Strategy

All three tiers use the same backoff algorithm:

```
delay = min(baseDelay * 2^attempt, maxDelay) + jitter
```

| Parameter | Android | Companion | OpenClaw Bridge |
|-----------|---------|-----------|-----------------|
| baseDelay | 1s | 1s | 1s |
| maxDelay | 30s | 30s | 30s |
| jitter | 0-500ms | 0-1000ms | 0-500ms |
| reset on success | yes | yes | yes |

### Shared Utility

```typescript
// packages/wire/src/backoff.ts
export class ExponentialBackoff {
  private attempt = 0;
  constructor(
    private baseMs: number = 1000,
    private maxMs: number = 30000,
    private jitterMs: number = 1000,
  ) {}

  nextDelay(): number {
    const delay = Math.min(this.baseMs * Math.pow(2, this.attempt), this.maxMs);
    const jitter = Math.random() * this.jitterMs;
    this.attempt++;
    return delay + jitter;
  }

  reset(): void { this.attempt = 0; }
}
```

Android (Kotlin) has its own implementation with the same logic.

## Android WebSocket Reconnection

### Architecture

```
┌───────────────┐     ┌──────────────────┐     ┌──────────────┐
│ WsManager     │────►│ OkHttp WebSocket │────►│ Relay Server │
│               │     │ (auto-reconnect) │     │              │
│ - backoff     │     └──────────────────┘     └──────────────┘
│ - state flow  │
│ - sub tracker │     ┌──────────────────┐
│               │────►│ ConnectivityMgr  │ ← network change callback
└───────┬───────┘     └──────────────────┘
        │
        ▼
┌───────────────┐
│ ConnectionState│ = Connected | Connecting | Disconnected(retryIn: Int)
└───────────────┘
```

### Connection State Machine (Kotlin)

```kotlin
sealed class ConnectionState {
    object Connected : ConnectionState()
    object Connecting : ConnectionState()
    data class Disconnected(val retryInMs: Long) : ConnectionState()
}
```

- `WsManager` exposes `StateFlow<ConnectionState>` observed by `ConnectionBanner` composable.
- On disconnect: emit `Disconnected(retryInMs)`, schedule reconnect via `CoroutineScope.delay()`.
- On connect: emit `Connected`, call `resubscribeAll()`.
- `NetworkCallback` (ConnectivityManager) triggers immediate reconnect attempt on network change.

### Subscription Tracker

```kotlin
class SubscriptionTracker {
    private val subscribed = mutableSetOf<String>()  // session IDs

    fun subscribe(sessionId: String)
    fun unsubscribe(sessionId: String)
    fun getAll(): Set<String>
    fun resubscribeAll(ws: WebSocket)  // sends subscribe for each tracked session
}
```

## Android Event Catch-Up

### Flow

```
Reconnect successful
        │
        ▼
For each subscribed session (parallel):
    │
    ├─ Read lastKnownSeq from Room: SELECT MAX(seq) FROM events WHERE session_id = ?
    │
    ├─ GET /v1/sessions/:id/events?since_seq=<lastKnownSeq>
    │
    ├─ INSERT OR IGNORE events into Room (dedup by session_id + seq)
    │
    ├─ If has_more: repeat with since_seq = max seq from last batch
    │
    └─ Done → notify ViewModel
        │
All sessions done → hide "Syncing" indicator
```

### Room DAO

```kotlin
@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<SessionEventEntity>)

    @Query("SELECT COALESCE(MAX(seq), 0) FROM events WHERE session_id = :sessionId")
    suspend fun getMaxSeq(sessionId: String): Int

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY seq ASC")
    fun observeEvents(sessionId: String): Flow<List<SessionEventEntity>>
}
```

### Syncing State

```kotlin
class CatchUpManager(private val eventDao: EventDao, private val api: RelayApi) {
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    suspend fun catchUpAll(sessionIds: Set<String>) {
        _syncing.value = true
        try {
            coroutineScope {
                sessionIds.map { id -> async { catchUpSession(id) } }.awaitAll()
            }
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun catchUpSession(sessionId: String) {
        var sinceSeq = eventDao.getMaxSeq(sessionId)
        do {
            val resp = api.getEvents(sessionId, sinceSeq, limit = 500)
            eventDao.insertAll(resp.events.map { it.toEntity() })
            sinceSeq = resp.events.maxOfOrNull { it.seq } ?: break
        } while (resp.hasMore)
    }
}
```

## Companion Reconnection

### Event Buffer

```typescript
// packages/companion/src/event-buffer.ts
class EventBuffer {
  private buffer: CompanionMessage[] = [];
  private maxSize = 10_000;

  push(event: CompanionMessage): void {
    if (this.buffer.length >= this.maxSize) {
      this.buffer.shift(); // drop oldest
      logger.warn('Event buffer overflow, dropping oldest event');
    }
    this.buffer.push(event);
  }

  flush(): CompanionMessage[] {
    const events = [...this.buffer];
    this.buffer = [];
    return events;
  }

  get size(): number { return this.buffer.length; }
}
```

### Reconnect Flow

```
WSS disconnects
    │
    ▼
Start ExponentialBackoff
    │
    ▼
[Running CLI processes continue locally]
[Events buffered in EventBuffer]
    │
    ▼
Reconnect succeeds
    │
    ├─ Send heartbeat (re-register host)
    ├─ Report running session statuses
    └─ Flush buffered events in order
```

## OpenClaw Bridge Reconnection

### State Tracking

```typescript
// packages/relay/src/openclaw-bridge.ts
class OpenClawBridge {
  private ws: WebSocket | null = null;
  private backoff = new ExponentialBackoff(1000, 30000, 500);
  private available = false;

  get isAvailable(): boolean { return this.available; }

  private onDisconnect(): void {
    this.available = false;
    // Mark active openclaw sessions as failed
    this.activeSessionIds.forEach(id =>
      sessionOrchestrator.transition(id, 'failed', { error_code: 'provider_unreachable' })
    );
    this.scheduleReconnect();
  }

  private onConnect(): void {
    this.available = true;
    this.backoff.reset();
    // Check if gateway has any live sessions (brief hiccup case)
  }
}
```

## Relay Catch-Up API

### SQL Query

```sql
SELECT id, session_id, seq, type, payload, created_at
FROM session_events
WHERE session_id = ? AND seq > ?
ORDER BY seq ASC
LIMIT ?
```

Uses `idx_events_session_seq(session_id, seq)` — range scan on composite index.

### has_more Detection

Query `LIMIT + 1` rows. If result count > limit, `has_more = true` and return only `limit` rows. Avoids a separate COUNT query.

### Route Implementation

```typescript
// packages/relay/src/routes/events.ts
fastify.get('/v1/sessions/:id/events', async (req, reply) => {
  const { id } = req.params;
  const sinceSeq = parseInt(req.query.since_seq);
  const limit = Math.min(parseInt(req.query.limit) || 500, 500);

  if (isNaN(sinceSeq) || sinceSeq < 0) return reply.code(400).send({ error: 'invalid_request', message: 'since_seq must be a non-negative integer' });

  const session = db.getSession(id);
  if (!session) return reply.code(404).send({ error: 'not_found', message: 'Session not found' });

  const rows = db.prepare(
    `SELECT id, session_id, seq, type, payload, created_at
     FROM session_events WHERE session_id = ? AND seq > ? ORDER BY seq ASC LIMIT ?`
  ).all(id, sinceSeq, limit + 1);

  const hasMore = rows.length > limit;
  const events = hasMore ? rows.slice(0, limit) : rows;

  return { events: events.map(parseEvent), has_more: hasMore };
});
```

## File Layout

```
packages/wire/src/
└── backoff.ts                    // NEW: shared ExponentialBackoff

packages/relay/src/
├── routes/events.ts              // MODIFIED: add since_seq + has_more logic
└── openclaw-bridge.ts            // MODIFIED: add reconnect + availability tracking

packages/companion/src/
├── ws-client.ts                  // MODIFIED: integrate ExponentialBackoff + event buffering
└── event-buffer.ts               // NEW: bounded event buffer

packages/android/app/src/main/
├── data/ws/WsManager.kt          // MODIFIED: add reconnect + ConnectionState
├── data/ws/SubscriptionTracker.kt // NEW: track active subscriptions
├── data/sync/CatchUpManager.kt   // NEW: post-reconnect event catch-up
├── data/db/EventDao.kt           // MODIFIED: add getMaxSeq, insertAll with IGNORE
└── ui/components/ConnectionBanner.kt // NEW: connection status banner composable
```
