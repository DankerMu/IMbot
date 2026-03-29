# IMbot Engineering Specification Index

**PRD Source**: `docs/PRD.md` v1.0
**Spec Version**: 1.0
**Date**: 2026-03-28

## PRD Validation

**Status**: ✅ PASS — PRD 已通过验证，无 ❌ 项。

| Category | Score | Notes |
|----------|-------|-------|
| Document Basics | 5/5 | ✅ |
| Problem Statement | 5/5 | ✅ |
| Users & Personas | 5/5 | ✅ 单用户，画像清晰 |
| Functional Requirements | 10/10 | ✅ FR-01~FR-10 全覆盖 |
| Data Requirements | 5/5 | ✅ |
| UI/UX | 4/5 | ⚠️ 无线框图（可接受，单用户） |
| Error Handling | 5/5 | ✅ Sad paths 全覆盖 |
| NFRs | 5/5 | ✅ 量化指标完整 |
| Integrations | 4/4 | ✅ |
| Timeline | 4/4 | ✅ |

**Assumptions**:
- OpenClaw gateway 协议稳定，bridge 适配层足够隔离变更
- 单用户场景无需考虑并发写冲突

## Alignment Resources

1. [01_Requirements/REQUIREMENTS_MATRIX.md](01_Requirements/REQUIREMENTS_MATRIX.md) — PRD ↔ engineering spec ↔ OpenSpec ↔ test coverage 总矩阵
2. [../../openspec/README.md](../../openspec/README.md) — OpenSpec 变更索引、`p0-p3` 语义、按 FR 的实现映射
3. [../specs/ui-ux-rd-spec/00_SourceInventory/COVERAGE.md](../specs/ui-ux-rd-spec/00_SourceInventory/COVERAGE.md) — Android/UI 侧覆盖与未覆盖项
4. [06_Implementation/TASK_BREAKDOWN.md](06_Implementation/TASK_BREAKDOWN.md) — PRD phase 与 OpenSpec workstream 的落地节奏

## Reading Order

1. [00_Overview/SUMMARY.md](00_Overview/SUMMARY.md) — 全局概览 + 决策日志
2. [02_Technical_Design/ARCHITECTURE.md](02_Technical_Design/ARCHITECTURE.md) — 组件架构 + 模块拆分
3. [02_Technical_Design/DATA_MODEL.md](02_Technical_Design/DATA_MODEL.md) — 完整 SQL Schema
4. [02_Technical_Design/API_SPEC.md](02_Technical_Design/API_SPEC.md) — 全量 API + 请求/响应示例
5. [02_Technical_Design/BUSINESS_LOGIC.md](02_Technical_Design/BUSINESS_LOGIC.md) — 状态机 + 事件流 + 协议
6. [03_Security/AUTH_DESIGN.md](03_Security/AUTH_DESIGN.md) — 认证设计
7. [04_Operations/CONFIGURATION.md](04_Operations/CONFIGURATION.md) — 全量配置参数
8. [04_Operations/DEPLOYMENT.md](04_Operations/DEPLOYMENT.md) — 部署方案
9. [05_Testing/TEST_PLAN.md](05_Testing/TEST_PLAN.md) — 测试计划 + 可追溯矩阵
10. [06_Implementation/TASK_BREAKDOWN.md](06_Implementation/TASK_BREAKDOWN.md) — 任务分解 + 里程碑
