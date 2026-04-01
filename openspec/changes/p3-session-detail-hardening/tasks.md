# Tasks: p3-session-detail-hardening

## 1. Session Lifecycle Recovery

- [x] 1.1 Update shared wire transition metadata so `cancelled -> running` is valid
- [x] 1.2 Update relay transition guards so `POST /resume` accepts `cancelled`
- [x] 1.3 Preserve the existing `session_not_resumable` guard when `provider_session_id` is missing
- [x] 1.4 Update relay unit / contract / E2E tests that previously expected `409 state_conflict` after cancel

## 2. Android Detail Recovery UX

- [x] 2.1 Treat `cancelled` as resumable in detail UI helpers
- [x] 2.2 Auto-resume `completed` / `failed` / `cancelled` sessions when detail loads
- [x] 2.3 Keep manual resume action in the overflow menu as fallback
- [x] 2.4 Update Android unit tests for placeholder copy and auto-resume coverage
- [x] 2.5 Replace the current detail route when a foreground deep link targets a different session

## 3. Markdown Rendering Hardening

- [x] 3.1 Bundle offline KaTeX assets inside the Android app
- [x] 3.2 Render inline and display math through the bundled KaTeX WebView
- [x] 3.3 Parse and render GFM tables as structured cells instead of raw pipe text
- [x] 3.4 Add Android parser/unit coverage for math and tables
- [x] 3.5 Re-run emulator verification for headings, emphasis, lists, blockquotes, links, formulas, and tables

## 4. Spec And Test Plan Alignment

- [x] 4.1 Add this OpenSpec change to the requirement mapping index
- [x] 4.2 Update engineering spec lifecycle/API docs for resumable `cancelled`
- [x] 4.3 Update Android UI/UX docs for auto-resume and offline formula/table rendering
- [x] 4.4 Update E2E plan docs to reflect cancelled-resume behavior and markdown coverage

## 5. Post-Review Hardening

- [x] 5.1 Block javascript: URI XSS in KaTeX WebView markdown links (URL scheme allowlist)
- [x] 5.2 Restrict file:// access in KaTeX WebView to katex/ assets only
- [x] 5.3 Add auto-resume integration test for failed sessions in DetailViewModelTest
- [x] 5.4 Add contract test for cancelled resume rejection without provider_session_id
