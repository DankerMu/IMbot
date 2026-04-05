@file:Suppress("MaxLineLength")

package com.imbot.android

import android.content.SharedPreferences
import com.imbot.android.data.ErrorStateManager
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.data.repository.SessionStore
import com.imbot.android.network.BrowseResult
import com.imbot.android.network.ConnectionState
import com.imbot.android.network.HealthzResponse
import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWorkspaceRoot
import com.imbot.android.network.RelayWsClient
import com.imbot.android.network.ServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.rules.TestWatcher
import org.junit.runner.Description

fun defaultRelaySettings() =
    RelaySettings(
        relayUrl = "https://relay.example.com",
        token = "test-token",
    )

data class RootRequest(
    val relayUrl: String,
    val token: String,
    val hostId: String,
)

data class BrowseRequest(
    val relayUrl: String,
    val token: String,
    val hostId: String,
    val path: String,
)

data class AddRootRequest(
    val relayUrl: String,
    val token: String,
    val hostId: String,
    val provider: String,
    val path: String,
    val label: String?,
)

data class RemoveRootRequest(
    val relayUrl: String,
    val token: String,
    val hostId: String,
    val rootId: String,
)

data class TestConnectionRequest(
    val relayUrl: String,
    val token: String,
)

class FakeRelayHttpClient : RelayHttpClient(OkHttpClient()) {
    var hostsResult: Result<List<RelayHost>> = Result.success(emptyList())
    var rootsResult: Result<List<RelayWorkspaceRoot>> = Result.success(emptyList())
    var browseResult: Result<BrowseResult> =
        Result.success(BrowseResult(path = "/", directories = emptyList()))
    var addRootResult: Result<RelayWorkspaceRoot> =
        Result.failure(IllegalStateException("addRootResult not configured"))
    var removeRootResult: Result<Unit> = Result.success(Unit)
    var testConnectionResult: Result<HealthzResponse> =
        Result.failure(IllegalStateException("testConnectionResult not configured"))

    var getHostsHandler: suspend (String, String) -> Result<List<RelayHost>> = { _, _ -> hostsResult }
    var getHostRootsHandler: suspend (String, String, String) -> Result<List<RelayWorkspaceRoot>> =
        { _, _, _ -> rootsResult }
    var browseHandler: suspend (String, String, String, String) -> Result<BrowseResult> =
        { _, _, _, _ -> browseResult }
    var addRootHandler: suspend (String, String, String, String, String, String?) -> Result<RelayWorkspaceRoot> =
        { _, _, _, _, _, _ -> addRootResult }
    var removeRootHandler: suspend (String, String, String, String) -> Result<Unit> =
        { _, _, _, _ -> removeRootResult }
    var testConnectionHandler: suspend (String, String) -> Result<HealthzResponse> =
        { _, _ -> testConnectionResult }

    var getHostsCalls = 0
    var getHostRootsCalls = 0
    var browseDirectoryCalls = 0
    var addRootCalls = 0
    var removeRootCalls = 0
    var testConnectionCalls = 0

    val rootRequests = mutableListOf<RootRequest>()
    val browseRequests = mutableListOf<BrowseRequest>()
    val addRootRequests = mutableListOf<AddRootRequest>()
    val removeRootRequests = mutableListOf<RemoveRootRequest>()
    val testConnectionRequests = mutableListOf<TestConnectionRequest>()

    override suspend fun getHosts(
        relayUrl: String,
        token: String,
    ): Result<List<RelayHost>> {
        getHostsCalls++
        return getHostsHandler(relayUrl, token)
    }

    override suspend fun getHostRoots(
        relayUrl: String,
        token: String,
        hostId: String,
    ): Result<List<RelayWorkspaceRoot>> {
        getHostRootsCalls++
        rootRequests += RootRequest(relayUrl = relayUrl, token = token, hostId = hostId)
        return getHostRootsHandler(relayUrl, token, hostId)
    }

    override suspend fun browseDirectory(
        relayUrl: String,
        token: String,
        hostId: String,
        path: String,
    ): Result<BrowseResult> {
        browseDirectoryCalls++
        browseRequests += BrowseRequest(relayUrl = relayUrl, token = token, hostId = hostId, path = path)
        return browseHandler(relayUrl, token, hostId, path)
    }

    override suspend fun addRoot(
        relayUrl: String,
        token: String,
        hostId: String,
        provider: String,
        path: String,
        label: String?,
    ): Result<RelayWorkspaceRoot> {
        addRootCalls++
        addRootRequests +=
            AddRootRequest(
                relayUrl = relayUrl,
                token = token,
                hostId = hostId,
                provider = provider,
                path = path,
                label = label,
            )
        return addRootHandler(relayUrl, token, hostId, provider, path, label)
    }

    override suspend fun removeRoot(
        relayUrl: String,
        token: String,
        hostId: String,
        rootId: String,
    ): Result<Unit> {
        removeRootCalls++
        removeRootRequests +=
            RemoveRootRequest(
                relayUrl = relayUrl,
                token = token,
                hostId = hostId,
                rootId = rootId,
            )
        return removeRootHandler(relayUrl, token, hostId, rootId)
    }

    override suspend fun testConnection(
        relayUrl: String,
        token: String,
    ): Result<HealthzResponse> {
        testConnectionCalls++
        testConnectionRequests += TestConnectionRequest(relayUrl = relayUrl, token = token)
        return testConnectionHandler(relayUrl, token)
    }
}

class FakeRelayWsClient : RelayWsClient(OkHttpClient(), ErrorStateManager()) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.NotConfigured)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<ServerMessage>()
    override val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val _events = MutableSharedFlow<ServerMessage.Event>()
    override val events: SharedFlow<ServerMessage.Event> = _events.asSharedFlow()

    val connectRequests = mutableListOf<Pair<String, String>>()
    val subscriptions = mutableListOf<String>()
    var lastTrackedSessionIds: Set<String> = emptySet()
    var pauseReconnectionCalls = 0
    var resumeReconnectionCalls = 0
    var forceReconnectCalls = 0

    override fun connect(
        relayUrl: String,
        token: String,
    ) {
        connectRequests += relayUrl to token
        _connectionState.value =
            if (relayUrl.isBlank() || token.isBlank()) {
                ConnectionState.NotConfigured
            } else {
                ConnectionState.Connected
            }
    }

    override fun subscribe(sessionId: String) {
        subscriptions += sessionId
    }

    override fun setTrackedSessionIds(sessionIds: Set<String>) {
        lastTrackedSessionIds = sessionIds
    }

    override fun pauseReconnection() {
        pauseReconnectionCalls++
        _connectionState.value = ConnectionState.Disconnected("Network unavailable")
    }

    override fun resumeReconnection() {
        resumeReconnectionCalls++
    }

    override fun forceReconnect() {
        forceReconnectCalls++
        _connectionState.value = ConnectionState.Connected
    }

    override fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    fun emitConnectionState(connectionState: ConnectionState) {
        _connectionState.value = connectionState
    }

    suspend fun emitMessage(message: ServerMessage) {
        _messages.emit(message)
    }

    suspend fun emitEvent(event: ServerMessage.Event) {
        _events.emit(event)
    }
}

open class FakeSettingsRepository(
    initialSettings: RelaySettings = defaultRelaySettings(),
    initialThemeMode: String = SettingsRepository.THEME_MODE_SYSTEM,
) : SettingsRepository(FakeSharedPreferences(), FakeSharedPreferences()) {
    private val relayUrlFlow = MutableStateFlow(initialSettings.relayUrl)
    private val themeModeFlow = MutableStateFlow(initialThemeMode)

    var saveCalls = 0
    var lastSavedSettings: RelaySettings? = null
    val savedThemeModes = mutableListOf<String>()

    init {
        super.save(initialSettings)
        super.saveThemeMode(initialThemeMode)
        lastSavedSettings = initialSettings
        savedThemeModes += initialThemeMode
    }

    override fun save(settings: RelaySettings) {
        saveCalls++
        lastSavedSettings = settings
        relayUrlFlow.value = settings.relayUrl
        super.save(settings)
    }

    override fun observeRelayUrl(): Flow<String> = relayUrlFlow

    override fun loadThemeMode(): String = themeModeFlow.value

    override fun saveThemeMode(mode: String) {
        themeModeFlow.value = mode
        savedThemeModes += mode
        super.saveThemeMode(mode)
    }

    override fun observeThemeMode(): Flow<String> = themeModeFlow

    fun emitRelayUrl(relayUrl: String) {
        relayUrlFlow.value = relayUrl
    }

    fun emitThemeMode(themeMode: String) {
        themeModeFlow.value = themeMode
    }
}

class FakeSessionStore : SessionStore {
    var clearLocalCacheCalls = 0
    var clearLocalCacheError: Throwable? = null
    val requestedPrefixes = mutableListOf<String>()
    private val flows = mutableMapOf<String, MutableStateFlow<List<SessionEntity>>>()

    override suspend fun clearLocalCache() {
        clearLocalCacheCalls++
        clearLocalCacheError?.let { throw it }
    }

    override fun getSessionsByPathPrefix(pathPrefix: String): Flow<List<SessionEntity>> {
        requestedPrefixes += pathPrefix
        return flows.getOrPut(pathPrefix) { MutableStateFlow(emptyList()) }
    }

    fun emitSessions(
        pathPrefix: String,
        sessions: List<SessionEntity>,
    ) {
        flows.getOrPut(pathPrefix) { MutableStateFlow(emptyList()) }.value = sessions
    }
}

class FakeSharedPreferences : SharedPreferences {
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

    override fun edit(): SharedPreferences.Editor = FakeEditor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private class FakeEditor(
    private val values: MutableMap<String, Any?>,
) : SharedPreferences.Editor {
    private val staged = linkedMapOf<String, Any?>()
    private val removedKeys = linkedSetOf<String>()
    private var clearAll = false

    override fun putString(
        key: String?,
        value: String?,
    ): SharedPreferences.Editor = applyChange(key, value)

    override fun putStringSet(
        key: String?,
        values: Set<String>?,
    ): SharedPreferences.Editor = applyChange(key, values?.toSet())

    override fun putInt(
        key: String?,
        value: Int,
    ): SharedPreferences.Editor = applyChange(key, value)

    override fun putLong(
        key: String?,
        value: Long,
    ): SharedPreferences.Editor = applyChange(key, value)

    override fun putFloat(
        key: String?,
        value: Float,
    ): SharedPreferences.Editor = applyChange(key, value)

    override fun putBoolean(
        key: String?,
        value: Boolean,
    ): SharedPreferences.Editor = applyChange(key, value)

    override fun remove(key: String?): SharedPreferences.Editor {
        if (key != null) {
            removedKeys += key
            staged.remove(key)
        }
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        clearAll = true
        staged.clear()
        removedKeys.clear()
        return this
    }

    override fun commit(): Boolean {
        applyChanges()
        return true
    }

    override fun apply() {
        applyChanges()
    }

    private fun applyChange(
        key: String?,
        value: Any?,
    ): SharedPreferences.Editor {
        if (key != null) {
            staged[key] = value
            removedKeys.remove(key)
        }
        return this
    }

    private fun applyChanges() {
        if (clearAll) {
            values.clear()
            clearAll = false
        }
        removedKeys.forEach(values::remove)
        removedKeys.clear()
        staged.forEach { (key, value) ->
            values[key] = value
        }
        staged.clear()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    val dispatcher: TestDispatcher = StandardTestDispatcher(scheduler),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
