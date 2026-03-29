# Capability: detail-screen-layout

The main layout structure of the session detail screen, including TopAppBar, status bar, message list, input bar, and connection banner.

## ADDED Requirements

### Requirement: TopAppBar with Session Info and Overflow Menu

The `TopAppBar` SHALL display a back button, the session title (provider name + last path segment of workspace), a status badge, and an overflow menu (three-dot icon). The overflow menu SHALL contain actions appropriate to the session status.

#### Scenario: Navigate from list -- shared element transition

WHEN the user taps a session card in the list
THEN the app navigates to `SessionDetailScreen` with a shared element transition
AND the `TopAppBar` shows the session title and status badge

#### Scenario: Overflow menu shows correct actions based on status

WHEN the session status is `running`
THEN the overflow menu contains: "取消会话", "删除会话", "复制全部输出"
WHEN the session status is `completed` or `failed`
THEN the overflow menu contains: "删除会话", "复制全部输出" (no "取消会话")

#### Scenario: Back button returns to list

WHEN the user taps the back button in TopAppBar
THEN the app navigates back to the session list

---

### Requirement: Message List with LazyColumn

The screen SHALL display a `LazyColumn` containing `MessageItem` components (user messages, agent messages, tool call cards, status changes). The list SHALL use stable keys (event seq or generated ID) for efficient recomposition.

#### Scenario: Messages render in correct order

WHEN the session has events [user_message, assistant_delta x5, tool_call_started, tool_call_completed, assistant_delta x3, session_result]
THEN the LazyColumn renders: UserMessage, AgentMessage (aggregated), ToolCallCard, AgentMessage (aggregated), StatusChange
AND items appear in chronological order

---

### Requirement: ConnectionBanner Overlay

A `ConnectionBanner` SHALL overlay the content area below the TopAppBar when the WebSocket connection is lost. The banner SHALL show reconnection status.

#### Scenario: WS disconnect -- banner shown

WHEN the WebSocket connection is lost
THEN a yellow banner appears below the TopAppBar: "连接中断，正在重连..."
WHEN the connection is restored
THEN the banner turns green "已恢复" for 2 seconds, then disappears
