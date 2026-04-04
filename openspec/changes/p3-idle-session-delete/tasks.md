# Tasks: Idle Session Delete Without State Conflict

## 1. Relay

- [ ] 1.1 Allow `DELETE /sessions/:id` to delete `idle` sessions that already have `provider_session_id`
- [ ] 1.2 Reuse the existing best-effort `cancel_session -> cancelled -> delete` path for both `running` and `idle`

## 2. Tests

- [ ] 2.1 Update relay contract test: idle interactive session delete returns `204` instead of `409`
- [ ] 2.2 Add relay unit coverage for idle interactive session delete issuing `cancel_session`

## 3. Docs

- [ ] 3.1 Update API spec to document that running/idle interactive sessions are best-effort cancelled before delete
