# Capability: approval-event-passthrough

## ADDED Requirements

### Requirement: Approval Events Are Stored Like Other Session Events

The relay SHALL accept `approval_required` and `approval_resolved` events as valid `EventType` values, allocate `seq`, store them in `session_events`, and broadcast them to subscribed Android clients.

#### Scenario: `approval_required` event arrives

WHEN the relay receives `{ session_id, event_type: "approval_required", payload }`
THEN the event is stored in `session_events`
AND it is broadcast over WebSocket as a normal `event` message
AND the session remains otherwise valid for subsequent events

#### Scenario: `approval_resolved` event arrives

WHEN the relay receives `{ session_id, event_type: "approval_resolved", payload }`
THEN the event is stored in `session_events`
AND it is broadcast over WebSocket as a normal `event` message

### Requirement: No Dedicated Mobile Approval UI In This Change

This reserved-path change SHALL NOT require an Approval Inbox, decision buttons, or mobile approve/reject actions.

#### Scenario: approval event arrives while no Inbox UI exists

WHEN Android receives an `approval_required` event
THEN the app is allowed to treat it as reserved / non-actionable data
AND the absence of an Inbox UI does NOT block this change from being considered complete
