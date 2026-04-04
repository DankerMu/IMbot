# Foundation — 设计基座

## Visual Direction

参考 `Claude / Linear / Vercel / Warp / VoltAgent` 的共同优点，形成“暖中性色 + 精确层级 + 开发者工具密度”的移动端设计语言。

- Light：暖纸感背景、象牙色 surface、深墨色文本
- Dark：温润炭黑背景、暖灰边界、克制高对比
- Accent：单一结构化主色，只用于 CTA、选中态、焦点、链接
- Meta：路径、provider、状态、usage 以 pill / badge / monospace 元信息表达

## Color System

采用固定设计 token，保证不同设备上的视觉一致性。

### Theme Tokens

| Role | Light | Dark | Usage |
|------|-------|------|-------|
| Primary | `#5C66D6` | `#5C66D6` | 主操作按钮、选中态、主链接 |
| Primary Container | `#E8EBFF` | `#2D3159` | 选中容器、激活状态背景 |
| Secondary | `#1D8C66` | `#30B789` | running / success |
| Error | `#BF4A3F` | `#E17B72` | failed / destructive |
| Surface | `#FCFAF5` | `#181614` | 卡片、顶部/底部壳层 |
| Surface Variant | `#F1ECE2` | `#1F1C19` | 输入区、未选中 pills |
| Background | `#F3EFE7` | `#100F0D` | 页面背景 |
| Outline | `#D8D0C4` | `#3E392F` | 容器边界、分隔线 |

### Provider Colors

| Provider | Color | Icon |
|----------|-------|------|
| Claude Code | `#C58C68` | 自定义 Claude logo / monogram |
| book | `#8E7AD9` | 书本图标 / monogram |
| OpenClaw | `#DA7268` | OpenClaw 图标 / monogram |

### Status Colors

| Status | Light | Dark | Animation |
|--------|-------|------|-----------|
| `queued` | `#9B9488` | `#7C756A` | 无 |
| `running` | `#1D8C66` | `#30B789` | 脉冲 (pulse) |
| `completed` | `#147353` | `#65D1AA` | 无 |
| `failed` | `#BF4A3F` | `#E17B72` | 无 |
| `cancelled` | `#7A7369` | `#A59C90` | 无 |

## Typography

使用 Inter + JetBrains Mono，但标题收紧字距，元信息层级更明确。

| Use Case | Style | Size | Weight |
|----------|-------|------|--------|
| Hero / screen title | `headlineLarge` | 32sp | 600 |
| Section title | `headlineMedium` / `titleLarge` | 18-24sp | 600 |
| Session card title | `titleLarge` | 18sp | 600 |
| Message text | `bodyLarge` | 15sp | 400 |
| Path / code / cwd | `JetBrains Mono` | 12-13sp | 400 |
| Timestamp / status meta | `labelMedium` / `labelSmall` | 10-11sp | 500 |
| Eyebrow / overline | `labelSmall` | 10sp | 500 |

## Spacing

以 8dp 为主节奏，允许 4dp 微调，不再使用随意的 9/14dp 非系统值。

| Token | Value | Usage |
|-------|-------|-------|
| `spacing.xs` | 4dp | 微调、图标内间距 |
| `spacing.sm` | 8dp | pill / chip 内部间距 |
| `spacing.md` | 12dp | 组件内部垂直节奏 |
| `spacing.lg` | 16dp | 卡片内 padding、行间距 |
| `spacing.xl` | 20dp | 页面水平 padding |
| `spacing.xxl` | 24dp | section 间距 |
| `spacing.xxxl` | 32dp | 页面顶部与大 section 留白 |

## Corner Radius

| Element | Radius |
|---------|--------|
| Card | 20dp |
| Chip / Badge | 14dp |
| Button | 16dp |
| Bottom Sheet | 28dp (top) |
| Dialog | 24dp |
| Input field | 18dp |
| Code block | 14dp |

## Elevation

| Element | Elevation |
|---------|-----------|
| Flat | 无阴影，仅边界 | 深色模式容器 / 普通分组 |
| Soft card | 轻阴影 + 细描边 | 浅色模式主要卡片 |
| Elevated shell | 更明显阴影 + 细描边 | FAB / 底部导航 / 顶部阶段容器 |
| Dialog / Bottom sheet | 高于普通容器 | 浮层与确认弹窗 |

## Animation Tokens

| Animation | Duration | Easing | Trigger |
|-----------|----------|--------|---------|
| Page enter | 300ms | `EmphasizedDecelerate` | Navigation forward |
| Page exit | 250ms | `EmphasizedAccelerate` | Navigation back |
| Shared element | 400ms | `Emphasized` | Session card → detail |
| Fade in (message) | 200ms | `Standard` | New message arrives |
| Slide up (message) | 200ms | `Standard` | New message arrives |
| Pulse (running) | 1500ms loop | `Linear` | Status == running |
| Color morph | 300ms | `Standard` | Status change |
| Theme switch | 400ms | cross-fade | Theme toggle |
| Swipe dismiss | 200ms | `Standard` | Left swipe on card |

## Icon System

Material Symbols Rounded（Material 3 默认）+ 自定义 Provider 图标。

| Concept | Icon |
|---------|------|
| New session | `add` |
| Session running | `play_circle` (animated) |
| Session completed | `check_circle` |
| Session failed | `error` |
| Session cancelled | `cancel` |
| Settings | `settings` |
| Workspace | `folder` |
| Directory | `folder_open` |
| Back | `arrow_back` |
| Send message | `send` |
| Cancel session | `stop_circle` |
| Theme | `dark_mode` / `light_mode` |
| Connection OK | `cloud_done` |
| Connection lost | `cloud_off` |

## Code Syntax Highlighting

### Light Theme Palette

| Token | Color | Usage |
|-------|-------|-------|
| keyword | `#D73A49` | `if`, `return`, `function` |
| string | `#032F62` | string literals |
| comment | `#6A737D` | comments |
| number | `#005CC5` | numeric literals |
| type | `#6F42C1` | class/type names |
| function | `#6F42C1` | function names |
| background | `#F6F8FA` | code block bg |

### Dark Theme Palette

| Token | Color | Usage |
|-------|-------|-------|
| keyword | `#FF7B72` | |
| string | `#A5D6FF` | |
| comment | `#8B949E` | |
| number | `#79C0FF` | |
| type | `#D2A8FF` | |
| function | `#D2A8FF` | |
| background | `#161B22` | code block bg |
