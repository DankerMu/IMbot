## Context

Companion 以 `-p --input-format stream-json --output-format stream-json` 启动 Claude Code 子进程。`-p` (print) 模式设计用于单次 prompt→response 场景。当模型调用 AskUserQuestion 时，Claude Code 内部尝试展示终端 prompt 但失败，立即返回错误 tool_result，模型在同一 turn 内已处理完错误。

参考项目 happy (`packages/happy-cli/src/claude/sdk/query.ts`) 使用 `--permission-prompt-tool stdio` 标志替代 `-p`，实现双向控制协议：Claude Code 在 stdout 发送 `control_request`，阻塞等待 stdin 的 `control_response`。这是 Claude Code SDK 的官方交互模式。

当前 companion 的 `ClaudeRuntimeAdapter` 通过 `writeUserMessage()` 向 stdin 发送 user message JSON，通过 `handleStdoutLine()` 读取 stdout JSON 事件。stdin pipe 已可用，只需调整协议层。

## Goals / Non-Goals

**Goals:**
- AskUserQuestion 单轮 turn 完成：模型调用 → Android 用户选择 → tool_result 返回 → 模型继续
- 非交互工具的权限请求自动审批（保持当前 bypassPermissions 行为）
- 移除 event-mapper 中所有 `-p` 模式 workaround
- 保持 session 生命周期（create/resume/cancel/complete）不变

**Non-Goals:**
- 支持 Android 端的权限审批 UI（approval_required 已有独立实现）
- 支持 `--permission-prompt-tool` 以外的控制协议
- 修改 session 状态机或数据模型
- 支持多个并发 AskUserQuestion（Claude Code 串行执行工具）

## Decisions

### D1: 去掉 `-p`，改用 `--input-format stream-json --permission-prompt-tool stdio`

**选择**: createSession 的 spawn args 从 `["-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose", "--permission-mode", mode]` 改为 `["--input-format", "stream-json", "--output-format", "stream-json", "--verbose", "--permission-prompt-tool", "stdio", "--permission-mode", mode]`。

**替代方案**: 保持 `-p` 不变，在 companion 层拦截 AskUserQuestion 并构造假 tool_result。
**否决原因**: `-p` 模式下 Claude Code 内部已经处理了工具调用，外部无法拦截。`--permission-prompt-tool stdio` 是 Claude Code 官方支持的双向控制机制。

**注意**: 去掉 `-p` 后，初始 prompt 不再通过命令行参数传入，而是通过 stdin 的 stream-json 消息发送。当前 `writeUserMessage()` 已经可以做到这一点——`createSession` 调用 `writeUserMessage(command.prompt)` 的现有流程无需修改。resumeSession 同理，已使用 `--resume` + stdin。

**验证**: 官方 `@anthropic-ai/claude-agent-sdk` 的 `query()` 函数（CodePilot 项目使用）构建的 spawn args 为 `["--output-format", "stream-json", "--verbose", "--input-format", "stream-json"]`，**从不使用 `-p`**。当 `canUseTool` callback 存在时追加 `--permission-prompt-tool stdio`。虽然 `claude --help` 称 `--input-format` / `--output-format` "only works with --print"，但 SDK 证明这是 help 文本过时，实际可用（已在 Claude Code v2.1.91 确认）。

### D2: handleStdoutLine 分流 control_request vs 普通事件

**选择**: 在 `handleStdoutLine` 中 JSON.parse 后，先检查 `type === "control_request"` / `type === "control_cancel_request"` / `type === "control_response"`，分流到独立的 `handleControlRequest()` 方法。其余消息继续走 `eventMapper.map()` 原有路径。

**替代方案**: 新建独立的 `ControlProtocolHandler` 类。
**否决原因**: control_request 的处理需要访问 session 的 stdin（写 `control_response`）和 relay 的 `sendEvent`（转发 AskUserQuestion 到 Android）。这些 依赖都在 `ClaudeRuntimeAdapter` 内部，抽出独立类会增加不必要的依赖注入。保持在 adapter 内更简单。

### D3: AskUserQuestion control_request → 挂起 Promise 等待 Android 回答

**选择**: 当 `control_request.request.tool_name === "AskUserQuestion"` 时：
1. 生成 `tool_call_started` 事件发送给 relay（同现有逻辑）
2. 在 session 上注册一个 `pendingControlResponse: { requestId, originalInput, resolve, reject }` 对象，保存原始 `request.input`（包含 `questions` 数组）
3. **不写 control_response** — Claude Code 在此阻塞
4. 当 relay 转发 Android 用户的回答时，调用 `resolve(answer)`，adapter 构造 `control_response` 写到 stdin
5. Claude Code 继续执行

**control_response 格式**: AskUserQuestion 的 `call` 函数签名为 `call({questions, answers = {}, annotations}, ctx)`，它直接返回 `{ data: { questions, answers } }` 而不尝试终端交互。因此 `updatedInput` 必须保留原始 `questions` 并填入 `answers` 对象：
```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "<id>",
    "response": {
      "behavior": "allow",
      "updatedInput": {
        "questions": [<原始 request.input.questions 原样传回>],
        "answers": { "0": "用户选中的 label" }
      }
    }
  }
}
```
`answers` 的 key 是问题索引（string "0", "1", ...），value 是选中选项的 label 字符串。对于 multiSelect 问题，value 为逗号分隔的多个 label。对于用户自定义输入（Other），直接填入用户文本。

**超时**: 使用 session 的现有 idle timeout（默认 30min）。如果超时前用户未回答，发送 `control_response` with `subtype: "error"` 让模型继续。

**替代方案**: companion 自己弹出终端 prompt（不可能，headless 模式）。
**替代方案**: 把 control_request 原样转发给 relay 让 relay 处理。
**否决原因**: relay 无法直接写 companion 子进程的 stdin。companion 必须持有 resolve callback。

### D4: 非 AskUserQuestion 的 control_request 自动放行

**选择**: 对 `control_request.request.subtype === "can_use_tool"` 且 tool_name 不是 AskUserQuestion 的请求，立即回复 `{ behavior: "allow", updatedInput: request.input }`。

**Spike 验证结果（2026-04-03）**: 在 `default` permission mode + `--permission-prompt-tool stdio` 下实测发现：**非交互工具（如 Bash、Read）不发 `control_request`，Claude Code 内部直接执行**。只有 `requiresUserInteraction() === true` 的工具（AskUserQuestion、ExitPlanMode）才通过 control protocol 路由。因此 D4 的 auto-allow 逻辑是一个**防御性处理**——当前环境下不会触发，但作为安全兜底保留（未来 Claude Code 版本可能改变行为）。

**理由**: 当前 companion 所有 session 都使用 `--permission-mode bypassPermissions`（或 `default`），非交互工具不需要人工审批。实测证明 CLI 内部已处理这些工具的权限，companion 只需关注 AskUserQuestion 的 control_request。

### D5: 新增 `answer_interactive_tool` companion 命令

**选择**: wire 包新增命令类型：
```typescript
type AnswerInteractiveToolCommand = {
  cmd: "answer_interactive_tool";
  req_id: string;
  session_id: string;
  call_id: string;
  answer: string;         // 选中选项的 label，或用户自定义输入文本
  question_index: number; // 问题索引（默认 0，单问题场景）
};
```

relay 新增 `POST /v1/sessions/:id/answer` REST 端点，接收 `{ call_id, answer, question_index? }`，转发为 companion command。companion 收到后查找 session 的 `pendingControlResponse`，将 `answer` 填入 `answers[question_index]`，合并原始 `questions`，构造完整 `updatedInput` 后写 `control_response`。

**替代方案**: 复用 `send_message` 命令，在 companion 端判断是否有 pending control request。
**否决原因**: 语义不清晰。send_message 是新 turn 的用户消息，answer_interactive_tool 是当前 turn 的 tool_result 回填。混用会导致竞态和状态混淆。

### D6: event-mapper 清理

**选择**: 移除 `interactiveToolActive`、`suppressUserMessageCount` 状态。`emittedTools` Map 保留用于去重（verbose 模式仍会发重复消息）。AskUserQuestion 的 `tool_call_started` 继续由 content block extraction 生成。`tool_call_completed` 不再需要抑制——control protocol 下 Claude Code 会在收到 `control_response` 后正常产出 tool_result。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| `--permission-prompt-tool stdio` 在某些 Claude Code 版本不可用 | companion 启动时检查 `claude --version`，低于 2.x 报错并回退 `-p` 模式 |
| Android 用户长时间不回答 AskUserQuestion，session 挂起 | 复用现有 idle timeout（30min），超时后发送 error control_response |
| 去掉 `-p` 后初始 prompt 发送时序变化 | 已验证 createSession 流程：spawn → wait init → writeUserMessage(prompt)，与 `-p` 模式行为一致。官方 SDK 也不使用 `-p` |
| control_request 和普通事件在 stdout 交错 | handleStdoutLine 已是串行逐行处理，不存在并发问题 |
| relay 重启时 companion 有 pending control request | companion 检测 relay 断连后向 pending request 发送 error response，让模型继续 |
| AskUserQuestion `updatedInput` 格式需精确匹配 `call({questions, answers, annotations})` 签名 | 必须保留原始 `questions` 数组 + 填入 `answers` 对象（key=问题索引 string, value=选中 label），不能用 `{ result }` 简写。已通过阅读 CLI 源码确认 `call` 函数签名 |
| happy 是自建 SDK，与官方 `@anthropic-ai/claude-agent-sdk` 有细节差异 | 已交叉验证官方 SDK 也使用相同的 `--permission-prompt-tool stdio` 机制。官方 SDK 的 `CanUseTool` callback 有更多参数（`suggestions`, `blockedPath`, `toolUseID` 等），但 `control_response` 核心格式一致 |
| `--permission-mode bypassPermissions` 与 `--permission-prompt-tool stdio` 共存时，非 `requiresUserInteraction` 工具是否仍发 `control_request` | CLI 源码显示当 `requireCanUseTool` 为 true 时所有工具都走 control protocol。AskUserQuestion 有 `requiresUserInteraction(){return!0}` 确保始终触发。需在实现阶段实测非交互工具的行为 |

## Open Questions

1. ~~是否需要支持 `control_cancel_request`？~~ → 需要。Claude Code 可能在用户未回答前取消工具调用（如 context window 满）。companion 需要处理 cancel 并通知 Android 卡片过期。
2. Skill 调用产生的 user message（expanded prompt）在 control protocol 下是否仍然需要抑制？→ 需实测确认 `--permission-prompt-tool stdio` 模式下 Skill 的 stdout 行为。
