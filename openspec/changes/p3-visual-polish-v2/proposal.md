# Proposal: UI 美化第二轮（Visual Polish V2）

## Problem

第一轮 `p3-visual-redesign` 建立了 Apple 设计系统基础（色彩、字体、圆角、动画），但 E2E 测试显示整体视觉效果仍未达到 iOS 系统级美感。与 CodePilot 桌面端对比，主要差距在：

1. **消息气泡**：用户蓝色/助手灰色的二元配色过于简单，助手消息应该无背景（像 CodePilot 那样干净）
2. **间距系统**：消息间距 12dp 太紧凑，缺乏呼吸感（CodePilot 用 24dp）
3. **输入栏**：仍然是 Material 默认 OutlinedTextField，不够精致
4. **代码块**：缺少语言标签 badge、折叠/展开大文件的能力
5. **状态气泡**：视觉权重过大，干扰主内容阅读
6. **整体纹理**：缺少 CodePilot 那种半透明毛玻璃层、微妙渐变等质感元素

## Design Direction

以 CodePilot 的聊天界面为直接参考，结合 iOS Messages / Apple Notes 的设计语言：
- **助手消息去气泡化**：助手回复不使用灰色气泡背景，直接在白底上渲染文字
- **用户消息深色反转**：用户消息用深色背景 + 白色文字（CodePilot 风格），而非亮蓝
- **间距翻倍**：消息组间距 24dp，组内 8dp
- **输入栏毛玻璃**：半透明背景 + 模糊效果
- **代码块增强**：语言标签 badge + 大块折叠 + 复制按钮视觉升级
- **状态气泡极简化**：只保留文字 + 小图标，去除独立背景

## Scope

覆盖 Session Detail 页面的消息区域、输入栏、代码块组件。不改变其他页面（Home/Settings/Onboarding 在第一轮已处理）。不改变功能逻辑或数据流。

## Success Criteria

- 消息区域视觉质量达到 CodePilot / iOS Messages 水准
- 助手消息干净无气泡
- 用户消息深色高对比
- 代码块有语言标签、复制按钮、折叠能力
- 输入栏有毛玻璃质感
- 状态气泡视觉权重降低 70%
- 暗色模式完整适配
- 无功能回归
