@file:Suppress("MaxLineLength")

package com.imbot.android.ui.detail

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallUtilsTest {
    @Test
    fun `extractBashCommand handles json missing values and raw text`() {
        assertEquals("ls -la", extractBashCommand("""{"command":"ls -la"}"""))
        assertNull(extractBashCommand(null))
        assertEquals("not json", extractBashCommand("not json"))
    }

    @Test
    fun `extractBashCommand truncates raw fallback to 80 chars`() {
        val rawCommand = "x".repeat(81)

        assertEquals("x".repeat(80), extractBashCommand(rawCommand))
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
        assertEquals("https://example.com", extractSearchPattern("""{"url":"https://example.com"}"""))
    }

    @Test
    fun `extractSkillName parses skill field from JSON args`() {
        assertEquals("commit", extractSkillName("""{"skill":"commit","args":"-m 'fix'"}"""))
    }

    @Test
    fun `extractSkillName returns null for non-JSON or missing skill`() {
        assertNull(extractSkillName("not json"))
        assertNull(extractSkillName("""{"other":"value"}"""))
        assertNull(extractSkillName(null))
        assertNull(extractSkillName(""))
    }

    @Test
    fun `extractJsonField returns requested string field`() {
        assertNull(extractJsonField(null, "field"))
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
        assertEquals(
            "Search · TODO",
            buildToolSummary(
                category = ToolCategory.SEARCH,
                item = toolCall(toolName = "search", args = """{"pattern":"TODO"}"""),
            ),
        )
        assertEquals(
            "Skill · /commit",
            buildToolSummary(
                category = ToolCategory.SKILL,
                item = toolCall(toolName = "Skill", args = """{"skill":"commit"}"""),
            ),
        )
    }

    @Test
    fun `isToolCallError detects payload and common error prefixes`() {
        assertTrue(isToolCallError(null, null))
        assertTrue(isToolCallError("success", JSONObject("""{"error":"old_string not found"}""")))
        assertTrue(isToolCallError("error: command failed", null))
        assertTrue(isToolCallError("ENOENT: no such file", null))
        assertTrue(isToolCallError("EPERM: access denied", null))
        assertTrue(isToolCallError("permission denied", null))
        assertFalse(isToolCallError("success", null))
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
