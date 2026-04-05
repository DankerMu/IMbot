@file:Suppress("TooManyFunctions")

package com.imbot.android.ui.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imbot.android.network.RelaySession
import com.imbot.android.ui.theme.DestructiveColor
import com.imbot.android.ui.theme.ProviderColors
import com.imbot.android.ui.theme.StatusColors
import com.imbot.android.ui.theme.SuccessColor
import com.imbot.android.ui.theme.providerColorFor
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val DEFAULT_ASK_USER_QUESTION_MESSAGE = "Agent is asking for input"
internal const val MAX_ASK_USER_QUESTION_OPTIONS = 10
internal const val ASK_USER_QUESTION_OPTIONS_TRUNCATED_NOTE = "仅显示前 10 个选项"
internal val MESSAGE_GROUP_SPACING = 24.dp
internal val MESSAGE_WITHIN_GROUP_SPACING = 8.dp
internal val MESSAGE_HORIZONTAL_PADDING = 16.dp
internal val MESSAGE_VERTICAL_PADDING = 24.dp
private const val MAX_ASK_USER_QUESTIONS = 5
private const val TOOL_CALL_COPY_SUMMARY_LIMIT = 200

private val DefaultDetailStatusColors =
    StatusColors(
        queued = Color(0xFF9CA3AF),
        running = Color(0xFF10B981),
        idle = Color(0xFF2196F3),
        completed = Color(0xFF059669),
        failed = Color(0xFFEF4444),
        cancelled = Color(0xFF6B7280),
    )

data class DetailScrollState(
    val autoScrollEnabled: Boolean = true,
    val newMsgCount: Int = 0,
    val fabVisible: Boolean = false,
)

internal data class SessionUsageState(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheCreationTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val totalCostUsd: Double = 0.0,
    val contextWindow: Int = 0,
    val model: String? = null,
) {
    val totalTokens: Int
        get() = inputTokens + outputTokens

    val usagePercent: Float
        get() = if (contextWindow > 0) (totalTokens.toFloat() / contextWindow).coerceIn(0f, 1f) else 0f
}

internal data class ScrollMutation(
    val state: DetailScrollState,
    val shouldScrollToBottom: Boolean,
)

internal enum class MessageItemKind {
    User,
    Agent,
    ToolCall,
    StatusChange,
}

internal fun messageItemKind(item: MessageItem): MessageItemKind =
    when (item) {
        is MessageItem.UserMessage -> MessageItemKind.User
        is MessageItem.AgentMessage -> MessageItemKind.Agent
        is MessageItem.InteractiveToolCall, is MessageItem.ToolCall -> MessageItemKind.ToolCall
        is MessageItem.StatusChange -> MessageItemKind.StatusChange
    }

internal fun messageSpacing(
    previousItem: MessageItem?,
    currentItem: MessageItem,
): Dp =
    if (previousItem == null || messageItemKind(previousItem) != messageItemKind(currentItem)) {
        MESSAGE_GROUP_SPACING
    } else {
        MESSAGE_WITHIN_GROUP_SPACING
    }

internal fun deduplicateStatusChanges(messages: List<MessageItem>): List<MessageItem> =
    messages.filterIndexed { index, item ->
        if (item !is MessageItem.StatusChange || !item.isDeduplicableStatusChange()) {
            return@filterIndexed true
        }
        val next = messages.getOrNull(index + 1) as? MessageItem.StatusChange ?: return@filterIndexed true
        !(next.isDeduplicableStatusChange() && next.status == item.status)
    }

internal fun onTimelineChanged(
    current: DetailScrollState,
    itemCountChanged: Boolean,
): ScrollMutation =
    if (current.autoScrollEnabled) {
        ScrollMutation(current, shouldScrollToBottom = true)
    } else {
        ScrollMutation(
            state =
                current.copy(
                    newMsgCount = current.newMsgCount + if (itemCountChanged) 1 else 0,
                    fabVisible = true,
                ),
            shouldScrollToBottom = false,
        )
    }

internal fun onScrollPositionUpdate(
    current: DetailScrollState,
    nearBottom: Boolean,
    userInitiatedScrollAway: Boolean,
): DetailScrollState =
    if (nearBottom) {
        current.copy(
            autoScrollEnabled = true,
            newMsgCount = 0,
            fabVisible = false,
        )
    } else if (userInitiatedScrollAway) {
        current.copy(
            autoScrollEnabled = false,
            fabVisible = true,
        )
    } else {
        current
    }

internal fun resumeAutoScroll(current: DetailScrollState): ScrollMutation =
    ScrollMutation(
        state =
            current.copy(
                autoScrollEnabled = true,
                newMsgCount = 0,
                fabVisible = false,
            ),
        shouldScrollToBottom = true,
    )

internal fun canSendToSession(status: String?): Boolean = status == "running" || status == "idle"

internal fun canInputToSession(status: String?): Boolean = status == "idle"

internal fun canRespondToInteractiveRequest(status: String?): Boolean = status == "running"

internal fun canResumeSession(status: String?): Boolean = status in RESUMABLE_STATUSES

private val RESUMABLE_STATUSES = setOf("completed", "failed", "cancelled")

internal fun canCancelSession(status: String?): Boolean = status == "running"

internal fun canCompleteSession(status: String?): Boolean = status == "idle"

internal fun effectiveSessionStatus(
    sessionStatus: String?,
    messages: List<MessageItem>,
): String? {
    val normalizedSessionStatus = sessionStatus?.takeIf(String::isNotBlank)
    val timelineStatus =
        messages
            .asReversed()
            .firstNotNullOfOrNull { item ->
                val statusChange = item as? MessageItem.StatusChange ?: return@firstNotNullOfOrNull null
                if (statusChange.eventType != null) {
                    return@firstNotNullOfOrNull null
                }
                statusChange.status.takeIf(String::isNotBlank)
            }

    return when {
        normalizedSessionStatus in RESUMABLE_STATUSES -> normalizedSessionStatus
        hasPendingSessionInteraction(messages) -> "running"
        else -> normalizedSessionStatus ?: timelineStatus
    }
}

internal fun shouldIgnoreSessionSnapshotStatus(
    currentStatus: String?,
    snapshotStatus: String?,
): Boolean =
    when {
        currentStatus.isNullOrBlank() || snapshotStatus.isNullOrBlank() -> false
        currentStatus == snapshotStatus -> false
        currentStatus == "idle" && snapshotStatus in setOf("queued", "running") -> true
        currentStatus == "running" && snapshotStatus == "queued" -> true
        currentStatus in RESUMABLE_STATUSES && snapshotStatus !in RESUMABLE_STATUSES -> true
        else -> false
    }

internal fun detailStatusColor(
    status: String,
    colors: StatusColors = DefaultDetailStatusColors,
): Color =
    when (status.lowercase()) {
        "queued" -> colors.queued
        "running" -> colors.running
        "idle" -> colors.idle
        "completed" -> colors.completed
        "failed" -> colors.failed
        "cancelled" -> colors.cancelled
        else -> colors.queued
    }

internal fun statusBubbleDotColor(status: String): Color =
    when (status.lowercase()) {
        "running", "completed" -> SuccessColor
        "idle" -> Color(0xFF2196F3)
        "failed" -> DestructiveColor
        "cancelled" -> Color(0xFF6B7280)
        else -> Color(0xFF9CA3AF)
    }

internal fun formatTokenCount(count: Int): String =
    when {
        count >= 1_000_000 -> formatCompactTokenCount(count / 1_000_000f, "M")
        count >= 1_000 -> formatCompactTokenCount(count / 1_000f, "k")
        else -> count.toString()
    }

private fun formatCompactTokenCount(
    value: Float,
    suffix: String,
): String = "${String.format(Locale.US, "%.1f", value).removeSuffix(".0")}$suffix"

internal fun usageColor(percent: Float): Color =
    when {
        percent > 0.9f -> Color(0xFFE53935)
        percent > 0.8f -> Color(0xFFFFA726)
        else -> Color(0xFF66BB6A)
    }

internal fun inputPlaceholderForStatus(status: String?): String =
    when (status) {
        "running" -> "AI 正在回复..."
        "idle" -> "继续对话..."
        "queued" -> "会话启动中，暂时无法发送"
        "completed" -> "会话已结束，可恢复后继续"
        "failed" -> "会话已失败，可恢复后继续"
        "cancelled" -> "会话已取消，可恢复后继续"
        else -> "当前无法发送消息"
    }

internal fun isInteractiveToolCall(toolName: String?): Boolean =
    toolName?.equals(
        "AskUserQuestion",
        ignoreCase = true,
    ) == true

data class ParsedQuestion(
    val question: String,
    val header: String?,
    val options: List<ParsedOption>?,
    val multiSelect: Boolean,
)

data class ParsedOption(
    val label: String,
    val description: String?,
)

internal fun parseAskUserQuestionV2(inputJson: String?): List<ParsedQuestion> =
    if (inputJson.isNullOrBlank()) {
        listOf(defaultParsedQuestion())
    } else {
        runCatching {
            val json = JSONObject(inputJson)
            val questionsArray = json.optJSONArray("questions")
            if (questionsArray != null && questionsArray.length() > 0) {
                parseStandardAskUserQuestions(questionsArray)
            } else {
                parseSimplifiedAskUserQuestion(json)
            }
        }.getOrElse {
            listOf(defaultParsedQuestion(question = inputJson))
        }
    }

internal fun parseAskUserQuestion(inputJson: String?): Pair<String, List<String>?> {
    if (inputJson.isNullOrBlank()) {
        return DEFAULT_ASK_USER_QUESTION_MESSAGE to null
    }

    return try {
        val json = JSONObject(inputJson)
        val question = json.optString("question", "").takeIf { it.isNotBlank() }
        val optionCount = json.optJSONArray("options")?.length() ?: 0
        val options =
            json.optJSONArray("options")?.let { array ->
                (0 until array.length().coerceAtMost(MAX_ASK_USER_QUESTION_OPTIONS)).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            }?.takeIf { it.isNotEmpty() }
        val decoratedQuestion = question?.let { parsedQuestion -> decorateAskUserQuestion(parsedQuestion, optionCount) }

        when {
            decoratedQuestion != null -> decoratedQuestion to options
            json.length() == 0 -> DEFAULT_ASK_USER_QUESTION_MESSAGE to null
            else -> inputJson to null
        }
    } catch (_: Exception) {
        inputJson to null
    }
}

private fun defaultParsedQuestion(question: String = DEFAULT_ASK_USER_QUESTION_MESSAGE) =
    ParsedQuestion(
        question = question,
        header = null,
        options = null,
        multiSelect = false,
    )

private fun decorateAskUserQuestion(
    question: String,
    optionCount: Int,
): String =
    if (optionCount > MAX_ASK_USER_QUESTION_OPTIONS) {
        "$question\n\n$ASK_USER_QUESTION_OPTIONS_TRUNCATED_NOTE"
    } else {
        question
    }

private fun parseStandardAskUserQuestions(array: JSONArray): List<ParsedQuestion> =
    (0 until array.length().coerceAtMost(MAX_ASK_USER_QUESTIONS)).map { index ->
        val questionObject = array.getJSONObject(index)
        val rawQuestionText =
            questionObject
                .optString("question", "")
                .takeIf(String::isNotBlank)
                ?: DEFAULT_ASK_USER_QUESTION_MESSAGE
        val header = questionObject.optString("header", "").takeIf(String::isNotBlank)
        val multiSelect = questionObject.optBoolean("multiSelect", false)
        val optionArray = questionObject.optJSONArray("options")
        val options =
            optionArray?.let {
                parseAskUserQuestionOptions(
                    array = it,
                    allowScalarFallback = true,
                )
            }?.takeIf { it.isNotEmpty() }

        ParsedQuestion(
            question = decorateAskUserQuestion(rawQuestionText, optionArray?.length() ?: 0),
            header = header,
            options = options,
            multiSelect = multiSelect,
        )
    }

private fun parseSimplifiedAskUserQuestion(json: JSONObject): List<ParsedQuestion> {
    val question = json.optString("question", "").takeIf(String::isNotBlank)
    val optionArray = json.optJSONArray("options")
    val options =
        optionArray?.let {
            parseAskUserQuestionOptions(
                array = it,
                allowScalarFallback = false,
            )
        }?.takeIf { it.isNotEmpty() }

    return listOf(
        ParsedQuestion(
            question =
                decorateAskUserQuestion(
                    question = question ?: DEFAULT_ASK_USER_QUESTION_MESSAGE,
                    optionCount = optionArray?.length() ?: 0,
                ),
            header = null,
            options = options,
            multiSelect = false,
        ),
    )
}

private fun parseAskUserQuestionOptions(
    array: JSONArray,
    allowScalarFallback: Boolean,
): List<ParsedOption> =
    (0 until array.length().coerceAtMost(MAX_ASK_USER_QUESTION_OPTIONS)).mapNotNull { index ->
        when (val option = array.opt(index)) {
            is JSONObject -> {
                val label = option.optString("label", "").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val description = option.optString("description", "").takeIf(String::isNotBlank)
                ParsedOption(label = label, description = description)
            }
            is String ->
                option
                    .takeIf(String::isNotBlank)
                    ?.let { label -> ParsedOption(label = label, description = null) }
            else ->
                if (allowScalarFallback) {
                    option
                        ?.toString()
                        ?.takeIf(String::isNotBlank)
                        ?.let { label -> ParsedOption(label = label, description = null) }
                } else {
                    null
                }
        }
    }

internal fun messageItemKindForEventType(eventType: String): MessageItemKind? =
    when (eventType) {
        "user_message" -> MessageItemKind.User
        "assistant_delta", "assistant_message" -> MessageItemKind.Agent
        "tool_call_started", "tool_call_completed" -> MessageItemKind.ToolCall
        "session_status_changed",
        "session_started",
        "session_idle",
        "session_result",
        "session_error",
        "approval_required",
        "approval_resolved",
        -> MessageItemKind.StatusChange
        else -> null
    }

internal fun approvalStatusMessage(
    eventType: String,
    description: String?,
    toolName: String?,
): String =
    buildString {
        append(
            when (eventType) {
                "approval_required" -> "Approval required"
                "approval_resolved" -> "Approval resolved"
                else -> eventType
            },
        )
        val detail =
            when {
                !description.isNullOrBlank() -> description
                !toolName.isNullOrBlank() -> toolName
                else -> null
            }
        if (detail != null) {
            append(": ")
            append(detail)
        }
    }

internal fun approvalDecisionLabel(item: MessageItem.StatusChange): String =
    item.approvalDecision?.takeIf(String::isNotBlank)?.let { decision ->
        when (decision.lowercase()) {
            "approved", "approve", "true" -> "已批准"
            "denied", "deny", "false" -> "已拒绝"
            else -> decision
        }
    } ?: if (item.eventType == "approval_resolved") "已处理" else "等待审批"

internal fun approvalInputText(approved: Boolean): String = if (approved) "approve" else "deny"

internal fun findLatestPendingInteractiveToolCallId(
    messages: List<MessageItem>,
    sessionStatus: String?,
): String? {
    if (!canRespondToInteractiveRequest(sessionStatus)) {
        return null
    }

    return messages
        .asReversed()
        .firstNotNullOfOrNull { item ->
            (item as? MessageItem.InteractiveToolCall)
                ?.takeUnless { it.isAnswered }
                ?.id
        }
}

internal fun hasPendingSessionInteraction(messages: List<MessageItem>): Boolean =
    hasPendingInteractiveToolCall(messages) || hasPendingApprovalRequest(messages)

private fun hasPendingInteractiveToolCall(messages: List<MessageItem>): Boolean =
    messages.any { item ->
        (item as? MessageItem.InteractiveToolCall)?.isAnswered == false
    }

private fun hasPendingApprovalRequest(messages: List<MessageItem>): Boolean {
    val resolvedApprovalCallIds = mutableSetOf<String>()
    messages
        .asReversed()
        .forEach { item ->
            val statusChange = item as? MessageItem.StatusChange ?: return@forEach
            val callId = statusChange.callId?.takeIf(String::isNotBlank) ?: return@forEach
            when (statusChange.eventType) {
                "approval_resolved" -> resolvedApprovalCallIds += callId
                "approval_required" -> if (callId !in resolvedApprovalCallIds) return true
            }
        }
    return false
}

internal fun findLatestPendingApprovalCallId(
    messages: List<MessageItem>,
    sessionStatus: String?,
): String? {
    if (!canRespondToInteractiveRequest(sessionStatus)) {
        return null
    }

    return messages
        .asReversed()
        .firstNotNullOfOrNull { item ->
            (item as? MessageItem.StatusChange)
                ?.takeIf { it.eventType == "approval_required" }
                ?.callId
                ?.takeIf(String::isNotBlank)
        }
}

internal fun isLatestPendingInteractiveToolCall(
    item: MessageItem.InteractiveToolCall,
    latestPendingCallId: String?,
): Boolean = item.isAnswered || item.id == latestPendingCallId

internal fun resolvedInteractiveToolCall(
    item: MessageItem.InteractiveToolCall,
    sessionStatus: String?,
): MessageItem.InteractiveToolCall =
    when {
        item.isAnswered -> item
        sessionStatus.isNullOrBlank() -> item
        canRespondToInteractiveRequest(sessionStatus) -> item
        sessionStatus == "idle" && item.answer.isNullOrBlank() -> item
        else ->
            item.copy(
                isAnswered = true,
                errorMessage = null,
            )
    }

internal fun isLatestPendingApprovalRequest(
    item: MessageItem.StatusChange,
    latestPendingCallId: String?,
): Boolean =
    item.eventType != "approval_required" ||
        item.callId.isNullOrBlank() ||
        item.callId == latestPendingCallId

internal fun formatRelativeTimestamp(
    isoString: String,
    now: Instant = Instant.now(),
): String {
    val instant =
        runCatching {
            Instant.parse(isoString)
        }.getOrElse {
            return "未知"
        }
    val duration = Duration.between(instant, now)
    val minutes = duration.toMinutes().coerceAtLeast(0)
    val hours = duration.toHours().coerceAtLeast(0)

    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        hours < 24 &&
            instant.atZone(ZoneId.systemDefault()).toLocalDate() ==
            now.atZone(ZoneId.systemDefault()).toLocalDate() ->
            "$hours 小时前"

        else ->
            instant.atZone(ZoneId.systemDefault()).format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            )
    }
}

internal fun providerDisplayName(provider: String): String =
    when (provider) {
        "claude" -> "Claude Code"
        "book" -> "book"
        "openclaw" -> "OpenClaw"
        else -> provider
    }

internal fun providerShortLabel(provider: String): String =
    when (provider) {
        "claude" -> "CC"
        "book" -> "BK"
        "openclaw" -> "OC"
        else -> provider.take(2).uppercase()
    }

internal fun providerColor(
    provider: String,
    colors: ProviderColors = ProviderColors(),
): Color = providerColorFor(provider, colors)

internal fun statusLabel(status: String): String =
    when (status) {
        "running" -> "运行中"
        "idle" -> "空闲"
        "queued" -> "排队中"
        "completed" -> "已完成"
        "failed" -> "失败"
        "cancelled" -> "已取消"
        else -> status
    }

internal fun summarizeSessionPath(path: String): String {
    val segments = path.replace("\\", "/").split("/").filter(String::isNotBlank)
    return when {
        segments.isEmpty() -> "/"
        segments.size == 1 -> segments.first()
        else -> segments.takeLast(2).joinToString("/")
    }
}

internal fun sessionTitle(session: RelaySession): String = providerDisplayName(session.provider)

internal fun sessionSubtitle(session: RelaySession): String = summarizeSessionPath(session.workspaceCwd)

internal fun copyableText(item: MessageItem): String? =
    when (item) {
        is MessageItem.AgentMessage -> item.content.takeIf(String::isNotBlank)
        is MessageItem.InteractiveToolCall ->
            item.primaryQuestion.question.takeIf(String::isNotBlank)
        is MessageItem.UserMessage -> item.text.takeIf(String::isNotBlank)
        is MessageItem.ToolCall ->
            buildString {
                item.toolName.takeIf(String::isNotBlank)?.let { toolName ->
                    append("Tool: ")
                    append(toolName)
                }
                item.args?.takeIf(String::isNotBlank)?.let { args ->
                    if (isNotEmpty()) {
                        append("\n")
                    }
                    append("Input: ")
                    append(summarizeToolCallCopyField(args))
                }
                item.result?.takeIf(String::isNotBlank)?.let { result ->
                    if (isNotEmpty()) {
                        append("\n")
                    }
                    append("Output: ")
                    append(summarizeToolCallCopyField(result))
                }
            }.takeIf(String::isNotBlank)

        is MessageItem.StatusChange ->
            item.description?.takeIf(String::isNotBlank)
                ?: item.message?.takeIf(String::isNotBlank)
    }

private fun summarizeToolCallCopyField(text: String): String =
    if (text.length > TOOL_CALL_COPY_SUMMARY_LIMIT) {
        text.take(TOOL_CALL_COPY_SUMMARY_LIMIT) + "..."
    } else {
        text
    }

internal fun copyableAgentTranscript(messages: List<MessageItem>): String =
    messages
        .mapNotNull { item ->
            when (item) {
                is MessageItem.AgentMessage -> item.content.takeIf { it.isNotBlank() }
                else -> null
            }
        }
        .joinToString(separator = "\n\n")

private fun MessageItem.StatusChange.isDeduplicableStatusChange(): Boolean =
    eventType != "approval_required" && eventType != "approval_resolved"
