package com.imbot.android.ui.components

import com.imbot.android.ui.theme.normalizeLanguage

internal const val MAX_CODE_GUTTER_LINES = 500
internal const val CODE_BLOCK_COLLAPSE_THRESHOLD_LINES = 20
internal const val CODE_BLOCK_COLLAPSED_VISIBLE_LINES = 10

private val TerminalLanguages = setOf("bash", "shell", "sh", "zsh", "terminal")

internal data class CodeBlockDisplayState(
    val totalLines: Int,
    val isCollapsible: Boolean,
    val isCollapsed: Boolean,
    val displayedCode: String,
    val toggleLabel: String?,
)

internal fun extractCodeLanguageLabel(infoString: String?): String? {
    val rawLabel = rawCodeLanguageLabel(infoString) ?: return null
    return rawLabel.takeIf { normalizeLanguage(rawLabel) != null || rawLabel in TerminalLanguages }
}

internal fun buildCodeLineNumbers(code: String): List<String> {
    val lineCount = codeLineCount(code)
    if (lineCount == 0 || lineCount > MAX_CODE_GUTTER_LINES) {
        return emptyList()
    }
    return (1..lineCount).map(Int::toString)
}

internal fun codeLineCount(code: String): Int =
    if (code.isEmpty()) {
        0
    } else {
        code.count { it == '\n' } + 1
    }

internal fun resolveCodeBlockDisplayState(
    code: String,
    expanded: Boolean,
): CodeBlockDisplayState {
    val totalLines = codeLineCount(code)
    val isCollapsible = totalLines > CODE_BLOCK_COLLAPSE_THRESHOLD_LINES
    val isCollapsed = isCollapsible && !expanded
    val displayedCode =
        if (isCollapsed) {
            takeCodeLines(code, CODE_BLOCK_COLLAPSED_VISIBLE_LINES)
        } else {
            code
        }

    return CodeBlockDisplayState(
        totalLines = totalLines,
        isCollapsible = isCollapsible,
        isCollapsed = isCollapsed,
        displayedCode = displayedCode,
        toggleLabel =
            when {
                !isCollapsible -> null
                isCollapsed -> "展开 ($totalLines 行)"
                else -> "收起"
            },
    )
}

internal fun isTerminalCodeLanguage(infoString: String?): Boolean {
    val rawLabel = rawCodeLanguageLabel(infoString) ?: return false
    return rawLabel in TerminalLanguages || normalizeLanguage(rawLabel) == "bash"
}

private fun rawCodeLanguageLabel(infoString: String?): String? =
    infoString
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)

private fun takeCodeLines(
    code: String,
    count: Int,
): String {
    if (count <= 0 || code.isEmpty()) {
        return ""
    }

    var nextSearchStart = 0
    var remainingVisibleLineBreaks = count - 1
    var containsRequestedLineCount = true
    while (remainingVisibleLineBreaks > 0) {
        val lineBreakIndex = code.indexOf('\n', startIndex = nextSearchStart)
        if (lineBreakIndex < 0) {
            containsRequestedLineCount = false
            break
        }
        nextSearchStart = lineBreakIndex + 1
        remainingVisibleLineBreaks--
    }

    val endIndex =
        if (containsRequestedLineCount) {
            code.indexOf('\n', startIndex = nextSearchStart)
        } else {
            -1
        }
    return if (endIndex >= 0) {
        code.substring(0, endIndex)
    } else {
        code
    }
}
