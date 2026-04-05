package com.imbot.android.data.repository

import android.content.SharedPreferences
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.local.AppDatabase
import com.imbot.android.data.local.SessionDao
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelaySession
import com.imbot.android.network.ServerMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionRepositoryRealtimeTest {
    @Test
    fun `applyRealtimeSummaryEvent updates usage summary fields`() =
        runTest {
            val sessionDao = InMemorySessionDao()
            val repository = createRepository(sessionDao)
            sessionDao.insertAll(listOf(testSession()))

            repository.applyRealtimeSummaryEvent(
                ServerMessage.Event(
                    sessionId = "sess-1",
                    seq = 2,
                    eventType = "session_usage",
                    payload =
                        JSONObject()
                            .put("input_tokens", 42_000)
                            .put("output_tokens", 9_000)
                            .put("context_window", 200_000)
                            .put("model", "glm-5"),
                    timestamp = "2026-04-04T10:02:00Z",
                ),
            )

            val stored = sessionDao.getById("sess-1")
            assertNotNull(stored)
            assertEquals("glm-5", stored?.model)
            assertEquals(42_000, stored?.inputTokens)
            assertEquals(9_000, stored?.outputTokens)
            assertEquals(200_000, stored?.contextWindow)
            assertEquals("2026-04-04T10:02:00Z", stored?.lastActiveAt)
        }

    @Test
    fun `applyRealtimeSummaryEvent marks session started as running and updates model`() =
        runTest {
            val sessionDao = InMemorySessionDao()
            val repository = createRepository(sessionDao)
            sessionDao.insertAll(listOf(testSession(status = "queued", model = null)))

            repository.applyRealtimeSummaryEvent(
                ServerMessage.Event(
                    sessionId = "sess-1",
                    seq = 1,
                    eventType = "session_started",
                    payload = JSONObject().put("model", "claude-opus-4-6"),
                    timestamp = "2026-04-04T10:01:00Z",
                ),
            )

            val stored = sessionDao.getById("sess-1")
            assertNotNull(stored)
            assertEquals("running", stored?.status)
            assertEquals("claude-opus-4-6", stored?.model)
            assertEquals("2026-04-04T10:01:00Z", stored?.lastActiveAt)
        }

    @Test
    fun `applyRealtimeSummaryEvent ignores stale summary events after fresher cache state`() =
        runTest {
            val sessionDao = InMemorySessionDao()
            val repository = createRepository(sessionDao)
            sessionDao.insertAll(
                listOf(
                    testSession(
                        model = "glm-5",
                        inputTokens = 55_000,
                        outputTokens = 8_000,
                        contextWindow = 200_000,
                        summarySeq = 11,
                        updatedAt = "2026-04-04T10:03:00Z",
                        lastActiveAt = "2026-04-04T10:03:00Z",
                    ),
                ),
            )

            repository.applyRealtimeSummaryEvent(
                ServerMessage.Event(
                    sessionId = "sess-1",
                    seq = 9,
                    eventType = "session_usage",
                    payload =
                        JSONObject()
                            .put("input_tokens", 42_000)
                            .put("output_tokens", 9_000)
                            .put("context_window", 120_000)
                            .put("model", "claude-opus-4-6"),
                    timestamp = "2026-04-04T10:02:00Z",
                ),
            )

            val stored = sessionDao.getById("sess-1")
            assertNotNull(stored)
            assertEquals("glm-5", stored?.model)
            assertEquals(55_000, stored?.inputTokens)
            assertEquals(8_000, stored?.outputTokens)
            assertEquals(200_000, stored?.contextWindow)
            assertEquals(11, stored?.summarySeq)
            assertEquals("2026-04-04T10:03:00Z", stored?.lastActiveAt)
        }

    @Test
    fun `upsertSessionSnapshot keeps fresher snapshot when stale write arrives late`() =
        runTest {
            val sessionDao = InMemorySessionDao()
            val repository = createRepository(sessionDao)
            sessionDao.insertAll(listOf(testSession(status = "running")))

            repository.upsertSessionSnapshot(
                relaySession(
                    status = "running",
                    model = "claude-opus-4-6",
                    updatedAt = "2026-04-04T10:03:00Z",
                    lastActiveAt = "2026-04-04T10:03:00Z",
                ),
            )
            repository.upsertSessionSnapshot(
                relaySession(
                    status = "failed",
                    model = "claude-sonnet-4-5",
                    errorMessage = "companion shutdown",
                    updatedAt = "2026-04-04T10:02:00Z",
                    lastActiveAt = "2026-04-04T10:02:00Z",
                ),
            )

            val stored = sessionDao.getById("sess-1")
            assertNotNull(stored)
            assertEquals("running", stored?.status)
            assertEquals("claude-opus-4-6", stored?.model)
            assertEquals(null, stored?.errorMessage)
            assertEquals("2026-04-04T10:03:00Z", stored?.lastActiveAt)
        }

    @Test
    fun `refresh merge preserves summary seq when a session moves into the fetched page`() =
        runTest {
            val sessionDao = InMemorySessionDao()
            val repository = createRepository(sessionDao)
            sessionDao.insertAll(
                listOf(
                    testSession(
                        id = "sess-2",
                        status = "idle",
                        model = "glm-5",
                        createdAt = "2026-04-04T10:10:00Z",
                        updatedAt = "2026-04-04T10:10:00Z",
                        lastActiveAt = "2026-04-04T10:10:00Z",
                    ),
                    testSession(
                        id = "sess-1",
                        status = "running",
                        model = "claude-opus-4-6",
                        inputTokens = 55_000,
                        outputTokens = 8_000,
                        contextWindow = 200_000,
                        summarySeq = 11,
                        updatedAt = "2026-04-04T10:03:00Z",
                        lastActiveAt = "2026-04-04T10:03:00Z",
                    ),
                ),
            )

            val merged =
                buildMergedSessionSnapshots(
                    sessionDao = sessionDao,
                    remoteSessions =
                        listOf(
                            relaySession(
                                id = "sess-1",
                                status = "running",
                                model = "glm-5",
                                updatedAt = "2026-04-04T10:05:00Z",
                                lastActiveAt = "2026-04-04T10:05:00Z",
                            ),
                        ),
                )
            sessionDao.insertAll(merged)
            repository.applyRealtimeSummaryEvent(
                ServerMessage.Event(
                    sessionId = "sess-1",
                    seq = 9,
                    eventType = "session_usage",
                    payload =
                        JSONObject()
                            .put("input_tokens", 42_000)
                            .put("output_tokens", 9_000)
                            .put("context_window", 120_000)
                            .put("model", "claude-sonnet-4-5"),
                    timestamp = "2026-04-04T10:06:00Z",
                ),
            )

            val stored = sessionDao.getById("sess-1")
            assertNotNull(stored)
            assertEquals(11, stored?.summarySeq)
            assertEquals("glm-5", stored?.model)
            assertEquals(55_000, stored?.inputTokens)
            assertEquals(8_000, stored?.outputTokens)
            assertEquals(200_000, stored?.contextWindow)
        }

    private fun createRepository(
        sessionDao: InMemorySessionDao,
        relayHttpClient: RelayHttpClient = RelayHttpClient(okhttp3.OkHttpClient()),
    ): SessionRepository =
        SessionRepository(
            database = FakeAppDatabase(sessionDao),
            sessionDao = sessionDao,
            relayHttpClient = relayHttpClient,
            settingsRepository = FakeSettingsRepository(),
        )

    private fun testSession(
        id: String = "sess-1",
        status: String = "running",
        model: String? = "sonnet",
        errorMessage: String? = null,
        inputTokens: Int = 0,
        outputTokens: Int = 0,
        contextWindow: Int = 0,
        summarySeq: Int = 0,
        createdAt: String = "2026-04-04T10:00:00Z",
        updatedAt: String = "2026-04-04T10:00:00Z",
        lastActiveAt: String = "2026-04-04T10:00:00Z",
    ) = SessionEntity(
        id = id,
        provider = "claude",
        hostId = "macbook-1",
        workspaceCwd = "/tmp/project",
        initialPrompt = "hello",
        model = model,
        status = status,
        errorMessage = errorMessage,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        contextWindow = contextWindow,
        summarySeq = summarySeq,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastActiveAt = lastActiveAt,
    )

    private fun relaySession(
        id: String = "sess-1",
        status: String,
        model: String?,
        errorMessage: String? = null,
        updatedAt: String,
        lastActiveAt: String,
    ) = RelaySession(
        id = id,
        provider = "claude",
        hostId = "macbook-1",
        workspaceCwd = "/tmp/project",
        initialPrompt = "hello",
        model = model,
        status = status,
        errorMessage = errorMessage,
        createdAt = "2026-04-04T10:00:00Z",
        updatedAt = updatedAt,
        lastActiveAt = lastActiveAt,
    )
}

private class FakeSettingsRepository : SettingsRepository(FakeSharedPreferences()) {
    init {
        save(
            RelaySettings(
                relayUrl = "https://relay.example.com",
                token = "token",
            ),
        )
    }
}

private class FakeAppDatabase(
    private val sessionDao: SessionDao,
) : AppDatabase() {
    override fun sessionDao(): SessionDao = sessionDao

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper =
        object : SupportSQLiteOpenHelper {
            override val databaseName: String = "fake-db"

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

private class FakeSharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    override fun getStringSet(
        key: String?,
        defValues: Set<String>?,
    ): Set<String>? = (values[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

    override fun edit(): SharedPreferences.Editor =
        object : SharedPreferences.Editor {
            override fun putString(
                key: String?,
                value: String?,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) {
                        values[key] = value
                    }
                }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) {
                        this@FakeSharedPreferences.values[key] = values?.toSet()
                    }
                }

            override fun putInt(
                key: String?,
                value: Int,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) {
                        values[key] = value
                    }
                }

            override fun putLong(
                key: String?,
                value: Long,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) {
                        values[key] = value
                    }
                }

            override fun putFloat(
                key: String?,
                value: Float,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) {
                        values[key] = value
                    }
                }

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) {
                        values[key] = value
                    }
                }

            override fun remove(key: String?): SharedPreferences.Editor =
                apply {
                    if (key != null) {
                        values.remove(key)
                    }
                }

            override fun clear(): SharedPreferences.Editor =
                apply {
                    values.clear()
                }

            override fun commit(): Boolean = true

            override fun apply() = Unit
        }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
