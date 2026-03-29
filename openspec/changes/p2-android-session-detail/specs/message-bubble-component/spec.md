# Capability: message-bubble-component

Chat-style message bubbles for user and agent messages in the session detail view. Supports streaming indicators and provider-specific styling.

## ADDED Requirements

### Requirement: User Message Right-Aligned

User messages SHALL be right-aligned with a Primary container color background. The message text is rendered as plain text (no Markdown). A timestamp is shown below the bubble.

#### Scenario: User message renders right-aligned

WHEN a `UserMessage` item is displayed
THEN the bubble is right-aligned
AND the background uses `MaterialTheme.colorScheme.primaryContainer`
AND the text is rendered as plain text
AND a relative timestamp is shown below the bubble

---

### Requirement: Agent Message Left-Aligned with Provider Icon

Agent messages SHALL be left-aligned with a Surface color background. A provider-specific icon SHALL be displayed to the left of the bubble. The content SHALL be rendered as Markdown via the `MarkdownRenderer`.

#### Scenario: Agent message renders left-aligned with icon

WHEN an `AgentMessage` item is displayed for a Claude Code session
THEN the bubble is left-aligned
AND the background uses `MaterialTheme.colorScheme.surface`
AND a Claude Code provider icon appears to the left
AND the content is rendered as Markdown

---

### Requirement: Streaming Indicator

While an agent message is being streamed (`isStreaming == true`), a blinking cursor character `▊` SHALL be displayed at the end of the content.

#### Scenario: Streaming message shows cursor

WHEN an `AgentMessage` has `isStreaming = true`
THEN the blinking cursor `▊` is rendered at the end of the message content
AND the cursor blinks with a 500ms interval animation

#### Scenario: Streaming ends -- cursor removed

WHEN the `assistant_message` event arrives and `isStreaming` becomes `false`
THEN the blinking cursor is removed
AND the final content is displayed as static Markdown

---

### Requirement: Performance for Long Messages

Very long messages (10K+ characters) SHALL render without causing UI lag (< 16ms per frame).

#### Scenario: Very long message -- renders without lag

WHEN an agent message contains 10,000+ characters of Markdown
THEN the message renders without dropping frames
AND scrolling through the message is smooth (60fps)
AND the initial render does not block the main thread
