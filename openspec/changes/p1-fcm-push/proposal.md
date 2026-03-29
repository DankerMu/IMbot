# Proposal: p1-fcm-push

## Why

Users need push notifications when sessions complete or fail and when the MacBook host goes offline, even when the Android app is in the background or killed. Without FCM push, the user must actively open the app to check session status, defeating the purpose of a remote control system. The relay already has a `push/` module stub and `push_subscriptions` table; this change wires up the full send/receive/token-management pipeline.

## What Changes

| Area | Change |
|------|--------|
| Relay FCM sender | On session `completed`/`failed` transition, build an FCM notification via `firebase-admin` and send to all registered tokens. On host offline, send a host-offline notification. Include `session_id` in the data payload for Android deep-link. |
| Android FCM receiver | `FCMService` extends `FirebaseMessagingService`. Displays system notification with `NotificationChannel`. Tapping notification deep-links via `imbot://session/{id}` to `SessionDetailScreen`. Foreground app suppresses popup (in-app handling). |
| Push token management | `POST /v1/push/register` saves FCM token with upsert semantics. Android `WorkManager` periodic job refreshes token. Token deduplication via `UNIQUE` constraint on `fcm_token`. |

## Capabilities

- `relay-fcm-sender`
- `android-fcm-receiver`
- `push-token-management`

## Affected Areas

- `packages/relay/src/push/` -- FCM adapter using `firebase-admin`
- `packages/relay/src/session/orchestrator.ts` -- call push on terminal transitions and host offline
- `packages/android/.../service/FCMService.kt` -- push receiver
- `packages/android/.../data/remote/` -- register token API call
- `packages/android/.../di/` -- WorkManager setup for token refresh

## Dependencies

- Relay server session lifecycle (`p1-relay-session-lifecycle`) must emit terminal state transitions.
- `firebase-admin` npm package on relay.
- Firebase Cloud Messaging configured in Android project (`google-services.json`).
- `POST /v1/push/register` endpoint from relay (already in API spec).

## Risks

| Risk | Mitigation |
|------|-----------|
| FCM credentials missing on relay | Push silently skipped when `FIREBASE_SERVICE_ACCOUNT` env var is absent; no crash |
| Token expires or becomes invalid | WorkManager periodic refresh (every 12h); relay handles `messaging/registration-token-not-registered` by deleting stale token |
| Push delivery latency | FCM best-effort; not retried from relay side; user can always pull-to-refresh |

## References

- docs/engineering-spec/02_Technical_Design/BUSINESS_LOGIC.md (Section 7: FCM Push Logic)
- docs/engineering-spec/02_Technical_Design/API_SPEC.md (`POST /v1/push/register`)
- docs/engineering-spec/02_Technical_Design/DATA_MODEL.md (`push_subscriptions` table)
- docs/engineering-spec/02_Technical_Design/ARCHITECTURE.md (FCM Adapter, FCMService)
