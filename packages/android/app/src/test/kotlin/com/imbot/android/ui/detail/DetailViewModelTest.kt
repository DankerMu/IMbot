@file:Suppress("MaxLineLength")

package com.imbot.android.ui.detail

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.imbot.android.data.ErrorStateManager
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.local.AppDatabase
import com.imbot.android.data.local.SessionDao
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.data.repository.SessionRepository
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
import org.junit.Assert.assertNotNull
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

            assertEquals(2, relay.getSessionCalls)
            assertEquals(1, relay.getSessionEventsCalls)
            assertEquals(listOf(TEST_SESSION.id), ws.subscriptions)
            assertEquals(TEST_SESSION, viewModel.uiState.value.session)
            // running status: input disabled (waiting for Claude to finish)
            assertFalse(viewModel.uiState.value.canSend)
        }

    @Test
    fun `loadSession falls back to initial prompt when history has no user_message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionEventsResult = Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                }

            val viewModel = createViewModel(relay = relay, ws = FakeRelayWsClient())
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(1, messages.size)
            assertUserMessage(messages[0], "分析这个仓库")
        }

    @Test
    fun `loadSession publishes legacy initial prompt before catch-up refresh runs`() =
        runTest(mainDispatcherRule.dispatcher) {
            val eventsGate = CompletableDeferred<Unit>()
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionEventsHandler =
                        { _, _, _, _, _ ->
                            eventsGate.await()
                            Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                        }
                }

            val viewModel = createViewModel(relay = relay, ws = FakeRelayWsClient())
            val initialPublish =
                backgroundScope.async {
                    viewModel.uiState.first { state ->
                        state.session != null && state.messages.isNotEmpty()
                    }
                }

            runCurrent()

            val state = initialPublish.await()
            assertEquals(1, relay.getSessionCalls)
            assertEquals(1, state.messages.size)
            assertUserMessage(state.messages.single(), "分析这个仓库")

            eventsGate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `loadSession does not duplicate initial prompt when history already has user_message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionEventsResult =
                        Result.success(
                            RelayEventPage(
                                events =
                                    listOf(
                                        event(
                                            seq = 1,
                                            eventType = "user_message",
                                            payload = payload("text" to "分析这个仓库"),
                                        ),
                                    ),
                                hasMore = false,
                            ),
                        )
                }

            val viewModel = createViewModel(relay = relay, ws = FakeRelayWsClient())
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(1, messages.size)
            assertUserMessage(messages[0], "分析这个仓库")
        }

    @Test
    fun `loadSession preserves legacy initial prompt when later user messages exist`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionEventsResult =
                        Result.success(
                            RelayEventPage(
                                events =
                                    listOf(
                                        event(
                                            seq = 1,
                                            eventType = "user_message",
                                            payload = payload("text" to "继续下一步"),
                                        ),
                                    ),
                                hasMore = false,
                            ),
                        )
                }

            val viewModel = createViewModel(relay = relay, ws = FakeRelayWsClient())
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(2, messages.size)
            assertUserMessage(messages[0], "分析这个仓库")
            assertUserMessage(messages[1], "继续下一步")
        }

    @Test
    fun `loadSession still restores legacy initial prompt when a later turn repeats the same text`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionEventsResult =
                        Result.success(
                            RelayEventPage(
                                events =
                                    listOf(
                                        event(
                                            seq = 1,
                                            eventType = "assistant_message",
                                            payload = payload("text" to "先看一下仓库"),
                                        ),
                                        event(
                                            seq = 2,
                                            eventType = "user_message",
                                            payload = payload("text" to "分析这个仓库"),
                                        ),
                                    ),
                                hasMore = false,
                            ),
                        )
                }

            val viewModel = createViewModel(relay = relay, ws = FakeRelayWsClient())
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(3, messages.size)
            assertUserMessage(messages[0], "分析这个仓库")
            assertAgentMessage(messages[1], "先看一下仓库", isStreaming = false)
            assertUserMessage(messages[2], "分析这个仓库")
        }

    @Test
    fun `loadSession does not synthesize a user bubble for empty sessions`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "idle", initialPrompt = null))
                    getSessionEventsResult = Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                }

            val viewModel = createViewModel(relay = relay, ws = FakeRelayWsClient())
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.messages.isEmpty())
        }

    @Test
    fun `websocket events update message timeline through event processor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(initialPrompt = null))
                }
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
    fun `session_usage event updates uiState usage`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "session_usage",
                    payload =
                        payload(
                            "input_tokens" to 12,
                            "output_tokens" to 34,
                            "cache_creation_input_tokens" to 56,
                            "cache_read_input_tokens" to 78,
                            "total_cost_usd" to 0.12,
                            "context_window" to 200_000,
                            "model" to "claude-sonnet-4-6[1m]",
                        ),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                SessionUsageState(
                    inputTokens = 12,
                    outputTokens = 34,
                    cacheCreationTokens = 56,
                    cacheReadTokens = 78,
                    totalCostUsd = 0.12,
                    contextWindow = 200_000,
                    model = "claude-sonnet-4-6[1m]",
                ),
                viewModel.uiState.value.usage,
            )
        }

    @Test
    fun `session_started event updates uiState usage model`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(model = null))
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.usage.model)

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "session_started",
                    payload =
                        payload(
                            "provider_session_id" to "provider-1",
                            "model" to "claude-opus-4-6[1m]",
                        ),
                ),
            )
            advanceUntilIdle()

            assertEquals("claude-opus-4-6[1m]", viewModel.uiState.value.usage.model)
        }

    @Test
    fun `session_usage without context window keeps total window unknown`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "session_usage",
                    payload =
                        payload(
                            "input_tokens" to 12,
                            "output_tokens" to 34,
                            "model" to "claude-sonnet-4-6",
                        ),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                SessionUsageState(
                    inputTokens = 12,
                    outputTokens = 34,
                    contextWindow = 0,
                    model = "claude-sonnet-4-6",
                ),
                viewModel.uiState.value.usage,
            )
        }

    @Test
    fun `session_usage event also persists updated session summary into local cache`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val ws = FakeRelayWsClient()
            val sessionDao = FakeSessionDao()
            val settingsRepository = FakeSettingsRepository()
            val sessionRepository =
                SessionRepository(
                    database = FakeAppDatabase(sessionDao),
                    sessionDao = sessionDao,
                    relayHttpClient = relay,
                    settingsRepository = settingsRepository,
                )
            val viewModel =
                DetailViewModel(
                    relayHttpClient = relay,
                    sessionRepository = sessionRepository,
                    relayWsClient = ws,
                    settingsRepository = settingsRepository,
                    savedStateHandle = SavedStateHandle(mapOf("sessionId" to TEST_SESSION.id)),
                )
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "session_usage",
                    payload =
                        payload(
                            "input_tokens" to 42_000,
                            "output_tokens" to 9_000,
                            "context_window" to 200_000,
                            "model" to "glm-5",
                        ),
                    timestamp = "2026-04-04T10:02:00Z",
                ),
            )
            advanceUntilIdle()

            val cached = sessionDao.storedSession(TEST_SESSION.id)
            assertNotNull(cached)
            assertEquals("glm-5", cached?.model)
            assertEquals(42_000, cached?.inputTokens)
            assertEquals(9_000, cached?.outputTokens)
            assertEquals(200_000, cached?.contextWindow)
            assertEquals("2026-04-04T10:02:00Z", cached?.lastActiveAt)
        }

    @Test
    fun `loadSession resets usage to default values`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "session_usage",
                    payload =
                        payload(
                            "input_tokens" to 12,
                            "output_tokens" to 34,
                            "context_window" to 200_000,
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.loadSession()
            advanceUntilIdle()

            assertEquals(
                SessionUsageState(
                    contextWindow = 0,
                    model = TEST_SESSION.model,
                ),
                viewModel.uiState.value.usage,
            )
        }

    @Test
    fun `sendMessage adds optimistic message and removes it on failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "idle", initialPrompt = null))
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
    fun `submitToolAnswer calls relay API while session is running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(initialPrompt = null))
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "tool-1",
                            "tool_name" to "AskUserQuestion",
                            "args" to """{"question":"选哪个?","options":["A","B"]}""",
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.submitToolAnswer("A")
            advanceUntilIdle()

            assertEquals(0, relay.sendMessageCalls)
            assertEquals(1, relay.answerInteractiveToolCalls)
            assertEquals(listOf(InteractiveToolAnswerRequest(TEST_SESSION.id, "tool-1", "A", 0)), relay.interactiveToolAnswers)
            assertEquals(1, viewModel.uiState.value.messages.size)
            assertTrue(viewModel.uiState.value.messages.single() is MessageItem.InteractiveToolCall)
        }

    @Test
    fun `submitToolAnswer ignores non latest pending interactive card`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "tool-1",
                            "tool_name" to "AskUserQuestion",
                            "args" to """{"question":"旧问题?"}""",
                        ),
                ),
            )
            ws.emitEvent(
                event(
                    seq = 2,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "tool-2",
                            "tool_name" to "AskUserQuestion",
                            "args" to """{"question":"新问题?"}""",
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.submitToolAnswer("tool-1", "A")
            advanceUntilIdle()

            assertEquals(0, relay.answerInteractiveToolCalls)

            viewModel.submitToolAnswer("tool-2", "B")
            advanceUntilIdle()

            assertEquals(1, relay.answerInteractiveToolCalls)
            assertEquals(listOf(InteractiveToolAnswerRequest(TEST_SESSION.id, "tool-2", "B", 0)), relay.interactiveToolAnswers)
        }

    @Test
    fun `submitToolAnswer catches up relay events when websocket misses final updates`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(initialPrompt = null))
                    getSessionEventsHandler =
                        { _, _, _, sinceSeq, _ ->
                            when (sinceSeq) {
                                0 -> Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                                1 ->
                                    Result.success(
                                        RelayEventPage(
                                            events =
                                                listOf(
                                                    event(
                                                        seq = 2,
                                                        eventType = "tool_call_completed",
                                                        payload =
                                                            payload(
                                                                "call_id" to "tool-1",
                                                                "tool_name" to "AskUserQuestion",
                                                                "result" to "User has answered your questions: \"0\"=\"Beta\".",
                                                            ),
                                                    ),
                                                    event(
                                                        seq = 3,
                                                        eventType = "assistant_delta",
                                                        payload = payload("text" to "FINAL_ANSWER:Beta"),
                                                    ),
                                                    event(seq = 4, eventType = "session_idle"),
                                                    event(
                                                        seq = 5,
                                                        eventType = "session_status_changed",
                                                        payload = payload("status" to "idle"),
                                                    ),
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

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "tool-1",
                            "tool_name" to "AskUserQuestion",
                            "args" to """{"question":"选哪个?","options":["A","B"]}""",
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.submitToolAnswer("Beta")
            advanceUntilIdle()

            assertEquals(listOf(0, 1), relay.getSessionEventRequests.map(SessionEventsRequest::sinceSeq))
            assertEquals("idle", viewModel.uiState.value.session?.status)
            assertTrue(viewModel.uiState.value.canSend)

            val messages = viewModel.uiState.value.messages
            val interactive = messages.filterIsInstance<MessageItem.InteractiveToolCall>().single()
            assertTrue(interactive.isAnswered)
            assertEquals("Beta", interactive.answer)
            assertAgentMessage(messages.last(), "FINAL_ANSWER:Beta", isStreaming = true)
        }

    @Test
    fun `loadSession drops superseded terminal status bubbles after a later resumed run`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "idle", initialPrompt = null))
                    getSessionEventsHandler =
                        { _, _, _, sinceSeq, _ ->
                            when (sinceSeq) {
                                0 ->
                                    Result.success(
                                        RelayEventPage(
                                            events =
                                                listOf(
                                                    event(
                                                        seq = 1,
                                                        eventType = "session_error",
                                                        payload =
                                                            payload("message" to "Companion shutting down; session process will be lost"),
                                                    ),
                                                    event(
                                                        seq = 2,
                                                        eventType = "session_status_changed",
                                                        payload = payload("status" to "failed"),
                                                    ),
                                                    event(
                                                        seq = 3,
                                                        eventType = "session_result",
                                                        payload = payload("status" to "failed"),
                                                    ),
                                                    event(seq = 4, eventType = "session_started"),
                                                    event(
                                                        seq = 5,
                                                        eventType = "session_status_changed",
                                                        payload = payload("status" to "running"),
                                                    ),
                                                    event(seq = 6, eventType = "user_message", payload = payload("text" to "继续")),
                                                    event(
                                                        seq = 7,
                                                        eventType = "assistant_message",
                                                        payload = payload("text" to "新的回复"),
                                                    ),
                                                    event(seq = 8, eventType = "session_idle"),
                                                    event(
                                                        seq = 9,
                                                        eventType = "session_status_changed",
                                                        payload = payload("status" to "idle"),
                                                    ),
                                                ),
                                            hasMore = false,
                                        ),
                                    )

                                else -> Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                            }
                        }
                }

            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            assertEquals("idle", viewModel.uiState.value.effectiveStatus)
            assertTrue(viewModel.uiState.value.canSend)
            assertEquals(2, viewModel.uiState.value.messages.size)
            assertUserMessage(viewModel.uiState.value.messages[0], "继续")
            assertAgentMessage(viewModel.uiState.value.messages[1], "新的回复", isStreaming = false)
            assertTrue(viewModel.uiState.value.messages.none { it is MessageItem.StatusChange })
        }

    @Test
    fun `submitToolAnswer marks interactive card resolved once session reaches idle even without tool completion event`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionHandler =
                        { _, _, _ ->
                            Result.success(
                                if (getSessionCalls >= 2) {
                                    TEST_SESSION.copy(status = "idle")
                                } else {
                                    TEST_SESSION
                                },
                            )
                        }
                    getSessionEventsHandler =
                        { _, _, _, sinceSeq, _ ->
                            when (sinceSeq) {
                                0 -> Result.success(RelayEventPage(events = emptyList(), hasMore = false))
                                1 ->
                                    Result.success(
                                        RelayEventPage(
                                            events =
                                                listOf(
                                                    event(seq = 2, eventType = "session_idle"),
                                                    event(
                                                        seq = 3,
                                                        eventType = "session_status_changed",
                                                        payload = payload("status" to "idle"),
                                                    ),
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

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "tool-1",
                            "tool_name" to "AskUserQuestion",
                            "args" to """{"question":"选哪个?","options":["Alpha","Beta"]}""",
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.submitToolAnswer("Beta")
            advanceUntilIdle()

            val interactive = viewModel.uiState.value.messages.filterIsInstance<MessageItem.InteractiveToolCall>().single()
            assertEquals("idle", viewModel.uiState.value.effectiveStatus)
            assertTrue(viewModel.uiState.value.canSend)
            assertTrue(resolvedInteractiveToolCall(interactive, viewModel.uiState.value.effectiveStatus).isAnswered)
            assertEquals("Beta", interactive.answer)
        }

    @Test
    fun `submitToolAnswer failure sets errorMessage on card and reverts answer`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(initialPrompt = null))
                    answerInteractiveToolHandler = { _, _, _, _, _, _ -> Result.failure(RuntimeException("网络超时")) }
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "tool-1",
                            "tool_name" to "AskUserQuestion",
                            "args" to """{"question":"你好?"}""",
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.submitToolAnswer("回答")
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            val interactive = messages.filterIsInstance<MessageItem.InteractiveToolCall>().first()
            assertFalse(interactive.isAnswered)
            assertNull(interactive.answer)
            assertEquals("发送失败，点击重试", interactive.errorMessage)
            assertFalse(viewModel.uiState.value.isSending)
            assertEquals(1, messages.size)
            assertEquals(1, relay.answerInteractiveToolCalls)
        }

    @Test
    fun `approveToolCall failure shows error state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    sendMessageHandler = { _, _, _, _ -> Result.failure(RuntimeException("连接断开")) }
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "approval_required",
                    payload =
                        payload(
                            "call_id" to "appr-1",
                            "tool_name" to "bash",
                            "description" to "rm -rf /tmp",
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.approveToolCall("appr-1")
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isSending)
        }

    @Test
    fun `approveToolCall ignores non latest pending approval card`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay = FakeRelayHttpClient()
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "approval_required",
                    payload =
                        payload(
                            "call_id" to "approval-1",
                            "tool_name" to "bash",
                            "description" to "old",
                        ),
                ),
            )
            ws.emitEvent(
                event(
                    seq = 2,
                    eventType = "approval_required",
                    payload =
                        payload(
                            "call_id" to "approval-2",
                            "tool_name" to "bash",
                            "description" to "new",
                        ),
                ),
            )
            advanceUntilIdle()

            viewModel.approveToolCall("approval-1")
            advanceUntilIdle()

            assertEquals(0, relay.sendMessageCalls)

            viewModel.approveToolCall("approval-2")
            advanceUntilIdle()

            assertEquals(1, relay.sendMessageCalls)
            assertEquals(listOf("approve"), relay.sentMessages)
        }

    @Test
    fun `onSlashTrigger sets showSlashSheet`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSlashTrigger()

            assertTrue(viewModel.uiState.value.showSlashSheet)
        }

    @Test
    fun `onSkillSelected sets commandChip and clears showSlashSheet`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val skill = DEFAULT_SKILLS.first { it.command == "commit" }

            viewModel.onSlashTrigger()
            viewModel.onSkillSelected(skill)

            assertEquals(skill, viewModel.uiState.value.commandChip)
            assertFalse(viewModel.uiState.value.showSlashSheet)
        }

    @Test
    fun `onDismissCommand clears commandChip`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSkillSelected(DEFAULT_SKILLS.first { it.command == "commit" })
            viewModel.onDismissCommand()

            assertNull(viewModel.uiState.value.commandChip)
        }

    @Test
    fun `command chip send assembles slash command`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "idle"))
                }
            val viewModel = createViewModel(relay = relay)
            advanceUntilIdle()

            viewModel.onSkillSelected(DEFAULT_SKILLS.first { it.command == "commit" })
            viewModel.sendMessage("fix typo")
            advanceUntilIdle()

            assertEquals(1, relay.sendMessageCalls)
            assertEquals(listOf("/commit fix typo"), relay.sentMessages)
            assertNull(viewModel.uiState.value.commandChip)
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
    fun `session_started is silent but session_result shows terminal status`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "queued", initialPrompt = null))
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
            advanceUntilIdle()

            assertEquals("queued", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)

            ws.emitEvent(event(seq = 1, eventType = "session_started"))
            advanceUntilIdle()

            // session_started updates session status but does NOT create a status bubble
            assertEquals("running", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
            assertTrue(viewModel.uiState.value.messages.isEmpty())

            ws.emitEvent(
                event(
                    seq = 2,
                    eventType = "session_result",
                    payload = payload("status" to "completed", "message" to "任务完成"),
                ),
            )
            advanceUntilIdle()

            // session_result is a terminal state → shows status bubble
            val messages = viewModel.uiState.value.messages
            assertEquals(1, messages.size)
            assertStatusChange(messages[0], status = "completed", message = "任务完成")
            assertEquals("completed", viewModel.uiState.value.session?.status)
            assertFalse(viewModel.uiState.value.canSend)
        }

    @Test
    fun `approval events render as generic status messages while session stays running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(initialPrompt = null))
                }
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(relay = relay, ws = ws)
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
    fun `historical recovered session error does not resurface as current error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val relay =
                FakeRelayHttpClient().apply {
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "idle", errorMessage = null, initialPrompt = null))
                    getSessionEventsResult =
                        Result.success(
                            RelayEventPage(
                                events =
                                    listOf(
                                        event(
                                            seq = 1,
                                            eventType = "session_error",
                                            payload =
                                                payload(
                                                    "message" to "Companion shutting down; session process will be lost",
                                                    "error_code" to "companion_restart",
                                                ),
                                        ),
                                        event(
                                            seq = 2,
                                            eventType = "session_started",
                                            payload = payload("provider_session_id" to "provider-1", "model" to "sonnet"),
                                        ),
                                        event(
                                            seq = 3,
                                            eventType = "session_status_changed",
                                            payload = payload("status" to "running"),
                                        ),
                                        event(
                                            seq = 4,
                                            eventType = "session_idle",
                                            payload = payload("result" to JSONObject.NULL),
                                        ),
                                        event(
                                            seq = 5,
                                            eventType = "session_status_changed",
                                            payload = payload("status" to "idle"),
                                        ),
                                    ),
                                hasMore = false,
                            ),
                        )
                }

            val viewModel = createViewModel(relay = relay, ws = FakeRelayWsClient())
            advanceUntilIdle()

            assertEquals("idle", viewModel.uiState.value.session?.status)
            assertNull(viewModel.uiState.value.session?.errorMessage)
            assertNull(viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.canSend)
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
                    getSessionResult = Result.success(TEST_SESSION.copy(initialPrompt = null))
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
                    getSessionResult = Result.success(TEST_SESSION.copy(initialPrompt = null))
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
                    getSessionResult = Result.success(TEST_SESSION.copy(status = "idle", initialPrompt = null))
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

    @Test
    fun `onEnterSelectionMode switches to new message id directly`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEnterSelectionMode("msg-123")
            assertEquals("msg-123", viewModel.uiState.value.selectionModeMessageId)

            viewModel.onEnterSelectionMode("msg-456")
            assertEquals("msg-456", viewModel.uiState.value.selectionModeMessageId)
        }

    @Test
    fun `assistant delta does not clear selectionMode when message count unchanged`() =
        runTest(mainDispatcherRule.dispatcher) {
            val ws = FakeRelayWsClient()
            val viewModel = createViewModel(ws = ws)
            advanceUntilIdle()

            // First delta creates the agent message
            ws.emitEvent(
                event(
                    seq = 1,
                    eventType = "assistant_delta",
                    payload = payload("content" to "hello"),
                ),
            )
            advanceUntilIdle()

            viewModel.onEnterSelectionMode("agent-1")

            // Second delta updates the same message (no new message added)
            ws.emitEvent(
                event(
                    seq = 2,
                    eventType = "assistant_delta",
                    payload = payload("content" to " world"),
                ),
            )
            advanceUntilIdle()

            assertEquals("agent-1", viewModel.uiState.value.selectionModeMessageId)
        }

    private fun createViewModel(
        relay: FakeRelayHttpClient = FakeRelayHttpClient(),
        ws: FakeRelayWsClient = FakeRelayWsClient(),
    ): DetailViewModel {
        val settingsRepository = FakeSettingsRepository()
        val sessionDao = FakeSessionDao()
        val sessionRepository =
            SessionRepository(
                database = FakeAppDatabase(sessionDao),
                sessionDao = sessionDao,
                relayHttpClient = relay,
                settingsRepository = settingsRepository,
            )
        return DetailViewModel(
            relayHttpClient = relay,
            sessionRepository = sessionRepository,
            relayWsClient = ws,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to TEST_SESSION.id)),
        )
    }
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

private data class InteractiveToolAnswerRequest(
    val sessionId: String,
    val callId: String,
    val answer: String,
    val questionIndex: Int,
)

private class FakeRelayHttpClient : RelayHttpClient(OkHttpClient()) {
    var getSessionResult: Result<RelaySession> = Result.success(TEST_SESSION)
    var getSessionEventsResult: Result<RelayEventPage> = Result.success(RelayEventPage(events = emptyList(), hasMore = false))
    var sendMessageResult: Result<Unit> = Result.success(Unit)
    var answerInteractiveToolResult: Result<Unit> = Result.success(Unit)
    var resumeSessionResult: Result<RelaySession> = Result.success(TEST_SESSION.copy(status = "running"))
    var cancelSessionResult: Result<RelaySession> = Result.success(TEST_SESSION.copy(status = "cancelled"))
    var deleteSessionResult: Result<Unit> = Result.success(Unit)

    var getSessionCalls = 0
    var getSessionEventsCalls = 0
    var sendMessageCalls = 0
    var answerInteractiveToolCalls = 0
    var resumeSessionCalls = 0
    var cancelSessionCalls = 0
    var deleteSessionCalls = 0
    val sentMessages = mutableListOf<String>()
    val interactiveToolAnswers = mutableListOf<InteractiveToolAnswerRequest>()

    var getSessionHandler: suspend (String, String, String) -> Result<RelaySession> = { _, _, _ -> getSessionResult }
    var getSessionEventsHandler: suspend (String, String, String, Int, Int) -> Result<RelayEventPage> =
        { _, _, _, _, _ -> getSessionEventsResult }
    var sendMessageHandler: suspend (String, String, String, String) -> Result<Unit> = { _, _, _, _ -> sendMessageResult }
    var answerInteractiveToolHandler: suspend (String, String, String, String, String, Int) -> Result<Unit> =
        { _, _, _, _, _, _ -> answerInteractiveToolResult }
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
        sentMessages += text
        return sendMessageHandler(relayUrl, token, sessionId, text)
    }

    override suspend fun answerInteractiveTool(
        relayUrl: String,
        token: String,
        sessionId: String,
        callId: String,
        answer: String,
        questionIndex: Int,
    ): Result<Unit> {
        answerInteractiveToolCalls++
        interactiveToolAnswers +=
            InteractiveToolAnswerRequest(
                sessionId = sessionId,
                callId = callId,
                answer = answer,
                questionIndex = questionIndex,
            )
        return answerInteractiveToolHandler(relayUrl, token, sessionId, callId, answer, questionIndex)
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

private class FakeSessionDao : SessionDao {
    private val sessions = linkedMapOf<String, SessionEntity>()
    private val allSessionsFlow = MutableStateFlow(emptyList<SessionEntity>())

    override suspend fun insertAll(sessions: List<SessionEntity>) {
        sessions.forEach { session ->
            this.sessions[session.id] = session
        }
        publish()
    }

    override fun getAll() = allSessionsFlow

    override suspend fun getPage(
        offset: Int,
        limit: Int,
    ): List<SessionEntity> = allSessionsFlow.value.drop(offset).take(limit)

    override fun getByPathPrefix(
        prefix: String,
        escapedPrefix: String,
    ) = MutableStateFlow(allSessionsFlow.value.filter { it.workspaceCwd == prefix || it.workspaceCwd.startsWith("$prefix/") })

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

    fun storedSession(id: String): SessionEntity? = sessions[id]

    private fun publish() {
        allSessionsFlow.value = sessions.values.toList()
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
