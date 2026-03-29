# Capability: relay-auth

## ADDED Requirements

### Requirement: REST Static Bearer Token Validation

All REST endpoints (except `GET /healthz`) SHALL require a valid `Authorization: Bearer <RELAY_STATIC_TOKEN>` header. Invalid or missing tokens MUST result in a 401 response.

#### Scenario: valid token passes authentication

WHEN a request is sent with header `Authorization: Bearer <correct-token>`
THEN the request proceeds to the route handler
AND no authentication error is returned

#### Scenario: missing Authorization header returns 401

WHEN a request is sent without an `Authorization` header
THEN the response is HTTP 401
AND the body is `{ "error": "unauthenticated" }`

#### Scenario: invalid token returns 401

WHEN a request is sent with header `Authorization: Bearer wrong-token-value`
THEN the response is HTTP 401
AND the body is `{ "error": "unauthenticated" }`

#### Scenario: malformed Authorization header returns 401

WHEN a request is sent with header `Authorization: Basic dXNlcjpwYXNz`
THEN the response is HTTP 401
AND the body is `{ "error": "unauthenticated" }`

#### Scenario: healthz is exempt from auth

WHEN `GET /healthz` is called without any Authorization header
THEN the response is HTTP 200 (no auth required)

---

### Requirement: WebSocket Token Validation (Android)

Android WebSocket connections at `/v1/ws` SHALL be authenticated via query parameter `token` or first-message `auth` action. Unauthenticated connections MUST be closed with code 4001.

#### Scenario: WS auth via query parameter

WHEN a WebSocket connection is opened to `/v1/ws?token=<correct-token>`
THEN the connection is accepted and remains open
AND the client can send `subscribe`/`ping` messages

#### Scenario: WS auth via first message

WHEN a WebSocket connection is opened to `/v1/ws` (no query param)
AND the first message sent is `{ "action": "auth", "token": "<correct-token>" }`
THEN the connection is authenticated and remains open

#### Scenario: WS missing token closes connection

WHEN a WebSocket connection is opened to `/v1/ws` without a token query param
AND the first message is not an auth message (e.g., `{ "action": "subscribe", "session_id": "..." }`)
THEN the server closes the connection with code 4001
AND the close reason is `"unauthenticated"`

#### Scenario: WS invalid token closes connection

WHEN a WebSocket connection is opened to `/v1/ws?token=wrong-token`
THEN the server closes the connection with code 4001
AND the close reason is `"unauthenticated"`

#### Scenario: WS auth via first message with wrong token

WHEN a WebSocket connection is opened to `/v1/ws`
AND the first message is `{ "action": "auth", "token": "wrong-token" }`
THEN the server sends `{ "type": "error", "code": "unauthenticated", "message": "..." }`
AND then closes the connection with code 4001

---

### Requirement: WebSocket Token Validation (Companion)

Companion WebSocket connections at `/v1/companion` SHALL be authenticated via query parameter `token`. The `host_id` query parameter is also required.

#### Scenario: companion auth with valid token and host_id

WHEN a WebSocket connection is opened to `/v1/companion?token=<correct-token>&host_id=macbook-1`
THEN the connection is accepted
AND the host is registered as online

#### Scenario: companion missing token closes connection

WHEN a WebSocket connection is opened to `/v1/companion?host_id=macbook-1` (no token)
THEN the server closes the connection with code 4001

#### Scenario: companion missing host_id closes connection

WHEN a WebSocket connection is opened to `/v1/companion?token=<correct-token>` (no host_id)
THEN the server closes the connection with code 4002
AND the close reason is `"missing host_id"`

#### Scenario: companion invalid token closes connection

WHEN a WebSocket connection is opened to `/v1/companion?token=wrong&host_id=macbook-1`
THEN the server closes the connection with code 4001

---

### Requirement: Token Timing-Safe Comparison

Token comparison SHALL use a constant-time comparison function to prevent timing attacks.

#### Scenario: token comparison uses constant-time algorithm

WHEN the auth middleware compares the provided token with the configured token
THEN it uses `crypto.timingSafeEqual` or equivalent
AND the comparison time does not vary based on how many characters match
