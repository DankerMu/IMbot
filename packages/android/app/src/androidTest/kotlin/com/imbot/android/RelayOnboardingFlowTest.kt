package com.imbot.android

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
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayOnboardingFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingAuthenticatesAndSubsequentScreensDoNotShow401() {
        val relayUrl = optionalArg("relayUrl")
        val token = optionalArg("token")
        assumeTrue("Skipped: relayUrl and token instrumentation args required", relayUrl != null && token != null)

        if (hasTextInTree("测试连接")) {
            composeRule.onNodeWithText("测试连接").assertIsDisplayed()

            val textFields = composeRule.onAllNodes(hasSetTextAction())
            textFields[0].performTextInput(relayUrl!!)
            textFields[1].performTextInput(token!!)

            composeRule.onNodeWithText("测试连接").performClick()
            composeRule.waitUntil(timeoutMillis = 20_000) {
                hasExactTextInTree("开始使用") || hasTextInTree("认证失败") || hasHomeTabs()
            }

            val authFailures =
                composeRule
                    .onAllNodesWithText("认证失败", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes()
            check(authFailures.isEmpty()) { "Onboarding authentication failed" }

            if (hasExactTextInTree("开始使用")) {
                composeRule.onNodeWithText("开始使用", useUnmergedTree = true).performClick()
            }
        }

        waitForHomeTabs()

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

    private fun hasExactTextInTree(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitForHomeTabs() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            hasHomeTabs()
        }
    }

    private fun hasHomeTabs(): Boolean = hasTextInTree("会话") && hasTextInTree("目录") && hasTextInTree("设置")

    private fun optionalArg(name: String): String? =
        InstrumentationRegistry.getArguments().getString(name)
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val POLL_INTERVAL_MS = 250L
    }
}
