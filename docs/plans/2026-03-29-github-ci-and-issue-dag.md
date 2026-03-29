# GitHub CI And Issue DAG Plan

## Goal

Prepare IMbot for GitHub-based development with:

1. a strict CI baseline that is enforceable immediately in a spec-first repository
2. a dependency-aware GitHub issue DAG derived from `openspec/changes/*`

This plan is intentionally split into:

- **Current-state CI**: what should run now, before code packages exist
- **Phase-activated CI**: what becomes required once `packages/relay`, `packages/companion`, `packages/wire`, and `packages/android` land
- **Scheduled / security CI**: heavier system tests and supply-chain gates that should exist from the start but activate in stages

## CI Design

### Required Immediately

These checks should be branch-protection blockers from day 1:

1. `Spec Governance`
   - validates required root docs exist
   - validates every OpenSpec change has `.openspec.yaml`, `README.md`, `proposal.md`, `design.md`, `tasks.md`
   - validates every OpenSpec change has at least one capability spec
   - validates `openspec/README.md` mentions every change
   - validates FR / NFR traceability anchors exist
   - validates relative markdown links

2. `Markdown Quality`
   - `markdownlint-cli2` for governance docs and new planning docs
   - `prettier --check` for governance-owned `md/yml/json`
   - repo-wide markdown normalization is a separate follow-up, not a blocker for enabling CI now

3. `Shell Quality`
   - `shellcheck` for repo scripts

4. `Workflow Quality`
   - `actionlint` for GitHub Actions workflows

5. `Dependency Review`
   - GitHub dependency review for pull requests
   - should stay required even before executable code exists, because Actions and future package manifests still change supply-chain risk

### Activation Model

Use `.github/ci-gates.json` as the single activation switchboard.

Rules:

- workflows exist from day 1 so branch protection names remain stable
- implementation jobs are skipped until their gate is turned on
- enabling a gate is a deliberate pull request, not an implicit side effect of adding a folder
- gates should be enabled no later than the first PR that makes the relevant package or test layer part of the supported delivery path

Initial gate state:

- `node_static=false`
- `node_integration=false`
- `android_static=false`
- `android_instrumented=false`
- `nightly_system=false`
- `codeql_javascript_typescript=false`
- `codeql_kotlin=false`

### Activate When Node Packages Exist

Use `Implementation Gates / Node Static Quality`.

Required jobs:

- dependency install with lockfile enforcement
- lint
- typecheck
- unit tests with coverage threshold enforcement
- contract tests for wire / REST / event compatibility once relay, companion, or wire contracts exist
- build

Rules:

- Node 22 only
- fail on missing `npm test` / `npm run lint` / `npm run typecheck` / `npm run build`
- fail on missing `npm run test:unit`
- fail on missing `npm run test:contract` once `packages/relay`, `packages/companion`, or `packages/wire` exist
- cache package manager data only, never build outputs

Recommended activation sequence:

1. enable `node_static` in the PR that lands `p0-monorepo-and-wire`
2. keep `node_integration=false` until mocked relay / companion flows are stable in CI
3. enable `node_integration` no later than `p1-relay-session-lifecycle` or `p1-companion-session-management`

### Activate When Relay / Companion Integration Is Ready

Use `Implementation Gates / Node Integration`.

Required jobs:

- install dependencies with lockfile enforcement
- run `test:integration`
- upload integration reports on failure

Rules:

- CI must use mocked Claude / book / OpenClaw dependencies
- no dependency on a real local login state
- approval-path reserved plumbing must remain covered once `p3-approval-path-reserved` lands

### Activate When Android Package Exists

Use `Implementation Gates / Android Static Quality`.

Required jobs:

- Gradle wrapper validation
- Android lint
- unit tests
- assemble debug
- static analysis (`detekt` and/or `ktlint` once configured)

Rules:

- JDK 21
- Gradle dependency caching
- fail on warnings promoted to errors where practical

Recommended activation sequence:

1. enable `android_static` once `packages/android` builds in CI during `p0-android-prototype`
2. keep `android_instrumented=false` during prototype-only work if stable emulator tests do not exist yet
3. enable `android_instrumented` no later than the first PR landing `p2` user-facing flows

### Activate When Android Product Surfaces Stabilize

Use `Implementation Gates / Android Instrumented Smoke`.

Required jobs:

- emulator boot on `ubuntu-latest`
- `connectedDebugAndroidTest`
- artifact upload for screenshots / reports when tests fail

Minimum smoke coverage:

- onboarding / test connection
- session list render
- new session flow
- session detail streaming render
- workspace / settings navigation

### Security Workflows

Workflows should exist immediately, but deeper analysis gates should be activated with `.github/ci-gates.json`.

Enabled now:

- dependency review on PRs
- Dependabot for GitHub Actions, npm, and Gradle

Activate later:

- CodeQL
- secret scanning / push protection on GitHub

Recommended activation sequence:

1. enable `codeql_javascript_typescript` when relay / companion / wire contain real runtime logic
2. enable `codeql_kotlin` when Android runtime code is committed
3. turn on GitHub secret scanning and push protection immediately after the public repo is created

### Scheduled / Nightly Gates

Use `Nightly System Gates` for:

- `test:e2e`
- `test:perf`
- full Android emulator regression
- reconnect / catch-up longevity checks

Rules:

- scheduled gates are not day-1 PR blockers
- failed nightly runs should block release tags until fixed
- performance regressions touching `PERF-01` to `PERF-04` should either be fixed immediately or have an approved threshold change in spec

### Branch Protection

Recommended protection for `main`:

- require PR before merge
- require up-to-date branch before merge
- require `Spec Governance`, `Markdown Quality`, `Shell Quality`, `Workflow Quality`, `Dependency Review`
- add `Node Static Quality`, `Node Integration`, `Android Static Quality`, `Android Instrumented Smoke` as required checks from day 1 because skipped jobs will keep check names stable until gates are enabled
- dismiss stale approvals on new commits
- block force-push and deletion
- require conversation resolution before merge
- keep GitHub merge queue optional until active development concurrency justifies it

### Release Gate Expectations

Before the first external testing or distribution build:

- nightly system gates enabled
- relevant CodeQL gates enabled
- branch protection updated to include all active implementation jobs
- Android debug and release-like builds reproducible from CI
- dependency update automation proven on at least one merged Dependabot PR

## Issue Structure Strategy

Use a **master roadmap epic** with **phase epics** beneath it, then one implementation issue per OpenSpec change plus a small set of GitHub foundation issues for CI / repository rules.

This keeps GitHub readable while preserving the real dependency graph.

### Labels

Required labels:

- `epic`
- `sub-task`
- `feature`
- `type:infra`
- `type:backend`
- `type:android`
- `type:security`
- `area:wire`
- `area:relay`
- `area:companion`
- `area:android`
- `area:openclaw`
- `area:github`
- `phase:0`
- `phase:1`
- `phase:2`
- `phase:3`
- `priority:critical`
- `priority:high`
- `priority:medium`

## DAG

### Master Epic

- `[Epic] IMbot GitHub Delivery Roadmap`

### Epic A: GitHub Delivery Foundation

- `Set up GitHub repository governance and CI baseline`
  - no dependency
  - labels: `epic`, `feature`, `type:infra`, `area:github`, `priority:critical`

Sub-issues:

1. `Design and implement repo-governance CI for the spec-first repository`
   - depends on: none
   - labels: `sub-task`, `type:infra`, `area:github`, `priority:critical`
2. `Scaffold phase-activated implementation gates for Node and Android`
   - depends on: repo-governance CI
   - labels: `sub-task`, `type:infra`, `area:github`, `priority:critical`
3. `Enable security and supply-chain automation for the public GitHub repo`
   - depends on: repo-governance CI
   - labels: `sub-task`, `type:security`, `area:github`, `priority:high`
4. `Configure branch protection, PR template, and required check policy`
   - depends on: repo-governance CI, implementation gates, security automation
   - labels: `sub-task`, `type:infra`, `area:github`, `priority:high`

### Epic B: Phase 0 Feasibility Foundation

- `[Epic] Phase 0 feasibility foundation`

Sub-issues:

1. `p0-monorepo-and-wire: scaffold monorepo and shared wire protocol`
   - depends on: GitHub CI baseline
2. `p0-relay-minimal: bootstrap relay server and basic session/event flow`
   - depends on: `p0-monorepo-and-wire`
3. `p0-companion-minimal: implement companion relay client and CLI adapter`
   - depends on: `p0-monorepo-and-wire`
4. `p0-android-prototype: build Android prototype for end-to-end validation`
   - depends on: `p0-monorepo-and-wire`, `p0-relay-minimal`
5. `p0-openclaw-bridge: connect relay to local OpenClaw gateway`
   - depends on: `p0-monorepo-and-wire`, `p0-relay-minimal`

### Epic C: Phase 1 Core MVP Services

- `[Epic] Phase 1 core MVP services`

Sub-issues:

1. `p1-relay-session-lifecycle: complete session state machine and REST API`
   - depends on: `p0-relay-minimal`
2. `p1-relay-workspace-api: implement hosts, roots, browsing, host status`
   - depends on: `p0-relay-minimal`, `p0-companion-minimal`
3. `p1-companion-session-management: add list/resume/send/cancel and workspace sync`
   - depends on: `p0-companion-minimal`, `p0-monorepo-and-wire`
4. `p1-reconnect-and-catchup: implement reconnect loops and since_seq catch-up`
   - depends on: `p0-relay-minimal`, `p0-companion-minimal`, `p0-android-prototype`, `p1-relay-session-lifecycle`
5. `p1-fcm-push: implement relay and Android push pipeline`
   - depends on: `p1-relay-session-lifecycle`

### Epic D: Phase 2 Android Product Surfaces

- `[Epic] Phase 2 Android product surfaces`

Sub-issues:

1. `p2-android-session-list: implement home list, provider filter, Room cache`
   - depends on: `p0-android-prototype`, `p1-relay-session-lifecycle`
2. `p2-android-new-session: implement provider-directory-prompt creation flow`
   - depends on: `p1-relay-workspace-api`, `p1-relay-session-lifecycle`
3. `p2-android-session-detail: implement detail timeline, markdown, tool calls`
   - depends on: `p1-relay-session-lifecycle`, `p1-reconnect-and-catchup`
4. `p2-android-workspace-settings: implement workspace manager, settings, onboarding`
   - depends on: `p1-relay-workspace-api`, `p2-android-session-list`, `p2-android-new-session`

### Epic E: Phase 3 Hardening And Reserved Capability

- `[Epic] Phase 3 polish, hardening, and reserved capability`

Sub-issues:

1. `p3-theme-and-animations: implement theme system and motion polish`
   - depends on: `p2-android-session-list`, `p2-android-session-detail`, `p2-android-workspace-settings`
2. `p3-error-ux-and-cleanup: implement error UX, purge job, connection hardening`
   - depends on: `p1-reconnect-and-catchup`, `p2-android-session-list`, `p2-android-session-detail`, `p2-android-workspace-settings`
3. `p3-approval-path-reserved: preserve non-bypass permission-mode plumbing`
   - depends on: `p1-relay-session-lifecycle`, `p1-companion-session-management`

## Suggested Creation Order

1. Master roadmap epic
2. GitHub / CI epic + its four foundation issues
3. Phase 0 epic and all phase 0 issues
4. Phase 1 epic and all phase 1 issues
5. Phase 2 epic and all phase 2 issues
6. Phase 3 epic and all phase 3 issues

This order lets issue bodies reference already-created parent issue numbers and concrete dependencies.
