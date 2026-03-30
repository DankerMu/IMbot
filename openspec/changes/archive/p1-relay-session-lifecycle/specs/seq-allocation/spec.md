# Capability: seq-allocation

## ADDED Requirements

### Requirement: Per-Session Monotonic Sequence

The relay SHALL allocate a monotonically increasing `seq` number for each event within a session. Sequence numbers MUST start at 1 for the first event and increment by 1 for each subsequent event. The relay is the sole allocator of seq values; companions and bridges MUST NOT assign seq.

#### Scenario: first event gets seq=1

WHEN the first event arrives for a new session with no prior events
THEN the allocated seq is `1`
AND the event is stored with `seq: 1` in `session_events`

#### Scenario: consecutive events get sequential seq

WHEN a session has 5 stored events (seq 1 through 5)
AND a new event arrives
THEN the allocated seq is `6`
AND the event is stored with `seq: 6`

#### Scenario: seq is independent per session

WHEN session A has 10 events and session B has 3 events
AND a new event arrives for session B
THEN the allocated seq for session B's event is `4`
AND session A's sequence is unaffected

---

### Requirement: Atomic Allocation

Seq allocation MUST be atomic to prevent duplicate or skipped seq values. The SQLite single-writer model (WAL mode) SHALL be relied upon for atomicity. The allocation query MUST read the current max seq and produce the next value in a single operation.

#### Scenario: concurrent events for same session still sequential

WHEN two events arrive nearly simultaneously for the same session
AND the session currently has seq up to 10
THEN SQLite serializes the writes
AND the first event gets seq `11`
AND the second event gets seq `12`
AND no seq values are skipped or duplicated

#### Scenario: allocation under high throughput

WHEN 50 events arrive in rapid succession for the same session
THEN all 50 events receive unique, consecutive seq values
AND the final seq equals the initial max + 50

---

### Requirement: Gap Detection

The seq allocator SHALL detect gaps in the sequence after allocation. A gap occurs when the newly allocated seq is greater than the expected next seq (previous max + 1). Gaps MUST be logged as warnings but SHALL NOT block event processing.

#### Scenario: gap detected when seq jumps

WHEN events are stored with seq 1, 2, 3 for a session
AND an external process or bug causes the next allocated seq to be 5 (skipping 4)
THEN a warning is logged: "Seq gap detected for session {id}: expected 4, got 5"
AND the event is still stored with seq 5
AND the `seq_gap_detected` error code is logged but does not fail the request

#### Scenario: no gap -- no warning

WHEN events are stored consecutively with no gaps
THEN no gap warning is logged

---

### Requirement: Relay is Sole Allocator

Events received from companions or bridges MUST NOT contain a `seq` field. If a `seq` field is present in an incoming event, it SHALL be ignored and overwritten by the relay's own allocation.

#### Scenario: incoming event with seq field is overwritten

WHEN a companion sends an event with `seq: 99`
AND the session's current max seq is 10
THEN the relay allocates seq `11` (ignoring the companion's value)
AND the event is stored with `seq: 11`
