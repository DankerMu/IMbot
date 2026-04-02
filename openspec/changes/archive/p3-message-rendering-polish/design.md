# Design: Message Rendering Polish

## Architecture

纯 UI 层改动，不涉及数据流或协议变更。所有改动在现有 Compose 渲染层进行。

## Rendering Improvements

### 1. Typography & Spacing System

**段落**：
```kotlin
// Current: 默认 Text spacing
// Target:
val ParagraphStyle = TextStyle(
    lineHeight = 24.sp,         // ~1.7x of 14sp body
    letterSpacing = 0.2.sp,     // 微调字间距
)
val ParagraphSpacing = 12.dp    // 段间距
val HeadingTopSpacing = 20.dp   // 标题段前额外留白
```

**标题层级**：
```kotlin
H1: 24sp, SemiBold, bottomPadding = 16dp
H2: 20sp, SemiBold, bottomPadding = 12dp, topPadding = 20dp
H3: 17sp, Medium, bottomPadding = 8dp, topPadding = 16dp
H4-H6: 15sp, Medium, bottomPadding = 4dp, topPadding = 12dp
```

### 2. Code Block Enhancement

**当前**：RoundedCornerShape(8dp) 灰色背景，Monospace 字体，7 token 高亮。

**目标**：
```
┌─ kotlin ─────────────────────── 📋 ┐
│  1 │ fun greet() {                  │
│  2 │     println("hi")              │
│  3 │ }                              │
└─────────────────────────────────────┘
```

- **语言标签**：顶部栏左侧，12sp labelSmall，surface variant 背景
- **行号**：左侧列，monospace，muted 颜色，右对齐，竖线分隔
- **复制按钮**：顶部栏右侧保留，添加 "已复制" 反馈动画
- **Token 扩展**：新增 Annotation, Operator, Bracket 三种 token type
- **背景色**：Light `#F6F8FA` / Dark `#161B22`（GitHub 风格）
- **圆角**：12dp（从 8dp 增加）
- **内边距**：horizontal 16dp, vertical 12dp

### 3. Blockquote Styling

**当前**：灰色背景 Surface composable。

**目标**：
```
│  Quoted text with proper left border
│  and multi-line support
```

- 左边框：4dp 宽度，`Primary.copy(alpha = 0.4f)` 颜色
- 背景：`SurfaceVariant.copy(alpha = 0.3f)`
- 左内边距：16dp（边框后）
- 嵌套引用：边框颜色递进变淡（alpha 0.4 → 0.25 → 0.15）
- 垂直间距：上下各 8dp

### 4. List Styling

**无序列表**：
```kotlin
Level 0: "●" (filled circle)    indent = 0dp
Level 1: "○" (hollow circle)   indent = 20dp
Level 2: "■" (filled square)   indent = 40dp
Level 3+: "▪" (small square)   indent = 20dp * level
```

**有序列表**：
```kotlin
数字右对齐 + "." 后缀
indent = 20dp * level
数字宽度固定为 24dp（保证对齐）
```

- Bullet/数字颜色：`OnSurface.copy(alpha = 0.6f)`
- 列表项行间距：6dp
- 列表块外间距：上下各 8dp

### 5. Table Styling

**当前**：修复了渲染正确性，但样式简陋。

**目标**：
```
┌──────────┬──────────┐
│  Col A   │  Col B   │  ← 表头：Medium weight, surfaceVariant 背景
├──────────┼──────────┤
│  1       │  2       │  ← 奇数行：surface
│  3       │  4       │  ← 偶数行：surfaceVariant.copy(alpha=0.3f)
└──────────┴──────────┘
```

- 表头行：`SurfaceVariant` 背景，`Medium` 字重
- 斑马条纹：偶数行 `SurfaceVariant.copy(alpha = 0.3f)`
- Cell padding：horizontal 12dp, vertical 8dp
- 边框：1dp `OutlineVariant` 颜色
- 宽表格：`horizontalScroll` 支持
- 对齐：支持 left/center/right（从解析层已有）

### 6. Inline Elements

**Inline Code**：
```kotlin
// Current: basic SpanStyle with monospace
// Target:
SpanStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,  // 比正文小 1sp
    background = Color(0xFFEFF1F3),  // Light
    // Dark: Color(0xFF2D333B)
)
// + 圆角通过 DrawBehind modifier 模拟（AnnotatedString 不支持原生圆角）
```

**链接**：
```kotlin
SpanStyle(
    color = MaterialTheme.colorScheme.primary,
    textDecoration = TextDecoration.Underline,
)
// Press state: alpha = 0.7f
```

**粗体/斜体/删除线**：保持现有实现，确保组合正确（如 bold + italic）。

### 7. Message Bubble Refinement

**Agent 消息**：
- 背景：`SurfaceContainer`（略深于 Surface）
- 圆角：topStart = 4dp, topEnd = 16dp, bottomStart = 16dp, bottomEnd = 16dp
- 阴影：`elevation = 0.5dp`（极微妙）
- Provider badge：保持现有样式

**用户消息**：
- 背景：`PrimaryContainer`
- 圆角：topStart = 16dp, topEnd = 4dp, bottomStart = 16dp, bottomEnd = 16dp
- 阴影：`elevation = 0.5dp`

**状态气泡**：
- 保持居中对齐
- 减小字号至 11sp
- 增加透明度，减少视觉噪音

## File Changes

| File | Change |
|------|--------|
| `ui/detail/MarkdownText.kt` | 排版参数调整、列表/引用/标题样式升级 |
| `ui/detail/MarkdownKatex.kt` | KaTeX 块间距调整 |
| `ui/components/CodeBlock.kt` | 语言标签、行号、扩展 token、GitHub 色 |
| `ui/detail/MessageBubble.kt` | 气泡圆角/阴影/背景色调整 |
| `ui/theme/CodeTheme.kt` | 新增 3 token type + GitHub 风格色值 |
| `ui/theme/IMbotTheme.kt` | 新增 SurfaceContainer 色值 |
| `ui/detail/DetailUtils.kt` | 状态气泡样式参数 |

## Constraints

- 不改变 Markdown 解析逻辑，仅调整渲染样式参数
- 所有改动必须同时适配 Light / Dark 模式
- 代码块行号为可选功能（默认开启，长代码块可能影响性能——限制最大行号到 999）
