package com.imbot.android.ui.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imbot.android.ui.components.buildCodeLineNumbers
import com.imbot.android.ui.components.extractCodeLanguageLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRenderingTest {
    @Test
    fun `code block language extraction uses first normalized token`() {
        assertEquals("kotlin", extractCodeLanguageLabel("kotlin"))
        assertEquals("kotlin", extractCodeLanguageLabel("KOTLIN"))
        assertNull(extractCodeLanguageLabel(""))
        assertEquals("kotlin", extractCodeLanguageLabel("kotlin extra"))
        assertEquals("javascript", extractCodeLanguageLabel("javascript"))
    }

    @Test
    fun `code line numbers skip empty code and large blocks`() {
        assertTrue(buildCodeLineNumbers("").isEmpty())
        assertEquals(listOf("1"), buildCodeLineNumbers("val ready = true"))

        val maxGutterCode = (1..500).joinToString(separator = "\n") { "line $it" }
        val maxGutterLineNumbers = buildCodeLineNumbers(maxGutterCode)
        assertEquals(500, maxGutterLineNumbers.size)
        assertEquals("500", maxGutterLineNumbers.last())

        val oversizedCode = (1..501).joinToString(separator = "\n") { "line $it" }
        assertTrue(buildCodeLineNumbers(oversizedCode).isEmpty())
    }

    @Test
    fun `list bullet mapping follows nesting level`() {
        assertEquals("●", markdownBulletForLevel(0))
        assertEquals("○", markdownBulletForLevel(1))
        assertEquals("■", markdownBulletForLevel(2))
        assertEquals("▪", markdownBulletForLevel(3))
        assertEquals("▪", markdownBulletForLevel(5))
    }

    @Test
    fun `blockquote border alpha decreases and clamps`() {
        assertEquals(0.4f, blockquoteBorderAlpha(0))
        assertEquals(0.25f, blockquoteBorderAlpha(1))
        assertEquals(0.15f, blockquoteBorderAlpha(2))
        assertEquals(0.1f, blockquoteBorderAlpha(3))
    }

    @Test
    fun `table zebra striping alternates data rows`() {
        assertEquals(false, isStripedTableRow(0))
        assertEquals(true, isStripedTableRow(1))
        assertEquals(false, isStripedTableRow(2))
        assertEquals(true, isStripedTableRow(3))
    }

    @Test
    fun `heading style specs match typography contract`() {
        val h1 = markdownHeadingStyle(1)
        val h2 = markdownHeadingStyle(2)
        val h3 = markdownHeadingStyle(3)
        val h4 = markdownHeadingStyle(4)

        assertEquals(24.sp, h1.fontSize)
        assertEquals(FontWeight.SemiBold, h1.fontWeight)
        assertEquals(16.dp, h1.topPadding)
        assertEquals(16.dp, h1.bottomPadding)

        assertEquals(20.sp, h2.fontSize)
        assertEquals(FontWeight.SemiBold, h2.fontWeight)
        assertEquals(20.dp, h2.topPadding)
        assertEquals(12.dp, h2.bottomPadding)

        assertEquals(17.sp, h3.fontSize)
        assertEquals(FontWeight.Medium, h3.fontWeight)
        assertEquals(16.dp, h3.topPadding)
        assertEquals(8.dp, h3.bottomPadding)

        assertEquals(15.sp, h4.fontSize)
        assertEquals(FontWeight.Medium, h4.fontWeight)
        assertEquals(12.dp, h4.topPadding)
        assertEquals(4.dp, h4.bottomPadding)
    }

    @Test
    fun `paragraph and inline code style constants match spec`() {
        assertEquals(24.sp, MarkdownParagraphLineHeight)
        assertEquals(12.dp, MarkdownParagraphSpacing)
        assertEquals(Color(0xFFEFF1F3), markdownInlineCodeBackground(useDarkTheme = false))
        assertEquals(Color(0xFF2D333B), markdownInlineCodeBackground(useDarkTheme = true))
    }

    @Test
    fun `last markdown block does not keep trailing bottom padding`() {
        val blocks =
            listOf(
                MarkdownBlock.Paragraph("first"),
                MarkdownBlock.Paragraph("last"),
            )

        assertEquals(12.dp, markdownBlockBottomPadding(0, blocks, MarkdownParagraphSpacing))
        assertEquals(0.dp, markdownBlockBottomPadding(1, blocks, MarkdownParagraphSpacing))
    }
}
