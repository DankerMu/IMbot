# Tasks: p3-approval-path-reserved

## 1. Permission Mode Propagation

- [ ] 1.1 Verify `POST /v1/sessions` request validation preserves caller-supplied `permission_mode`
- [ ] 1.2 Persist `permission_mode` on `sessions` records and expose it via `GET /v1/sessions/:id`
- [ ] 1.3 Ensure companion `create_session` command forwards `permission_mode` unchanged
- [ ] 1.4 Ensure CLI adapter spawns `claude` / `book` with the supplied `--permission-mode <value>`

## 2. Approval Event Passthrough

- [ ] 2.1 Treat `approval_required` as a normal stored `session_event`
- [ ] 2.2 Treat `approval_resolved` as a normal stored `session_event`
- [ ] 2.3 Broadcast approval events over Android WebSocket subscriptions without schema changes

## 3. Guardrails

- [ ] 3.1 Document that default mode remains `bypassPermissions`
- [ ] 3.2 Explicitly mark Inbox UI / decision actions as out of scope

## Unit Tests: Permission Mode Propagation

- [ ] 4.1 POST /v1/sessions with permission_mode="default" → session record has permission_mode="default"
- [ ] 4.2 POST /v1/sessions with permission_mode="bypassPermissions" → session record has permission_mode="bypassPermissions"
- [ ] 4.3 POST /v1/sessions without permission_mode → defaults to "bypassPermissions"
- [ ] 4.4 GET /v1/sessions/:id → response includes permission_mode field
- [ ] 4.5 Companion create_session command receives permission_mode from relay
- [ ] 4.6 CLI adapter spawns with --permission-mode matching the session's permission_mode
- [ ] 4.7 Round-trip: Android sends "default" → relay stores → companion receives → CLI flag matches

## Unit Tests: Approval Events

- [ ] 5.1 approval_required event stored as session_event with correct type and payload
- [ ] 5.2 approval_resolved event stored as session_event with correct type and payload
- [ ] 5.3 approval_required broadcast to subscribed Android WS clients
- [ ] 5.4 approval_resolved broadcast to subscribed Android WS clients
- [ ] 5.5 Approval events do not alter session status (session stays running)
- [ ] 5.6 Approval event payload includes tool_name, description, and call_id

## Integration Tests: End-to-End Approval Flow

- [ ] 6.1 Create session with permission_mode="default" → session runs → CLI emits approval_required → Android WS receives event with correct payload
- [ ] 6.2 approval_required followed by approval_resolved → both stored in order, both broadcast
- [ ] 6.3 Session with permission_mode="bypassPermissions" → no approval events emitted

## Guardrail Tests

- [ ] 7.1 Default permission_mode in Android UI is "bypassPermissions" (verify NewSessionViewModel constant)
- [ ] 7.2 Existing sessions created before this change still work (no migration needed)
- [ ] 7.3 Approval events render as generic StatusChange in Android detail screen (no Inbox UI)
