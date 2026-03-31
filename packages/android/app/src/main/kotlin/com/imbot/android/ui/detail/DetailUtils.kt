@file:Suppress("TooManyFunctions")

package com.imbot.android.ui.detail

import androidx.compose.ui.graphics.Color
import com.imbot.android.network.RelaySession
import com.imbot.android.ui.theme.LightStatusColors
import com.imbot.android.ui.theme.ProviderColors
import com.imbot.android.ui.theme.StatusColors
import com.imbot.android.ui.theme.providerColorFor
import com.imbot.android.ui.theme.statusColorFor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val SCROLL_PAUSE_THRESHOLD_DP = 100f

data class DetailScrollState(
    val autoScrollEnabled: Boolean = true,
    val newMsgCount: Int = 0,
    val fabVisible: Boolean = false,
)

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

internal fun onScrollDistanceChanged(
    current: DetailScrollState,
    distanceFromBottomDp: Float,
): DetailScrollState =
    if (distanceFromBottomDp > SCROLL_PAUSE_THRESHOLD_DP) {
        current.copy(
            autoScrollEnabled = false,
            fabVisible = true,
        )
    } else {
        current.copy(
            autoScrollEnabled = true,
            newMsgCount = 0,
            fabVisible = false,
        )
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

internal fun canSendToSession(status: String?): Boolean = status == "running"

internal fun detailStatusColor(
    status: String,
    colors: StatusColors = LightStatusColors,
): Color = statusColorFor(status = status, colors = colors)

internal fun inputPlaceholderForStatus(status: String?): String =
    when (status) {
        "running" -> "输入消息..."
        "queued" -> "会话启动中，暂时无法发送"
        "completed" -> "会话已结束"
        "failed" -> "会话已失败"
        "cancelled" -> "会话已取消"
        else -> "当前无法发送消息"
    }

internal fun messageItemKindForEventType(eventType: String): MessageItemKind? =
    when (eventType) {
        "user_message" -> MessageItemKind.User
        "assistant_delta", "assistant_message" -> MessageItemKind.Agent
        "tool_call_started", "tool_call_completed" -> MessageItemKind.ToolCall
        "session_status_changed", "session_started", "session_result", "session_error" -> MessageItemKind.StatusChange
        else -> null
    }

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
): Color = providerColorFor(provider = provider, colors = colors)

internal fun statusLabel(status: String): String =
    when (status) {
        "running" -> "运行中"
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

internal fun copyableAgentTranscript(messages: List<MessageItem>): String =
    messages
        .mapNotNull { item ->
            when (item) {
                is MessageItem.AgentMessage -> item.content.takeIf { it.isNotBlank() }
                else -> null
            }
        }
        .joinToString(separator = "\n\n")
