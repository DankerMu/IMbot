package com.imbot.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlockTest {
    @Test
    fun `10 line code block does not collapse`() {
        val state = resolveCodeBlockDisplayState(code = numberedCode(10), expanded = false)

        assertFalse(state.isCollapsible)
        assertFalse(state.isCollapsed)
        assertEquals(10, state.totalLines)
        assertEquals(10, codeLineCount(state.displayedCode))
        assertEquals(null, state.toggleLabel)
    }

    @Test
    fun `25 line code block defaults to collapsed with 10 visible lines`() {
        val state = resolveCodeBlockDisplayState(code = numberedCode(25), expanded = false)

        assertTrue(state.isCollapsible)
        assertTrue(state.isCollapsed)
        assertEquals(25, state.totalLines)
        assertEquals(10, codeLineCount(state.displayedCode))
        assertEquals("展开 (25 行)", state.toggleLabel)
    }

    @Test
    fun `expanded code block shows all lines and collapse label`() {
        val state = resolveCodeBlockDisplayState(code = numberedCode(25), expanded = true)

        assertTrue(state.isCollapsible)
        assertFalse(state.isCollapsed)
        assertEquals(25, codeLineCount(state.displayedCode))
        assertEquals("收起", state.toggleLabel)
    }
}

private fun numberedCode(lineCount: Int): String {
    return (1..lineCount).joinToString(separator = "\n") { index -> "line $index" }
}
