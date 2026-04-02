# Proposal: AskUserQuestion 显示修复

## Problem

E2E 测试中发现 AskUserQuestion 工具调用结果在 Android 端完全无法渲染。截图显示 agent 回复 "已弹出问题，等待你的选择。" 但用户端没有看到任何选项 UI。

**根因**：`parseAskUserQuestion()` 期望 `{ "question": "...", "options": [...] }` 扁平 JSON，但 Claude Code 的 AskUserQuestion 工具实际输入结构是嵌套的：

```json
{
  "questions": [
    {
      "question": "你希望使用哪种方案？",
      "header": "Approach",
      "options": [
        { "label": "方案A", "description": "..." },
        { "label": "方案B", "description": "..." }
      ],
      "multiSelect": false
    }
  ]
}
```

当前解析逻辑在 `DetailUtils.kt:138-170` 找不到顶层 `question` 字段，fallback 到把整个 JSON 字符串当作问题文本显示，导致选项按钮不渲染。

## Scope

- 修复 `parseAskUserQuestion()` 以支持 `questions[]` 数组嵌套结构
- 修复选项解析：从 `options[].label` 提取显示文本，而非直接使用字符串数组
- 支持 `multiSelect` 模式（复选框 vs 单选按钮）
- 兼容旧格式（扁平 `question` + `options` 字符串数组）

## Success Criteria

- AskUserQuestion 工具调用在 Android 端正确渲染问题文本 + 选项按钮
- 选项点击后正确回填到回答草稿
- 提交回答后卡片正确过渡到 "已提交" 状态
- 向后兼容：旧格式仍能正确解析
- 无功能回归
