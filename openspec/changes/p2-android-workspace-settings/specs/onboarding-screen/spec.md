# Capability: onboarding-screen

First-launch configuration screen for relay connection setup. Shown when no saved relay URL + token exist.

## ADDED Requirements

### Requirement: First-Launch Detection and Navigation

The app SHALL check SharedPreferences on startup for saved relay URL and token. If either is missing, the app SHALL navigate to OnboardingScreen instead of the home screen (SessionListScreen). The check MUST happen before any network requests.

#### Scenario: First launch -- onboarding shown

WHEN the app launches for the first time (no relay URL or token in SharedPreferences)
THEN the OnboardingScreen is displayed
AND the home screen is not shown

#### Scenario: Subsequent launch -- onboarding skipped

WHEN the app launches with a valid relay URL and token already saved in SharedPreferences
THEN the app navigates directly to SessionListScreen (home)
AND the OnboardingScreen is not shown

### Requirement: Onboarding Layout

The OnboardingScreen SHALL display: the IMbot logo and title at the top, a Relay URL text input (keyboard type URL, placeholder "https://"), a Token text input (password field with visibility toggle), a "测试连接" button, a result area (initially hidden), and a "开始使用" button (initially disabled). The layout SHALL be vertically centered and padded with `spacing.lg` (24dp).

#### Scenario: Initial layout state

WHEN the OnboardingScreen is displayed
THEN the logo and "IMbot" title are visible
AND the Relay URL input is empty with placeholder "https://"
AND the Token input is empty with password masking
AND the "测试连接" button is visible but disabled (both fields empty)
AND the "开始使用" button is not visible
AND the result area is hidden

### Requirement: Input Validation for Test Button

The "测试连接" button SHALL be enabled only when both the Relay URL and Token fields are non-empty. The URL field SHALL validate basic URL format (starts with http:// or https://).

#### Scenario: Empty URL and token -- test button disabled

WHEN both the Relay URL and Token fields are empty
THEN the "测试连接" button is disabled (grayed out)

#### Scenario: Only URL filled -- test button disabled

WHEN the Relay URL is filled but Token is empty
THEN the "测试连接" button remains disabled

#### Scenario: Both fields filled -- test button enabled

WHEN both fields have non-empty values
THEN the "测试连接" button becomes enabled

### Requirement: Connection Test

Tapping "测试连接" SHALL call `GET /healthz` on the specified relay URL with the provided token as Bearer auth. During the request, the button SHALL show a loading spinner. On success, the result area SHALL display host statuses and enable the "开始使用" button. On failure, the result area SHALL display a clear error message.

#### Scenario: Test connection -- success

WHEN the user enters a valid relay URL and token, then taps "测试连接"
THEN the button shows a loading spinner
AND a GET request is sent to `{relayUrl}/healthz` with `Authorization: Bearer {token}`
AND on 200 response, the result area shows:
  - "✓ 连接成功！Relay v{version}"
  - "MacBook: online" or "MacBook: offline"
  - "OpenClaw: online" or "OpenClaw: offline"
AND the "开始使用" button appears and is enabled

#### Scenario: Test connection -- unreachable URL

WHEN the user enters an unreachable URL and taps "测试连接"
THEN the button shows a loading spinner briefly
AND on network error, the result area shows "✗ 无法连接" in red text
AND the "开始使用" button remains hidden

#### Scenario: Test connection -- invalid token (401)

WHEN the user enters a valid URL but wrong token and taps "测试连接"
THEN the relay returns 401
AND the result area shows "✗ 认证失败" in red text
AND the "开始使用" button remains hidden

#### Scenario: Test connection -- timeout

WHEN the /healthz request takes longer than 10 seconds
THEN the request is cancelled
AND the result area shows "✗ 连接超时" in red text

### Requirement: Save and Proceed

Tapping "开始使用" SHALL save the relay URL and token to SharedPreferences (token stored using Android EncryptedSharedPreferences) and navigate to SessionListScreen, replacing the navigation stack so back-press exits the app rather than returning to onboarding.

#### Scenario: Proceed saves config and navigates to home

WHEN the user taps "开始使用" after a successful test
THEN the relay URL and token are saved to SharedPreferences
AND the app navigates to SessionListScreen
AND the onboarding screen is removed from the back stack

#### Scenario: Restart after onboarding -- goes to home directly

WHEN the user completes onboarding and restarts the app
THEN the app launches directly to SessionListScreen
AND OnboardingScreen is not shown
