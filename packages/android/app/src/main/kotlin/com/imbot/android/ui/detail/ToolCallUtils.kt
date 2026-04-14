package com.imbot.android.ui.detail

import org.json.JSONObject

private const val BASH_FALLBACK_LIMIT = 80
private const val TOOL_SUMMARY_DETAIL_LIMIT = 60

internal fun extractBashCommand(args: String?): String? {
    if (args.isNullOrBlank()) {
        return null
    }

    val json = parseJsonObject(args)
    return if (json != null) {
        json.optNonBlankString("command")
    } else {
        args.take(BASH_FALLBACK_LIMIT).takeIf(String::isNotBlank)
    }
}

internal fun extractFilePath(args: String?): String? {
    val json = args?.takeIf(String::isNotBlank)?.let(::parseJsonObject)
    val path = json?.optNonBlankString("file_path") ?: json?.optNonBlankString("path")
    return path?.let(::summarizeFilePath)
}

internal fun extractSearchPattern(args: String?): String? {
    val json = args?.takeIf(String::isNotBlank)?.let(::parseJsonObject)
    return json?.optNonBlankString("pattern")
        ?: json?.optNonBlankString("query")
        ?: json?.optNonBlankString("url")
}

internal fun extractSkillName(args: String?): String? {
    val json = args?.takeIf(String::isNotBlank)?.let(::parseJsonObject)
    return json?.optNonBlankString("skill")
}

internal fun extractJsonField(
    json: String?,
    field: String,
): String? {
    if (json.isNullOrBlank()) {
        return null
    }

    return parseJsonObject(json)?.optNonBlankString(field)
}

internal fun buildToolSummary(
    category: ToolCategory,
    item: MessageItem.ToolCall,
): String {
    val label = formatToolName(toolName = item.toolName, fallback = item.title)
    val detail =
        when (category) {
            ToolCategory.BASH ->
                extractBashCommand(item.args)?.let { command ->
                    "$ ${command.take(TOOL_SUMMARY_DETAIL_LIMIT)}"
                }

            ToolCategory.READ,
            ToolCategory.WRITE,
            -> extractFilePath(item.args)

            ToolCategory.SEARCH -> extractSearchPattern(item.args)?.take(TOOL_SUMMARY_DETAIL_LIMIT)
            ToolCategory.SKILL -> extractSkillName(item.args)?.let { "/$it" }
            ToolCategory.OTHER -> null
        }

    return detail?.let { "$label · $it" } ?: label
}

private fun parseJsonObject(json: String): JSONObject? = runCatching { JSONObject(json) }.getOrNull()

private fun JSONObject.optNonBlankString(field: String): String? =
    when (val value = opt(field)) {
        null,
        JSONObject.NULL,
        -> null

        is String -> value
        else -> value.toString()
    }?.takeIf(String::isNotBlank)

private fun summarizeFilePath(path: String): String {
    val normalizedPath = path.replace("\\", "/")
    val segments = normalizedPath.split("/").filter(String::isNotBlank)
    return if (segments.size > 3) {
        ".../" + segments.takeLast(3).joinToString("/")
    } else {
        normalizedPath
    }
}

private fun formatToolName(
    toolName: String,
    fallback: String,
): String {
    val rawName =
        toolName
            .ifBlank { fallback }
            .ifBlank { "Tool" }
    val normalizedName =
        if (rawName == rawName.uppercase()) {
            rawName.lowercase()
        } else {
            rawName
        }

    return normalizedName.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase()
        } else {
            char.toString()
        }
    }
}
