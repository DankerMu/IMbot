# Proposal: Android 详情页三项显示 Bug 修复

## Problem

当前 Android 详情页存在三个影响阅读体验和数据准确性的 bug：

### Bug 1: Skill 工具内容完整泄露

调用 Skill 工具时，Skill 的完整 prompt 内容（通常数百行的技能定义）在手机端被当作普通工具的 args 完整渲染。Skill 工具名为 `"Skill"` 或 `"skill"`，在 `classifyTool()` 中归类为 `ToolCategory.OTHER`，使用 `GenericToolContent()` 无差别展示 args + result。

**影响**：用户在滑动时被迫看到大段无意义的技能定义文本，严重干扰消息流阅读。

### Bug 2: 工具调用结果不自动折叠

当工具完成执行后（`isRunning` 从 `true` 变为 `false`），`ToolCallCard` 的展开状态不变——因为只有 `LaunchedEffect` 在 `isRunning=true` 时强制展开，没有对应的完成时折叠逻辑。`rememberSaveable` 保持了运行时的 `expanded=true`。

**影响**：所有工具结果（grep 搜索结果、文件内容、bash 输出）全部展开，消息流充斥大量工具输出，严重影响阅读。

### Bug 3: 上下文窗口使用率计算错误

`SessionUsageState.totalTokens` 计算为 `inputTokens + outputTokens`，遗漏了 `cacheCreationTokens` 和 `cacheReadTokens`。Claude API 中 `input_tokens` 仅包含非缓存的输入 token，cache_creation 和 cache_read 是独立计数的。实际上下文占用 = `input_tokens + cache_creation_input_tokens + cache_read_input_tokens + output_tokens`。

**影响**：上下文使用百分比严重偏低（可能只显示真实值的 10-30%），用户无法准确判断何时接近上下文上限。

## Scope

纯 Android 端 UI 层修复，不涉及 relay/companion/wire 协议变更：

1. `ToolCategory.kt` — 新增 Skill 工具分类
2. `ToolCallCard.kt` — 新增 Skill 专用渲染 + 工具完成后自动折叠
3. `ToolContentRenderers.kt` — 新增 `SkillToolContent` composable
4. `ToolCallUtils.kt` — Skill 工具摘要提取
5. `DetailUtils.kt` — 修正 `totalTokens` 计算公式
6. `DetailUtilsTest.kt` — 测试覆盖

## Out of Scope

- Relay 端 cache token 持久化（`sessions` 表当前不存储 cache tokens，留给后续迭代）
- 工具结果折叠的动画优化
- 其他工具类型的专用渲染增强

## Success Criteria

1. Skill 工具在详情页只显示一行摘要（如 `技能: commit`），args 内容被抑制不显示
2. 工具完成执行后自动折叠为摘要行，用户可手动点击展开查看详情
3. 上下文用量百分比包含 cache tokens，数值与 Claude Code CLI 的 `/context` 命令一致
4. 无功能回归（现有 DetailUtilsTest 和 DetailViewModelTest 全部通过）
