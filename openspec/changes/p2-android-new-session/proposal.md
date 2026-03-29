# Proposal: p2-android-new-session

## Why

Users need to create new AI agent sessions from their phone by selecting a provider, browsing remote directories on the MacBook, and entering a prompt. This is a primary workflow -- the user is away from the MacBook and wants to kick off a task in a specific project directory. The three-step wizard (provider -> directory -> prompt) keeps the flow focused and prevents mistakes.

## What Changes

| Area | Change |
|------|--------|
| New session flow | 3-step `HorizontalPager` with step indicator. Forward/back navigation between steps. Final step submits `POST /v1/sessions`. On success, navigates to session detail. |
| Provider picker step | 3 large selectable cards (Claude Code / book / OpenClaw). Shows provider icon, name, host status. Disabled if host offline. Auto-selects `host_id`. |
| Directory browser step | Uses `DirectoryBrowser` component (C-05). Shows roots for the selected provider's host. book provider only shows novel roots. Browse subdirectories. Select button. |
| Prompt input step | Summary of selections. Multi-line prompt input (required). Optional model dropdown (sonnet/opus/haiku). "开始" button with loading state. |
| Directory browser component | Reusable component. Fetches dirs from `GET /v1/hosts/:id/browse?path=`. Breadcrumb navigation. Loading shimmer. Inline error with retry. |

## Capabilities

- `new-session-flow`
- `provider-picker-step`
- `directory-browser-step`
- `prompt-input-step`
- `directory-browser-component`

## Affected Areas

- `packages/android/.../ui/newsession/NewSessionScreen.kt` -- pager + step indicator
- `packages/android/.../ui/newsession/NewSessionViewModel.kt` -- state management
- `packages/android/.../ui/newsession/ProviderPickerStep.kt` -- provider selection
- `packages/android/.../ui/newsession/DirectoryBrowserStep.kt` -- directory selection
- `packages/android/.../ui/newsession/PromptInputStep.kt` -- prompt + model + submit
- `packages/android/.../ui/components/DirectoryBrowser.kt` -- reusable directory component
- `packages/android/.../data/repository/WorkspaceRepository.kt` -- browse API calls
- `packages/android/.../data/repository/HostRepository.kt` -- host status

## Dependencies

- Relay REST API: `GET /v1/hosts` (host status), `GET /v1/hosts/:id/roots` (roots), `GET /v1/hosts/:id/browse` (directory listing), `POST /v1/sessions` (create).
- Host status tracking from `p1-relay-workspace-api`.
- Session creation from `p1-relay-session-lifecycle`.
- UI spec from `docs/specs/ui-ux-rd-spec/04_Pages/03_NewSessionScreen.md`.

## Risks

| Risk | Mitigation |
|------|-----------|
| Host goes offline between step 1 and step 3 | Re-check host status before submit; show error if offline |
| Directory browse latency (relay -> companion -> filesystem) | Loading shimmer in DirectoryBrowser; breadcrumb allows quick navigation back |
| Double-tap "开始" creates duplicate sessions | Loading state on button disables re-tap; debounce in ViewModel |

## References

- docs/specs/ui-ux-rd-spec/04_Pages/03_NewSessionScreen.md
- docs/specs/ui-ux-rd-spec/02_Components/COMPONENTS.md (C-05: DirectoryBrowser, C-06: ProviderChip)
- docs/engineering-spec/02_Technical_Design/API_SPEC.md (POST /sessions, GET /hosts, GET /browse)
