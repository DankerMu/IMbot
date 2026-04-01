# Tasks: Message Rendering Polish

## 1. Typography & Spacing

### 1.1 段落排版
- [ ] 修改 `MarkdownText.kt`：段落行高调整为 24sp（~1.7x of 14sp body）
- [ ] 段间距调整为 12dp
- [ ] 标题段前额外留白：H1=16dp, H2=20dp, H3=16dp, H4-H6=12dp
- [ ] 标题字号/字重：H1=24sp SemiBold, H2=20sp SemiBold, H3=17sp Medium, H4-H6=15sp Medium

### 1.2 标题底部间距
- [ ] H1 bottomPadding=16dp, H2=12dp, H3=8dp, H4-H6=4dp

## 2. Code Block Enhancement

### 2.1 语言标签
- [ ] 修改 `CodeBlock.kt`：顶部栏左侧显示语言名称
- [ ] 标签样式：12sp labelSmall，surfaceVariant 背景，8dp 圆角
- [ ] 未知语言时不显示标签

### 2.2 行号
- [ ] 代码内容左侧添加行号列
- [ ] 行号样式：monospace, muted 颜色 (LabelTertiary), 右对齐
- [ ] 行号与代码间 1dp 竖线分隔
- [ ] 最大行号 999（超长代码块截断行号显示）

### 2.3 语法高亮扩展
- [ ] 修改 `CodeTheme.kt`：新增 Annotation, Operator, Bracket 三种 token type
- [ ] 修改 `CodeTokenizer`：识别 `@annotation`, `+=-<>!&|`, `(){}[]` 并分类
- [ ] 调整色值为 GitHub 风格（light: 基于 github-light, dark: 基于 github-dark）

### 2.4 代码块容器样式
- [ ] 背景色：Light `#F6F8FA` / Dark `#161B22`
- [ ] 圆角：12dp（从 8dp 增加）
- [ ] 内边距：horizontal 16dp, vertical 12dp
- [ ] 复制按钮添加 "已复制" → "复制" 反馈动画（Check icon 2s → Copy icon）

## 3. Blockquote Styling

### 3.1 左边框引用
- [ ] 修改 `MarkdownText.kt` 的引用块渲染
- [ ] 左边框：4dp 宽度，`Primary.copy(alpha = 0.4f)` 颜色
- [ ] 背景：`SurfaceVariant.copy(alpha = 0.3f)`
- [ ] 左内边距：16dp（边框后）
- [ ] 垂直外间距：上下各 8dp

### 3.2 嵌套引用
- [ ] 支持嵌套引用（`>> text`）
- [ ] 边框颜色递进：alpha 0.4 → 0.25 → 0.15
- [ ] 嵌套缩进递增 16dp

## 4. List Styling

### 4.1 无序列表
- [ ] Bullet 样式随层级变化：Level 0 "●", Level 1 "○", Level 2 "■", Level 3+ "▪"
- [ ] 缩进：20dp * level
- [ ] Bullet 颜色：`OnSurface.copy(alpha = 0.6f)`

### 4.2 有序列表
- [ ] 数字右对齐 + "." 后缀
- [ ] 数字宽度固定 24dp
- [ ] 缩进同无序列表

### 4.3 列表间距
- [ ] 列表项行间距 6dp
- [ ] 列表块外间距上下各 8dp

## 5. Table Styling

### 5.1 表头
- [ ] 表头行背景：`SurfaceVariant`
- [ ] 字重：Medium
- [ ] Cell padding：horizontal 12dp, vertical 8dp

### 5.2 斑马条纹
- [ ] 偶数行背景：`SurfaceVariant.copy(alpha = 0.3f)`
- [ ] 奇数行背景：Surface

### 5.3 边框与滚动
- [ ] 边框：1dp `OutlineVariant` 颜色
- [ ] 宽表格支持 `horizontalScroll`
- [ ] 表格外间距上下各 12dp

## 6. Inline Elements

### 6.1 Inline Code
- [ ] 字号：13sp（比正文小 1sp）
- [ ] 背景色：Light `#EFF1F3` / Dark `#2D333B`
- [ ] 通过 `DrawBehind` modifier 模拟圆角背景（4dp radius）

### 6.2 链接
- [ ] 颜色：`Primary`
- [ ] 下划线：`TextDecoration.Underline`
- [ ] Press 状态：alpha = 0.7f 反馈

## 7. Message Bubble Refinement

### 7.1 Agent 消息气泡
- [ ] 背景：`SurfaceContainer`
- [ ] 非对称圆角：topStart=4dp, topEnd=16dp, bottomStart=16dp, bottomEnd=16dp
- [ ] elevation：0.5dp

### 7.2 用户消息气泡
- [ ] 背景：`PrimaryContainer`
- [ ] 非对称圆角：topStart=16dp, topEnd=4dp, bottomStart=16dp, bottomEnd=16dp
- [ ] elevation：0.5dp

### 7.3 状态气泡减噪
- [ ] 字号缩小至 11sp
- [ ] 颜色透明度增加（降低视觉重量）
- [ ] 连续相同状态考虑合并

## 8. Dark Mode Verification

- [ ] 所有改动在 Dark 模式下验证
- [ ] 代码块 Dark 色值 #161B22 验证
- [ ] 引用块 Dark 模式边框和背景对比度验证
- [ ] 表格 Dark 模式斑马条纹可辨识度验证
- [ ] Inline code Dark 色值 #2D333B 验证

## 9. Tests

> **测试环境**：使用 book CLI 作为 provider 进行端到端渲染验证。发送包含各种 Markdown 元素的消息，验证手机端渲染效果。

### 9.1 CodeTokenizer — Unit Tests

**新 token 类型识别**：
- [ ] `@Test` → Annotation type
- [ ] `@Suppress("X")` → Annotation type（含括号参数）
- [ ] `@file:Suppress` → Annotation type（含前缀）
- [ ] `+=` → Operator type
- [ ] `&&` → Operator type
- [ ] `||` → Operator type
- [ ] `!=` → Operator type
- [ ] `->` → Operator type（Kotlin lambda）
- [ ] `{` → Bracket type
- [ ] `}` → Bracket type
- [ ] `[` → Bracket type
- [ ] `()` → Bracket types（左右各一个）
- [ ] 混合行 `@Test fun foo() {` → [Annotation, Keyword, Function, Bracket, Bracket]

**边界**：
- [ ] 空字符串 → 空 token 列表
- [ ] 纯空白 "   " → 空 token 列表
- [ ] 纯注释 `// comment` → [Comment]
- [ ] `@` 单独出现（非注解上下文）→ 不误判为 Annotation
- [ ] 邮箱 `user@test.com` → 不误判为 Annotation

### 9.2 代码块 — Unit Tests

**语言标签提取**：
- [ ] ` ```kotlin ` → "kotlin"
- [ ] ` ```javascript ` → "javascript"
- [ ] ` ``` ` 无语言 → null（不显示标签）
- [ ] ` ```KOTLIN ` 大写 → "kotlin"（标准化为小写）
- [ ] ` ```kotlin extra ` 含多余文本 → "kotlin"（取第一个 token）

**行号生成**：
- [ ] 0 行代码 → 不显示行号区域
- [ ] 1 行代码 → 行号 "1"
- [ ] 100 行代码 → 行号 1-100，右对齐（宽度 = 3 字符位）
- [ ] 999 行代码 → 行号 1-999
- [ ] 1000+ 行 → 行号截断到 999，第 1000 行显示 "..."

**复制按钮状态**：
- [ ] 初始状态 → 显示 Copy 图标
- [ ] 点击后 → 立即切换为 Check 图标
- [ ] 2s 后 → 恢复为 Copy 图标
- [ ] 快速连续点击 → 不重复 toast，重置 2s 计时器

### 9.3 引用块 — Unit Tests

**嵌套层级**：
- [ ] 单层引用 `> text` → alpha = 0.4f 边框
- [ ] 二层引用 `>> text` → alpha = 0.25f 边框
- [ ] 三层引用 `>>> text` → alpha = 0.15f 边框
- [ ] 四层引用 `>>>> text` → alpha 不低于 0.1f（下限保护）
- [ ] 缩进计算：level 0 = 0dp, level 1 = 16dp, level 2 = 32dp

### 9.4 列表 — Unit Tests

**Bullet 层级映射**：
- [ ] level 0 → "●"（filled circle）
- [ ] level 1 → "○"（hollow circle）
- [ ] level 2 → "■"（filled square）
- [ ] level 3 → "▪"（small square）
- [ ] level 5 → "▪"（3+ 统一为 small square）

**有序列表对齐**：
- [ ] 单数字 "1." → 宽度 24dp，右对齐
- [ ] 两位数 "10." → 宽度 24dp，右对齐
- [ ] 三位数 "100." → 宽度 24dp，右对齐（数字可能溢出但不截断）

**缩进计算**：
- [ ] level 0 → indent 0dp
- [ ] level 1 → indent 20dp
- [ ] level 2 → indent 40dp

### 9.5 表格 — Unit Tests

**斑马条纹**：
- [ ] row 0 → surface（非 variant）
- [ ] row 1 → surfaceVariant.copy(alpha=0.3f)
- [ ] row 2 → surface
- [ ] 仅表头无数据行 → 仅显示表头（variant 背景），无数据行

**对齐**：
- [ ] alignment = LEFT → textAlign Start
- [ ] alignment = CENTER → textAlign Center
- [ ] alignment = RIGHT → textAlign End
- [ ] 无 alignment 信息 → 默认 Start

**Cell padding**：
- [ ] 验证 horizontal 12dp, vertical 8dp 断言

### 9.6 排版参数 — Unit Tests

**标题层级**：
- [ ] H1 → fontSize 24sp, fontWeight SemiBold, bottomPadding 16dp, topPadding 16dp
- [ ] H2 → fontSize 20sp, fontWeight SemiBold, bottomPadding 12dp, topPadding 20dp
- [ ] H3 → fontSize 17sp, fontWeight Medium, bottomPadding 8dp, topPadding 16dp
- [ ] H4 → fontSize 15sp, fontWeight Medium, bottomPadding 4dp, topPadding 12dp
- [ ] H5 → 同 H4
- [ ] H6 → 同 H4

**段落**：
- [ ] 段落行高 = 24sp
- [ ] 段间距 = 12dp

### 9.7 Inline 元素 — Unit Tests

**Inline code style**：
- [ ] fontSize = 13sp
- [ ] Light mode background = Color(0xFFEFF1F3)
- [ ] Dark mode background = Color(0xFF2D333B)

**Link style**：
- [ ] color = MaterialTheme.primary
- [ ] textDecoration = Underline

### 9.8 Message Bubble — Unit Tests

**Agent 气泡圆角**：
- [ ] topStart = 4dp, topEnd = 16dp, bottomStart = 16dp, bottomEnd = 16dp

**User 气泡圆角**：
- [ ] topStart = 16dp, topEnd = 4dp, bottomStart = 16dp, bottomEnd = 16dp

### 9.9 Dark Mode — Unit Tests

- [ ] 代码块背景：Dark = Color(0xFF161B22)
- [ ] 代码块背景：Light = Color(0xFFF6F8FA)
- [ ] Inline code 背景：Dark = Color(0xFF2D333B)
- [ ] 引用块边框 Dark 模式下对比度 > 3:1（相对于背景）
- [ ] 表格斑马条纹 Dark 模式下两种行背景色差异可辨识（delta E > 5）

### 9.10 Integration Tests (book CLI)

- [ ] **综合 Markdown 渲染**：通过 book session 发送包含所有元素的 Markdown（H1-H3、段落、bold/italic/strikethrough、inline code、link、bullet list 3 层嵌套、ordered list、blockquote 2 层嵌套、code block with language、table 3x3、KaTeX inline + block）→ 手机端全部正确渲染，无崩溃
- [ ] **长代码块**：发送 200 行代码 → 行号正确显示到 200，可滚动浏览
- [ ] **宽表格**：发送 8 列表格 → 手机端可水平滚动，不截断
- [ ] **空内容边界**：发送空消息 / 仅空白消息 → 不崩溃，显示空气泡

### 9.11 Visual Regression (手动)

- [ ] 截图对比：改动前后的 Markdown 综合渲染页面（包含所有元素类型）
- [ ] Light / Dark 模式双截图
- [ ] 与 Obsidian 同内容渲染对比截图
