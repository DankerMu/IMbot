# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

IMbot is a spec-first, single-user remote-control system for Claude Code / book / OpenClaw. Three surfaces: Android app → relay server (Fastify + SQLite on VPS) → MacBook companion. The relay is the central hub; companion spawns CLI processes; Android is the mobile UI (Jetpack Compose).

Providers: `claude` (MacBook), `book` (MacBook, novel dir), `openclaw` (relay-local gateway on localhost:18789).

## Build & Test Commands

```bash
# Full build (wire first, then relay + companion)
npm run build

# Typecheck only (includes wire build)
npm run typecheck          # alias: npm run lint

# Tests — all require build first (handled by scripts)
npm test                   # unit + contract
npm run test:unit          # node --test tests/unit/*.test.mjs
npm run test:contract      # node --test tests/contract/*.test.mjs
npm run test:integration   # node --test tests/integration/*.test.mjs

# Single test file
npm run build && node --test tests/unit/wire.test.mjs

# Per-package build
npm run build:wire
npm run build:relay
npm run build:companion

# Clean dist artifacts
npm run clean
```

Node ≥22 required. Tests use Node's built-in test runner (no Jest/Vitest).

## Monorepo Structure

```
packages/
  wire/       — @imbot/wire: shared TypeScript types (enums, models, messages, commands)
  relay/      — @imbot/relay: Fastify server, SQLite, WS hub, session orchestrator
  companion/  — @imbot/companion: WS client, CLI adapter, heartbeat, workspace browser
packages/android/  — (future) Kotlin/Compose Android app
tests/
  unit/       — unit tests (*.test.mjs)
  contract/   — contract tests between packages
  integration/— runtime integration tests
```

**Build order matters**: wire → relay/companion (wire must build first as others depend on `@imbot/wire` dist).

## Architecture

- **Relay** (`packages/relay/src/app.ts`): Fastify app assembling WsHub, CompanionManager, SessionOrchestrator, OpenClawBridge, AuditLogger. Routes under `/v1` prefix have auth guard (static token). WS routes for android and companion are unauthenticated at HTTP level (token in WS handshake).
- **Companion** (`packages/companion/src/index.ts`): Connects to relay via WS, dispatches commands (`create_session`, `resume_session`, `send_message`, `cancel_session`, `browse_directory`), spawns Claude/book CLI processes via `ClaudeRuntimeAdapter`.
- **Wire** (`packages/wire/src/`): Shared contract — `enums.ts` (providers, session statuses, event types, error codes with HTTP mapping, valid state transitions), `models.ts` (Session, Host, WorkspaceRoot, SessionEvent), `messages.ts`, `commands.ts`.

Session state machine: `queued → running → completed/failed/cancelled` (completed/failed can transition back to running for resume).

## Spec-First Workflow

This repo is **spec-first**. Read before coding:
1. `docs/PRD.md` — product scope and milestones
2. `docs/engineering-spec/SPEC_INDEX.md` — architecture, API, data model
3. `openspec/README.md` — execution slices (p0–p3 delivery bands)

OpenSpec skills (`/opsx:*`) manage the change lifecycle: `/opsx:new` → `/opsx:continue` → `/opsx:apply` → `/opsx:verify` → `/opsx:archive`.

If a change only implements an existing spec, do not rewrite product intent.

## CI Pipeline

Two workflow files enforce quality on every PR:

**Implementation Gates** (`.github/workflows/implementation-gates.yml`):
- `node-static-quality`: lint → typecheck → test:unit → test:contract → build
- `node-integration`: test:integration
- `android-static-quality` / `android-instrumented-smoke`: Gradle-based (activated via `.github/ci-gates.json`)

**Repo Governance** (`.github/workflows/repo-governance.yml`):
- `pr-review-evidence`: validates agent review evidence in PR body
- `spec-governance`: validates spec-first repo structure
- `markdown-quality`: markdownlint + prettier on governed files
- `shell-quality`: shellcheck on all `.sh` files
- `workflow-quality`: actionlint on GitHub Actions

Gate activation config: `.github/ci-gates.json` (toggle `node_static`, `node_integration`, etc.)

## PR Requirements

Use `.github/pull_request_template.md`. Key requirements:
- At least two reviewer agents must cross-review before merge
- Review evidence (reviewer name, head SHA, linked PR comments) recorded in PR body `Agent Review` section
- No unresolved conversations before merge
- Linked reviewer comments are immutable evidence; post new comments for corrections

## Key Conventions

- Auth: static token (single-user, no RBAC)
- DB: SQLite via better-sqlite3 (not Postgres)
- WS: native `ws` package (not Socket.IO)
- Default permission mode: `bypassPermissions`
- TypeScript strict mode, ES2022 target, Node16 module resolution
- No web client, no iOS, no multi-tenant

## Implementation Workflow

Issue implementation uses the `cc-cx-workflow` skill (Claude Code orchestrates, Codex implements via `codeagent-wrapper`).

The full pipeline: DAG analysis → codeagent implement → build/test → PR → parallel cross-review → fix → mop-up → CI → merge. Invoke with "处理下一个 issue" or "implement #XX".

Key rules that apply repo-wide:
- Give Codex clear goals, spec references (`@file`), and repo constraints — it handles implementation details itself
- Always use `--backend codex` and `--full-output` (for reviews)
- Only Claude Code performs GitHub write actions (merge, close, comment, push)
- Codeagent review tasks are **read-only**
- Merge only after CI passes + review evidence (full 40-char SHA) + user approval
- `codeagent-wrapper` path: `~/.claude/bin/codeagent-wrapper` (if not in PATH)
