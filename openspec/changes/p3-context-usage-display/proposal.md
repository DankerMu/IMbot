# Proposal: Session 上下文用量实时展示

## Problem

用户在 Android 端使用 session 时完全无法感知当前模型的上下文窗口使用情况。这导致：

1. 无法判断对话何时接近上下文上限，无法提前规划切分策略
2. 对长对话的 token 消耗完全无感，影响成本管控意识
3. 与 native Claude Code CLI 的体验差距（CLI 在状态栏显示 token 计数和上下文百分比）

**当前系统状态**：全链路零支持

```
Claude CLI (stream-json stdout)  →  companion event-mapper  →  relay DB/events  →  Android UI
     可能输出 usage 数据           不提取 usage 信息          无 usage 列/事件        无 usage 展示
```

## Scope

1. 调研 Claude Code stream-json 输出中的 usage/token 信息格式（`type: "usage"` 或嵌入 `result` 中）
2. Companion event-mapper 提取 usage 信息并映射为新的 wire 事件
3. Relay 转发 usage 事件（无需持久化，仅广播）
4. Android detail 页面顶部状态栏展示：模型名称 + 上下文用量进度条 + token 计数

## Out of Scope

- 历史 session 的 usage 回溯（仅实时展示当前活跃 session）
- 基于 usage 的自动行为（如自动截断、预警弹窗）
- Relay 侧 usage 数据持久化和统计
- OpenClaw provider 的 usage 展示（不同协议）

## Success Criteria

1. 活跃 session 的 Android detail 页面顶部显示上下文用量：`[模型名] 12.5k / 200k (6%)`
2. 用量信息随对话进展实时更新（每个 turn 至少更新一次）
3. 进度条在接近上限（>80%）时变色预警
4. session 非 running/idle 状态时不显示用量（已结束的 session 不再更新）
5. 无性能影响：usage 事件不持久化、不触发额外网络请求
6. 无功能回归
