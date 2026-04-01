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

| PRD Requirement      | Primary OpenSpec Changes                                                                                                                            |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-01 Provider 管理  | `p0-openclaw-bridge`, `p1-companion-session-management`, `p2-android-new-session`, `p2-android-session-list`                                        |
| FR-02 Workspace 管理 | `p1-relay-workspace-api`, `p1-companion-session-management`, `p2-android-new-session`, `p2-android-workspace-settings`                              |
| FR-03 会话创建       | `p0-relay-minimal`, `p0-companion-minimal`, `p0-android-prototype`, `p2-android-new-session`                                                        |
| FR-04 会话恢复       | `p1-relay-session-lifecycle`, `p1-companion-session-management`, `p0-openclaw-bridge`, `p2-android-workspace-settings`, `p2-android-session-detail`, `persistent-interactive-sessions` |
| FR-05 多会话并发     | `p1-relay-session-lifecycle`, `p1-reconnect-and-catchup`, `p2-android-session-list`, `p3-error-ux-and-cleanup`                                      |
| FR-06 流式输出与渲染 | `p0-android-prototype`, `p2-android-session-detail`, `p3-theme-and-animations`, `persistent-interactive-sessions`                                     |
| FR-07 断线恢复       | `p1-reconnect-and-catchup`, `p3-error-ux-and-cleanup`                                                                                               |
| FR-08 FCM 推送       | `p1-fcm-push`                                                                                                                                       |
| FR-09 主题与外观     | `p2-android-workspace-settings`, `p3-theme-and-animations`                                                                                          |
| FR-10 审批保留       | `p3-approval-path-reserved`                                                                                                                         |

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

### `p3` Hardening / Future

- `p3-theme-and-animations` — theme system, transitions, motion, code theme
- `p3-error-ux-and-cleanup` — three-layer error UX, purge job, connection stability
- `p3-approval-path-reserved` — reserved non-bypass permission mode and approval event plumbing
