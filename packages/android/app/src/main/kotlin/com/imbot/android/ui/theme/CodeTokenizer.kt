@file:Suppress("MaxLineLength")

package com.imbot.android.ui.theme

import java.util.LinkedHashMap

data class TokenSpan(
    val start: Int,
    val end: Int,
    val type: CodeTokenType,
)

private data class TokenPattern(
    val type: CodeTokenType,
    val regex: Regex,
    val groupIndex: Int = 0,
)

private data class TokenizationRequest(
    val code: String,
    val language: String,
)

internal const val MAX_HIGHLIGHT_SIZE = 10_000
internal const val MAX_TOKEN_CACHE_ENTRIES = 64

object CodeTokenizer {
    private val cacheLock = Any()
    private val cache =
        object : LinkedHashMap<TokenizationRequest, List<TokenSpan>>(MAX_TOKEN_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TokenizationRequest, List<TokenSpan>>?): Boolean =
                size > MAX_TOKEN_CACHE_ENTRIES
        }

    @Suppress("ReturnCount")
    fun tokenize(
        code: String,
        language: String?,
    ): List<TokenSpan> {
        if (code.isEmpty() || code.length > MAX_HIGHLIGHT_SIZE) {
            return emptyList()
        }

        val normalizedLanguage = normalizeLanguage(language) ?: return emptyList()
        val request = TokenizationRequest(code = code, language = normalizedLanguage)
        synchronized(cacheLock) {
            cache[request]?.let { return it }
        }

        val tokens = tokenizeUncached(code = code, language = normalizedLanguage)
        synchronized(cacheLock) {
            cache[request]?.let { return it }
            cache[request] = tokens
            return tokens
        }
    }

    internal fun clearCache() {
        synchronized(cacheLock) {
            cache.clear()
        }
    }

    internal fun cacheSize(): Int =
        synchronized(cacheLock) {
            cache.size
        }

    private fun tokenizeUncached(
        code: String,
        language: String,
    ): List<TokenSpan> {
        val patterns = languagePatterns(language) ?: return emptyList()
        val occupied = BooleanArray(code.length)
        val tokens = mutableListOf<TokenSpan>()

        patterns.forEach { pattern ->
            pattern.regex.findAll(code).forEach tokenMatch@{ match ->
                val range =
                    if (pattern.groupIndex == 0) {
                        match.range
                    } else {
                        match.groups[pattern.groupIndex]?.range ?: return@tokenMatch
                    }
                val start = range.first
                val end = range.last + 1
                if (start >= end || !rangeIsFree(occupied, start, end)) {
                    return@tokenMatch
                }
                occupyRange(occupied, start, end)
                tokens += TokenSpan(start = start, end = end, type = pattern.type)
            }
        }

        return tokens.sortedBy(TokenSpan::start)
    }

    private fun languagePatterns(language: String): List<TokenPattern>? =
        when (language) {
            "kotlin" -> kotlinPatterns
            "typescript" -> typescriptPatterns
            "python" -> pythonPatterns
            "json" -> jsonPatterns
            "sql" -> sqlPatterns
            "bash" -> bashPatterns
            else -> null
        }

    private fun rangeIsFree(
        occupied: BooleanArray,
        start: Int,
        end: Int,
    ): Boolean {
        for (index in start until end) {
            if (occupied[index]) {
                return false
            }
        }
        return true
    }

    private fun occupyRange(
        occupied: BooleanArray,
        start: Int,
        end: Int,
    ) {
        for (index in start until end) {
            occupied[index] = true
        }
    }
}

internal fun normalizeLanguage(language: String?): String? =
    when (language?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()) {
        "kt", "kts", "kotlin" -> "kotlin"
        "ts", "tsx", "typescript", "js", "jsx", "javascript" -> "typescript"
        "py", "python" -> "python"
        "json" -> "json"
        "sql" -> "sql"
        "sh", "bash", "shell", "zsh" -> "bash"
        else -> null
    }

private val multiline = setOf(RegexOption.MULTILINE)
private val multilineIgnoreCase = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
private const val GENERIC_ANNOTATION_PATTERN =
    """(?<![\w@.])@(?:[A-Za-z_][A-Za-z0-9_]*:)?[A-Za-z_][A-Za-z0-9_]*"""
private const val GENERIC_OPERATOR_PATTERN =
    """(?:\+=|-=|->|!=|==|>=|<=|&&|\|\||[+\-=<>!&|])"""
private const val GENERIC_BRACKET_PATTERN = """[(){}\[\]]"""

private val kotlinPatterns =
    listOf(
        TokenPattern(CodeTokenType.Comment, Regex("""/\*[\s\S]*?\*/""")),
        TokenPattern(CodeTokenType.Comment, Regex("""//.*$""", multiline)),
        TokenPattern(CodeTokenType.String, Regex("\"\"\"[\\s\\S]*?\"\"\"")),
        TokenPattern(CodeTokenType.String, Regex("\"(?:\\\\.|[^\"\\\\])*\"", multiline)),
        TokenPattern(CodeTokenType.String, Regex("""'(?:\\.|[^'\\])*'""", multiline)),
        TokenPattern(CodeTokenType.Annotation, Regex(GENERIC_ANNOTATION_PATTERN)),
        TokenPattern(
            CodeTokenType.Keyword,
            Regex(
                """\b(?:as|break|class|continue|data|else|false|for|fun|if|in|""" +
                    """interface|is|null|object|override|package|return|sealed|super|""" +
                    """this|true|typealias|val|var|when|while)\b""",
            ),
        ),
        TokenPattern(CodeTokenType.Operator, Regex(GENERIC_OPERATOR_PATTERN)),
        TokenPattern(CodeTokenType.Number, Regex("""\b\d+(?:\.\d+)?\b""")),
        TokenPattern(CodeTokenType.Function, Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)"""), groupIndex = 1),
        TokenPattern(
            CodeTokenType.Type,
            Regex(
                """\b(?:String|Int|Long|Double|Float|Boolean|Unit|Any|List|MutableList|""" +
                    """Map|MutableMap|Set|MutableSet|Result|[A-Z][A-Za-z0-9_]*)\b""",
            ),
        ),
        TokenPattern(CodeTokenType.Bracket, Regex(GENERIC_BRACKET_PATTERN)),
    )

private val typescriptPatterns =
    listOf(
        TokenPattern(CodeTokenType.Comment, Regex("""/\*[\s\S]*?\*/""")),
        TokenPattern(CodeTokenType.Comment, Regex("""//.*$""", multiline)),
        TokenPattern(CodeTokenType.String, Regex("""`(?:\\.|[^`\\])*`""", multiline)),
        TokenPattern(CodeTokenType.String, Regex("\"(?:\\\\.|[^\"\\\\])*\"", multiline)),
        TokenPattern(CodeTokenType.String, Regex("""'(?:\\.|[^'\\])*'""", multiline)),
        TokenPattern(CodeTokenType.Annotation, Regex(GENERIC_ANNOTATION_PATTERN)),
        TokenPattern(
            CodeTokenType.Keyword,
            Regex(
                """\b(?:async|await|break|case|catch|class|const|continue|default|""" +
                    """else|export|extends|false|for|from|function|if|import|""" +
                    """interface|let|new|null|return|switch|throw|true|try|type|""" +
                    """var|while)\b""",
            ),
        ),
        TokenPattern(CodeTokenType.Operator, Regex(GENERIC_OPERATOR_PATTERN)),
        TokenPattern(CodeTokenType.Number, Regex("""\b\d+(?:\.\d+)?\b""")),
        TokenPattern(CodeTokenType.Function, Regex("""\bfunction\s+([A-Za-z_][A-Za-z0-9_]*)"""), groupIndex = 1),
        TokenPattern(
            CodeTokenType.Type,
            Regex("""\b(?:string|number|boolean|void|unknown|never|any|Promise|Array|Record|Map|Set|[A-Z][A-Za-z0-9_]*)\b"""),
        ),
        TokenPattern(CodeTokenType.Bracket, Regex(GENERIC_BRACKET_PATTERN)),
    )

private val pythonPatterns =
    listOf(
        TokenPattern(CodeTokenType.Comment, Regex("""#.*$""", multiline)),
        TokenPattern(CodeTokenType.String, Regex("""'''[\s\S]*?'''""")),
        TokenPattern(CodeTokenType.String, Regex("\"\"\"[\\s\\S]*?\"\"\"")),
        TokenPattern(CodeTokenType.String, Regex("\"(?:\\\\.|[^\"\\\\])*\"", multiline)),
        TokenPattern(CodeTokenType.String, Regex("""'(?:\\.|[^'\\])*'""", multiline)),
        TokenPattern(CodeTokenType.Annotation, Regex(GENERIC_ANNOTATION_PATTERN)),
        TokenPattern(
            CodeTokenType.Keyword,
            Regex(
                """\b(?:and|as|class|def|elif|else|False|for|from|if|import|in|is|lambda|None|not|or|pass|return|True|while|with)\b""",
            ),
        ),
        TokenPattern(CodeTokenType.Operator, Regex(GENERIC_OPERATOR_PATTERN)),
        TokenPattern(CodeTokenType.Number, Regex("""\b\d+(?:\.\d+)?\b""")),
        TokenPattern(CodeTokenType.Function, Regex("""\bdef\s+([A-Za-z_][A-Za-z0-9_]*)"""), groupIndex = 1),
        TokenPattern(
            CodeTokenType.Type,
            Regex("""\b(?:str|int|float|bool|dict|list|tuple|set|None|[A-Z][A-Za-z0-9_]*)\b"""),
        ),
        TokenPattern(CodeTokenType.Bracket, Regex(GENERIC_BRACKET_PATTERN)),
    )

private val jsonPatterns =
    listOf(
        TokenPattern(CodeTokenType.Property, Regex("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)")),
        TokenPattern(CodeTokenType.String, Regex("\"(?:\\\\.|[^\"\\\\])*\"", multiline)),
        TokenPattern(CodeTokenType.Number, Regex("""-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?""")),
        TokenPattern(CodeTokenType.Operator, Regex(GENERIC_OPERATOR_PATTERN)),
        TokenPattern(CodeTokenType.Keyword, Regex("""\b(?:true|false|null)\b""")),
        TokenPattern(CodeTokenType.Bracket, Regex(GENERIC_BRACKET_PATTERN)),
    )

private val sqlPatterns =
    listOf(
        TokenPattern(CodeTokenType.Comment, Regex("""/\*[\s\S]*?\*/""")),
        TokenPattern(CodeTokenType.Comment, Regex("""--.*$""", multiline)),
        TokenPattern(CodeTokenType.String, Regex("\"(?:\\\\.|[^\"\\\\])*\"", multiline)),
        TokenPattern(CodeTokenType.String, Regex("""'(?:\\.|[^'\\])*'""", multiline)),
        TokenPattern(CodeTokenType.Annotation, Regex(GENERIC_ANNOTATION_PATTERN)),
        TokenPattern(
            CodeTokenType.Keyword,
            Regex(
                """\b(?:select|from|where|join|inner|left|right|full|insert|into|""" +
                    """update|delete|create|table|values|group|by|order|limit|as|""" +
                    """on|and|or|null|having)\b""",
                multilineIgnoreCase,
            ),
        ),
        TokenPattern(CodeTokenType.Operator, Regex(GENERIC_OPERATOR_PATTERN)),
        TokenPattern(CodeTokenType.Number, Regex("""\b\d+(?:\.\d+)?\b""")),
        TokenPattern(
            CodeTokenType.Type,
            Regex("""\b(?:int|integer|text|varchar|boolean|timestamp|date|float|double|decimal)\b""", multilineIgnoreCase),
        ),
        TokenPattern(CodeTokenType.Bracket, Regex(GENERIC_BRACKET_PATTERN)),
    )

private val bashPatterns =
    listOf(
        TokenPattern(CodeTokenType.Comment, Regex("""#.*$""", multiline)),
        TokenPattern(CodeTokenType.String, Regex("\"(?:\\\\.|[^\"\\\\])*\"", multiline)),
        TokenPattern(CodeTokenType.String, Regex("""'(?:\\.|[^'\\])*'""", multiline)),
        TokenPattern(CodeTokenType.Annotation, Regex(GENERIC_ANNOTATION_PATTERN)),
        TokenPattern(
            CodeTokenType.Keyword,
            Regex("""\b(?:case|do|done|elif|else|esac|export|fi|for|function|if|in|local|return|then|while)\b"""),
        ),
        TokenPattern(CodeTokenType.Operator, Regex(GENERIC_OPERATOR_PATTERN)),
        TokenPattern(CodeTokenType.Number, Regex("""\b\d+(?:\.\d+)?\b""")),
        TokenPattern(CodeTokenType.Function, Regex("""\b([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*\)\s*\{"""), groupIndex = 1),
        TokenPattern(CodeTokenType.Bracket, Regex(GENERIC_BRACKET_PATTERN)),
    )
