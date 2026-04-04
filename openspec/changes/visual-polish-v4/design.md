## Context

IMbot Android 前端在 `p3-visual-redesign` 中建立了 Apple 美学基础（spring 动画、shadow token、asymmetric bubble corners）。当前 Gemini 美学审查指出：色彩层级不够清晰、代码块与聊天背景融为一体、间距存在非 8 倍数值、表格边框过粗。

**本次改动仅涉及 Android 前端 8 个 Kotlin 文件，纯视觉/样式改动，零逻辑变更。**

所有源文件根目录：`packages/android/app/src/main/kotlin/com/imbot/android/`

## Goals / Non-Goals

**Goals:**
- 品牌主色切换到靛蓝系
- 浅色模式文本、背景、surface 色彩层级拉开
- 代码块在浅色模式下统一使用深色背景（VS Code Dark 风格）
- 表格、数学公式块视觉减噪
- 所有间距值对齐 8 的倍数网格
- Agent 气泡使用微阴影替代 border

**Non-Goals:**
- 不改深色模式色值（除代码块外）
- 不改功能逻辑、导航、数据流
- 不改动画参数
- 不改 Onboarding、Settings、Workspace、NewSession 页面
- 不新增文件、不新增依赖

---

## Decision 1: 色彩调色板 — Color.kt

**文件**: `ui/theme/Color.kt`

### 精确改动表（old → new）

| 常量名 | 行号 | 当前值 | 新值 | 理由 |
|--------|------|--------|------|------|
| `BrandBlue` | 6 | `0xFF0071E3` | `0xFF4F46E5` | 靛蓝更稳重，与 ChatGPT/Claude 风格一致 |
| `BrandBlueLight` | 7 | `0xFFE3F2FF` | `0xFFEEF2FF` | 靛蓝的 tint 色 |
| `Background` | 8 | `0xFFF2F2F7` | `0xFFF5F7F9` | 偏暖灰白，让白色 surface 凸显 |
| `SurfaceSecondary` | 10 | `0xFFF9F9F9` | `0xFFF1F5F9` | 更明显的浅灰，用于输入框/tab 背景 |
| `SurfaceTertiary` | 11 | `0xFFF2F2F7` | `0xFFF5F7F9` | 与 Background 保持一致 |
| `LabelPrimary` | 13 | `0xFF000000` | `0xFF1E293B` | 近黑但不纯黑，更护眼 |
| `LabelSecondary` | 14 | `0xFF6C6C70` | `0xFF64748B` | 偏蓝灰的次级文字 |
| `LabelTertiary` | 15 | `0xFFC7C7CC` | `0xFFCBD5E1` | 偏蓝灰的三级文字 |
| `UserBubbleLight` | 20 | `0xFF1F2937` | `0xFF4F46E5` | 用户气泡使用品牌色（iMessage 风格） |

### 不改动的常量

以下常量保持不变，Codex **不要碰**：
- `SurfaceLight` (0xFFFFFFFF)
- `SeparatorLight` (0x33000000)
- `SuccessColor`, `WarningColor`, `DestructiveColor`
- `UserBubbleLightText` (0xFFFFFFFF) — 靛蓝背景上白字仍然可读
- `CodeBlockHeaderBg`, `CodeBlockBorder` — 这两个常量不再被使用（见 Decision 3），但保留不删
- 所有 `*Dark` 常量（深色模式不变）
- 所有 `Provider*` 常量
- 所有 `Status*` 常量
- `assistantBubbleBackground()`, `assistantMessageTextColor()`, `userBubbleBackground()`, `userBubbleTextColor()` 函数 — 不改

### 下游影响

`BrandBlue` 变更会通过 `IMbotTheme.kt` 的 `StaticLightColorScheme` 级联到所有使用 `MaterialTheme.colorScheme.primary` 的组件（FAB、发送按钮、链接色、选中 tab 等），这是预期行为，无需额外改动。

---

## Decision 2: 圆角微调 — Shape.kt

**文件**: `ui/theme/Shape.kt`

| 常量 | 当前值 | 新值 |
|------|--------|------|
| `codeBlock` | `RoundedCornerShape(8.dp)` | `RoundedCornerShape(12.dp)` |
| `codeBlockHeader` | `RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)` | `RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)` |

其余 shape 全部不动。

---

## Decision 3: 代码块深色主题 — CodeBlock.kt

**文件**: `ui/components/CodeBlock.kt`

**核心决策**：浅色模式下的非 terminal 代码块改用深色背景 + 深色语法高亮。

### 3a. codeBlockPalette 函数重写

当前逻辑（行 400-424）：
```kotlin
@Composable
private fun codeBlockPalette(
    useDarkTheme: Boolean,
    isTerminal: Boolean,
    codeTheme: CodeTheme,
): CodeBlockPalette =
    if (isTerminal) {
        // terminal palette ...
    } else {
        CodeBlockPalette(
            headerBackground = codeBlockHeaderBackground(useDarkTheme),
            bodyBackground = codeTheme.background,
            border = codeBlockBorderColor(useDarkTheme),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionColor = MaterialTheme.colorScheme.onSurfaceVariant,
            codeTextColor = MaterialTheme.colorScheme.onSurface,
        )
    }
```

**新逻辑**：非 terminal 分支始终返回深色 palette，不区分 `useDarkTheme`：

```kotlin
@Composable
private fun codeBlockPalette(
    useDarkTheme: Boolean,
    isTerminal: Boolean,
    codeTheme: CodeTheme,
): CodeBlockPalette =
    if (isTerminal) {
        CodeBlockPalette(
            headerBackground = TerminalBg,
            bodyBackground = TerminalBg,
            border = codeBlockBorderColor(useDarkTheme),
            labelColor = TerminalGreen,
            actionColor = TerminalText,
            codeTextColor = TerminalText,
        )
    } else {
        CodeBlockPalette(
            headerBackground = Color(0xFF2D2D2D),
            bodyBackground = Color(0xFF1E1E1E),
            border = Color(0xFF3E3E42),
            labelColor = Color(0xFF858585),
            actionColor = Color(0xFFCCCCCC),
            codeTextColor = Color(0xFFD4D4D4),
        )
    }
```

**色值来源**：VS Code Dark+ 主题。

### 3b. 语法高亮主题

当前（约行 89）：
```kotlin
val codeTheme = LocalCodeTheme.current
```

**改为**：非 terminal 时始终使用 `CodeTheme.Dark`：
```kotlin
val isTerminal = remember(language) { isTerminalCodeLanguage(language) }
val codeTheme = if (isTerminal) LocalCodeTheme.current else CodeTheme.Dark
```

这确保浅色模式下代码块内的关键字、字符串等使用深色背景上的浅色语法高亮色（`CodeTheme.Dark` 定义在 `CodeTheme.kt` 行 47-59）。

### 3c. import 变更

新增 `import androidx.compose.ui.graphics.Color`（如果尚未存在）。`codeBlockHeaderBackground` 和 `codeBlockBorderColor` 的 import 可以保留（terminal 分支仍通过 `codeBlockBorderColor` 使用）。

---

## Decision 4: 过滤标签精简 — HomeScreen.kt

**文件**: `ui/home/HomeScreen.kt`

### 4a. 移除未选中态 border

当前（行 260-266）：
```kotlin
border =
    if (selected) {
        null
    } else {
        androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        )
    },
```

**改为**：
```kotlin
border = null,
```

`BorderStroke` 相关 import 不需要删除（可能其他地方用到）。

### 4b. Padding 对齐 8 倍数

当前（行 274）：
```kotlin
modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
```

**改为**：
```kotlin
modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
```

---

## Decision 5: Agent 气泡微阴影 — MessageBubble.kt

**文件**: `ui/detail/MessageBubble.kt`

### 5a. AgentMessageBubble Surface 改动

当前（行 196-236）：
```kotlin
val outlineVariant = MaterialTheme.colorScheme.outlineVariant
val bubbleBorder =
    remember(outlineVariant) {
        BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.45f))
    }
// ...
Surface(
    modifier = bubbleModifier,
    color = selectedBubbleColor(...),
    shape = componentShapes.assistantMessageBubble,
    border = bubbleBorder,
)
```

**改为**：浅色模式用 shadow，深色模式保留 border：

```kotlin
val outlineVariant = MaterialTheme.colorScheme.outlineVariant
val bubbleBorder =
    remember(outlineVariant) {
        BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.45f))
    }
// ...
Surface(
    modifier = bubbleModifier,
    color = selectedBubbleColor(...),
    shape = componentShapes.assistantMessageBubble,
    border = if (useDarkTheme) bubbleBorder else null,
    shadowElevation = if (useDarkTheme) 0.dp else 1.dp,
)
```

`useDarkTheme` 已在函数开头可用（行 187 `val useDarkTheme = LocalUseDarkTheme.current`）。

**注意**：`bubbleBorder` 的 `remember` 和变量声明保留不删，深色模式仍使用。

---

## Decision 6: 表格色值 — MarkdownText.kt

**文件**: `ui/detail/MarkdownText.kt`

### 6a. MarkdownTable 色值

当前（行 358-359）：
```kotlin
val borderColor = MaterialTheme.colorScheme.outlineVariant
val headerBackground = MaterialTheme.colorScheme.surfaceVariant
```

**改为**：浅色模式使用精确色值，深色模式保持原样：

```kotlin
val useDarkTheme = LocalUseDarkTheme.current
val borderColor = if (useDarkTheme) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
val headerBackground = if (useDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC)
```

需要新增 import：`import androidx.compose.ui.graphics.Color`（如果不存在）。`LocalUseDarkTheme` 已在文件中 import。

### 6b. 斑马纹行背景

当前（行 398-403，MarkdownTable composable 内 rows.forEachIndexed 的 backgroundColor）：
```kotlin
backgroundColor =
    if (isStripedTableRow(rowIndex)) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    },
```

**改为**：
```kotlin
backgroundColor =
    if (isStripedTableRow(rowIndex)) {
        if (useDarkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color(0xFFF8FAFC)
    } else {
        MaterialTheme.colorScheme.surface
    },
```

### 6c. 单元格 border

当前 MarkdownTableRow 内（行 438）：
```kotlin
.border(width = 1.dp, color = borderColor)
```

**改为**：浅色模式下单元格只画底边（水平分割线），不画全框 border。深色模式保持四边 border。

```kotlin
.then(
    if (useDarkTheme) {
        Modifier.border(width = 1.dp, color = borderColor)
    } else {
        Modifier.drawBehind {
            drawLine(
                color = borderColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
)
```

**注意**：`borderColor` 需要从 `MarkdownTable` 传入 `MarkdownTableRow`。当前 `MarkdownTableRow` 已接收 `borderColor: Color` 参数。`useDarkTheme` 需要新增为参数（因为 `MarkdownTableRow` 是 private 函数，加参数无外部影响）。

### 6d. MarkdownTableRow 签名变更

当前：
```kotlin
@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    columnCount: Int,
    alignments: List<MarkdownTableAlignment>,
    backgroundColor: Color,
    borderColor: Color,
    textStyle: TextStyle,
)
```

**改为**：
```kotlin
@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    columnCount: Int,
    alignments: List<MarkdownTableAlignment>,
    backgroundColor: Color,
    borderColor: Color,
    textStyle: TextStyle,
    useDarkTheme: Boolean,
)
```

所有调用处需要传入 `useDarkTheme = useDarkTheme`。调用处在 `MarkdownTable` composable 内（行 381 和 393），共 2 处。

### 6e. 新增 import

MarkdownText.kt 需要新增：
```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
```

`Color` import 已存在（行 37）。

---

## Decision 7: 数学公式块 — MarkdownKatex.kt

**文件**: `ui/detail/MarkdownKatex.kt`

### 7a. MarkdownKatexMathBlock surface

当前（行 98-101）：
```kotlin
Surface(
    color = colors.surfaceVariant.copy(alpha = 0.38f),
    shape = RoundedCornerShape(18.dp),
    modifier = modifier.fillMaxWidth(),
)
```

**改为**：
```kotlin
val useDarkTheme = LocalUseDarkTheme.current
// ...
Surface(
    color = if (useDarkTheme) colors.surfaceVariant.copy(alpha = 0.38f) else Color(0xFFF8FAFC),
    shape = RoundedCornerShape(12.dp),
    modifier = modifier.fillMaxWidth(),
)
```

`val useDarkTheme = LocalUseDarkTheme.current` 放在 `val colors = MaterialTheme.colorScheme` 的下一行。

需要新增 import：`import androidx.compose.ui.graphics.Color`（如果不存在）。`LocalUseDarkTheme` 已在文件中 import（行 40）。

---

## Decision 8: 输入框背景 — InputBar.kt

**文件**: `ui/detail/InputBar.kt`

### 8a. PillTextField 背景 alpha

当前（行 178）：
```kotlin
.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
```

**改为**：
```kotlin
.background(MaterialTheme.colorScheme.surfaceVariant)
```

移除 `.copy(alpha = 0.5f)`，使用完整色值。浅色模式下 `surfaceVariant` 将是 `#F1F5F9`（由 Color.kt 变更级联）。

---

## Risks / Trade-offs

| 风险 | 缓解方案 |
|------|---------|
| 代码块深色背景在靛蓝用户气泡内可能产生深色套深色 | 实际场景中用户消息几乎不包含代码块（代码块出现在 Agent 消息中），风险极低 |
| `BrandBlue` 变更级联面广（所有 primary 组件） | 这是预期行为——靛蓝色的对比度 ratio 与原蓝色接近（均 >4.5:1 on white），WCAG AA 合规 |
| `LabelPrimary` 从纯黑改为近黑 | 对比度从 21:1 降至 ~14.5:1，仍远超 WCAG AAA 要求（7:1），无可访问性问题 |
| 表格 drawBehind 替代 border 增加渲染复杂度 | 表格是低频组件，性能影响可忽略 |
| `CodeBlockHeaderBg` 和 `CodeBlockBorder` 常量变为 dead code | 保留不删，避免 diff 膨胀，未来如需恢复浅色代码块可复用 |
