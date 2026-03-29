# Capability: push-token-management

Register, refresh, and deduplicate FCM push tokens. The relay stores tokens in `push_subscriptions`; the Android app registers on first launch and periodically refreshes via WorkManager.

## ADDED Requirements

### Requirement: Register FCM Token via REST API

The relay SHALL accept `POST /v1/push/register` with body `{ "fcm_token": "<token>" }`. The token SHALL be saved to the `push_subscriptions` table with upsert semantics: if the token already exists, update `updated_at`; if new, insert a new row.

#### Scenario: Register new token -- saved

WHEN `POST /v1/push/register` is called with `{ "fcm_token": "abc123" }`
AND no row exists with `fcm_token = "abc123"`
THEN a new row is inserted into `push_subscriptions` with `fcm_token = "abc123"` and current timestamps
AND the response is `200 { "ok": true }`

#### Scenario: Register same token -- upsert (no duplicate)

WHEN `POST /v1/push/register` is called with `{ "fcm_token": "abc123" }`
AND a row already exists with `fcm_token = "abc123"`
THEN the existing row's `updated_at` is updated to the current timestamp
AND no duplicate row is created
AND the response is `200 { "ok": true }`

#### Scenario: Register with empty token -- rejected

WHEN `POST /v1/push/register` is called with `{ "fcm_token": "" }` or missing `fcm_token`
THEN the response is `400 { "error": "invalid_request", "message": "fcm_token is required" }`

---

### Requirement: Token Refresh via WorkManager

The Android app SHALL schedule a `PeriodicWorkRequest` using WorkManager that runs every 12 hours. The job SHALL retrieve the current FCM token via `FirebaseMessaging.getInstance().token` and call `POST /v1/push/register` to ensure the relay has the latest token.

#### Scenario: Token refresh -- new token registered, old one replaced

WHEN the WorkManager job fires
AND Firebase returns a new token different from the previously registered one
THEN the app calls `POST /v1/push/register` with the new token
AND the relay upserts the new token (old token row remains until it fails delivery and gets cleaned up)

#### Scenario: Token refresh -- same token, no-op

WHEN the WorkManager job fires
AND Firebase returns the same token as before
THEN the app still calls `POST /v1/push/register` (idempotent upsert)
AND the relay updates `updated_at` without creating a duplicate

#### Scenario: Token refresh -- network unavailable

WHEN the WorkManager job fires but the relay is unreachable
THEN WorkManager retries with its built-in exponential backoff
AND the token is eventually registered when network is available

---

### Requirement: Initial Token Registration on App Launch

The Android app SHALL register the FCM token with the relay on first successful app launch (after onboarding/relay configuration). This ensures push notifications work from the first session.

#### Scenario: First launch -- token registered

WHEN the app completes onboarding and has a valid relay URL + auth token
THEN the app retrieves the FCM token from Firebase
AND calls `POST /v1/push/register`
AND push notifications are functional for subsequent sessions

---

### Requirement: Token Deduplication on Relay

The `push_subscriptions` table SHALL enforce `UNIQUE` on `fcm_token`. This prevents duplicate push delivery to the same device.

#### Scenario: Concurrent registration of same token -- no duplicate

WHEN two concurrent `POST /v1/push/register` requests arrive with the same `fcm_token`
THEN exactly one row exists in `push_subscriptions` for that token after both complete
AND no integrity constraint error is returned to the client
