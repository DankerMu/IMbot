# Tasks: 长按复制/选择菜单稳定性修复

## 1. 扩展消息可操作性

### 1.1 hasActions() 覆盖全类型
- [ ] `InteractiveToolCall` → `true`（可复制 question）
- [ ] `StatusChange` → `item.message?.isNotBlank() == true || item.description?.isNotBlank() == true`
- [ ] `ToolCall` → `true`（移除 `toolName.isNotBlank()` 前置条件）

### 1.2 availableActions() 新增分支
- [ ] `InteractiveToolCall` → `listOf(CopyMessage(copyableText(item)))`
- [ ] `StatusChange` → `listOf(CopyMessage(copyableText(item)))`

### 1.3 copyableText() 新增分支
- [ ] `InteractiveToolCall` → `item.question.takeIf(String::isNotBlank)`（注意适配 parseAskUserQuestionV2 后可能字段名变化）
- [ ] `StatusChange` → `(item.description ?: item.message)?.takeIf(String::isNotBlank)`
- [ ] `ToolCall` → 移除 `toolName.isNotBlank` 前置条件，拼接可用字段

## 2. ModalBottomSheet 修复

### 2.1 skipPartiallyExpanded
- [ ] `MessageActionSheet` 中 `ModalBottomSheet` 添加 `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`

## 3. 手势稳定性

### 3.1 ToolCallCard 长按注册
- [ ] 移除 `canLongPress` 对 `hasActions` 的前置判断，始终注册 `onLongClick`（在 `onLongClick` 内部再判断是否有 actions）

### 3.2 触觉反馈全覆盖
- [ ] 验证 `MessageBubble.kt` 中 AgentMessage/UserMessage 长按路径有 `HapticFeedbackType.LongPress`
- [ ] 验证 `ToolCallCard.kt` 中已有 haptic（当前已有）
- [ ] 验证 `InteractiveToolCard.kt` — 如果增加长按，也需要 haptic

## 4. 测试

### 4.1 Unit Tests — hasActions

- [ ] `hasActions(AgentMessage(isStreaming=false))` → `true`
- [ ] `hasActions(AgentMessage(isStreaming=true))` → `false`
- [ ] `hasActions(UserMessage)` → `true`
- [ ] `hasActions(ToolCall(toolName="bash"))` → `true`
- [ ] `hasActions(ToolCall(toolName=""))` → `true`（改后）
- [ ] `hasActions(InteractiveToolCall)` → `true`（改后）
- [ ] `hasActions(StatusChange(message="运行中"))` → `true`（改后）
- [ ] `hasActions(StatusChange(message=null, description=null))` → `false`

### 4.2 Unit Tests — copyableText

- [ ] `copyableText(InteractiveToolCall(question="Q?"))` → `"Q?"`
- [ ] `copyableText(StatusChange(description="session started"))` → `"session started"`
- [ ] `copyableText(ToolCall(toolName="", result="output"))` → 包含 "Output: output"

### 4.3 Integration Tests

- [ ] AgentMessage 长按 → 弹出 "复制消息" + "选择文本" → 点击 "复制消息" → 剪贴板包含消息内容
- [ ] UserMessage 长按 → 弹出菜单 → 功能正常
- [ ] ToolCall 长按 → 弹出 "复制消息" → 剪贴板包含 tool summary
- [ ] InteractiveToolCall 长按 → 弹出 "复制消息" → 剪贴板包含 question
- [ ] StatusChange 长按 → 弹出 "复制消息" → 剪贴板包含 description/message
- [ ] ModalBottomSheet 弹出后内容完全可见（无半展开截断）
- [ ] 长按时有触觉反馈震动
