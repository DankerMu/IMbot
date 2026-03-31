package com.imbot.android.ui.detail

import com.imbot.android.network.ServerMessage
import org.json.JSONArray
import org.json.JSONObject

sealed class MessageItem {
    data class UserMessage(
        val text: String,
        val timestamp: String,
    ) : MessageItem()

    data class AgentMessage(
        val content: String,
        val isStreaming: Boolean,
        val timestamp: String,
    ) : MessageItem()

    data class ToolCall(
        val callId: String,
        val toolName: String,
        val title: String,
        val args: String?,
        val result: String?,
        val isRunning: Boolean,
    ) : MessageItem()

    data class StatusChange(
        val status: String,
        val message: String?,
    ) : MessageItem()
}

@Suppress("TooManyFunctions")
class EventProcessor {
    private val messages = mutableListOf<MessageItem>()
    private val processedSeqs = mutableSetOf<Int>()
    private var lastProcessedSeq = 0

    fun process(event: ServerMessage.Event): List<MessageItem> {
        if (!processedSeqs.add(event.seq)) {
            return snapshot()
        }
        lastProcessedSeq = maxOf(lastProcessedSeq, event.seq)

        when (event.eventType) {
            "user_message" -> appendUserMessage(event)
            "assistant_delta" -> appendAssistantDelta(event)
            "assistant_message" -> finalizeAssistantMessage(event)
            "tool_call_started" -> appendToolCallStarted(event)
            "tool_call_completed" -> appendToolCallCompleted(event)
            "session_status_changed" -> appendStatusChange(event)
            "session_error" -> appendSessionError(event)
        }

        return snapshot()
    }

    internal fun reset() {
        messages.clear()
        processedSeqs.clear()
        lastProcessedSeq = 0
    }

    internal fun snapshot(): List<MessageItem> = messages.toList()

    internal fun lastProcessedSeq(): Int = lastProcessedSeq

    private fun appendUserMessage(event: ServerMessage.Event) {
        val text = event.payload.stringValue("text").orEmpty().trim()
        if (text.isBlank()) {
            return
        }

        closeStreamingAgentMessage()
        messages +=
            MessageItem.UserMessage(
                text = text,
                timestamp = event.timestamp,
            )
    }

    private fun appendAssistantDelta(event: ServerMessage.Event) {
        val deltaText = event.payload.stringValue("text").orEmpty()
        if (deltaText.isBlank()) {
            return
        }

        val lastIndex = messages.lastIndex
        val lastMessage = messages.lastOrNull()
        if (lastMessage is MessageItem.AgentMessage && lastMessage.isStreaming) {
            messages[lastIndex] =
                lastMessage.copy(
                    content = lastMessage.content + deltaText,
                    timestamp = event.timestamp.ifBlank { lastMessage.timestamp },
                )
            return
        }

        messages +=
            MessageItem.AgentMessage(
                content = deltaText,
                isStreaming = true,
                timestamp = event.timestamp,
            )
    }

    private fun finalizeAssistantMessage(event: ServerMessage.Event) {
        val finalText = event.payload.stringValue("text").orEmpty()
        val lastIndex = messages.lastIndex
        val lastMessage = messages.lastOrNull()

        if (lastMessage is MessageItem.AgentMessage && lastMessage.isStreaming) {
            messages[lastIndex] =
                lastMessage.copy(
                    content = finalText.ifBlank { lastMessage.content },
                    isStreaming = false,
                    timestamp = event.timestamp.ifBlank { lastMessage.timestamp },
                )
            return
        }

        if (finalText.isBlank()) {
            return
        }

        messages +=
            MessageItem.AgentMessage(
                content = finalText,
                isStreaming = false,
                timestamp = event.timestamp,
            )
    }

    private fun appendToolCallStarted(event: ServerMessage.Event) {
        val payload = event.payload ?: return
        val callId = payload.stringValue("call_id").orEmpty()
        if (callId.isBlank()) {
            return
        }

        closeStreamingAgentMessage()
        messages +=
            MessageItem.ToolCall(
                callId = callId,
                toolName = payload.stringValue("tool_name").orEmpty(),
                title = payload.stringValue("title").orEmpty(),
                args = payload.compactValue("args"),
                result = null,
                isRunning = true,
            )
    }

    private fun appendToolCallCompleted(event: ServerMessage.Event) {
        val payload = event.payload
        val callId = payload.stringValue("call_id").orEmpty()
        if (payload == null || callId.isBlank()) {
            return
        }

        val toolCallIndex =
            messages.indexOfLast { item ->
                item is MessageItem.ToolCall && item.callId == callId
            }

        if (toolCallIndex >= 0) {
            val item = messages[toolCallIndex] as MessageItem.ToolCall
            messages[toolCallIndex] =
                item.copy(
                    result = payload.compactValue("result"),
                    isRunning = false,
                )
        } else {
            messages +=
                MessageItem.ToolCall(
                    callId = callId,
                    toolName = payload.stringValue("tool_name").orEmpty(),
                    title = payload.stringValue("title").orEmpty(),
                    args = payload.compactValue("args"),
                    result = payload.compactValue("result"),
                    isRunning = false,
                )
        }
    }

    private fun appendStatusChange(event: ServerMessage.Event) {
        val payload = event.payload ?: return
        val status = payload.stringValue("status").orEmpty()
        if (status.isBlank()) {
            return
        }

        closeStreamingAgentMessage()
        messages +=
            MessageItem.StatusChange(
                status = status,
                message = payload.stringValue("message"),
            )
    }

    private fun appendSessionError(event: ServerMessage.Event) {
        val message = event.payload.stringValue("message")

        closeStreamingAgentMessage()
        messages +=
            MessageItem.StatusChange(
                status = "failed",
                message = message,
            )
    }

    private fun closeStreamingAgentMessage() {
        val lastIndex = messages.lastIndex
        val lastMessage = messages.lastOrNull()
        if (lastMessage is MessageItem.AgentMessage && lastMessage.isStreaming) {
            messages[lastIndex] = lastMessage.copy(isStreaming = false)
        }
    }
}

internal fun JSONObject?.stringValue(key: String): String? {
    val payload = this ?: return null
    val value = payload.opt(key)
    return when (value) {
        null,
        JSONObject.NULL,
        -> null
        is String -> value
        else -> value.toString()
    }?.takeIf { it.isNotBlank() }
}

internal fun JSONObject?.compactValue(key: String): String? {
    val payload = this ?: return null
    val value = payload.opt(key)
    return when (value) {
        null,
        JSONObject.NULL,
        -> null
        is JSONObject -> value.toString(2)
        is JSONArray -> value.toString(2)
        else -> value.toString()
    }?.takeIf { it.isNotBlank() }
}
