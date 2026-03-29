# Capability: data-purge-job

Server-side daily cleanup of stale sessions and Android-side sync of purged data.

## ADDED Requirements

### Requirement: Daily Cron Purge on Relay

The relay server SHALL run a cron job at 03:00 UTC daily (via `node-cron`) that deletes sessions where `status IN ('completed', 'failed', 'cancelled') AND last_active_at < datetime('now', '-30 days')`. Sessions in `queued` or `running` status SHALL NEVER be purged regardless of age. The `session_events` table uses `ON DELETE CASCADE`, so events are automatically deleted with their parent session.

#### Scenario: 30-day-old completed session -- purged

WHEN a session has status `completed` and `last_active_at` is 31 days ago
THEN the purge job deletes the session
AND all associated session_events are deleted via CASCADE
AND the deletion is logged

#### Scenario: 29-day-old session -- not purged

WHEN a session has status `completed` and `last_active_at` is 29 days ago
THEN the purge job does NOT delete the session

#### Scenario: Running session even if old -- never purged

WHEN a session has status `running` and `last_active_at` is 60 days ago
THEN the purge job does NOT delete the session
AND the same applies to `queued` sessions

#### Scenario: Purge with many sessions -- completes efficiently

WHEN 100 sessions qualify for purge
THEN the purge completes in < 5 seconds
AND the purge processes sessions in batches (DELETE with LIMIT) to avoid long-running transactions

#### Scenario: CASCADE deletes events correctly (no orphans)

WHEN a session is purged
THEN all its session_events are deleted via CASCADE
AND no orphaned events remain in the session_events table

### Requirement: Purge Logging

The purge job SHALL log the count of deleted sessions after each run. The log SHALL include the timestamp and the number of sessions purged. If zero sessions qualify, the job SHALL still log "purge completed: 0 sessions deleted".

#### Scenario: Purge log shows count

WHEN the purge job runs and deletes 5 sessions
THEN the relay logs: "[purge] 2026-03-28T03:00:00Z - purged 5 sessions"
WHEN the purge job runs and no sessions qualify
THEN the relay logs: "[purge] 2026-03-28T03:00:00Z - purged 0 sessions"

### Requirement: Android Sync After Purge

When the Android app refreshes the session list (pull-to-refresh or app foregrounding), it SHALL compare the relay's session list with the local Room cache. Sessions present in Room but absent from the relay response SHALL be deleted from Room. This handles server-side purge without requiring a dedicated "purge notification" mechanism.

#### Scenario: Android refresh after purge -- stale sessions removed

WHEN the relay purged sessions A and B overnight
AND the Android app refreshes the session list
THEN sessions A and B are deleted from the local Room database
AND the session list no longer shows A and B
AND the deletion is silent (no user notification needed)

#### Scenario: Android offline -- stale data preserved until refresh

WHEN the Android app is offline
THEN stale sessions remain in Room (visible in offline mode)
AND they are cleaned up on the next successful refresh
