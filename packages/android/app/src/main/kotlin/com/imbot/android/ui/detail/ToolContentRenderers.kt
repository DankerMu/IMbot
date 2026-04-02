@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.components.CodeBlock

@Composable
internal fun BashToolContent(item: MessageItem.ToolCall) {
    val command = extractBashCommand(item.args)
    val output = item.result

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(
                    color = Color(0xFF0A0A0A),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (command != null) {
            SelectionContainer {
                Row {
                    Text(
                        text = "$ ",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF34C759),
                    )
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFF5F5F5),
                    )
                }
            }
        }

        if (!output.isNullOrBlank()) {
            SelectionContainer {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA1A1AA),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
internal fun ReadToolContent(item: MessageItem.ToolCall) {
    val filePath = extractFilePath(item.args)
    val content = item.result
    val language = inferCodeLanguage(filePath)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filePath != null) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!content.isNullOrBlank()) {
            CodeBlock(
                code = content,
                language = language ?: "text",
            )
        }
    }
}

@Composable
internal fun WriteToolContent(item: MessageItem.ToolCall) {
    val filePath = extractFilePath(item.args)
    val oldString = extractJsonField(item.args, "old_string")
    val newString = extractJsonField(item.args, "new_string")
    val writeContent = extractJsonField(item.args, "content")
    val result = item.result
    val language = inferCodeLanguage(filePath)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filePath != null) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            oldString != null && newString != null -> {
                DiffView(
                    oldText = oldString,
                    newText = newString,
                )
            }

            !writeContent.isNullOrBlank() -> {
                CodeBlock(
                    code = writeContent,
                    language = language ?: "text",
                )
            }

            !result.isNullOrBlank() -> {
                SelectionContainer {
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchToolContent(item: MessageItem.ToolCall) {
    val pattern = extractSearchPattern(item.args)
    val result = item.result

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pattern != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = pattern,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (!result.isNullOrBlank()) {
            val lines = result.lines()
            val visibleLines = lines.take(50)
            val hiddenLineCount = (lines.size - visibleLines.size).coerceAtLeast(0)

            SelectionContainer {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(12.dp)
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = visibleLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (hiddenLineCount > 0) {
                Text(
                    text = "+$hiddenLineCount more lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun GenericToolContent(item: MessageItem.ToolCall) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item.args?.takeIf(String::isNotBlank)?.let { args ->
            ToolCallSection(
                title = "参数",
                content = args,
            )
        }
        item.result?.takeIf(String::isNotBlank)?.let { result ->
            ToolCallSection(
                title = "结果",
                content = result,
            )
        }
    }
}

@Composable
internal fun ToolCallSection(
    title: String,
    content: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun inferCodeLanguage(filePath: String?): String? {
    val extension = filePath?.substringAfterLast('.', missingDelimiterValue = "")
    return extension?.takeIf(String::isNotBlank)?.lowercase()
}
