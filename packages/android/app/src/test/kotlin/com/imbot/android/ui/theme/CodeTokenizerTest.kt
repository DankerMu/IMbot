package com.imbot.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
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
    fun `json negative numbers stay a single number token`() {
        val code = """{"delta":-1}"""
        val tokens = CodeTokenizer.tokenize(code, "json")

        assertContainsToken(tokens, code, "-1", CodeTokenType.Number)
        assertFalse(
            tokens.any { token ->
                token.type == CodeTokenType.Operator && code.substring(token.start, token.end) == "-"
            },
        )
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
    fun `annotations operators and brackets are recognized for kotlin`() {
        val code = """@Suppress("X") value += other && ready -> { call() }"""
        val tokens = CodeTokenizer.tokenize(code, "kotlin")

        assertContainsToken(tokens, code, "@Suppress", CodeTokenType.Annotation)
        assertContainsToken(tokens, code, "+=", CodeTokenType.Operator)
        assertContainsToken(tokens, code, "&&", CodeTokenType.Operator)
        assertContainsToken(tokens, code, "->", CodeTokenType.Operator)
        assertContainsToken(tokens, code, "{", CodeTokenType.Bracket)
        assertContainsToken(tokens, code, "(", CodeTokenType.Bracket)
        assertContainsToken(tokens, code, ")", CodeTokenType.Bracket)
    }

    @Test
    fun `file scoped annotations are recognized`() {
        val code = "@file:Suppress(\"MagicNumber\")"
        val tokens = CodeTokenizer.tokenize(code, "kotlin")

        assertContainsToken(tokens, code, "@file:Suppress", CodeTokenType.Annotation)
    }

    @Test
    fun `mixed kotlin line keeps annotation keyword function and brackets in order`() {
        val code = "@Test fun foo() {"
        val tokens = CodeTokenizer.tokenize(code, "kotlin")

        assertEquals(
            listOf(
                CodeTokenType.Annotation,
                CodeTokenType.Keyword,
                CodeTokenType.Function,
                CodeTokenType.Bracket,
                CodeTokenType.Bracket,
                CodeTokenType.Bracket,
            ),
            tokens.map(TokenSpan::type),
        )
    }

    @Test
    fun `standalone at sign and email do not become annotations`() {
        assertFalse(
            CodeTokenizer.tokenize("@", "kotlin").any { it.type == CodeTokenType.Annotation },
        )
        assertFalse(
            CodeTokenizer.tokenize("user@test.com", "kotlin").any { it.type == CodeTokenType.Annotation },
        )
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

    @Test
    fun `oversized snippets skip highlighting and caching`() {
        val code = "a".repeat(MAX_HIGHLIGHT_SIZE + 1)

        val tokens = CodeTokenizer.tokenize(code, "kotlin")

        assertTrue(tokens.isEmpty())
        assertEquals(0, CodeTokenizer.cacheSize())
    }

    @Test
    fun `cache evicts oldest entries beyond capacity`() {
        val firstCode = "fun first() = 1"
        val first = CodeTokenizer.tokenize(firstCode, "kotlin")

        repeat(MAX_TOKEN_CACHE_ENTRIES) { index ->
            CodeTokenizer.tokenize("fun value$index() = $index", "kotlin")
        }

        assertEquals(MAX_TOKEN_CACHE_ENTRIES, CodeTokenizer.cacheSize())

        val reloaded = CodeTokenizer.tokenize(firstCode, "kotlin")

        assertEquals(MAX_TOKEN_CACHE_ENTRIES, CodeTokenizer.cacheSize())
        assertNotSame(first, reloaded)
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
