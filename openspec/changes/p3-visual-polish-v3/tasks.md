# Tasks: Visual Polish V3

## T1: MessageAvatar 提取 + 双侧头像

- [x] 提取 `MessageAvatar(label, backgroundColor, contentColor)` composable
- [x] UserMessageBubble: Row 包裹 Surface + Avatar（右侧），时间戳 padding end=48dp
- [x] AgentMessageBubble: Row 包裹 Avatar（左侧） + Surface

### T1 测试
- [x] ThemeResolutionTest: `assistantBubbleBackground` 返回实色（light/dark）
- [x] 手动 E2E: E2E-A38 / E2E-A39 截图验证

## T2: Assistant 气泡化

- [x] `assistantMessageBubble` shape 定义（topStart=16, topEnd=16, bottomStart=4, bottomEnd=16）
- [x] `AssistantBubbleLight` / `AssistantBubbleDark` 颜色常量
- [x] `assistantBubbleBackground()` 返回实色
- [x] AgentMessageBubble Surface: shape + BorderStroke(0.5dp, outlineVariant 0.45 alpha)
- [x] 内部 Column padding(horizontal=14, vertical=10)
- [x] 移除 StreamingCursor

### T2 测试
- [x] ThemeResolutionTest: light → AssistantBubbleLight, dark → AssistantBubbleDark
- [x] 手动 E2E: 明暗模式气泡截图

## T3: 自动滚动机制重写

- [x] 删除 `SCROLL_PAUSE_THRESHOLD_DP` 和 `calculateDistanceFromBottomPx()`
- [x] 新增 `isNearBottom()`: 最后 item 底边 ≤ NEAR_BOTTOM_TOLERANCE_PX(80) 即 true
- [x] 新增 `ScrollObservation` / `ListViewportPosition` data class
- [x] `onScrollDistanceChanged` → `onScrollPositionUpdate(current, nearBottom, userInitiatedScrollAway)`
- [x] 三态逻辑：nearBottom 恢复 / userInitiated 暂停 / 被动漂移不变
- [x] `programmaticScrollInProgress` 标志：animateScrollToItem 期间为 true
- [x] `alignTargetItemBottom()`: repeat(3) 精确对齐
- [x] ViewModel `onScrollPositionChanged` 参数更新

### T3 测试
- [x] DetailUtilsTest: `scrolling away from bottom pauses auto scroll` — nearBottom=false, userInitiated=true
- [x] DetailUtilsTest: `scrolling back to bottom resumes auto scroll` — nearBottom=true
- [x] DetailUtilsTest: `passive drift away from bottom keeps auto scroll enabled` — 两个 false → 状态不变
- [x] 手动 E2E: E2E-A46 — 长回复自动贴底不中断

## T4: Markdown 末尾 Padding 清零

- [x] `markdownBlockBottomPadding(index, blocks, defaultPadding)` 辅助函数
- [x] 应用于 Paragraph / Heading / Blockquote / CodeBlock / Math / Table
- [x] ListItem lastIndex → 0.dp

### T4 测试
- [x] MarkdownRenderingTest: `last markdown block does not keep trailing bottom padding`
- [x] 手动 E2E: 气泡内无多余底部间距

## T5: 状态指示器移至顶栏

- [x] `DetailTopBarTitle` 移除 `status` 参数
- [x] 新增 `TopBarStatusBadge` 在 TopAppBar actions 区
- [x] 使用 `StatusIndicator(variant = Badge)`

### T5 测试
- [x] 手动 E2E: E2E-A41 更新断言 — 顶栏右上角状态指示器

## T6: EventProcessor 简化

- [x] `appendStatusChangeIfSignificant`: 增加 `status.isBlank()` 提前返回
- [x] 简化条件：`isTerminal || hasMessage`

### T6 测试
- [x] EventProcessorTest: `session_status_changed to running stays in top bar only`

## T7: E2E 测试计划更新

- [x] E2E-A38: 更新断言 — assistant 气泡 + 圆角 + 左对齐
- [x] E2E-A41: 更新为顶栏状态指示器
- [x] E2E-A46: 新增断言 — 长回复自动贴底
- [x] E2E-A40: 间距断言保持不变

## T8: FAB 动画修饰符位置

- [x] `AnimatedVisibility` modifier 上移 align + padding，确保动画退出时不占位
