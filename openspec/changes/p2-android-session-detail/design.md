# Design: p2-android-session-detail

## Key Decisions

### 1. Event-to-MessageItem Aggregation

**Decision**: Raw WebSocket events are aggregated into `MessageItem` sealed class instances. Consecutive `assistant_delta` events are merged into a single `AgentMessage`. This aggregated list drives the `LazyColumn`.

**Rationale**: Rendering one `LazyColumn` item per delta event would create thousands of items for a typical session. Aggregating into logical messages (user turn, agent turn, tool call) keeps the list manageable and aligns with chat UX expectations.

### 2. Incremental Markdown Rendering

**Decision**: When an `assistant_delta` arrives, the delta text is appended to the current `AgentMessage.content` and only the last paragraph/block of the Markdown is re-rendered, not the entire message.

**Rationale**: Re-rendering a 10K+ character Markdown message on every delta (which arrives every ~50ms) would cause frame drops. Incremental rendering keeps the UI at 60fps.

**Trade-off**: Requires a Markdown renderer that supports partial updates. If using Markwon, this means appending to the Spannable. If using Compose-native, this means diffing the AnnotatedString.

### 3. Auto-Scroll State Machine

**Decision**: Auto-scroll is ON by default. When the user scrolls up more than 100dp from the bottom, auto-scroll is paused and a "new messages" FAB appears. The FAB shows a counter of new messages received while paused. Tapping the FAB smooth-scrolls to bottom and resumes auto-scroll. Manually scrolling back to the bottom also resumes auto-scroll.

**Rationale**: Users need to read previous output without being yanked to the bottom. The 100dp threshold prevents accidental pause from small scroll adjustments.

### 4. Lazy Rendering for Long Messages

**Decision**: Messages exceeding 5000 characters use lazy rendering (only the visible portion is fully rendered; the rest is rendered on demand as the user scrolls within the message).

**Rationale**: Per the UI spec (C-02), long messages should not cause lag. Lazy rendering prevents the worst-case scenario of a single 50K-character message blocking the UI thread.

### 5. Tool Call Card Auto-Expand/Collapse

**Decision**: Tool call cards auto-expand when the tool starts running (showing spinner + args). When the tool completes, the result is shown, then the card auto-collapses after a brief pause (1 second). The user can manually toggle expand/collapse at any time.

**Rationale**: Auto-expand draws attention to active tool execution. Auto-collapse after completion prevents the screen from being dominated by completed tool details, while giving the user a moment to see the result.

### 6. InputBar State Tied to Session Status

**Decision**: The `InputBar` is enabled only when `session.status == running`. For `completed`/`failed`/`cancelled`, the input is disabled with a descriptive placeholder.

**Rationale**: Sending a message to a non-running session would fail with `409 state_conflict`. Disabling the input prevents the error and communicates session state clearly.

## State Architecture

```
DetailViewModel
    │
    ├── SessionRepository
    │   ├── REST: GET /v1/sessions/:id (session info)
    │   ├── REST: GET /v1/sessions/:id/events?since_seq= (catch-up)
    │   ├── REST: POST /v1/sessions/:id/message (send)
    │   ├── REST: POST /v1/sessions/:id/cancel (cancel)
    │   ├── WS: subscribe session events (real-time)
    │   └── Room: cache events locally
    │
    └── DetailUiState (StateFlow)
        ├── session: SessionDetail?
        ├── messages: List<MessageItem>   // aggregated
        ├── isLoading: Boolean
        ├── isConnected: Boolean
        ├── isCatchingUp: Boolean
        ├── error: String?
        ├── canSend: Boolean
        └── scrollState: ScrollState (auto/paused, newMsgCount)
```

## Event Processing Pipeline

```
WS event arrives (or catch-up event)
        │
        ▼
    EventProcessor.process(event)
        │
        ├── user_message → append UserMessage
        ├── assistant_delta → append to last AgentMessage.content (or create new)
        ├── assistant_message → finalize last AgentMessage (isStreaming=false)
        ├── tool_call_started → append ToolCall(isRunning=true)
        ├── tool_call_completed → update ToolCall(isRunning=false, result=...)
        ├── session_status_changed → append StatusChange + update session.status
        └── session_error → append StatusChange(failed) + update session.status
        │
        ▼
    messages list updated → UI recomposes
        │
        ▼
    if autoScroll enabled → scroll to bottom
    else → increment newMsgCount on FAB
```
