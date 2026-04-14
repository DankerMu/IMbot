# Design: p3-detail-display-fixes

## Context

Android 详情页 (`SessionDetailScreen`) 通过 `EventProcessor` 解析 companion 事件流，将工具调用渲染为 `ToolCallCard` 组件，将上下文用量渲染为顶部状态栏。三个 bug 都是纯展示层问题，数据源（relay/companion 事件）本身正确。

## Root Cause Analysis

### Bug 1: Skill 工具内容泄露

**数据流**：
```
companion event: { type: "tool_use", tool_name: "Skill", input: { skill: "commit", args: "..." } }
  → EventProcessor.appendToolCallStarted(): MessageItem.ToolCall(toolName="Skill", args=<full JSON>)
  → ToolCallCard → classifyTool("Skill") → ToolCategory.OTHER
  → GenericToolContent() → 无差别展示 args + result
```

**根因**：`ToolCategory.kt:16-19` 定义了 READ/WRITE/BASH/SEARCH 四类工具名集合，`"skill"` 不在任何集合中，落入 `OTHER` 分支（line 60）。`GenericToolContent()` 在 `ToolContentRenderers.kt:266-289` 中对 `OTHER` 类型无条件展示 `item.args`，而 Skill 的 args 包含完整的技能 prompt 定义（通常数百行）。

### Bug 2: 工具结果不自动折叠

**数据流**：
```
tool_use event → ToolCall(isRunning=true) → ToolCallCard(expanded=true via LaunchedEffect)
tool_result event → ToolCall(isRunning=false) → expanded 仍为 true（无折叠逻辑）
```

**根因**：`ToolCallCard.kt:60` 初始化 `expanded = item.isRunning`，`LaunchedEffect(item.isRunning)` 在 line 82-86 只处理 `isRunning=true` 的展开，没有 `else` 分支处理完成时的折叠。`rememberSaveable` 保持了运行时的展开状态。

### Bug 3: 上下文计算错误

**数据流**：
```
companion session_usage event:
  { input_tokens: 500, output_tokens: 200, cache_creation_input_tokens: 8000, cache_read_input_tokens: 150000, context_window: 1000000 }
  → EventProcessor.toSessionUsageState(): 四个 token 字段全部正确解析
  → SessionUsageState.totalTokens = 500 + 200 = 700  ← BUG: 应为 500+200+8000+150000 = 158700
  → usagePercent = 700/1000000 = 0.07%  ← 实际应为 15.87%
```

**根因**：`DetailUtils.kt:57-58` 中 `totalTokens` getter 仅加 `inputTokens + outputTokens`。Claude API 的 `input_tokens` 字段只包含非缓存输入 token，`cache_creation_input_tokens` 和 `cache_read_input_tokens` 是独立字段，三者合计才是真实的输入 token 总量。

## Decisions

### D1: 新增 `ToolCategory.SKILL` 分类

**选择**：在 `ToolCategory` enum 中新增 `SKILL` 项，`classifyTool()` 中添加 `"skill"` → `ToolCategory.SKILL` 映射。

**理由**：
- Skill 工具的渲染需求与其他 OTHER 工具完全不同（args 应被抑制）
- 枚举扩展是最小侵入的修改，不影响现有分类逻辑
- 未来可能需要独立的 Skill 图标和颜色

**工具名匹配**：`setOf("skill")` — Claude Code 的 Skill 工具名固定为 `"Skill"`（大小写不敏感，`classifyTool` 已做 `lowercase()`）。

### D2: Skill 工具渲染策略 — 摘要行 + args 抑制

**选择**：新增 `SkillToolContent` composable，只展示 skill 名称和执行结果摘要，不展示 args。

**理由**：
- Skill 的 args 是完整的技能定义 prompt，对用户毫无价值
- 对用户有价值的信息是：哪个 skill 被调用了（可从 args JSON 中提取 `skill` 字段）
- 结果可简短展示（通常是 "Skill invoked successfully" 或类似信息）

**Args 解析**：从 `item.args` JSON 字符串中提取 `skill` 字段作为显示名，fallback 到 `"Skill"`。

### D3: 工具完成后自动折叠

**选择**：在 `ToolCallCard` 的 `LaunchedEffect(item.isRunning)` 中增加 `else` 分支，当 `isRunning` 从 `true` 变为 `false` 时设置 `expanded = false`。

**理由**：
- 用户主要关心 assistant 的文本回复，工具调用是中间过程
- 折叠后的摘要行（工具图标 + 名称 + 文件路径/命令）已能传达核心信息
- 用户仍可点击展开查看完整输出
- `rememberSaveable` 保证手动展开后刷新不会重新折叠

**注意**：首次渲染已完成的工具（如加载历史事件）时，`rememberSaveable` 初始值为 `mutableStateOf(item.isRunning)` 即 `false`，已经是折叠状态，无需额外处理。

### D4: totalTokens 计算修正

**选择**：`totalTokens = inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens`

**理由**：
- Claude API 文档明确：上下文窗口占用 = 所有输入 token（non-cached + cache_creation + cache_read）+ 输出 token
- `input_tokens` 仅为非缓存输入 token，不含 cache 部分
- 修正后百分比与 Claude Code CLI `/context` 命令显示一致

**替代方案被否**：
- 只加 `cacheReadTokens`（忽略 `cacheCreationTokens`）：cache_creation 同样占用上下文窗口，不能忽略
- 在 relay 端修正：relay 不持久化 cache token 字段，Android 端的实时数据已包含完整值，本地修正更简单

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Skill 工具名可能不总是 `"Skill"` | 某些版本可能用不同名称 | `classifyTool()` 已做 lowercase，且 `ToolSearch` 等工具走 `OTHER` 分支不受影响 |
| 自动折叠可能让用户错过重要工具输出 | 需要额外一次点击才能看到输出 | 折叠摘要已包含关键信息（命令/文件路径），错误工具用红色状态条醒目提示 |
| Relay `sessions` 表不存 cache tokens | 完成态 session 重新打开时，事件回放前 usage 不含 cache | 事件回放后 `session_usage` 事件会恢复正确的 cache token 值；影响只在短暂的加载期间 |
| `ToolSearch` 工具也可能出现在 OTHER 中 | 不影响，`ToolSearch` 的 args 较短，GenericToolContent 展示合理 | 仅 Skill 需要特殊处理 |
