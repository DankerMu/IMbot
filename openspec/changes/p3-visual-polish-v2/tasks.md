# Tasks: UI 美化第二轮（Visual Polish V2）

## 1. 消息气泡重设计

### 1.1 助手消息去气泡化
- [ ] `MessageBubble.kt`：助手消息 background 从 `surfaceVariant` 改为 `Color.Transparent`
- [ ] 移除助手消息的 shape/圆角（无背景则无需圆角）
- [ ] 保留左侧 provider 头像（36dp 圆形），与消息内容间距 12dp
- [ ] 消息文字颜色：亮色 `Color(0xFF1F2937)` / 暗色 `Color(0xFFF3F4F6)`
- [ ] 时间戳：`labelSmall` 11sp，`onSurfaceVariant` 颜色，在消息内容下方 4dp

### 1.2 用户消息深色反转气泡
- [ ] 亮色模式气泡背景：`Color(0xFF1F2937)` (Gray-800)
- [ ] 亮色模式文字颜色：`Color(0xFFFFFFFF)` (白)
- [ ] 暗色模式气泡背景：`Color(0xFFE5E7EB)` (Gray-200)
- [ ] 暗色模式文字颜色：`Color(0xFF1F2937)` (Gray-800)
- [ ] 圆角：topStart=16dp, topEnd=16dp, bottomStart=16dp, bottomEnd=4dp（右下角小圆角，聊天尾巴感）
- [ ] 内边距：horizontal=14dp, vertical=10dp
- [ ] 最大宽度：300dp（或屏幕宽度 80%）
- [ ] 右对齐（`Alignment.End`）
- [ ] 时间戳在气泡外右下角，2dp top padding

### 1.3 消息间距系统
- [ ] 不同发送者间消息组间距：24dp（从当前 12dp 翻倍）
- [ ] 同一发送者连续消息间距：8dp
- [ ] LazyColumn contentPadding：vertical=24dp
- [ ] 水平 padding：16dp
- [ ] 判断逻辑：比较相邻消息的 kind（User/Agent/ToolCall/Status），相同 kind 用 8dp，不同用 24dp

## 2. 输入栏重设计

### 2.1 毛玻璃容器
- [ ] 背景：`surface.copy(alpha = 0.85f)`
- [ ] API 31+ 添加 `Modifier.blur(radius = 20.dp)`
- [ ] API 30 以下 fallback：`surface.copy(alpha = 0.95f)`（接近不透明）
- [ ] 上方 0.5dp 分隔线：`outlineVariant` 颜色

### 2.2 Pill 输入框
- [ ] 替代 OutlinedTextField 为 BasicTextField
- [ ] 形状：`RoundedCornerShape(999.dp)` (pill)
- [ ] 背景：`surfaceVariant.copy(alpha = 0.5f)`
- [ ] 无边框（移除所有 border 相关参数）
- [ ] 内边距：horizontal=16dp, vertical=10dp
- [ ] 占位文字：`onSurfaceVariant.copy(alpha = 0.5f)`, bodyMedium
- [ ] 行数：1-4 行自适应（minLines=1, maxLines=4）
- [ ] 保留 IME action Send 键盘行为
- [ ] 保留 CommandChip 上方 slot

### 2.3 iMessage 风格发送按钮
- [ ] 大小：36dp 圆形
- [ ] 可发送时背景：`primary` (品牌蓝)
- [ ] 不可发送时背景：`onSurfaceVariant.copy(alpha = 0.2f)`
- [ ] 图标：`Icons.Default.ArrowUpward`, 18dp, 白色
- [ ] 按压动画：`scale(0.92f)` + spring(dampingRatio=0.6, stiffness=400)
- [ ] 位置：输入框右侧 8dp，垂直底部对齐

## 3. 代码块增强

### 3.1 语言标签 Badge
- [ ] 代码块头部栏：`surfaceVariant`（亮色）/ `Color(0xFF2C2C2E)`（暗色）
- [ ] 圆角：top=8dp, bottom=0
- [ ] 内边距：horizontal=12dp, vertical=8dp
- [ ] 左侧语言名：`labelSmall` 11sp, `onSurfaceVariant`, 小写
- [ ] 右侧复制按钮：`ContentCopy` 图标 14dp, `onSurfaceVariant`
- [ ] 复制反馈：图标变 `Check` + 文字变"已复制"，2s 后恢复（使用 `LaunchedEffect` + delay）

### 3.2 大代码块折叠
- [ ] 阈值：>20 行默认折叠
- [ ] 折叠状态显示前 10 行
- [ ] 底部渐变遮罩：从 `transparent` 到 `surface`，高度 40dp
- [ ] 折叠/展开按钮：TextButton, "展开 (N 行)" / "收起", labelSmall, primary
- [ ] 居中于渐变遮罩上方
- [ ] 动画：maxHeight transition 300ms easeInOut

### 3.3 终端代码块样式
- [ ] language in `setOf("bash", "shell", "sh", "zsh", "terminal")` 时特殊处理
- [ ] 头部背景：`Color(0xFF0A0A0A)`
- [ ] 语言标签颜色：`Color(0xFF34C759)` green
- [ ] 代码区域背景：`Color(0xFF0A0A0A)`
- [ ] 代码文字颜色：`Color(0xFFE4E4E7)` zinc-200
- [ ] 亮色/暗色模式下保持一致

## 4. 状态气泡极简化

### 4.1 极简内联样式
- [ ] 移除 pill 背景色（`SurfaceTertiary` → `Color.Transparent`）
- [ ] 字号：11sp (`Caption2`)
- [ ] 颜色：`onSurfaceVariant.copy(alpha = 0.6f)`
- [ ] 左侧 6dp 语义色圆点（running=green, idle=blue, completed=green, failed=red, cancelled=gray）
- [ ] 圆点与文字间距 4dp
- [ ] 居中排列
- [ ] 上下间距各 4dp

### 4.2 连续状态合并
- [ ] 在渲染消息列表前过滤连续相同状态的 StatusChange
- [ ] 规则：连续 N 个相同 status 的 StatusChange，只保留最后一个
- [ ] 实现位置：`SessionDetailScreen.kt` 的消息预处理逻辑中

## 5. 滚动到底部 FAB

### 5.1 缩小重设计
- [ ] 大小：36dp（从当前 56dp 缩小）
- [ ] 形状：圆形
- [ ] 背景：`surface.copy(alpha = 0.9f)`
- [ ] 边框：`outlineVariant` 0.5dp
- [ ] 图标：`KeyboardArrowDown` 18dp, `onSurfaceVariant`
- [ ] 阴影：2dp elevation
- [ ] 位置：右下角，距底部 80dp（输入栏上方留空）
- [ ] 出现/消失动画：`scale + fadeIn/fadeOut`, 150ms

### 5.2 未读计数 Badge
- [ ] 右上角偏移 `(-4.dp, -4.dp)`
- [ ] 16dp 圆形，`primary` 背景
- [ ] 白色数字 10sp
- [ ] 只在 `newMsgCount > 0` 时显示

## 6. 色值新增

### 6.1 Color.kt 新增
- [ ] `UserBubbleLight = Color(0xFF1F2937)`
- [ ] `UserBubbleLightText = Color(0xFFFFFFFF)`
- [ ] `UserBubbleDark = Color(0xFFE5E7EB)`
- [ ] `UserBubbleDarkText = Color(0xFF1F2937)`
- [ ] `CodeBlockHeaderBg = Color(0xFFF8FAFB)` / Dark `Color(0xFF2C2C2E)`
- [ ] `CodeBlockBorder = Color(0xFFE5E7EB)` / Dark `Color(0x1AFFFFFF)`
- [ ] `TerminalBg = Color(0xFF0A0A0A)`
- [ ] `TerminalText = Color(0xFFE4E4E7)`
- [ ] `TerminalGreen = Color(0xFF34C759)`

## 7. 测试

### 7.1 Color Token — Unit Tests

**用户气泡**：
- [ ] Light: userBubbleBg == Color(0xFF1F2937), userBubbleText == Color(0xFFFFFFFF)
- [ ] Dark: userBubbleBg == Color(0xFFE5E7EB), userBubbleText == Color(0xFF1F2937)

**助手气泡**：
- [ ] Light: assistantBg == Color.Transparent
- [ ] Dark: assistantBg == Color.Transparent

**代码块**：
- [ ] Light: codeHeaderBg == Color(0xFFF8FAFB)
- [ ] Dark: codeHeaderBg == Color(0xFF2C2C2E)
- [ ] Terminal: bg == Color(0xFF0A0A0A) 两种模式下不变

### 7.2 间距 — Unit Tests

- [ ] MESSAGE_GROUP_SPACING == 24.dp
- [ ] MESSAGE_WITHIN_GROUP_SPACING == 8.dp
- [ ] 相邻 AgentMessage 使用 8dp 间距
- [ ] AgentMessage → UserMessage 使用 24dp 间距

### 7.3 代码块折叠 — Unit Tests

- [ ] 10 行代码 → 不折叠
- [ ] 25 行代码 → 默认折叠，显示 10 行
- [ ] 折叠状态显示 "展开 (25 行)"
- [ ] 点击展开后显示全部 25 行
- [ ] 展开后显示 "收起"

### 7.4 状态合并 — Unit Tests

- [ ] `[running, running, running]` → 过滤后 `[running]`（保留最后一个）
- [ ] `[running, idle, running]` → 不合并（状态不同），保留全部 3 个
- [ ] `[AgentMessage, running, running, AgentMessage]` → `[AgentMessage, running, AgentMessage]`
- [ ] 空列表 → 空列表

### 7.5 功能回归

- [ ] 消息发送/接收流式渲染正常
- [ ] Markdown 渲染（粗体、链接、列表）不受影响
- [ ] 代码块语法高亮正常
- [ ] 输入栏 IME action Send 正常
- [ ] CommandChip 功能不受影响
- [ ] 长按复制/选择仍正常
- [ ] 暗色模式所有颜色正确
- [ ] 滚动到底部 FAB 功能正常
- [ ] 自动滚动行为不受影响
