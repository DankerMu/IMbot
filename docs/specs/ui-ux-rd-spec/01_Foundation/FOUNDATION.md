# Foundation — 设计基座

## Color System

采用 Material 3 Dynamic Color + 自定义 seed color。

### Seed Colors

| Role | Light | Dark | Usage |
|------|-------|------|-------|
| Primary | `#1A73E8` (blue) | `#8AB4F8` | 主操作按钮、FAB、选中态 |
| Secondary | `#34A853` (green) | `#81C995` | running 状态、成功态 |
| Error | `#EA4335` (red) | `#F28B82` | failed 状态、错误提示 |
| Surface | `#FAFAFA` | `#1E1E1E` | 卡片、底部栏 |
| Background | `#FFFFFF` | `#121212` | 页面背景 |

### Provider Colors

| Provider | Color | Icon |
|----------|-------|------|
| Claude Code | `#D97706` (amber) | 自定义 Claude logo |
| book | `#7C3AED` (violet) | 书本图标 |
| OpenClaw | `#DC2626` (red) | 龙虾图标 🦞 |

### Status Colors

| Status | Light | Dark | Animation |
|--------|-------|------|-----------|
| `queued` | `#9CA3AF` (gray) | `#6B7280` | 无 |
| `running` | `#10B981` (green) | `#34D399` | 脉冲 (pulse) |
| `completed` | `#059669` (teal) | `#6EE7B7` | 无 |
| `failed` | `#EF4444` (red) | `#FCA5A5` | 无 |
| `cancelled` | `#6B7280` (gray) | `#9CA3AF` | 无 |

## Typography

Material 3 默认 type scale，额外约束：

| Use Case | Style | Size | Weight |
|----------|-------|------|--------|
| Session card title | `titleMedium` | 16sp | 500 |
| Session card subtitle | `bodySmall` | 12sp | 400 |
| Message text | `bodyLarge` | 16sp | 400 |
| Code block | `JetBrains Mono` / `Fira Code` | 14sp | 400 |
| Timestamp | `labelSmall` | 11sp | 400 |
| Section header | `titleSmall` | 14sp | 500 |

## Spacing

Material 3 4dp grid：

| Token | Value | Usage |
|-------|-------|-------|
| `spacing.xs` | 4dp | 图标内间距 |
| `spacing.sm` | 8dp | 卡片内 padding |
| `spacing.md` | 16dp | 页面水平 padding、卡片间距 |
| `spacing.lg` | 24dp | section 间距 |
| `spacing.xl` | 32dp | 页面顶部 padding |

## Corner Radius

| Element | Radius |
|---------|--------|
| Card | 12dp |
| Chip / Badge | 8dp |
| Button | 20dp (rounded) |
| Bottom Sheet | 28dp (top) |
| Dialog | 28dp |
| Input field | 12dp |
| Code block | 8dp |

## Elevation

| Element | Elevation |
|---------|-----------|
| FAB | 6dp |
| Card (resting) | 1dp |
| Card (pressed) | 2dp |
| Bottom nav | 3dp |
| Top app bar (scrolled) | 2dp |
| Dialog / Bottom sheet | 6dp |

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
