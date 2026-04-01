# Proposal: Message Rendering Polish (Obsidian-Level Quality)

## Problem

当前消息渲染虽然功能完整（Markdown、KaTeX、代码高亮、表格），但视觉质量远低于 Obsidian / Notion 等现代笔记应用的标准：

1. **排版粗糙**：行间距、段间距、缩进层级缺乏精细控制，文字密度偏高，阅读体验差。
2. **代码块平庸**：语法高亮仅覆盖 7 种 token 类型，颜色方案偏灰暗，缺少语言标签装饰、行号显示。
3. **引用块无视觉层次**：`Quoted line` 仅有灰色背景，缺少左边框竖线、嵌套引用的缩进层级区分。
4. **列表样式简陋**：bullet 用 `·` 代替，缺少有序列表的数字对齐和嵌套缩进。
5. **表格勉强可用**：修复了渲染正确性后，样式仍然简陋——无斑马条纹、无表头高亮、间距不均。
6. **Inline 元素不够精致**：`inline code` 的背景色和圆角不够突出；链接缺少 hover/press 反馈。
7. **消息气泡本身过于扁平**：用户消息和 agent 消息的视觉区分度不够，缺少阴影/层次感。

## Reference

- **Obsidian**：清晰的排版层次、精致的代码块（行号+语言标签+一键复制）、左边框引用、嵌套列表缩进
- **Notion**：干净的表格样式、优雅的内联元素、段落间适当留白
- **CodePilot**：使用 Streamdown 库渲染，插件式架构（code/math/mermaid/CJK），GitHub 风格代码主题

## Scope

仅涉及 Android Compose 渲染层。优化现有 `MarkdownText.kt`、`MarkdownKatex.kt`、`CodeBlock.kt`、`MessageBubble.kt` 的排版和样式参数。不改变解析逻辑。

## Success Criteria

- 代码块：语言标签（右上角）+ 行号（可选）+ 更丰富的语法高亮色谱 + 圆角容器 + 复制按钮保留
- 引用块：左边框竖线（4dp, primary 色）+ 内容区域左 padding + 嵌套引用递进颜色
- 表格：表头高亮背景 + 斑马条纹行 + 适当 cell padding + 水平滚动支持（宽表格）
- 列表：合理嵌套缩进 + bullet 样式随层级变化（●/○/■）+ 有序列表数字右对齐
- 段落：行高 1.6-1.75 倍、段间距 12-16dp、标题段前额外留白
- Inline code：圆角 4dp、背景色更明显、等宽字体
- 整体消息气泡：微妙阴影 + 更精细的圆角 + 用户/agent 更清晰的视觉区分
- 暗色模式下所有改进同步生效
