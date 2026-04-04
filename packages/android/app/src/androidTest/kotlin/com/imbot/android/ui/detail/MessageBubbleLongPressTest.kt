package com.imbot.android.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imbot.android.ui.theme.IMbotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageBubbleLongPressTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun agentMarkdownMessageLongPressInvokesCallback() {
        var longPressedId: String? = null
        val item =
            MessageItem.AgentMessage(
                id = "agent-1",
                content = "Plain assistant message",
                isStreaming = false,
                timestamp = "2026-04-04T00:00:00Z",
            )

        composeRule.setContent {
            IMbotTheme(themeMode = "system") {
                MessageBubble(
                    item = item,
                    provider = "book",
                    onLongPress = { pressed ->
                        longPressedId = (pressed as MessageItem.AgentMessage).id
                    },
                )
            }
        }

        composeRule.onNodeWithText("Plain assistant message").performTouchInput {
            longClick()
        }

        composeRule.runOnIdle {
            assertEquals("agent-1", longPressedId)
        }
    }
}
