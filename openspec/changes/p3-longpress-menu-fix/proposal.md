# Proposal: 长按复制/选择菜单稳定性修复

## Problem

E2E 测试发现消息长按后复制/选择菜单无法稳定弹出。截图对比显示长按前后画面无变化，ModalBottomSheet 没有出现。

**根因分析**：

1. **手势冲突**：`LazyColumn` 的垂直滚动手势和 `combinedClickable.onLongClick` 存在竞争。当用户手指微动（即使很小），系统判定为滚动而非长按，导致 `onLongClick` 不触发。

2. **ToolCall 和 InteractiveToolCall 缺少操作**：
   - `hasActions(InteractiveToolCall)` 返回 `false`，导致 InteractiveToolCard 完全不注册长按手势
   - `hasActions(StatusChange)` 返回 `false`，状态变更消息无法复制

3. **ModalBottomSheet 半展开状态**：默认 `SheetValue.PartiallyExpanded` 可能导致内容不可见（内容太少时 sheet 高度不足），用户感知为"没弹出来"。

## Scope

- 修复长按手势检测的可靠性
- 扩展所有消息类型的可操作性
- 修复 BottomSheet 展开状态

## Success Criteria

- 所有消息类型（Agent/User/ToolCall/InteractiveToolCall/StatusChange）长按都能弹出菜单
- 长按触发率从当前不确定提升到 >95%
- 弹出时有触觉反馈
- ModalBottomSheet 内容完全可见
