# Tasks: 工具调用富显示（Tool Call Rich Display）

## 1. 数据模型

### 1.1 ToolCategory 枚举
- [ ] 新增 `ToolCategory.kt`：READ / WRITE / BASH / SEARCH / OTHER
- [ ] 每个类别定义：icon (ImageVector), accentColor (@Composable), label (String)
- [ ] 图标：READ=Description, WRITE=Edit, BASH=Terminal, SEARCH=Search, OTHER=Build（全部 Outlined 变体）
- [ ] 颜色：READ=primary, WRITE=#FF9500, BASH=#34C759, SEARCH=primary, OTHER=onSurfaceVariant

### 1.2 classifyTool() 函数
- [ ] 输入 toolName (String)，输出 ToolCategory
- [ ] 匹配规则（lowercase）：
  - READ: "read", "readfile", "read_file"
  - WRITE: "write", "edit", "multiedit", "notebookedit"
  - BASH: "bash", "execute", "shell", "command"
  - SEARCH: "grep", "glob", "websearch", "webfetch", "search"
  - OTHER: 所有其他

### 1.3 MessageItem.ToolCall 增加 isError
- [ ] 新增 `isError: Boolean = false` 字段
- [ ] DetailViewModel 构造 ToolCall 时检测：result 为 null 或 payload 含 error 字段 → isError = true

## 2. 辅助函数

### 2.1 ToolCallUtils.kt（新增）
- [ ] `extractBashCommand(args: String?): String?` — 从 JSON 提取 `command` 字段
- [ ] `extractFilePath(args: String?): String?` — 从 JSON 提取 `file_path` 或 `path`，只保留最后 3 段路径
- [ ] `extractSearchPattern(args: String?): String?` — 从 JSON 提取 `pattern` 或 `query`
- [ ] `extractJsonField(json: String?, field: String): String?` — 通用 JSON 字段提取
- [ ] `buildToolSummary(category: ToolCategory, item: ToolCall): String` — 构建折叠状态摘要文本

## 3. 外层容器

### 3.1 ToolCallCard 重写
- [ ] 移除现有 Card 包装，改用 Row + 左侧色条 + Column
- [ ] 左侧色条：`Box` width=2.dp, fillMaxHeight, background=statusColor
- [ ] 使用 `Modifier.height(IntrinsicSize.Min)` 让色条与内容等高
- [ ] 卡片背景：surfaceVariant alpha 0.3（非完整卡片）
- [ ] 圆角：右侧 8dp（左侧被色条覆盖）

### 3.2 ToolCallHeader（折叠摘要行）
- [ ] 布局：`Row(padding = 12dp x 10dp, spacing = 8dp)`
- [ ] 元素顺序：折叠箭头(14dp) → 类别图标(16dp, 着色) → 摘要文本(weight=1, monospace, ellipsis) → 状态指示器
- [ ] 摘要格式："ToolName · detail"（如 "Bash · $ npm run build", "Read · src/index.ts"）

### 3.3 ToolStatusIndicator
- [ ] isRunning → CircularProgressIndicator (14dp, strokeWidth=1.5dp, primary)
- [ ] isError → Icons.Outlined.Cancel (14dp, error color)
- [ ] 完成 → Icons.Outlined.CheckCircle (14dp, #34C759 green)

## 4. 分类内容渲染

### 4.1 BashToolContent — 终端样式
- [ ] 外层容器：RoundedCornerShape(8.dp), background Color(0xFF0A0A0A), padding 12.dp
- [ ] 命令行："$ " 前缀 Color(0xFF34C759) green + 命令文本 Color(0xFFF5F5F5) white
- [ ] 输出文本：Color(0xFFA1A1AA) zinc-400
- [ ] 字体：Monospace bodySmall (12sp)
- [ ] 输出区域最大高度 240dp，verticalScroll
- [ ] 命令和输出间距 8dp
- [ ] 亮色/暗色模式下终端样式不变（始终深色背景）
- [ ] 支持 SelectionContainer 文本选择

### 4.2 ReadToolContent — 文件展示
- [ ] 文件路径：labelSmall monospace, onSurfaceVariant, maxLines=1, ellipsis
- [ ] 内容：复用 CodeBlock 组件，language 从文件扩展名推断
- [ ] 路径和内容间距 8dp

### 4.3 WriteToolContent — 编辑/写入展示
- [ ] 文件路径：同 ReadToolContent
- [ ] Edit 工具（有 old_string + new_string）：渲染 DiffView
- [ ] Write 工具（有 content）：渲染 CodeBlock
- [ ] Fallback：显示 result 纯文本

### 4.4 DiffView（新组件）
- [ ] 删除行背景：Color(0x1AFF3B30) red 10% alpha
- [ ] 删除行文本：暗色 Color(0xFFFF6B6B) / 亮色 Color(0xFFCC3333)
- [ ] 删除行前缀："- "
- [ ] 新增行背景：Color(0x1A34C759) green 10% alpha
- [ ] 新增行文本：暗色 Color(0xFF4ADE80) / 亮色 Color(0xFF228B22)
- [ ] 新增行前缀："+ "
- [ ] 字体：Monospace bodySmall (12sp)
- [ ] 行内边距：horizontal=12dp, vertical=1dp
- [ ] 外层圆角 8dp，最大高度 200dp，verticalScroll
- [ ] 先渲染所有删除行，再渲染所有新增行

### 4.5 SearchToolContent — 搜索结果
- [ ] 搜索模式：Search 图标(12dp) + pattern 文本(labelSmall monospace, primary)
- [ ] 结果：monospace bodySmall, surfaceVariant 50% 背景, 圆角 8dp, padding 12dp
- [ ] 最大 50 行，超出显示 "+N more lines" 提示
- [ ] 最大高度 240dp，verticalScroll

### 4.6 GenericToolContent — 通用 Fallback
- [ ] 保持现有 "参数" + "结果" 两段 ToolCallSection 布局
- [ ] 限制最大高度 200dp

## 5. 测试

### 5.1 Unit Tests — classifyTool

- [ ] `classifyTool("Bash")` → BASH
- [ ] `classifyTool("bash")` → BASH
- [ ] `classifyTool("Read")` → READ
- [ ] `classifyTool("Write")` → WRITE
- [ ] `classifyTool("Edit")` → WRITE
- [ ] `classifyTool("MultiEdit")` → WRITE
- [ ] `classifyTool("Grep")` → SEARCH
- [ ] `classifyTool("Glob")` → SEARCH
- [ ] `classifyTool("WebSearch")` → SEARCH
- [ ] `classifyTool("AskUserQuestion")` → OTHER（走 InteractiveToolCard）
- [ ] `classifyTool("Agent")` → OTHER
- [ ] `classifyTool("")` → OTHER

### 5.2 Unit Tests — 辅助函数

- [ ] `extractBashCommand('{"command":"ls -la"}')` → "ls -la"
- [ ] `extractBashCommand(null)` → null
- [ ] `extractBashCommand("not json")` → "not json"（前 80 字符）
- [ ] `extractFilePath('{"file_path":"/home/user/project/src/index.ts"}')` → ".../project/src/index.ts"
- [ ] `extractFilePath('{"path":"short.txt"}')` → "short.txt"
- [ ] `extractFilePath(null)` → null
- [ ] `extractSearchPattern('{"pattern":"class Foo"}')` → "class Foo"
- [ ] `extractSearchPattern('{"query":"react hooks"}')` → "react hooks"
- [ ] `buildToolSummary(BASH, item(args='{"command":"npm test"}'))` → "Bash · $ npm test"
- [ ] `buildToolSummary(READ, item(args='{"file_path":"src/index.ts"}'))` → "Read · src/index.ts"
- [ ] `buildToolSummary(OTHER, item(toolName="LSP"))` → "Lsp"

### 5.3 功能回归

- [ ] 现有所有工具类型仍能正确渲染（不丢信息）
- [ ] 折叠/展开动画正常
- [ ] 长按复制功能不受影响
- [ ] 运行中工具显示 spinner
- [ ] 完成工具显示绿色勾
- [ ] 终端区域可文本选择
- [ ] 代码块语法高亮正常
- [ ] 暗色模式所有颜色正确
