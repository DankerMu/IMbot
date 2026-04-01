# PR Workflow

## 流程概览

```
feature branch → PR → agent cross-review → fix → CI green → merge
```

单人开发，无需人工 approval，但必须有 agent 交叉审查证据。

## PR 模板

使用 `.github/pull_request_template.md`，包含以下必填 section：

| Section | 内容 |
|---------|------|
| Change Summary | what/why/impact/out-of-scope |
| Verification | 本地检查结果、CI 预期、风险 |
| Linked Work | Issue #、OpenSpec change、FR/NFR、测试 ID |
| Agent Review | reviewer agent 名、head SHA、评论链接、关键发现 |
| Checklist | PRD scope、spec 对齐、OpenSpec 更新、测试、reviewer 完成、conversation resolved |

## Agent Review 要求

每个 PR 必须有 **至少两个** reviewer agent 交叉审查：

1. 两个 reviewer 分别 post PR conversation comment，格式：
   ```
   Reviewer agent: <agent name>
   Reviewed head SHA: <40-char commit SHA>
   Summary: <one-line conclusion>
   Findings:
   - <finding or "None.">
   ```

2. PR body `Agent Review` section 记录：
   - Reviewer agents used
   - Reviewed head SHA（必须是当前 PR head 的完整 40-char SHA）
   - Review evidence（两个 PR comment 的链接）
   - Key findings addressed

3. 如果 push 了新 commit，必须重新运行 reviewer agent 并更新 Agent Review section

## 不可变证据规则

- Linked reviewer comments 是不可变证据，一旦 post 不可编辑
- 如果需要更正，post 新的 PR comment 并更新 PR body 中的链接
- `PR Review Evidence` CI job 会校验链接 comment 的格式和 SHA 匹配

## Conversation Resolution

- PR 中的 threaded review discussion 必须全部 resolved 才能合并
- 这是 GitHub merge rule，独立于 agent review evidence 检查

## 合并规则

- 必须 CI 全绿（包括 skipped gate jobs）
- Agent review evidence 通过校验
- 所有 conversation resolved
- 使用 merge commit（不 squash、不 rebase）
- 合并后删除 feature branch
- 关联 issue 在合并后 close（手动或 auto-close）

## Commit 规范

```
<type>(<scope>): <description> (#<issue>)
```

常见 type: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`

scope 对应包名或功能区域：`wire`, `relay`, `companion`, `viewer`, `android`, `ci`
