package com.imbot.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class CodeTokenType {
    Keyword,
    String,
    Comment,
    Number,
    Type,
    Function,
    Property,
}

sealed class CodeTheme(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val function: Color,
    val property: Color,
    val background: Color,
) {
    data object Light :
        CodeTheme(
            keyword = Color(0xFFD73A49),
            string = Color(0xFF032F62),
            comment = Color(0xFF6A737D),
            number = Color(0xFF005CC5),
            type = Color(0xFF6F42C1),
            function = Color(0xFF6F42C1),
            property = Color(0xFF6F42C1),
            background = Color(0xFFF6F8FA),
        )

    data object Dark :
        CodeTheme(
            keyword = Color(0xFFFF7B72),
            string = Color(0xFFA5D6FF),
            comment = Color(0xFF8B949E),
            number = Color(0xFF79C0FF),
            type = Color(0xFFD2A8FF),
            function = Color(0xFFD2A8FF),
            property = Color(0xFFD2A8FF),
            background = Color(0xFF161B22),
        )

    fun colorFor(tokenType: CodeTokenType): Color =
        when (tokenType) {
            CodeTokenType.Keyword -> keyword
            CodeTokenType.String -> string
            CodeTokenType.Comment -> comment
            CodeTokenType.Number -> number
            CodeTokenType.Type -> type
            CodeTokenType.Function -> function
            CodeTokenType.Property -> property
        }
}

val LocalCodeTheme = staticCompositionLocalOf<CodeTheme> { CodeTheme.Light }
