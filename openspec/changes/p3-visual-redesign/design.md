# Design: Visual Redesign — Apple Aesthetic

## Design Philosophy

参考 Apple Human Interface Guidelines 和 iOS/macOS 原生应用（Notes, Messages, Mail, Settings），在 Material 3 框架内实现 Apple 美学：

- **Clarity**：内容优先，Chrome 最小化
- **Deference**：UI 元素退后，为内容让路
- **Depth**：通过分层、模糊、动画建立空间感

## Design Token System

### Color Palette

```kotlin
// === Primary Brand ===
// 从刺眼的 Google Blue #1A73E8 切换到更柔和的品牌蓝
BrandBlue = Color(0xFF007AFF)       // iOS system blue
BrandBlueLight = Color(0xFFE3F2FF)  // tint

// === Neutral System (Light) ===
Background = Color(0xFFF2F2F7)      // iOS system grouped background
Surface = Color(0xFFFFFFFF)
SurfaceSecondary = Color(0xFFF9F9F9)
SurfaceTertiary = Color(0xFFF2F2F7)
Separator = Color(0x33000000)       // 20% black
LabelPrimary = Color(0xFF000000)
LabelSecondary = Color(0xFF8E8E93)
LabelTertiary = Color(0xFFC7C7CC)

// === Neutral System (Dark) ===
BackgroundDark = Color(0xFF000000)
SurfaceDark = Color(0xFF1C1C1E)
SurfaceSecondaryDark = Color(0xFF2C2C2E)
SurfaceTertiaryDark = Color(0xFF3A3A3C)
SeparatorDark = Color(0x33FFFFFF)
LabelPrimaryDark = Color(0xFFFFFFFF)
LabelSecondaryDark = Color(0xFF8E8E93)

// === Semantic ===
Success = Color(0xFF34C759)         // iOS green
Warning = Color(0xFFFF9500)         // iOS orange
Destructive = Color(0xFFFF3B30)     // iOS red
Info = Color(0xFF5AC8FA)            // iOS teal

// === Provider (降低饱和度) ===
ProviderClaude = Color(0xFFD4956A)  // 柔和琥珀
ProviderBook = Color(0xFF9B7ED8)    // 柔和紫
ProviderOpenClaw = Color(0xFFE07070) // 柔和红
```

### Typography

```kotlin
// San Francisco 风格排版（使用系统默认 sans-serif，Android 上为 Roboto/Google Sans）
DisplayLarge = 34sp, Regular, -0.4 tracking   // 大标题
Title1 = 28sp, Regular, 0.36 tracking         // 页面标题
Title2 = 22sp, Regular, -0.26 tracking        // 区域标题
Title3 = 20sp, Regular, -0.45 tracking
Headline = 17sp, SemiBold, -0.41 tracking     // 卡片标题
Body = 17sp, Regular, -0.41 tracking          // 正文
Callout = 16sp, Regular, -0.32 tracking       // 辅助文本
Subheadline = 15sp, Regular, -0.24 tracking
Footnote = 13sp, Regular, -0.08 tracking      // 时间戳
Caption1 = 12sp, Regular, 0 tracking          // 标签
Caption2 = 11sp, Regular, 0.07 tracking       // 次要标签
```

### Spacing Scale

```kotlin
// 4dp base scale
xxs = 2.dp
xs = 4.dp
sm = 8.dp
md = 12.dp
lg = 16.dp
xl = 20.dp
xxl = 24.dp
xxxl = 32.dp
```

### Corner Radius

```kotlin
// Apple 连续曲线风格（Compose 使用 RoundedCornerShape 近似）
RadiusSmall = 8.dp      // 小型元素（chip, badge）
RadiusMedium = 12.dp    // 输入框、代码块
RadiusLarge = 16.dp     // 卡片
RadiusXLarge = 20.dp    // Modal / BottomSheet
RadiusFull = 999.dp     // Pill 形状（按钮、搜索栏）
```

### Elevation & Shadow

```kotlin
// Apple 风格柔和阴影（非 Material 高程系统）
val AppleShadow = Modifier.shadow(
    elevation = 2.dp,
    shape = RoundedCornerShape(RadiusLarge),
    ambientColor = Color.Black.copy(alpha = 0.08f),
    spotColor = Color.Black.copy(alpha = 0.04f),
)
// 暗色模式：不使用阴影，改用 1dp border outline
```

## Page-Level Design

### Onboarding Page

**当前问题**：蓝灰色 "IM" 圆形 logo 简陋；输入框边框过粗；按钮灰色→蓝色跳跃突兀。

**改进**：
- Logo：渐变色 SF Symbols 风格图标或纯文字 "IMbot" 搭配品牌蓝色
- 输入框：去除 OutlinedTextField 边框风格，改用 filled style（`SurfaceSecondary` 背景 + `RadiusMedium` 圆角 + 无边框）
- 按钮：全宽圆角按钮，品牌蓝背景，禁用态 `LabelTertiary` 文字 + `SurfaceTertiary` 背景
- 连接成功反馈：保持绿色 ✓ 但使用 `Success` 色值
- 整体布局：垂直居中，最大宽度 360dp，两侧 padding 24dp

### Home / Session List

**当前问题**：SessionCard 扁平无层次；badge 颜色过饱和；空态无情感设计。

**改进**：
- SessionCard：
  - 白色 Surface 背景 + Apple 柔和阴影
  - 圆角 `RadiusLarge` (16dp)
  - Provider 头像：缩小至 36dp，背景色使用降饱和 provider 色
  - 标题行：provider name (Headline) + 状态点 (8dp, semantic color)
  - 副标题：workspace path (Subheadline, LabelSecondary)
  - 时间戳：右上角 (Footnote, LabelSecondary)
  - Prompt 预览：最多 2 行 (Body, LabelSecondary)
  - 内边距：16dp 全方向
- 顶部栏：大标题风格 "会话"（Title1），provider 过滤器改为 horizontal chip row
- FAB：品牌蓝 + 白色图标 + 阴影
- 空态：温暖的插画 + "开始你的第一次对话" 提示文字
- Pull-to-refresh：系统默认即可

### Session Detail

**当前问题**：顶栏拥挤；状态气泡视觉噪音大；消息气泡颜色单调；输入栏默认样式。

**改进**：
- 顶栏：
  - 精简为：← 返回 | Provider 小图标 + 标题 | 状态点 | ⋯ 菜单
  - "空间" 按钮移入菜单
  - 背景：Surface + 0.5dp 底部分隔线
- 状态气泡：
  - 大幅缩小视觉权重：11sp Caption 字号，`LabelTertiary` 颜色
  - 连续相同状态合并为一个（如连续 "运行中" 只显示一次）
  - 使用 inline chip 样式而非独立行
- 消息气泡：见 `p3-message-rendering-polish` change
- 输入栏：
  - Filled style，`SurfaceSecondary` 背景，`RadiusFull` 圆角（pill 形状）
  - 发送按钮：圆形品牌蓝，白色向上箭头图标（类似 iMessage）
  - 高度自适应（1-4 行）
- 回到底部 FAB：
  - 缩小为 36dp 圆形
  - 半透明白色背景 + 灰色向下箭头
  - 未读计数 badge（如有）

### Workspace / Directory Browser

**改进**：
- 列表项使用 iOS Settings 风格：左侧图标 + 标题 + 副标题 + 右侧 chevron
- 分组使用圆角白色容器 + 灰色背景分隔
- Host 状态标签使用 semantic 色彩的小圆点

### Settings

**改进**：
- iOS Settings 风格分组列表
- 每组：白色圆角容器内包含多行，行间 0.5dp 分隔线（左侧 indent 不满宽）
- 灰色分组背景
- 开关使用系统 Switch 样式

### New Session Flow

**改进**：
- Step indicator：顶部 3 个圆点连线（active = 品牌蓝 + filled, inactive = LabelTertiary + outline）
- Provider picker：三张大卡片（竖向排列），每张含 provider 图标 + 名称 + 描述
- Directory browser：保持现有列表，应用 iOS list 样式
- Prompt input：大文本区域，placeholder "说点什么开始..."

## Animation System

```kotlin
// Spring-based animations (replacing LinearEasing / FastOutSlowIn)
val DefaultSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,  // 0.5
    stiffness = Spring.StiffnessMedium,               // 400
)

val GentleSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,       // 1.0
    stiffness = Spring.StiffnessLow,                  // 200
)

// Page transitions: shared element + fade
// Bottom sheet: slide up with spring
// Card press: scale(0.98f) feedback
// Status change: crossfade 200ms
```

## File Changes

| File | Change |
|------|--------|
| `ui/theme/Color.kt` | 全新调色板 |
| `ui/theme/Type.kt` | Apple 风格排版 |
| `ui/theme/Shape.kt` | 更大圆角 |
| `ui/theme/IMbotTheme.kt` | 整合新 token，自定义 CompositionLocal |
| `ui/theme/Animation.kt` | Spring 动画曲线 |
| `ui/onboarding/OnboardingScreen.kt` | 视觉重设计 |
| `ui/home/HomeScreen.kt` | 大标题、chip filter |
| `ui/home/SessionCard.kt` | Apple 风格卡片 |
| `ui/detail/SessionDetailScreen.kt` | 顶栏精简、状态气泡减噪 |
| `ui/detail/InputBar.kt` | Pill 输入栏 + iMessage 发送按钮 |
| `ui/detail/MessageBubble.kt` | 气泡样式更新 |
| `ui/workspace/WorkspaceScreen.kt` | iOS list 样式 |
| `ui/settings/SettingsScreen.kt` | iOS Settings 风格 |
| `ui/newsession/NewSessionScreen.kt` | Step indicator + 卡片式 provider picker |
| `ui/navigation/AppNavigation.kt` | 底部栏样式微调 |

## Dependencies

- 与 `p3-message-rendering-polish` 共享气泡样式——visual-redesign 定义 token，rendering-polish 使用 token 细化内容排版
- 与 `p3-message-copy` 独立——copy 是交互行为，不影响视觉

## Constraints

- 不改变导航结构或数据流
- Material 3 框架内实现 Apple 风格（不引入第三方 UI 库）
- 所有改动必须同时适配 Light / Dark 模式
- 动画必须支持 Reduce Motion accessibility 设置
