# Capability: companion-heartbeat

Send periodic heartbeat messages to the relay so that the relay can track companion online status and available providers.

## ADDED Requirements

### Requirement: Periodic Heartbeat Emission

The companion SHALL send a heartbeat message every 30 seconds while connected to the relay. The heartbeat message MUST conform to the CompanionMessage schema: `{ type: "heartbeat", host_id: string, providers: string[], uptime: number }`.

#### Scenario: Heartbeat sent on schedule

WHEN the companion is connected to the relay
THEN a heartbeat message is sent every 30 seconds (+/- 1s tolerance)
AND each heartbeat contains the configured `host_id`
AND each heartbeat contains `uptime` as seconds since companion process start

#### Scenario: Heartbeat includes correct provider list

WHEN the companion configuration lists `claude` and `book` as available providers
THEN every heartbeat message contains `providers: ["claude", "book"]`

WHEN the companion configuration lists only `claude` (book binary not configured)
THEN every heartbeat message contains `providers: ["claude"]`

#### Scenario: Heartbeat stops when disconnected

WHEN the WSS connection drops
THEN the heartbeat timer is paused (no heartbeat messages are generated)
AND no errors are thrown from attempting to send on a closed socket

#### Scenario: Heartbeat resumes on reconnect

WHEN the WSS connection is re-established after a disconnect
THEN the heartbeat timer restarts
AND the first heartbeat is sent immediately upon reconnection (serves as re-registration)
AND subsequent heartbeats follow the 30-second interval

### Requirement: Uptime Tracking

The companion SHALL track process uptime starting from process boot. The `uptime` field in each heartbeat MUST be an integer representing whole seconds since companion process start.

#### Scenario: Uptime increases monotonically

WHEN two consecutive heartbeats are sent 30 seconds apart
THEN the second heartbeat's `uptime` value is approximately 30 greater than the first

#### Scenario: Uptime resets on process restart

WHEN the companion process is restarted
THEN the first heartbeat after restart has `uptime` close to 0
