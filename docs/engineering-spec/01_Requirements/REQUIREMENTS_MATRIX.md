# Requirements Traceability Matrix

## Functional Requirements → Spec → Test

| ID | Requirement | Spec Section(s) | Test(s) | Priority |
|----|-------------|------------------|---------|----------|
| FR-01 | Provider 管理 (Claude/book/OpenClaw 三种平级) | ARCH §Relay Modules, DATA §hosts/sessions, API §/hosts | UT-01, IT-01, E2E-05 | P0 |
| FR-02 | Workspace 管理 (动态根目录+子目录浏览) | ARCH §Companion/Workspace, DATA §workspace_roots, API §/hosts/:id/roots+browse | UT-02, IT-02, E2E-02 | P0 |
| FR-03 | 会话创建 (provider+目录+prompt) | BIZ §Create Session Flow, API §POST /sessions, ARCH §SessionOrchestrator | UT-03, IT-03, E2E-01 | P0 |
| FR-04 | 会话恢复 (列出历史+恢复) | BIZ §State Machine, API §POST /sessions/:id/resume, ARCH §Companion/Runtime | UT-04, IT-04, E2E-02 | P0 |
| FR-05 | 多会话并发 (无硬上限) | DATA §sessions, ARCH §WsHub/subscriptions, BIZ §State Machine | UT-05, IT-05, E2E-05 | P0 |
| FR-06 | 流式输出+实时渲染 (Markdown+语法高亮) | BIZ §Event Pipeline, API §WS Protocol, ARCH §Android/DetailScreen | UT-06, E2E-07, PERF-04 | P0 |
| FR-07 | 断线恢复 (since_seq 补拉) | BIZ §Reconnect & Catch-up, API §GET /events?since_seq, DATA §session_events.seq | UT-07, IT-07, E2E-03 | P0 |
| FR-08 | FCM 推送 (完成/失败通知) | BIZ §FCM Push Logic, API §POST /push/register, ARCH §Push Adapter | UT-08, IT-08 | P1 |
| FR-09 | 主题 (System/Light/Dark) | ARCH §Android/Theme | UT-09 | P1 |
| FR-10 | 审批保留 (默认 bypass) | DATA §approvals, ARCH §permission_mode | UT-10, IT-10 | P2 |

## Non-Functional Requirements → Spec → Test

| ID | Requirement | Target | Spec Section | Test |
|----|-------------|--------|-------------|------|
| NFR-01 | 端到端延迟 | < 1s P95 | BIZ §Create Session Flow | PERF-01 |
| NFR-02 | 目录浏览响应 | < 500ms P95 | API §GET /browse | PERF-03 |
| NFR-03 | 会话列表加载 | < 300ms (缓存命中) | ARCH §Android/Room | — |
| NFR-04 | 补拉 1000 事件 | < 3s P95 | API §GET /events | PERF-02 |
| NFR-05 | WSS 稳定性 (24h) | > 99% | BIZ §Reconnect, OPS §Deployment | PERF-02 |
| NFR-06 | 事件完整性 | 100% | BIZ §Seq Allocation | IT-07, E2E-03 |
| NFR-07 | App 冷启动 | < 2s | ARCH §Android | — |

## Decision Trace

| Decision | Rationale | Impacts |
|----------|-----------|---------|
| D-001 SQLite | 单用户、VPS 内存敏感 | DATA全表, OPS §Deployment |
| D-002 native WS | 无 room 需求 | API §WS Protocol, ARCH §WsHub |
| D-003 Static token | 单用户、最简 | SEC §AUTH_DESIGN |
| D-004 Claude Code CLI (stream-json) | 直接控制本地 CLI 进程，结构化事件流 | ARCH §Companion/Runtime |
| D-005 Bypass default | 手机审批不便 | DATA §approvals, FR-10 |
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
新建任务             │  + → 选 provider → 选目录 → 写 prompt → 开始  │ FR-01, FR-02, FR-03
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
