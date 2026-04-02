@file:Suppress("MaxLineLength")

package com.imbot.android.ui.detail

import com.imbot.android.network.ServerMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LargeClass")
class EventProcessorTest {
    private var nextId = 0
    private val processor = EventProcessor { "id-${++nextId}" }

    @Test
    fun `user_message event generates user message`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "user_message",
                    payload = payload("text" to "你好"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.UserMessage(
                    id = "id-1",
                    text = "你好",
                    timestamp = TIMESTAMP,
                    seq = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun `single assistant delta creates streaming agent message`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "assistant_delta",
                    payload = payload("text" to "正在分析"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.AgentMessage(
                    id = "id-1",
                    content = "正在分析",
                    isStreaming = true,
                    timestamp = TIMESTAMP,
                    seq = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun `consecutive assistant deltas are aggregated`() {
        processor.process(event(seq = 1, eventType = "assistant_delta", payload = payload("text" to "正在")))

        val result =
            processor.process(
                event(
                    seq = 2,
                    eventType = "assistant_delta",
                    payload = payload("text" to "分析"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.AgentMessage(
                    id = "id-1",
                    content = "正在分析",
                    isStreaming = true,
                    timestamp = TIMESTAMP,
                    seq = 2,
                ),
            ),
            result,
        )
    }

    @Test
    fun `assistant_message finalizes previous streaming message`() {
        processor.process(event(seq = 1, eventType = "assistant_delta", payload = payload("text" to "草稿")))

        val result =
            processor.process(
                event(
                    seq = 2,
                    eventType = "assistant_message",
                    payload = payload("text" to "完整回复"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.AgentMessage(
                    id = "id-1",
                    content = "完整回复",
                    isStreaming = false,
                    timestamp = TIMESTAMP,
                    seq = 2,
                ),
            ),
            result,
        )
    }

    @Test
    fun `assistant delta after finalized message starts a new turn`() {
        processor.process(event(seq = 1, eventType = "assistant_delta", payload = payload("text" to "第一段")))
        processor.process(event(seq = 2, eventType = "assistant_message", payload = payload("text" to "第一段完成")))

        val result =
            processor.process(
                event(
                    seq = 3,
                    eventType = "assistant_delta",
                    payload = payload("text" to "第二段"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.AgentMessage(
                    id = "id-1",
                    content = "第一段完成",
                    isStreaming = false,
                    timestamp = TIMESTAMP,
                    seq = 2,
                ),
                MessageItem.AgentMessage(
                    id = "id-2",
                    content = "第二段",
                    isStreaming = true,
                    timestamp = TIMESTAMP,
                    seq = 3,
                ),
            ),
            result,
        )
    }

    @Test
    fun `tool_call_started appends running tool card`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "call-1",
                            "tool_name" to "grep",
                            "title" to "搜索仓库",
                            "args" to JSONObject("""{"pattern":"TODO"}"""),
                        ),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.ToolCall(
                    callId = "call-1",
                    toolName = "grep",
                    title = "搜索仓库",
                    args = JSONObject("""{"pattern":"TODO"}""").toString(2),
                    result = null,
                    isRunning = true,
                    seq = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun `AskUserQuestion tool call renders interactive tool card`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "call-ask-1",
                            "tool_name" to "AskUserQuestion",
                            "args" to """{"question":"选哪个?","options":["A","B"]}""",
                        ),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.InteractiveToolCall(
                    id = "call-ask-1",
                    toolName = "AskUserQuestion",
                    questions =
                        listOf(
                            ParsedQuestion(
                                question = "选哪个?",
                                header = null,
                                options = listOf(ParsedOption("A", null), ParsedOption("B", null)),
                                multiSelect = false,
                            ),
                        ),
                    timestamp = TIMESTAMP,
                    seq = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun `AskUserQuestion nested tool call parses questions array`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload =
                        payload(
                            "call_id" to "call-ask-2",
                            "tool_name" to "AskUserQuestion",
                            "args" to
                                """
                                {"questions":[{"question":"Which library?","header":"Library","options":[{"label":"Option A","description":"Fast but limited"},{"label":"Option B","description":"Full-featured"}],"multiSelect":true}]}
                                """.trimIndent(),
                        ),
                ),
            )

        assertEquals(1, result.size)
        val item = result.single() as MessageItem.InteractiveToolCall
        assertEquals("Which library?", item.primaryQuestion.question)
        assertEquals("Library", item.primaryQuestion.header)
        assertTrue(item.primaryQuestion.multiSelect)
        assertEquals(
            listOf(
                ParsedOption("Option A", "Fast but limited"),
                ParsedOption("Option B", "Full-featured"),
            ),
            item.primaryQuestion.options,
        )
    }

    @Test
    fun `tool_call_completed updates matching tool card`() {
        processor.process(
            event(
                seq = 1,
                eventType = "tool_call_started",
                payload = payload("call_id" to "call-1", "tool_name" to "grep", "title" to "搜索"),
            ),
        )

        val result =
            processor.process(
                event(
                    seq = 2,
                    eventType = "tool_call_completed",
                    payload = payload("call_id" to "call-1", "result" to "找到 3 条"),
                ),
            )

        assertEquals(
            MessageItem.ToolCall(
                callId = "call-1",
                toolName = "grep",
                title = "搜索",
                args = null,
                result = "找到 3 条",
                isRunning = false,
                seq = 2,
            ),
            result.single(),
        )
    }

    @Test
    fun `tool_call_completed marks null and error results as failed`() {
        processor.process(
            event(
                seq = 1,
                eventType = "tool_call_started",
                payload = payload("call_id" to "call-1", "tool_name" to "bash", "title" to "执行命令"),
            ),
        )

        val nullResult =
            processor.process(
                event(
                    seq = 2,
                    eventType = "tool_call_completed",
                    payload = payload("call_id" to "call-1"),
                ),
            )

        assertTrue((nullResult.single() as MessageItem.ToolCall).isError)

        val secondProcessor = EventProcessor { "id-${++nextId}" }
        secondProcessor.process(
            event(
                seq = 1,
                eventType = "tool_call_started",
                payload = payload("call_id" to "call-2", "tool_name" to "bash", "title" to "执行命令"),
            ),
        )

        val errorResult =
            secondProcessor.process(
                event(
                    seq = 2,
                    eventType = "tool_call_completed",
                    payload = payload("call_id" to "call-2", "result" to "Error: command failed"),
                ),
            )

        assertTrue((errorResult.single() as MessageItem.ToolCall).isError)
    }

    @Test
    fun `interactive tool completion marks card answered`() {
        processor.process(
            event(
                seq = 1,
                eventType = "tool_call_started",
                payload =
                    payload(
                        "call_id" to "call-ask-1",
                        "tool_name" to "AskUserQuestion",
                        "args" to """{"question":"选哪个?"}""",
                    ),
            ),
        )
        processor.recordInteractiveToolAnswer("call-ask-1", "A")

        val result =
            processor.process(
                event(
                    seq = 2,
                    eventType = "tool_call_completed",
                    payload =
                        payload(
                            "call_id" to "call-ask-1",
                            "tool_name" to "AskUserQuestion",
                            "result" to "done",
                        ),
                ),
            )

        assertEquals(
            MessageItem.InteractiveToolCall(
                id = "call-ask-1",
                toolName = "AskUserQuestion",
                questions =
                    listOf(
                        ParsedQuestion(
                            question = "选哪个?",
                            header = null,
                            options = null,
                            multiSelect = false,
                        ),
                    ),
                isAnswered = true,
                answer = "A",
                timestamp = TIMESTAMP,
                seq = 2,
            ),
            result.single(),
        )
    }

    @Test
    fun `mixed event sequence produces stable message timeline`() {
        processor.process(event(seq = 1, eventType = "user_message", payload = payload("text" to "帮我检查日志")))
        processor.process(event(seq = 2, eventType = "assistant_delta", payload = payload("text" to "先看一下")))
        processor.process(
            event(
                seq = 3,
                eventType = "tool_call_started",
                payload = payload("call_id" to "tool-1", "tool_name" to "tail", "title" to "读取日志"),
            ),
        )
        processor.process(
            event(
                seq = 4,
                eventType = "tool_call_completed",
                payload = payload("call_id" to "tool-1", "result" to "读取完成"),
            ),
        )
        processor.process(event(seq = 5, eventType = "assistant_delta", payload = payload("text" to "问题已经定位")))

        val result =
            processor.process(
                event(
                    seq = 6,
                    eventType = "assistant_message",
                    payload = payload("text" to "问题已经定位，建议重启服务"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.UserMessage("id-1", "帮我检查日志", TIMESTAMP, seq = 1),
                MessageItem.AgentMessage("id-2", "先看一下", false, TIMESTAMP, seq = 2),
                MessageItem.ToolCall(
                    callId = "tool-1",
                    toolName = "tail",
                    title = "读取日志",
                    args = null,
                    result = "读取完成",
                    isRunning = false,
                    seq = 4,
                ),
                MessageItem.AgentMessage("id-3", "问题已经定位，建议重启服务", false, TIMESTAMP, seq = 6),
            ),
            result,
        )
    }

    @Test
    fun `session_status_changed appends status change`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "session_status_changed",
                    payload = payload("status" to "completed"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.StatusChange(
                    id = "id-1",
                    status = "completed",
                    message = null,
                    seq = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun `session_started is silent and produces no status bubble`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "session_started",
                ),
            )

        assertEquals(emptyList<MessageItem>(), result)
    }

    @Test
    fun `session_idle is silent and produces no status bubble`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "session_idle",
                ),
            )

        assertEquals(emptyList<MessageItem>(), result)
    }

    @Test
    fun `session_status_changed to running is silent`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "session_status_changed",
                    payload = payload("status" to "running"),
                ),
            )

        assertEquals(emptyList<MessageItem>(), result)
    }

    @Test
    fun `session_status_changed with error message is shown`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "session_status_changed",
                    payload =
                        payload(
                            "status" to "running",
                            "message" to "Host companion disconnected unexpectedly",
                        ),
                ),
            )

        assertEquals(1, result.size)
        val status = result[0] as MessageItem.StatusChange
        assertEquals("running", status.status)
        assertEquals("Host companion disconnected unexpectedly", status.message)
    }

    @Test
    fun `session_result appends status change from payload`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "session_result",
                    payload = payload("status" to "completed", "message" to "任务结束"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.StatusChange(
                    id = "id-1",
                    status = "completed",
                    message = "任务结束",
                    seq = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun `session_error appends failed status change`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "session_error",
                    payload = payload("message" to "provider timeout"),
                ),
            )

        assertEquals(
            listOf(
                MessageItem.StatusChange(
                    id = "id-1",
                    status = "failed",
                    message = "provider timeout",
                    seq = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun `approval_resolved updates existing approval card in place`() {
        val required =
            processor.process(
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
        val resolved =
            processor.process(
                event(
                    seq = 2,
                    eventType = "approval_resolved",
                    payload =
                        payload(
                            "call_id" to "call-1",
                            "tool_name" to "bash",
                            "description" to "Run a shell command",
                            "approved" to true,
                        ),
                ),
            )

        assertEquals(
            MessageItem.StatusChange(
                id = "id-1",
                status = "running",
                message = "Approval required: Run a shell command",
                eventType = "approval_required",
                callId = "call-1",
                toolName = "bash",
                description = "Run a shell command",
                seq = 1,
            ),
            required.single(),
        )
        assertEquals(
            MessageItem.StatusChange(
                id = "id-1",
                status = "running",
                message = "Approval resolved: Run a shell command",
                eventType = "approval_resolved",
                callId = "call-1",
                toolName = "bash",
                description = "Run a shell command",
                approvalDecision = "approved",
                seq = 2,
            ),
            resolved.single(),
        )
    }

    @Test
    fun `empty assistant delta is ignored`() {
        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "assistant_delta",
                    payload = payload("text" to ""),
                ),
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `duplicate seq numbers are idempotent`() {
        val first =
            processor.process(
                event(
                    seq = 1,
                    eventType = "user_message",
                    payload = payload("text" to "重复测试"),
                ),
            )

        val second =
            processor.process(
                event(
                    seq = 1,
                    eventType = "user_message",
                    payload = payload("text" to "重复测试"),
                ),
            )

        assertEquals(first, second)
        assertEquals(1, second.size)
    }

    @Test
    fun `assistant message keeps stable id across streaming updates`() {
        val first =
            processor.process(
                event(
                    seq = 1,
                    eventType = "assistant_delta",
                    payload = payload("text" to "第一段"),
                ),
            ).single() as MessageItem.AgentMessage

        val second =
            processor.process(
                event(
                    seq = 2,
                    eventType = "assistant_delta",
                    payload = payload("text" to "第二段"),
                ),
            ).single() as MessageItem.AgentMessage

        val final =
            processor.process(
                event(
                    seq = 3,
                    eventType = "assistant_message",
                    payload = payload("text" to "完整回复"),
                ),
            ).single() as MessageItem.AgentMessage

        assertEquals(first.id, second.id)
        assertEquals(second.id, final.id)
        assertEquals("完整回复", final.content)
        assertFalse(final.isStreaming)
    }

    @Test
    fun `tool payloads are truncated to bounded length`() {
        val longPayload = "a".repeat(10_100)

        val result =
            processor.process(
                event(
                    seq = 1,
                    eventType = "tool_call_started",
                    payload = payload("call_id" to "call-1", "args" to longPayload),
                ),
            )

        val toolCall = result.single() as MessageItem.ToolCall
        assertNotNull(toolCall.args)
        assertTrue(toolCall.args!!.endsWith("…(已截断)"))
        assertEquals(10_006, toolCall.args!!.length)
    }

    @Test
    fun `processor keeps only the most recent 500 timeline items`() {
        repeat(MAX_MESSAGE_ITEMS + 1) { index ->
            processor.process(
                event(
                    seq = index + 1,
                    eventType = "user_message",
                    payload = payload("text" to "消息 ${index + 1}"),
                ),
            )
        }

        val snapshot = processor.snapshot()
        assertEquals(MAX_MESSAGE_ITEMS, snapshot.size)
        assertEquals("消息 2", (snapshot.first() as MessageItem.UserMessage).text)
        assertEquals("消息 501", (snapshot.last() as MessageItem.UserMessage).text)
    }

    @Test
    fun `new assistant turns receive new stable ids`() {
        processor.process(event(seq = 1, eventType = "assistant_delta", payload = payload("text" to "第一条")))
        processor.process(event(seq = 2, eventType = "assistant_message", payload = payload("text" to "第一条完成")))

        val secondTurn =
            processor.process(
                event(
                    seq = 3,
                    eventType = "assistant_delta",
                    payload = payload("text" to "第二条"),
                ),
            ).last() as MessageItem.AgentMessage

        assertEquals("id-2", secondTurn.id)
        assertNotEquals("id-1", secondTurn.id)
    }
}

private const val SESSION_ID = "session-123"
private const val TIMESTAMP = "2026-03-31T12:00:00Z"

private fun event(
    seq: Int,
    eventType: String,
    payload: JSONObject? = null,
    timestamp: String = TIMESTAMP,
) = ServerMessage.Event(
    sessionId = SESSION_ID,
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
