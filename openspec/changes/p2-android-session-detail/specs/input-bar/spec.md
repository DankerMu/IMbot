# Capability: input-bar

A multi-line text input bar at the bottom of the session detail screen for continuing conversations with the AI agent.

## ADDED Requirements

### Requirement: Multi-Line Text Input

The `InputBar` SHALL provide a multi-line `TextField` that auto-grows up to 4 visible lines, then becomes scrollable. A send button SHALL be displayed to the right of the text field.

#### Scenario: Type text -- send button enabled

WHEN the user types "帮我重构这个模块" in the input field
THEN the send button becomes enabled (visually active)

#### Scenario: Empty text -- send disabled

WHEN the input field is empty
THEN the send button is disabled (grayed out, not clickable)

#### Scenario: Multi-line input auto-grows

WHEN the user types text that spans 3 lines
THEN the input field grows to show all 3 lines
WHEN the text spans 5 lines
THEN the input field shows 4 lines with the rest scrollable

---

### Requirement: Send Message Action

Tapping the send button SHALL send the text to the session via `POST /v1/sessions/:id/message` and clear the input field.

#### Scenario: Tap send -- text sent + input cleared

WHEN the user types "接下来帮我写测试" and taps the send button
THEN `POST /v1/sessions/{id}/message` is called with `{ "text": "接下来帮我写测试" }`
AND the input field is cleared
AND a `UserMessage` item appears in the message list immediately (optimistic)

---

### Requirement: Disabled State for Non-Running Sessions

The `InputBar` SHALL be disabled (non-interactive) when the session status is not `running`. A contextual placeholder SHALL indicate the reason.

#### Scenario: Session completed -- input bar disabled with "会话已结束" placeholder

WHEN the session status is `completed`
THEN the input field is disabled
AND the placeholder text is "会话已结束"
AND the send button is disabled

#### Scenario: Session running -- normal placeholder "输入消息..."

WHEN the session status is `running`
THEN the input field is enabled
AND the placeholder text is "输入消息..."

#### Scenario: Session failed -- input bar disabled

WHEN the session status is `failed`
THEN the input field is disabled
AND the placeholder text is "会话已失败"
