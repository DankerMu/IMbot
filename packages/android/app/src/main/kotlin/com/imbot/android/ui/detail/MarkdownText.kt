@file:Suppress("FunctionName", "TooManyFunctions")

package com.imbot.android.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imbot.android.ui.components.CodeBlock
import com.imbot.android.ui.theme.CodeFontFamily
import com.imbot.android.ui.theme.LocalUseDarkTheme

internal val MarkdownParagraphLineHeight = 24.sp
internal val MarkdownParagraphSpacing = 12.dp
private val MarkdownListBlockSpacing = 8.dp
private val MarkdownListItemSpacing = 6.dp
private val MarkdownQuoteIndentStep = 16.dp
private val MarkdownTableOuterMargin = 12.dp
private val MarkdownTableCellHorizontalPadding = 12.dp
private val MarkdownTableCellVerticalPadding = 8.dp
private val MarkdownTableMinColumnWidth = 140.dp
private val MarkdownInlineCodeCornerRadius = 4.dp
private val MarkdownInlineCodeHorizontalPadding = 4.dp
private val MarkdownInlineCodeVerticalPadding = 2.dp
private const val MAX_ROUNDED_INLINE_CODE_SPANS = 50
private const val URL_ANNOTATION_TAG = "URL"
private const val INLINE_CODE_ANNOTATION_TAG = "INLINE_CODE"

internal data class MarkdownHeadingStyleSpec(
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val topPadding: Dp,
    val bottomPadding: Dp,
)

internal fun markdownHeadingStyle(level: Int): MarkdownHeadingStyleSpec =
    when (level.coerceIn(1, 6)) {
        1 ->
            MarkdownHeadingStyleSpec(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                topPadding = 16.dp,
                bottomPadding = 16.dp,
            )

        2 ->
            MarkdownHeadingStyleSpec(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                topPadding = 20.dp,
                bottomPadding = 12.dp,
            )

        3 ->
            MarkdownHeadingStyleSpec(
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                topPadding = 16.dp,
                bottomPadding = 8.dp,
            )

        else ->
            MarkdownHeadingStyleSpec(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                topPadding = 12.dp,
                bottomPadding = 4.dp,
            )
    }

private fun markdownHeadingLineHeight(level: Int): TextUnit =
    when (level.coerceIn(1, 6)) {
        1 -> 30.sp
        2 -> 26.sp
        3 -> 22.sp
        else -> 20.sp
    }

internal fun markdownBulletForLevel(level: Int): String =
    when (level) {
        0 -> "●"
        1 -> "○"
        2 -> "■"
        else -> "▪"
    }

internal fun blockquoteBorderAlpha(level: Int): Float =
    when (level) {
        0 -> 0.4f
        1 -> 0.25f
        2 -> 0.15f
        else -> 0.1f
    }

internal fun isStripedTableRow(rowIndex: Int): Boolean = rowIndex % 2 == 1

internal fun markdownBlockBottomPadding(
    index: Int,
    blocks: List<MarkdownBlock>,
    defaultPadding: Dp,
): Dp = if (index == blocks.lastIndex) 0.dp else defaultPadding

internal fun markdownInlineCodeBackground(useDarkTheme: Boolean): Color =
    if (useDarkTheme) {
        Color(0xFF2D333B)
    } else {
        Color(0xFFEFF1F3)
    }

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.Paragraph ->
                    MarkdownRichText(
                        text = block.text,
                        modifier =
                            Modifier.padding(
                                bottom = markdownBlockBottomPadding(index, blocks, MarkdownParagraphSpacing),
                            ),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = MarkdownParagraphLineHeight,
                                letterSpacing = 0.2.sp,
                                color = contentColor,
                            ),
                    )

                is MarkdownBlock.Heading -> {
                    val headingStyle = markdownHeadingStyle(block.level)
                    MarkdownRichText(
                        text = block.text,
                        modifier =
                            Modifier.padding(
                                top = headingStyle.topPadding,
                                bottom = markdownBlockBottomPadding(index, blocks, headingStyle.bottomPadding),
                            ),
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontSize = headingStyle.fontSize,
                                lineHeight = markdownHeadingLineHeight(block.level),
                                fontWeight = headingStyle.fontWeight,
                                color = contentColor,
                            ),
                    )
                }

                is MarkdownBlock.ListItem ->
                    MarkdownListItem(
                        block = block,
                        modifier =
                            Modifier.padding(
                                top = listTopPadding(index, blocks),
                                bottom = listBottomPadding(index, blocks),
                            ),
                        contentColor = contentColor,
                    )

                is MarkdownBlock.Quote ->
                    MarkdownQuote(
                        block = block,
                        modifier =
                            Modifier.padding(
                                start = MarkdownQuoteIndentStep * block.level.toFloat(),
                                top = 8.dp,
                                bottom = markdownBlockBottomPadding(index, blocks, 8.dp),
                            ),
                        contentColor = contentColor,
                    )

                is MarkdownBlock.Code ->
                    CodeBlock(
                        language = block.language,
                        code = block.code,
                        modifier =
                            Modifier.padding(
                                top = 8.dp,
                                bottom = markdownBlockBottomPadding(index, blocks, 8.dp),
                            ),
                    )

                is MarkdownBlock.Math ->
                    MarkdownKatexMathBlock(
                        expression = block.expression,
                        modifier =
                            Modifier.padding(
                                top = 8.dp,
                                bottom = markdownBlockBottomPadding(index, blocks, 8.dp),
                            ),
                    )

                is MarkdownBlock.Table ->
                    MarkdownTable(
                        header = block.header,
                        alignments = block.alignments,
                        rows = block.rows,
                        modifier =
                            Modifier.padding(
                                top = MarkdownTableOuterMargin,
                                bottom = markdownBlockBottomPadding(index, blocks, MarkdownTableOuterMargin),
                            ),
                        contentColor = contentColor,
                    )
            }
        }
    }
}

@Composable
private fun MarkdownListItem(
    block: MarkdownBlock.ListItem,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val markerColor = contentColor.copy(alpha = 0.6f)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = (20 * block.level).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (block.ordered) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text = "${block.ordinal}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = markerColor,
                )
            }
        } else {
            Text(
                text = markdownBulletForLevel(block.level),
                style = MaterialTheme.typography.bodyMedium,
                color = markerColor,
            )
        }
        MarkdownRichText(
            text = block.text,
            modifier = Modifier.weight(1f),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MarkdownParagraphLineHeight,
                    letterSpacing = 0.2.sp,
                    color = contentColor,
                ),
        )
    }
}

@Composable
private fun MarkdownQuote(
    block: MarkdownBlock.Quote,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = colorScheme.primary.copy(alpha = blockquoteBorderAlpha(block.level))

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .height(IntrinsicSize.Min)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(borderColor),
        )
        MarkdownRichText(
            text = block.text,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 14.dp),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = MarkdownParagraphLineHeight,
                    letterSpacing = 0.2.sp,
                    color = contentColor,
                ),
        )
    }
}

@Composable
private fun MarkdownTable(
    header: List<String>,
    alignments: List<MarkdownTableAlignment>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant
    val columnCount = maxOf(header.size, rows.maxOfOrNull(List<String>::size) ?: 0)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val minTableWidth = MarkdownTableMinColumnWidth * columnCount
        val tableWidth = if (maxWidth > minTableWidth) maxWidth else minTableWidth

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
        ) {
            Column(
                modifier =
                    Modifier
                        .width(tableWidth)
                        .clip(MaterialTheme.shapes.medium)
                        .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.medium),
            ) {
                MarkdownTableRow(
                    cells = header,
                    columnCount = columnCount,
                    alignments = alignments,
                    backgroundColor = headerBackground,
                    borderColor = borderColor,
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                        ),
                )
                rows.forEachIndexed { rowIndex, row ->
                    MarkdownTableRow(
                        cells = row,
                        columnCount = columnCount,
                        alignments = alignments,
                        backgroundColor =
                            if (isStripedTableRow(rowIndex)) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        borderColor = borderColor,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    columnCount: Int,
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
        repeat(columnCount) { index ->
            val cell = cells.getOrElse(index) { "" }
            val cellAlignment = alignments.getOrElse(index) { MarkdownTableAlignment.Start }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = MarkdownTableMinColumnWidth)
                        .fillMaxHeight()
                        .background(backgroundColor)
                        .border(width = 1.dp, color = borderColor)
                        .padding(
                            horizontal = MarkdownTableCellHorizontalPadding,
                            vertical = MarkdownTableCellVerticalPadding,
                        ),
            ) {
                MarkdownRichText(
                    text = cell,
                    style = textStyle.copy(textAlign = cellAlignment.toTextAlign()),
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
    val inlineCodeBackground = markdownInlineCodeBackground(LocalUseDarkTheme.current)
    val linkColor = MaterialTheme.colorScheme.primary
    val inlineCodeTextColor = style.color.takeOrElse { MaterialTheme.colorScheme.onSurface }
    val useRoundedInlineCodeBackgrounds =
        remember(text) { countInlineCodeSpans(text) <= MAX_ROUNDED_INLINE_CODE_SPANS }
    val annotated =
        remember(
            text,
            inlineCodeBackground,
            linkColor,
            inlineCodeTextColor,
            useRoundedInlineCodeBackgrounds,
        ) {
            buildMarkdownAnnotatedString(
                text = text,
                linkColor = linkColor,
                inlineCodeTextColor = inlineCodeTextColor,
                inlineCodeBackground = inlineCodeBackground,
                useRoundedInlineCodeBackgrounds = useRoundedInlineCodeBackgrounds,
            )
        }
    var inlineCodeBackgroundRects by
        remember(annotated, useRoundedInlineCodeBackgrounds) {
            mutableStateOf(emptyList<Rect>())
        }
    val inlineCodeBackgroundModifier =
        if (useRoundedInlineCodeBackgrounds) {
            Modifier.drawBehind {
                drawInlineCodeBackgrounds(
                    rects = inlineCodeBackgroundRects,
                    inlineCodeBackground = inlineCodeBackground,
                )
            }
        } else {
            Modifier
        }

    ClickableText(
        text = annotated,
        modifier = modifier.then(inlineCodeBackgroundModifier),
        style = style,
        onTextLayout = { layoutResult ->
            inlineCodeBackgroundRects =
                if (useRoundedInlineCodeBackgrounds) {
                    buildInlineCodeBackgroundRects(
                        annotated = annotated,
                        textLayoutResult = layoutResult,
                    )
                } else {
                    emptyList()
                }
        },
        onClick = { offset ->
            annotated.getStringAnnotations(tag = URL_ANNOTATION_TAG, start = offset, end = offset)
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

    data class ListItem(
        val text: String,
        val level: Int,
        val ordered: Boolean,
        val ordinal: Int? = null,
    ) : MarkdownBlock

    data class Quote(
        val text: String,
        val level: Int,
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
                val (table, nextIndex) = parseTableBlock(lines, index, trimmed)
                blocks += table
                index = nextIndex
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
            UNORDERED_LIST_REGEX.matches(line) -> {
                val match = UNORDERED_LIST_REGEX.matchEntire(line)
                blocks +=
                    MarkdownBlock.ListItem(
                        text = match?.groupValues?.get(3).orEmpty(),
                        level = markdownIndentLevel(match?.groupValues?.get(1).orEmpty()),
                        ordered = false,
                    )
                index++
            }
            ORDERED_LIST_REGEX.matches(line) -> {
                val match = ORDERED_LIST_REGEX.matchEntire(line)
                blocks +=
                    MarkdownBlock.ListItem(
                        text = match?.groupValues?.get(3).orEmpty(),
                        level = markdownIndentLevel(match?.groupValues?.get(1).orEmpty()),
                        ordered = true,
                        ordinal = match?.groupValues?.get(2)?.toIntOrNull(),
                    )
                index++
            }
            QUOTE_REGEX.matches(line) -> {
                val match = QUOTE_REGEX.matchEntire(line)
                blocks +=
                    MarkdownBlock.Quote(
                        text = match?.groupValues?.get(3).orEmpty().trim(),
                        level = (match?.groupValues?.get(2)?.length ?: 1).minus(1).coerceAtLeast(0),
                    )
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

private fun parseTableBlock(
    lines: List<String>,
    startIndex: Int,
    headerLine: String,
): Pair<MarkdownBlock.Table, Int> {
    val headerCells = splitTableCells(headerLine)
    val alignments = parseTableAlignments(lines[startIndex + 1].trim())
    var index = startIndex + 2
    val rows = mutableListOf<List<String>>()
    while (index < lines.size) {
        val rowTrimmed = lines[index].trim()
        if (!looksLikeTableRow(rowTrimmed, expectedColumns = headerCells.size)) break
        rows += splitTableCells(rowTrimmed)
        index++
    }
    return MarkdownBlock.Table(header = headerCells, alignments = alignments, rows = rows) to index
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
        !UNORDERED_LIST_REGEX.matches(line) &&
        !ORDERED_LIST_REGEX.matches(line) &&
        !QUOTE_REGEX.matches(line)
}

private fun looksLikeTableStart(
    line: String,
    nextLine: String?,
): Boolean =
    !nextLine.isNullOrBlank() &&
        splitTableCells(line).let { cells ->
            cells.size >= 2 && isTableSeparatorRow(nextLine, expectedColumns = cells.size)
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

private fun listTopPadding(
    index: Int,
    blocks: List<MarkdownBlock>,
): Dp =
    if (index == 0 || blocks[index - 1] !is MarkdownBlock.ListItem) {
        MarkdownListBlockSpacing
    } else {
        MarkdownListItemSpacing / 2
    }

private fun listBottomPadding(
    index: Int,
    blocks: List<MarkdownBlock>,
): Dp =
    if (index == blocks.lastIndex) {
        0.dp
    } else if (blocks[index + 1] !is MarkdownBlock.ListItem) {
        MarkdownListBlockSpacing
    } else {
        MarkdownListItemSpacing / 2
    }

private fun markdownIndentLevel(indent: String): Int =
    indent
        .replace("\t", "    ")
        .length
        .div(2)
        .coerceAtLeast(0)

@Suppress("CyclomaticComplexMethod")
private fun buildMarkdownAnnotatedString(
    text: String,
    linkColor: Color,
    inlineCodeTextColor: Color,
    inlineCodeBackground: Color,
    useRoundedInlineCodeBackgrounds: Boolean,
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

            token.startsWith("`") && token.endsWith("`") -> {
                val inlineCode = token.removePrefix("`").removeSuffix("`")
                val start = builder.length
                val inlineCodeStyle =
                    if (useRoundedInlineCodeBackgrounds) {
                        SpanStyle(
                            fontFamily = CodeFontFamily,
                            fontSize = 13.sp,
                            color = inlineCodeTextColor,
                        )
                    } else {
                        SpanStyle(
                            fontFamily = CodeFontFamily,
                            fontSize = 13.sp,
                            color = inlineCodeTextColor,
                            background = inlineCodeBackground,
                        )
                    }
                builder.withStyle(inlineCodeStyle) {
                    append(inlineCode)
                }
                if (useRoundedInlineCodeBackgrounds) {
                    builder.addStringAnnotation(
                        tag = INLINE_CODE_ANNOTATION_TAG,
                        annotation = INLINE_CODE_ANNOTATION_TAG,
                        start = start,
                        end = builder.length,
                    )
                }
            }

            token.startsWith("[") -> {
                val linkMatch = LINK_REGEX.matchEntire(token)
                val label = linkMatch?.groupValues?.get(1).orEmpty()
                val url = linkMatch?.groupValues?.get(2).orEmpty()
                if (label.isNotBlank() && url.isNotBlank()) {
                    builder.pushStringAnnotation(tag = URL_ANNOTATION_TAG, annotation = url)
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

private fun countInlineCodeSpans(text: String): Int =
    INLINE_TOKEN_REGEX.findAll(text).count { match ->
        val token = match.value
        token.startsWith("`") && token.endsWith("`")
    }

private fun buildInlineCodeBackgroundRects(
    annotated: AnnotatedString,
    textLayoutResult: TextLayoutResult,
): List<Rect> =
    annotated
        .getStringAnnotations(
            tag = INLINE_CODE_ANNOTATION_TAG,
            start = 0,
            end = annotated.length,
        )
        .flatMap { annotation ->
            buildInlineCodeRects(
                text = annotated.text,
                textLayoutResult = textLayoutResult,
                start = annotation.start,
                end = annotation.end,
            )
        }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInlineCodeBackgrounds(
    rects: List<Rect>,
    inlineCodeBackground: Color,
) {
    val horizontalPadding = MarkdownInlineCodeHorizontalPadding.toPx()
    val verticalPadding = MarkdownInlineCodeVerticalPadding.toPx()
    val cornerRadius = MarkdownInlineCodeCornerRadius.toPx()

    rects.forEach { rect ->
        drawRoundRect(
            color = inlineCodeBackground,
            topLeft = Offset(rect.left - horizontalPadding, rect.top - verticalPadding),
            size =
                Size(
                    width = rect.width + (horizontalPadding * 2),
                    height = rect.height + (verticalPadding * 2),
                ),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        )
    }
}

private fun buildInlineCodeRects(
    text: String,
    textLayoutResult: TextLayoutResult,
    start: Int,
    end: Int,
): List<Rect> {
    if (start >= end) {
        return emptyList()
    }

    val rects = mutableListOf<Rect>()
    var currentLine = -1
    var currentRect: Rect? = null

    for (offset in start until end) {
        val character = text[offset]
        val boundingBox = textLayoutResult.getBoundingBox(offset)
        val shouldSkip = character == '\n' || (boundingBox.width == 0f && boundingBox.height == 0f)
        if (shouldSkip) {
            continue
        }
        val lineIndex = textLayoutResult.getLineForOffset(offset)
        currentRect =
            if (lineIndex != currentLine || currentRect == null) {
                currentRect?.let(rects::add)
                currentLine = lineIndex
                boundingBox
            } else {
                Rect(
                    left = minOf(currentRect.left, boundingBox.left),
                    top = minOf(currentRect.top, boundingBox.top),
                    right = maxOf(currentRect.right, boundingBox.right),
                    bottom = maxOf(currentRect.bottom, boundingBox.bottom),
                )
            }
    }

    currentRect?.let(rects::add)
    return rects
}

private val UNORDERED_LIST_REGEX = Regex("""^(\s*)([-*])\s+(.*)$""")
private val ORDERED_LIST_REGEX = Regex("""^(\s*)(\d+)\.\s+(.*)$""")
private val QUOTE_REGEX = Regex("""^(\s*)(>+)\s?(.*)$""")
internal val LINK_REGEX = Regex("""\[(.*?)]\((.*?)\)""")
internal val INLINE_TOKEN_REGEX =
    Regex("""(\$\$[^$]+\$\$|\$[^$\n]+\$|\*\*[^*]+\*\*|\*[^*]+\*|~~[^~]+~~|`[^`]+`|\[[^\]]+]\([^)]+\))""")
private val TABLE_SEPARATOR_CELL_REGEX = Regex(""":?-{3,}:?""")
