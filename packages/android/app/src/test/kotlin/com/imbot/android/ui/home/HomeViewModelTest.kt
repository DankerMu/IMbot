package com.imbot.android.ui.home

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.imbot.android.FakeRelayWsClient
import com.imbot.android.FakeSettingsRepository
import com.imbot.android.MainDispatcherRule
import com.imbot.android.data.local.AppDatabase
import com.imbot.android.data.local.SessionDao
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.data.repository.SessionRepository
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelaySessionPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `observeSessions tracks terminal sessions for late transcript summaries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sessionDao = InMemorySessionDao()
            val repository =
                SessionRepository(
                    database = FakeAppDatabase(sessionDao),
                    sessionDao = sessionDao,
                    relayHttpClient = FakeHomeRelayHttpClient(),
                    settingsRepository = FakeSettingsRepository(),
                )
            val ws = FakeRelayWsClient()

            HomeViewModel(
                relayWsClient = ws,
                sessionRepository = repository,
                settingsRepository = FakeSettingsRepository(),
            )

            sessionDao.insertAll(
                listOf(
                    testSession(id = "running-1", status = "running"),
                    testSession(id = "completed-1", status = "completed"),
                ),
            )
            advanceUntilIdle()

            assertEquals(linkedSetOf("running-1", "completed-1"), ws.lastTrackedSessionIds)
        }
}

private class FakeHomeRelayHttpClient : RelayHttpClient(OkHttpClient()) {
    override suspend fun getSessionsPage(
        relayUrl: String,
        token: String,
        limit: Int,
        offset: Int,
    ): Result<RelaySessionPage> = Result.success(RelaySessionPage(sessions = emptyList(), total = 0, limit = limit, offset = offset))
}

private class FakeAppDatabase(
    private val sessionDao: SessionDao,
) : AppDatabase() {
    override fun sessionDao(): SessionDao = sessionDao

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper =
        object : SupportSQLiteOpenHelper {
            override val databaseName: String = "fake-home-db"

            override fun setWriteAheadLoggingEnabled(enabled: Boolean) = Unit

            override val writableDatabase: SupportSQLiteDatabase
                get() = error("Not used in unit tests")

            override val readableDatabase: SupportSQLiteDatabase
                get() = error("Not used in unit tests")

            override fun close() = Unit
        }

    override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this)

    override fun clearAllTables() = Unit
}

private class InMemorySessionDao : SessionDao {
    private val sessions = linkedMapOf<String, SessionEntity>()
    private val flow = MutableStateFlow(emptyList<SessionEntity>())

    override suspend fun insertAll(sessions: List<SessionEntity>) {
        sessions.forEach { session ->
            this.sessions[session.id] = session
        }
        publish()
    }

    override fun getAll() = flow

    override suspend fun getPage(
        offset: Int,
        limit: Int,
    ): List<SessionEntity> = flow.value.drop(offset).take(limit)

    override fun getByPathPrefix(
        prefix: String,
        escapedPrefix: String,
    ) = MutableStateFlow(flow.value.filter { it.workspaceCwd == prefix || it.workspaceCwd.startsWith("$prefix/") })

    override suspend fun getById(id: String): SessionEntity? = sessions[id]

    override suspend fun deleteById(id: String) {
        sessions.remove(id)
        publish()
    }

    override suspend fun deleteNotIn(ids: List<String>) {
        sessions.keys.retainAll(ids.toSet())
        publish()
    }

    override suspend fun deleteByIds(ids: List<String>) {
        ids.forEach(sessions::remove)
        publish()
    }

    override suspend fun deleteAll() {
        sessions.clear()
        publish()
    }

    private fun publish() {
        flow.value = sessions.values.toList()
    }
}

private fun testSession(
    id: String,
    status: String,
) = SessionEntity(
    id = id,
    provider = "book",
    hostId = "macbook-1",
    workspaceCwd = "/tmp/project",
    initialPrompt = "hello",
    model = "glm-5",
    status = status,
    errorMessage = null,
    inputTokens = 42_000,
    outputTokens = 9_000,
    contextWindow = 200_000,
    createdAt = "2026-04-04T10:00:00Z",
    updatedAt = "2026-04-04T10:03:00Z",
    lastActiveAt = "2026-04-04T10:03:00Z",
)
