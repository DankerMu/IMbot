package com.imbot.android.data.repository

import com.imbot.android.data.local.SessionDao
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.network.RelaySession
import com.imbot.android.network.RelaySessionPage

internal data class PreparedSessionPageRefresh(
    val sessions: List<SessionEntity>,
    val staleIds: List<String>,
)

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

internal suspend fun prepareSessionPageRefresh(
    sessionDao: SessionDao,
    localPage: List<SessionEntity>,
    remotePage: RelaySessionPage,
): PreparedSessionPageRefresh {
    val sessions = buildMergedSessionSnapshots(sessionDao, remotePage.sessions)
    val staleIds =
        if (shouldDeleteMissingSessionsFromPage(remotePage)) {
            computeStaleSessionIds(
                localPage = localPage,
                remoteSessionIds = sessions.map(SessionEntity::id).toSet(),
            )
        } else {
            emptyList()
        }
    return PreparedSessionPageRefresh(
        sessions = sessions,
        staleIds = staleIds,
    )
}

internal fun shouldDeleteMissingSessionsFromPage(remotePage: RelaySessionPage): Boolean =
    remotePage.offset + remotePage.sessions.size >= remotePage.total

private const val STATUS_QUEUED = "queued"
private const val STATUS_RUNNING = "running"
