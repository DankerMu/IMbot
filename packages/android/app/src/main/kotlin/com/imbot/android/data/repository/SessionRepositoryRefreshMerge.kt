package com.imbot.android.data.repository

import com.imbot.android.data.local.SessionDao
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.network.RelaySession

internal fun computeStaleSessionIds(
    localPage: List<SessionEntity>,
    remoteSessionIds: Set<String>,
): List<String> =
    localPage
        .asSequence()
        .filterNot { session -> session.id in remoteSessionIds }
        .filterNot { session -> session.status == STATUS_RUNNING || session.status == STATUS_QUEUED }
        .map(SessionEntity::id)
        .toList()

internal suspend fun buildMergedSessionSnapshots(
    sessionDao: SessionDao,
    remoteSessions: List<RelaySession>,
): List<SessionEntity> {
    val existingSessionsById =
        mutableMapOf<String, SessionEntity>().apply {
            remoteSessions
                .asSequence()
                .map(RelaySession::id)
                .distinct()
                .forEach { sessionId ->
                    sessionDao.getById(sessionId)?.let { session ->
                        put(sessionId, session)
                    }
                }
        }

    return remoteSessions.map { session ->
        val existing = existingSessionsById[session.id]
        mergeSessionSnapshot(
            existing = existing,
            incoming =
                session.toEntity(
                    summarySeq = existing?.summarySeq ?: 0,
                ),
        )
    }
}

private const val STATUS_QUEUED = "queued"
private const val STATUS_RUNNING = "running"
