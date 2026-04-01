package com.imbot.android.ui.onboarding

import com.imbot.android.FakeRelayHttpClient
import com.imbot.android.FakeRelayWsClient
import com.imbot.android.FakeSettingsRepository
import com.imbot.android.MainDispatcherRule
import com.imbot.android.data.RelaySettings
import com.imbot.android.network.HealthzHost
import com.imbot.android.network.HealthzResponse
import com.imbot.android.network.RelayApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init state is empty and idle`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            val state = viewModel.uiState.value
            assertEquals("", state.relayUrl)
            assertEquals("", state.token)
            assertFalse(state.isTesting)
            assertNull(state.testResult)
        }

    @Test
    fun `updateUrl and updateToken reflect immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.updateUrl("https://relay.example.com")
            viewModel.updateToken("secret-token")

            assertEquals("https://relay.example.com", viewModel.uiState.value.relayUrl)
            assertEquals("secret-token", viewModel.uiState.value.token)
        }

    @Test
    fun `testConnection with empty fields returns validation error without API call`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val viewModel = createViewModel(relay = relay)

            viewModel.testConnection()

            assertEquals(0, relay.testConnectionCalls)
            assertEquals(
                TestResult.Error("请填写完整的连接信息"),
                viewModel.uiState.value.testResult,
            )
        }

    @Test
    fun `testConnection success exposes parsed host statuses`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(authenticatedHosts())
                    testConnectionResult = Result.success(successHealthz())
                }
            val viewModel = createViewModel(relay = relay)
            viewModel.updateUrl("https://relay.example.com")
            viewModel.updateToken("secret-token")

            viewModel.testConnection()
            advanceUntilIdle()

            val result = viewModel.uiState.value.testResult as TestResult.Success
            assertEquals("1.2.3", result.response.version)
            assertEquals("online", result.response.macbookHost()?.status)
            assertEquals("offline", result.response.openClawHost()?.status)
            assertEquals(1, relay.getHostsCalls)
            assertEquals(1, relay.testConnectionCalls)
        }

    @Test
    fun `testConnection maps 401 to authentication error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult =
                        Result.failure(
                            RelayApiException(
                                statusCode = 401,
                                code = "unauthenticated",
                                message = "HTTP 401",
                            ),
                        )
                }
            val viewModel = createConfiguredViewModel(relay = relay)

            viewModel.testConnection()
            advanceUntilIdle()

            assertEquals(TestResult.Error("认证失败"), viewModel.uiState.value.testResult)
            assertEquals(1, relay.getHostsCalls)
            assertEquals(0, relay.testConnectionCalls)
        }

    @Test
    fun `testConnection maps IOException to unreachable error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.failure(IOException("boom"))
                }
            val viewModel = createConfiguredViewModel(relay = relay)

            viewModel.testConnection()
            advanceUntilIdle()

            assertEquals(TestResult.Error("无法连接"), viewModel.uiState.value.testResult)
        }

    @Test
    fun `testConnection maps timeout to timeout error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.failure(SocketTimeoutException("slow"))
                }
            val viewModel = createConfiguredViewModel(relay = relay)

            viewModel.testConnection()
            advanceUntilIdle()

            assertEquals(TestResult.Error("连接超时"), viewModel.uiState.value.testResult)
        }

    @Test
    fun `isTesting is true during request and false after completion`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Result<HealthzResponse>>()
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(authenticatedHosts())
                    testConnectionHandler = { _, _ -> gate.await() }
                }
            val viewModel = createConfiguredViewModel(relay = relay)

            viewModel.testConnection()
            runCurrent()

            assertTrue(viewModel.uiState.value.isTesting)

            gate.complete(Result.success(successHealthz()))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isTesting)
        }

    @Test
    fun `double tap guard ignores second testConnection while request is in flight`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Result<HealthzResponse>>()
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(authenticatedHosts())
                    testConnectionHandler = { _, _ -> gate.await() }
                }
            val viewModel = createConfiguredViewModel(relay = relay)

            viewModel.testConnection()
            runCurrent()
            viewModel.testConnection()

            assertEquals(1, relay.testConnectionCalls)

            gate.complete(Result.success(successHealthz()))
            advanceUntilIdle()
        }

    @Test
    fun `testConnection falls back to authenticated hosts when healthz fails after auth succeeds`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(authenticatedHosts())
                    testConnectionResult = Result.failure(IOException("healthz unavailable"))
                }
            val viewModel = createConfiguredViewModel(relay = relay)

            viewModel.testConnection()
            advanceUntilIdle()

            val result = viewModel.uiState.value.testResult as TestResult.Success
            assertEquals("unknown", result.response.version)
            assertEquals("online", result.response.macbookHost()?.status)
            assertEquals("offline", result.response.openClawHost()?.status)
        }

    @Test
    fun `saveAndProceed writes settings connects websocket and emits navigation event`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeSettingsRepository(RelaySettings("", ""))
            val relay =
                FakeRelayHttpClient().apply {
                    hostsResult = Result.success(authenticatedHosts())
                    testConnectionResult = Result.success(successHealthz())
                }
            val ws = FakeRelayWsClient()
            val viewModel =
                createConfiguredViewModel(
                    relay = relay,
                    settingsRepository = settingsRepository,
                    ws = ws,
                )

            viewModel.testConnection()
            advanceUntilIdle()

            val event = backgroundScope.async { viewModel.events.first() }

            viewModel.saveAndProceed()
            advanceUntilIdle()

            assertEquals(1, settingsRepository.saveCalls)
            assertEquals(
                RelaySettings("https://relay.example.com", "secret-token"),
                settingsRepository.lastSavedSettings,
            )
            assertEquals(listOf("https://relay.example.com" to "secret-token"), ws.connectRequests)
            assertEquals(OnboardingEvent.NavigateHome, event.await())
        }

    @Test
    fun `saveAndProceed is blocked without successful connection test`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settingsRepository = FakeSettingsRepository(RelaySettings("", ""))
            val ws = FakeRelayWsClient()
            val viewModel =
                createConfiguredViewModel(
                    settingsRepository = settingsRepository,
                    ws = ws,
                )

            viewModel.saveAndProceed()
            advanceUntilIdle()

            assertEquals(0, settingsRepository.saveCalls)
            assertTrue(ws.connectRequests.isEmpty())
        }

    @Test
    fun `invalid relay url format returns error without API call`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val viewModel = createViewModel(relay = relay)
            viewModel.updateUrl("relay.example.com")
            viewModel.updateToken("secret-token")

            viewModel.testConnection()

            assertEquals(0, relay.testConnectionCalls)
            assertEquals(TestResult.Error("请输入有效的 Relay URL"), viewModel.uiState.value.testResult)
        }

    @Test
    fun `http relay url returns validation error without API call`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val viewModel = createViewModel(relay = relay)
            viewModel.updateUrl("http://relay.example.com")
            viewModel.updateToken("secret-token")

            viewModel.testConnection()

            assertEquals(0, relay.testConnectionCalls)
            assertEquals(TestResult.Error("请输入有效的 Relay URL"), viewModel.uiState.value.testResult)
        }

    private fun createConfiguredViewModel(
        relay: FakeRelayHttpClient = FakeRelayHttpClient(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(RelaySettings("", "")),
        ws: FakeRelayWsClient = FakeRelayWsClient(),
    ): OnboardingViewModel =
        createViewModel(
            relay = relay,
            settingsRepository = settingsRepository,
            ws = ws,
        ).also { viewModel ->
            viewModel.updateUrl("https://relay.example.com")
            viewModel.updateToken("secret-token")
        }

    private fun createViewModel(
        relay: FakeRelayHttpClient = FakeRelayHttpClient(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(RelaySettings("", "")),
        ws: FakeRelayWsClient = FakeRelayWsClient(),
    ) = OnboardingViewModel(
        relayHttpClient = relay,
        settingsRepository = settingsRepository,
        relayWsClient = ws,
    )
}

private fun successHealthz() =
    HealthzResponse(
        version = "1.2.3",
        hosts =
            listOf(
                HealthzHost(
                    id = "macbook-1",
                    name = "MacBook Pro",
                    type = "macbook",
                    status = "online",
                ),
                HealthzHost(
                    id = "relay-local",
                    name = "Relay VPS",
                    type = "relay_local",
                    status = "offline",
                ),
            ),
    )

private fun authenticatedHosts() =
    listOf(
        com.imbot.android.network.RelayHost(
            id = "macbook-1",
            name = "MacBook Pro",
            type = "macbook",
            status = "online",
            providers = listOf("claude", "book"),
        ),
        com.imbot.android.network.RelayHost(
            id = "relay-local",
            name = "Relay VPS",
            type = "relay_local",
            status = "offline",
            providers = listOf("openclaw"),
        ),
    )
