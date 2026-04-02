package com.imbot.android.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailUtilsAskUserQuestionV2RegressionTest {
    @Test
    fun `parseAskUserQuestionV2 appends truncation note for standard format`() {
        val optionsPayload = (1..12).joinToString(separator = ",") { index -> "\"$index\"" }
        val result = parseAskUserQuestionV2("""{"questions":[{"question":"选哪个?","options":[$optionsPayload]}]}""")

        assertEquals(1, result.size)
        assertEquals(
            "选哪个?\n\n$ASK_USER_QUESTION_OPTIONS_TRUNCATED_NOTE",
            result.single().question,
        )
        assertEquals(
            listOf(
                ParsedOption("1", null),
                ParsedOption("2", null),
                ParsedOption("3", null),
                ParsedOption("4", null),
                ParsedOption("5", null),
                ParsedOption("6", null),
                ParsedOption("7", null),
                ParsedOption("8", null),
                ParsedOption("9", null),
                ParsedOption("10", null),
            ),
            result.single().options,
        )
    }

    @Test
    fun `parseAskUserQuestionV2 caps standard question count`() {
        val questionsPayload =
            (1..10).joinToString(separator = ",") { index ->
                """{"question":"Q$index"}"""
            }
        val result = parseAskUserQuestionV2("""{"questions":[$questionsPayload]}""")

        assertEquals(5, result.size)
        assertEquals(
            listOf("Q1", "Q2", "Q3", "Q4", "Q5"),
            result.map(ParsedQuestion::question),
        )
    }
}
