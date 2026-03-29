# Proposal: p3-approval-path-reserved

## Why

The PRD and engineering spec explicitly reserve a future approval path: sessions default to `bypassPermissions`, but the system must retain enough schema, wire, and runtime plumbing to support non-bypass execution later. Today that intent is spread across root docs, test placeholders, and enum definitions, but it has no dedicated OpenSpec change. That makes FR-10 hard to trace and easy to implement inconsistently.

This change gives FR-10 a clear OpenSpec home while preserving the product decision that approval UX is not part of MVP.

## What Changes

| Area | Change |
|------|--------|
| Permission mode propagation | Relay, wire, companion, and CLI adapter explicitly support `permission_mode` as an end-to-end field. Default remains `bypassPermissions`; non-default values are preserved instead of normalized away. |
| Approval event passthrough | `approval_required` and `approval_resolved` events are treated as first-class stored/broadcast events so future UI can consume them later. |
| Scope guardrail | No Approval Inbox UI, no approve/reject decision surface, no mobile action flow in this change. The path remains reserved and disabled by default. |

## Capabilities

- `permission-mode-propagation`
- `approval-event-passthrough`

## Dependencies

- `p0-monorepo-and-wire` for event / command types
- `p0-companion-minimal` for CLI spawn plumbing
- `p1-relay-session-lifecycle` for session persistence and event broadcasting

## Risks

| Risk | Mitigation |
|------|-----------|
| Claude / book runtime semantics differ across versions when not using `bypassPermissions` | Keep feature default-off; treat first activation as controlled validation work |
| Engineering assumes FR-10 includes Inbox UI | Call out non-goals explicitly in proposal, design, and tasks |
| Approval events exist but UI ignores them | This is intentional for the reserved path; events are persisted for observability and future implementation |

## References

- docs/PRD.md (FR-10, Phase 3)
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md (`approvals`, `permission_mode`, approval events)
- docs/engineering-spec/05_Testing/TEST_PLAN.md (`IT-10`)
