# Capability: host-management-api

## ADDED Requirements

### Requirement: List All Hosts

`GET /v1/hosts` SHALL return all registered hosts with their current status, supported providers, and last heartbeat timestamp. The relay-local host MUST always be present in the list.

#### Scenario: list hosts includes macbook and relay-local

WHEN `GET /v1/hosts` is called
THEN the response is `200` with a `hosts` array
AND the array contains an entry with `id: "relay-local"`, `type: "relay_local"`, `providers: ["openclaw"]`
AND if a macbook companion has registered, the array also contains an entry with `type: "macbook"` and providers `["claude", "book"]`

#### Scenario: host shows correct online/offline status

WHEN `GET /v1/hosts` is called
AND the macbook companion is connected with a fresh heartbeat
THEN the macbook host entry has `status: "online"`

WHEN `GET /v1/hosts` is called
AND the macbook companion has not sent a heartbeat in over 90 seconds
THEN the macbook host entry has `status: "offline"`

WHEN `GET /v1/hosts` is called
THEN the relay-local host always has `status: "online"` (it is the local machine)

#### Scenario: host shows correct provider list

WHEN `GET /v1/hosts` is called
AND the macbook companion's last heartbeat declared providers `["claude", "book"]`
THEN the macbook host entry has `providers: ["claude", "book"]`

WHEN `GET /v1/hosts` is called
AND the OpenClaw gateway is connected
THEN the relay-local host entry has `providers: ["openclaw"]`

#### Scenario: host shows last heartbeat timestamp

WHEN `GET /v1/hosts` is called
THEN each host entry includes `last_heartbeat_at`
AND for macbook hosts, this is the ISO 8601 timestamp of the last heartbeat
AND for relay-local, `last_heartbeat_at` is `null` (no heartbeat mechanism for local host)

#### Scenario: no registered hosts returns only relay-local

WHEN `GET /v1/hosts` is called
AND no companion has ever connected
THEN the response contains exactly one host: `relay-local`
