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
    Annotation,
    Operator,
    Bracket,
}

sealed class CodeTheme(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val function: Color,
    val property: Color,
    val annotation: Color,
    val operator: Color,
    val bracket: Color,
    val background: Color,
) {
    data object Light :
        CodeTheme(
            keyword = Color(0xFFCF222E),
            string = Color(0xFF0A3069),
            comment = Color(0xFF6A737D),
            number = Color(0xFF0550AE),
            type = Color(0xFF953800),
            function = Color(0xFF8250DF),
            property = Color(0xFF116329),
            annotation = Color(0xFF8250DF),
            operator = Color(0xFFCF222E),
            bracket = Color(0xFF24292F),
            background = Color(0xFFF6F8FA),
        )

    data object Dark :
        CodeTheme(
            keyword = Color(0xFFFF7B72),
            string = Color(0xFFA5D6FF),
            comment = Color(0xFF8B949E),
            number = Color(0xFF79C0FF),
            type = Color(0xFFFFA657),
            function = Color(0xFFD2A8FF),
            property = Color(0xFF7EE787),
            annotation = Color(0xFFD2A8FF),
            operator = Color(0xFFFF7B72),
            bracket = Color(0xFFC9D1D9),
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
            CodeTokenType.Annotation -> annotation
            CodeTokenType.Operator -> operator
            CodeTokenType.Bracket -> bracket
        }
}

val LocalCodeTheme = staticCompositionLocalOf<CodeTheme> { CodeTheme.Light }
