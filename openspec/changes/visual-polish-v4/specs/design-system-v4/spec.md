## ADDED Requirements

---

### Requirement: Indigo brand color palette

系统 SHALL 使用靛蓝色 `#4F46E5` 作为品牌主色（`BrandBlue`），替代原 Apple Blue `#0071E3`。品牌浅色变体 `BrandBlueLight` SHALL 为 `#EEF2FF`。

#### Scenario: Primary color renders as indigo
- **WHEN** 任意使用 `MaterialTheme.colorScheme.primary` 的组件渲染（FAB、发送按钮、选中 tab、链接文字等）
- **THEN** 显示色值 SHALL 为 `#4F46E5`

#### Scenario: Primary container renders as indigo tint
- **WHEN** 任意使用 `MaterialTheme.colorScheme.primaryContainer` 的组件渲染
- **THEN** 显示色值 SHALL 为 `#EEF2FF`

---

### Requirement: Softer light-mode background hierarchy

浅色模式 SHALL 使用以下背景层级，确保 surface 白色卡片/气泡在背景上凸显：

| Token | Color.kt 常量 | 色值 |
|-------|---------------|------|
| background | `Background` | `#F5F7F9` |
| surface | `SurfaceLight` | `#FFFFFF`（不变） |
| surfaceVariant | `SurfaceSecondary` | `#F1F5F9` |
| surfaceTertiary | `SurfaceTertiary` | `#F5F7F9` |

#### Scenario: Background behind session list
- **WHEN** Home 页面 Scaffold 渲染
- **THEN** 背景色 SHALL 为 `#F5F7F9`

#### Scenario: Surface variant for input fields and unselected tabs
- **WHEN** 输入框或未选中 filter tab 渲染
- **THEN** 背景色 SHALL 为 `#F1F5F9`

---

### Requirement: Near-black text instead of pure black

浅色模式文本色 SHALL 从纯黑切换到近黑灰色系：

| Token | 常量 | 旧值 | 新值 |
|-------|------|------|------|
| onSurface / onBackground | `LabelPrimary` | `#000000` | `#1E293B` |
| onSurfaceVariant | `LabelSecondary` | `#6C6C70` | `#64748B` |
| tertiary label | `LabelTertiary` | `#C7C7CC` | `#CBD5E1` |

#### Scenario: Primary text in light mode
- **WHEN** 正文文本在浅色模式渲染
- **THEN** 文本颜色 SHALL 为 `#1E293B`

#### Scenario: Secondary text in light mode
- **WHEN** 时间戳、workspace 路径等次要文本渲染
- **THEN** 文本颜色 SHALL 为 `#64748B`

---

### Requirement: Indigo user message bubble

浅色模式下用户消息气泡背景 SHALL 使用品牌靛蓝色 `#4F46E5`，文字保持白色 `#FFFFFF`。

#### Scenario: User message bubble in light mode
- **WHEN** 用户消息气泡在浅色模式渲染
- **THEN** 气泡背景色 SHALL 为 `#4F46E5`
- **AND** 气泡文字颜色 SHALL 为 `#FFFFFF`

#### Scenario: User message bubble in dark mode unchanged
- **WHEN** 用户消息气泡在深色模式渲染
- **THEN** 气泡背景色 SHALL 仍为 `#E5E7EB`（`UserBubbleDark`，不变）

---

### Requirement: Dark code blocks in light mode

浅色模式下所有非 terminal 代码块 SHALL 使用深色背景，实现 VS Code Dark+ 风格。

代码块调色板：

| 元素 | 色值 |
|------|------|
| Header 背景 | `#2D2D2D` |
| Body 背景 | `#1E1E1E` |
| Border | `#3E3E42` |
| 语言标签 | `#858585` |
| 操作按钮 (copy) | `#CCCCCC` |
| 代码文本默认色 | `#D4D4D4` |

语法高亮 SHALL 始终使用 `CodeTheme.Dark` 色系（keywords `#FF7B72`, strings `#A5D6FF`, comments `#8B949E` 等）。

#### Scenario: Non-terminal code block in light mode
- **WHEN** 一个 language=`kotlin` 的代码块在浅色模式渲染
- **THEN** body 背景 SHALL 为 `#1E1E1E`
- **AND** header 背景 SHALL 为 `#2D2D2D`
- **AND** 语法关键字 SHALL 使用 `CodeTheme.Dark.keyword` 色值 (`#FF7B72`)

#### Scenario: Non-terminal code block in dark mode
- **WHEN** 一个 language=`python` 的代码块在深色模式渲染
- **THEN** 同样使用上述深色 palette（与浅色模式一致）

#### Scenario: Terminal code block unchanged
- **WHEN** 一个 language=`bash`/`shell`/`sh`/`zsh`/`terminal` 的代码块渲染
- **THEN** 仍使用 `TerminalBg` (`#0A0A0A`) + `TerminalGreen` + `TerminalText` palette，不受影响

---

### Requirement: Code block corner radius 12dp

代码块圆角 SHALL 从 8dp 增加到 12dp。

| Shape token | 旧值 | 新值 |
|-------------|------|------|
| `codeBlock` | `RoundedCornerShape(8.dp)` | `RoundedCornerShape(12.dp)` |
| `codeBlockHeader` | `RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)` | `RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)` |

#### Scenario: Code block renders with 12dp corners
- **WHEN** 代码块 Surface 渲染
- **THEN** 圆角 SHALL 为 12dp

---

### Requirement: Filter tabs without border

Home 页 provider 过滤标签在未选中态 SHALL 无边框（去除当前的 1dp outline）。

#### Scenario: Unselected filter tab
- **WHEN** 未选中的 provider filter tab 渲染
- **THEN** border SHALL 为 `null`
- **AND** 背景色 SHALL 为 `surfaceVariant`（浅色模式下为 `#F1F5F9`）
- **AND** 文字颜色 SHALL 为 `onSurfaceVariant`（浅色模式下为 `#64748B`）

#### Scenario: Selected filter tab unchanged
- **WHEN** 选中的 filter tab 渲染
- **THEN** 背景色 SHALL 为 `primary`（`#4F46E5`）
- **AND** 文字颜色 SHALL 为 `onPrimary`（白色）
- **AND** border SHALL 为 `null`

---

### Requirement: Filter tab padding aligned to 8-grid

Filter tab 内边距 SHALL 对齐 8 的倍数网格。

| 方向 | 旧值 | 新值 |
|------|------|------|
| horizontal | 14dp | 16dp |
| vertical | 9dp | 8dp |

#### Scenario: Filter tab padding
- **WHEN** filter tab 文字区域渲染
- **THEN** horizontal padding SHALL 为 16dp
- **AND** vertical padding SHALL 为 8dp

---

### Requirement: Agent bubble micro-shadow in light mode

浅色模式下 Agent 消息气泡 SHALL 使用 1dp shadowElevation 替代 0.5dp border，实现更柔和的浮起感。深色模式 SHALL 保留 border（阴影在暗色背景下不可见）。

#### Scenario: Agent bubble in light mode
- **WHEN** Agent 消息气泡在浅色模式渲染
- **THEN** `shadowElevation` SHALL 为 `1.dp`
- **AND** `border` SHALL 为 `null`

#### Scenario: Agent bubble in dark mode
- **WHEN** Agent 消息气泡在深色模式渲染
- **THEN** `border` SHALL 为 `BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.45f))`
- **AND** `shadowElevation` SHALL 为 `0.dp`

---

### Requirement: Refined table styling in light mode

浅色模式下 Markdown 表格 SHALL 使用更精致的色彩方案：

| 元素 | 旧值 | 新值 |
|------|------|------|
| 表头背景 | `surfaceVariant` (Material token) | `#F8FAFC` |
| 边框/分割线 | `outlineVariant` (Material token) | `#E2E8F0` |
| 斑马纹行 | `surfaceVariant.copy(alpha = 0.3f)` | `#F8FAFC` |
| 普通行背景 | `surface` | `surface`（不变） |

深色模式 SHALL 保持使用 Material token（不变）。

浅色模式下单元格 SHALL 仅绘制底部水平分割线（不画四边全框 border）。深色模式保持四边 border。

#### Scenario: Table header in light mode
- **WHEN** 表格表头行在浅色模式渲染
- **THEN** 背景色 SHALL 为 `#F8FAFC`

#### Scenario: Table striped row in light mode
- **WHEN** 表格奇数数据行在浅色模式渲染
- **THEN** 背景色 SHALL 为 `#F8FAFC`

#### Scenario: Table cell border in light mode
- **WHEN** 表格单元格在浅色模式渲染
- **THEN** SHALL 仅在底部绘制 1dp `#E2E8F0` 水平线
- **AND** SHALL 无四边完整 border

#### Scenario: Table in dark mode unchanged
- **WHEN** 表格在深色模式渲染
- **THEN** 表头背景 SHALL 使用 `MaterialTheme.colorScheme.surfaceVariant`
- **AND** 单元格 SHALL 使用四边 `border(1.dp, outlineVariant)`

---

### Requirement: Math block surface refinement

浅色模式下 LaTeX 数学公式块 SHALL 使用 `#F8FAFC` 背景色和 12dp 圆角（替代当前的 `surfaceVariant 0.38α` + 18dp）。深色模式保持不变。

#### Scenario: Block math in light mode
- **WHEN** `$$...$$` 数学公式块在浅色模式渲染
- **THEN** Surface 背景色 SHALL 为 `#F8FAFC`
- **AND** 圆角 SHALL 为 `12.dp`

#### Scenario: Block math in dark mode
- **WHEN** 数学公式块在深色模式渲染
- **THEN** Surface 背景色 SHALL 为 `surfaceVariant.copy(alpha = 0.38f)`（不变）
- **AND** 圆角 SHALL 为 `12.dp`

---

### Requirement: Input field full-opacity background

底部输入栏的 PillTextField 背景 SHALL 使用完整 `surfaceVariant` 色值，不再附加 alpha 0.5。

#### Scenario: Input field background in light mode
- **WHEN** 底部输入框渲染
- **THEN** 背景色 SHALL 为 `surfaceVariant`（`#F1F5F9`，不带 alpha 衰减）

---

### Requirement: Dark mode unaffected

除代码块外，所有深色模式色值和行为 SHALL 保持不变。

#### Scenario: Dark mode color constants
- **WHEN** 应用在深色模式下运行
- **THEN** `BackgroundDark`, `SurfaceDark`, `SurfaceSecondaryDark`, `LabelPrimaryDark`, `LabelSecondaryDark`, `UserBubbleDark`, `UserBubbleDarkText`, `AssistantBubbleDark` SHALL 保持原值不变

#### Scenario: Dark mode code blocks
- **WHEN** 代码块在深色模式渲染
- **THEN** SHALL 使用与浅色模式相同的深色 palette（`#1E1E1E` body、`#2D2D2D` header）
