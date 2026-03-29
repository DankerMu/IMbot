# Capability: android-fcm-receiver

Receive FCM push notifications on Android, display system notifications, and deep-link to the appropriate session detail screen. Suppress system notifications when the app is in the foreground.

## ADDED Requirements

### Requirement: FCMService Extends FirebaseMessagingService

The Android app SHALL implement `FCMService` extending `FirebaseMessagingService`. The service SHALL handle `onMessageReceived` for incoming push messages and `onNewToken` for token refresh events.

#### Scenario: Background push -- system notification shown

WHEN the app is in the background or killed
AND a push message arrives with `notification.title = "✓ 分析代码 已完成"` and `data.session_id = "sess-123"`
THEN a system notification is displayed using the `imbot_sessions` NotificationChannel
AND the notification shows the correct title and body
AND tapping the notification opens the app

#### Scenario: Foreground push -- no system notification (handled in-app)

WHEN the app is in the foreground
AND a push message arrives
THEN no system notification popup is displayed
AND the message data is delivered to the in-app handler (e.g., triggering a session list refresh or a Snackbar)

---

### Requirement: NotificationChannel Configuration

The app SHALL create a `NotificationChannel` with id `imbot_sessions`, name `会话通知`, and importance `IMPORTANCE_HIGH` during `Application.onCreate()`. This channel SHALL be used for all session-related push notifications.

#### Scenario: Notification channel exists on API 26+

WHEN the app starts on Android 8.0 (API 26) or higher
THEN the `imbot_sessions` NotificationChannel is created if it does not already exist
AND the channel has `IMPORTANCE_HIGH` (shows heads-up notification)

---

### Requirement: Deep Link on Notification Tap

Tapping a push notification SHALL navigate the user to `SessionDetailScreen` for the session specified in the `data.session_id` field. The deep link URI format SHALL be `imbot://session/{session_id}`.

#### Scenario: Tap notification -- app opens to correct session

WHEN the user taps a notification with `data.session_id = "sess-456"`
THEN the app opens (or comes to foreground)
AND navigates to `SessionDetailScreen` with `sessionId = "sess-456"`
AND the back button from detail returns to the session list

#### Scenario: Tap notification -- app was killed (cold start)

WHEN the app is not running
AND the user taps a notification with `data.session_id = "sess-789"`
THEN the app cold-starts
AND the launch intent contains the deep link `imbot://session/sess-789`
AND the app navigates to `SessionDetailScreen` after initialization

---

### Requirement: Token Refresh Re-Registration

When Firebase rotates the FCM token, `onNewToken` SHALL be called. The service SHALL immediately re-register the new token with the relay via `POST /v1/push/register`.

#### Scenario: Token refresh -- re-register with relay

WHEN `onNewToken(newToken)` is called by Firebase
THEN the service calls `POST /v1/push/register` with `{ fcm_token: newToken }`
AND the old token is effectively replaced (relay upsert semantics)
AND if the API call fails (e.g., no network), the token is saved locally and retried via WorkManager

---

### Requirement: Host Offline Notification Handling

Push notifications for host offline events SHALL be displayed as system notifications but SHALL NOT deep-link to a specific session. The notification SHALL navigate to the session list (home screen).

#### Scenario: Host offline push -- notification shown

WHEN a push arrives with `data.action = "host_offline"` and `notification.title = "MacBook Pro 已离线"`
THEN a system notification is shown with the title and body
AND tapping the notification opens the app to the session list screen
