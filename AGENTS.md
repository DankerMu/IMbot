# IMbot Agent Guide

## Purpose

This repo is spec-first.
Before changing code or writing new implementation docs, align with the existing product and execution docs instead of inventing behavior locally.

## Read First

1. `docs/PRD.md`
2. `docs/engineering-spec/SPEC_INDEX.md`
3. `openspec/README.md`
4. `docs/specs/ui-ux-rd-spec/OVERVIEW.md` for Android/UI work

## Core Rules

- Treat `docs/PRD.md` as product scope and milestone source of truth.
- Treat `docs/engineering-spec/` as implementation source of truth for architecture, API, data model, testing, and rollout.
- Treat `openspec/` as execution slices. `p0-p3` are delivery bands, not a strict copy of PRD phases.
- Keep `PRD`, `engineering-spec`, `ui-ux-rd-spec`, and `openspec` aligned when scope changes.
- If a change only implements an existing spec, do not rewrite product intent unnecessarily.

## Default Product Boundaries

- Single-user system only.
- Three surfaces: Android app, relay server, MacBook companion.
- Supported providers: `claude`, `book`, `openclaw`.
- Default permission mode remains `bypassPermissions`.
- Approval flow is reserved plumbing only unless the task explicitly expands it.
- No web client, no iOS client, no multi-tenant features unless explicitly requested.

## How To Work

- Start from the requirement in `docs/engineering-spec/01_Requirements/REQUIREMENTS_MATRIX.md`.
- Find the owning OpenSpec change in `openspec/README.md`.
- If work does not fit an existing change, add a new OpenSpec change before implementing.
- For Android/UI changes, also update `docs/specs/ui-ux-rd-spec/00_SourceInventory/COVERAGE.md` when coverage meaning changes.
- For API/data/lifecycle changes, update tests and traceability docs in the same pass.

## GitHub Flow

- Work one GitHub issue at a time unless the dependency graph explicitly allows parallel work.
- Create a dedicated branch from `master` for each issue.
- Open a pull request for every change. Do not merge directly to `master`.
- Before merge, run at least two reviewer agents for cross-review with a bug/risk focus and record the result in the PR body `Agent Review` section plus linked PR conversation comments.
- If the PR head changes after agent review, rerun reviewer agents and update the recorded head SHA before merge.
- Merge only after the required GitHub checks pass and the PR has no unresolved conversations.
- After merge, move to the next unblocked issue in the DAG.

## Before Merging A Change

- Confirm behavior still matches the PRD.
- Confirm the affected engineering spec sections are still accurate.
- Confirm OpenSpec ownership is explicit.
- Confirm tests or test-plan references still match the requirement.
- Confirm reviewer-agent cross-review has been completed and the PR body records the reviewer names, reviewed head SHA, linked PR comment evidence, and a concise PR-level findings summary.
- Confirm the PR has no unresolved conversations before merge.
- Avoid silent scope creep.

## PR Communication

- PR descriptions must use short sections and flat bullets, not one large paragraph.
- Every PR description should clearly cover: change summary, why the change exists, verification, and remaining risks or follow-ups.
- When reviewer agents are running, wait patiently for them to complete before redirecting, interrupting, or closing them unless the work is clearly stuck.
- Reviewer-agent results must be posted as separate PR conversation comments, one comment per agent, and each comment must use this readable structure: `Reviewer agent`, `Reviewed head SHA`, `Summary`, `Findings`.
- Treat linked reviewer comments as immutable evidence. If a reviewer result needs correction, post a new PR comment and update the PR body links instead of editing an already linked comment.
- If a linked reviewer comment is edited, also rerun `Repo Governance` by editing the PR body or rerunning the workflow so `PR Review Evidence` reevaluates the new comment content.
- The linked reviewer comments are the source of truth for review evidence; `Key findings addressed` in the PR body is the concise roll-up for merge readers.
- Reviewer-agent evidence is enforced through `PR Review Evidence`; GitHub conversation resolution is a separate merge rule for any threaded review discussions or follow-up conversations.
- Review comments for concrete issues must be one finding per comment and use a readable structure with `Severity`, `Problem`, `Impact`, and `Requested fix`.
- If reviewer agents find no issues, record that in short bullets instead of a prose block.

## Practical Bias

- Prefer small, traceable changes.
- Prefer preserving naming already used in the specs.
- Prefer adding missing alignment docs over leaving ambiguity for later.
