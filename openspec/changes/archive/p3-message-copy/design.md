# Design: Message Copy & Text Selection

## Architecture

纯 Android Compose 交互层改动。利用 Compose 的 `SelectionContainer` 和自定义长按手势处理。

## Component Design

### 1. Message Long-Press Menu

**触发**：长按任意消息气泡（Agent / User / ToolCall）。

**实现**：`ModalBottomSheet`（Material 3），统一的操作菜单。

```
┌─────────────────────────────────┐
│  📋  复制消息                    │
│  📝  选择文本                    │
│  ──────────────────────────      │
│  📤  分享（未来）                 │  ← disabled, grayed out
│  ↩️  引用回复（未来）             │  ← disabled, grayed out
└─────────────────────────────────┘
```

**可用操作**：
```kotlin
sealed interface MessageAction {
    data class CopyMessage(val text: String) : MessageAction
    data object SelectText : MessageAction
    // Future:
    // data class Share(val text: String) : MessageAction
    // data class QuoteReply(val text: String) : MessageAction
}
```

**消息类型决定可用操作**：
| 消息类型 | 复制消息 | 选择文本 |
|---------|---------|---------|
| AgentMessage | ✓ (content) | ✓ |
| UserMessage | ✓ (text) | ✓ |
| ToolCall | ✓ (summary) | ✗ |
| StatusChange | ✗ | ✗ |

### 2. Copy Message

**复制内容**：纯文本（Markdown 源码），不含格式标记。

```kotlin
fun copyableText(item: MessageItem): String? = when (item) {
    is MessageItem.AgentMessage -> item.content.takeIf { it.isNotBlank() }
    is MessageItem.UserMessage -> item.text.takeIf { it.isNotBlank() }
    is MessageItem.ToolCall -> buildToolCallSummary(item)
    is MessageItem.StatusChange -> null
}
```

**反馈**：
- 复制成功 → Snackbar "已复制到剪贴板" + haptic feedback (HapticFeedbackType.LongPress)
- 空内容 → 不响应

### 3. Text Selection Mode

**实现方案**：利用 Compose `SelectionContainer` 包裹消息内容。

**方案对比**：
| 方案 | 优点 | 缺点 |
|------|------|------|
| A: 全局 SelectionContainer | 简单 | LazyColumn 不支持跨 item 选择 |
| B: 单消息 SelectionContainer | 兼容 LazyColumn | 需要进入选择模式 |
| C: 自定义 BasicTextField readonly | 完全控制 | 实现复杂 |

**选定方案 B**：
1. 用户长按 → 选择 "选择文本" → 该消息进入选择模式
2. 选择模式下，消息内容被 `SelectionContainer` 包裹
3. Compose 原生提供系统选择手柄和复制菜单
4. 点击消息外区域退出选择模式

```kotlin
@Composable
fun SelectableMessageContent(
    content: String,
    isSelectionMode: Boolean,
    onExitSelection: () -> Unit,
) {
    if (isSelectionMode) {
        SelectionContainer {
            MarkdownText(content = content)
        }
    } else {
        MarkdownText(content = content)
    }
}
```

**注意**：`SelectionContainer` 内的 `Text` composable 自动支持文本选择。但 `AnnotatedString` 的选择可能仅返回纯文本。这是可接受的行为。

### 4. Haptic & Visual Feedback

- 长按触发：`HapticFeedbackType.LongPress`
- 复制成功：`Snackbar` "已复制到剪贴板"（2s 自动消失）
- 进入选择模式：消息背景临时高亮为 `Primary.copy(alpha = 0.08f)`
- 退出选择模式：背景恢复正常

## State Management

```kotlin
// DetailViewModel additions
data class DetailUiState(
    // ... existing fields
    val messageMenuTarget: MessageItem? = null,  // 长按菜单目标
    val selectionModeMessageId: String? = null,   // 文本选择模式的消息 ID
)

fun onMessageLongPress(item: MessageItem) {
    _uiState.update { it.copy(messageMenuTarget = item) }
}

fun onDismissMessageMenu() {
    _uiState.update { it.copy(messageMenuTarget = null) }
}

fun onEnterSelectionMode(messageId: String) {
    _uiState.update { it.copy(
        messageMenuTarget = null,
        selectionModeMessageId = messageId,
    ) }
}

fun onExitSelectionMode() {
    _uiState.update { it.copy(selectionModeMessageId = null) }
}
```

## File Changes

| File | Change |
|------|--------|
| `ui/detail/MessageBubble.kt` | 添加 onLongPress 手势、选择模式 UI |
| `ui/detail/MessageActionSheet.kt` | 新增长按操作菜单 ModalBottomSheet |
| `ui/detail/SessionDetailScreen.kt` | 集成菜单状态、选择模式退出 |
| `ui/detail/DetailViewModel.kt` | 新增 messageMenuTarget / selectionMode 状态 |
| `ui/detail/DetailUtils.kt` | 新增 copyableText() 函数 |

## Constraints

- `SelectionContainer` 在 LazyColumn 中不支持跨 item 选择——设计上限制为单条消息选择，可接受
- MarkdownKatex (WebView) 内的文本选择由 WebView 原生处理，不受 Compose SelectionContainer 控制——这是已知限制，不在本 change 范围内
- 代码块的复制按钮保持独立，不冲突
