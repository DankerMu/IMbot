# OpenSpec Execution Index

OpenSpec documents the implementation slices for IMbot. The root source of truth for product scope remains:

- `docs/PRD.md` for product requirements and roadmap milestones
- `docs/engineering-spec/` for architecture, data model, APIs, testing, and milestone planning
- `docs/specs/ui-ux-rd-spec/` for Android/UI execution detail

OpenSpec is the execution layer that breaks those requirements into independently deliverable changes.

## How To Read `p0-p3`

`p0-p3` prefixes are delivery bands, not a strict 1:1 copy of PRD `Phase 0-3`.

| Prefix | Meaning                                                                          | Typical PRD Milestone |
| ------ | -------------------------------------------------------------------------------- | --------------------- |
| `p0`   | Foundation and feasibility slices needed to prove the system can work end-to-end | Mostly PRD Phase 0    |
| `p1`   | Core backend / protocol / reliability capabilities required for MVP completeness | Mostly PRD Phase 1    |
| `p2`   | Android product surfaces and user-facing flows that complete the usable app      | Spans PRD Phase 1-2   |
| `p3`   | Hardening, visual polish, cleanup, and reserved future capability                | Mostly PRD Phase 2-3  |

If you need milestone planning, use `docs/engineering-spec/06_Implementation/TASK_BREAKDOWN.md`.
If you need requirement-level mapping, use `docs/engineering-spec/01_Requirements/REQUIREMENTS_MATRIX.md`.

## Requirement Mapping

| PRD Requirement      | Primary OpenSpec Changes                                                                                                                                                                                                                                                             |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| FR-01 Provider 管理  | `p0-openclaw-bridge`, `p1-companion-session-management`, `p2-android-new-session`, `p2-android-session-list`                                                                                                                                                                         |
| FR-02 Workspace 管理 | `p1-relay-workspace-api`, `p1-companion-session-management`, `p2-android-new-session`, `p2-android-workspace-settings`                                                                                                                                                               |
| FR-03 会话创建       | `p0-relay-minimal`, `p0-companion-minimal`, `p0-android-prototype`, `p2-android-new-session`, `p3-initial-user-message-echo`                                                                                                                                                          |
| FR-04 会话恢复       | `p1-relay-session-lifecycle`, `p1-companion-session-management`, `p0-openclaw-bridge`, `p2-android-workspace-settings`, `p2-android-session-detail`, `persistent-interactive-sessions`, `p3-session-detail-hardening`                                                                |
| FR-05 多会话并发     | `p1-relay-session-lifecycle`, `p1-reconnect-and-catchup`, `p2-android-session-list`, `p3-error-ux-and-cleanup`                                                                                                                                                                       |
| FR-06 流式输出与渲染 | `p0-android-prototype`, `p2-android-session-detail`, `p3-theme-and-animations`, `persistent-interactive-sessions`, `p3-session-detail-hardening`, `p3-message-rendering-polish`, `p3-message-copy`, `p3-tool-call-rich-display`, `p3-ask-user-question-fix`, `p3-longpress-menu-fix`, `p3-initial-user-message-echo` |
| FR-07 断线恢复       | `p1-reconnect-and-catchup`, `p3-error-ux-and-cleanup`                                                                                                                                                                                                                                |
| FR-08 FCM 推送       | `p1-fcm-push`                                                                                                                                                                                                                                                                        |
| FR-09 主题与外观     | `p2-android-workspace-settings`, `p3-theme-and-animations`, `p3-visual-redesign`, `p3-message-rendering-polish`, `p3-visual-polish-v2`, `p3-visual-polish-v3`, `p3-design-md-ui-refresh`                                                                                              |
| FR-10 审批保留       | `p3-approval-path-reserved`, `p3-mobile-skill-interaction`                                                                                                                                                                                                                           |

## Change Index

### `p0` Foundation / Feasibility

- `p0-monorepo-and-wire` — monorepo scaffold + shared wire protocol types
- `p0-relay-minimal` — relay bootstrap, auth, SQLite, WS hub, basic session orchestration
- `p0-companion-minimal` — relay client, heartbeat, CLI adapter
- `p0-android-prototype` — minimal Android debug surface for end-to-end validation
- `p0-openclaw-bridge` — local gateway bridge and event translation

### `p1` Core MVP Capability

- `p1-relay-session-lifecycle` — full session state machine, REST API, seq allocation, audit logging
- `p1-relay-workspace-api` — hosts, roots, browsing, host status tracking
- `p1-companion-session-management` — list/resume/send/cancel, workspace sync, book provider
- `p1-reconnect-and-catchup` — reconnect loops and `since_seq` catch-up
- `p1-fcm-push` — token management, push send/receive, deep-link handling

### `p2` Android Product Surfaces

- `p2-android-session-list` — home list, provider filter, Room cache, bottom navigation
- `p2-android-session-detail` — detail timeline, markdown, tool calls, input bar, auto-scroll
- `p2-android-new-session` — provider → directory → prompt flow
- `p2-android-workspace-settings` — workspace manager, settings, onboarding

### Cross-Cutting (Completed)

- `persistent-interactive-sessions` — stream-json bidirectional protocol, `idle` session state, viewer CLI, Android idle UX ✅
- `dual-session-persistence` — 双端 session 持久化：per-provider configDir/projectsDir，SessionIndex 元数据增强，discovery/reconciler 补强
- `stream-json-control-protocol` — stream-json 控制协议：AskUserQuestion 双向 control_request/control_response，relay answer_interactive_tool 通道
- `visual-polish-v4` — 详情页 UI 润色 v4：CommandChip inline variant、InputBar 状态驱动、MarkdownText 表格/公式、DetailUtils 重构

### `p1` Active

- `p1-dual-session-sync` — 双端 session 同步：修复 cwd 过滤过严导致 Android 创建的 session 在 native CLI resume 中不可见
- `p1-session-transcript-sync` — 远端 transcript 增量同步：让 Mac 原生 CLI 追加的消息增量回灌 relay，并在 Android 详情页同步显示

### `p2` Active

- `p2-empty-session-creation` — 空 session 创建：允许选完 provider + 目录后直接创建 session，无需首条 prompt

### `p3` Hardening / Future

- `p3-idle-session-delete` — idle interactive session delete: allow deleting `idle` Claude/Book sessions without `state_conflict` by reusing the existing cancel-then-delete path
- `p3-initial-user-message-echo` — 首条 prompt 回显：对 companion-backed session 在启动时补写 `user_message`，避免详情页吞掉第一条用户消息
- `p3-session-list-multi-select` — Android 会话列表长按进入多选模式，支持批量删除多个会话
- `p3-context-usage-display` — session 上下文用量实时展示：在 Android detail 顶部状态栏显示 token 计数和上下文窗口使用进度
- `p3-design-md-ui-refresh` — 基于 awesome-design-md 的 Android UI 设计系统刷新：暖中性色主题、首页/新建/详情壳层重构
- `p3-theme-and-animations` — theme system, transitions, motion, code theme
- `p3-error-ux-and-cleanup` — three-layer error UX, purge job, connection stability
- `p3-approval-path-reserved` — reserved non-bypass permission mode and approval event plumbing
- `p3-ask-user-question-fix` — fix AskUserQuestion input parsing (questions[] nested format), option label+description rendering
- `p3-longpress-menu-fix` — stabilize long-press menu trigger, extend copy to all message types, fix BottomSheet expand
- `p3-tool-call-rich-display` — categorized tool rendering (Bash terminal, Read/Write file, Search, Diff), status indicators, CodePilot-style
- `p3-visual-polish-v2` — assistant bubble removal, user bubble dark inversion, message spacing, pill input bar, code block folding, status dedup
- `p3-visual-polish-v3` — assistant bubble restoration with border, dual avatars, auto-stick scroll fix, markdown trailing padding, top-bar status badge

### `p3` Completed (Archived)

- `p3-session-detail-hardening` — cancelled-session recovery, offline formula rendering, markdown table correctness ✅
- `p3-mobile-skill-interaction` — slash command recognition, AskUserQuestion/approval interactive cards ✅
- `p3-message-rendering-polish` — Obsidian-level markdown rendering: typography, code blocks, quotes, tables, lists ✅
- `p3-message-copy` — per-message copy, text selection mode, long-press action menu ✅
- `p3-visual-redesign` — Apple-aesthetic UI overhaul: design tokens, all 6 pages, spring animations ✅
