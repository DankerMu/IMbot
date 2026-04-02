package com.imbot.android.ui.detail

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDetailTimelineTest {
    @Test
    fun `message spacing tokens and grouping rules match visual polish spec`() {
        assertEquals(24.dp, MESSAGE_GROUP_SPACING)
        assertEquals(8.dp, MESSAGE_WITHIN_GROUP_SPACING)
        assertEquals(
            MESSAGE_WITHIN_GROUP_SPACING,
            messageSpacing(
                previousItem = agentMessage(content = "first"),
                currentItem = agentMessage(content = "second"),
            ),
        )
        assertEquals(
            MESSAGE_GROUP_SPACING,
            messageSpacing(
                previousItem = agentMessage(content = "reply"),
                currentItem = userMessage(text = "follow up"),
            ),
        )
    }

    @Test
    fun `deduplicateStatusChanges keeps only the last item in identical status runs`() {
        val first = statusChange(id = "status-1", status = "running")
        val second = statusChange(id = "status-2", status = "running")
        val third = statusChange(id = "status-3", status = "running")

        assertEquals(
            listOf(third),
            deduplicateStatusChanges(listOf(first, second, third)),
        )
    }

    @Test
    fun `deduplicateStatusChanges preserves different status transitions`() {
        val first = statusChange(id = "status-1", status = "running")
        val second = statusChange(id = "status-2", status = "idle")
        val third = statusChange(id = "status-3", status = "running")

        assertEquals(
            listOf(first, second, third),
            deduplicateStatusChanges(listOf(first, second, third)),
        )
    }

    @Test
    fun `deduplicateStatusChanges works when status runs are mixed with other message kinds`() {
        val agentStart = agentMessage(content = "before")
        val runningFirst = statusChange(id = "status-1", status = "running")
        val runningLast = statusChange(id = "status-2", status = "running")
        val agentEnd = agentMessage(content = "after")

        assertEquals(
            listOf(agentStart, runningLast, agentEnd),
            deduplicateStatusChanges(listOf(agentStart, runningFirst, runningLast, agentEnd)),
        )
    }

    @Test
    fun `deduplicateStatusChanges handles empty lists`() {
        assertEquals(emptyList<MessageItem>(), deduplicateStatusChanges(emptyList()))
    }
}

private const val TIMELINE_TEST_TIMESTAMP = "2026-03-31T12:00:00Z"

private fun agentMessage(content: String) =
    MessageItem.AgentMessage(
        id = "agent-$content",
        content = content,
        isStreaming = false,
        timestamp = TIMELINE_TEST_TIMESTAMP,
    )

private fun userMessage(text: String) =
    MessageItem.UserMessage(
        id = "user-$text",
        text = text,
        timestamp = TIMELINE_TEST_TIMESTAMP,
    )

private fun statusChange(
    id: String,
    status: String,
) = MessageItem.StatusChange(
    id = id,
    status = status,
    message = status,
)
