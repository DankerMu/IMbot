@file:Suppress("MaxLineLength")

package com.imbot.android.ui.newsession

import android.content.SharedPreferences
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.BrowseEntry
import com.imbot.android.network.BrowseResult
import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWorkspaceRoot
import com.imbot.android.network.SessionResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class NewSessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init loads hosts automatically`() =
        runTest(mainDispatcherRule.dispatcher) {
            val hosts = onlineHosts()
            val relay = FakeRelayHttpClient().apply { hostsResult = Result.success(hosts) }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())

            advanceUntilIdle()

            assertEquals(1, relay.getHostsCalls)
            assertEquals(hosts, viewModel.uiState.value.hosts)
            assertFalse(viewModel.uiState.value.isLoadingHosts)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `selectProvider updates provider and hostId resets directory state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    rootsResult =
                        Result.success(
                            listOf(
                                root(
                                    id = "claude-root",
                                    provider = "claude",
                                    path = "/Users/danker/projects",
                                ),
                            ),
                        )
                    browseResult =
                        Result.success(
                            BrowseResult(
                                path = "/Users/danker/projects",
                                directories =
                                    listOf(
                                        entry(
                                            name = "IMbot",
                                            path = "/Users/danker/projects/IMbot",
                                        ),
                                    ),
                            ),
                        )
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()

            viewModel.selectProvider("claude", "macbook-1")
            viewModel.loadRoots()
            advanceUntilIdle()
            viewModel.browseDirectory("/Users/danker/projects")
            advanceUntilIdle()
            viewModel.selectDirectory("/Users/danker/projects/IMbot")
            viewModel.updateModel("opus")

            viewModel.selectProvider("openclaw", "relay-local")

            val state = viewModel.uiState.value
            assertEquals("openclaw", state.provider)
            assertEquals("relay-local", state.hostId)
            assertTrue(state.roots.isEmpty())
            assertTrue(state.browseEntries.isEmpty())
            assertNull(state.browsePath)
            assertNull(state.pendingBrowsePath)
            assertTrue(state.breadcrumbs.isEmpty())
            assertNull(state.cwd)
            assertNull(state.directoryError)
            assertFalse(state.isLoadingRoots)
            assertFalse(state.isLoadingBrowse)
            assertEquals("sonnet", state.model)
        }

    @Test
    fun `loadRoots fetches and filters roots for book provider`() =
        runTest(mainDispatcherRule.dispatcher) {
            val bookRoot = root(id = "book-root", provider = "book", path = "/Users/danker/novel")
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    rootsResult =
                        Result.success(
                            listOf(
                                root(id = "claude-root", provider = "claude", path = "/Users/danker/projects"),
                                bookRoot,
                                root(
                                    id = "openclaw-root",
                                    hostId = "relay-local",
                                    provider = "openclaw",
                                    path = "/srv/openclaw",
                                ),
                            ),
                        )
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()

            viewModel.selectProvider("book", "macbook-1")
            viewModel.loadRoots()
            advanceUntilIdle()

            assertEquals(1, relay.getHostRootsCalls)
            assertEquals(listOf(bookRoot), viewModel.uiState.value.roots)
            assertEquals(listOf("macbook-1"), relay.rootRequests.map(RootRequest::hostId))
        }

    @Test
    fun `browseDirectory fetches entries and builds breadcrumbs`() =
        runTest(mainDispatcherRule.dispatcher) {
            val entries =
                listOf(
                    entry(name = "src", path = "/Users/danker/projects/src"),
                    entry(name = "docs", path = "/Users/danker/projects/docs"),
                )
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    browseResult = Result.success(BrowseResult(path = "/Users/danker/projects", directories = entries))
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()

            viewModel.selectProvider("claude", "macbook-1")
            viewModel.browseDirectory("/Users/danker/projects")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("/Users/danker/projects", state.browsePath)
            assertEquals(entries, state.browseEntries)
            assertEquals(
                listOf(
                    DirectoryBreadcrumb(label = "/", path = "/"),
                    DirectoryBreadcrumb(label = "Users", path = "/Users"),
                    DirectoryBreadcrumb(label = "danker", path = "/Users/danker"),
                    DirectoryBreadcrumb(label = "projects", path = "/Users/danker/projects"),
                ),
                state.breadcrumbs,
            )
            assertNull(state.pendingBrowsePath)
            assertNull(state.directoryError)
            assertEquals(listOf("/Users/danker/projects"), relay.browseRequests.map(BrowseRequest::path))
        }

    @Test
    fun `selectDirectory sets cwd`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = NewSessionViewModel(FakeRelayHttpClient(), FakeSettingsRepository())
            advanceUntilIdle()

            viewModel.selectDirectory("/Users/danker/projects/IMbot")

            assertEquals("/Users/danker/projects/IMbot", viewModel.uiState.value.cwd)
        }

    @Test
    fun `goToStep navigates and triggers appropriate loads`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    rootsResult =
                        Result.success(
                            listOf(
                                root(
                                    id = "claude-root",
                                    provider = "claude",
                                    path = "/Users/danker/projects",
                                ),
                            ),
                        )
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")

            viewModel.goToStep(1)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.step)
            assertEquals(1, relay.getHostRootsCalls)

            viewModel.goToStep(2)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.step)
            assertEquals(1, relay.getHostRootsCalls)

            viewModel.goToStep(0)
            advanceUntilIdle()
            assertEquals(0, viewModel.uiState.value.step)
            assertEquals(2, relay.getHostsCalls)
        }

    @Test
    fun `createSession succeeds and emits SessionCreated event`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    createSessionResult = Result.success(SessionResponse(sessionId = "session-123", rawJson = "{}"))
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")
            viewModel.selectDirectory("/Users/danker/projects")
            viewModel.updatePrompt("  Analyze this repository  ")
            viewModel.updateModel("opus")

            val eventDeferred = backgroundScope.async { viewModel.events.first() }

            viewModel.createSession()
            advanceUntilIdle()

            assertEquals(NewSessionEvent.SessionCreated("session-123"), eventDeferred.await())
            assertEquals(
                CreateSessionRequest(
                    relayUrl = "https://relay.example.com",
                    token = "test-token",
                    provider = "claude",
                    hostId = "macbook-1",
                    cwd = "/Users/danker/projects",
                    prompt = "Analyze this repository",
                    permissionMode = "bypassPermissions",
                    model = "opus",
                ),
                relay.lastCreateSessionRequest,
            )
            assertFalse(viewModel.uiState.value.isCreating)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `all providers offline shows disabled state with no selection`() =
        runTest(mainDispatcherRule.dispatcher) {
            val offlineHosts =
                listOf(
                    host(id = "macbook-1", status = "offline", providers = listOf("claude", "book")),
                    host(id = "relay-local", type = "relay", status = "offline", providers = listOf("openclaw")),
                )
            val relay = FakeRelayHttpClient().apply { hostsResult = Result.success(offlineHosts) }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(offlineHosts, state.hosts)
            assertNull(state.provider)
            assertNull(state.hostId)
            assertFalse(canMoveNext(state))
        }

    @Test
    fun `empty roots list renders empty state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    rootsResult = Result.success(emptyList())
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")

            viewModel.loadRoots()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.roots.isEmpty())
            assertTrue(state.browseEntries.isEmpty())
            assertNull(state.directoryError)
            assertFalse(state.isLoadingRoots)
        }

    @Test
    fun `empty browse results renders empty state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    browseResult =
                        Result.success(
                            BrowseResult(path = "/Users/danker/projects", directories = emptyList()),
                        )
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")

            viewModel.browseDirectory("/Users/danker/projects")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("/Users/danker/projects", state.browsePath)
            assertTrue(state.browseEntries.isEmpty())
            assertNull(state.directoryError)
        }

    @Test
    fun `browse result capped at 200 entries with truncation message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    browseResult =
                        Result.success(
                            BrowseResult(
                                path = "/Users/danker/projects",
                                directories =
                                    (1..205).map { index ->
                                        entry(name = "dir-$index", path = "/Users/danker/projects/dir-$index")
                                    },
                            ),
                        )
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")

            viewModel.browseDirectory("/Users/danker/projects")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(200, state.browseEntries.size)
            assertNull(state.directoryError)
            assertEquals("目录条目过多，仅显示前 200 项", state.directoryWarning)
            assertEquals("dir-200", state.browseEntries.last().name)
        }

    @Test
    fun `back navigation from step 3 preserves browse state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val roots = listOf(root(id = "claude-root", provider = "claude", path = "/Users/danker/projects"))
            val entries = listOf(entry(name = "IMbot", path = "/Users/danker/projects/IMbot"))
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    rootsResult = Result.success(roots)
                    browseResult = Result.success(BrowseResult(path = "/Users/danker/projects", directories = entries))
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")
            viewModel.goToStep(1)
            advanceUntilIdle()
            viewModel.browseDirectory("/Users/danker/projects")
            advanceUntilIdle()
            viewModel.selectDirectory("/Users/danker/projects")
            val rootsCallsBeforeBack = relay.getHostRootsCalls

            viewModel.goToStep(2)
            advanceUntilIdle()
            viewModel.goToStep(1)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.step)
            assertEquals("/Users/danker/projects", state.browsePath)
            assertEquals(entries, state.browseEntries)
            assertEquals(
                listOf(
                    DirectoryBreadcrumb(label = "/", path = "/"),
                    DirectoryBreadcrumb(label = "Users", path = "/Users"),
                    DirectoryBreadcrumb(label = "danker", path = "/Users/danker"),
                    DirectoryBreadcrumb(label = "projects", path = "/Users/danker/projects"),
                ),
                state.breadcrumbs,
            )
            assertEquals("/Users/danker/projects", state.cwd)
            assertEquals(rootsCallsBeforeBack, relay.getHostRootsCalls)
        }

    @Test
    fun `rapid provider switch discards stale responses requestGeneration`() =
        runTest(mainDispatcherRule.dispatcher) {
            val firstRoots = CompletableDeferred<Result<List<RelayWorkspaceRoot>>>()
            val secondRoots = CompletableDeferred<Result<List<RelayWorkspaceRoot>>>()
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    getHostRootsHandler = { _, _, _ ->
                        when (getHostRootsCalls) {
                            1 -> firstRoots.await()
                            2 -> secondRoots.await()
                            else -> error("Unexpected roots call")
                        }
                    }
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()

            viewModel.selectProvider("claude", "macbook-1")
            viewModel.loadRoots()
            runCurrent()

            viewModel.selectProvider("openclaw", "relay-local")
            viewModel.loadRoots()
            runCurrent()

            secondRoots.complete(
                Result.success(
                    listOf(
                        root(
                            id = "openclaw-root",
                            hostId = "relay-local",
                            provider = "openclaw",
                            path = "/srv/openclaw",
                        ),
                    ),
                ),
            )
            firstRoots.complete(
                Result.success(
                    listOf(
                        root(
                            id = "claude-root",
                            provider = "claude",
                            path = "/Users/danker/projects",
                        ),
                    ),
                ),
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("openclaw", state.provider)
            assertEquals("relay-local", state.hostId)
            assertEquals(
                listOf(
                    root(
                        id = "openclaw-root",
                        hostId = "relay-local",
                        provider = "openclaw",
                        path = "/srv/openclaw",
                    ),
                ),
                state.roots,
            )
        }

    @Test
    fun `loadHosts failure sets error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.failure(IllegalStateException("加载主机失败: 网络错误"))
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()

            assertEquals("加载主机失败: 网络错误", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoadingHosts)
        }

    @Test
    fun `loadRoots failure sets directoryError message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    rootsResult = Result.failure(IllegalStateException("目录加载失败"))
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")

            viewModel.loadRoots()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("目录加载失败", state.directoryError)
            assertFalse(state.isLoadingRoots)
            assertNull(state.pendingBrowsePath)
        }

    @Test
    fun `browseDirectory failure sets directoryError and preserves pendingBrowsePath`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    browseResult = Result.failure(IllegalStateException("浏览失败"))
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")

            viewModel.browseDirectory("/Users/danker/projects")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("浏览失败", state.directoryError)
            assertEquals("/Users/danker/projects", state.pendingBrowsePath)
            assertFalse(state.isLoadingBrowse)
        }

    @Test
    fun `createSession failure sets error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    createSessionResult = Result.failure(IllegalStateException("创建失败"))
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")
            viewModel.selectDirectory("/Users/danker/projects")
            viewModel.updatePrompt("Start")

            viewModel.createSession()
            advanceUntilIdle()

            assertEquals("创建失败", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isCreating)
        }

    @Test
    fun `host goes offline before submit shows offline error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val onlineHosts = onlineHosts()
            val refreshedHosts =
                listOf(
                    host(id = "macbook-1", status = "offline", providers = listOf("claude", "book")),
                    host(id = "relay-local", type = "relay", providers = listOf("openclaw")),
                )
            val hostResults = ArrayDeque(listOf(Result.success(onlineHosts), Result.success(refreshedHosts)))
            val relay =
                FakeRelayHttpClient().apply {
                    getHostsHandler = { _, _ -> hostResults.removeFirst() }
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")
            viewModel.selectDirectory("/Users/danker/projects")
            viewModel.updatePrompt("Start")

            viewModel.createSession()
            advanceUntilIdle()

            assertEquals(providerOfflineMessage("claude"), viewModel.uiState.value.error)
            assertEquals(0, relay.createSessionCalls)
        }

    @Test
    fun `unconfigured relay settings shows config error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settings = FakeSettingsRepository(RelaySettings(relayUrl = "", token = ""))
            val relay = FakeRelayHttpClient()

            val viewModel = NewSessionViewModel(relay, settings)
            advanceUntilIdle()

            assertEquals("请先在设置页完成 Relay 配置", viewModel.uiState.value.error)
            assertEquals(0, relay.getHostsCalls)
        }

    @Test
    fun `double tap createSession ignored when isCreating`() =
        runTest(mainDispatcherRule.dispatcher) {
            val createGate = CompletableDeferred<Result<SessionResponse>>()
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    createSessionHandler = { _, _, _, _, _, _, _, _ -> createGate.await() }
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")
            viewModel.selectDirectory("/Users/danker/projects")
            viewModel.updatePrompt("Start")

            viewModel.createSession()
            runCurrent()
            viewModel.createSession()
            runCurrent()

            assertTrue(viewModel.uiState.value.isCreating)
            assertEquals(1, relay.createSessionCalls)

            createGate.complete(Result.success(SessionResponse(sessionId = "session-1", rawJson = "{}")))
            advanceUntilIdle()
        }

    @Test
    fun `double loadHosts ignored when isLoadingHosts`() =
        runTest(mainDispatcherRule.dispatcher) {
            val hostsGate = CompletableDeferred<Result<List<RelayHost>>>()
            val relay =
                FakeRelayHttpClient().apply {
                    getHostsHandler = { _, _ -> hostsGate.await() }
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            runCurrent()

            assertTrue(viewModel.uiState.value.isLoadingHosts)
            viewModel.loadHosts()
            runCurrent()

            assertEquals(1, relay.getHostsCalls)

            hostsGate.complete(Result.success(onlineHosts()))
            advanceUntilIdle()
        }

    @Test
    fun `double loadRoots ignored when isLoadingRoots`() =
        runTest(mainDispatcherRule.dispatcher) {
            val rootsGate = CompletableDeferred<Result<List<RelayWorkspaceRoot>>>()
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    getHostRootsHandler = { _, _, _ -> rootsGate.await() }
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")

            viewModel.loadRoots()
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoadingRoots)

            viewModel.loadRoots()
            runCurrent()

            assertEquals(1, relay.getHostRootsCalls)

            rootsGate.complete(
                Result.success(
                    listOf(
                        root(
                            id = "claude-root",
                            provider = "claude",
                            path = "/Users/danker/projects",
                        ),
                    ),
                ),
            )
            advanceUntilIdle()
        }

    @Test
    fun `double browseDirectory ignored when isLoadingBrowse`() =
        runTest(mainDispatcherRule.dispatcher) {
            val browseGate = CompletableDeferred<Result<BrowseResult>>()
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(onlineHosts())
                    getBrowseDirectoryHandler = { _, _, _, _ -> browseGate.await() }
                }

            val viewModel = NewSessionViewModel(relay, FakeSettingsRepository())
            advanceUntilIdle()
            viewModel.selectProvider("claude", "macbook-1")

            viewModel.browseDirectory("/Users/danker/projects")
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoadingBrowse)

            viewModel.browseDirectory("/Users/danker/projects/src")
            runCurrent()

            assertEquals(1, relay.browseDirectoryCalls)
            assertEquals("/Users/danker/projects", viewModel.uiState.value.pendingBrowsePath)

            browseGate.complete(
                Result.success(
                    BrowseResult(path = "/Users/danker/projects", directories = emptyList()),
                ),
            )
            advanceUntilIdle()
        }

    @Test
    fun `updateModel rejects invalid model names`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = NewSessionViewModel(FakeRelayHttpClient(), FakeSettingsRepository())
            advanceUntilIdle()

            viewModel.updateModel("invalid-model")

            assertEquals("sonnet", viewModel.uiState.value.model)
        }

    @Test
    fun `selectDirectory ignores blank paths`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = NewSessionViewModel(FakeRelayHttpClient(), FakeSettingsRepository())
            advanceUntilIdle()

            viewModel.selectDirectory("   ")

            assertNull(viewModel.uiState.value.cwd)
        }

    private class FakeRelayHttpClient : RelayHttpClient(OkHttpClient()) {
        var hostsResult: Result<List<RelayHost>> = Result.success(emptyList())
        var rootsResult: Result<List<RelayWorkspaceRoot>> = Result.success(emptyList())
        var browseResult: Result<BrowseResult> =
            Result.success(BrowseResult(path = "/", directories = emptyList()))
        var createSessionResult: Result<SessionResponse> =
            Result.success(SessionResponse(sessionId = "test-id", rawJson = "{}"))

        var getHostsCalls = 0
        var getHostRootsCalls = 0
        var browseDirectoryCalls = 0
        var createSessionCalls = 0

        var getHostsHandler: suspend (String, String) -> Result<List<RelayHost>> = { _, _ -> hostsResult }
        var getHostRootsHandler: suspend (String, String, String) -> Result<List<RelayWorkspaceRoot>> =
            { _, _, _ -> rootsResult }
        var getBrowseDirectoryHandler: suspend (String, String, String, String) -> Result<BrowseResult> =
            { _, _, _, _ -> browseResult }
        var createSessionHandler:
            suspend (String, String, String, String, String, String, String, String?) -> Result<SessionResponse> =
            { _, _, _, _, _, _, _, _ -> createSessionResult }

        val rootRequests = mutableListOf<RootRequest>()
        val browseRequests = mutableListOf<BrowseRequest>()
        var lastCreateSessionRequest: CreateSessionRequest? = null

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
            return getBrowseDirectoryHandler(relayUrl, token, hostId, path)
        }

        override suspend fun createSession(
            relayUrl: String,
            token: String,
            provider: String,
            hostId: String,
            cwd: String,
            prompt: String,
            permissionMode: String,
            model: String?,
        ): Result<SessionResponse> {
            createSessionCalls++
            lastCreateSessionRequest =
                CreateSessionRequest(
                    relayUrl = relayUrl,
                    token = token,
                    provider = provider,
                    hostId = hostId,
                    cwd = cwd,
                    prompt = prompt,
                    permissionMode = permissionMode,
                    model = model,
                )
            return createSessionHandler(relayUrl, token, provider, hostId, cwd, prompt, permissionMode, model)
        }
    }

    private class FakeSettingsRepository(
        initialSettings: RelaySettings = defaultSettings(),
    ) : SettingsRepository(FakeSharedPreferences()) {
        init {
            save(initialSettings)
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

    private fun onlineHosts(): List<RelayHost> =
        listOf(
            host(id = "macbook-1", providers = listOf("claude", "book")),
            host(id = "relay-local", type = "relay", providers = listOf("openclaw")),
        )

    private fun host(
        id: String,
        type: String = "companion",
        status: String = "online",
        providers: List<String>,
    ) = RelayHost(
        id = id,
        name = id,
        type = type,
        status = status,
        providers = providers,
    )

    private fun root(
        id: String,
        hostId: String = "macbook-1",
        provider: String,
        path: String,
    ) = RelayWorkspaceRoot(
        id = id,
        hostId = hostId,
        provider = provider,
        path = path,
        label = null,
    )

    private fun entry(
        name: String,
        path: String,
    ) = BrowseEntry(
        name = name,
        path = path,
    )
}

private fun defaultSettings() =
    RelaySettings(
        relayUrl = "https://relay.example.com",
        token = "test-token",
    )

private data class RootRequest(
    val relayUrl: String,
    val token: String,
    val hostId: String,
)

private data class BrowseRequest(
    val relayUrl: String,
    val token: String,
    val hostId: String,
    val path: String,
)

private data class CreateSessionRequest(
    val relayUrl: String,
    val token: String,
    val provider: String,
    val hostId: String,
    val cwd: String,
    val prompt: String,
    val permissionMode: String,
    val model: String?,
)
