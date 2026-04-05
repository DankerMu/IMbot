# Requirements Traceability Matrix

> Note: PRD `Phase 0-3` are roadmap milestones. OpenSpec `p0-p3` prefixes are delivery bands for implementation slices, not a 1:1 replacement for PRD phases. Use [../../../openspec/README.md](../../../openspec/README.md) for the change index and exact mapping.

## Functional Requirements → Spec → Test

| ID | Requirement | Spec Section(s) | OpenSpec Change(s) | Test(s) | PRD Milestone |
|----|-------------|------------------|--------------------|---------|---------------|
| FR-01 | Provider 管理 (Claude/book/OpenClaw 三种平级) | ARCH §Relay Modules, DATA §hosts/sessions, API §/hosts | `p0-openclaw-bridge`, `p1-companion-session-management`, `p2-android-new-session`, `p2-android-session-list` | UT-01, IT-01, E2E-01, E2E-05 | Phase 0-1 |
| FR-02 | Workspace 管理 (动态根目录+子目录浏览) | ARCH §Companion/Workspace, DATA §workspace_roots, API §/hosts/:id/roots+browse | `p1-relay-workspace-api`, `p1-companion-session-management`, `p2-android-new-session`, `p2-android-workspace-settings` | UT-02, IT-02, E2E-01, E2E-02 | Phase 1-2 |
| FR-03 | 会话创建 (provider+目录+可选 prompt) | BIZ §Create Session Flow, API §POST /sessions, ARCH §SessionOrchestrator | `p0-relay-minimal`, `p0-companion-minimal`, `p0-android-prototype`, `p2-android-new-session`, `p3-initial-user-message-echo` | UT-03, IT-03, E2E-01 | Phase 0-1 |
| FR-04 | 会话恢复 (列出历史+恢复) | BIZ §State Machine, API §POST /sessions/:id/resume, ARCH §Companion/Runtime | `p1-relay-session-lifecycle`, `p1-companion-session-management`, `p0-openclaw-bridge`, `p2-android-workspace-settings`, `p2-android-session-detail`, `p3-session-detail-hardening`, `p3-context-usage-display`, `p1-session-catalog-sync` | UT-04, IT-04, E2E-02, E2E-06 | Phase 1 |
| FR-05 | 多会话并发 (无硬上限) | DATA §sessions, ARCH §WsHub/subscriptions, BIZ §State Machine | `p1-relay-session-lifecycle`, `p1-reconnect-and-catchup`, `p2-android-session-list`, `p3-error-ux-and-cleanup`, `p1-session-catalog-sync` | UT-05, IT-05, E2E-05 | Phase 1 |
| FR-06 | 流式输出+实时渲染 (Markdown+语法高亮) | BIZ §Event Pipeline, API §WS Protocol, ARCH §Android/DetailScreen | `p0-android-prototype`, `p2-android-session-detail`, `p3-theme-and-animations`, `p3-session-detail-hardening`, `p3-initial-user-message-echo`, `p3-context-usage-display` | UT-06, E2E-07, PERF-04 | Phase 1-2 |
| FR-07 | 断线恢复 (since_seq 补拉) | BIZ §Reconnect & Catch-up, API §GET /events?since_seq, DATA §session_events.seq | `p1-reconnect-and-catchup`, `p3-error-ux-and-cleanup` | UT-07, IT-07, E2E-03, PERF-02 | Phase 1-3 |
| FR-08 | FCM 推送 (完成/失败通知) | BIZ §FCM Push Logic, API §POST /push/register, ARCH §Push Adapter | `p1-fcm-push` | UT-08, IT-08 | Phase 1 |
| FR-09 | 主题 (System/Light/Dark) | ARCH §Android/Theme | `p2-android-workspace-settings`, `p3-theme-and-animations`, `p3-design-md-ui-refresh` | UT-09 | Phase 2 |
| FR-10 | 审批保留 (默认 bypass) | DATA §approvals, ARCH §permission_mode | `p3-approval-path-reserved` | UT-10, IT-10 | Phase 3 (reserved) |

## Non-Functional Requirements → Spec → Test

| PRD NFR | Coverage | Target(s) | Spec Section(s) | OpenSpec Change(s) | Test(s) |
|---------|----------|-----------|-----------------|--------------------|---------|
| NFR-01 | Performance | 指令到首个事件 `< 1s P95`; 目录浏览 `< 500ms P95`; 会话列表加载 `< 300ms`; 补拉 1000 事件 `< 3s P95`; 冷启动 `< 2s`; Android 内存 `< 200MB` | BIZ §Create Session Flow, API §GET /browse, API §GET /events, ARCH §Android/Room | `p0-android-prototype`, `p1-relay-workspace-api`, `p1-reconnect-and-catchup`, `p2-android-session-list`, `p2-android-session-detail`, `p3-theme-and-animations` | PERF-01, PERF-02, PERF-03, PERF-04 |
| NFR-02 | Reliability | WSS 稳定性 `> 99%`; 事件完整性 `100%`; 接受短暂 VPS 中断 | BIZ §Reconnect & Catch-up, BIZ §Seq Allocation, OPS §Deployment | `p1-reconnect-and-catchup`, `p3-error-ux-and-cleanup` | IT-07, E2E-03, E2E-04, PERF-02 |
| NFR-03 | Security | TLS / WSS only; Android 禁止 cleartext; token 首次输入; Claude 凭据不上传 relay; MVP 无 E2E | SEC §AUTH_DESIGN, API §Authentication, ARCH §Android Onboarding | `p0-relay-minimal`, `p1-relay-workspace-api`, `p2-android-workspace-settings`, `p3-approval-path-reserved` | UT-02, manual security checklist |
| NFR-04 | Compatibility | Android API 26+; relay / companion Node.js 22+; TypeScript monorepo | Overview §Tech Stack, OPS §Deployment, UI/UX Overview | `p0-monorepo-and-wire`, `p0-android-prototype`, `p3-theme-and-animations` | build validation, Android device smoke |

## Decision Trace

| Decision | Rationale | Impacts |
|----------|-----------|---------|
| D-001 SQLite | 单用户、VPS 内存敏感 | DATA全表, OPS §Deployment |
| D-002 native WS | 无 room 需求 | API §WS Protocol, ARCH §WsHub |
| D-003 Static token | 单用户、最简 | SEC §AUTH_DESIGN |
| D-004 Claude Code CLI (stream-json) | 直接控制本地 CLI 进程，结构化事件流 | ARCH §Companion/Runtime |
| D-005 Bypass default | 手机审批不便 | DATA §approvals, FR-10, `p3-approval-path-reserved` |
| D-006 Jetpack Compose | 现代声明式 | ARCH §Android |
| D-007 Fastify | 高性能 TS 框架 | ARCH §Relay |
| D-008 OpenClaw GW WS | 独立进程隔离 | ARCH §OpenClaw Bridge, BIZ §Translation |
| D-009 Per-session seq | 简单、断线补拉直观 | DATA §session_events, BIZ §Seq |
| D-010 launchd | macOS 原生 | OPS §Deployment |

## User Story Map

```
                    ┌──────────────────────────────────────────────┐
                    │              Core Journey                     │
                    ├──────────────────────────────────────────────┤
                    │                                              │
查看状态             │  打开 App → 看 session list → 选一个看详情    │ FR-05, FR-06
                    │                                              │
新建任务             │  + → 选 provider → 选目录 → 可选写 prompt → 开始 │ FR-01, FR-02, FR-03
                    │                                              │
恢复旧话             │  浏览目录 → 选旧 session → 恢复 → 继续       │ FR-04
                    │                                              │
被动通知             │  收推送 → 点击 → 看结果                      │ FR-08
                    │                                              │
                    └──────────────────────────────────────────────┘
                    ┌──────────────────────────────────────────────┐
                    │              Support Functions                │
                    ├──────────────────────────────────────────────┤
                    │  Workspace 管理: 添加/删除根目录               │ FR-02
                    │  主题切换: System/Light/Dark                   │ FR-09
                    │  断线恢复: 自动重连+补拉                       │ FR-07
                    │  审批: 保留能力, 默认关闭                      │ FR-10
                    └──────────────────────────────────────────────┘
```
