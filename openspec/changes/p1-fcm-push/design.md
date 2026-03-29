# Design: p1-fcm-push

## Key Decisions

### 1. firebase-admin on Relay (Server-Side Send)

**Decision**: Use `firebase-admin` SDK on the relay server to send FCM messages.

**Rationale**: The relay is the single source of truth for session state transitions. It already has the `push/` module stub. Server-side sending is the standard pattern -- Android never sends push to itself.

**Trade-off**: Requires Firebase project setup and a service account JSON. Mitigated by making FCM optional (no-op when config absent).

### 2. Data-Only Messages with Notification Fallback

**Decision**: Send FCM messages with both `notification` (for system tray) and `data` (for deep link) payloads.

**Rationale**: `notification` payload ensures the system displays a notification even when the app is killed. `data` payload carries `session_id` and `action` for deep-link routing. When the app is in the foreground, `FCMService.onMessageReceived` handles it in-app (no system notification popup).

### 3. Token Upsert via UNIQUE Constraint

**Decision**: `push_subscriptions.fcm_token` has a `UNIQUE` constraint. Registration uses `INSERT OR REPLACE` semantics.

**Rationale**: A single device only needs one token row. Re-registering the same token is a no-op (upsert), and a refreshed token replaces the old one. This avoids duplicate push delivery.

### 4. WorkManager for Token Refresh (Not onTokenRefresh Alone)

**Decision**: Use both `FirebaseMessagingService.onNewToken` (reactive) and a `WorkManager` `PeriodicWorkRequest` every 12 hours (proactive) to refresh the FCM token.

**Rationale**: `onNewToken` fires when Firebase rotates the token, but it is not guaranteed to fire after app updates or data clears. The periodic job ensures the relay always has a valid token.

### 5. Host Offline Notification

**Decision**: When the relay detects a host going offline (heartbeat timeout), send a push notification to all registered tokens.

**Rationale**: The user may be away from the phone and unaware that their MacBook lost connectivity. A push notification surfaces this critical infrastructure event.

### 6. No Retry on Push Failure

**Decision**: If FCM `send()` throws, log the error but do not retry.

**Rationale**: FCM delivery is best-effort. Retrying adds complexity and may spam the user if the token is permanently invalid. Stale tokens are cleaned up on `messaging/registration-token-not-registered` errors.

## Data Flow

```
Session completes/fails
        │
        ▼
SessionOrchestrator.transition()
        │
        ▼
PushAdapter.notify(sessionId, status, errorMessage?)
        │
        ├── Query push_subscriptions (all tokens)
        │
        ├── Build FCM message:
        │     notification: { title, body }
        │     data: { session_id, action: "open_session" }
        │
        ├── For each token: firebase.messaging().send(message)
        │     ├── Success → done
        │     └── Error → log; if token-not-registered → DELETE token
        │
        └── Return (fire-and-forget, don't block transition)
```

```
Host goes offline (heartbeat timeout)
        │
        ▼
CompanionManager.markOffline(hostId)
        │
        ├── Broadcast host_status via WS
        │
        └── PushAdapter.notifyHostOffline(hostId, hostName)
              │
              └── Same flow as above, title: "{hostName} 已离线"
```

## Notification Format

| Trigger | Title | Body |
|---------|-------|------|
| Session completed | `✓ {prompt摘要(30chars)} 已完成` | (empty) |
| Session failed | `✗ {prompt摘要(30chars)} 失败` | `{errorMessage}` or `未知错误` |
| Host offline | `{hostName} 已离线` | `设备连接中断` |

## Android Deep Link

```
imbot://session/{session_id}
```

Handled by `SessionDetailScreen` route. If the app is cold-started from notification, the activity intent parser extracts the deep link URI and navigates accordingly.
