# Capability: relay-fcm-sender

Send FCM push notifications when sessions reach terminal states (completed/failed) and when a host goes offline. Uses `firebase-admin` SDK. Gracefully degrades when FCM is not configured.

## ADDED Requirements

### Requirement: Send Push on Session Completed

The relay push adapter SHALL send an FCM notification to all registered push tokens when a session transitions to `completed` status. The notification title SHALL be `✓ {prompt摘要} 已完成` where `prompt摘要` is the first 30 characters of `initial_prompt`. The data payload SHALL include `session_id` and `action: "open_session"` for deep linking.

#### Scenario: Session completes -- push sent with correct title and body

WHEN a session with `initial_prompt = "帮我分析一下这个项目的架构设计"` transitions to `completed`
AND there is at least one registered push subscription
THEN an FCM message is sent with `notification.title = "✓ 帮我分析一下这个项目的架构设计 已完成"`
AND `notification.body` is empty
AND `data.session_id` equals the session's ID
AND `data.action` equals `"open_session"`

#### Scenario: Session completes with long prompt -- title truncated

WHEN a session with an `initial_prompt` longer than 30 characters transitions to `completed`
THEN the title uses only the first 30 characters of the prompt followed by `"..."`: `✓ {first30chars}... 已完成`

---

### Requirement: Send Push on Session Failed

The relay push adapter SHALL send an FCM notification to all registered push tokens when a session transitions to `failed` status. The notification title SHALL be `✗ {prompt摘要} 失败`. The body SHALL contain the `error_message` or `未知错误` if no error message is available.

#### Scenario: Session fails -- push sent with error summary

WHEN a session transitions to `failed` with `error_message = "API rate limit exceeded"`
THEN an FCM message is sent with `notification.title = "✗ {prompt摘要} 失败"`
AND `notification.body = "API rate limit exceeded"`
AND `data.session_id` equals the session's ID

#### Scenario: Session fails with no error message

WHEN a session transitions to `failed` with `error_message = null`
THEN `notification.body = "未知错误"`

---

### Requirement: Send Push on Host Offline

The relay push adapter SHALL send an FCM notification when a host is marked offline due to heartbeat timeout or WebSocket disconnection. The notification title SHALL be `{hostName} 已离线` and body SHALL be `设备连接中断`.

#### Scenario: Host goes offline -- push notification sent

WHEN the relay marks host `macbook-1` (name: "MacBook Pro") as `offline`
THEN an FCM message is sent with `notification.title = "MacBook Pro 已离线"`
AND `notification.body = "设备连接中断"`
AND `data.action = "host_offline"` and `data.host_id = "macbook-1"`

---

### Requirement: Graceful Degradation Without FCM Config

The push adapter SHALL initialize silently when Firebase credentials are not configured. All push operations SHALL be no-ops when the adapter is not initialized, without throwing errors or crashing the relay.

#### Scenario: No FCM config -- push silently skipped (no crash)

WHEN the relay starts without `FIREBASE_SERVICE_ACCOUNT` environment variable or without a valid service account JSON
THEN the push adapter logs a warning: `"FCM not configured, push notifications disabled"`
AND the adapter marks itself as disabled
AND subsequent calls to `notify()` and `notifyHostOffline()` return immediately without error

---

### Requirement: No-Op When No Subscriptions

The push adapter SHALL query `push_subscriptions` before attempting to send. If no subscriptions exist, the adapter SHALL return immediately without calling the FCM API.

#### Scenario: No push subscriptions -- no-op

WHEN a session transitions to `completed`
AND the `push_subscriptions` table is empty
THEN no FCM API call is made
AND no error is logged

---

### Requirement: Handle Push Delivery Failure

The push adapter SHALL catch errors from `firebase.messaging().send()`. Failures SHALL be logged but not retried. If the error indicates an invalid/expired token (`messaging/registration-token-not-registered`), the stale token SHALL be deleted from `push_subscriptions`.

#### Scenario: Push delivery fails -- logged but not retried

WHEN `firebase.messaging().send()` throws a transient error (e.g., network timeout)
THEN the error is logged at `warn` level with the token (masked) and error message
AND the push is NOT retried
AND the session transition is NOT affected

#### Scenario: Stale token detected -- token deleted

WHEN `firebase.messaging().send()` throws `messaging/registration-token-not-registered`
THEN the corresponding row in `push_subscriptions` is deleted
AND a debug log records the cleanup

---

### Requirement: Push Is Fire-and-Forget

The push notification send SHALL NOT block the session state transition. The `notify()` call SHALL be invoked after the transition is committed to the database and the WebSocket broadcast is sent.

#### Scenario: Push send does not delay state transition

WHEN a session transitions to `completed`
THEN the database is updated and WebSocket broadcast is sent BEFORE `notify()` is called
AND even if `notify()` takes 2 seconds (slow network), the transition has already been visible to connected clients
