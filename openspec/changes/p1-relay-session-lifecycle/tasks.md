# Tasks: p1-relay-session-lifecycle

## 1. Session State Machine (Full)

- [ ] 1.1 Create `packages/relay/src/session/transitions.ts` with static transition table: `TRANSITIONS: Record<SessionStatus, SessionStatus[]>`
- [ ] 1.2 Implement `isValidTransition(from, to): boolean` helper function
- [ ] 1.3 Refactor `SessionOrchestrator.transition()` to use conditional UPDATE (`WHERE status = ?`) for atomic state changes
- [ ] 1.4 Implement `queued → running` transition: on companion/bridge ack ok, set `provider_session_id`, emit `session_started`
- [ ] 1.5 Implement `queued → failed` transition: on ack error, set `error_message`/`error_code`, emit `session_error`, trigger FCM
- [ ] 1.6 Implement queued timeout: 30s timer per `req_id`, cancel on ack, fire `queued → failed` with `command_timeout`
- [ ] 1.7 Implement `running → completed` transition: on `session_result` event, trigger FCM
- [ ] 1.8 Implement `running → failed` on `session_error` event: populate error fields, trigger FCM
- [ ] 1.9 Implement `running → failed` on companion disconnect: iterate running sessions for that host, transition each to failed with `host_disconnected`
- [ ] 1.10 Implement `POST /cancel` for running sessions: send cancel command, normally transition to `cancelled`, but preserve provider terminal state if it wins the race
- [ ] 1.11 Implement `completed → running` on `POST /resume`: send resume command, on ack ok emit `session_started`
- [ ] 1.12 Implement `failed → running` on `POST /resume`: send resume command, clear error fields on ack ok
- [ ] 1.13 Enforce `cancelled` as terminal: reject all operations with `409 state_conflict`
- [ ] 1.14 Ensure every transition updates `sessions.updated_at` and `last_active_at`
- [ ] 1.15 FCM push on `completed` and `failed` only (not `cancelled`)
- [ ] 1.16 Write unit tests: every transition in the table, invalid transitions return 409, timeout timer fires and cancels correctly, companion disconnect cascades

## 2. Session REST API (Complete)

- [ ] 2.1 Implement `GET /v1/sessions` with query params: `provider`, `status`, `host_id`, `limit` (default 50, max 200), `offset` (default 0)
- [ ] 2.2 Implement `GET /v1/sessions` SQL query with dynamic WHERE clause and ORDER BY `created_at DESC`
- [ ] 2.3 Implement `GET /v1/sessions` response with `total` count (separate COUNT query)
- [ ] 2.4 Implement `GET /v1/sessions/:id` returning full session object with all fields
- [ ] 2.5 Implement `GET /v1/sessions/:id` returning 404 for non-existent session
- [ ] 2.6 Implement `POST /v1/sessions` with request validation (provider, host_id, cwd, prompt required)
- [ ] 2.7 Implement `POST /v1/sessions` guard checks: host online, provider available
- [ ] 2.8 Implement `POST /v1/sessions/:id/resume` with state guard (completed or failed only)
- [ ] 2.9 Implement `POST /v1/sessions/:id/resume` host online check
- [ ] 2.10 Implement `POST /v1/sessions/:id/message` with state guard (running only) and host online check
- [ ] 2.11 Implement `POST /v1/sessions/:id/cancel` with state guard (running only)
- [ ] 2.12 Implement `DELETE /v1/sessions/:id` with CASCADE delete of events
- [ ] 2.13 Implement `GET /v1/sessions/:id/events?since_seq=&limit=` with `has_more` calculation
- [ ] 2.14 Add Fastify JSON Schema validation for all request bodies and query params
- [ ] 2.15 Write integration tests: each endpoint happy path, each error case (400, 404, 409, 502), pagination behavior, filter combinations

## 3. Seq Allocation

- [ ] 3.1 Create `packages/relay/src/session/seq.ts` with `allocateSeq(sessionId): number` function
- [ ] 3.2 Implement via `SELECT COALESCE(MAX(seq), 0) + 1 FROM session_events WHERE session_id = ?`
- [ ] 3.3 Add gap detection: compare allocated seq with expected (previous stored max + 1), log warning on mismatch
- [ ] 3.4 Ensure incoming events from companion/bridge have any `seq` field stripped before allocation
- [ ] 3.5 Write unit tests: first event gets 1, consecutive events are sequential, gap detection logs warning, incoming seq is overwritten

## 4. Audit Logging

- [ ] 4.1 Create `packages/relay/src/audit/logger.ts` with `writeAuditLog(action, sessionId?, hostId?, detail?): void`
- [ ] 4.2 Implement INSERT into `audit_logs` table with UUID generation for `id`
- [ ] 4.3 Wrap INSERT in try/catch: log error to stderr on failure, never throw
- [ ] 4.4 Add audit calls to `POST /v1/sessions` handler → `session.create`
- [ ] 4.5 Add audit calls to `POST /v1/sessions/:id/resume` handler → `session.resume`
- [ ] 4.6 Add audit calls to `POST /v1/sessions/:id/cancel` handler → `session.cancel` only when the relay actually lands on `cancelled`
- [ ] 4.7 Add audit calls to `DELETE /v1/sessions/:id` handler → `session.delete`
- [ ] 4.8 Add audit calls to host status changes → `host.online`, `host.offline`
- [ ] 4.9 Add audit calls to root CRUD → `root.add`, `root.remove`
- [ ] 4.10 Write unit tests: each action writes correct fields, detail is valid JSON, failure does not throw
