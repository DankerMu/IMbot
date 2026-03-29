# Tasks: p2-android-new-session

## New Session Flow

- [ ] 1.1 Create `NewSessionScreen` composable with `HorizontalPager` (3 pages), step indicator, and "上一步"/"下一步"/"开始" buttons
- [ ] 1.2 Create `NewSessionViewModel` with `NewSessionUiState` StateFlow: step, provider, hostId, hosts, roots, cwd, prompt, model, isCreating, error
- [ ] 1.3 Implement step indicator: 3 dots with connecting lines, filled dot for completed/current step, outlined for future
- [ ] 1.4 Implement step navigation: "下一步" advances pager, "上一步" goes back, state preserved across transitions
- [ ] 1.5 Implement session creation: `viewModel.createSession()` calls `POST /v1/sessions`, sets `isCreating = true`, navigates on success, Snackbar on failure
- [ ] 1.6 Implement double-tap prevention: ignore `createSession()` calls when `isCreating == true`

## Provider Picker Step

- [ ] 2.1 Create `ProviderPickerStep` composable with 3 large selectable cards (Claude Code, book, OpenClaw)
- [ ] 2.2 Fetch host status on step enter: `GET /v1/hosts` → populate `hosts` in ViewModel
- [ ] 2.3 Implement card states: selected (filled, border), unselected (outlined), disabled (grayed + "离线" label)
- [ ] 2.4 Implement provider-to-host mapping: Claude/book → macbook host, OpenClaw → relay-local
- [ ] 2.5 Implement single selection: tapping one card deselects others
- [ ] 2.6 Enable/disable "下一步" button based on provider selection

## Directory Browser Step

- [ ] 3.1 Create `DirectoryBrowserStep` composable wrapping the reusable `DirectoryBrowser` component
- [ ] 3.2 On step enter: fetch roots via `GET /v1/hosts/:id/roots`, filter by provider for book
- [ ] 3.3 Pass roots as initial entries to `DirectoryBrowser`; pass `hostId` for subsequent browse calls
- [ ] 3.4 Wire `onSelect` callback: save `cwd` to ViewModel state, enable "下一步"
- [ ] 3.5 Show "当前选择: {path}" below the browser when a directory is selected

## Prompt Input Step

- [ ] 4.1 Create `PromptInputStep` composable with selection summary, prompt TextField, model dropdown, "开始" button
- [ ] 4.2 Implement summary section: provider icon + name, directory path (last 2 segments)
- [ ] 4.3 Implement multi-line prompt input: auto-grow, required (empty disables "开始")
- [ ] 4.4 Implement model dropdown: options from provider's supported models (sonnet, opus, haiku); default sonnet
- [ ] 4.5 Implement "开始" button states: disabled (empty prompt), enabled (has prompt), loading (isCreating)

## Directory Browser Component

- [ ] 5.1 Create reusable `DirectoryBrowser` composable with props: `hostId: String`, `initialPath: String`, `onSelect: (String) -> Unit`
- [ ] 5.2 Implement directory fetching: `GET /v1/hosts/:id/browse?path=` via `WorkspaceRepository`
- [ ] 5.3 Implement breadcrumb bar: horizontal scrollable row of path segments, each tappable
- [ ] 5.4 Implement directory entry list: folder icon + name, tap to navigate into
- [ ] 5.5 Implement loading shimmer: show during API calls
- [ ] 5.6 Implement inline error + retry: show on API failure with "重试" button
- [ ] 5.7 Implement empty state: "无子目录" text when directory has no subdirectories
- [ ] 5.8 Implement "选择此目录" button at bottom: invokes `onSelect(currentPath)`
