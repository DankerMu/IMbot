package com.imbot.android.ui.settings

import com.imbot.android.FakeRelayWsClient
import com.imbot.android.FakeSessionStore
import com.imbot.android.FakeSettingsRepository
import com.imbot.android.MainDispatcherRule
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.ConnectionState
import com.imbot.android.network.ServerMessage
import com.imbot.android.ui.workspace.FakeWorkspaceRepository
import com.imbot.android.ui.workspace.hostWithRoots
import com.imbot.android.ui.workspace.workspaceHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init observes relayUrl and theme mode from repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository =
                FakeSettingsRepository(
                    initialSettings = RelaySettings("https://relay.example.com", "token"),
                    initialThemeMode = SettingsRepository.THEME_MODE_DARK,
                )

            val viewModel =
                createViewModel(
                    settingsRepository = settingsRepository,
                )
            advanceUntilIdle()

            assertEquals("https://relay.example.com", viewModel.uiState.value.relayUrl)
            assertEquals(SettingsRepository.THEME_MODE_DARK, viewModel.uiState.value.themeMode)
        }

    @Test
    fun `init observes websocket connection state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            ws.emitConnectionState(ConnectionState.Connected)
            advanceUntilIdle()
            assertEquals(ConnectionState.Connected, viewModel.uiState.value.connectionState)

            ws.emitConnectionState(ConnectionState.Disconnected("lost"))
            advanceUntilIdle()
            assertEquals(ConnectionState.Disconnected("lost"), viewModel.uiState.value.connectionState)
        }

    @Test
    fun `setTheme persists selection and reflects in uiState`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeSettingsRepository(initialSettings = RelaySettings("", ""))
            val viewModel = createViewModel(settingsRepository = settingsRepository)

            viewModel.setTheme(SettingsRepository.THEME_MODE_LIGHT)
            advanceUntilIdle()

            assertEquals(SettingsRepository.THEME_MODE_LIGHT, settingsRepository.savedThemeModes.last())
            assertEquals(SettingsRepository.THEME_MODE_LIGHT, viewModel.uiState.value.themeMode)
        }

    @Test
    fun `updateRelayUrl saves settings and reconnects websocket`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository =
                FakeSettingsRepository(
                    initialSettings = RelaySettings("https://relay.old", "secret"),
                )
            val ws = FakeRelayWsClient()
            val viewModel =
                createViewModel(
                    settingsRepository = settingsRepository,
                    ws = ws,
                )

            viewModel.updateRelayUrl("https://relay.new")
            advanceUntilIdle()

            assertEquals("https://relay.new", settingsRepository.lastSavedSettings?.relayUrl)
            assertEquals(listOf("https://relay.new" to "secret"), ws.connectRequests)
            assertEquals("https://relay.new", viewModel.uiState.value.relayUrl)
        }

    @Test
    fun `clearCache clears local cache and emits success message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sessionStore = FakeSessionStore()
            val viewModel = createViewModel(sessionStore = sessionStore)

            val event = backgroundScope.async { viewModel.events.first() }

            viewModel.clearCache()
            advanceUntilIdle()

            assertEquals(1, sessionStore.clearLocalCacheCalls)
            assertEquals(SettingsEvent.ShowMessage("已清除"), event.await())
        }

    @Test
    fun `clearCache failure emits error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val sessionStore =
                FakeSessionStore().apply {
                    clearLocalCacheError = IllegalStateException("清除失败")
                }
            val viewModel = createViewModel(sessionStore = sessionStore)

            val event = backgroundScope.async { viewModel.events.first() }

            viewModel.clearCache()
            advanceUntilIdle()

            assertEquals(SettingsEvent.ShowMessage("清除失败"), event.await())
        }

    @Test
    fun `host statuses update from websocket host_status messages`() =
        runTest(mainDispatcherRule.dispatcher) {
            val workspaceRepository =
                FakeWorkspaceRepository().apply {
                    getHostsWithRootsResult =
                        Result.success(
                            listOf(
                                hostWithRoots(
                                    workspaceHost(
                                        id = "macbook-1",
                                        type = "macbook",
                                        status = "offline",
                                    ),
                                ),
                                hostWithRoots(
                                    workspaceHost(
                                        id = "relay-local",
                                        type = "relay_local",
                                        status = "offline",
                                    ),
                                ),
                            ),
                        )
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(workspaceRepository = workspaceRepository, ws = ws)
            advanceUntilIdle()

            ws.emitMessage(ServerMessage.HostStatus(hostId = "macbook-1", status = "online"))
            ws.emitMessage(ServerMessage.HostStatus(hostId = "relay-local", status = "online"))
            advanceUntilIdle()

            assertEquals("online", viewModel.uiState.value.macbookStatus)
            assertEquals("online", viewModel.uiState.value.openClawStatus)
        }

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(initialSettings = RelaySettings("", "")),
        ws: FakeRelayWsClient = FakeRelayWsClient(),
        sessionStore: FakeSessionStore = FakeSessionStore(),
        workspaceRepository: FakeWorkspaceRepository = FakeWorkspaceRepository(),
    ) = SettingsViewModel(
        settingsRepository = settingsRepository,
        relayWsClient = ws,
        sessionStore = sessionStore,
        workspaceRepository = workspaceRepository,
    )
}
