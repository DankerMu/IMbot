# Tasks: Visual Redesign — Apple Aesthetic

## 1. Design Token System

### 1.1 Color Palette
- [ ] 重写 `ui/theme/Color.kt`：Apple iOS 风格调色板
- [ ] Primary brand blue: `#007AFF`（替代 Google Blue `#1A73E8`）
- [ ] Neutral system: Background `#F2F2F7`, Surface `#FFFFFF`, Separator `20% black`
- [ ] Semantic: Success `#34C759`, Warning `#FF9500`, Destructive `#FF3B30`
- [ ] Provider 降饱和：Claude `#D4956A`, Book `#9B7ED8`, OpenClaw `#E07070`
- [ ] 完整 Dark mode 色值

### 1.2 Typography
- [ ] 重写 `ui/theme/Type.kt`：Apple SF Pro 风格字号/字重/字间距体系
- [ ] Display 34sp, Title1 28sp, Title2 22sp, Headline 17sp SemiBold, Body 17sp, Footnote 13sp, Caption 11-12sp
- [ ] 负 letter-spacing 值模拟 SF Pro 紧凑感

### 1.3 Shape & Radius
- [ ] 更新 `ui/theme/Shape.kt`：RadiusSmall 8dp, Medium 12dp, Large 16dp, XLarge 20dp, Full 999dp
- [ ] 卡片统一使用 RadiusLarge (16dp)

### 1.4 Elevation & Shadow
- [ ] 定义 Apple 柔和阴影 Modifier 扩展（ambientColor 8% black, spotColor 4% black）
- [ ] Dark 模式：不使用阴影，改用 1dp border outline

### 1.5 Animation Curves
- [ ] 更新 `ui/theme/Animation.kt`：spring-based 动画替代线性/ease
- [ ] DefaultSpring: dampingRatio 0.5, stiffness 400
- [ ] GentleSpring: dampingRatio 1.0, stiffness 200
- [ ] 页面过渡使用 spring + fade 组合

### 1.6 IMbotTheme 整合
- [ ] 更新 `ui/theme/IMbotTheme.kt`：整合所有新 token
- [ ] 自定义 CompositionLocal 提供 spacing, radius, shadow 扩展
- [ ] 确保 Light/Dark 主题切换正确

## 2. Onboarding Page

### 2.1 Logo & Branding
- [ ] 重设计 logo：渐变品牌蓝 + 简洁图标（或纯文字 "IMbot"）
- [ ] 副标题 "连接你的 Relay 并开始使用" 保留，调整为 Subheadline 样式

### 2.2 Input Fields
- [ ] Relay URL / Token 输入框：Filled style（SurfaceSecondary 背景 + RadiusMedium 圆角 + 无边框）
- [ ] Focus 状态：底部 2dp 品牌蓝线
- [ ] 标签文字：Caption1, LabelSecondary

### 2.3 Buttons
- [ ] "测试连接" 按钮：全宽，RadiusFull 圆角，品牌蓝背景
- [ ] 禁用态：LabelTertiary 文字 + SurfaceTertiary 背景
- [ ] "开始使用" 按钮：同样式

### 2.4 Status Feedback
- [ ] 连接成功：Success 绿色 ✓ + host 列表
- [ ] 连接失败：Destructive 红色 ✕ + 错误信息
- [ ] Loading 状态：圆形 progress indicator

### 2.5 Layout
- [ ] 垂直居中，最大宽度 360dp，两侧 padding 24dp
- [ ] 元素间距按 spacing scale 调整

## 3. Home / Session List

### 3.1 Top Bar
- [ ] 大标题风格 "会话"（Title1, 28sp）
- [ ] 右侧仅保留一个 filter icon / dropdown
- [ ] 滚动时标题缩小到 inline（类似 iOS large title collapsing）

### 3.2 Provider Filter
- [ ] 水平 scrollable chip row（替代 dropdown）
- [ ] Chip: RadiusFull 圆角，选中态 = 品牌蓝 filled，未选中 = SurfaceSecondary outline
- [ ] "全部" / "Claude Code" / "book" / "OpenClaw"

### 3.3 SessionCard Redesign
- [ ] 白色 Surface 背景 + Apple 柔和阴影
- [ ] RadiusLarge (16dp) 圆角
- [ ] Provider 头像缩小至 36dp，降饱和背景色
- [ ] 标题行：provider name (Headline) + 状态小圆点 (8dp)
- [ ] 副标题：workspace path (Subheadline, LabelSecondary)
- [ ] 时间戳：右上角 (Footnote, LabelSecondary)
- [ ] Prompt 预览：最多 2 行 (Body, LabelSecondary)
- [ ] 内边距：16dp

### 3.4 FAB
- [ ] 品牌蓝圆形 + 白色 "+" 图标
- [ ] Apple 柔和阴影
- [ ] 按压缩放动画 scale(0.95f)

### 3.5 Empty State
- [ ] 居中温暖插画（或简洁图标）
- [ ] "开始你的第一次对话" 提示文字
- [ ] 下方品牌蓝 "新建会话" 按钮

### 3.6 Bottom Navigation Bar
- [ ] 半透明背景（模拟毛玻璃，如 Surface.copy(alpha=0.9f)）
- [ ] 图标线条变细（Material Symbols outlined）
- [ ] 选中态：品牌蓝填充，未选中：LabelSecondary
- [ ] 标签字号 Caption2 (11sp)

## 4. Session Detail Page

### 4.1 Top Bar
- [ ] 精简：← 返回 | Provider 小图标 (24dp) + session 标题 | 状态点 (8dp) | ⋯ 菜单
- [ ] "空间" 按钮移入 ⋯ 菜单
- [ ] 背景：Surface + 0.5dp 底部分隔线（非阴影）

### 4.2 Status Bubbles
- [ ] 字号 11sp Caption
- [ ] 颜色使用 LabelTertiary（降低视觉权重）
- [ ] Pill 形状（RadiusFull）
- [ ] 连续相同状态合并显示

### 4.3 Input Bar
- [ ] Pill 形状（RadiusFull）
- [ ] Filled style：SurfaceSecondary 背景
- [ ] 发送按钮：圆形品牌蓝，白色向上箭头图标（iMessage 风格）
- [ ] 高度自适应 1-4 行

### 4.4 Scroll-to-Bottom FAB
- [ ] 缩小为 36dp 圆形
- [ ] 半透明白色背景 + LabelSecondary 向下箭头
- [ ] 未读计数 badge（品牌蓝背景 + 白色数字）

### 4.5 Message Bubbles
- [ ] 见 `p3-message-rendering-polish` change 负责气泡内容排版
- [ ] 本 change 负责：bubble 外层 padding、间距、时间戳样式

## 5. Workspace Screen

### 5.1 iOS List 风格
- [ ] 白色圆角容器（RadiusLarge），灰色 grouped background
- [ ] 行间 0.5dp 分隔线（左侧 56dp indent，不满宽）
- [ ] 每行：左侧图标 (28dp) + 标题 + 副标题 + 右侧 chevron (>)

### 5.2 Host Status
- [ ] 小圆点 (8dp) semantic 色彩：online=Success, offline=LabelTertiary

### 5.3 Root Detail
- [ ] 同样 iOS list 样式
- [ ] Session 列表复用 SessionCard 组件

## 6. Settings Screen

### 6.1 iOS Settings 风格
- [ ] 分组列表：白色圆角容器 + 灰色背景
- [ ] 行间分隔线（左侧 indent）
- [ ] 开关使用系统 Switch
- [ ] 版本信息底部居中 (Footnote)

## 7. New Session Flow

### 7.1 Step Indicator
- [ ] 顶部 3 圆点 + 连线
- [ ] Active: 品牌蓝 filled, inactive: LabelTertiary outline

### 7.2 Provider Picker
- [ ] 三张竖向大卡片
- [ ] 每张：Provider 图标 + 名称 + 一行描述
- [ ] 选中态：品牌蓝边框 + 浅蓝背景
- [ ] RadiusLarge 圆角

### 7.3 Directory Browser
- [ ] 复用 iOS list 样式
- [ ] 面包屑导航或返回箭头

### 7.4 Prompt Input
- [ ] 大文本区域（至少 4 行可见）
- [ ] Placeholder "说点什么开始..."
- [ ] 底部 "创建会话" 按钮

## 8. Dark Mode

- [ ] 所有页面 Dark 模式验证
- [ ] 阴影 → border outline 切换
- [ ] 分隔线颜色适配
- [ ] Provider 色 Dark 模式微调

## 9. Accessibility

- [ ] 所有按钮/图标有 contentDescription
- [ ] 对比度满足 WCAG AA（4.5:1 正文, 3:1 大文本）
- [ ] Reduce Motion 设置：禁用 spring 弹性，改用 tween 300ms
- [ ] 字号尊重系统无障碍设置（sp 单位已满足）

## 10. Tests

> **测试环境**：使用 book CLI 作为 provider 进行端到端功能回归验证。所有 UI 测试和手动验证均通过 book session 触发。

### 10.1 Color Token — Unit Tests

**Light Mode**：
- [ ] colorScheme.primary == Color(0xFF007AFF)
- [ ] colorScheme.background == Color(0xFFF2F2F7)
- [ ] colorScheme.surface == Color(0xFFFFFFFF)
- [ ] colorScheme.error == Color(0xFFFF3B30)
- [ ] statusColors.running == Color(0xFF10B981) 保持不变
- [ ] statusColors.failed == Color(0xFFEF4444) 保持不变

**Dark Mode**：
- [ ] colorScheme.background == Color(0xFF000000)
- [ ] colorScheme.surface == Color(0xFF1C1C1E)
- [ ] colorScheme.onSurface == Color(0xFFFFFFFF)

**Provider Colors**：
- [ ] providerColors.claude == Color(0xFFD4956A)
- [ ] providerColors.book == Color(0xFF9B7ED8)
- [ ] providerColors.openclaw == Color(0xFFE07070)

**Semantic Colors**：
- [ ] Success == Color(0xFF34C759)
- [ ] Warning == Color(0xFFFF9500)
- [ ] Destructive == Color(0xFFFF3B30)

### 10.2 Typography Token — Unit Tests

- [ ] Headline: fontSize == 17.sp, fontWeight == SemiBold
- [ ] Body: fontSize == 17.sp, fontWeight == Normal
- [ ] Title1: fontSize == 28.sp, fontWeight == Regular
- [ ] Footnote: fontSize == 13.sp
- [ ] Caption1: fontSize == 12.sp
- [ ] Caption2: fontSize == 11.sp
- [ ] Headline letterSpacing < 0.sp（负 tracking）

### 10.3 Shape Token — Unit Tests

- [ ] shapes.small == RoundedCornerShape(8.dp)
- [ ] shapes.medium == RoundedCornerShape(12.dp)
- [ ] shapes.large == RoundedCornerShape(16.dp)

### 10.4 Animation Token — Unit Tests

- [ ] DefaultSpring: dampingRatio == 0.5f, stiffness == 400f
- [ ] GentleSpring: dampingRatio == 1.0f, stiffness == 200f

### 10.5 Shadow & Elevation — Unit Tests

- [ ] Light mode: appleShadow elevation == 2.dp
- [ ] Light mode: ambientColor alpha == 0.08f
- [ ] Light mode: spotColor alpha == 0.04f
- [ ] Dark mode: elevation == 0.dp（无阴影）
- [ ] Dark mode: border width == 1.dp

### 10.6 功能回归 — Unit Tests

**导航逻辑不变**：
- [ ] resolveStartDestination(relayUrl="", token="") → AppRoute.ONBOARDING
- [ ] resolveStartDestination(relayUrl="http://x", token="abc") → AppRoute.HOME
- [ ] onboardingCompletionNavigation() 返回 route=HOME, popUpTo=ONBOARDING, inclusive=true

**Detail 工具函数不变**：
- [ ] canSendToSession("running") → true
- [ ] canSendToSession("idle") → true
- [ ] canSendToSession("completed") → false
- [ ] canInputToSession("idle") → true
- [ ] canInputToSession("running") → false
- [ ] canResumeSession("completed") → true
- [ ] canResumeSession("running") → false
- [ ] canCancelSession("running") → true
- [ ] inputPlaceholderForStatus("running") → "AI 正在回复..."
- [ ] inputPlaceholderForStatus("idle") → "继续对话..."

**Provider 工具函数不变**：
- [ ] providerDisplayName("claude") → "Claude Code"
- [ ] providerDisplayName("book") → "book"
- [ ] providerShortLabel("claude") → "CC"
- [ ] providerShortLabel("book") → "BK"
- [ ] statusLabel("running") → "运行中"
- [ ] sessionTitle(session with provider="book") → "book"

**Home 过滤逻辑不变**：
- [ ] provider filter "all" → 显示所有 sessions
- [ ] provider filter "book" → 仅显示 provider=="book" 的 sessions
- [ ] 空列表 → 显示 empty state

### 10.7 Accessibility — Unit Tests

- [ ] WCAG AA 对比度：primary (#007AFF) on background (#F2F2F7) → ratio >= 4.5:1
- [ ] WCAG AA 对比度：onSurface on surface → ratio >= 4.5:1
- [ ] WCAG AA 对比度：LabelSecondary (#8E8E93) on surface (#FFFFFF) → ratio >= 3:1（大文本）
- [ ] Reduce Motion: isReduceMotionEnabled == true → 使用 tween(300) 替代 spring

### 10.8 Dark Mode 切换 — Unit Tests

- [ ] Light → Dark：所有 CompositionLocal 色值正确切换
- [ ] Dark → Light：同上
- [ ] Shadow → border outline 切换：Dark mode 下 elevation == 0, border visible
- [ ] Separator 颜色：Light = 20% black, Dark = 20% white

### 10.9 Integration Tests (book CLI)

**Onboarding 流程完整性**：
- [ ] 输入 relay URL + token → "测试连接" → 显示 host 状态（MacBook: online）→ "开始使用" → 导航到 Home
- [ ] 输入无效 URL → "测试连接" → 显示错误信息（红色 ✕）→ "开始使用" 按钮不出现
- [ ] 空 URL → "测试连接" 按钮 disabled

**Home 功能完整性**：
- [ ] SessionCard 点击 → 导航到 SessionDetail（book session）
- [ ] SessionCard 长按 → 出现 Archive/Delete 选项
- [ ] SessionCard swipe-to-delete → 确认弹窗 → 删除 → session 消失
- [ ] FAB 点击 → 导航到 NewSession
- [ ] Provider chip filter 选择 "book" → 仅显示 book sessions
- [ ] Pull-to-refresh → 刷新 session 列表
- [ ] 空列表 → 显示 empty state + "新建会话" 按钮

**Session Detail 功能完整性**：
- [ ] 发送消息 → 消息出现在时间线中
- [ ] book agent 回复 → 流式渲染正确
- [ ] 输入栏 placeholder 随状态变化正确（running → "AI 正在回复...", idle → "继续对话..."）
- [ ] 状态气泡渲染正确（运行中/空闲/已完成）
- [ ] ⋯ 菜单 → 包含 "空间" / "复制全部输出" 等选项
- [ ] 返回按钮 → 返回 Home

**Bottom Navigation**：
- [ ] 三 tab 切换（会话 / 目录 / 设置）功能不变
- [ ] 选中态颜色为品牌蓝
- [ ] 运行中 session 时 Home tab 显示 badge

**New Session 流程**：
- [ ] Step 1 选择 book provider → Step 2 选择目录 → Step 3 输入 prompt → 创建成功 → 导航到 Detail

**Workspace**：
- [ ] 显示 root 列表
- [ ] 点击 root → 显示该 root 下的 sessions

**Settings**：
- [ ] 各设置项可正常切换
- [ ] 版本号正确显示

### 10.10 Visual Regression (手动)

- [ ] 6 个页面 Light 模式截图（Onboarding / Home / Detail / Workspace / Settings / NewSession）
- [ ] 6 个页面 Dark 模式截图
- [ ] 与改动前截图对比，确认视觉提升
- [ ] 与 Apple Notes / Messages / Settings 风格主观对比
- [ ] 长列表滚动流畅度验证（60fps，无明显掉帧）
