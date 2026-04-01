# Proposal: p3-session-detail-hardening

## Why

Real Android testing exposed two production-grade failures in the session detail flow:

1. A user-cancelled session could become permanently stuck even when the provider session mapping still existed. A bad CLI command such as `/skills` on `book`/`claude` could lead the user to cancel, after which Android and relay both refused to resume the session.
2. Markdown rendering on Android was incomplete in important user-visible cases. Formula output needed an offline renderer, and GFM tables were shown as raw pipe text instead of structured cells.

These are hardening issues in the highest-frequency surface of the product, so the fix must cover runtime policy, Android UX, tests, and specs together.

## What Changes

| Area | Change |
|------|--------|
| Session lifecycle | Allow `cancelled -> running` via `POST /resume` when `provider_session_id` still exists. Keep `cancelled` as an inactive state, but not a dead end. |
| Android detail recovery UX | Auto-resume `completed` / `failed` / `cancelled` sessions when the detail page opens; keep a manual "恢复会话" fallback action in the overflow menu; replace the current detail route when a foreground deep link targets a different session. |
| Markdown rendering | Keep the existing Compose renderer for standard Markdown, add bundled offline KaTeX for inline/display math, and render GFM tables as structured grid cells. |
| Verification | Update unit / contract / E2E expectations from "cancelled cannot resume" to "cancelled can resume if mapping survives", and extend markdown verification to formulas and tables. |

## Capabilities

- `session-lifecycle`
- `android-session-detail`
- `markdown-renderer`

## Affected Areas

- `packages/wire/src/enums.ts`
- `packages/relay/src/session/transitions.ts`
- `packages/relay/src/session/orchestrator.ts`
- `packages/android/app/src/main/kotlin/com/imbot/android/ui/detail/`
- `packages/android/app/src/main/assets/katex/`
- `tests/unit/`
- `tests/contract/`
- `tests/e2e/`
- `docs/engineering-spec/`
- `docs/specs/ui-ux-rd-spec/`

## Dependencies

- `persistent-interactive-sessions` for persistent provider session IDs, `idle`, and resume semantics
- `p2-android-session-detail` for the detail screen surface
- `p1-companion-session-management` for persisted session mappings on the companion side

## Risks

| Risk | Mitigation |
|------|-----------|
| Resume from `cancelled` could still fail when the provider mapping is gone | Keep the existing `session_not_resumable` guard and surface resume failure to Android instead of silently hanging |
| Bundled math renderer could regress scrolling or layout | Use WebView only for math-containing fragments, keep normal Markdown on Compose, and size the WebView to its measured content |
| Table rendering could drift from GFM | Cover header parsing, alignment, and cell rendering with dedicated Android parser tests and emulator verification |
| Foreground notification / deep-link taps could leave the user on the wrong detail screen | Replace the active detail destination instead of relying on `launchSingleTop` when another session deep link arrives |

## References

- `openspec/changes/persistent-interactive-sessions/`
- `openspec/changes/p2-android-session-detail/`
- `docs/E2E_TEST_PLAN.md`
