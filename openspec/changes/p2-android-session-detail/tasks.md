# Tasks: p2-android-session-detail

## Detail Screen Layout

- [ ] 1.1 Create `SessionDetailScreen` composable with `Scaffold`: TopAppBar (back, title, status badge, overflow), content area, InputBar at bottom
- [ ] 1.2 Implement TopAppBar overflow menu: "取消会话" (if running), "删除会话", "复制全部输出"; actions call ViewModel methods with confirmation dialogs
- [ ] 1.3 Implement `DetailViewModel` with `DetailUiState` StateFlow: session, messages, isLoading, isConnected, canSend, scrollState
- [ ] 1.4 Implement `ConnectionBanner` overlay below TopAppBar: yellow during reconnect, green "已恢复" for 2s on reconnect, then hidden
- [ ] 1.5 Wire navigation: from session list card tap with shared element transition; back button returns to list; deep link from FCM push

## Message Bubble Component

- [ ] 2.1 Create `MessageBubble` composable: user variant (right-aligned, primaryContainer) and agent variant (left-aligned, surface + provider icon)
- [ ] 2.2 Implement streaming cursor `▊` animation: 500ms blink interval, shown when `isStreaming == true`, removed when false
- [ ] 2.3 Add relative timestamp below each bubble
- [ ] 2.4 Implement lazy rendering for messages > 5000 chars: render visible portion, load rest on scroll

## Markdown Renderer

- [ ] 3.1 Integrate Markdown rendering library (Markwon or Compose-native) with GFM support: headings, bold, italic, strikethrough, lists, nested lists, tables, blockquotes, links
- [ ] 3.2 Implement syntax highlighting for fenced code blocks: detect language hint, apply highlighting theme (dark/light aware)
- [ ] 3.3 Add copy button overlay on each code block: tap copies content to clipboard, shows checkmark feedback for 1s
- [ ] 3.4 Implement inline code rendering: monospace font with subtle background
- [ ] 3.5 Implement incremental rendering: delta text appends to existing rendered content without full re-render
- [ ] 3.6 Performance test: verify 10K char message renders < 16ms per frame; profile and optimize if needed

## Tool Call Card

- [ ] 4.1 Create `ToolCallCard` composable: header row (icon, tool name, title, chevron), expandable body (args, result)
- [ ] 4.2 Implement running state: CircularProgressIndicator spinner in header, auto-expanded
- [ ] 4.3 Implement completed state: remove spinner, show result, auto-collapse after 1 second
- [ ] 4.4 Implement manual expand/collapse toggle on header tap with chevron rotation animation
- [ ] 4.5 Ensure each ToolCallCard maintains independent expand/collapse state

## Input Bar

- [ ] 5.1 Create `InputBar` composable: multi-line TextField (maxVisibleLines=4), send IconButton
- [ ] 5.2 Implement send action: call `viewModel.sendMessage(text)`, clear input, add optimistic UserMessage to list
- [ ] 5.3 Implement disabled state: when `session.status != running`, disable TextField + send button, show contextual placeholder ("会话已结束" / "会话已失败")
- [ ] 5.4 Implement send button state: enabled when text is non-empty AND session is running; disabled otherwise

## Auto-Scroll Behavior

- [ ] 6.1 Track scroll position in `LazyListState`: detect if user is within 100dp of the bottom
- [ ] 6.2 Implement auto-scroll: on new message item, if within 100dp threshold, call `animateScrollToItem(lastIndex)`
- [ ] 6.3 Implement pause: when user scrolls > 100dp from bottom, set `autoScrollPaused = true`
- [ ] 6.4 Implement "new messages" FAB: show when paused, display "↓ N" counter, increment on each new message
- [ ] 6.5 Implement FAB tap: smooth scroll to bottom, reset counter, set `autoScrollPaused = false`, hide FAB
- [ ] 6.6 Implement manual resume: when user scrolls back to within 100dp of bottom, auto-resume and hide FAB

## Detail Status Bar

- [ ] 7.1 Create `StatusBar` composable: 2dp height, full width, below TopAppBar
- [ ] 7.2 Implement status-to-color mapping: running=green, completed=green, failed=red, cancelled=gray, queued=gray
- [ ] 7.3 Implement pulse animation for running status: alpha oscillates 0.3-1.0 over 750ms using `rememberInfiniteTransition`
- [ ] 7.4 Implement color morph animation: 300ms `animateColorAsState` transition when status changes

## Event Processing

- [ ] 8.1 Implement `EventProcessor` in ViewModel: maps raw events to `MessageItem` sealed class instances
- [ ] 8.2 Implement delta aggregation: consecutive `assistant_delta` events merge into single `AgentMessage` with accumulated content
- [ ] 8.3 Implement `assistant_message` finalization: replace streaming AgentMessage with final version (isStreaming=false)
- [ ] 8.4 Implement tool call lifecycle: `tool_call_started` creates ToolCall(isRunning=true), `tool_call_completed` updates it
- [ ] 8.5 Implement session event subscription: on screen enter, subscribe to WS events for the session; on exit, unsubscribe
- [ ] 8.6 Implement catch-up on reconnect: after WS reconnect, call `GET /v1/sessions/:id/events?since_seq=` and merge into message list
