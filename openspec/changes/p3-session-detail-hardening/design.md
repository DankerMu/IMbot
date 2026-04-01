## Context

The companion already persists `provider_session_id` mappings across process exits. For `claude` and `book`, cancelling a session kills the local process but does not necessarily destroy the upstream session identity. In practice, the user can still recover the conversation context if relay allows `resume_session` to be dispatched again.

Android session detail currently fails this recovery path because:

- relay forbids `POST /resume` from `cancelled`
- Android only treats `completed` / `failed` as resumable
- foreground session deep links can be delivered to an already-running `MainActivity` without replacing the currently visible detail destination

Separately, Android markdown rendering was already close to spec for headings, emphasis, lists, quotes, links, and code blocks, but formulas and tables were still user-visible gaps during emulator testing.

## Decisions

### D1: `cancelled` remains inactive, but not terminal for resume

**Choice**: `cancelled` can transition to `running` only via `POST /resume`, and only when `provider_session_id` still exists.

```
queued     → running, failed
running    → idle, completed, failed, cancelled
idle       → running, completed, failed, cancelled
completed  → running  (resume only)
failed     → running  (resume only)
cancelled  → running  (resume only)
```

This preserves the user meaning of cancel ("stop the current local process now") without turning a user-initiated stop into permanent data loss.

### D2: Android detail auto-resumes resumable inactive sessions

**Choice**: when the detail page loads a `completed`, `failed`, or `cancelled` session, it immediately calls `resumeSession()`.

**Fallback**: the overflow menu continues to expose "恢复会话" so the user can retry if auto-resume fails or if they revisit a stale cached state.

### D3: Keep Compose Markdown for most content, isolate math in offline KaTeX WebView

**Choice**: do not replace the entire renderer with a WebView. Instead:

- standard Markdown blocks remain in Compose
- inline math and display math render through a local-asset KaTeX WebView
- GFM tables parse into a simple Compose table layout

This keeps scrolling and normal text rendering native while avoiding brittle symbol substitution hacks for formulas.

### D4: Foreground deep links replace the current detail destination

**Choice**: when Android receives `OpenSessionDetail(sessionId)` while already showing a session detail screen, navigation replaces the current detail destination instead of using `launchSingleTop`.

This preserves the expected push/deep-link behavior: tapping a different session while the app is already open must move the user to that exact session so the auto-resume logic runs against the correct record.

### D5: Bundle KaTeX assets inside the APK

**Choice**: package KaTeX CSS/JS/fonts under `app/src/main/assets/katex/` and load them with `file:///android_asset/`.

This ensures formulas render offline inside the emulator/device without network dependency.
