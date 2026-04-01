# CI Pipeline

## Workflow 总览

| Workflow | File | Trigger | 职责 |
|----------|------|---------|------|
| Implementation Gates | `.github/workflows/implementation-gates.yml` | PR + push master | 构建、类型检查、测试 |
| Repo Governance | `.github/workflows/repo-governance.yml` | PR + push master | 规范检查（spec、markdown、shell、workflow、PR 证据） |
| Security And Supply Chain | `.github/workflows/security-supply-chain.yml` | PR + push master + 周一定时 | 依赖审计、CodeQL |
| Nightly System Gates | `.github/workflows/nightly-system-gates.yml` | 定时 | 系统级测试（未激活） |

## Gate 激活机制

所有实现级 job 受 `.github/ci-gates.json` 控制。Gate 为 `false` 时 job 报告 `skipped`，不阻塞 PR。

```json
{
  "node_static": true,
  "node_integration": true,
  "android_static": true,
  "android_instrumented": true,
  "nightly_system": false,
  "codeql_javascript_typescript": false,
  "codeql_kotlin": true
}
```

启用 gate 是显式 PR 操作，不因添加目录自动触发。

## Implementation Gates

### Node Static Quality

条件：`node_static=true` + 根 `package.json` 存在

执行脚本：`scripts/ci/run_node_static_gate.sh`

流程：`npm ci` → `npm run lint` (typecheck) → `npm run test:unit` → `npm run test:contract` → `npm run build`

### Node Integration

条件：`node_integration=true` + relay/companion/wire package 存在

执行脚本：`scripts/ci/run_node_integration_gate.sh`

流程：`npm ci` → `npm run test:integration`

### Android Static Quality

条件：`android_static=true` + gradlew 存在

执行脚本：`scripts/ci/run_android_static_gate.sh`

环境：JDK 21, Android SDK 35

流程：Gradle wrapper 校验 → detekt → ktlint → unit test → assembleDebug

### Android Instrumented Smoke

条件：`android_instrumented=true` + gradlew 存在

执行脚本：`scripts/ci/run_android_instrumented_gate.sh`

环境：JDK 21, API 35 emulator (x86_64, google_apis, pixel_7)

流程：启动模拟器 → connectedDebugAndroidTest

## Repo Governance

以下 job 始终运行，不受 gate 控制：

| Job | 脚本 | 检查内容 |
|-----|------|---------|
| PR Review Evidence | `scripts/ci/validate_pr_review_evidence.sh` | PR body Agent Review section 含 2 个 reviewer、40-char SHA、评论链接 |
| Spec Governance | `scripts/ci/validate_spec_repo.sh` | 根文档存在、OpenSpec 变更结构完整、README 索引一致 |
| Markdown Quality | (inline) | markdownlint + prettier 检查治理文档 |
| Shell Quality | (inline) | shellcheck 检查所有 `.sh` |
| Workflow Quality | (inline) | actionlint 检查 GitHub Actions |

## Security And Supply Chain

| Job | 条件 | 检查内容 |
|-----|------|---------|
| Dependency Review | PR + 有依赖清单 | `actions/dependency-review-action` |
| CodeQL JS/TS | `codeql_javascript_typescript=true` | 静态安全分析 |
| CodeQL Kotlin | `codeql_kotlin=true` | 静态安全分析 |

## Branch Protection

`master` 分支保护规则：
- 必须通过 PR 合并
- 必须分支最新
- 所有 job-level check name 作为 required status（skipped = pass）
- 不要求 approval（单人开发）
- 禁止 force-push 和删除
- 合并前必须解决所有 conversation

## 激活时序建议

| 时机 | 操作 |
|------|------|
| p0-monorepo-and-wire 落地 | 启用 `node_static` |
| p1-relay-session-lifecycle 落地 | 启用 `node_integration` |
| p0-android-prototype 构建通过 | 启用 `android_static` |
| p2 Android 界面稳定 | 启用 `android_instrumented` |
| runtime 代码充足 | 启用 `codeql_javascript_typescript` |
| Android runtime 代码落地 | 启用 `codeql_kotlin` |
