# Implementation Workflow

## 工具链

- **Claude Code**: 编排者，负责上下文收集、规划、验证、git 操作、PR 管理
- **Codeagent (Codex backend)**: 实现者，负责写代码和测试
- 路径：`~/.claude/bin/codeagent-wrapper`
- 始终使用 `--backend codex` 和 `--full-output`

## 触发方式

- "处理下一个 issue" — 自动 DAG 选择
- "implement #XX" — 指定 issue

## 8 阶段流水线

### Phase 0: Issue 选择 + Spec 检查

1. `gh issue list` 获取 open issues
2. 按依赖关系构建 DAG（检查 issue body 中的 `Depends on #XX`）
3. 自动选择：phase 优先 → priority 优先 → backend > android → 低编号优先
4. 读取 `tasks.md`，确认测试 section 有场景级粒度
5. 如果用户指定了 issue 编号则跳过 DAG

### Phase 1: Codeagent 实现（代码 + 测试一轮完成）

1. 创建 feature branch: `feat/issue-<N>-<change-name>`
2. 读取 issue body → 定位 openspec change → 读取 proposal/design/tasks
3. 组装 HEREDOC prompt，包含：
   - `@` 引用 spec 文件和关键源码
   - 明确 scope、验收标准、仓库约定
   - 错误路径覆盖要求
   - 完整测试 section
   - 构建/lint/测试验证命令
4. 后台调用 codeagent

### Phase 2: 本地全量验证

按项目类型运行完整 CI 等效流程：

- **Node**: `npm run build && npm test`
- **Android**: `./gradlew detekt ktlintMainSourceSetCheck ktlintTestSourceSetCheck testDebugUnitTest assembleDebug`
- **Mixed**: 两者都跑

额外检查：
- 扫描新代码的 I/O callback、error handler，确认每个都有测试
- 测试 timer 不使用 `.unref()`（CI 中会导致 `cancelledByParent`）

自修复：小问题直接修，大问题 resume codeagent，2 轮后上报

### Phase 3: Commit & PR

- Stage 具体文件（不用 `git add -A`）
- Commit message: `feat(<scope>): <desc> (#<issue>)`
- Push + `gh pr create`，Agent Review section 留空

### Phase 4: 并行交叉审查

使用 `codeagent-wrapper --parallel --full-output` 同时启动两个 reviewer：

1. **Correctness Reviewer**: 逻辑正确性、边界、类型安全、合约、回归风险、测试覆盖
2. **Security & Performance Reviewer**: 路径遍历、DoS、竞态、信息泄露、热路径性能

规则：
- Reviewer **只读**，不修改文件
- 结果写入 `/tmp/review-pr-<PR#>/`，不 post PR comment
- 两个 reviewer 都收到完整变更文件和 spec 引用

### Phase 5: 修复清单合成

合并 reviewer 发现 + 自身 diff review + 测试覆盖缺口 → 统一清单

规则：
- wontfix 必须引用 spec 条款或量化约束
- **测试覆盖缺口永远不能 wontfix**
- 全部 clean 则跳过 Phase 6

### Phase 6: Codeagent 修复

将 actionable findings 交给 codeagent，每个 finding 包含：severity、file:line、problem、fix、test

### Phase 7: 最终审查 + 收尾

1. Build + test + lint 全通过
2. 小问题（<5 行）直接修
3. **≥3 个新文件时必须**：spawn 独立 reviewer agent 做 clean-context review
4. 修复 critical/major findings
5. 语义化 commit，push

### Phase 8: PR 证据 & CI（唯一人工门控）

1. 确认无未提交变更、无未推送 commit
2. Post 恰好 2 条 PR comment（每个 reviewer 一条），格式见 `docs/PR_WORKFLOW.md`
3. 更新 PR body Agent Review section
4. `gh pr checks` 确认 CI 通过
5. **等待用户确认后合并**
6. `gh pr merge --merge --delete-branch`
7. Close 关联 issue

## 职责边界

| 操作 | 执行者 |
|------|--------|
| 写代码、写测试 | Codeagent |
| 读代码审查（Phase 4） | Codeagent (只读) |
| Build/test 验证 | Claude Code |
| Git 操作 (commit, push, merge) | Claude Code |
| GitHub 写操作 (PR, comment, close) | Claude Code |
| 修复 <5 行小问题 | Claude Code |
| 修复 >5 行问题 | Codeagent |

## 不适用场景

- 无 openspec 产物 → 先 `/opsx:new` 或 `/opsx:ff`
- 纯文档/spec PR → 直接 commit
- 紧急热修复 → 跳过 review，PR body 注明
- 上游依赖未解决 → 先解决依赖
