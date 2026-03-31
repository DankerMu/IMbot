@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.components.CodeBlock

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph ->
                    MarkdownInlineText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                is MarkdownBlock.Heading ->
                    MarkdownInlineText(
                        text = block.text,
                        style =
                            when (block.level) {
                                1 -> MaterialTheme.typography.headlineSmall
                                2 -> MaterialTheme.typography.titleLarge
                                else -> MaterialTheme.typography.titleMedium
                            },
                    )

                is MarkdownBlock.Bullet ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = block.marker,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        MarkdownInlineText(
                            text = block.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                is MarkdownBlock.Quote ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        MarkdownInlineText(
                            text = block.text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                is MarkdownBlock.Code ->
                    CodeBlock(
                        language = block.language,
                        code = block.code,
                    )
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated =
        remember(text, inlineCodeBackground, linkColor) {
            buildMarkdownAnnotatedString(
                text = text,
                inlineCodeBackground = inlineCodeBackground,
                linkColor = linkColor,
            )
        }

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { annotation ->
                    runCatching {
                        uriHandler.openUri(annotation.item)
                    }
                }
        },
    )
}

private sealed interface MarkdownBlock {
    data class Paragraph(
        val text: String,
    ) : MarkdownBlock

    data class Heading(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class Bullet(
        val marker: String,
        val text: String,
    ) : MarkdownBlock

    data class Quote(
        val text: String,
    ) : MarkdownBlock

    data class Code(
        val language: String?,
        val code: String,
    ) : MarkdownBlock
}

@Suppress("CyclomaticComplexMethod")
private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        when {
            trimmed.isBlank() -> index++
            trimmed.startsWith("```") -> {
                val language = trimmed.removePrefix("```").trim().ifBlank { null }
                index++
                val codeLines = mutableListOf<String>()
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    codeLines += lines[index]
                    index++
                }
                if (index < lines.size) {
                    index++
                }
                blocks += MarkdownBlock.Code(language = language, code = codeLines.joinToString("\n"))
            }

            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks += MarkdownBlock.Heading(level = level, text = trimmed.drop(level).trim())
                index++
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks += MarkdownBlock.Bullet(marker = "•", text = trimmed.drop(2).trim())
                index++
            }

            ORDERED_LIST_REGEX.matches(trimmed) -> {
                val match = ORDERED_LIST_REGEX.find(trimmed)
                blocks +=
                    MarkdownBlock.Bullet(
                        marker = "${match?.groupValues?.get(1).orEmpty()}.",
                        text = match?.groupValues?.get(2).orEmpty(),
                    )
                index++
            }

            trimmed.startsWith("> ") -> {
                blocks += MarkdownBlock.Quote(text = trimmed.removePrefix("> ").trim())
                index++
            }

            else -> {
                val paragraphLines = mutableListOf(trimmed)
                index++
                while (index < lines.size && shouldContinueParagraph(lines[index])) {
                    paragraphLines += lines[index].trim()
                    index++
                }
                blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString(separator = "\n"))
            }
        }
    }

    return blocks
}

private fun shouldContinueParagraph(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.isNotBlank() &&
        !trimmed.startsWith("```") &&
        !trimmed.startsWith("#") &&
        !trimmed.startsWith("- ") &&
        !trimmed.startsWith("* ") &&
        !trimmed.startsWith("> ") &&
        !ORDERED_LIST_REGEX.matches(trimmed)
}

@Suppress("CyclomaticComplexMethod")
private fun buildMarkdownAnnotatedString(
    text: String,
    inlineCodeBackground: Color,
    linkColor: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var currentIndex = 0

    INLINE_TOKEN_REGEX.findAll(text).forEach { match ->
        if (match.range.first > currentIndex) {
            builder.append(text.substring(currentIndex, match.range.first))
        }

        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") ->
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removePrefix("**").removeSuffix("**"))
                }

            token.startsWith("*") && token.endsWith("*") ->
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removePrefix("*").removeSuffix("*"))
                }

            token.startsWith("~~") && token.endsWith("~~") ->
                builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(token.removePrefix("~~").removeSuffix("~~"))
                }

            token.startsWith("`") && token.endsWith("`") ->
                builder.withStyle(
                    SpanStyle(
                        background = inlineCodeBackground,
                    ),
                ) {
                    append(token.removePrefix("`").removeSuffix("`"))
                }

            token.startsWith("[") -> {
                val linkMatch = LINK_REGEX.matchEntire(token)
                val label = linkMatch?.groupValues?.get(1).orEmpty()
                val url = linkMatch?.groupValues?.get(2).orEmpty()
                if (label.isNotBlank() && url.isNotBlank()) {
                    builder.pushStringAnnotation(tag = "URL", annotation = url)
                    builder.withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(label)
                    }
                    builder.pop()
                } else {
                    builder.append(token)
                }
            }
        }

        currentIndex = match.range.last + 1
    }

    if (currentIndex < text.length) {
        builder.append(text.substring(currentIndex))
    }

    return builder.toAnnotatedString()
}

private val ORDERED_LIST_REGEX = Regex("""(\d+)\.\s+(.*)""")
private val LINK_REGEX = Regex("""\[(.*?)]\((.*?)\)""")
private val INLINE_TOKEN_REGEX =
    Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|~~[^~]+~~|`[^`]+`|\[[^\]]+]\([^)]+\))""")
