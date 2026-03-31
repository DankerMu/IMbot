package com.imbot.android.data.repository

import androidx.room.withTransaction
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.local.AppDatabase
import com.imbot.android.data.local.SessionDao
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.data.local.escapeSqlLikePattern
import com.imbot.android.data.relayValidationError
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelaySession
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository
    @Inject
    constructor(
        private val database: AppDatabase,
        private val sessionDao: SessionDao,
        private val relayHttpClient: RelayHttpClient,
        private val settingsRepository: SettingsRepository,
    ) : SessionStore {
        fun getSessions(): Flow<List<SessionEntity>> = sessionDao.getAll()

        override fun getSessionsByPathPrefix(pathPrefix: String): Flow<List<SessionEntity>> =
            sessionDao.getByPathPrefix(
                prefix = pathPrefix,
                escapedPrefix = pathPrefix.escapeSqlLikePattern(),
            )

        suspend fun refreshFromApi(
            limit: Int = DEFAULT_SESSION_PAGE_LIMIT,
            offset: Int = 0,
        ) {
            val settings = settingsRepository.load()
            if (!settings.isConfigured()) {
                return
            }
            val relayValidationError = settings.relayValidationError()
            require(relayValidationError == null) { relayValidationError.orEmpty() }

            val page =
                relayHttpClient.getSessionsPage(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    limit = limit,
                    offset = offset,
                ).getOrThrow()
            val sessions = page.sessions.map(RelaySession::toEntity)

            database.withTransaction {
                sessionDao.insertAll(sessions)
                val staleIds =
                    computeStaleSessionIds(
                        localPage =
                            sessionDao.getPage(
                                offset = page.offset,
                                limit = page.limit,
                            ),
                        remoteSessionIds = sessions.map(SessionEntity::id).toSet(),
                    )
                if (staleIds.isNotEmpty()) {
                    sessionDao.deleteByIds(staleIds)
                }
            }
        }

        suspend fun updateSessionStatus(
            sessionId: String,
            status: String,
        ) {
            val existing = sessionDao.getById(sessionId) ?: return
            if (existing.status == status) {
                return
            }
            val now = Instant.now().toString()

            sessionDao.insertAll(
                listOf(
                    existing.copy(
                        status = status,
                        updatedAt = now,
                        lastActiveAt = now,
                    ),
                ),
            )
        }

        suspend fun deleteSession(sessionId: String) {
            val settings = settingsRepository.load()
            val relayValidationError = settings.relayValidationError()
            require(relayValidationError == null) { relayValidationError.orEmpty() }

            relayHttpClient.deleteSession(
                relayUrl = settings.relayUrl,
                token = settings.token,
                sessionId = sessionId,
            ).getOrThrow()
            sessionDao.deleteById(sessionId)
        }

        override suspend fun clearLocalCache() {
            database.withTransaction {
                sessionDao.deleteAll()
            }
        }
    }

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

private fun RelaySession.toEntity() =
    SessionEntity(
        id = id,
        provider = provider,
        hostId = hostId,
        workspaceCwd = workspaceCwd,
        initialPrompt = initialPrompt,
        model = model,
        status = status,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastActiveAt = lastActiveAt,
    )

private const val DEFAULT_SESSION_PAGE_LIMIT = 200
private const val STATUS_QUEUED = "queued"
private const val STATUS_RUNNING = "running"
