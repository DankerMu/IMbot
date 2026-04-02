# Design: 工具调用富显示（Tool Call Rich Display）

## Architecture

纯 Android Compose UI 层重构。核心改动是 `ToolCallCard.kt` 从"通用展示"升级为"分类展示"，新增工具类别检测和专用渲染组件。

## 工具分类模型

### 分类规则

```kotlin
enum class ToolCategory(
    val icon: ImageVector,
    val accentColor: @Composable () -> Color,
    val label: String,
) {
    READ(
        icon = Icons.Outlined.Description,      // 文件图标
        accentColor = { MaterialTheme.colorScheme.primary },  // 品牌蓝
        label = "读取文件",
    ),
    WRITE(
        icon = Icons.Outlined.Edit,              // 铅笔图标
        accentColor = { Color(0xFFFF9500) },     // iOS orange (Warning)
        label = "编辑文件",
    ),
    BASH(
        icon = Icons.Outlined.Terminal,           // 终端图标
        accentColor = { Color(0xFF34C759) },     // iOS green (Success)
        label = "执行命令",
    ),
    SEARCH(
        icon = Icons.Outlined.Search,             // 搜索图标
        accentColor = { MaterialTheme.colorScheme.primary },  // 品牌蓝
        label = "搜索",
    ),
    OTHER(
        icon = Icons.Outlined.Build,              // 扳手图标
        accentColor = { MaterialTheme.colorScheme.onSurfaceVariant },  // 灰色
        label = "工具",
    );
}

fun classifyTool(toolName: String): ToolCategory {
    val name = toolName.lowercase()
    return when {
        name in setOf("read", "readfile", "read_file") -> ToolCategory.READ
        name in setOf("write", "edit", "multiedit", "notebookedit") -> ToolCategory.WRITE
        name in setOf("bash", "execute", "shell", "command") -> ToolCategory.BASH
        name in setOf("grep", "glob", "websearch", "webfetch", "search") -> ToolCategory.SEARCH
        else -> ToolCategory.OTHER
    }
}
```

### 工具名到图标 + 颜色映射

| 工具名 | 类别 | 图标 | 强调色 | 折叠摘要内容 |
|--------|------|------|--------|-------------|
| Read | READ | Description (outlined) | primary blue | 文件路径 |
| Write, Edit, MultiEdit | WRITE | Edit (outlined) | #FF9500 orange | 文件路径 |
| Bash | BASH | Terminal (outlined) | #34C759 green | 命令文本（截断 80 字符） |
| Grep, Glob | SEARCH | Search (outlined) | primary blue | 搜索模式 |
| WebSearch, WebFetch | SEARCH | Search (outlined) | primary blue | URL 或查询词 |
| AskUserQuestion | (特殊) | 不经过 ToolCallCard，走 InteractiveToolCard | — | — |
| 其他 | OTHER | Build (outlined) | onSurfaceVariant | toolName |

## 组件设计

### 1. ToolCallCard 重构 — 外层容器

**折叠状态（默认）**：

```
┌─ 2dp 左色条 ─┬──────────────────────────────────────────────┐
│              │  [图标]  Bash · $ npm run build    [✓] [▼]   │
│  (绿色)      │                                               │
└──────────────┴──────────────────────────────────────────────┘
```

**展开状态**：

```
┌─ 2dp 左色条 ─┬──────────────────────────────────────────────┐
│              │  [图标]  Bash · $ npm run build    [✓] [▲]   │
│  (绿色)      ├──────────────────────────────────────────────┤
│              │  ┌─ 终端区域 ────────────────────────────┐   │
│              │  │  $ npm run build                       │   │
│              │  │                                        │   │
│              │  │  > wire@1.0.0 build                    │   │
│              │  │  > tsc --build                         │   │
│              │  │  Done in 2.3s                          │   │
│              │  └────────────────────────────────────────┘   │
└──────────────┴──────────────────────────────────────────────┘
```

**Compose 结构**：

```kotlin
@Composable
fun ToolCallCard(item: MessageItem.ToolCall, ...) {
    val category = classifyTool(item.toolName)
    val accentColor = category.accentColor()
    val isComplete = !item.isRunning && item.result != null
    val statusColor = when {
        item.isRunning -> accentColor
        item.isError -> MaterialTheme.colorScheme.error
        else -> Color(0xFF34C759)  // Success green
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // 左侧 2dp 状态色条
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()  // 使用 IntrinsicSize.Min + fillMaxHeight
                .background(statusColor)
        )

        Column(modifier = Modifier.weight(1f)) {
            // Header: 图标 + 类别标签 + 摘要 + 状态 + 展开箭头
            ToolCallHeader(category, item, statusColor, expanded, onToggle)

            // Content: 按类别分发渲染
            AnimatedVisibility(visible = expanded) {
                when (category) {
                    ToolCategory.BASH -> BashToolContent(item)
                    ToolCategory.READ -> ReadToolContent(item)
                    ToolCategory.WRITE -> WriteToolContent(item)
                    ToolCategory.SEARCH -> SearchToolContent(item)
                    ToolCategory.OTHER -> GenericToolContent(item)
                }
            }
        }
    }
}
```

### 2. ToolCallHeader — 折叠摘要行

```kotlin
@Composable
private fun ToolCallHeader(
    category: ToolCategory,
    item: MessageItem.ToolCall,
    statusColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 展开/折叠箭头 (12dp)
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 类别图标 (16dp, 着色)
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = category.accentColor(),
        )

        // 摘要文本: "Bash · $ npm run build" 或 "Read · src/index.ts"
        Text(
            text = buildToolSummary(category, item),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )

        // 状态指示器
        ToolStatusIndicator(item.isRunning, item.isError)
    }
}
```

**摘要提取逻辑**：

```kotlin
private fun buildToolSummary(category: ToolCategory, item: MessageItem.ToolCall): String {
    val prefix = item.toolName.replaceFirstChar { it.uppercase() }
    val detail = when (category) {
        ToolCategory.BASH -> extractBashCommand(item.args)?.take(60)
        ToolCategory.READ -> extractFilePath(item.args)
        ToolCategory.WRITE -> extractFilePath(item.args)
        ToolCategory.SEARCH -> extractSearchPattern(item.args)
        ToolCategory.OTHER -> item.title.takeIf(String::isNotBlank)
    }
    return if (detail != null) "$prefix · $detail" else prefix
}

// 从 Bash args JSON 中提取 command 字段
private fun extractBashCommand(args: String?): String? {
    if (args == null) return null
    return try {
        JSONObject(args).optString("command", "").takeIf(String::isNotBlank)
    } catch (_: Exception) {
        // args 可能直接是命令文本
        args.take(80).takeIf(String::isNotBlank)
    }
}

// 从 Read/Write/Edit args JSON 中提取 file_path 字段
private fun extractFilePath(args: String?): String? {
    if (args == null) return null
    return try {
        val json = JSONObject(args)
        (json.optString("file_path", "") 
            .takeIf(String::isNotBlank)
            ?: json.optString("path", "")
            .takeIf(String::isNotBlank))
            ?.let { path ->
                // 只显示最后 2-3 段路径
                val segments = path.split("/").filter(String::isNotBlank)
                if (segments.size > 3) ".../" + segments.takeLast(3).joinToString("/")
                else path
            }
    } catch (_: Exception) { null }
}

// 从 Grep/Glob args JSON 中提取搜索模式
private fun extractSearchPattern(args: String?): String? {
    if (args == null) return null
    return try {
        val json = JSONObject(args)
        json.optString("pattern", "").takeIf(String::isNotBlank)
            ?: json.optString("query", "").takeIf(String::isNotBlank)
    } catch (_: Exception) { null }
}
```

### 3. ToolStatusIndicator — 状态图标

```kotlin
@Composable
private fun ToolStatusIndicator(isRunning: Boolean, isError: Boolean) {
    when {
        isRunning -> {
            // 旋转动画 spinner (14dp)
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        isError -> {
            // 红色 ✗ 图标
            Icon(
                imageVector = Icons.Outlined.Cancel,
                contentDescription = "失败",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        else -> {
            // 绿色 ✓ 图标
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "完成",
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF34C759),
            )
        }
    }
}
```

### 4. BashToolContent — 终端样式渲染

**这是最关键的组件，需要精确规范给 Codex 实现**：

```kotlin
/**
 * Bash 工具的终端样式内容区域。
 *
 * 视觉规范：
 * - 外层容器：圆角 8dp，背景 #0A0A0A (接近纯黑)，内边距 12dp
 * - 命令行：绿色 "$ " 前缀 (#34C759)，白色命令文本 (#F5F5F5)
 * - 输出区域：浅灰色文本 (#A1A1AA，zinc-400)
 * - 字体：Monospace，12sp
 * - 最大高度 240dp（约 20 行），超出后可滚动
 * - 命令和输出之间 8dp 间距
 * - 暗色模式下保持不变（终端本身就是暗色）
 */
@Composable
private fun BashToolContent(item: MessageItem.ToolCall) {
    val command = extractBashCommand(item.args)
    val output = item.result

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                color = Color(0xFF0A0A0A),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 命令行
        if (command != null) {
            Row {
                // 绿色 "$ " 提示符
                Text(
                    text = "$ ",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF34C759),  // iOS green
                )
                // 白色命令文本
                SelectionContainer {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFF5F5F5),
                    )
                }
            }
        }

        // 输出区域
        if (output?.isNotBlank() == true) {
            SelectionContainer {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA1A1AA),  // zinc-400
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
```

### 5. ReadToolContent — 文件读取渲染

```kotlin
/**
 * Read 工具的文件展示区域。
 *
 * 视觉规范：
 * - 文件路径：Monospace 12sp，onSurfaceVariant 颜色，左侧 Description 图标
 * - 文件内容：复用已有 CodeBlock 组件，自动检测语言
 * - 如果结果超过 500 字符，默认折叠，显示 "点击展开完整内容"
 */
@Composable
private fun ReadToolContent(item: MessageItem.ToolCall) {
    val filePath = extractFilePath(item.args)
    val content = item.result
    val language = filePath?.substringAfterLast(".")?.lowercase()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 文件路径
        if (filePath != null) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 文件内容 — 复用 CodeBlock
        if (content?.isNotBlank() == true) {
            CodeBlock(
                code = content,
                language = language ?: "text",
            )
        }
    }
}
```

### 6. WriteToolContent — 文件编辑渲染

```kotlin
/**
 * Write/Edit 工具的 diff 展示区域。
 *
 * 视觉规范：
 * - 文件路径：同 ReadToolContent
 * - 如果 args 包含 old_string/new_string（Edit 工具）：显示 diff
 *   - 删除行：背景 #FF3B3020 (红色 12% 透明度)，文本 #FF6B6B，"-" 前缀
 *   - 新增行：背景 #34C75920 (绿色 12% 透明度)，文本 #4ADE80，"+" 前缀
 * - 如果 args 包含 content（Write 工具）：显示代码块预览
 * - 最大高度 300dp，超出可滚动
 */
@Composable
private fun WriteToolContent(item: MessageItem.ToolCall) {
    val filePath = extractFilePath(item.args)
    val oldString = extractJsonField(item.args, "old_string")
    val newString = extractJsonField(item.args, "new_string")
    val writeContent = extractJsonField(item.args, "content")
    val language = filePath?.substringAfterLast(".")?.lowercase()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filePath != null) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Diff 视图（Edit 工具）
        if (oldString != null && newString != null) {
            DiffView(
                oldText = oldString,
                newText = newString,
            )
        }
        // 完整内容（Write 工具）
        else if (writeContent?.isNotBlank() == true) {
            CodeBlock(
                code = writeContent,
                language = language ?: "text",
            )
        }
        // Fallback：显示 result
        else if (item.result?.isNotBlank() == true) {
            Text(
                text = item.result,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
```

### 7. DiffView — Diff 渲染组件

```kotlin
/**
 * 简易 diff 视图。
 *
 * 视觉规范（精确色值，Codex 必须严格遵守）：
 * - 外层：圆角 8dp，background surfaceVariant
 * - 删除行：
 *   - 背景：Color(0x1AFF3B30) — 红色 10% 透明度
 *   - 文本：Color(0xFFFF6B6B) — 亮红（暗色模式）/ Color(0xFFCC3333)（亮色模式）
 *   - 前缀："- " (monospace)
 * - 新增行：
 *   - 背景：Color(0x1A34C759) — 绿色 10% 透明度
 *   - 文本：Color(0xFF4ADE80) — 亮绿（暗色模式）/ Color(0xFF228B22)（亮色模式）
 *   - 前缀："+ " (monospace)
 * - 字体：Monospace 12sp
 * - 内边距：水平 12dp，垂直 8dp
 * - 行间距：0dp（紧凑）
 * - 最大高度：200dp，超出可滚动
 */
@Composable
private fun DiffView(oldText: String, newText: String) {
    val isDark = isSystemInDarkTheme()
    val removedBg = Color(0x1AFF3B30)
    val removedFg = if (isDark) Color(0xFFFF6B6B) else Color(0xFFCC3333)
    val addedBg = Color(0x1A34C759)
    val addedFg = if (isDark) Color(0xFF4ADE80) else Color(0xFF228B22)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // 删除行
        oldText.lines().forEach { line ->
            Text(
                text = "- $line",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = removedFg,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(removedBg)
                    .padding(horizontal = 12.dp, vertical = 1.dp),
            )
        }
        // 新增行
        newText.lines().forEach { line ->
            Text(
                text = "+ $line",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = addedFg,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(addedBg)
                    .padding(horizontal = 12.dp, vertical = 1.dp),
            )
        }
    }
}
```

### 8. SearchToolContent — 搜索结果渲染

```kotlin
/**
 * Search 工具（Grep/Glob/WebSearch）的结果展示。
 *
 * 视觉规范：
 * - 搜索模式/查询词：labelSmall monospace，primary 颜色
 * - 结果：monospace bodySmall，onSurface 颜色
 * - 背景：surfaceVariant 50% 透明度
 * - 最大显示 50 行，超出截断并显示 "+N more lines"
 * - 圆角 8dp，内边距 12dp
 */
@Composable
private fun SearchToolContent(item: MessageItem.ToolCall) {
    val pattern = extractSearchPattern(item.args)
    val result = item.result

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pattern != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = pattern,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (result?.isNotBlank() == true) {
            val lines = result.lines()
            val displayLines = lines.take(50)
            val truncated = lines.size > 50

            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp)
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = displayLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (truncated) {
                Text(
                    text = "+${lines.size - 50} more lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

### 9. GenericToolContent — 通用 Fallback

```kotlin
/**
 * 未分类工具的通用渲染（保持现有行为，但使用新样式）。
 *
 * - 参数区域：labelSmall 标题 + monospace bodySmall 内容
 * - 结果区域：同上
 * - 最大高度 200dp
 */
@Composable
private fun GenericToolContent(item: MessageItem.ToolCall) {
    // 保持当前 ToolCallSection 逻辑，但限制高度
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item.args?.takeIf(String::isNotBlank)?.let { args ->
            ToolCallSection(title = "参数", content = args)
        }
        item.result?.takeIf(String::isNotBlank)?.let { result ->
            ToolCallSection(title = "结果", content = result)
        }
    }
}
```

### 10. isError 字段添加

当前 `MessageItem.ToolCall` 没有 `isError` 字段。需要添加：

```kotlin
data class ToolCall(
    val callId: String,
    val toolName: String,
    val title: String,
    val args: String?,
    val result: String?,
    val isRunning: Boolean,
    val isError: Boolean = false,  // 新增
)
```

**来源**：`tool_call_completed` 事件的 payload 中如果 result 包含错误信息（error 字段），或者后续有 `session_error` 事件紧随其后。初版简化处理：检测 result 中是否包含 "error" / "Error" / "ERROR" 字样或 result 为 null 时 isError = true。

## 辅助函数

```kotlin
// 从 JSON 字符串中提取指定字段
private fun extractJsonField(json: String?, field: String): String? {
    if (json == null) return null
    return try {
        JSONObject(json).optString(field, "").takeIf(String::isNotBlank)
    } catch (_: Exception) { null }
}
```

## File Changes

| File | Change |
|------|--------|
| `ui/detail/ToolCallCard.kt` | 完全重写：分类容器 + 左色条 + header + 分类内容 |
| `ui/detail/ToolCategory.kt` | 新增：工具分类枚举 + classifyTool() |
| `ui/detail/ToolContentRenderers.kt` | 新增：BashToolContent, ReadToolContent, WriteToolContent, SearchToolContent, GenericToolContent |
| `ui/detail/DiffView.kt` | 新增：简易 diff 渲染组件 |
| `ui/detail/ToolCallUtils.kt` | 新增：extractBashCommand, extractFilePath, extractSearchPattern, buildToolSummary 等辅助函数 |
| `ui/detail/DetailViewModel.kt` | MessageItem.ToolCall 新增 isError 字段 |

## Dependencies

- 复用已有 `ui/components/CodeBlock.kt` 做语法高亮
- 与 `p3-longpress-menu-fix` 独立——本 change 不改变长按逻辑

## Constraints

- 不修改 relay/companion/wire 协议
- 不引入新的第三方库
- 终端样式在亮色/暗色模式下保持一致（终端本身就是暗色）
- 所有新组件必须支持 SelectionContainer 文本选择
