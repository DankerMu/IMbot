@file:Suppress("MaxLineLength")

package com.imbot.android.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallUtilsTest {
    @Test
    fun `extractBashCommand handles json missing values and raw text`() {
        assertEquals("ls -la", extractBashCommand("""{"command":"ls -la"}"""))
        assertNull(extractBashCommand(null))
        assertEquals("not json", extractBashCommand("not json"))
    }

    @Test
    fun `extractFilePath prefers file path keys and shortens long paths`() {
        assertEquals(
            ".../project/src/index.ts",
            extractFilePath("""{"file_path":"/home/user/project/src/index.ts"}"""),
        )
        assertEquals("short.txt", extractFilePath("""{"path":"short.txt"}"""))
        assertNull(extractFilePath(null))
    }

    @Test
    fun `extractSearchPattern handles pattern and query fields`() {
        assertEquals("class Foo", extractSearchPattern("""{"pattern":"class Foo"}"""))
        assertEquals("react hooks", extractSearchPattern("""{"query":"react hooks"}"""))
    }

    @Test
    fun `extractJsonField returns requested string field`() {
        assertEquals("new text", extractJsonField("""{"new_string":"new text"}""", "new_string"))
        assertNull(extractJsonField("""{"new_string":"new text"}""", "missing"))
    }

    @Test
    fun `buildToolSummary formats details by category`() {
        assertEquals(
            "Bash · $ npm test",
            buildToolSummary(
                category = ToolCategory.BASH,
                item = toolCall(toolName = "Bash", args = """{"command":"npm test"}"""),
            ),
        )
        assertEquals(
            "Read · src/index.ts",
            buildToolSummary(
                category = ToolCategory.READ,
                item = toolCall(toolName = "Read", args = """{"file_path":"src/index.ts"}"""),
            ),
        )
        assertEquals(
            "Lsp",
            buildToolSummary(
                category = ToolCategory.OTHER,
                item = toolCall(toolName = "LSP", title = "", args = null),
            ),
        )
    }
}

private fun toolCall(
    toolName: String,
    title: String = toolName,
    args: String?,
) = MessageItem.ToolCall(
    callId = "call-1",
    toolName = toolName,
    title = title,
    args = args,
    result = null,
    isRunning = false,
)
