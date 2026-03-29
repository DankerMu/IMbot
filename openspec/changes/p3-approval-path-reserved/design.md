# Design: p3-approval-path-reserved

## Scope

This change defines the reserved non-bypass path only:

1. Preserve `permission_mode` from API request through relay persistence, companion command dispatch, and CLI spawn arguments.
2. Persist and broadcast approval-related events without requiring dedicated mobile UI.
3. Keep default behavior unchanged: `bypassPermissions`.

## Non-Goals

- No approve / reject endpoint
- No Android Approval Inbox
- No banner / dialog / action sheet for approval decisions
- No change to the default runtime mode

## Flow

```
Android / caller
    │ POST /v1/sessions { permission_mode }
    ▼
Relay
    │ persist permission_mode on session
    │ send create_session { permission_mode }
    ▼
Companion
    │ spawn claude/book with --permission-mode <value>
    ▼
Runtime emits approval_required / approval_resolved (future-enabled path)
    ▼
Relay stores session_events + broadcasts to subscribers
```

## Notes

- For current MVP operation, `permission_mode` should still be omitted or set to `bypassPermissions`.
- Approval events remain observable system events even before dedicated UI exists.
