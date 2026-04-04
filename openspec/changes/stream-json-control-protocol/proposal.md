## Why

Companion 当前以 `-p` (print) 模式启动 Claude Code，该模式下 AskUserQuestion 等交互式工具会**立即自动失败**（9ms 内返回 `"Answer questions?"` 错误），因为 `-p` 模式没有终端交互能力。这导致：

1. **额外 API 调用**：模型拿到错误后生成废弃文本，用户选择只能作为新 turn 发送（2 轮 vs 1 轮）
2. **模型上下文污染**：失败的 tool_result + 废弃文本残留在上下文中
3. **脆弱的 hack**：event-mapper 需要抑制 tool_call_completed、assistant_delta、去重等一系列 workaround

参考项目 `happy` (`packages/happy-cli/src/claude/sdk/query.ts`) 和官方 `@anthropic-ai/claude-agent-sdk`（CodePilot 项目使用）均已验证正确方案：使用 `--input-format stream-json --output-format stream-json --permission-prompt-tool stdio` 双向控制协议（不使用 `-p`），Claude Code 发出 `control_request` 后**真正阻塞等待** stdin 的 `control_response`。注意：happy 使用自建 SDK 实现，官方 SDK 的 `query()` 函数在内部自动构建相同的 CLI flags。

## What Changes

- **BREAKING**: Companion 不再使用 `-p` 标志启动 Claude Code，改用 `--input-format stream-json --output-format stream-json --permission-prompt-tool stdio`
- 新增 `ControlRequestHandler`：解析 stdout 的 `control_request` 消息，根据 tool name 分发处理
- AskUserQuestion `control_request` → 转发给 relay → 广播到 Android → 用户选择后回写 `control_response` 到 stdin → Claude Code 继续执行（单轮 turn）
- 非交互工具的 `control_request`（权限审批）→ 根据 permission_mode 自动回复 `allow`
- event-mapper 移除所有 `-p` 模式 workaround（`interactiveToolActive`、`suppressUserMessageCount` 等）
- Relay 新增 `answer_interactive_tool` 命令，Android → relay → companion 回传用户选择
- Android `InteractiveToolCard` 提交答案走专用通道而非普通 sendMessage

## Capabilities

### New Capabilities
- `control-protocol`: companion 双向 control_request/control_response 协议处理，包含 AskUserQuestion 阻塞等待 + 权限自动审批
- `interactive-tool-answer`: relay ↔ companion 交互式工具回答通道（Android 提交选择 → relay → companion stdin）

### Modified Capabilities
<!-- 无需修改已有 spec -->

## Impact

| 层 | 影响 |
|---|------|
| `packages/companion/src/runtime/claude-adapter.ts` | 重构 spawn 参数、新增 control_request 处理循环、新增 answerInteractiveTool 方法 |
| `packages/companion/src/runtime/event-mapper.ts` | 移除 `-p` 模式 workaround，简化为纯事件映射 |
| `packages/relay/src/session/orchestrator.ts` | 新增 `answerInteractiveTool` 命令转发 |
| `packages/relay/src/routes/sessions.ts` | 新增 POST `/sessions/:id/answer` 端点 |
| `packages/wire/src/index.ts` | 新增 `AnswerInteractiveToolCommand` 类型 |
| `packages/android/.../DetailViewModel.kt` | `submitToolAnswer` 改用专用 API 而非 sendMessage |
| `packages/android/.../RelayHttpClient.kt` | 新增 `answerInteractiveTool` HTTP 调用 |
| `tests/` | 更新 event-mapper 测试，新增 control-protocol 单元/集成测试 |
