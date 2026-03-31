package com.imbot.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rendersHomeScreen() {
        composeRule.onNodeWithText("IMbot").assertIsDisplayed()
        // Without a configured relay URL, the onboarding screen is shown.
        // With a configured relay URL, the home screen with bottom nav tabs is shown.
        // Both screens display "IMbot", so this assertion covers both paths.
        composeRule.onNodeWithText("测试连接").assertIsDisplayed()
    }
}
