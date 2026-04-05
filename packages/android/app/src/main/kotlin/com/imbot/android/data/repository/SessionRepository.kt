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
import com.imbot.android.network.ServerMessage
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
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

        suspend fun upsertSessionSnapshot(session: RelaySession) {
            sessionDao.insertAll(listOf(session.toEntity()))
        }

        suspend fun applyRealtimeSummaryEvent(event: ServerMessage.Event) {
            val existing = sessionDao.getById(event.sessionId) ?: return
            val timestamp = event.timestamp.takeIf(String::isNotBlank) ?: Instant.now().toString()
            val updated =
                when (event.eventType) {
                    "user_message",
                    "assistant_message",
                    ->
                        existing.copy(
                            updatedAt = timestamp,
                            lastActiveAt = timestamp,
                        )

                    "session_started" ->
                        existing.copy(
                            model = event.payload.stringValue("model") ?: existing.model,
                            status = "running",
                            updatedAt = timestamp,
                            lastActiveAt = timestamp,
                        )

                    "session_usage" ->
                        existing.copy(
                            model = event.payload.stringValue("model") ?: existing.model,
                            inputTokens = event.payload.intValue("input_tokens") ?: existing.inputTokens,
                            outputTokens = event.payload.intValue("output_tokens") ?: existing.outputTokens,
                            contextWindow = event.payload.intValue("context_window") ?: existing.contextWindow,
                            updatedAt = timestamp,
                            lastActiveAt = timestamp,
                        )

                    "session_idle" ->
                        existing.copy(
                            status = "idle",
                            updatedAt = timestamp,
                            lastActiveAt = timestamp,
                        )

                    else -> null
                } ?: return

            sessionDao.insertAll(listOf(updated))
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

        suspend fun answerInteractiveTool(
            sessionId: String,
            callId: String,
            answer: String,
            questionIndex: Int = 0,
        ): Result<Unit> {
            val settings = settingsRepository.load()
            val relayValidationError = settings.relayValidationError()
            require(relayValidationError == null) { relayValidationError.orEmpty() }

            return relayHttpClient.answerInteractiveTool(
                relayUrl = settings.relayUrl,
                token = settings.token,
                sessionId = sessionId,
                callId = callId,
                answer = answer,
                questionIndex = questionIndex,
            )
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
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        contextWindow = contextWindow,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastActiveAt = lastActiveAt,
    )

private fun JSONObject?.stringValue(key: String): String? {
    val payload = this ?: return null
    val value = payload.opt(key)
    return when (value) {
        null,
        JSONObject.NULL,
        -> null

        is String -> value.ifBlank { null }
        else -> value.toString().ifBlank { null }
    }
}

private fun JSONObject?.intValue(key: String): Int? {
    val payload = this ?: return null
    val value = payload.opt(key)
    return when (value) {
        null,
        JSONObject.NULL,
        -> null

        is Int -> value
        is Long -> value.toInt()
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

private const val DEFAULT_SESSION_PAGE_LIMIT = 200
private const val STATUS_QUEUED = "queued"
private const val STATUS_RUNNING = "running"
