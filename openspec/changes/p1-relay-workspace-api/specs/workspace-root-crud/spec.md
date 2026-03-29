# Capability: workspace-root-crud

## ADDED Requirements

### Requirement: Add Workspace Root

`POST /v1/hosts/:hostId/roots` SHALL add a new workspace root for the specified host and provider. The root MUST have a unique combination of (host_id, provider, path).

#### Scenario: add root success

WHEN `POST /v1/hosts/macbook-1/roots` is called with `{ "provider": "claude", "path": "/Users/danker/Projects", "label": "Projects" }`
AND host `macbook-1` exists
AND no root with the same (host_id, provider, path) combination exists
THEN a new `workspace_roots` record is inserted
AND the response is `201` with the root object containing `id`, `host_id`, `provider`, `path`, `label`, `created_at`

#### Scenario: add duplicate root returns 409

WHEN `POST /v1/hosts/macbook-1/roots` is called with `{ "provider": "claude", "path": "/Users/danker/Projects" }`
AND a root with `host_id: "macbook-1"`, `provider: "claude"`, `path: "/Users/danker/Projects"` already exists
THEN the response is `409` with `{ "error": "state_conflict", "message": "Root already exists for this host, provider, and path" }`

#### Scenario: add root for non-existent host returns 404

WHEN `POST /v1/hosts/nonexistent/roots` is called
THEN the response is `404` with `{ "error": "not_found" }`

#### Scenario: add root with missing path returns 400

WHEN `POST /v1/hosts/macbook-1/roots` is called with `{ "provider": "claude" }` (no path)
THEN the response is `400` with `{ "error": "invalid_request", "message": "path is required" }`

#### Scenario: add root with empty path returns 400

WHEN `POST /v1/hosts/macbook-1/roots` is called with `{ "provider": "claude", "path": "" }`
THEN the response is `400` with `{ "error": "invalid_request", "message": "path must not be empty" }`

#### Scenario: add root to relay-local host for openclaw

WHEN `POST /v1/hosts/relay-local/roots` is called with `{ "provider": "openclaw", "path": "/home/user/projects", "label": "VPS Projects" }`
THEN the root is created successfully
AND the response is `201`

---

### Requirement: List Workspace Roots by Host

`GET /v1/hosts/:hostId/roots` SHALL return all workspace roots for the specified host.

#### Scenario: list roots by host

WHEN `GET /v1/hosts/macbook-1/roots` is called
AND host `macbook-1` has 3 workspace roots
THEN the response is `200` with a `roots` array containing 3 entries
AND each entry includes `id`, `host_id`, `provider`, `path`, `label`, `created_at`

#### Scenario: list roots for host with no roots

WHEN `GET /v1/hosts/macbook-1/roots` is called
AND host `macbook-1` has no workspace roots
THEN the response is `200` with `roots: []`

#### Scenario: list roots for non-existent host returns 404

WHEN `GET /v1/hosts/nonexistent/roots` is called
THEN the response is `404` with `{ "error": "not_found" }`

---

### Requirement: Delete Workspace Root

`DELETE /v1/hosts/:hostId/roots/:rootId` SHALL remove the specified workspace root. Existing sessions using this root are NOT affected.

#### Scenario: delete root returns 204

WHEN `DELETE /v1/hosts/macbook-1/roots/uuid-1` is called
AND the root `uuid-1` exists under host `macbook-1`
THEN the root record is deleted from the database
AND the response is `204` with no body

#### Scenario: delete non-existent root returns 404

WHEN `DELETE /v1/hosts/macbook-1/roots/nonexistent` is called
THEN the response is `404` with `{ "error": "not_found" }`

#### Scenario: delete root does not affect existing sessions

WHEN a root at path `/Users/danker/Projects` is deleted
AND a session exists with `workspace_cwd: "/Users/danker/Projects/myapp"`
THEN the session record is unaffected
AND the session continues to operate normally

---

### Requirement: Root Path Uniqueness

The database MUST enforce a UNIQUE constraint on `(host_id, provider, path)` for workspace roots. Roots under the same host MAY share the same path if they belong to different providers.

#### Scenario: same path different provider is allowed

WHEN a root exists with `host_id: "macbook-1"`, `provider: "claude"`, `path: "/Users/danker/Projects"`
AND `POST /v1/hosts/macbook-1/roots` is called with `{ "provider": "book", "path": "/Users/danker/Projects" }`
THEN the root is created successfully (different provider)
AND the response is `201`

#### Scenario: book roots only under novel directory

WHEN `POST /v1/hosts/macbook-1/roots` is called with `{ "provider": "book", "path": "/Users/danker/Desktop/novel" }`
THEN the root is created successfully
AND the label defaults to the directory basename "novel" if not provided
