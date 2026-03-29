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

## 4. Tests

- [ ] 4.1 Add unit coverage for `permission_mode` propagation
- [ ] 4.2 Add unit coverage for approval event storage / broadcast
- [ ] 4.3 Keep integration coverage for `permission_mode=default → approval_required event`
