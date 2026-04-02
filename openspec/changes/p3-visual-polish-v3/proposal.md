# Proposal: Visual Polish V3 — 气泡化 + 自动贴底修复

## Problem

Visual Polish V2 将 assistant 消息改为"去气泡化"（透明背景直出），但实际效果在深色模式下对比度不足，且缺少视觉边界使消息难以区分。同时，自动滚动机制基于像素距离阈值（`SCROLL_PAUSE_THRESHOLD_DP = 100f`），在长消息渲染过程中因单帧内容高度突变而误判用户已滑离底部，导致自动贴底失效、FAB 错误弹出。

具体问题：
1. **Assistant 消息需要气泡**：去气泡设计在实际使用中缺乏视觉边界感，消息间难以快速区分
2. **缺少头像**：用户和 assistant 消息均无头像标识，对话双方不够直观
3. **自动滚动误触发**：距离阈值检测在长回复渲染时产生假阳性，用户未主动滑动却收到 FAB
4. **Markdown 末尾多余 padding**：气泡内最后一个 block 的 bottom padding 导致气泡底部间距过大
5. **状态指示器位置**：标题行内的状态点占用标题空间，移至顶栏右侧 action 区更合理

## Design Direction

- **Assistant 气泡化**：带 0.5dp 半透明边框的 Surface，`assistantMessageBubble` 圆角（左下 4dp，其余 16dp —— 与用户气泡镜像）
- **双侧头像**：36dp 圆形 avatar — 用户显示"我"在右侧，assistant 显示 provider 首字母在左侧
- **布尔滚动检测**：`isNearBottom()` + `userInitiatedScrollAway` 替代像素距离阈值，通过 `programmaticScrollInProgress` 标志区分程序性滚动与用户手势
- **末尾 padding 清零**：`markdownBlockBottomPadding()` 辅助函数，最后一个 block 返回 0.dp
- **状态移至顶栏 badge**：从 `DetailTopBarTitle` 移除 status 参数，改为 action 区的 `TopBarStatusBadge`

## Scope

Session Detail 页面：MessageBubble、SessionDetailScreen、MarkdownText、DetailUtils、EventProcessor、Color、Shape。不改变其他页面。不改变功能逻辑或数据流。

## Success Criteria

- 双侧头像清晰可见（用户右侧、assistant 左侧）
- Assistant 消息有浅色气泡 + 细边框，明暗模式均适配
- 长回复渲染期间自动贴底不中断（用户未主动滑动时 FAB 不弹出）
- 用户主动上滑时 FAB 正常弹出
- Markdown 气泡内无多余底部间距
- 状态指示器在顶栏右侧显示
- 所有现有测试通过，新增测试覆盖滚动逻辑和 padding 逻辑
