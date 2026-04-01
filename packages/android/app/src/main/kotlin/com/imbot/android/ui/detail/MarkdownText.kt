@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.TextStyle
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
                    MarkdownRichText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                is MarkdownBlock.Heading ->
                    MarkdownRichText(
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
                        MarkdownRichText(
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
                        MarkdownRichText(
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

                is MarkdownBlock.Math ->
                    MarkdownKatexMathBlock(expression = block.expression)

                is MarkdownBlock.Table ->
                    MarkdownTable(
                        header = block.header,
                        alignments = block.alignments,
                        rows = block.rows,
                    )
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    header: List<String>,
    alignments: List<MarkdownTableAlignment>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    val rowBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp)),
        ) {
                MarkdownTableRow(
                    cells = header,
                    alignments = alignments,
                    backgroundColor = headerBackground,
                    borderColor = borderColor,
                    textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            rows.forEach { row ->
                MarkdownTableRow(
                    cells = row,
                    alignments = alignments,
                    backgroundColor = rowBackground,
                    borderColor = borderColor,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    alignments: List<MarkdownTableAlignment>,
    backgroundColor: Color,
    borderColor: Color,
    textStyle: TextStyle,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
    ) {
        cells.forEachIndexed { index, cell ->
            val cellAlignment = alignments.getOrElse(index) { MarkdownTableAlignment.Start }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(backgroundColor)
                        .border(width = 0.5.dp, color = borderColor)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                MarkdownRichText(
                    text = cell,
                    style =
                        textStyle.copy(
                            textAlign = cellAlignment.toTextAlign(),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MarkdownRichText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (containsInlineMath(text)) {
        MarkdownKatexInlineText(
            text = text,
            style = style,
            modifier = modifier,
        )
    } else {
        MarkdownInlineText(
            text = text,
            style = style,
            modifier = modifier,
        )
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    style: TextStyle,
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

internal sealed interface MarkdownBlock {
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

    data class Math(
        val expression: String,
    ) : MarkdownBlock

    data class Table(
        val header: List<String>,
        val alignments: List<MarkdownTableAlignment>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
}

internal enum class MarkdownTableAlignment {
    Start,
    Center,
    End,
}

@Suppress("CyclomaticComplexMethod")
internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        when {
            trimmed.isBlank() -> index++
            looksLikeTableStart(
                line = trimmed,
                nextLine = lines.getOrNull(index + 1)?.trim(),
            ) -> {
                val headerCells = splitTableCells(trimmed)
                val alignments = parseTableAlignments(lines[index + 1].trim())
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size) {
                    val rowTrimmed = lines[index].trim()
                    if (!looksLikeTableRow(rowTrimmed, expectedColumns = headerCells.size)) {
                        break
                    }
                    rows += splitTableCells(rowTrimmed)
                    index++
                }
                blocks +=
                    MarkdownBlock.Table(
                        header = headerCells,
                        alignments = alignments,
                        rows = rows,
                    )
            }
            trimmed == "$$" || (trimmed.startsWith("$$") && !trimmed.removePrefix("$$").contains("$$")) -> {
                val expressionLines = mutableListOf<String>()
                val openingLineRemainder = trimmed.removePrefix("$$").trim()
                if (openingLineRemainder.isNotBlank()) {
                    expressionLines += openingLineRemainder
                }
                index++
                while (index < lines.size && lines[index].trim() != "$$") {
                    expressionLines += lines[index].trim()
                    index++
                }
                if (index < lines.size && lines[index].trim() == "$$") {
                    index++
                }
                blocks += MarkdownBlock.Math(expression = expressionLines.joinToString("\n").trim())
            }
            trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4 -> {
                blocks += MarkdownBlock.Math(expression = trimmed.removePrefix("$$").removeSuffix("$$").trim())
                index++
            }
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
                while (
                    index < lines.size &&
                    shouldContinueParagraph(
                        line = lines[index],
                        nextLine = lines.getOrNull(index + 1),
                    )
                ) {
                    paragraphLines += lines[index].trim()
                    index++
                }
                blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString(separator = "\n"))
            }
        }
    }

    return blocks
}

internal fun containsInlineMath(text: String): Boolean =
    INLINE_TOKEN_REGEX.findAll(text).any { match ->
        val token = match.value
        token.startsWith("$") && token.endsWith("$") && !token.startsWith("$$")
    }

private fun shouldContinueParagraph(
    line: String,
    nextLine: String? = null,
): Boolean {
    val trimmed = line.trim()
    return trimmed.isNotBlank() &&
        !looksLikeTableStart(trimmed, nextLine?.trim()) &&
        !trimmed.startsWith("$$") &&
        !trimmed.startsWith("```") &&
        !trimmed.startsWith("#") &&
        !trimmed.startsWith("- ") &&
        !trimmed.startsWith("* ") &&
        !trimmed.startsWith("> ") &&
        !ORDERED_LIST_REGEX.matches(trimmed)
}

private fun looksLikeTableStart(
    line: String,
    nextLine: String?,
): Boolean {
    if (nextLine.isNullOrBlank()) {
        return false
    }
    val headerCells = splitTableCells(line)
    if (headerCells.size < 2) {
        return false
    }
    return isTableSeparatorRow(nextLine, expectedColumns = headerCells.size)
}

private fun looksLikeTableRow(
    line: String,
    expectedColumns: Int,
): Boolean {
    if (line.isBlank()) {
        return false
    }
    val cells = splitTableCells(line)
    return cells.size == expectedColumns
}

private fun splitTableCells(line: String): List<String> {
    val normalized =
        line
            .trim()
            .removePrefix("|")
            .removeSuffix("|")
    return normalized
        .split('|')
        .map { it.trim() }
}

private fun isTableSeparatorRow(
    line: String,
    expectedColumns: Int,
): Boolean {
    val cells = splitTableCells(line)
    if (cells.size != expectedColumns) {
        return false
    }
    return cells.all { cell ->
        cell.isNotBlank() && TABLE_SEPARATOR_CELL_REGEX.matches(cell)
    }
}

private fun parseTableAlignments(line: String): List<MarkdownTableAlignment> =
    splitTableCells(line).map { cell ->
        when {
            cell.startsWith(":") && cell.endsWith(":") -> MarkdownTableAlignment.Center
            cell.endsWith(":") -> MarkdownTableAlignment.End
            else -> MarkdownTableAlignment.Start
        }
    }

private fun MarkdownTableAlignment.toTextAlign() =
    when (this) {
        MarkdownTableAlignment.Center -> androidx.compose.ui.text.style.TextAlign.Center
        MarkdownTableAlignment.End -> androidx.compose.ui.text.style.TextAlign.End
        MarkdownTableAlignment.Start -> androidx.compose.ui.text.style.TextAlign.Start
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

            else -> builder.append(token)
        }

        currentIndex = match.range.last + 1
    }

    if (currentIndex < text.length) {
        builder.append(text.substring(currentIndex))
    }

    return builder.toAnnotatedString()
}

private val ORDERED_LIST_REGEX = Regex("""(\d+)\.\s+(.*)""")
internal val LINK_REGEX = Regex("""\[(.*?)]\((.*?)\)""")
internal val INLINE_TOKEN_REGEX =
    Regex("""(\$\$[^$]+\$\$|\$[^$\n]+\$|\*\*[^*]+\*\*|\*[^*]+\*|~~[^~]+~~|`[^`]+`|\[[^\]]+]\([^)]+\))""")
private val TABLE_SEPARATOR_CELL_REGEX = Regex(""":?-{3,}:?""")
