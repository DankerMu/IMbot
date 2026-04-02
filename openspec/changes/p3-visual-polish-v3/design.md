# Design: Visual Polish V3

## 1. MessageBubble 重构

### 1.1 MessageAvatar 组件

提取通用 `MessageAvatar` composable：
- 36dp 圆形 `Box`，背景色 + 居中文本
- 用户侧：`label = "我"`，背景 = `userBubbleBackground`，文字色 = `userBubbleTextColor`
- Assistant 侧：`label = providerShortLabel(provider)`，背景 = `badgeColor.copy(alpha = 0.16f)`

### 1.2 UserMessageBubble

- 外层 `Row(spacedBy = 12.dp, verticalAlignment = Bottom)` 包裹 Surface + Avatar
- Avatar 在右侧
- 时间戳 `padding(end = 48.dp)` 对齐气泡左边缘（36dp avatar + 12dp gap）

### 1.3 AgentMessageBubble

- 外层 `Row(spacedBy = 12.dp, verticalAlignment = Top)` 包裹 Avatar + Surface
- Avatar 在左侧
- Surface 用 `assistantMessageBubble` shape + `BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.45f))`
- 内部 `Column(padding = 14.dp horizontal, 10.dp vertical)` 包裹 MarkdownText + 展开按钮
- 移除 `StreamingCursor`（streaming 状态已由顶栏指示器表达）

## 2. 自动滚动机制

### 2.1 isNearBottom() 检测

替换 `calculateDistanceFromBottomPx()` → `isNearBottom()`：
- 如果最后一个 list item 不在可见范围 → false
- 如果最后 item 的底边超出 viewport 末端 ≤ `NEAR_BOTTOM_TOLERANCE_PX (80)` → true

### 2.2 三态滚动逻辑

`onScrollPositionUpdate(current, nearBottom, userInitiatedScrollAway)`:
- `nearBottom = true` → 恢复自动滚动，清除 FAB
- `userInitiatedScrollAway = true` → 暂停自动滚动，显示 FAB
- 两者都 false（被动漂移） → 不改变状态

### 2.3 程序性滚动标记

- `programmaticScrollInProgress` 布尔状态
- `animateScrollToItem` + `alignTargetItemBottom` 执行期间设为 true
- 滚动观察器在 `programmaticScrollInProgress = true` 时不将位移视为用户操作

### 2.4 alignTargetItemBottom()

重复最多 3 次：计算目标 item 底边到 viewport 底边的 overflow，`animateScrollBy(overflow)` 精确对齐。

## 3. Markdown 末尾 Padding

`markdownBlockBottomPadding(index, blocks, defaultPadding)`：
- `index == blocks.lastIndex` → `0.dp`
- 否则 → `defaultPadding`

应用于：Paragraph、Heading、Blockquote、CodeBlock、Math、Table、ListItem（最后一个 item 返回 0.dp 而非 `MarkdownListBlockSpacing`）。

## 4. 状态指示器位置

- `DetailTopBarTitle` 移除 `status` 参数，不再在标题行内显示状态
- 新增 `TopBarStatusBadge` composable 在 TopAppBar actions 区域
- 使用 `StatusIndicator(variant = Badge)`

## 5. Theme 扩展

### Color
- `AssistantBubbleLight = SurfaceLight`
- `AssistantBubbleDark = SurfaceSecondaryDark`
- `assistantBubbleBackground()` 返回实色替代 `Color.Transparent`

### Shape
- `assistantMessageBubble`: topStart=16, topEnd=16, bottomStart=4, bottomEnd=16（与 userMessageBubble 镜像）

## 6. EventProcessor 简化

`appendStatusChangeIfSignificant`：
- 增加 `status.isBlank()` 提前返回
- 保留终态 + 有消息的非终态，去除多余注释
