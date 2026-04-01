# Proposal: Mobile Skill & Tool Interaction

## Problem

IMbot Android 作为 Claude Code / book / OpenClaw 的远程控制终端，目前无法处理 AI agent 端的交互式工具调用和技能触发：

1. **Slash command 不可识别**：用户在手机端输入 `/skill-name` 格式的命令时，消息被当作普通文本直接发送给 agent，agent 可能无法正确解析。缺少命令自动补全、选择弹窗等辅助 UI。

2. **AskUserQuestion 工具无法呈现**：当 agent 调用 `AskUserQuestion` 工具向用户提问时，手机端只能看到一个普通的 tool_call 事件卡片，无法以友好的方式呈现问题并收集用户回答。用户必须手动理解工具调用内容、手动输入回答——体验极差。

3. **审批/确认类交互缺失**：`approval_required` / `approval_resolved` 事件虽然有状态气泡渲染，但缺乏可操作的 UI（approve/deny 按钮），目前只是占位设计。

## Scope

仅涵盖 Android 端 UI 交互层。不修改 relay/companion 协议（现有 WS 事件和 REST API 已支持 `send_input` 和 `session.sendInput`）。

## Reference

CodePilot 桌面端实现了完善的 skills 交互系统：
- `SlashCommandPopover.tsx`：`/` 触发弹窗，分 Commands / Slash Commands / Agent Skills / AI Suggested 四区
- `SlashCommandButton.tsx`：输入栏旁的快捷按钮
- `useSlashCommands.ts`：状态管理 hook，支持键盘导航（↑↓/Enter/Escape）
- `MessageInput.tsx`：badge 系统——选中 skill 后在输入栏上方显示 command badge

**适配要点**：CodePilot 是桌面端，键盘导航为主；IMbot 是手机端，需改为触摸弹窗 + 列表选择。

## Success Criteria

- 用户输入 `/` 时自动弹出可用命令列表（从 companion 报告的 skills 或静态列表）
- 选中命令后显示为输入栏上方的 chip/badge，附带参数输入区
- `AskUserQuestion` 工具调用渲染为专用卡片（问题文本 + 可选项/自由输入 + 提交按钮）
- 审批事件渲染为可操作卡片（approve/deny 按钮）
- 所有交互通过现有 `POST /sessions/:id/input` API 完成
