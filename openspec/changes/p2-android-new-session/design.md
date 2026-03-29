# Design: p2-android-new-session

## Key Decisions

### 1. HorizontalPager for Step Navigation

**Decision**: Use Jetpack Compose `HorizontalPager` with `userScrollEnabled = false` (programmatic navigation only via next/back buttons). A step indicator at the top shows progress.

**Rationale**: Pager provides smooth horizontal animation between steps. Disabling swipe prevents accidental navigation. The step indicator gives clear progress feedback.

### 2. State Preserved Across Steps

**Decision**: All step state (selected provider, selected directory, prompt text, model) is held in `NewSessionViewModel` as a single `NewSessionUiState`. Navigating back preserves all selections.

**Rationale**: The user should never lose their selections when going back. Holding state in the ViewModel (not per-page composable) ensures survival across step transitions.

### 3. Provider Determines Host Automatically

**Decision**: Selecting a provider automatically determines the `host_id`: Claude Code and book map to the MacBook host, OpenClaw maps to `relay-local`. The user does not manually select a host.

**Rationale**: With the current architecture (single MacBook, single relay), provider-to-host mapping is deterministic. Exposing a host picker would add complexity for zero benefit.

### 4. book Provider Restricted to Novel Roots

**Decision**: When the selected provider is `book`, the directory browser step only shows roots with `provider == "book"` (novel directories). Other roots are hidden.

**Rationale**: Per PRD, `book` is used for novel writing and should only access novel project directories. Showing code project roots for `book` would be confusing.

### 5. Reusable DirectoryBrowser Component

**Decision**: The directory browser is implemented as a standalone reusable `@Composable` (not tied to the new-session flow). It accepts `hostId`, `initialPath`, and `onSelect` callback. The same component can be used in the workspace management screen.

**Rationale**: Directory browsing is needed in both the new-session flow and the workspace screen. A reusable component avoids duplication.

### 6. Double-Tap Prevention

**Decision**: The "开始" button enters a loading state on tap, disabling itself. The ViewModel ignores duplicate `createSession()` calls when `isCreating == true`.

**Rationale**: Network latency between tap and API response could lead to accidental double-taps creating duplicate sessions.

## State Architecture

```
NewSessionViewModel
    │
    ├── HostRepository
    │   └── GET /v1/hosts (status)
    │
    ├── WorkspaceRepository
    │   ├── GET /v1/hosts/:id/roots
    │   └── GET /v1/hosts/:id/browse?path=
    │
    ├── SessionRepository
    │   └── POST /v1/sessions (create)
    │
    └── NewSessionUiState (StateFlow)
        ├── step: Int (1, 2, 3)
        ├── provider: Provider?
        ├── hostId: String?
        ├── hosts: List<HostInfo>
        ├── roots: List<WorkspaceRoot>
        ├── cwd: String?
        ├── prompt: String
        ├── model: String ("sonnet")
        ├── isCreating: Boolean
        └── error: String?
```

## Flow Sequence

```
Step 1: Provider Picker
    │
    ├── On enter: fetch hosts (GET /v1/hosts)
    ├── Display 3 provider cards with host status
    ├── User taps provider → set provider + hostId
    └── "下一步" → advance to step 2
         │
Step 2: Directory Browser
    │
    ├── On enter: fetch roots (GET /v1/hosts/:id/roots)
    │              filter by provider if book
    ├── Display DirectoryBrowser at root level
    ├── User browses and selects directory
    └── "下一步" → advance to step 3
         │
Step 3: Prompt Input
    │
    ├── Display summary (provider + directory)
    ├── Multi-line prompt input
    ├── Model dropdown (sonnet/opus/haiku)
    ├── "开始" → POST /v1/sessions
    │    ├── Success → navigate to SessionDetailScreen
    │    └── Failure → Snackbar error + stay on step 3
    └── "上一步" → back to step 2 (preserving prompt)
```
