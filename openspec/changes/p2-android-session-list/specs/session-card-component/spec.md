# Capability: session-card-component

A Material 3 card component representing a single session in the list. Displays provider, workspace, prompt summary, status, and timestamp. Supports swipe-to-delete and long-press context menu.

## ADDED Requirements

### Requirement: Card Content Layout

Each `SessionCard` SHALL display: the provider icon (left), workspace path showing the last 2 path segments, prompt summary truncated to 50 characters, a status indicator colored dot, and a relative timestamp (e.g., "3 分钟前").

#### Scenario: All status colors correct

WHEN sessions with statuses `running`, `completed`, `failed`, `queued`, and `cancelled` are displayed
THEN `running` shows a green pulsing dot
AND `completed` shows a static green dot
AND `failed` shows a static red dot
AND `queued` shows a static gray dot
AND `cancelled` shows a static gray dot

#### Scenario: Prompt longer than 50 chars -- truncated with "..."

WHEN a session has `initialPrompt = "帮我重构这个项目的整体架构，包括前端组件、后端接口、数据库模型和测试用例"` (> 50 chars)
THEN the card displays the first 50 characters followed by "..."

#### Scenario: Long workspace path -- last 2 segments shown

WHEN a session has `workspaceCwd = "/Users/danker/Desktop/AI-vault/IMbot/packages/relay"`
THEN the card displays `packages/relay` (last 2 segments)

---

### Requirement: Swipe Left to Delete

Swiping a `SessionCard` to the left SHALL reveal a red delete background. Releasing the swipe SHALL show a confirmation dialog before deletion.

#### Scenario: Swipe left -- red delete background revealed

WHEN the user swipes a session card to the left
THEN a red background with a delete icon is revealed behind the card

#### Scenario: Release -- confirmation dialog

WHEN the user releases after swiping past the threshold
THEN a confirmation dialog appears: "确认删除此会话？"
AND the dialog has "取消" and "删除" buttons

#### Scenario: Confirm delete -- API call + card removed with animation

WHEN the user taps "删除" in the confirmation dialog
THEN `DELETE /v1/sessions/{id}` is called
AND the card is removed from the list with a fade-out animation
AND the session is removed from the Room cache

---

### Requirement: Long Press Context Menu

Long pressing a `SessionCard` SHALL display a context menu with options "归档" (Archive) and "删除" (Delete).

#### Scenario: Long press -- context menu (Archive/Delete)

WHEN the user long-presses a session card
THEN a popup context menu appears with "归档" and "删除" options
AND tapping "删除" shows the same confirmation dialog as swipe-to-delete
AND tapping outside the menu dismisses it

---

### Requirement: Tap Navigation

Tapping a `SessionCard` SHALL navigate to the `SessionDetailScreen` for that session.

#### Scenario: Tap card -- navigate to detail

WHEN the user taps a session card
THEN the app navigates to `SessionDetailScreen` with the session's ID
AND a shared element transition animates the card into the detail view
