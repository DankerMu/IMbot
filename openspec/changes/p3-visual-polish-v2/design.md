# Design: UI 美化第二轮（Visual Polish V2）

## Design Reference: CodePilot

以下设计规范直接从 CodePilot 源码提取并适配到 Android Compose。每个数值都有精确来源。

---

## 1. 消息气泡重设计

### 1.1 助手消息 — 去气泡化

**CodePilot 做法**：助手消息没有背景色，直接在页面底色上渲染文字内容。视觉上"消息即内容"。

**IMbot 实现**：

```kotlin
// ===== 助手消息 =====
// 移除灰色气泡背景，改为透明
// 保留左侧 provider 头像标识

// 亮色模式：
val assistantBubbleBackground = Color.Transparent        // ← 关键：无背景
val assistantTextColor = Color(0xFF1F2937)               // Gray-800，主文字
val assistantSecondaryTextColor = Color(0xFF6B7280)       // Gray-500，时间戳等

// 暗色模式：
val assistantBubbleBackgroundDark = Color.Transparent
val assistantTextColorDark = Color(0xFFF3F4F6)           // Gray-100
val assistantSecondaryTextColorDark = Color(0xFF9CA3AF)   // Gray-400
```

**布局变更**：

```
// 改前（有背景气泡）：
┌─────────────────────────────┐
│  BK  │  灰色气泡背景         │
│      │  消息文字内容          │
│      │  时间戳               │
└─────────────────────────────┘

// 改后（无背景，干净）：
  BK   消息文字内容
       时间戳（onSurfaceVariant, 11sp）
```

**Compose 代码要点**：

```kotlin
@Composable
fun AssistantMessageBubble(content: String, timestamp: String, ...) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),  // 水平 padding 16dp
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Provider 头像（保留，36dp 圆形）
        ProviderAvatar(provider = provider, size = 36.dp)

        // 消息内容（无背景）
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Markdown 渲染内容
            MarkdownText(
                content = content,
                style = MaterialTheme.typography.bodyMedium,  // 15sp
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 时间戳
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,  // 11sp
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

### 1.2 用户消息 — 深色反转气泡

**CodePilot 做法**：用户消息在暗色模式下用浅灰背景 + 深色文字（反转配色）。

**IMbot 实现**：

```kotlin
// ===== 用户消息 =====
// 亮色模式：深色气泡 + 白色文字（高对比，不再用蓝色）
val userBubbleBackground = Color(0xFF1F2937)             // Gray-800
val userTextColor = Color(0xFFFFFFFF)                     // 纯白

// 暗色模式：浅灰气泡 + 深色文字（CodePilot 的 oklch(0.90, 0.003, 106)）
val userBubbleBackgroundDark = Color(0xFFE5E7EB)         // Gray-200
val userTextColorDark = Color(0xFF1F2937)                 // Gray-800
```

**布局**：

```kotlin
@Composable
fun UserMessageBubble(text: String, timestamp: String, ...) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,  // 右对齐
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)  // 最大宽度约屏幕 80%
                .background(
                    color = userBubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp,  // 右下角小圆角（聊天气泡尾巴感）
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,  // 15sp
                color = userTextColor,
            )
        }
        // 时间戳在气泡外右下角
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, end = 4.dp),
        )
    }
}
```

### 1.3 消息间距系统

```kotlin
// CodePilot: gap-6 (24px) between message groups, gap-2 (8px) within group

// 消息组间距（不同发送者之间）
val MESSAGE_GROUP_SPACING = 24.dp

// 同一发送者连续消息间距
val MESSAGE_WITHIN_GROUP_SPACING = 8.dp

// 消息列表水平 padding
val MESSAGE_HORIZONTAL_PADDING = 16.dp

// 消息列表垂直 padding（顶/底）
val MESSAGE_VERTICAL_PADDING = 24.dp
```

**LazyColumn 应用**：
```kotlin
LazyColumn(
    contentPadding = PaddingValues(vertical = MESSAGE_VERTICAL_PADDING),
) {
    itemsIndexed(messages) { index, item ->
        val prevItem = messages.getOrNull(index - 1)
        val isSameSender = prevItem != null && sameGroup(prevItem, item)
        val spacing = if (isSameSender) MESSAGE_WITHIN_GROUP_SPACING else MESSAGE_GROUP_SPACING

        Spacer(modifier = Modifier.height(if (index == 0) 0.dp else spacing))

        // 渲染消息
        MessageItemView(item, ...)
    }
}
```

## 2. 输入栏重设计

### 2.1 毛玻璃效果

**CodePilot 做法**：`bg-background/80 backdrop-blur-lg`（80% 透明度 + 大模糊半径）

```kotlin
/**
 * 输入栏容器。
 *
 * 视觉规范：
 * - 背景：Surface 颜色 80% 不透明度
 * - 模糊效果：Modifier.blur(radius = 20.dp)（需要 API 31+，低版本 fallback 到不透明背景）
 * - 上方 0.5dp 分隔线（Separator 颜色）
 * - 内边距：horizontal=16dp, top=8dp, bottom=4dp（留空间给系统导航栏 inset）
 * - WindowInsets.ime 响应键盘弹出
 */
@Composable
fun InputBarContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(radius = 20.dp)
                } else {
                    Modifier
                }
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(
            modifier = Modifier.padding(
                start = 16.dp, end = 16.dp,
                top = 8.dp, bottom = 4.dp,
            ),
        ) {
            content()
        }
    }
}
```

### 2.2 输入框 Pill 样式

```kotlin
/**
 * 输入框。
 *
 * 视觉规范：
 * - 形状：Pill（RadiusFull = 999.dp）
 * - 背景：surfaceVariant alpha 0.5
 * - 边框：无（去除 OutlinedTextField 样式）
 * - 内边距：horizontal=16dp, vertical=10dp
 * - 占位文字：onSurfaceVariant alpha 0.5, bodyMedium
 * - 文字：onSurface, bodyMedium
 * - 行数：1-4 行自适应
 * - 使用 BasicTextField 替代 OutlinedTextField 以完全控制样式
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        maxLines = 4,
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                innerTextField()
            }
        },
    )
}
```

### 2.3 发送按钮 — iMessage 风格

```kotlin
/**
 * 圆形发送按钮。
 *
 * 视觉规范：
 * - 大小：36dp 圆形
 * - 背景：可发送时 = primary (品牌蓝), 不可发送时 = onSurfaceVariant alpha 0.2
 * - 图标：向上箭头（Icons.AutoMirrored.Filled.Send 或 Icons.Default.ArrowUpward）
 * - 图标颜色：白色
 * - 图标大小：18dp
 * - 按压动画：scale(0.92f) + spring
 * - 位置：输入框右侧，垂直居中于输入框底部
 */
@Composable
fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "send-scale",
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .background(
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                },
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = "发送",
            modifier = Modifier.size(18.dp),
            tint = Color.White,
        )
    }
}
```

## 3. 代码块增强

### 3.1 语言标签 Badge

```kotlin
/**
 * 代码块头部。
 *
 * 视觉规范：
 * - 背景：surfaceVariant（亮色）/ Color(0xFF2C2C2E)（暗色）
 * - 圆角：顶部 8dp，底部 0（与代码区域连接）
 * - 内边距：horizontal=12dp, vertical=8dp
 * - 左侧：语言标签
 *   - 文字：labelSmall (11sp), onSurfaceVariant
 *   - 前景：小写语言名（"kotlin", "typescript", "bash"）
 * - 右侧：复制按钮
 *   - 图标：ContentCopy (14dp)
 *   - 点击后变为 Check 图标 + "已复制" 文字，2s 后恢复
 *   - 颜色：onSurfaceVariant
 */
```

### 3.2 代码块折叠

```kotlin
/**
 * 大代码块折叠。
 *
 * 规则：
 * - 超过 20 行代码时默认折叠
 * - 折叠状态显示前 10 行 + 底部渐变遮罩
 * - 渐变遮罩：从透明到 surface 颜色，高度 40dp
 * - 折叠/展开按钮：居中于渐变上方
 *   - 文字："展开 (N 行)" / "收起"
 *   - 样式：TextButton, labelSmall, primary
 * - 展开/折叠动画：maxHeight transition 300ms easeInOut
 */
```

### 3.3 终端代码块特殊样式

```kotlin
/**
 * 如果 language 是 "bash", "shell", "sh", "zsh", "terminal"：
 *
 * - 头部背景：Color(0xFF0A0A0A)
 * - 语言标签颜色：Color(0xFF34C759) green（而非默认 onSurfaceVariant）
 * - 代码区域背景：Color(0xFF0A0A0A)
 * - 代码文字颜色：Color(0xFFE4E4E7) zinc-200
 * - 亮色/暗色模式下保持一致（终端始终深色）
 */
```

## 4. 状态气泡极简化

### 4.1 当前问题

状态变更（运行中/空闲/已完成）以带颜色背景的独立 pill 渲染，每个状态一行，占据大量垂直空间，视觉噪音高。

### 4.2 改进方案

```kotlin
/**
 * 状态指示器 — 极简内联样式。
 *
 * 视觉规范：
 * - 居中文本，无背景色（去除 pill 背景）
 * - 字号：11sp (Caption2)
 * - 颜色：onSurfaceVariant alpha 0.6（极低对比度，不干扰主内容）
 * - 图标：6dp 圆点（语义色），在文字左侧，间距 4dp
 * - 连续相同状态合并：如连续 3 个 "运行中"，只显示 1 个
 * - 上下间距：8dp（与消息间距独立）
 *
 * 布局：
 *   ●  运行中      （● = 6dp 绿色圆点，文字 = 11sp 灰色）
 */
@Composable
fun StatusIndicatorMinimal(
    status: String,
    statusColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态圆点
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(statusColor, CircleShape),
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 状态文字
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
```

### 4.3 连续状态合并逻辑

```kotlin
/**
 * 在 SessionDetailScreen 渲染消息列表时，过滤掉连续重复的状态变更。
 *
 * 规则：如果当前 StatusChange 与前一个 StatusChange 的 status 相同，跳过渲染。
 * 仅保留每组连续同状态的最后一个（确保最终状态可见）。
 */
fun deduplicateStatusChanges(messages: List<MessageItem>): List<MessageItem> {
    return messages.filterIndexed { index, item ->
        if (item !is MessageItem.StatusChange) return@filterIndexed true
        val next = messages.getOrNull(index + 1)
        // 如果下一条也是相同状态，跳过当前
        !(next is MessageItem.StatusChange && next.status == item.status)
    }
}
```

## 5. 整体颜色修正

基于 CodePilot 分析的精确色值，补充/修正 Color.kt 中不足的部分：

```kotlin
// ===== 新增色值（来自 CodePilot）=====

// 代码块
val CodeBlockHeaderBg = Color(0xFFF8FAFB)          // 亮色：近白色
val CodeBlockHeaderBgDark = Color(0xFF2C2C2E)       // 暗色
val CodeBlockBorder = Color(0xFFE5E7EB)             // Gray-200 边框
val CodeBlockBorderDark = Color(0x1AFFFFFF)          // 白色 10% 透明度

// 终端
val TerminalBg = Color(0xFF0A0A0A)                  // 接近纯黑
val TerminalText = Color(0xFFE4E4E7)                // zinc-200
val TerminalGreen = Color(0xFF34C759)               // 命令提示符

// Diff
val DiffRemovedBg = Color(0x1AFF3B30)               // 红色 10%
val DiffRemovedFgLight = Color(0xFFCC3333)
val DiffRemovedFgDark = Color(0xFFFF6B6B)
val DiffAddedBg = Color(0x1A34C759)                 // 绿色 10%
val DiffAddedFgLight = Color(0xFF228B22)
val DiffAddedFgDark = Color(0xFF4ADE80)

// 用户气泡
val UserBubbleLight = Color(0xFF1F2937)             // Gray-800
val UserBubbleLightText = Color(0xFFFFFFFF)
val UserBubbleDark = Color(0xFFE5E7EB)              // Gray-200
val UserBubbleDarkText = Color(0xFF1F2937)           // Gray-800
```

## 6. 滚动到底部 FAB 重设计

```kotlin
/**
 * 滚动到底部按钮。
 *
 * 视觉规范（替代当前深蓝大 FAB）：
 * - 大小：36dp 圆形（缩小 40%）
 * - 背景：Surface alpha 0.9（半透明白/暗灰）
 * - 边框：outlineVariant 0.5dp
 * - 图标：KeyboardArrowDown, 18dp, onSurfaceVariant
 * - 阴影：2dp elevation
 * - 位置：右下角，距底部 80dp（输入栏上方）
 * - 未读计数 Badge（如有）：
 *   - 右上角偏移 (-4dp, -4dp)
 *   - 16dp 圆形，primary 背景，白色数字 (10sp)
 * - 出现/消失动画：scale + fade, 150ms
 */
```

## File Changes

| File | Change |
|------|--------|
| `ui/detail/MessageBubble.kt` | 助手去气泡化 + 用户深色反转 + 间距系统 |
| `ui/detail/SessionDetailScreen.kt` | 消息间距逻辑 + 状态合并 + FAB 重设计 |
| `ui/detail/InputBar.kt` | Pill 输入框 + 毛玻璃容器 + iMessage 发送按钮 |
| `ui/detail/StatusChangeBubble.kt` | 极简内联状态指示器（或直接内联到 SessionDetailScreen） |
| `ui/components/CodeBlock.kt` | 语言标签 badge + 折叠/展开 + 终端特殊样式 |
| `ui/theme/Color.kt` | 新增色值（终端、Diff、用户气泡、代码块） |

## Dependencies

- 与 `p3-tool-call-rich-display` 共享终端色值和 CodeBlock 增强
- 与 `p3-longpress-menu-fix` 独立
- 与 `p3-ask-user-question-fix` 独立

## Constraints

- 不改变导航结构或数据流
- Material 3 框架内实现（不引入第三方 UI 库）
- 所有改动必须同时适配 Light / Dark 模式
- 动画使用已有的 spring 曲线（来自 p3-visual-redesign）
- BasicTextField 替代 OutlinedTextField 时保留 IME action 和键盘行为
- blur() 效果需要 API 31+，低版本 fallback 到不透明背景
