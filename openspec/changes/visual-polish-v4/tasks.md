# Tasks: visual-polish-v4

> 所有文件路径相对于 `packages/android/app/src/main/kotlin/com/imbot/android/`。
> 每个 task 包含精确的 old→new 代码片段，按依赖顺序排列。Task 1（Color.kt）必须先完成，其余可并行。

---

## 1. Color.kt — 色彩调色板

**文件**: `ui/theme/Color.kt`

- [ ] 1.1 将 `BrandBlue` 从 `Color(0xFF0071E3)` 改为 `Color(0xFF4F46E5)`（行 6）
- [ ] 1.2 将 `BrandBlueLight` 从 `Color(0xFFE3F2FF)` 改为 `Color(0xFFEEF2FF)`（行 7）
- [ ] 1.3 将 `Background` 从 `Color(0xFFF2F2F7)` 改为 `Color(0xFFF5F7F9)`（行 8）
- [ ] 1.4 将 `SurfaceSecondary` 从 `Color(0xFFF9F9F9)` 改为 `Color(0xFFF1F5F9)`（行 10）
- [ ] 1.5 将 `SurfaceTertiary` 从 `Color(0xFFF2F2F7)` 改为 `Color(0xFFF5F7F9)`（行 11）
- [ ] 1.6 将 `LabelPrimary` 从 `Color(0xFF000000)` 改为 `Color(0xFF1E293B)`（行 13）
- [ ] 1.7 将 `LabelSecondary` 从 `Color(0xFF6C6C70)` 改为 `Color(0xFF64748B)`（行 14）
- [ ] 1.8 将 `LabelTertiary` 从 `Color(0xFFC7C7CC)` 改为 `Color(0xFFCBD5E1)`（行 15）
- [ ] 1.9 将 `UserBubbleLight` 从 `Color(0xFF1F2937)` 改为 `Color(0xFF4F46E5)`（行 20）

**验证**：编译通过，grep 确认 9 个旧色值均已替换。不要修改任何 `*Dark` 常量。

---

## 2. Shape.kt — 代码块圆角

**文件**: `ui/theme/Shape.kt`

- [ ] 2.1 将 `codeBlock` shape 从 `RoundedCornerShape(8.dp)` 改为 `RoundedCornerShape(12.dp)`（行 38）
- [ ] 2.2 将 `codeBlockHeader` shape 中的 `topStart = 8.dp, topEnd = 8.dp` 改为 `topStart = 12.dp, topEnd = 12.dp`（行 39）

**验证**：编译通过。

---

## 3. CodeBlock.kt — 深色代码块

**文件**: `ui/components/CodeBlock.kt`

- [ ] 3.1 修改 `codeTheme` 变量赋值。找到 `val codeTheme = LocalCodeTheme.current`（约行 89），在其下方添加 `val isTerminal` 的引用（已存在于约行 92），然后将 `codeTheme` 改为条件赋值：

**当前代码**:
```kotlin
val codeTheme = LocalCodeTheme.current
```

**替换为**:
```kotlin
val codeTheme = if (isTerminal) LocalCodeTheme.current else CodeTheme.Dark
```

注意：`isTerminal` 变量在约行 92 定义为 `val isTerminal = remember(language) { isTerminalCodeLanguage(language) }`，需要确保 `codeTheme` 赋值在 `isTerminal` 之后。如果 `codeTheme` 当前在 `isTerminal` 之前，需要调换顺序，把 `val isTerminal = ...` 移到 `val codeTheme = ...` 之前。

- [ ] 3.2 重写 `codeBlockPalette` 函数的非 terminal 分支。找到该函数（约行 400-424）。

**当前 else 分支**:
```kotlin
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

**替换为**:
```kotlin
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

- [ ] 3.3 确保文件顶部有 `import androidx.compose.ui.graphics.Color`。如果已存在则跳过。同时确保有 `import com.imbot.android.ui.theme.CodeTheme`（用于 `CodeTheme.Dark`）。

**验证**：编译通过。在浅色模式下，非 terminal 代码块 body 背景应为 `#1E1E1E`，header 为 `#2D2D2D`。Terminal 代码块（bash/shell/sh/zsh/terminal）不受影响，仍为 `#0A0A0A`。

---

## 4. HomeScreen.kt — 过滤标签

**文件**: `ui/home/HomeScreen.kt`

- [ ] 4.1 移除 filter tab 的 border 参数。找到 `HomeTopAppBar` 内的 `Surface` 组件（约行 251-284）。

**当前代码**:
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

**替换为**:
```kotlin
                    border = null,
```

- [ ] 4.2 修改 filter tab 文字区域的 padding。找到（约行 274）：

**当前代码**:
```kotlin
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
```

**替换为**:
```kotlin
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
```

**验证**：编译通过。未选中 tab 无边框，背景为 `surfaceVariant`（`#F1F5F9`）。

---

## 5. MessageBubble.kt — Agent 气泡微阴影

**文件**: `ui/detail/MessageBubble.kt`

- [ ] 5.1 修改 `AgentMessageBubble` 内的 `Surface` 调用。找到约行 227-236 的 Surface：

**当前代码**:
```kotlin
            Surface(
                modifier = bubbleModifier,
                color =
                    selectedBubbleColor(
                        baseColor = assistantBubbleBackground(useDarkTheme),
                        isSelectionMode = isSelectionMode,
                    ),
                shape = componentShapes.assistantMessageBubble,
                border = bubbleBorder,
            ) {
```

**替换为**:
```kotlin
            Surface(
                modifier = bubbleModifier,
                color =
                    selectedBubbleColor(
                        baseColor = assistantBubbleBackground(useDarkTheme),
                        isSelectionMode = isSelectionMode,
                    ),
                shape = componentShapes.assistantMessageBubble,
                border = if (useDarkTheme) bubbleBorder else null,
                shadowElevation = if (useDarkTheme) 0.dp else 1.dp,
            ) {
```

- [ ] 5.2 确保文件顶部有 `import androidx.compose.ui.unit.dp`（应该已存在）。

**验证**：编译通过。浅色模式 Agent 气泡无边框、有 1dp 微阴影。深色模式不变（border + 无阴影）。

---

## 6. MarkdownText.kt — 表格样式

**文件**: `ui/detail/MarkdownText.kt`

- [ ] 6.1 修改 `MarkdownTable` composable 内的色值变量。找到约行 358-359：

**当前代码**:
```kotlin
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant
```

**替换为**:
```kotlin
    val useDarkTheme = LocalUseDarkTheme.current
    val borderColor = if (useDarkTheme) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
    val headerBackground = if (useDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC)
```

- [ ] 6.2 修改斑马纹行背景。找到 `rows.forEachIndexed` 内的 `backgroundColor`（约行 398-403）：

**当前代码**:
```kotlin
                    backgroundColor =
                        if (isStripedTableRow(rowIndex)) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
```

**替换为**:
```kotlin
                    backgroundColor =
                        if (isStripedTableRow(rowIndex)) {
                            if (useDarkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color(0xFFF8FAFC)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
```

- [ ] 6.3 给 `MarkdownTableRow` 函数签名添加 `useDarkTheme: Boolean` 参数。找到约行 414-421：

**当前签名**:
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

**替换为**:
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

- [ ] 6.4 修改 `MarkdownTableRow` 内单元格的 border 逻辑。找到 `.border(width = 1.dp, color = borderColor)`（约行 438）：

**当前代码**:
```kotlin
                    .border(width = 1.dp, color = borderColor)
```

**替换为**:
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
                        },
                    )
```

- [ ] 6.5 更新 `MarkdownTable` 内所有调用 `MarkdownTableRow` 的地方，传入 `useDarkTheme = useDarkTheme`。共 2 处调用：

**调用 1**（表头，约行 381）：
当前：
```kotlin
                MarkdownTableRow(
                    cells = header,
                    columnCount = columnCount,
                    alignments = alignments,
                    backgroundColor = headerBackground,
                    borderColor = borderColor,
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                        ),
                )
```
在 `textStyle = ...` 参数后添加：
```kotlin
                    useDarkTheme = useDarkTheme,
```

**调用 2**（数据行，约行 393）：
当前：
```kotlin
                    MarkdownTableRow(
                        cells = row,
                        columnCount = columnCount,
                        alignments = alignments,
                        backgroundColor = ...,
                        borderColor = borderColor,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                    )
```
在 `textStyle = ...` 参数后添加：
```kotlin
                        useDarkTheme = useDarkTheme,
```

- [ ] 6.6 在文件顶部添加缺失的 import（如果不存在）：
```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
```
`Color` import 已存在（行 37）。`LocalUseDarkTheme` import 已存在（行 54）。

**验证**：编译通过。浅色模式表格表头背景 `#F8FAFC`，单元格只有底部水平线，边框色 `#E2E8F0`。深色模式不变。

---

## 7. MarkdownKatex.kt — 数学公式块

**文件**: `ui/detail/MarkdownKatex.kt`

- [ ] 7.1 在 `MarkdownKatexMathBlock` composable 内添加 `useDarkTheme` 变量，并修改 Surface 参数。

找到约行 76-113。当前：
```kotlin
@Composable
internal fun MarkdownKatexMathBlock(
    expression: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleMedium
    val density = LocalDensity.current
    val inlineCodeBackground = markdownInlineCodeBackground(LocalUseDarkTheme.current)
```

在 `val colors = MaterialTheme.colorScheme` 后一行添加：
```kotlin
    val useDarkTheme = LocalUseDarkTheme.current
```

然后将 `inlineCodeBackground` 行的 `LocalUseDarkTheme.current` 替换为 `useDarkTheme`：
```kotlin
    val inlineCodeBackground = markdownInlineCodeBackground(useDarkTheme)
```

- [ ] 7.2 修改 Surface 色值和圆角。找到约行 98-101：

**当前代码**:
```kotlin
    Surface(
        color = colors.surfaceVariant.copy(alpha = 0.38f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
```

**替换为**:
```kotlin
    Surface(
        color = if (useDarkTheme) colors.surfaceVariant.copy(alpha = 0.38f) else Color(0xFFF8FAFC),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
```

- [ ] 7.3 确保文件顶部有 `import androidx.compose.ui.graphics.Color`。如果已存在则跳过。

**验证**：编译通过。浅色模式数学公式块背景 `#F8FAFC`、圆角 12dp。深色模式不变（surfaceVariant 0.38α）。

---

## 8. InputBar.kt — 输入框背景

**文件**: `ui/detail/InputBar.kt`

- [ ] 8.1 移除 PillTextField 内背景色的 alpha。找到约行 178：

**当前代码**:
```kotlin
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
```

**替换为**:
```kotlin
                        .background(MaterialTheme.colorScheme.surfaceVariant)
```

**验证**：编译通过。输入框背景在浅色模式下为实色 `#F1F5F9`。

---

## 9. 最终验证

- [ ] 9.1 运行完整编译：`cd packages/android && ./gradlew assembleDebug`
- [ ] 9.2 运行单元测试：`npm run test:unit`（确保无回归）
- [ ] 9.3 grep 确认无残留旧色值：
  - `grep -r "0xFF0071E3" packages/android/` → 应返回空
  - `grep -r "0xFFF2F2F7" packages/android/` → 应返回空（SurfaceTertiary 已改）
  - `grep -r "0xFF000000" packages/android/` → 仅允许在 `BackgroundDark` 处出现
  - `grep -r "0xFFF9F9F9" packages/android/` → 应返回空
