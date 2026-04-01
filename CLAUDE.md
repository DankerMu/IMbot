# CLAUDE.md

IMbot: spec-first, single-user remote-control system for Claude Code / book / OpenClaw. Android → relay (Fastify + SQLite) → companion (MacBook). See `docs/PRD.md` for product scope.

## Quick Reference

```bash
npm run build                # wire → relay → companion → viewer
npm test                     # unit + contract
npm run test:unit            # node --test tests/unit/*.test.mjs
npm run test:contract        # node --test tests/contract/*.test.mjs
npm run test:integration     # node --test tests/integration/*.test.mjs
npm run typecheck            # alias: npm run lint
```

Node ≥22, built-in test runner (no Jest/Vitest). Build order: wire → relay/companion/viewer.

## Monorepo

```
packages/wire/       — @imbot/wire: shared types, enums, state transitions
packages/relay/      — @imbot/relay: Fastify, SQLite, WS hub, orchestrator
packages/companion/  — @imbot/companion: WS client, CLI adapter (stream-json)
packages/viewer/     — @imbot/viewer: CLI session event streamer
packages/android/    — Kotlin/Compose Android app
tests/{unit,contract,integration}/
```

## Conventions

- Single-user, static token auth (no RBAC)
- SQLite via better-sqlite3, WS via `ws` (not Socket.IO)
- TypeScript strict, ES2022, Node16 module resolution
- Session state machine: `queued → running ⇄ idle → completed/failed/cancelled`
- Companion uses `--input-format stream-json --output-format stream-json` for persistent sessions
- Default permission mode: `bypassPermissions`
- Implementation via `cc-cx-workflow` skill (Claude Code orchestrates, Codex implements)
- PR requires two agent cross-reviews → `.github/pull_request_template.md`
- CI gates: `.github/ci-gates.json` → `.github/workflows/implementation-gates.yml` + `repo-governance.yml`

## Document Index

### Product & Architecture

| Document | Path |
|----------|------|
| PRD (产品需求) | `docs/PRD.md` |
| Engineering Spec 总入口 | `docs/engineering-spec/SPEC_INDEX.md` |
| Architecture (组件架构) | `docs/engineering-spec/02_Technical_Design/ARCHITECTURE.md` |
| Data Model (SQL schema, 状态转换) | `docs/engineering-spec/02_Technical_Design/DATA_MODEL.md` |
| API Spec (REST + WS + Companion 协议) | `docs/engineering-spec/02_Technical_Design/API_SPEC.md` |
| Business Logic (状态机, 事件流) | `docs/engineering-spec/02_Technical_Design/BUSINESS_LOGIC.md` |
| Auth Design | `docs/engineering-spec/03_Security/AUTH_DESIGN.md` |
| Configuration | `docs/engineering-spec/04_Operations/CONFIGURATION.md` |
| Deployment | `docs/engineering-spec/04_Operations/DEPLOYMENT.md` |
| Test Plan | `docs/engineering-spec/05_Testing/TEST_PLAN.md` |
| Task Breakdown | `docs/engineering-spec/06_Implementation/TASK_BREAKDOWN.md` |
| Requirements Matrix | `docs/engineering-spec/01_Requirements/REQUIREMENTS_MATRIX.md` |

### UI/UX Spec

| Document | Path |
|----------|------|
| UI/UX Overview | `docs/specs/ui-ux-rd-spec/OVERVIEW.md` |
| Foundation / Components / Patterns | `docs/specs/ui-ux-rd-spec/01_Foundation/` ~ `03_Patterns/` |
| Pages (6 screens) | `docs/specs/ui-ux-rd-spec/04_Pages/` |
| Accessibility | `docs/specs/ui-ux-rd-spec/05_A11y/A11Y.md` |

### OpenSpec (Execution Layer)

| Document | Path |
|----------|------|
| OpenSpec 变更索引 | `openspec/README.md` |
| Archived (p0–p1, 9 changes) | `openspec/changes/archive/` |
| Active (p1–p3) | `openspec/changes/p1-*`, `p2-*`, `p3-*` |
| Persistent Sessions ✅ | `openspec/changes/persistent-interactive-sessions/` |

### Process & Operations

| Document | Path |
|----------|------|
| CI Pipeline (workflow, gate, branch protection) | `docs/CI_PIPELINE.md` |
| PR Workflow (模板, agent review, 合并规则) | `docs/PR_WORKFLOW.md` |
| Implementation Workflow (8 阶段流水线) | `docs/IMPLEMENTATION_WORKFLOW.md` |
| E2E Test Plan (T1–T10) | `docs/E2E_TEST_PLAN.md` |
| CI & Issue DAG Plan (历史) | `docs/plans/2026-03-29-github-ci-and-issue-dag.md` |
| References (happy 项目) | `docs/REFERENCES.md` |
| Interactive CLI Spike | `docs/spike-interactive-cli.md` |
