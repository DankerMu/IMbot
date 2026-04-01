package com.imbot.android.ui.detail

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextMathTest {
    @Test
    fun `inline math detection ignores code spans`() {
        assertTrue(containsInlineMath("行内公式：${'$'}E=mc^2${'$'}"))
        assertFalse(containsInlineMath("环境变量写法：`${'$'}HOME`"))
    }

    @Test
    fun `inline markdown html preserves math delimiters and formatting`() {
        val html =
            buildMarkdownInlineHtml(
                "公式 ${'$'}E=mc^2${'$'} 与 **bold**、[链接](https://example.com)、`code`",
            )

        assertTrue(html.contains("${'$'}E=mc^2${'$'}"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("""<a href="https://example.com">链接</a>"""))
        assertTrue(html.contains("<code>code</code>"))
    }

    @Test
    fun `katex html document references bundled offline assets`() {
        val document =
            buildKatexDocumentHtml(
                bodyHtml = "${'$'}E=mc^2${'$'}",
                textColor = "#111111",
                linkColor = "#0066CC",
                inlineCodeBackground = "#EFEFEF",
                fontSizePx = 16f,
                lineHeightPx = 22f,
                fontWeight = 400,
                fontStyle = FontStyle.Normal,
                textAlign = TextAlign.Start,
            )

        assertTrue(document.contains("href=\"katex/katex.min.css\""))
        assertTrue(document.contains("src=\"katex/katex.min.js\""))
        assertTrue(document.contains("src=\"katex/auto-render.min.js\""))
        assertTrue(document.contains("renderMathInElement"))
    }

    @Test
    fun `math blocks are parsed separately from paragraphs`() {
        val blocks =
            parseMarkdownBlocks(
                """
                行内公式：${'$'}E=mc^2${'$'}

                ${'$'}${'$'}\int_0^1 x^2\,dx = \frac{1}{3}${'$'}${'$'}
                """.trimIndent(),
            )

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.Math)
        assertEquals(
            "\\int_0^1 x^2\\,dx = \\frac{1}{3}",
            (blocks[1] as MarkdownBlock.Math).expression,
        )
    }

    @Test
    fun `gfm tables are parsed into table blocks`() {
        val blocks =
            parseMarkdownBlocks(
                """
                | Col A | Col B |
                | :--- | ---: |
                | 1 | 2 |
                | 3 | 4 |
                """.trimIndent(),
            )

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Table)

        val table = blocks[0] as MarkdownBlock.Table
        assertEquals(listOf("Col A", "Col B"), table.header)
        assertEquals(
            listOf(MarkdownTableAlignment.Start, MarkdownTableAlignment.End),
            table.alignments,
        )
        assertEquals(
            listOf(
                listOf("1", "2"),
                listOf("3", "4"),
            ),
            table.rows,
        )
    }
}
