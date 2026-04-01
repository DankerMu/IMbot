package com.imbot.android

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayOnboardingFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingAuthenticatesAndSubsequentScreensDoNotShow401() {
        val relayUrl = requireArg("relayUrl")
        val token = requireArg("token")

        composeRule.onNodeWithText("测试连接").assertIsDisplayed()

        val textFields = composeRule.onAllNodes(hasSetTextAction())
        textFields[0].performTextInput(relayUrl)
        textFields[1].performTextInput(token)

        composeRule.onNodeWithText("测试连接").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("开始使用", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("认证失败", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }

        val authFailures =
            composeRule.onAllNodesWithText("认证失败", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
        check(authFailures.isEmpty()) { "Onboarding authentication failed" }

        composeRule.onNodeWithText("开始使用").assertIsDisplayed()
        composeRule.onNodeWithText("开始使用").performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("会话", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("目录", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("设置", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        assertNoAuthErrorWindow(durationMs = 8_000)

        composeRule.onNodeWithContentDescription("新建会话").performClick()
        composeRule.onNodeWithText("新建会话").assertIsDisplayed()
        composeRule.onNodeWithText("选择 Provider").assertIsDisplayed()

        assertNoAuthErrorWindow(durationMs = 8_000)
    }

    private fun assertNoAuthErrorWindow(durationMs: Long) {
        val deadline = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()

            val has401 = hasTextInTree("401")
            val hasUnauthenticated = hasTextInTree("unauthenticated")
            check(!has401 && !hasUnauthenticated) {
                "Detected auth error after onboarding: 401=$has401 unauthenticated=$hasUnauthenticated"
            }

            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun hasTextInTree(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun requireArg(name: String): String =
        InstrumentationRegistry.getArguments().getString(name)
            ?.takeIf { it.isNotBlank() }
            ?: error("Missing instrumentation arg: $name")

    private companion object {
        const val POLL_INTERVAL_MS = 250L
    }
}
