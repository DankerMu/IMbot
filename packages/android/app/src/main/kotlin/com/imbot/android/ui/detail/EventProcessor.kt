package com.imbot.android.ui.detail

import com.imbot.android.network.ServerMessage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal const val MAX_MESSAGE_ITEMS = 500
private const val MAX_TOOL_PAYLOAD_LENGTH = 10_000

sealed class MessageItem {
    data class UserMessage(
        val id: String,
        val text: String,
        val timestamp: String,
        val seq: Int? = null,
    ) : MessageItem()

    data class AgentMessage(
        val id: String,
        val content: String,
        val isStreaming: Boolean,
        val timestamp: String,
        val seq: Int? = null,
    ) : MessageItem()

    data class InteractiveToolCall(
        val id: String,
        val toolName: String,
        val questions: List<ParsedQuestion>,
        val isAnswered: Boolean = false,
        val answer: String? = null,
        val errorMessage: String? = null,
        val timestamp: String,
        val seq: Int? = null,
    ) : MessageItem() {
        val primaryQuestion: ParsedQuestion
            get() =
                questions.firstOrNull()
                    ?: ParsedQuestion(
                        question = DEFAULT_ASK_USER_QUESTION_MESSAGE,
                        header = null,
                        options = null,
                        multiSelect = false,
                    )
    }

    data class ToolCall(
        val callId: String,
        val toolName: String,
        val title: String,
        val args: String?,
        val result: String?,
        val isRunning: Boolean,
        val isError: Boolean = false,
        val seq: Int? = null,
    ) : MessageItem()

    data class StatusChange(
        val id: String,
        val status: String,
        val message: String?,
        val eventType: String? = null,
        val callId: String? = null,
        val toolName: String? = null,
        val description: String? = null,
        val approvalDecision: String? = null,
        val seq: Int? = null,
    ) : MessageItem()
}

internal fun generateMessageItemId(): String = UUID.randomUUID().toString()

internal data class EventProcessingResult(
    val messages: List<MessageItem>,
    val usageUpdate: SessionUsageState? = null,
)

@Suppress("TooManyFunctions")
class EventProcessor(
    private val idGenerator: () -> String = ::generateMessageItemId,
) {
    private val messages = mutableListOf<MessageItem>()
    private var maxProcessedSeq = 0

    fun process(event: ServerMessage.Event): List<MessageItem> = processWithMetadata(event).messages

    internal fun processWithMetadata(event: ServerMessage.Event): EventProcessingResult {
        if (event.seq <= maxProcessedSeq) {
            return EventProcessingResult(messages = snapshot())
        }
        maxProcessedSeq = event.seq
        var usageUpdate: SessionUsageState? = null

        when (event.eventType) {
            "user_message" -> appendUserMessage(event)
            "assistant_delta" -> appendAssistantDelta(event)
            "assistant_message" -> finalizeAssistantMessage(event)
            "tool_call_started" -> appendToolCallStarted(event)
            "tool_call_completed" -> appendToolCallCompleted(event)
            "session_status_changed" -> {
                clearSupersededTerminalStatusChangesIfNeeded(event)
                appendStatusChangeIfSignificant(event)
            }
            "session_started", "session_idle" -> clearSupersededTerminalStatusChanges()
            "session_result" ->
                appendStatusChange(
                    status = event.payload.stringValue("status").orEmpty(),
                    message = event.payload.stringValue("message"),
                    seq = event.seq,
                )
            "session_usage" -> usageUpdate = event.payload.toSessionUsageState()
            "approval_required", "approval_resolved" -> appendApprovalEvent(event)
            "session_error" -> appendSessionError(event)
        }

        trimMessagesIfNeeded()
        return EventProcessingResult(
            messages = snapshot(),
            usageUpdate = usageUpdate,
        )
    }

    internal fun reset() {
        messages.clear()
        maxProcessedSeq = 0
    }

    internal fun snapshot(): List<MessageItem> = messages.toList()

    internal fun lastProcessedSeq(): Int = maxProcessedSeq

    internal fun recordInteractiveToolAnswer(
        callId: String,
        answer: String,
    ) {
        val toolCallIndex = findToolCallIndex(callId)
        val item = messages.getOrNull(toolCallIndex) as? MessageItem.InteractiveToolCall ?: return
        messages[toolCallIndex] = item.copy(answer = answer)
    }

    internal fun clearInteractiveToolAnswer(
        callId: String,
        errorMessage: String? = null,
    ) {
        val toolCallIndex = findToolCallIndex(callId)
        val item = messages.getOrNull(toolCallIndex) as? MessageItem.InteractiveToolCall ?: return
        if (!item.isAnswered) {
            messages[toolCallIndex] = item.copy(answer = null, errorMessage = errorMessage)
        }
    }

    internal fun reconcileInteractiveToolCalls(
        sessionStatus: String?,
        fallbackAnswers: Map<String, String> = emptyMap(),
    ): Boolean {
        if (canRespondToInteractiveRequest(sessionStatus)) {
            return false
        }

        var mutated = false
        messages.replaceAll { item ->
            val interactive = item as? MessageItem.InteractiveToolCall ?: return@replaceAll item
            val mergedAnswer = interactive.answer ?: fallbackAnswers[interactive.id]
            val resolved =
                if (
                    interactive.isAnswered ||
                    mergedAnswer.isNullOrBlank()
                ) {
                    interactive
                } else {
                    interactive.copy(
                        isAnswered = true,
                        answer = mergedAnswer,
                        errorMessage = null,
                    )
                }
            if (resolved != interactive) {
                mutated = true
            }
            resolved
        }
        return mutated
    }

    private fun clearSupersededTerminalStatusChangesIfNeeded(event: ServerMessage.Event) {
        val status = event.payload.stringValue("status").orEmpty()
        if (status.isNotBlank() && status !in terminalStatuses) {
            clearSupersededTerminalStatusChanges()
        }
    }

    private fun clearSupersededTerminalStatusChanges() {
        messages.removeAll { item ->
            val statusChange = item as? MessageItem.StatusChange ?: return@removeAll false
            statusChange.eventType == null && statusChange.status in terminalStatuses
        }
    }

    private fun appendUserMessage(event: ServerMessage.Event) {
        val text = event.payload.stringValue("text").orEmpty().trim()
        if (text.isBlank()) {
            return
        }

        closeStreamingAgentMessage()
        messages +=
            MessageItem.UserMessage(
                id = idGenerator(),
                text = text,
                timestamp = event.timestamp,
                seq = event.seq,
            )
    }

    private fun appendAssistantDelta(event: ServerMessage.Event) {
        val deltaText = (event.payload.stringValue("text") ?: event.payload.stringValue("content")).orEmpty()
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
                    seq = event.seq,
                )
            return
        }

        messages +=
            MessageItem.AgentMessage(
                id = idGenerator(),
                content = deltaText,
                isStreaming = true,
                timestamp = event.timestamp,
                seq = event.seq,
            )
    }

    private fun finalizeAssistantMessage(event: ServerMessage.Event) {
        val finalText = (event.payload.stringValue("text") ?: event.payload.stringValue("content")).orEmpty()
        val lastIndex = messages.lastIndex
        val lastMessage = messages.lastOrNull()

        if (lastMessage is MessageItem.AgentMessage && lastMessage.isStreaming) {
            messages[lastIndex] =
                lastMessage.copy(
                    content = finalText.ifBlank { lastMessage.content },
                    isStreaming = false,
                    timestamp = event.timestamp.ifBlank { lastMessage.timestamp },
                    seq = event.seq,
                )
            return
        }

        if (finalText.isBlank()) {
            return
        }

        messages +=
            MessageItem.AgentMessage(
                id = idGenerator(),
                content = finalText,
                isStreaming = false,
                timestamp = event.timestamp,
                seq = event.seq,
            )
    }

    private fun appendToolCallStarted(event: ServerMessage.Event) {
        val payload = event.payload ?: return
        val callId = payload.stringValue("call_id").orEmpty()
        if (callId.isBlank() || hasToolCall(callId)) return
        val toolName = payload.toolName().orEmpty()
        val input = truncatePayload(payload.compactValue("args") ?: payload.compactValue("input"))

        closeStreamingAgentMessage()
        if (isInteractiveToolCall(toolName)) {
            val questions = parseAskUserQuestionV2(input)
            messages +=
                MessageItem.InteractiveToolCall(
                    id = callId,
                    toolName = toolName,
                    questions = questions,
                    timestamp = event.timestamp,
                    seq = event.seq,
                )
        } else {
            messages +=
                MessageItem.ToolCall(
                    callId = callId,
                    toolName = toolName,
                    title = payload.stringValue("title").orEmpty(),
                    args = input,
                    result = null,
                    isRunning = true,
                    isError = false,
                    seq = event.seq,
                )
        }
    }

    private fun appendToolCallCompleted(event: ServerMessage.Event) {
        val payload = event.payload
        val callId = payload.stringValue("call_id").orEmpty()
        if (payload == null || callId.isBlank()) {
            return
        }
        val toolName = payload.toolName().orEmpty()
        val input = truncatePayload(payload.compactValue("args") ?: payload.compactValue("input"))
        val result = truncatePayload(payload.compactValue("result") ?: payload.compactValue("output"))

        val toolCallIndex = findToolCallIndex(callId)

        if (toolCallIndex >= 0) {
            when (val item = messages[toolCallIndex]) {
                is MessageItem.InteractiveToolCall ->
                    messages[toolCallIndex] =
                        item.copy(
                            isAnswered = true,
                            answer = item.answer ?: result,
                            timestamp = event.timestamp.ifBlank { item.timestamp },
                            seq = event.seq,
                        )

                is MessageItem.ToolCall ->
                    messages[toolCallIndex] =
                        item.copy(
                            result = result,
                            isRunning = false,
                            isError = isToolCallError(result, payload),
                            seq = event.seq,
                        )

                else -> Unit
            }
        } else {
            if (isInteractiveToolCall(toolName)) {
                val questions = parseAskUserQuestionV2(input)
                messages +=
                    MessageItem.InteractiveToolCall(
                        id = callId,
                        toolName = toolName,
                        questions = questions,
                        isAnswered = true,
                        answer = result,
                        timestamp = event.timestamp,
                        seq = event.seq,
                    )
            } else {
                messages +=
                    MessageItem.ToolCall(
                        callId = callId,
                        toolName = toolName,
                        title = payload.stringValue("title").orEmpty(),
                        args = input,
                        result = result,
                        isRunning = false,
                        isError = isToolCallError(result, payload),
                        seq = event.seq,
                    )
            }
        }
    }

    private fun appendStatusChangeIfSignificant(event: ServerMessage.Event) {
        val payload = event.payload ?: return
        val status = payload.stringValue("status").orEmpty()
        val message = payload.stringValue("message")
        val isTerminal = status in terminalStatuses
        val hasMessage = !message.isNullOrBlank()
        if (status.isNotBlank() && (isTerminal || hasMessage)) {
            appendStatusChange(
                status = status,
                message = message,
                seq = event.seq,
            )
        }
    }

    private val terminalStatuses = setOf("completed", "failed", "cancelled")

    private fun appendStatusChange(
        status: String,
        message: String?,
        eventType: String? = null,
        callId: String? = null,
        toolName: String? = null,
        description: String? = null,
        approvalDecision: String? = null,
        seq: Int? = null,
    ) {
        if (status.isBlank()) {
            return
        }

        closeStreamingAgentMessage()
        messages +=
            MessageItem.StatusChange(
                status = status,
                id = idGenerator(),
                message = message,
                eventType = eventType,
                callId = callId,
                toolName = toolName,
                description = description,
                approvalDecision = approvalDecision,
                seq = seq,
            )
    }

    private fun appendSessionError(event: ServerMessage.Event) {
        appendStatusChange(
            status = "failed",
            message = event.payload.stringValue("message"),
            seq = event.seq,
        )
    }

    private fun appendApprovalEvent(event: ServerMessage.Event) {
        val payload = event.payload
        val callId = payload.stringValue("call_id")
        val toolName = payload.toolName()
        val description = payload.stringValue("description")
        val approvalDecision = payload.approvalDecision()

        if (
            event.eventType == "approval_resolved" &&
            updateApprovalCard(
                callId = callId,
                toolName = toolName,
                description = description,
                approvalDecision = approvalDecision,
                seq = event.seq,
            )
        ) {
            return
        }

        appendStatusChange(
            status = "running",
            message =
                approvalStatusMessage(
                    eventType = event.eventType,
                    description = description,
                    toolName = toolName,
                ),
            eventType = event.eventType,
            callId = callId,
            toolName = toolName,
            description = description,
            approvalDecision = approvalDecision,
            seq = event.seq,
        )
    }

    private fun updateApprovalCard(
        callId: String?,
        toolName: String?,
        description: String?,
        approvalDecision: String?,
        seq: Int,
    ): Boolean {
        val approvalIndex = findApprovalStatusIndex(callId)
        val item = messages.getOrNull(approvalIndex) as? MessageItem.StatusChange ?: return false
        val mergedToolName = toolName ?: item.toolName
        val mergedDescription = description ?: item.description

        closeStreamingAgentMessage()
        messages[approvalIndex] =
            item.copy(
                status = item.status.ifBlank { "running" },
                message = approvalStatusMessage("approval_resolved", mergedDescription, mergedToolName),
                eventType = "approval_resolved",
                toolName = mergedToolName,
                description = mergedDescription,
                approvalDecision = approvalDecision ?: item.approvalDecision,
                seq = seq,
            )
        return true
    }

    private fun findToolCallIndex(callId: String): Int =
        messages.indexOfLast { item ->
            when (item) {
                is MessageItem.InteractiveToolCall -> item.id == callId
                is MessageItem.ToolCall -> item.callId == callId
                else -> false
            }
        }

    private fun hasToolCall(callId: String): Boolean = findToolCallIndex(callId) >= 0

    private fun findApprovalStatusIndex(callId: String?): Int {
        if (callId.isNullOrBlank()) {
            return -1
        }
        return messages.indexOfLast { item ->
            val statusChange = item as? MessageItem.StatusChange ?: return@indexOfLast false
            statusChange.callId == callId &&
                (statusChange.eventType == "approval_required" || statusChange.eventType == "approval_resolved")
        }
    }

    private fun closeStreamingAgentMessage() {
        val lastIndex = messages.lastIndex
        val lastMessage = messages.lastOrNull()
        if (lastMessage is MessageItem.AgentMessage && lastMessage.isStreaming) {
            messages[lastIndex] = lastMessage.copy(isStreaming = false)
        }
    }

    private fun trimMessagesIfNeeded() {
        val overflow = messages.size - MAX_MESSAGE_ITEMS
        if (overflow > 0) {
            messages.subList(0, overflow).clear()
        }
    }

    private fun truncatePayload(
        text: String?,
        maxLength: Int = MAX_TOOL_PAYLOAD_LENGTH,
    ): String? {
        if (text == null) {
            return null
        }
        return if (text.length > maxLength) {
            text.take(maxLength) + "…(已截断)"
        } else {
            text
        }
    }
}

internal fun isToolCallError(
    result: String?,
    payload: JSONObject?,
): Boolean {
    val lower = result?.lowercase().orEmpty()
    return result == null ||
        payload?.has("error") == true ||
        lower.startsWith("error") ||
        lower.startsWith("enoent") ||
        lower.startsWith("eperm") ||
        lower.startsWith("permission denied")
}

private fun JSONObject?.toolName(): String? =
    stringValue("tool_name") ?: stringValue("toolName") ?: stringValue("tool") ?: stringValue("name")

private fun JSONObject?.approvalDecision(): String? =
    when (val payload = this) {
        null -> null
        else -> {
            val approvedValue = payload.opt("approved")
            if (approvedValue is Boolean) {
                if (approvedValue) "approved" else "denied"
            } else {
                payload.stringValue("decision") ?: payload.stringValue("resolution") ?: payload.stringValue("result")
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

internal fun JSONObject?.intValue(key: String): Int? {
    val payload = this ?: return null
    val value = payload.opt(key)
    return when (value) {
        null,
        JSONObject.NULL,
        -> null
        is Int -> value
        is Long -> value.toInt()
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

internal fun JSONObject?.doubleValue(key: String): Double? {
    val payload = this ?: return null
    val value = payload.opt(key)
    return when (value) {
        null,
        JSONObject.NULL,
        -> null
        is Double -> value
        is Float -> value.toDouble()
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

private fun JSONObject?.toSessionUsageState(): SessionUsageState? =
    this?.let { payload ->
        SessionUsageState(
            inputTokens = payload.intValue("input_tokens") ?: 0,
            outputTokens = payload.intValue("output_tokens") ?: 0,
            cacheCreationTokens = payload.intValue("cache_creation_input_tokens") ?: 0,
            cacheReadTokens = payload.intValue("cache_read_input_tokens") ?: 0,
            totalCostUsd = payload.doubleValue("total_cost_usd") ?: 0.0,
            contextWindow = payload.intValue("context_window") ?: 0,
            model = payload.stringValue("model"),
        )
    }
