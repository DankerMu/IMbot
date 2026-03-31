package com.imbot.android.data.repository

import androidx.room.withTransaction
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.local.AppDatabase
import com.imbot.android.data.local.SessionDao
import com.imbot.android.data.local.SessionEntity
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

        @Suppress("MaxLineLength")
        override fun getSessionsByPathPrefix(pathPrefix: String): Flow<List<SessionEntity>> = sessionDao.getByPathPrefix(pathPrefix)

        suspend fun refreshFromApi() {
            val settings = settingsRepository.load()
            if (!settings.isConfigured()) {
                return
            }
            val relayValidationError = settings.relayValidationError()
            require(relayValidationError == null) { relayValidationError.orEmpty() }

            val sessions =
                relayHttpClient.getSessions(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                ).getOrThrow()
                    .map(RelaySession::toEntity)

            database.withTransaction {
                sessionDao.insertAll(sessions)
                if (sessions.isEmpty()) {
                    sessionDao.deleteAll()
                } else {
                    sessionDao.deleteNotIn(sessions.map(SessionEntity::id))
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
