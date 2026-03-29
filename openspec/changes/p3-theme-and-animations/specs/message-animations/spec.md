# Capability: message-animations

Animations for message appearance, streaming indicators, and tool call card interactions.

## ADDED Requirements

### Requirement: New Message Fade-In and Slide-Up

When a new message appears in the SessionDetailScreen message list, it SHALL animate in with a combined fade-in (alpha 0→1) and slide-up (translateY +24dp→0) over 200ms using `Standard` easing. This applies to both user messages (echoed) and assistant messages.

#### Scenario: New message appears with fade-in and slide-up

WHEN a new assistant message arrives via WebSocket
THEN the message bubble appears at the bottom of the list with:
  - Alpha animates from 0 to 1 over 200ms
  - TranslateY animates from +24dp to 0 over 200ms
AND the list auto-scrolls if the user is at the bottom

#### Scenario: User message echo appears with same animation

WHEN the user sends a message and the echo event arrives
THEN the user message bubble appears with the same fade-in + slide-up animation

### Requirement: Streaming Cursor Blink

While an assistant message is streaming (receiving `assistant_delta` events), a block cursor character (▊) SHALL be appended to the end of the streamed text. The cursor SHALL blink with a rhythmic animation: visible for 500ms, invisible for 500ms, repeating.

#### Scenario: Streaming cursor blinks during active streaming

WHEN assistant text is being streamed (delta events arriving)
THEN a "▊" character appears at the end of the text
AND the cursor alternates between visible and invisible every 500ms

#### Scenario: Streaming cursor disappears when complete

WHEN the assistant message completes (final `assistant_message` event)
THEN the cursor "▊" is removed
AND the full message text is displayed without blinking

### Requirement: Tool Call Card Expand/Collapse Animation

ToolCallCard components SHALL animate expand and collapse. Tapping a collapsed card SHALL expand it to show details (tool input/output) with a height animation over 200ms. Tapping an expanded card SHALL collapse it with the same duration.

#### Scenario: Tool call expand -- smooth height animation

WHEN the user taps a collapsed ToolCallCard
THEN the card expands to reveal tool input and output details
AND the height animates smoothly over 200ms
AND surrounding content shifts down smoothly (no jump)

#### Scenario: Tool call collapse -- smooth animation

WHEN the user taps an expanded ToolCallCard
THEN the card collapses to show only the tool name and status
AND the height animates smoothly over 200ms

### Requirement: Staggered Message List Appearance

When the message list initially loads (entering SessionDetailScreen with cached messages), messages SHALL appear with a staggered animation: each message fades in with a 50ms delay after the previous one, up to a maximum of 10 staggered items. Messages beyond the 10th appear immediately.

#### Scenario: Many messages loading -- staggered appearance

WHEN the user enters SessionDetailScreen with 20 cached messages
THEN the first 10 visible messages appear with staggered fade-in (50ms delay between each)
AND messages 11-20 appear immediately (no stagger)
AND total animation duration for visible messages is ~500ms

#### Scenario: Scroll performance not affected by animations

WHEN the user scrolls rapidly through the message list
THEN scrolling remains smooth at 60fps
AND animations for off-screen items are skipped
AND only newly visible items animate if they haven't been seen before
