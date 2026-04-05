package com.imbot.android.ui.home

import com.imbot.android.ui.detail.formatTokenCount
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

fun formatRelativeTime(isoString: String): String = formatRelativeTime(isoString, Instant.now())

internal fun formatRelativeTime(
    isoString: String,
    now: Instant,
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
    val dayDifference =
        ChronoUnit.DAYS.between(
            instant.atZone(ZoneId.systemDefault()).toLocalDate(),
            now.atZone(ZoneId.systemDefault()).toLocalDate(),
        ).coerceAtLeast(0)

    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        dayDifference == 0L && hours < 24 -> "$hours 小时前"
        dayDifference == 1L -> "昨天"
        else -> "$dayDifference 天前"
    }
}

internal fun summarizeWorkspacePath(path: String): String {
    val segments = path.replace("\\", "/").split("/").filter(String::isNotBlank)

    return when {
        segments.isEmpty() -> "/"
        segments.size == 1 -> segments.first()
        else -> segments.takeLast(2).joinToString("/")
    }
}

internal fun summarizePrompt(
    prompt: String?,
    maxLength: Int = 50,
): String {
    val normalizedPrompt = prompt?.trim().orEmpty()
    if (normalizedPrompt.isBlank()) {
        return "无初始提示词"
    }

    return if (normalizedPrompt.length <= maxLength) {
        normalizedPrompt
    } else {
        normalizedPrompt.take(maxLength) + "..."
    }
}

internal fun sessionModelDisplayName(model: String?): String? {
    val normalizedModel = model.orEmpty().trim().substringBefore("[")
    if (normalizedModel.isBlank()) {
        return null
    }

    return when {
        normalizedModel.contains("opus", ignoreCase = true) -> "Opus"
        normalizedModel.contains("sonnet", ignoreCase = true) -> "Sonnet"
        normalizedModel.contains("haiku", ignoreCase = true) -> "Haiku"
        else -> normalizedModel
    }
}

internal fun sessionUsageSummaryLabel(
    inputTokens: Int,
    outputTokens: Int,
    contextWindow: Int,
): String? {
    val totalTokens = inputTokens + outputTokens
    if (totalTokens <= 0 || contextWindow <= 0) {
        return null
    }

    return "${formatTokenCount(totalTokens)}/${formatTokenCount(contextWindow)}"
}

internal fun isLiveStatus(status: String): Boolean = status == "queued" || status == "running" || status == "idle"

internal fun isRunningStatus(status: String): Boolean = status == "running"
