# Proposal: Visual Redesign — Apple Aesthetic

## Problem

当前 IMbot 的整体视觉效果显著落后于用户期望。从截图可以看出：

1. **Onboarding 页面**：
   - 圆形 "IM" logo 过于简陋，蓝灰色无品牌感
   - 输入框使用 OutlinedTextField 默认样式，边框过粗、圆角过小
   - 按钮灰色禁用态和蓝色激活态的视觉跳跃太突兀
   - 页面整体留白比例失调，元素纵向堆叠缺乏视觉节奏

2. **Home 首页 / Session List**：
   - SessionCard 视觉重量不足，缺少层次感
   - Provider badge（BK/CC/OC 圆形标签）颜色过于饱和，与整体风格不协调
   - 底部导航栏样式为 Material 3 默认，缺乏品牌定制
   - 空态页面缺乏情感设计

3. **Session Detail 页面**：
   - 顶部栏布局拥挤（provider badge + 标题 + 状态指示器 + 空间按钮 + 菜单）
   - 状态变更气泡（运行中/空闲/已完成）颜色过于鲜艳、排列密集，视觉噪音大
   - 消息气泡的颜色方案缺乏深度——用户消息蓝色、agent 消息灰色，过于简单
   - 输入栏 OutlinedTextField 默认样式，缺乏精致感
   - "回到底部" FAB 样式为默认深蓝色，过于突兀

4. **整体问题**：
   - 颜色系统没有品牌一致性，混用 Google Blue / Material 默认色
   - 动画虽有基础的 fade/slide，但缺少苹果系统特有的弹性动画和手势连贯感
   - 间距/圆角/阴影不统一，缺少设计系统约束

## Design Direction

以 Apple 生态系统的美学风格为参考：
- **色彩**：低饱和度中性色为主，强调色克制使用（SF Symbols 风格的单色图标）
- **圆角**：大圆角（16-20dp cards, 24-28dp modals）、连续曲线（superellipse 效果）
- **阴影**：柔和扩散阴影而非 Material 的锐利高程阴影
- **排版**：SF Pro 风格的字重层次（Regular/Medium/Semibold），行间距宽松
- **动画**：弹性曲线（spring animation）、手势驱动过渡、过度动画抑制（reduce motion 支持）
- **毛玻璃**：状态栏、底部栏适度使用 blur 效果（Modifier.blur 或 RenderEffect）

## Scope

覆盖所有 6 个页面的视觉层。不改变功能逻辑、导航结构或数据流。

## Design Process

使用 `ui-ux-pro-max` skill 进行系统化设计：
1. 提取设计 token（颜色/排版/间距/圆角/阴影）
2. 逐页面输出设计规格
3. 组件级 spec（SessionCard, MessageBubble, InputBar, StatusBadge, NavigationBar 等）

## Success Criteria

- 6 个页面全部重新设计视觉层
- 统一的设计 token 系统（color, typography, spacing, radius, elevation）
- 暗色模式完整适配
- 动画使用 spring 曲线替代线性/ease 曲线
- 视觉质量达到 Apple 原生应用水准（参考 Notes / Messages / Mail）
- 无功能回归
