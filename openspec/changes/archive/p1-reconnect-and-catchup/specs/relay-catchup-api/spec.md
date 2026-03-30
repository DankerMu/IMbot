# Capability: relay-catchup-api

## ADDED Requirements

### Requirement: Event Catch-Up Endpoint

The relay SHALL expose `GET /v1/sessions/:id/events?since_seq=N&limit=L` that returns events with `seq > N`, ordered by `seq` ascending, with a `has_more` boolean flag. The default `limit` is 500. The query MUST use the existing `idx_events_session_seq` SQLite index for efficient retrieval.

#### Scenario: since_seq=0 returns all events

WHEN the client calls `GET /v1/sessions/sess-1/events?since_seq=0`
AND session "sess-1" has 100 events (seq 1-100)
THEN the response contains all 100 events ordered by seq ascending
AND `has_more` is `false`

#### Scenario: since_seq=100 with 50 more events

WHEN the client calls `GET /v1/sessions/sess-1/events?since_seq=100`
AND session "sess-1" has events with seq up to 150
THEN the response contains 50 events (seq 101-150)
AND `has_more` is `false`

#### Scenario: since_seq=100 with 600 more events (pagination)

WHEN the client calls `GET /v1/sessions/sess-1/events?since_seq=100&limit=500`
AND session "sess-1" has events with seq up to 700
THEN the response contains 500 events (seq 101-600)
AND `has_more` is `true`
AND the client can call again with `since_seq=600` to get the remaining 100

#### Scenario: since_seq higher than max seq

WHEN the client calls `GET /v1/sessions/sess-1/events?since_seq=999`
AND session "sess-1" only has events up to seq 150
THEN the response contains an empty events array
AND `has_more` is `false`

#### Scenario: invalid since_seq (negative value)

WHEN the client calls `GET /v1/sessions/sess-1/events?since_seq=-1`
THEN the relay returns HTTP 400 with `{ "error": "invalid_request", "message": "since_seq must be a non-negative integer" }`

#### Scenario: since_seq is not a number

WHEN the client calls `GET /v1/sessions/sess-1/events?since_seq=abc`
THEN the relay returns HTTP 400 with `{ "error": "invalid_request", "message": "since_seq must be a non-negative integer" }`

#### Scenario: session not found

WHEN the client calls `GET /v1/sessions/nonexistent/events?since_seq=0`
THEN the relay returns HTTP 404 with `{ "error": "not_found", "message": "Session not found" }`

#### Scenario: missing since_seq parameter

WHEN the client calls `GET /v1/sessions/sess-1/events` without a `since_seq` query parameter
THEN the relay returns HTTP 400 with `{ "error": "invalid_request", "message": "since_seq is required" }`

---

### Requirement: Efficient SQLite Query

The catch-up query MUST use the `idx_events_session_seq` index to avoid full table scans. The query MUST complete within 50ms for up to 500 events.

#### Scenario: query uses index

WHEN `EXPLAIN QUERY PLAN` is run on the catch-up SQL
THEN the output shows usage of `idx_events_session_seq` index
AND no full table scan is indicated

#### Scenario: query performance under load

WHEN a session has 10,000 events
AND the client requests `since_seq=9500&limit=500`
THEN the response is returned within 50ms

---

### Requirement: Response Format

The catch-up response SHALL follow this format:

```json
{
  "events": [
    {
      "id": "evt-uuid",
      "session_id": "sess-uuid",
      "seq": 101,
      "type": "assistant_delta",
      "payload": { "text": "..." },
      "created_at": "2026-03-28T13:05:01Z"
    }
  ],
  "has_more": false
}
```

#### Scenario: events ordered by seq ascending

WHEN the catch-up endpoint returns events
THEN events are strictly ordered by `seq` ascending
AND each event's `seq` is greater than the previous event's `seq`

#### Scenario: limit parameter respected

WHEN the client requests `limit=100`
THEN at most 100 events are returned regardless of how many match
AND `has_more` is `true` if more events exist beyond the limit

#### Scenario: limit capped at 500

WHEN the client requests `limit=1000`
THEN the effective limit is capped at 500
AND at most 500 events are returned
