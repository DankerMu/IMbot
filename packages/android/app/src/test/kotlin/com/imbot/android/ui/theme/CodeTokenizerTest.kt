package com.imbot.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CodeTokenizerTest {
    @Before
    fun setUp() {
        CodeTokenizer.clearCache()
    }

    @Test
    fun `kotlin keywords are recognized`() {
        val code = "class Greeter { fun greet() { val answer = 42 } }"
        val tokens = CodeTokenizer.tokenize(code, "kotlin")

        assertContainsToken(tokens, code, "class", CodeTokenType.Keyword)
        assertContainsToken(tokens, code, "fun", CodeTokenType.Keyword)
        assertContainsToken(tokens, code, "val", CodeTokenType.Keyword)
    }

    @Test
    fun `typescript keywords are recognized`() {
        val code = "import { foo } from 'lib'; const greet = function bar() { return foo }"
        val tokens = CodeTokenizer.tokenize(code, "typescript")

        assertContainsToken(tokens, code, "import", CodeTokenType.Keyword)
        assertContainsToken(tokens, code, "const", CodeTokenType.Keyword)
        assertContainsToken(tokens, code, "function", CodeTokenType.Keyword)
    }

    @Test
    fun `python keywords are recognized`() {
        val code = "import os\nclass Greeter:\n    def greet(self):\n        return True"
        val tokens = CodeTokenizer.tokenize(code, "python")

        assertContainsToken(tokens, code, "import", CodeTokenType.Keyword)
        assertContainsToken(tokens, code, "class", CodeTokenType.Keyword)
        assertContainsToken(tokens, code, "def", CodeTokenType.Keyword)
    }

    @Test
    fun `json keys strings numbers and booleans are highlighted`() {
        val code = """{"name":"IMbot","retries":3,"enabled":true}"""
        val tokens = CodeTokenizer.tokenize(code, "json")

        assertContainsToken(tokens, code, "\"name\"", CodeTokenType.Property)
        assertContainsToken(tokens, code, "\"IMbot\"", CodeTokenType.String)
        assertContainsToken(tokens, code, "3", CodeTokenType.Number)
        assertContainsToken(tokens, code, "true", CodeTokenType.Keyword)
    }

    @Test
    fun `single double and template strings are detected`() {
        val code = "const a = 'one'; const b = \"two\"; const c = `three ${'$'}{value}`"
        val tokens = CodeTokenizer.tokenize(code, "typescript")

        assertContainsToken(tokens, code, "'one'", CodeTokenType.String)
        assertContainsToken(tokens, code, "\"two\"", CodeTokenType.String)
        assertContainsToken(tokens, code, "`three ${'$'}{value}`", CodeTokenType.String)
    }

    @Test
    fun `comments are detected across supported styles`() {
        val code = "// one\n/* two */\n# three"
        val tsTokens = CodeTokenizer.tokenize(code, "typescript")
        val pyTokens = CodeTokenizer.tokenize(code, "python")

        assertContainsToken(tsTokens, code, "// one", CodeTokenType.Comment)
        assertContainsToken(tsTokens, code, "/* two */", CodeTokenType.Comment)
        assertContainsToken(pyTokens, code, "# three", CodeTokenType.Comment)
    }

    @Test
    fun `empty code returns no tokens`() {
        assertTrue(CodeTokenizer.tokenize("", "kotlin").isEmpty())
    }

    @Test
    fun `unknown or null language returns plain text`() {
        assertTrue(CodeTokenizer.tokenize("const a = 1", "ruby").isEmpty())
        assertTrue(CodeTokenizer.tokenize("const a = 1", null).isEmpty())
    }

    @Test
    fun `escaped quotes stay inside one string token`() {
        val code = """val quote = "he said \"hello\"""""
        val tokens = CodeTokenizer.tokenize(code, "kotlin")

        assertContainsToken(tokens, code, "\"he said \\\"hello\\\"\"", CodeTokenType.String)
    }

    @Test
    fun `multi line tokens preserve full span`() {
        val code = "\"\"\"line 1\nline 2\"\"\""
        val tokens = CodeTokenizer.tokenize(code, "kotlin")

        val token = tokens.single { it.type == CodeTokenType.String }
        assertEquals(code, code.substring(token.start, token.end))
    }

    @Test
    fun `same request returns cached token list instance`() {
        val code = "fun greet() = 1"

        val first = CodeTokenizer.tokenize(code, "kotlin")
        val second = CodeTokenizer.tokenize(code, "kotlin")

        assertSame(first, second)
    }

    private fun assertContainsToken(
        tokens: List<TokenSpan>,
        code: String,
        snippet: String,
        expectedType: CodeTokenType,
    ) {
        val matchingToken =
            tokens.firstOrNull { token ->
                token.type == expectedType && code.substring(token.start, token.end) == snippet
            }

        assertTrue("Expected $expectedType token for `$snippet`", matchingToken != null)
    }
}
