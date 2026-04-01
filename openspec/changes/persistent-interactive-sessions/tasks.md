## 0. Spike（已完成）

- [x] 0.1 验证 Claude CLI 双向 stream-json 协议——`-p --input-format stream-json --output-format stream-json --verbose` 进程常驻，stdin JSON 消息，每轮 ~2s
- [x] 0.2 记录发现到 `docs/spike-interactive-cli.md`

## 1. Wire 协议扩展

- [ ] 1.1 `packages/wire/src/enums.ts`：SESSION_STATUSES 新增 `"idle"`；VALID_STATUS_TRANSITIONS 新增 `running → idle`、`idle → running/completed/cancelled`
- [ ] 1.2 `packages/wire/src/enums.ts`：EVENT_TYPES 新增 `"session_idle"`
- [ ] 1.3 `packages/wire/src/commands.ts`：新增 `CompleteSessionCommand { cmd: "complete_session", req_id, session_id }`，加入 CompanionCommand union
- [ ] 1.4 `packages/wire/src/models.ts`：Session type 的 status union 新增 `"idle"`
- [ ] 1.5 `tests/unit/wire.test.mjs`：验证新增 status、event type、command type；验证 `idle` transition 规则
- [ ] 1.6 build wire + typecheck

## 2. Relay 状态机与 API 扩展

- [ ] 2.1 `packages/relay/src/db/init.ts`：DB migration 支持 `idle` status
- [ ] 2.2 `packages/relay/src/session/transitions.ts`：TRANSITIONS 表新增 `idle` 的出入边
- [ ] 2.3 `packages/relay/src/session/orchestrator.ts`：`handleEvent` 收到 `session_idle` → transition to `idle`
- [ ] 2.4 `packages/relay/src/session/orchestrator.ts`：`sendMessage` 允许 `idle` 状态——先 transition to `running` 再 dispatch `send_message` 给 companion
- [ ] 2.5 `packages/relay/src/session/orchestrator.ts`：新增 `complete(sessionId)` 方法——dispatch `complete_session` 给 companion，等 ack + `session_result`，transition to `completed`
- [ ] 2.6 `packages/relay/src/routes/sessions.ts`：新增 `POST /v1/sessions/:id/complete`
- [ ] 2.7 `tests/unit/relay-core.test.mjs`：`session_idle` event 处理和广播
- [ ] 2.8 `tests/unit/relay-core.test.mjs`：`idle` 状态 sendMessage、complete、cancel transition
- [ ] 2.9 `tests/unit/relay-core.test.mjs`：complete endpoint 在各状态下的行为
- [ ] 2.10 `tests/contract/relay-companion.test.mjs`：multi-turn lifecycle（create → idle → send → running → idle → complete → completed）

## 3. Companion 持久运行时

- [ ] 3.1 `packages/companion/src/runtime/claude-adapter.ts`：`createSession` 改为 stream-json 持久模式——spawn 参数 `-p --input-format stream-json --output-format stream-json --verbose --permission-mode <mode>`，首条 prompt 在收到 `system.init` 后通过 stdin 以 JSON 格式写入
- [ ] 3.2 `packages/companion/src/runtime/claude-adapter.ts`：`RuntimeSession` 新增 `idleTimer: NodeJS.Timeout | null`、`isIdle: boolean` 字段
- [ ] 3.3 `packages/companion/src/runtime/claude-adapter.ts`：`handleStdoutLine` 收到 `result` event 时——检查进程是否存活：存活则 emit `session_idle` + 设 `isIdle = true` + 启动 idle timer；已退出则保持现有 `session_result` 行为
- [ ] 3.4 `packages/companion/src/runtime/claude-adapter.ts`：`sendMessage` 改为写 JSON 格式 `{"type":"user","message":{"role":"user","content":"<text>"}}\n`，重置 idle timer，设 `isIdle = false`
- [ ] 3.5 `packages/companion/src/runtime/claude-adapter.ts`：idle timer 到期 → SIGTERM 进程 → emit `session_result`
- [ ] 3.6 `packages/companion/src/runtime/claude-adapter.ts`：新增 `completeSession(relaySessionId)` 方法——SIGTERM 进程、等待退出、emit `session_result`
- [ ] 3.7 `packages/companion/src/runtime/claude-adapter.ts`：`resumeSession` 也改为 stream-json 持久模式——`-p --input-format stream-json --output-format stream-json --verbose -r <provider_session_id>`
- [ ] 3.8 `packages/companion/src/dispatcher.ts`：注册 `complete_session` 命令处理器
- [ ] 3.9 `packages/companion/src/index.ts`：启动时检查 sessionIndex 中的中断 session，emit `session_error`
- [ ] 3.10 `packages/companion/src/config.ts`：新增 `idle_timeout_ms` 配置项（默认 1800000）
- [ ] 3.11 `tests/unit/companion-core.test.mjs`：stream-json spawn 参数验证（无旧 `-p <prompt>` 模式）
- [ ] 3.12 `tests/unit/companion-core.test.mjs`：stdin JSON 消息格式验证
- [ ] 3.13 `tests/unit/companion-core.test.mjs`：idle timer 触发 auto-complete
- [ ] 3.14 `tests/unit/companion-core.test.mjs`：`complete_session` 命令处理
- [ ] 3.15 `tests/unit/companion-core.test.mjs`：companion 重启恢复
- [ ] 3.16 `tests/unit/approval-events.test.mjs`：`result` event 在进程存活时映射为 `session_idle`
- [ ] 3.17 `tests/integration/companion-runtime.test.mjs`：更新 mock CLI 支持 stream-json 双向协议（stdin 读 JSON，stdout 写 JSON）；新增 multi-turn integration test

## 4. Android Session Detail 改造

- [ ] 4.1 Android wire 层：`SessionStatus` enum 新增 `IDLE`；relay response 解析适配
- [ ] 4.2 Android `RelayHttpClient`：新增 `completeSession(sessionId)` 调用 `POST /v1/sessions/:id/complete`
- [ ] 4.3 Android session detail：`idle` 状态启用消息输入框 + 发送按钮；`running` 状态禁用输入显示 "Claude is responding..."
- [ ] 4.4 Android session detail：`idle` 状态 toolbar 显示 "End Session" 动作
- [ ] 4.5 Android session detail：`idle` status chip 样式（区别 completed）、turn separator
- [ ] 4.6 Android session list：`idle` session badge + 排序在 completed/failed 之前
- [ ] 4.7 Android unit tests：idle 状态 UI 逻辑、completeSession、status parsing

## 5. Session Viewer CLI

- [ ] 5.1 `packages/viewer/`：初始化包（package.json, tsconfig.json, src/index.ts）
- [ ] 5.2 CLI 参数解析：`--relay`, `--token`, `--session`；fallback 读 `~/.imbot/companion.json`
- [ ] 5.3 WebSocket 连接 relay + reconnect（复用 ExponentialBackoff）
- [ ] 5.4 事件格式化输出：assistant text 直接输出、tool call 格式化、status separator、user message 标记
- [ ] 5.5 Session 过滤：`--session` 指定时只显示该 session
- [ ] 5.6 根 `package.json` 新增 `build:viewer`
- [ ] 5.7 `tests/unit/viewer-formatter.test.mjs`：formatter 输出格式验证

## 6. 文档与收尾

- [ ] 6.1 更新 `docs/engineering-spec/`：状态机图、API 端点、companion 配置新字段
- [ ] 6.2 更新 `CLAUDE.md`：新增 `npm run build:viewer`
- [ ] 6.3 E2E 测试计划：新增 T11（multi-turn persistent session）、T12（idle timeout）、T13（complete session）、T14（session viewer）
- [ ] 6.4 全量测试通过：`npm test` + `npm run test:integration` + Android `testDebugUnitTest`
