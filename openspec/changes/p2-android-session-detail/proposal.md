# Proposal: p2-android-session-detail

## Why

The session detail screen is the core experience of IMbot -- viewing real-time streaming output from Claude Code, book, or OpenClaw sessions. It must render Markdown with syntax highlighting, visualize tool calls, support conversation continuation, and handle auto-scroll intelligently. This is where the user spends the most time, so performance and usability are critical.

## What Changes

| Area | Change |
|------|--------|
| Detail screen layout | `TopAppBar` with back button, session title, status badge, overflow menu. Status color bar. `LazyColumn` of messages. `InputBar` at bottom. `ConnectionBanner` overlay. |
| Message bubble component | User messages right-aligned (Primary container). Agent messages left-aligned (Surface + provider icon). Streaming cursor blink. Timestamp. |
| Markdown renderer | Full GFM support. Syntax highlighting for code blocks. Copy button on code blocks. Incremental rendering for streaming deltas. |
| Tool call card | Collapsible card: tool name + title. Expanded: args + result. Running state spinner. Auto-expand running, auto-collapse completed. |
| Input bar | Multi-line text field (max 4 lines visible). Send button. Enabled only when session is running. Context-aware placeholder. |
| Auto-scroll behavior | Auto-scroll on new messages. Manual scroll up pauses auto-scroll. "New messages" FAB with counter. Tap to resume. |
| Detail status bar | 2dp color strip below TopAppBar matching session status. Pulse animation for running. |

## Capabilities

- `detail-screen-layout`
- `message-bubble-component`
- `markdown-renderer`
- `tool-call-card`
- `input-bar`
- `auto-scroll-behavior`
- `detail-status-bar`

## Affected Areas

- `packages/android/.../ui/detail/SessionDetailScreen.kt` -- main detail Compose UI
- `packages/android/.../ui/detail/DetailViewModel.kt` -- state management, event processing
- `packages/android/.../ui/detail/MessageBubble.kt` -- message component
- `packages/android/.../ui/detail/ToolCallCard.kt` -- tool call component
- `packages/android/.../ui/detail/MarkdownRenderer.kt` -- markdown rendering
- `packages/android/.../ui/detail/InputBar.kt` -- input component
- `packages/android/.../data/repository/SessionRepository.kt` -- event stream + message send

## Dependencies

- WebSocket event stream from `p0-relay-minimal` (event delivery).
- REST API `POST /v1/sessions/:id/message`, `POST /v1/sessions/:id/cancel` from `p1-relay-session-lifecycle`.
- Event catch-up via `GET /v1/sessions/:id/events?since_seq=` from `p1-reconnect-and-catchup`.
- Component specs from `docs/specs/ui-ux-rd-spec/02_Components/COMPONENTS.md` (C-02, C-03, C-04, C-08).

## Risks

| Risk | Mitigation |
|------|-----------|
| Markdown rendering perf on 10K+ char messages | Incremental rendering: delta events append without re-rendering entire message; lazy rendering for long messages |
| Auto-scroll interferes with user reading | 100dp scroll-up threshold pauses auto-scroll; explicit resume via FAB |
| Memory pressure from long sessions (5000+ events) | Aggregate consecutive `assistant_delta` events into single `AgentMessage` items; limit event history in memory |

## References

- docs/specs/ui-ux-rd-spec/04_Pages/02_SessionDetailScreen.md
- docs/specs/ui-ux-rd-spec/02_Components/COMPONENTS.md (C-02: MessageBubble, C-03: ToolCallCard, C-04: MarkdownRenderer, C-08: InputBar, C-10: ConnectionBanner)
- docs/engineering-spec/02_Technical_Design/API_SPEC.md (session events, message, cancel endpoints)
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md (EventType enum, event→message mapping)
