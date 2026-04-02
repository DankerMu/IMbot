package com.imbot.android.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolCategoryTest {
    @Test
    fun `classifyTool maps known categories and falls back to other`() {
        assertEquals(ToolCategory.BASH, classifyTool("Bash"))
        assertEquals(ToolCategory.BASH, classifyTool("bash"))
        assertEquals(ToolCategory.READ, classifyTool("Read"))
        assertEquals(ToolCategory.WRITE, classifyTool("Write"))
        assertEquals(ToolCategory.WRITE, classifyTool("Edit"))
        assertEquals(ToolCategory.WRITE, classifyTool("MultiEdit"))
        assertEquals(ToolCategory.SEARCH, classifyTool("Grep"))
        assertEquals(ToolCategory.SEARCH, classifyTool("Glob"))
        assertEquals(ToolCategory.SEARCH, classifyTool("WebSearch"))
        assertEquals(ToolCategory.OTHER, classifyTool("AskUserQuestion"))
        assertEquals(ToolCategory.OTHER, classifyTool("Agent"))
        assertEquals(ToolCategory.OTHER, classifyTool(""))
    }
}
