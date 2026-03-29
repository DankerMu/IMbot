# Design: p1-relay-session-lifecycle

## Overview

This change completes the session lifecycle from the minimal P0 implementation to full production readiness. It covers the complete state machine, all REST endpoints, atomic seq allocation, and audit logging.

## Module Structure

```
packages/relay/src/
├── session/
│   ├── orchestrator.ts    # Extended: full state machine, all transitions
│   ├── seq.ts             # New: seq allocator with gap detection
│   └── transitions.ts     # New: transition table + guard conditions
├── routes/
│   ├── sessions.ts        # Extended: all CRUD + lifecycle endpoints
│   └── events.ts          # Extended: pagination, has_more
├── audit/
│   └── logger.ts          # New: audit log writer
└── fcm/
    └── adapter.ts         # Extended: push on terminal states
```

## Key Design Decisions

### 1. Transition Table as Data

The state machine transitions are defined as a static lookup table, not if/else chains:

```typescript
const TRANSITIONS: Record<SessionStatus, SessionStatus[]> = {
  queued:    ['running', 'failed'],
  running:   ['completed', 'failed', 'cancelled'],
  completed: ['running'],          // resume
  failed:    ['running'],          // retry
  cancelled: [],                   // terminal
};
```

Guard conditions (host online, error recoverable) are checked before consulting the table.

### 2. Atomic Transition with Single UPDATE

State transitions use a conditional UPDATE to prevent races:

```sql
UPDATE sessions SET status = ?, updated_at = datetime('now'), last_active_at = datetime('now')
WHERE id = ? AND status = ?
```

If `changes === 0`, the session was already transitioned by another request (or doesn't exist). This is cheaper and safer than SELECT-then-UPDATE.

### 3. Seq Allocation via MAX + 1

```sql
SELECT COALESCE(MAX(seq), 0) + 1 as next_seq
FROM session_events WHERE session_id = ?
```

SQLite WAL single-writer guarantees this is atomic. Gap detection compares the allocated seq against expected (stored max + 1) and logs a warning if they differ.

### 4. Audit Logger as Fire-and-Forget

Audit writes are best-effort. The logger catches INSERT failures and logs them to stderr without failing the parent operation. This prevents audit table issues (disk full, etc.) from blocking session operations.

### 5. REST Endpoint Validation

All endpoints use Fastify's JSON Schema validation for request bodies and query parameters. Invalid requests are rejected at the framework level before reaching handler code.

## State Machine Diagram

```
                  ack ok
    ┌─────────┐ ──────────► ┌─────────┐
    │ queued  │              │ running │
    └────┬────┘              └──┬──┬──┬┘
         │                      │  │  │
    ack error /            result  │  │ POST /cancel
    30s timeout                │  │  │
         │                     ▼  │  ▼
         │              ┌──────────┐ ┌───────────┐
         └─────────────►│  failed  │ │ cancelled │
                        └─────┬────┘ └───────────┘
                              │           (terminal)
                    POST /resume
                              │
                ┌─────────────┘
                │
         ┌──────────┐  POST /resume
         │completed │ ──────────────► running
         └──────────┘
```

## Pagination Design (GET /sessions)

- Default `limit`: 50, max: 200
- Default `offset`: 0
- Sort: `created_at DESC`
- Response includes `total` (count of all matching rows), enabling UI pagination
- Filters (`provider`, `status`, `host_id`) are combined with AND

## Events Catch-up (GET /sessions/:id/events)

- Required param: `since_seq` (integer, events with seq > since_seq)
- Default `limit`: 500
- `has_more`: true when more events exist beyond the returned set
- Sort: `seq ASC`

## Error Response Format

All errors follow the same shape:
```json
{
  "error": "<error_code>",
  "message": "<human-readable description>"
}
```
