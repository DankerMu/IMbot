# Test Plan

## Traceability Matrix

| Requirement          | Key Test Coverage                      |
| -------------------- | -------------------------------------- |
| FR-01 Provider 管理  | UT-01, IT-01, E2E-01, E2E-05           |
| FR-02 Workspace 管理 | UT-02, IT-02, E2E-01, E2E-02           |
| FR-03 会话创建       | UT-03, IT-03, E2E-01                   |
| FR-04 会话恢复       | UT-04, IT-04, E2E-02                   |
| FR-05 多会话并发     | UT-05, IT-05, E2E-05                   |
| FR-06 流式渲染       | UT-06, E2E-07, PERF-04                 |
| FR-07 断线恢复       | UT-07, IT-07, E2E-03, PERF-02          |
| FR-08 FCM 推送       | UT-08, IT-08                           |
| FR-09 主题           | UT-09                                  |
| FR-10 审批保留       | UT-10, IT-10                           |
| NFR-01 Performance   | PERF-01, PERF-02, PERF-03, PERF-04     |
| NFR-02 Reliability   | IT-07, E2E-03, E2E-04, PERF-02         |
| NFR-03 Security      | UT-02, manual security checklist       |
| NFR-04 Compatibility | build validation, Android device smoke |

## CI Gate Mapping

The repo uses a phased GitHub gate model so that CI can be strict from day 1 without blocking Phase 0 scaffolding on tests that do not exist yet.

### Always-On Required Checks

These checks are required on every pull request from the moment the GitHub repo is created:

- `Spec Governance`
- `Markdown Quality`
- `Shell Quality`
- `Workflow Quality`
- `Dependency Review`

### Phase-Activated Pull Request Gates

Activation is controlled in `.github/ci-gates.json`.
The workflow names stay stable so branch protection does not need to be redesigned later.

| Gate                         | When To Enable                                                                                                                                 | Minimum Coverage                                                                                             |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `Node Static Quality`        | Root Node workspace exists and `p0-monorepo-and-wire` is underway                                                                              | `lint`, `typecheck`, `test:unit`, `build`; add `test:contract` once relay/companion/wire API contracts exist |
| `Node Integration`           | `p0-relay-minimal` and `p0-companion-minimal` can run against mocks in CI                                                                      | `IT-01`, `IT-02`, `IT-03`, `IT-04`, `IT-05`, `IT-07`, `IT-08`, `IT-10` as applicable                         |
| `Android Static Quality`     | `packages/android` can compile in CI                                                                                                           | Android lint, unit tests, `detekt`, `ktlint`, debug assembly                                                 |
| `Android Instrumented Smoke` | a stable Android emulator smoke path exists; enabled now for `p0-android-prototype`, then expanded through `p2-android-session-list`, `p2-android-new-session`, `p2-android-session-detail`, and `p2-android-workspace-settings` | Prototype shell render smoke first, then onboarding, session list, new session, session detail, workspace/settings |

### Nightly / Scheduled Gates

These are intentionally separated from per-PR blockers because they are slower and need controlled fixtures:

| Gate                           | Scope                                                                       | Target Test IDs                                                                           |
| ------------------------------ | --------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `Node E2E And Perf`            | full-stack mock environment, reconnect, catch-up, latency, directory browse | `E2E-01` to `E2E-07`, `PERF-01` to `PERF-04`                                              |
| `Android Full Regression`      | emulator-based navigation, render, notification, reconnect flows            | Android UI/instrumentation suite tied to `E2E-01`, `E2E-02`, `E2E-03`, `E2E-06`, `E2E-07` |
| `CodeQL JavaScript/TypeScript` | relay, companion, wire                                                      | security regression scanning                                                              |
| `CodeQL Kotlin`                | Android app                                                                 | security regression scanning                                                              |

### Coverage Expectations

- Relay, companion, and wire unit suites should enforce at least 85% line coverage and 80% branch coverage once `Node Static Quality` is enabled.
- Android JVM unit suites should enforce at least 80% line coverage for ViewModel, repository, and domain modules once `Android Static Quality` is enabled.
- Contract tests are mandatory for wire schema, REST schema, event enum, and permission-mode plumbing changes.
- Integration tests must run entirely against deterministic mocks; CI must not depend on a real Claude CLI login, a real OpenClaw gateway, or a real Firebase project.
- Performance gates are nightly-only unless a PR explicitly changes sequence allocation, catch-up logic, workspace browse logic, or Android long-message rendering.

## Unit Tests

### Relay

| ID     | Test                       | Scenario                                                                            |
| ------ | -------------------------- | ----------------------------------------------------------------------------------- |
| UT-01a | SessionOrchestrator.create | 正常创建 → status=queued                                                            |
| UT-01b | SessionOrchestrator.create | provider invalid → error                                                            |
| UT-03a | Session state machine      | queued → running (valid)                                                            |
| UT-03b | Session state machine      | completed → running (invalid) → error                                               |
| UT-03c | Session state machine      | all valid transitions pass                                                          |
| UT-03d | Session state machine      | all invalid transitions reject                                                      |
| UT-07a | Seq allocation             | 连续分配递增                                                                        |
| UT-07b | Seq allocation             | 并发安全（SQLite 单写者）                                                           |
| UT-08a | FCM push                   | completed → 发送通知                                                                |
| UT-08b | FCM push                   | 无 subscription → 不报错                                                            |
| UT-10a | Permission mode plumbing   | `permission_mode=default` 创建会话时，relay DB 与 companion command 保持同值        |
| UT-10b | Approval event passthrough | `approval_required` / `approval_resolved` 事件可被存储并广播，不破坏现有 session 流 |
| UT-14a | Purge job                  | 30 天前 session 被删                                                                |
| UT-14b | Purge job                  | 活跃 session 不删                                                                   |
| UT-02a | Path validation            | 正常路径通过                                                                        |
| UT-02b | Path validation            | `..` 路径遍历拒绝                                                                   |
| UT-02c | Path validation            | 不在 root 下的路径拒绝                                                              |

### Companion

| ID     | Test                 | Scenario                    |
| ------ | -------------------- | --------------------------- |
| UT-11a | Command dispatcher   | create_session → 调用 SDK   |
| UT-11b | Command dispatcher   | unknown command → error ack |
| UT-12a | Workspace catalog    | browse 返回目录列表         |
| UT-12b | Workspace catalog    | browse 不返回文件           |
| UT-12c | Workspace catalog    | 不存在路径 → error          |
| UT-13a | ClaudeRuntimeAdapter | mock SDK → 事件流正常       |
| UT-13b | ClaudeRuntimeAdapter | SDK error → error event     |

### Android

| ID     | Test              | Scenario                     |
| ------ | ----------------- | ---------------------------- |
| UT-20a | SessionRepository | 合并远程 + 本地 session 列表 |
| UT-20b | SessionRepository | 按 provider 过滤             |
| UT-21a | DetailViewModel   | events 流 → uiState 更新     |
| UT-21b | DetailViewModel   | 断线 → reconnecting 状态     |
| UT-22a | Event catch-up    | since_seq 补拉合并无重复     |
| UT-09a | ThemeManager      | system/light/dark 切换       |

## Integration Tests

| ID    | Test               | Components                   | Scenario                                                |
| ----- | ------------------ | ---------------------------- | ------------------------------------------------------- |
| IT-01 | Provider 路由      | relay + companion            | Claude session 走 companion，OpenClaw session 走 bridge |
| IT-02 | Workspace CRUD     | relay + companion            | 添加 root → browse → 删除 root                          |
| IT-03 | Session create E2E | relay + companion + mock SDK | 创建 → queued → running → events → completed            |
| IT-04 | Session resume     | relay + companion            | 恢复旧 session → running → events                       |
| IT-05 | Multi-session      | relay + companion            | 3 个 session 并发 running                               |
| IT-07 | Catch-up           | relay                        | 插入 N 个 events → GET since_seq → 全量返回             |
| IT-08 | FCM                | relay + mock FCM             | session 完成 → 调用 FCM API                             |
| IT-10 | Approval path      | relay + companion            | 切换 permission_mode=default → approval_required event  |

## E2E Tests

| ID     | Test           | Description                                                             |
| ------ | -------------- | ----------------------------------------------------------------------- |
| E2E-01 | 完整创建流程   | Android → 选 provider → 选目录 → 输 prompt → 创建 → 看到流式输出 → 完成 |
| E2E-02 | 恢复旧 session | Android → 浏览目录 → 选旧 session → 恢复 → 继续对话                     |
| E2E-03 | 断线恢复       | 断开网络 60s → 恢复 → events 自动补拉 → UI 一致                         |
| E2E-04 | Host offline   | 断开 companion → Android 看到 "MacBook offline" → 重连后恢复            |
| E2E-05 | 多 provider    | 同时创建 Claude + OpenClaw session → 各自独立运行                       |
| E2E-06 | 取消 session   | 创建 running session → 取消 → 正常情况下 status=cancelled               |
| E2E-07 | Markdown 渲染  | 收到含代码块的 assistant_message → 语法高亮正确                         |
| E2E-08 | 取消竞态       | running session 发 cancel，同时 provider 先结束 → 返回 provider 终态，且不写 `session.cancel` audit |

## Performance Tests

| ID      | Test                   | Target                              |
| ------- | ---------------------- | ----------------------------------- |
| PERF-01 | 端到端延迟             | POST /sessions → 首个 WS event < 1s |
| PERF-02 | Catch-up 1000 events   | < 3s                                |
| PERF-03 | 目录浏览 (100 子目录)  | < 500ms                             |
| PERF-04 | 长消息渲染 (10K chars) | 无卡顿 (< 16ms frame)               |
