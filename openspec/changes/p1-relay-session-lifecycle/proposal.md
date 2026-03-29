# Proposal: p1-relay-session-lifecycle

## Why

The minimal relay (p0) implements only the happy path: create -> running -> completed. Production use requires the full session state machine with all transitions (resume, cancel, timeout, companion disconnect), comprehensive REST endpoints matching the API spec, robust seq allocation with gap detection, and audit logging for operational visibility. Without these, the relay cannot handle real-world failure scenarios, the Android app cannot resume or cancel sessions, and there is no operational audit trail.

## What Changes

1. **Full session state machine** -- Implement all state transitions defined in BUSINESS_LOGIC.md including: queued->failed (timeout, ack error), running->cancelled, running->failed (companion disconnect, runtime error), completed->running (resume), failed->running (retry). Each transition enforces guard conditions, emits the correct events, triggers FCM push for terminal states, and updates `sessions.updated_at` and `last_active_at`.
2. **Complete REST API** -- Implement all endpoints from API_SPEC.md with full request/response validation: `GET /sessions` (filters, pagination), `GET /sessions/:id`, `POST /sessions`, `POST /sessions/:id/resume`, `POST /sessions/:id/message`, `POST /sessions/:id/cancel`, `DELETE /sessions/:id`, `GET /sessions/:id/events?since_seq=`. Each endpoint returns proper error codes (`400`, `404`, `409`, `502`).
3. **Seq allocation** -- Atomic per-session monotonic seq allocation starting from 1. Relay is the sole allocator. SQLite single-writer model guarantees no conflicts. Add gap detection that logs warnings when seq values are non-contiguous.
4. **Audit logging** -- Write audit entries to the `audit_logs` table for key actions: `session.create`, `session.resume`, `session.cancel`, `session.delete`, `host.online`, `host.offline`, `root.add`, `root.remove`. Each entry includes action, session_id/host_id, detail JSON, and timestamp.

## Capabilities

- `session-state-machine-full`
- `session-rest-api-complete`
- `seq-allocation`
- `audit-logging`

## Affected Areas

- `packages/relay/src/session/orchestrator.ts` (state machine, transition logic)
- `packages/relay/src/session/seq.ts` (new -- seq allocator with gap detection)
- `packages/relay/src/routes/sessions.ts` (all session REST endpoints)
- `packages/relay/src/routes/events.ts` (catch-up endpoint with pagination)
- `packages/relay/src/audit/logger.ts` (new -- audit log writer)
- `packages/relay/src/ws/hub.ts` (broadcast integration for state changes)
- `packages/relay/src/fcm/adapter.ts` (push on terminal states)

## Risks

- The 30s queued-timeout must be a reliable timer even under load. If the event loop is blocked, timeouts may fire late. Use `setTimeout` with a cleanup mechanism on session transitions.
- Concurrent resume and cancel requests for the same session could race. The SQLite single-writer model serializes DB writes, but the guard check + update must be atomic (single `UPDATE ... WHERE status = ?`).
- Gap detection is informational only (logged warning). Automatic gap repair is out of scope for P1.

## References

- docs/engineering-spec/02_Technical_Design/BUSINESS_LOGIC.md (Transition Table, Seq Allocation, Event Pipeline)
- docs/engineering-spec/02_Technical_Design/API_SPEC.md (all REST endpoints, error codes)
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md (sessions, session_events, audit_logs tables)
