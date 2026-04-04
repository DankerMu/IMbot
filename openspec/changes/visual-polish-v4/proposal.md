## Why

p3-visual-redesign 建立了 Apple 美学基础（spring 动画、圆角系统、阴影 token），但 Gemini 美学审查指出仍存在 **Design System 不严格** 的问题：间距不统一（出现 9dp/14dp 等非 8 倍数值）、色彩层级不清晰（纯黑文本、偏冷灰背景与白色卡片缺乏对比）、代码块在浅色模式下与聊天背景融为一体缺乏专业感、表格边框过粗。当前距离 ChatGPT/Claude 级商业 AI 应用的视觉标准仍有差距。

## What Changes

### 1. 全局色彩调色板升级
- 品牌主色从 Apple Blue `#0071E3` 切换到靛蓝 `#4F46E5`，更稳重
- 背景色 `#F2F2F7` → `#F5F7F9`，让白色卡片/气泡凸显层级
- 文本主色 `#000000` → `#1E293B`（近黑，更护眼）
- 文本次色 `#6C6C70` → `#64748B`
- 用户气泡从深灰 `#1F2937` 改为品牌靛蓝 `#4F46E5`（与 iMessage 风格一致）
- SurfaceSecondary `#F9F9F9` → `#F1F5F9`（用于输入框、未选中 tab）

### 2. 代码块深色主题统一
- **BREAKING**: 浅色模式下所有代码块（非 terminal）改为深色背景 `#1E1E1E`
- Header 栏 `#2D2D2D`，语言标签灰色，复制图标浅灰
- 始终使用 Dark CodeTheme 语法高亮配色
- 代码块圆角 8dp → 12dp

### 3. 过滤标签 (Filter Tabs) 精简
- 移除未选中态的 1dp outline 边框
- 间距对齐 8 倍数网格（padding 14/9 → 16/8）

### 4. 表格渲染优化
- 表头背景 `#F8FAFC`，边框 `#E2E8F0`（替代 outlineVariant）
- 斑马纹行 `#F8FAFC`（替代 surfaceVariant 0.3α）
- 视觉上更轻量

### 5. 数学公式块改进
- 背景色 `#F8FAFC`（替代 surfaceVariant 0.38α），圆角 18dp → 12dp
- 更清晰的视觉分区

### 6. Agent 气泡微阴影
- 浅色模式下：移除 0.5dp border，改为 1dp shadowElevation
- 深色模式下：保持 border（阴影在暗色下不可见）

### 7. 输入框背景修正
- 输入框背景 alpha 0.5 → 1.0（使用完整 surfaceVariant 色值 `#F1F5F9`）

## Capabilities

### New Capabilities
- `design-system-v4`: 全局色彩调色板、代码块深色主题、表格/数学块样式升级、间距对齐

### Modified Capabilities
_(无现有 spec 需要修改 — specs/ 目录当前为空)_

## Impact

### 受影响文件（Android 前端，共 8 个 Kotlin 文件）

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `ui/theme/Color.kt` | 常量修改 | ~10 个色值更新 |
| `ui/theme/Shape.kt` | 常量修改 | codeBlock 圆角 8→12 |
| `ui/components/CodeBlock.kt` | 逻辑修改 | codeBlockPalette 改为始终深色 + 使用 CodeTheme.Dark |
| `ui/home/HomeScreen.kt` | 样式修改 | Filter tabs 移除 border、padding 对齐 |
| `ui/detail/MessageBubble.kt` | 样式修改 | Agent 气泡 border→shadow |
| `ui/detail/MarkdownText.kt` | 样式修改 | 表格色值 |
| `ui/detail/MarkdownKatex.kt` | 样式修改 | 数学块 surface/shape |
| `ui/detail/InputBar.kt` | 样式修改 | 输入框背景 alpha |

### 不影响
- 功能逻辑、导航结构、数据流、网络层
- 深色模式（除代码块外，其余改动仅影响浅色模式色值）
- 测试（纯视觉改动，现有 unit/contract 测试不涉及色值断言）

### 风险
- 代码块深色背景在用户气泡内可能出现视觉冲突（用户气泡也是深色靛蓝）→ 需实际验证
- 品牌色从蓝切换到靛蓝，全局所有使用 `primary` 的组件同步变化 → 覆盖面广但行为一致
