# Capability: tool-call-card

A collapsible card component for visualizing agent tool calls (e.g., file reads, grep, code edits). Shows tool name, status, arguments, and results.

## ADDED Requirements

### Requirement: Collapsible Card with Tool Name and Title

Each tool call SHALL be displayed as a card with a header row showing: tool icon, tool name, short title/description, and expand/collapse chevron. The body (when expanded) shows the arguments and result.

#### Scenario: Tool starts -- card appears expanded with spinner

WHEN a `tool_call_started` event arrives with `toolName = "Read"` and `title = "Reading main.kt"`
THEN a new `ToolCallCard` appears in the message list
AND the card is automatically expanded
AND a small `CircularProgressIndicator` spinner is shown next to the tool name
AND the args section shows the tool arguments (if available)

#### Scenario: Tool completes -- spinner removed + result shown + auto collapse

WHEN a `tool_call_completed` event arrives for the same tool call
THEN the spinner is removed
AND the result section shows the tool output
AND after 1 second, the card auto-collapses (showing only the header row)

---

### Requirement: Manual Expand/Collapse Toggle

The user SHALL be able to tap the card header to toggle between expanded and collapsed states at any time.

#### Scenario: Tap collapsed card -- expands

WHEN the user taps a collapsed `ToolCallCard`
THEN the card expands to show args and result
AND the chevron rotates to indicate expanded state

#### Scenario: Tap expanded card -- collapses

WHEN the user taps an expanded `ToolCallCard`
THEN the card collapses to show only the header row
AND the chevron rotates to indicate collapsed state

---

### Requirement: Multiple Tool Calls Independent

Each tool call card SHALL maintain its own expand/collapse state independently. Multiple tool calls in a session do not affect each other.

#### Scenario: Multiple tool calls render independently

WHEN 3 tool calls occur in sequence
THEN 3 separate `ToolCallCard` components are rendered
AND expanding one does not affect the others
AND each shows its own tool name, args, and result
