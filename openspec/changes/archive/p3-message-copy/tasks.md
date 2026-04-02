# Tasks: Message Copy & Text Selection

## 1. Message Action Sheet

### 1.1 MessageAction 数据模型
- [ ] 在 `ui/detail/` 下定义 `MessageAction` sealed interface
- [ ] CopyMessage(text: String), SelectText 两种初始 action
- [ ] `availableActions(item: MessageItem): List<MessageAction>` 函数（AgentMessage: [Copy, Select], UserMessage: [Copy, Select], ToolCall: [Copy], StatusChange: []）

### 1.2 MessageActionSheet composable
- [ ] 新建 `ui/detail/MessageActionSheet.kt`
- [ ] ModalBottomSheet 实现
- [ ] 每行：icon + 操作名称（"复制消息" / "选择文本"）
- [ ] 未来扩展项灰色 disabled 状态（分享、引用回复）
- [ ] 点击回调 `onAction(MessageAction)`

### 1.3 Long-press 手势
- [ ] 修改 `MessageBubble.kt`：添加 `combinedClickable(onLongClick = ...)` 到 Agent/User/ToolCall 消息
- [ ] 长按触发 haptic feedback（HapticFeedbackType.LongPress）
- [ ] 长按回调 `onMessageLongPress(item: MessageItem)`

## 2. Copy Message

### 2.1 copyableText 函数
- [ ] 在 `DetailUtils.kt` 新增 `copyableText(item: MessageItem): String?`
- [ ] AgentMessage → content（纯文本）
- [ ] UserMessage → text
- [ ] ToolCall → "Tool: <toolName>\nInput: <summary>\nOutput: <summary>"
- [ ] StatusChange → null

### 2.2 Clipboard 操作
- [ ] 使用 `ClipboardManager.setPrimaryClip()` 复制文本
- [ ] 成功后显示 Snackbar "已复制到剪贴板"（2s）
- [ ] Haptic feedback 确认

## 3. Text Selection Mode

### 3.1 Selection 状态管理
- [ ] DetailViewModel 新增 `selectionModeMessageId: String?` 状态
- [ ] `onEnterSelectionMode(messageId)` / `onExitSelectionMode()` 方法
- [ ] 选择 "选择文本" 操作 → 设置 selectionModeMessageId → 关闭 ActionSheet

### 3.2 SelectableMessageContent composable
- [ ] 修改 `MessageBubble.kt`：当 `isSelectionMode = true` 时用 `SelectionContainer` 包裹内容
- [ ] 选择模式下消息背景高亮：`Primary.copy(alpha = 0.08f)` 叠加
- [ ] 系统选择手柄和复制菜单由 Compose 原生提供

### 3.3 退出选择模式
- [ ] 点击消息外区域 → `onExitSelectionMode()`
- [ ] 返回按钮拦截 → 优先退出选择模式
- [ ] 新消息到达 → 自动退出选择模式

## 4. ViewModel 集成

### 4.1 DetailUiState 扩展
- [ ] 新增 `messageMenuTarget: MessageItem?` 字段
- [ ] 新增 `selectionModeMessageId: String?` 字段
- [ ] `onMessageLongPress(item)` → 设置 messageMenuTarget
- [ ] `onDismissMessageMenu()` → 清除 messageMenuTarget

### 4.2 SessionDetailScreen 集成
- [ ] 消息列表传递 `onLongPress` 回调
- [ ] `messageMenuTarget` 非 null → 显示 MessageActionSheet
- [ ] 处理 MessageAction 分发（CopyMessage → clipboard, SelectText → selectionMode）
- [ ] selectionModeMessageId 传递到对应 MessageBubble

## 5. Tests

> **测试环境**：使用 book CLI 作为 provider 进行端到端验证。通过 book session 产生各种消息类型，在手机端测试复制和选择行为。

### 5.1 copyableText — Unit Tests

**正常路径**：
- [ ] AgentMessage(content="hello") → "hello"
- [ ] UserMessage(text="world") → "world"
- [ ] ToolCall(toolName="Read", input="file.kt", output="content...") → "Tool: Read\nInput: file.kt\nOutput: content..."
- [ ] StatusChange(status="running") → null

**边界值**：
- [ ] AgentMessage(content="") 空字符串 → null
- [ ] AgentMessage(content="   ") 纯空白 → null
- [ ] UserMessage(text="") → null
- [ ] ToolCall 无 output → "Tool: Read\nInput: file.kt"（省略 Output 行）
- [ ] ToolCall 无 input 无 output → "Tool: Read"（仅 tool name）
- [ ] AgentMessage 含 Markdown `**bold** and `code`` → 原样返回 Markdown 源码（不 strip）
- [ ] AgentMessage 超长内容（10000 字符）→ 完整返回，不截断
- [ ] AgentMessage 含换行符 "line1\nline2" → 保留换行
- [ ] AgentMessage 含 emoji "hello 🎉" → 完整保留

### 5.2 availableActions — Unit Tests

- [ ] AgentMessage → [CopyMessage, SelectText]
- [ ] UserMessage → [CopyMessage, SelectText]
- [ ] ToolCall → [CopyMessage]（无 SelectText，因为 ToolCall 不含可选文本）
- [ ] StatusChange → []（空列表，不弹菜单）
- [ ] AgentMessage(content="") 空内容 → [SelectText]（CopyMessage 被过滤，无内容可复制）
- [ ] AgentMessage(isStreaming=true) 流式中 → []（流式中不允许操作，避免复制不完整内容）

### 5.3 ViewModel 状态 — Unit Tests

**messageMenuTarget**：
- [ ] 初始状态 → messageMenuTarget == null
- [ ] onMessageLongPress(agentMsg) → messageMenuTarget == agentMsg
- [ ] onDismissMessageMenu() → messageMenuTarget == null
- [ ] 连续两次 onMessageLongPress(msg1) 再 onMessageLongPress(msg2) → target == msg2（替换）

**selectionModeMessageId**：
- [ ] 初始状态 → selectionModeMessageId == null
- [ ] onEnterSelectionMode("msg-123") → selectionModeMessageId == "msg-123"
- [ ] onExitSelectionMode() → selectionModeMessageId == null
- [ ] 选择模式中再 onEnterSelectionMode("msg-456") → 切换到 "msg-456"

**状态转换**：
- [ ] 选择模式中 → 新消息到达（messages list 变化）→ selectionModeMessageId 自动清 null
- [ ] 选择模式中 → session status 变为 "running"（agent 开始回复）→ 不影响选择模式（仅新消息触发退出）
- [ ] messageMenuTarget 和 selectionModeMessageId 互斥：进入选择模式 → messageMenuTarget 自动清 null

### 5.4 Long-press 手势 — Unit Tests

- [ ] 长按 AgentMessage → onMessageLongPress 被调用
- [ ] 长按 UserMessage → onMessageLongPress 被调用
- [ ] 长按 ToolCall → onMessageLongPress 被调用
- [ ] 长按 StatusChange → onMessageLongPress 不被调用（无可用操作）
- [ ] 短按（非长按）→ onMessageLongPress 不被触发
- [ ] 已在选择模式 → 长按另一条消息 → 退出当前选择，对新消息弹 ActionSheet

### 5.5 Haptic & Feedback — Unit Tests

- [ ] 长按触发时 → HapticFeedbackType.LongPress 被请求一次
- [ ] 复制成功后 → Snackbar "已复制到剪贴板" 显示
- [ ] 短按 → 不触发 haptic

### 5.6 Integration Tests (book CLI)

- [ ] **Agent 消息复制**：book session 回复一段文字 → 长按 agent 消息 → ActionSheet 弹出（显示 "复制消息" / "选择文本"）→ 点击 "复制消息" → 粘贴验证内容一致
- [ ] **User 消息复制**：发送一条用户消息 → 长按 → 复制 → 粘贴验证内容一致
- [ ] **ToolCall 复制**：book session 调用 Read tool → 长按 ToolCall 卡片 → 复制 → 验证包含 tool name
- [ ] **StatusChange 不可操作**：长按状态气泡（如 "运行中"）→ 无任何响应
- [ ] **文本选择模式**：长按 agent 消息 → "选择文本" → 消息进入选择模式（背景高亮）→ 长按文本弹出系统选择手柄 → 选择部分文字 → 系统 "复制" → 粘贴验证为选中文本
- [ ] **退出选择模式**：进入选择模式 → 点击消息外空白区域 → 选择模式退出（背景恢复）
- [ ] **返回键退出选择**：进入选择模式 → 按返回键 → 退出选择模式，不返回上一页
- [ ] **流式消息不可操作**：book session 正在流式回复 → 长按流式中的 agent 消息 → 无响应（isStreaming=true）
- [ ] **复制含 Markdown 的消息**：agent 回复含 `**bold**` 和 `` `code` `` 的内容 → 复制 → 粘贴验证为原始 Markdown 源码
- [ ] **超长消息复制**：agent 回复超长文本（2000+ 字符）→ 复制 → 粘贴验证完整无截断
