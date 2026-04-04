## 1. Wire 包类型定义

- [ ] 1.1 在 `packages/wire/src/commands.ts` 新增 `AnswerInteractiveToolCommand` 类型（cmd: `"answer_interactive_tool"`，字段: req_id, session_id, call_id, answer, question_index?）
- [ ] 1.2 将 `AnswerInteractiveToolCommand` 加入 `CompanionCommand` 联合类型
- [ ] 1.3 `npm run build` 确认 wire 包编译通过，下游 relay/companion 类型检查通过

## 2. Companion spawn 参数迁移

- [ ] 2.1 `claude-adapter.ts` `createSession()`：去掉 `-p`，args 改为 `["--input-format", "stream-json", "--output-format", "stream-json", "--verbose", "--permission-prompt-tool", "stdio", "--permission-mode", mode]`
- [ ] 2.2 `claude-adapter.ts` `resumeSession()`：同样添加 `--permission-prompt-tool stdio`，移除 `-p`（如有）
- [ ] 2.3 手动验证：spawn 新 session，确认 stdout 输出 `system.init` + 可接收 stdin user message

## 3. Companion stdout 分流 control protocol

- [ ] 3.1 `handleStdoutLine()`：JSON.parse 后先检查 `type`，`control_request` / `control_cancel_request` / `control_response` 分流到新方法，其余走 `eventMapper.map()`
- [ ] 3.2 新增 `handleControlRequest(session, parsed)`：提取 `request_id`、`request.subtype`、`request.tool_name`、`request.input`
- [ ] 3.3 非 AskUserQuestion 的 `can_use_tool` 请求：立即写 `control_response` 到 stdin，`{ type: "control_response", response: { subtype: "success", request_id, response: { behavior: "allow", updatedInput: request.input } } }`
- [ ] 3.4 非 `can_use_tool` subtype 的请求：写 error `control_response`
- [ ] 3.5 新增 `handleControlCancelRequest(session, parsed)`：按 request_id 查找 pending callback 并 reject

## 4. AskUserQuestion 阻塞等待机制

- [ ] 4.1 `RuntimeSession` 接口新增 `pendingControlResponse: { requestId: string; originalInput: Record<string, unknown>; resolve: (answer: string, questionIndex: number) => void; reject: (reason: unknown) => void } | null`（`originalInput` 保存 control_request 的 `request.input`，用于构造 `updatedInput`）
- [ ] 4.2 AskUserQuestion control_request 处理：生成 `tool_call_started` 事件发给 relay，在 session 上挂 pending Promise（不写 control_response）
- [ ] 4.3 新增 `answerInteractiveTool(relaySessionId, callId, answer)` 方法：查找 session → 校验 callId 匹配 pendingControlResponse.requestId → resolve(answer) → 写 `control_response` 到 stdin
- [ ] 4.4 answer 写入的 control_response 格式：`{ type: "control_response", response: { subtype: "success", request_id, response: { behavior: "allow", updatedInput: { questions: originalInput.questions, answers: { [questionIndex]: answer } } } } }`（`updatedInput` 必须保留原始 `questions` 并填入 `answers` 对象，key 为问题索引 string，value 为选中 label）
- [ ] 4.5 超时处理：如果 session idle timer 触发且 pending 存在，写 error control_response 并 reject
- [ ] 4.6 session cancel/close 时清理：pending 存在则写 error control_response 并 reject

## 5. Companion 命令路由

- [ ] 5.1 companion `handleCommand()` 增加 `answer_interactive_tool` 分支，调用 `adapter.answerInteractiveTool()`
- [ ] 5.2 错误情况返回 `{ type: "ack", req_id, ok: false, error: "..." }`（session_not_found / no_pending_control_request / call_id_mismatch）

## 6. Relay 端点与编排

- [ ] 6.1 `packages/relay/src/routes/sessions.ts` 新增 `POST /v1/sessions/:id/answer`，schema 校验 body `{ call_id: string, answer: string, question_index?: number }`（question_index 缺省为 0）
- [ ] 6.2 `SessionOrchestrator` 新增 `answerInteractiveTool(sessionId, callId, answer)` 方法：requireSession → 校验 status === "running" → assertProviderAvailable → companionManager.sendCommand → assertAckOk
- [ ] 6.3 路由调用 orchestrator 方法，错误映射：not_found → 404, state_conflict → 409, host_offline → 502

## 7. Event-mapper 清理

- [ ] 7.1 移除 `interactiveToolActive` 标志及 assistant 文本抑制逻辑
- [ ] 7.2 移除 `emitToolCallCompleted` 中的 `INTERACTIVE_TOOLS` 抑制判断
- [ ] 7.3 评估 `suppressUserMessageCount`：实测 `--permission-prompt-tool stdio` 模式下 Skill 的 stdout 行为，如果不再泄露则一并移除
- [ ] 7.4 保留 `emittedTools` Map 去重逻辑（verbose 模式不受影响）

## 8. Android 客户端适配

- [ ] 8.1 `RelayHttpClient` 新增 `suspend fun answerInteractiveTool(relayUrl, token, sessionId, callId, answer, questionIndex): Result<Unit>`，发 POST 请求
- [ ] 8.2 `SessionRepository` 新增 `answerInteractiveTool(sessionId, callId, answer)` 封装
- [ ] 8.3 `DetailViewModel.submitToolAnswer()` 改为调用 `sessionRepository.answerInteractiveTool()` 而非 `sendMessage()`
- [ ] 8.4 提交时 InteractiveToolCard 显示 "提交中..." 加载态；成功/失败后更新状态
- [ ] 8.5 答案 API 失败时：snackbar 显示错误，卡片恢复未回答状态

## 9. 测试

- [ ] 9.1 单元测试：`event-mapper.test.mjs` — 移除 `-p` workaround 相关测试，更新断言（AskUserQuestion tool_result 不再抑制）
- [ ] 9.2 单元测试：新增 control protocol 分流测试（control_request 不走 eventMapper、非交互自动 allow、未知 subtype error）
- [ ] 9.3 单元测试：AskUserQuestion 阻塞 → 收到 answer → 写 control_response → pending 清除
- [ ] 9.4 单元测试：超时 / cancel / 进程退出时 pending 清理
- [ ] 9.5 单元测试：callId 不匹配 / session 无 pending 时 answer 命令返回错误 ack
- [ ] 9.6 contract 测试：POST `/v1/sessions/:id/answer` 端点 — 200 成功、404 不存在、409 状态冲突、400 缺少字段
- [ ] 9.7 集成测试：端到端 AskUserQuestion 流程 — 模型调用 → control_request → tool_call_started → Android answer → control_response → tool_call_completed → 模型继续同一 turn

## 10. 前置验证（实现前 spike）

- [ ] 10.1 **control protocol smoke test**: 手动执行 `claude --input-format stream-json --output-format stream-json --verbose --permission-prompt-tool stdio --permission-mode bypassPermissions`，通过 stdin 发送 user message，确认 stdout 输出 `system.init` + 可触发 `control_request`
- [ ] 10.2 **非交互工具 control_request 验证**: 确认 `bypassPermissions` + `--permission-prompt-tool stdio` 模式下，Bash 等工具是否仍发 `control_request`（还是被 bypass 跳过）。如果跳过，需调整 permission_mode 策略
- [ ] 10.3 **AskUserQuestion updatedInput 格式验证**: 手动构造 `control_response` with `{ questions: [...], answers: { "0": "test" } }`，确认 AskUserQuestion 返回预填答案而非尝试终端交互
- [ ] 10.4 **multiSelect 格式验证**: 验证多选答案的 `answers` 值格式（逗号分隔 vs 数组）

## 11. 部署与验证

- [ ] 11.1 `npm run build && npm run test:unit && npm run test:contract` 全部通过
- [ ] 11.2 部署 companion 到 `~/.imbot/companion/dist/` 并重启
- [ ] 11.3 部署 relay 到远程服务器并 pm2 restart
- [ ] 11.4 Android 构建安装 APK 到模拟器
- [ ] 11.5 端到端验证：创建 session → 触发 AskUserQuestion → Android 卡片出现 → 选择选项 → 模型在同一 turn 内继续回复 → 无额外文本/无多余 API 调用
