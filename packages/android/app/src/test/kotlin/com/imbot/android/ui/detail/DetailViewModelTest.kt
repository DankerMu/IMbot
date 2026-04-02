@file:Suppress("MaxLineLength")

package com.imbot.android.ui.detail

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import com.imbot.android.data.ErrorStateManager
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.ConnectionState
import com.imbot.android.network.RelayEventPage
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelaySession
import com.imbot.android.network.RelayWsClient
import com.imbot.android.network.ServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass", "TooManyFunctions")
class DetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init with sessionId loads session and subscribes to websocket`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val ws = FakeRelayWsClient()

            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            assertEquals(1, relay.getSessionCalls)
            assertEquals(1, relay.getSessionEventsCalls)
            assertEquals(listOf(TEST_SESSION.id), ws.subscriptions)
            assertEquals(TEST_SESSION, viewModel.uiState.value.session)
            // running status: input disabled (waiting for Claude to finish)
            assertFalse(viewModel.uiState.value.canSend)
        }

    @Test
    fun `websocket events update message timeline through event processor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            ws.emitEvent(event(seq = 1, eventType = "user_message", payload = payload("text" to "你好")))
            ws.emitEvent(event(seq = 2, eventType = "assistant_delta", payload = payload("text" to "正在处理")))
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(2, messages.size)
            assertUserMessage(messages[0], "你好")
            assertAgentMessage(messages[1], "正在处理", isStreaming = true)
        }

    @Test
    fun `sendMessage adds optimistic message and removes it on failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "idle"))
                    sendMessageResult = Result.failure(IllegalStateException("发送失败"))
                }
            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            viewModel.sendMessage("  帮我总结  ")

            val optimisticMessage = viewModel.uiState.value.messages.single() as MessageItem.UserMessage
            assertEquals("帮我总结", optimisticMessage.text)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.messages.isEmpty())
            assertEquals("发送失败", viewModel.uiState.value.error)
            assertEquals(1, relay.sendMessageCalls)
        }

    @Test
    fun `cancelSession uses API session status on success and surfaces error on failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val successRelay =
                FakeRelayHttpClient().apply {
                    cancelSessionResult = Result.success(TEST_SESSION.copy(status = "completed"))
                }
            val successViewModel = createViewModel(relay = successRelay)
            advanceUntilIdle()

            successViewModel.cancelSession()
            advanceUntilIdle()

            assertEquals(1, successRelay.cancelSessionCalls)
            assertEquals("completed", successViewModel.uiState.value.session?.status)
            assertFalse(successViewModel.uiState.value.canSend)

            val failureRelay =
                FakeRelayHttpClient().apply {
                    cancelSessionResult = Result.failure(IllegalStateException("取消失败"))
                }
            val failureViewModel = createViewModel(relay = failureRelay)
            advanceUntilIdle()

            failureViewModel.cancelSession()
            advanceUntilIdle()

            assertEquals("取消失败", failureViewModel.uiState.value.error)
        }

    @Test
    fun `resumeSession uses API session status on success and surfaces error on failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val successRelay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "completed"))
                    resumeSessionResult = Result.success(TEST_SESSION.copy(status = "running"))
                }
            val successViewModel = createViewModel(relay = successRelay)
            advanceUntilIdle()

            successViewModel.resumeSession()
            advanceUntilIdle()

            assertEquals(1, successRelay.resumeSessionCalls)
            assertEquals("running", successViewModel.uiState.value.session?.status)
            assertFalse(successViewModel.uiState.value.canSend)

            val failureRelay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "failed"))
                    resumeSessionResult = Result.failure(IllegalStateException("恢复失败"))
                }
            val failureViewModel = createViewModel(relay = failureRelay)
            advanceUntilIdle()

            failureViewModel.resumeSession()
            advanceUntilIdle()

            assertEquals("恢复失败", failureViewModel.uiState.value.error)
        }

    @Test
    fun `deleteSession navigates back on success and surfaces error on failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val successRelay = FakeRelayHttpClient()
            val successViewModel = createViewModel(relay = successRelay)
            advanceUntilIdle()

            val successEvent =
                backgroundScope.async {
                    successViewModel.events.first { event -> event is DetailEvent.NavigateBack }
                }

            successViewModel.deleteSession()
            advanceUntilIdle()

            assertEquals(1, successRelay.deleteSessionCalls)
            assertEquals(DetailEvent.NavigateBack(refreshHome = true), successEvent.await())

            val failureRelay =
                FakeRelayHttpClient().apply {
                    deleteSessionResult = Result.failure(IllegalStateException("删除失败"))
                }
            val failureViewModel = createViewModel(relay = failureRelay)
            advanceUntilIdle()

            failureViewModel.deleteSession()
            advanceUntilIdle()

            assertEquals("删除失败", failureViewModel.uiState.value.error)
        }

    @Test
    fun `completed status disables input`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            ws.emitEvent(event(seq = 1, eventType = "session_status_changed", payload = payload("status" to "completed")))
            advanceUntilIdle()

            assertEquals("completed", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
        }

    @Test
    fun `loading a completed session auto resumes it`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "completed"))
                    resumeSessionResult = Result.success(TEST_SESSION.copy(status = "running"))
                }

            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            assertEquals(1, relay.resumeSessionCalls)
            assertEquals("running", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
        }

    @Test
    fun `loading a failed session auto resumes it`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "failed"))
                    resumeSessionResult = Result.success(TEST_SESSION.copy(status = "running"))
                }

            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            assertEquals(1, relay.resumeSessionCalls)
            assertEquals("running", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
        }

    @Test
    fun `loading a cancelled session auto resumes it`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "cancelled"))
                    resumeSessionResult = Result.success(TEST_SESSION.copy(status = "running"))
                }

            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            assertEquals(1, relay.resumeSessionCalls)
            assertEquals("running", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
        }

    @Test
    fun `session_started and session_result update session status`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "queued"))
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            assertEquals("queued", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)

            ws.emitEvent(event(seq = 1, eventType = "session_started"))
            advanceUntilIdle()

            assertEquals("running", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
            assertStatusChange(viewModel.uiState.value.messages.single(), status = "running", message = null)

            ws.emitEvent(
                event(
                    seq = 2,
                    eventType = "session_result",
                    payload = payload("status" to "completed", "message" to "任务完成"),
                ),
            )
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(2, messages.size)
            assertStatusChange(messages[0], status = "running", message = null)
            assertStatusChange(messages[1], status = "completed", message = "任务完成")
            assertEquals("completed", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
        }

    @Test
    fun `approval events render as generic status messages while session stays running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "approval_required",
                    payload =
                        payload(
                            "call_id" to "call-1",
                            "tool_name" to "bash",
                            "description" to "Run a shell command",
                        ),
                ),
            )
            advanceUntilIdle()

            assertEquals("running", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
            assertStatusChange(
                viewModel.uiState.value.messages.single(),
                status = "running",
                message = "Approval required: Run a shell command",
            )
        }

    @Test
    fun `failed status shows error and disables input`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            ws.emitEvent(event(seq = 1, eventType = "session_error", payload = payload("message" to "provider timeout")))
            advanceUntilIdle()

            assertEquals("failed", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
            assertEquals("provider timeout", viewModel.uiState.value.error)
        }

    @Test
    fun `loadSession failure sets error and retry succeeds`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.failure(IllegalStateException("加载失败"))
                }
            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            assertEquals("加载失败", viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.session)

            relay.getSessionResult = Result.success(TEST_SESSION)
            viewModel.loadSession()
            advanceUntilIdle()

            assertEquals(TEST_SESSION, viewModel.uiState.value.session)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `disconnect updates connection state and reconnect catches up without duplicates`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionEventsHandler =
                        { _, _, _, sinceSeq, _ ->
                            when (sinceSeq) {
                                0 -> Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                                1 ->
                                    Result.success(
                                        RelayEventPage(
                                            events =
                                                listOf(
                                                    event(seq = 1, eventType = "user_message", payload = payload("text" to "你好")),
                                                    event(seq = 2, eventType = "assistant_message", payload = payload("text" to "已恢复")),
                                                ),
                                            hasMore = false,
                                        ),
                                    )

                                else -> Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                            }
                        }
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            ws.emitEvent(event(seq = 1, eventType = "user_message", payload = payload("text" to "你好")))
            advanceUntilIdle()

            ws.emitConnectionState(ConnectionState.Disconnected("socket lost"))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isConnected)

            ws.emitConnectionState(ConnectionState.Connected)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isConnected)
            assertEquals(listOf(0, 1), relay.getSessionEventRequests.map(SessionEventsRequest::sinceSeq))
            val messages = viewModel.uiState.value.messages
            assertEquals(2, messages.size)
            assertUserMessage(messages[0], "你好")
            assertAgentMessage(messages[1], "已恢复", isStreaming = false)
        }

    @Test
    fun `live websocket events are buffered until reconnect catch up completes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reconnectGate = CompletableDeferred<Result<RelayEventPage>>()
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionEventsHandler =
                        { _, _, _, sinceSeq, _ ->
                            when (sinceSeq) {
                                0 -> Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                                1 -> reconnectGate.await()
                                else -> Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                            }
                        }
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            ws.emitEvent(event(seq = 1, eventType = "user_message", payload = payload("text" to "你好")))
            advanceUntilIdle()

            ws.emitConnectionState(ConnectionState.Disconnected("socket lost"))
            advanceUntilIdle()

            ws.emitConnectionState(ConnectionState.Connected)
            runCurrent()

            ws.emitEvent(
                event(
                    seq = 3,
                    eventType = "session_status_changed",
                    payload = payload("status" to "completed"),
                ),
            )
            runCurrent()

            assertEquals(1, viewModel.uiState.value.messages.size)
            assertEquals("running", viewModel.uiState.value.session?.status)

            reconnectGate.complete(
                Result.success(
                    RelayEventPage(
                        events =
                            listOf(
                                event(
                                    seq = 2,
                                    eventType = "assistant_message",
                                    payload = payload("text" to "补拉消息"),
                                ),
                            ),
                        hasMore = false,
                    ),
                ),
            )
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(3, messages.size)
            assertUserMessage(messages[0], "你好")
            assertAgentMessage(messages[1], "补拉消息", isStreaming = false)
            assertStatusChange(messages[2], status = "completed", message = null)
            assertEquals("completed", viewModel.uiState.value.session?.status)
        }

    @Test
    fun `double tap send is ignored while first request is in flight`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Result<Unit>>()
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "idle"))
                    sendMessageHandler = { _, _, _, _ -> gate.await() }
                }
            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            viewModel.sendMessage("第一条")
            runCurrent()
            viewModel.sendMessage("第二条")
            runCurrent()

            assertEquals(1, relay.sendMessageCalls)
            assertEquals(1, viewModel.uiState.value.messages.size)

            gate.complete(Result.success(Unit))
            advanceUntilIdle()
        }

    @Test
    fun `clearError dismisses existing error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.failure(IllegalStateException("加载失败"))
                }
            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            assertEquals("加载失败", viewModel.uiState.value.error)

            viewModel.clearError()

            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `onMessageLongPress sets messageMenuTarget`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val item = agentMenuTarget()

            viewModel.onMessageLongPress(item)

            assertEquals(item, viewModel.uiState.value.messageMenuTarget)
        }

    @Test
    fun `onDismissMessageMenu clears messageMenuTarget`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onMessageLongPress(agentMenuTarget())
            viewModel.onDismissMessageMenu()

            assertNull(viewModel.uiState.value.messageMenuTarget)
        }

    @Test
    fun `onEnterSelectionMode sets id and clears menuTarget`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val item = agentMenuTarget()

            viewModel.onMessageLongPress(item)
            viewModel.onEnterSelectionMode(item.id)

            assertNull(viewModel.uiState.value.messageMenuTarget)
            assertEquals(item.id, viewModel.uiState.value.selectionModeMessageId)
        }

    @Test
    fun `onExitSelectionMode clears selectionModeMessageId`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEnterSelectionMode("agent-1")
            viewModel.onExitSelectionMode()

            assertNull(viewModel.uiState.value.selectionModeMessageId)
        }

    @Test
    fun `new message arrival clears selectionModeMessageId`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            viewModel.onEnterSelectionMode("agent-1")
            ws.emitEvent(event(seq = 1, eventType = "user_message", payload = payload("text" to "你好")))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectionModeMessageId)
        }

    @Test
    fun `selectionMode and menuTarget are mutually exclusive`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val item = userMenuTarget()

            viewModel.onEnterSelectionMode("agent-1")
            viewModel.onMessageLongPress(item)

            assertEquals(item, viewModel.uiState.value.messageMenuTarget)
            assertNull(viewModel.uiState.value.selectionModeMessageId)

            viewModel.onEnterSelectionMode("user-1")

            assertNull(viewModel.uiState.value.messageMenuTarget)
            assertEquals("user-1", viewModel.uiState.value.selectionModeMessageId)
        }

    private fun createViewModel(
        relay: FakeRelayHttpClient = FakeRelayHttpClient(),
        ws: FakeRelayWsClient = FakeRelayWsClient(),
    ): DetailViewModel =
        DetailViewModel(
            relayHttpClient = relay,
            relayWsClient = ws,
            settingsRepository = FakeSettingsRepository(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to TEST_SESSION.id)),
        )
}

private const val TIMESTAMP = "2026-03-31T12:00:00Z"

private val TEST_SESSION =
    RelaySession(
        id = "session-123",
        provider = "claude",
        hostId = "macbook-1",
        workspaceCwd = "/Users/danker/Desktop/AI-vault/IMbot",
        initialPrompt = "分析这个仓库",
        model = "sonnet",
        status = "running",
        errorMessage = null,
        createdAt = TIMESTAMP,
        updatedAt = TIMESTAMP,
        lastActiveAt = TIMESTAMP,
    )

private fun event(
    seq: Int,
    eventType: String,
    payload: JSONObject? = null,
    timestamp: String = TIMESTAMP,
) = ServerMessage.Event(
    sessionId = TEST_SESSION.id,
    seq = seq,
    eventType = eventType,
    payload = payload,
    timestamp = timestamp,
)

private fun payload(vararg pairs: Pair<String, Any?>): JSONObject =
    JSONObject().apply {
        pairs.forEach { (key, value) ->
            put(key, value)
        }
    }

private fun assertUserMessage(
    item: MessageItem,
    text: String,
    timestamp: String = TIMESTAMP,
) {
    val message = item as MessageItem.UserMessage
    assertTrue(message.id.isNotBlank())
    assertEquals(text, message.text)
    assertEquals(timestamp, message.timestamp)
}

private fun assertAgentMessage(
    item: MessageItem,
    content: String,
    isStreaming: Boolean,
    timestamp: String = TIMESTAMP,
) {
    val message = item as MessageItem.AgentMessage
    assertTrue(message.id.isNotBlank())
    assertEquals(content, message.content)
    assertEquals(isStreaming, message.isStreaming)
    assertEquals(timestamp, message.timestamp)
}

private fun assertStatusChange(
    item: MessageItem,
    status: String,
    message: String?,
) {
    val statusChange = item as MessageItem.StatusChange
    assertTrue(statusChange.id.isNotBlank())
    assertEquals(status, statusChange.status)
    assertEquals(message, statusChange.message)
}

private fun agentMenuTarget() =
    MessageItem.AgentMessage(
        id = "agent-1",
        content = "hello",
        isStreaming = false,
        timestamp = TIMESTAMP,
    )

private fun userMenuTarget() =
    MessageItem.UserMessage(
        id = "user-1",
        text = "world",
        timestamp = TIMESTAMP,
    )

private data class SessionEventsRequest(
    val relayUrl: String,
    val token: String,
    val sessionId: String,
    val sinceSeq: Int,
    val limit: Int,
)

private class FakeRelayHttpClient : RelayHttpClient(OkHttpClient()) {
    var getSessionResult: Result<RelaySession> = Result.success(TEST_SESSION)
    var getSessionEventsResult: Result<RelayEventPage> = Result.success(RelayEventPage(events = emptyList(), hasMore = false))
    var sendMessageResult: Result<Unit> = Result.success(Unit)
    var resumeSessionResult: Result<RelaySession> = Result.success(TEST_SESSION.copy(status = "running"))
    var cancelSessionResult: Result<RelaySession> = Result.success(TEST_SESSION.copy(status = "cancelled"))
    var deleteSessionResult: Result<Unit> = Result.success(Unit)

    var getSessionCalls = 0
    var getSessionEventsCalls = 0
    var sendMessageCalls = 0
    var resumeSessionCalls = 0
    var cancelSessionCalls = 0
    var deleteSessionCalls = 0

    var getSessionHandler: suspend (String, String, String) -> Result<RelaySession> = { _, _, _ -> getSessionResult }
    var getSessionEventsHandler: suspend (String, String, String, Int, Int) -> Result<RelayEventPage> =
        { _, _, _, _, _ -> getSessionEventsResult }
    var sendMessageHandler: suspend (String, String, String, String) -> Result<Unit> = { _, _, _, _ -> sendMessageResult }
    var resumeSessionHandler: suspend (String, String, String) -> Result<RelaySession> = { _, _, _ -> resumeSessionResult }
    var cancelSessionHandler: suspend (String, String, String) -> Result<RelaySession> = { _, _, _ -> cancelSessionResult }
    var deleteSessionHandler: suspend (String, String, String) -> Result<Unit> = { _, _, _ -> deleteSessionResult }

    val getSessionEventRequests = mutableListOf<SessionEventsRequest>()

    override suspend fun getSession(
        relayUrl: String,
        token: String,
        sessionId: String,
    ): Result<RelaySession> {
        getSessionCalls++
        return getSessionHandler(relayUrl, token, sessionId)
    }

    override suspend fun getSessionEvents(
        relayUrl: String,
        token: String,
        sessionId: String,
        sinceSeq: Int,
        limit: Int,
    ): Result<RelayEventPage> {
        getSessionEventsCalls++
        getSessionEventRequests +=
            SessionEventsRequest(
                relayUrl = relayUrl,
                token = token,
                sessionId = sessionId,
                sinceSeq = sinceSeq,
                limit = limit,
            )
        return getSessionEventsHandler(relayUrl, token, sessionId, sinceSeq, limit)
    }

    override suspend fun sendMessage(
        relayUrl: String,
        token: String,
        sessionId: String,
        text: String,
    ): Result<Unit> {
        sendMessageCalls++
        return sendMessageHandler(relayUrl, token, sessionId, text)
    }

    override suspend fun resumeSession(
        relayUrl: String,
        token: String,
        sessionId: String,
    ): Result<RelaySession> {
        resumeSessionCalls++
        return resumeSessionHandler(relayUrl, token, sessionId)
    }

    override suspend fun cancelSession(
        relayUrl: String,
        token: String,
        sessionId: String,
    ): Result<RelaySession> {
        cancelSessionCalls++
        return cancelSessionHandler(relayUrl, token, sessionId)
    }

    override suspend fun deleteSession(
        relayUrl: String,
        token: String,
        sessionId: String,
    ): Result<Unit> {
        deleteSessionCalls++
        return deleteSessionHandler(relayUrl, token, sessionId)
    }
}

private class FakeRelayWsClient : RelayWsClient(OkHttpClient(), ErrorStateManager()) {
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = connectionStateFlow.asStateFlow()

    private val messagesFlow = MutableSharedFlow<com.imbot.android.network.ServerMessage>(extraBufferCapacity = 16)
    override val messages: SharedFlow<com.imbot.android.network.ServerMessage> = messagesFlow.asSharedFlow()

    private val eventsFlow = MutableSharedFlow<ServerMessage.Event>(extraBufferCapacity = 16)
    override val events: SharedFlow<ServerMessage.Event> = eventsFlow.asSharedFlow()

    private val rawMessagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val rawMessages: SharedFlow<String> = rawMessagesFlow.asSharedFlow()

    val subscriptions = mutableListOf<String>()
    var clearSubscriptionCalls = 0

    override fun subscribe(sessionId: String) {
        subscriptions += sessionId
    }

    override fun clearSubscription() {
        clearSubscriptionCalls++
    }

    fun emitConnectionState(state: ConnectionState) {
        connectionStateFlow.value = state
    }

    suspend fun emitEvent(event: ServerMessage.Event) {
        eventsFlow.emit(event)
        messagesFlow.emit(event)
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

private fun defaultSettings() =
    RelaySettings(
        relayUrl = "https://relay.example.com",
        token = "test-token",
    )
