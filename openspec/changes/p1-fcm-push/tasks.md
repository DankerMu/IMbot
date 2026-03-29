# Tasks: p1-fcm-push

## Relay FCM Sender

- [ ] 1.1 Add `firebase-admin` to `packages/relay/package.json` dependencies
- [ ] 1.2 Create `packages/relay/src/push/fcm-adapter.ts` with `PushAdapter` class: `init()` reads `FIREBASE_SERVICE_ACCOUNT` env var (path or inline JSON), initializes `firebase-admin` app; if missing, log warning and mark disabled
- [ ] 1.3 Implement `PushAdapter.notify(sessionId, status, errorMessage?)`: query `push_subscriptions`, build notification title/body per format spec, send via `firebase.messaging().send()`, handle errors (log + delete stale tokens)
- [ ] 1.4 Implement `PushAdapter.notifyHostOffline(hostId, hostName)`: build host-offline notification, send to all tokens
- [ ] 1.5 Wire `PushAdapter.notify()` into `SessionOrchestrator.transition()` for `completed` and `failed` states (fire-and-forget, after DB commit + WS broadcast)
- [ ] 1.6 Wire `PushAdapter.notifyHostOffline()` into `CompanionManager.markOffline()` after WS host_status broadcast
- [ ] 1.7 Add `truncatePrompt(text, maxLen)` utility: truncate to `maxLen` chars, append `"..."` if truncated
- [ ] 1.8 Handle `messaging/registration-token-not-registered` error: DELETE the stale token from `push_subscriptions`

## Android FCM Receiver

- [ ] 2.1 Add Firebase Cloud Messaging dependency to `app/build.gradle.kts` and configure `google-services.json`
- [ ] 2.2 Create `NotificationChannel` (`imbot_sessions`, `会话通知`, `IMPORTANCE_HIGH`) in `IMbotApp.onCreate()`
- [ ] 2.3 Implement `FCMService` extending `FirebaseMessagingService`: `onMessageReceived` dispatches to notification builder or in-app handler based on app foreground state
- [ ] 2.4 Build system notification: title/body from `remoteMessage.notification`, PendingIntent with deep link `imbot://session/{data.session_id}`
- [ ] 2.5 Handle host-offline notifications: deep link to session list (home) instead of specific session
- [ ] 2.6 Implement `onNewToken`: call `POST /v1/push/register` with new token; on failure, save to SharedPreferences for retry
- [ ] 2.7 Register deep link scheme `imbot://` in `AndroidManifest.xml` intent filter for `SessionDetailScreen` activity/route
- [ ] 2.8 Handle cold-start deep link: parse launch intent URI in main activity, navigate to correct screen after initialization

## Push Token Management

- [ ] 3.1 Implement `POST /v1/push/register` route in `packages/relay/src/routes/push.ts`: validate `fcm_token` non-empty, `INSERT OR REPLACE INTO push_subscriptions` with current timestamps
- [ ] 3.2 Create `PushTokenWorker` in Android using WorkManager `PeriodicWorkRequest` (12-hour interval, `ExistingPeriodicWorkPolicy.KEEP`)
- [ ] 3.3 `PushTokenWorker.doWork()`: get token from `FirebaseMessaging.getInstance().token`, call `POST /v1/push/register`, return `Result.success()` or `Result.retry()`
- [ ] 3.4 Schedule `PushTokenWorker` in `IMbotApp.onCreate()` or after onboarding completion
- [ ] 3.5 Register token on first app launch after onboarding: retrieve FCM token and POST to relay
- [ ] 3.6 Verify upsert behavior: register same token twice → single row with updated `updated_at`
