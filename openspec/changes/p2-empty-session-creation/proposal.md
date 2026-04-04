# Proposal: 空 Session 创建（无需首条 Prompt）

## Problem

当前新建 session 必须输入第一条 prompt 才能创建。用户体验路径是：选 Provider → 选目录 → **必须输入 prompt** → 才能点"开始"。这个设计给人的体验非常差：

1. 用户选好目录和模型后，"开始" 按钮仍然禁用，必须先想好问什么才能创建 session
2. 与 native `book` / `claude` CLI 行为不一致——CLI 启动后可以不带 prompt，进入交互式等待
3. 用户可能想先创建 session 占位，再慢慢组织第一条消息

**当前约束链**（四层硬性依赖）：

```
Android canCreate()  →  relay API schema  →  orchestrator validation  →  companion dispatch
     prompt.isNotBlank()    required: ["prompt"]   !input.prompt → throw     immediately sends prompt
```

数据库层 `initial_prompt TEXT` 已经是 nullable，无需 schema 迁移。

## Scope

1. 解除 prompt 的强制要求：允许 provider + host_id + cwd 即可创建 session
2. 新的 session 创建路径：无 prompt → session 以 `idle` 状态创建 → 用户发送第一条消息时启动 provider 进程 → 转为 `running`
3. Android 新建 session 流程优化：选完目录后即可直接创建，跳过 prompt 步骤
4. 保持向后兼容：带 prompt 创建仍走现有流程（`queued → running`）

## Out of Scope

- 修改 session 状态机定义（复用现有 `idle` 状态）
- Android 新建流程的完整重新设计（仅移除 prompt 硬性要求）
- OpenClaw provider（已有独立创建流程）

## Success Criteria

1. Android 选完 provider + 目录后可直接点"开始"创建 session
2. 空 session 创建后进入 detail 页面，状态为 `idle`，输入框可用
3. 用户在空 session 中发送第一条消息后，session 转为 `running`，provider 进程启动
4. 带 prompt 创建仍然正常工作（一步到位 `queued → running`）
5. Relay API 向后兼容：`prompt` 字段变为 optional
6. 无功能回归，现有测试全部通过
