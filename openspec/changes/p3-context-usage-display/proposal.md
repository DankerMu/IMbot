# Proposal: Session 上下文用量实时展示

## Problem

用户在 Android 端使用 session 时完全无法感知当前模型的上下文窗口使用情况。这导致：

1. 无法判断对话何时接近上下文上限，无法提前规划切分策略
2. 对长对话的 token 消耗完全无感，影响成本管控意识
3. 与 native Claude Code CLI 的体验差距（CLI 在状态栏显示 token 计数和上下文百分比）

**当前系统状态**：runtime 能拿到部分 usage，但 session summary 无法稳定展示真实上下文占用

```
Claude CLI (stream-json stdout)  →  companion event-mapper  →  relay DB/events  →  Android UI
     可能输出 usage 数据           提取 usage/model           session summary 缺失      列表/详情只能部分显示
```

## Scope

1. 调研 Claude Code stream-json 输出中的 usage/token 信息格式（`type: "usage"` 或嵌入 `result` 中）
2. Companion event-mapper 提取 usage / model / contextWindow 并映射为 wire 事件
3. Relay 将最新 usage snapshot 持久化到 `sessions` summary 行，并继续广播 `session_usage`
4. Android detail 页面顶部状态栏展示：模型名称 + 上下文用量进度条 + token 计数
5. Android session list / workspace session list 使用 session summary 显示真实 `[used/total]` metadata pill

## Out of Scope

- 对历史 turn 的 usage 明细回放或统计报表
- 基于 usage 的自动行为（如自动截断、预警弹窗）
- OpenClaw provider 的 usage 展示（不同协议）

## Success Criteria

1. 活跃 session 的 Android detail 页面顶部显示上下文用量：`[模型名] 12.5k / 200k (6%)`
2. 用量信息随对话进展实时更新（每个 turn 至少更新一次）
3. 进度条在接近上限（>80%）时变色预警
4. session list 能显示真实 session summary，例如 `glm-5` + `55k/200k`
5. 当 relay / transcript 没拿到 `context_window` 时，Android 不猜测总上下文，仅隐藏该元信息
6. 无功能回归
