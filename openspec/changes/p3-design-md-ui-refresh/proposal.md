# Proposal: Design.md UI Refresh

## Problem

当前 Android UI 仍然带有明显的“默认 Material 3 + 少量品牌色修饰”痕迹，主要问题不是单个组件丑，而是整套视觉语言不统一：

1. **主题气质不稳定**：首页、会话详情、新建流程分别像不同阶段的产物，缺少统一的色彩、层级和留白节奏。
2. **信息层级偏平**：会话卡片、筛选条、顶部栏和底部导航都过于工具化，难以建立“主信息 / 次信息 / 元数据”的清晰阅读顺序。
3. **开发者产品感不够强**：虽然是远程控制 AI agent 的开发者工具，但界面仍偏“安全的通用移动应用”，没有形成 terminal/editorial/developer-console 的辨识度。
4. **新建流程过于朴素**：步骤感和阶段感弱，像表单页而不是一个受控的会话启动流程。
5. **详情页顶栏过于普通**：provider、路径、上下文用量、状态之间没有形成清晰的高密度工具信息结构。

## Design Direction

参考 `awesome-design-md` 中 `Claude`、`Linear`、`Vercel`、`Warp`、`VoltAgent` 的共同优点，但不直接复刻网页：

- **Light**：暖纸感背景 + 象牙色 surface + 深墨色文本
- **Dark**：温润炭黑背景 + 暖灰边界 + 克制高对比
- **Accent**：单一结构化主色，用于选中态、主要 CTA、焦点和关键链接
- **Card system**：轻阴影 + 细描边的“built, not floating” 卡片，而不是默认 Material 浮片
- **Typography**：更紧凑的标题字距、更明确的 label / metadata 层级
- **Developer density**：路径、provider、状态、usage 等元信息改为更精致的 badge / pill / monospace 标签表达

## Scope

Android UI only:

- `ui/theme/` design tokens
- `HomeScreen` + `SessionCard`
- `NewSessionScreen` + Provider/Directory/Prompt steps
- `DirectoryBrowser` shared component
- `SessionDetailScreen` top shell / top bar
- Bottom navigation shell
- UI/UX spec docs alignment

不改 relay / companion 行为，不改 session 生命周期逻辑。

## Success Criteria

- Light / Dark 主题都呈现统一的暖中性色设计语言
- 首页拥有更强的视觉层级：标题区、状态摘要、筛选条、分组列表
- 会话卡片具备更强的“开发者工具”质感，路径与状态信息更易扫读
- 新建会话流程具备明显的阶段容器和步骤反馈
- 详情页顶栏能够同时自然展示 provider、路径、状态和上下文 usage
- 仍保持现有功能与行为稳定，通过 Android 单测与安装验证
