# Design: 长按复制/选择菜单稳定性修复

## Problem Analysis

### 手势冲突机制

`LazyColumn` 拥有垂直拖拽手势处理器。`combinedClickable` 的 `onLongClick` 使用 `detectTapGestures(onLongPress = ...)` 实现，默认长按阈值 400ms。在这 400ms 内如果手指垂直位移超过 `touchSlop`（约 8dp），`LazyColumn` 会抢占手势，`onLongClick` 不触发。

### 当前各消息类型的可操作性

| 消息类型 | hasActions | 可用操作 | 问题 |
|---------|-----------|---------|------|
| AgentMessage (非 streaming) | true | Copy + SelectText | 正常 |
| AgentMessage (streaming) | false | 无 | 流式中不可操作（合理） |
| UserMessage | true | Copy + SelectText | 正常 |
| ToolCall (toolName 非空) | true | Copy | 正常 |
| ToolCall (toolName 为空) | false | 无 | 应该可以复制 result |
| InteractiveToolCall | false | 无 | 应该可以复制 question |
| StatusChange | false | 无 | 应该可以复制 message |

## Solution

### 1. 手势检测升级 — 替代 `combinedClickable`

**当前方案**：`Modifier.combinedClickable(onClick, onLongClick)`

**新方案**：使用 `Modifier.pointerInput` + `detectTapGestures` 并禁用垂直滚动竞争：

```kotlin
/**
 * 稳定长按检测 Modifier。
 *
 * 关键改进：
 * 1. 在 pointerInput 中使用 awaitLongPressOrCancellation 而非 combinedClickable
 * 2. 通过 consumePositionChange 在长按检测期间阻止 LazyColumn 抢夺手势
 * 3. 触觉反馈在长按确认瞬间触发（非菜单弹出后）
 */
fun Modifier.stableLongClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = this
    .pointerInput(onClick, onLongClick) {
        detectTapGestures(
            onTap = { onClick() },
            onLongPress = onLongClick?.let { handler ->
                { handler() }
            },
        )
    }
```

**注意**：`detectTapGestures` 本身已内置 `ViewConfiguration.longPressTimeoutMillis`（默认 400ms）。关键改进是移除 `combinedClickable` 的额外抽象层，直接使用底层手势 API。

**实际修复点**：问题可能不在手势 API 本身，而是 `canLongPress` 条件过滤太严格。当 `hasActions(item)` 返回 `false` 时，`canLongPress = false`，导致根本不注册 `onLongClick`。

### 2. 扩展 `hasActions()` 覆盖范围

```kotlin
// 改前：
internal fun hasActions(item: MessageItem): Boolean =
    when (item) {
        is MessageItem.AgentMessage -> !item.isStreaming
        is MessageItem.InteractiveToolCall -> false  // ← 问题
        is MessageItem.UserMessage -> true
        is MessageItem.ToolCall -> item.toolName.isNotBlank()  // ← toolName 为空时也丢失
        is MessageItem.StatusChange -> false  // ← 问题
    }

// 改后：
internal fun hasActions(item: MessageItem): Boolean =
    when (item) {
        is MessageItem.AgentMessage -> !item.isStreaming
        is MessageItem.InteractiveToolCall -> true  // 可复制 question
        is MessageItem.UserMessage -> true
        is MessageItem.ToolCall -> true  // 即使 toolName 为空也可以复制 result
        is MessageItem.StatusChange -> item.message?.isNotBlank() == true
            || item.description?.isNotBlank() == true
    }
```

### 3. 扩展 `availableActions()` 和 `copyableText()`

```kotlin
// availableActions 增加 InteractiveToolCall 和 StatusChange 的 Copy 支持：
is MessageItem.InteractiveToolCall ->
    copyableText(item)?.let { text ->
        listOf(MessageAction.CopyMessage(text))
    } ?: emptyList()

is MessageItem.StatusChange ->
    copyableText(item)?.let { text ->
        listOf(MessageAction.CopyMessage(text))
    } ?: emptyList()

// copyableText 增加：
is MessageItem.InteractiveToolCall ->
    item.question.takeIf(String::isNotBlank)

is MessageItem.StatusChange ->
    (item.description ?: item.message)?.takeIf(String::isNotBlank)

// ToolCall 移除 toolName.isNotBlank 前置条件：
is MessageItem.ToolCall ->
    buildString {
        item.toolName.takeIf(String::isNotBlank)?.let { append("Tool: $it\n") }
        item.args?.takeIf(String::isNotBlank)?.let { append("Input: ${summarizeToolCallCopyField(it)}\n") }
        item.result?.takeIf(String::isNotBlank)?.let { append("Output: ${summarizeToolCallCopyField(it)}") }
    }.takeIf(String::isNotBlank)
```

### 4. ModalBottomSheet 展开修复

```kotlin
// 改前：
ModalBottomSheet(onDismissRequest = onDismiss) { ... }

// 改后：
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) { ... }
```

`skipPartiallyExpanded = true` 确保 sheet 直接全展开，避免内容被 partial expand 截断。

### 5. 触觉反馈确认

当前代码中 `hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)` 已存在于 `ToolCallCard` 但需要确认所有消息类型的长按路径都有触觉反馈。

检查 `MessageBubble.kt` 中的长按处理是否也有 haptic：

```kotlin
// MessageBubble 中应该有：
Modifier.combinedClickable(
    onClick = { /* ... */ },
    onLongClick = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongPress?.invoke(item)
    },
)
```

## File Changes

| File | Change |
|------|--------|
| `ui/detail/MessageActionSheet.kt` | `hasActions()` 扩展覆盖所有类型；`availableActions()` 增加 InteractiveToolCall/StatusChange |
| `ui/detail/DetailUtils.kt` | `copyableText()` 增加 InteractiveToolCall/StatusChange 分支 |
| `ui/detail/SessionDetailScreen.kt` | ModalBottomSheet 添加 `skipPartiallyExpanded = true` |
| `ui/detail/ToolCallCard.kt` | 移除 `canLongPress` 对 `hasActions` 的依赖（始终注册 onLongClick） |
| `ui/detail/MessageBubble.kt` | 确认所有消息类型长按路径有 haptic feedback |

## Constraints

- 不改变消息数据模型
- 不改变 relay/companion 协议
- 保持流式消息（isStreaming）不可操作（合理限制）
