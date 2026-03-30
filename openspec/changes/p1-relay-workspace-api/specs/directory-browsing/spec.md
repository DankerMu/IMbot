# Capability: directory-browsing

## ADDED Requirements

### Requirement: Browse Directory Returns Subdirectories Only

`GET /v1/hosts/:hostId/browse?path=...` SHALL return a list of subdirectories at the given path. Files MUST NOT be included in the result. The path MUST be under an existing workspace root for the host.

#### Scenario: browse valid path returns list of directories

WHEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/Desktop/AI-vault` is called
AND `/Users/danker/Desktop/AI-vault` is under a workspace root for `macbook-1`
AND the directory contains subdirectories `IMbot`, `projects` and files `README.md`, `.gitignore`
THEN the response is `200` with:
```json
{
  "path": "/Users/danker/Desktop/AI-vault",
  "directories": [
    { "name": "IMbot", "path": "/Users/danker/Desktop/AI-vault/IMbot" },
    { "name": "projects", "path": "/Users/danker/Desktop/AI-vault/projects" }
  ]
}
```
AND no files are included in the `directories` array

#### Scenario: browse path not under any root returns 403

WHEN `GET /v1/hosts/macbook-1/browse?path=/etc/secrets` is called
AND `/etc/secrets` is not under any workspace root for `macbook-1`
THEN the response is `403` with `{ "error": "forbidden", "message": "Path is not under any workspace root" }`

#### Scenario: browse non-existent path returns 404

WHEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/nonexistent` is called
AND `/Users/danker/nonexistent` is under a workspace root
AND the directory does not exist on the host
THEN the response is `404` with `{ "error": "not_found" }`

#### Scenario: browse path with only files returns empty list

WHEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/Desktop/AI-vault/flat-dir` is called
AND the directory exists but contains only files (no subdirectories)
THEN the response is `200` with `{ "path": "...", "directories": [] }`

#### Scenario: empty directory returns empty list

WHEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/Desktop/AI-vault/empty-dir` is called
AND the directory exists but is completely empty
THEN the response is `200` with `{ "path": "...", "directories": [] }`

#### Scenario: browse without path parameter returns 400

WHEN `GET /v1/hosts/macbook-1/browse` is called with no `path` query parameter
THEN the response is `400` with `{ "error": "invalid_request", "message": "path is required" }`

---

### Requirement: Proxy Browse to Companion for Macbook Hosts

For hosts with `type: "macbook"`, browse requests SHALL be proxied to the companion via the `browse_directory` command only after the relay confirms the requested path is already under a configured workspace root. The companion reads the local filesystem and returns the directory listing.

#### Scenario: browse macbook path proxied to companion

WHEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/Projects` is called
AND host `macbook-1` has `type: "macbook"`
AND the companion is online
THEN the relay sends a `browse_directory` command to the companion with `path: "/Users/danker/Projects"`
AND the companion returns the list of subdirectories
AND the relay forwards the result as the API response

#### Scenario: macbook path outside all roots is rejected before companion access

WHEN `GET /v1/hosts/macbook-1/browse?path=/etc/secrets` is called
AND `/etc/secrets` is not under any workspace root for `macbook-1`
THEN the response is `403` with `{ "error": "forbidden" }`
AND the relay does NOT send a `browse_directory` command to the companion

#### Scenario: browse macbook path when companion offline returns 502

WHEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/Projects` is called
AND the companion for `macbook-1` is offline
THEN the response is `502` with `{ "error": "host_offline" }`

---

### Requirement: Local Filesystem Browse for Relay-Local Host

For the `relay-local` host, browse requests SHALL read the local filesystem directly on the relay VPS. The relay MUST use `fs.readdir` with `withFileTypes: true` to distinguish directories from files.

#### Scenario: browse relay-local path reads local filesystem

WHEN `GET /v1/hosts/relay-local/browse?path=/home/user/projects` is called
AND `/home/user/projects` is under a workspace root for `relay-local`
THEN the relay reads the directory using the local filesystem
AND returns only subdirectory entries (filtering out files and symlinks to files)

---

### Requirement: Path Traversal Prevention

The browse endpoint SHALL reject any path that contains path traversal sequences (`..`, `/../`). The relay SHALL validate the requested path against workspace roots before execution, allowing only controlled macOS alias equivalence for `/var`, `/tmp`, and `/etc` versus `/private/...`. After filesystem access or companion proxy returns, the relay SHALL revalidate the canonical result against workspace roots.

#### Scenario: path traversal attempt with ../ is rejected

WHEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/Desktop/AI-vault/../../etc` is called
THEN the response is `403` with `{ "error": "forbidden" }`
AND the request is NOT forwarded to the companion

#### Scenario: path with embedded .. component is rejected

WHEN `GET /v1/hosts/relay-local/browse?path=/home/user/projects/../../../etc/passwd` is called
THEN the response is `403` with `{ "error": "forbidden" }`
AND the local filesystem is NOT read

#### Scenario: normalized path under root is allowed

WHEN `GET /v1/hosts/macbook-1/browse?path=/Users/danker/Desktop/AI-vault/IMbot` is called
AND the path is absolute with no `..` components
AND it is under a workspace root
THEN the request proceeds normally

#### Scenario: legacy root alias is upgraded after exact root browse

WHEN a stored workspace root for `macbook-1` is `/var/tmp/IMbotLegacy`
AND `GET /v1/hosts/macbook-1/browse?path=/var/tmp/IMbotLegacy` is called
AND the companion returns canonical path `/private/var/tmp/IMbotLegacy`
THEN the response is `200`
AND the relay accepts the canonical result as still under the matched root
AND the relay upgrades the stored root path to `/private/var/tmp/IMbotLegacy`

#### Scenario: symlink escape is rejected after canonicalization

WHEN `GET /v1/hosts/relay-local/browse?path=/home/user/projects/escape-link` is called
AND `/home/user/projects/escape-link` is a symlink to `/etc`
THEN the response is `403` with `{ "error": "forbidden" }`
AND the canonical target path is not treated as under the workspace root
