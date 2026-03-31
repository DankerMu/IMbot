@file:Suppress("MaxLineLength")

package com.imbot.android.ui.detail

import com.imbot.android.network.ServerMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventProcessorTest {
    private val processor = EventProcessor()

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
                    text = "你好",
                    timestamp = TIMESTAMP,
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
                    content = "正在分析",
                    isStreaming = true,
                    timestamp = TIMESTAMP,
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

        assertEquals(1, result.size)
        assertEquals("正在分析", (result.single() as MessageItem.AgentMessage).content)
        assertTrue((result.single() as MessageItem.AgentMessage).isStreaming)
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
                    content = "完整回复",
                    isStreaming = false,
                    timestamp = TIMESTAMP,
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

        assertEquals(2, result.size)
        assertEquals("第一段完成", (result[0] as MessageItem.AgentMessage).content)
        assertEquals("第二段", (result[1] as MessageItem.AgentMessage).content)
        assertTrue((result[1] as MessageItem.AgentMessage).isStreaming)
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
                ),
            ),
            result,
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

        assertEquals(4, result.size)
        assertEquals(MessageItem.UserMessage("帮我检查日志", TIMESTAMP), result[0])
        assertEquals(MessageItem.AgentMessage("先看一下", false, TIMESTAMP), result[1])
        assertEquals(
            MessageItem.ToolCall(
                callId = "tool-1",
                toolName = "tail",
                title = "读取日志",
                args = null,
                result = "读取完成",
                isRunning = false,
            ),
            result[2],
        )
        assertEquals(
            MessageItem.AgentMessage("问题已经定位，建议重启服务", false, TIMESTAMP),
            result[3],
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
                    status = "completed",
                    message = null,
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
                    status = "failed",
                    message = "provider timeout",
                ),
            ),
            result,
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
