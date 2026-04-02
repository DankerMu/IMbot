# Proposal: 工具调用富显示（Tool Call Rich Display）

## Problem

当前 `ToolCallCard` 对所有工具类型（Bash、Read、Write、Grep、Glob 等）一视同仁地渲染为 "参数 + 结果" 两段纯文本 monospace。用户无法一眼看出 Claude Code 正在执行什么命令、编辑了什么文件、搜索了什么内容。

对比 CodePilot 桌面端：
- Bash 命令用黑底终端样式渲染，绿色 `$ ` 提示符 + 灰色输出
- Read/Write 操作显示文件路径 + 语法高亮代码块
- 不同工具类型用不同图标 + 颜色区分
- 左侧 2px 状态色条表示运行中/成功/失败
- 三层可折叠：摘要 → 列表 → 详情

## Design Direction

参考 CodePilot 的工具分类渲染模型，适配到 Android Compose：

1. **分类渲染**：按 `toolName` 匹配到 5 个视觉类别
2. **终端样式**：Bash 工具用深色终端背景 + 命令/输出分区
3. **文件操作样式**：Read/Write 显示文件路径 + 代码块（复用已有 `CodeBlock.kt`）
4. **状态指示器**：左侧色条 + 图标表示工具执行状态
5. **渐进式披露**：默认折叠为一行摘要，点击展开详情

## Scope

仅改动 Android 端 UI 层（`ToolCallCard.kt` 及相关组件）。不改动 relay/companion/wire 协议。

## Success Criteria

- 5 种工具类别有各自独立的视觉呈现
- Bash 命令用终端样式渲染（黑底、绿色提示符、灰色输出）
- Read/Write 操作显示文件路径 + 语法高亮代码块
- 工具卡片有清晰的运行中/完成/失败状态指示
- 折叠状态下一行摘要能传达核心信息（工具名 + 文件路径/命令）
- 无功能回归
