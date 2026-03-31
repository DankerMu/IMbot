@file:Suppress("MaxLineLength")

package com.imbot.android.ui.detail

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DetailUtilsTest {
    @Test
    fun `initial scroll state matches spec`() {
        val state = DetailScrollState()

        assertTrue(state.autoScrollEnabled)
        assertEquals(0, state.newMsgCount)
        assertFalse(state.fabVisible)
    }

    @Test
    fun `new message while auto scroll enabled requests scroll`() {
        val mutation = onTimelineChanged(DetailScrollState(), itemCountChanged = true)

        assertTrue(mutation.shouldScrollToBottom)
        assertEquals(DetailScrollState(), mutation.state)
    }

    @Test
    fun `scrolling away from bottom pauses auto scroll`() {
        val state =
            onScrollDistanceChanged(
                current = DetailScrollState(),
                distanceFromBottomDp = 160f,
            )

        assertFalse(state.autoScrollEnabled)
        assertTrue(state.fabVisible)
    }

    @Test
    fun `new message while paused increments unread counter`() {
        val mutation =
            onTimelineChanged(
                current = DetailScrollState(autoScrollEnabled = false, fabVisible = true),
                itemCountChanged = true,
            )

        assertFalse(mutation.shouldScrollToBottom)
        assertEquals(1, mutation.state.newMsgCount)
        assertTrue(mutation.state.fabVisible)
    }

    @Test
    fun `fab tap resumes auto scroll and resets counter`() {
        val mutation =
            resumeAutoScroll(
                DetailScrollState(autoScrollEnabled = false, newMsgCount = 3, fabVisible = true),
            )

        assertTrue(mutation.shouldScrollToBottom)
        assertTrue(mutation.state.autoScrollEnabled)
        assertEquals(0, mutation.state.newMsgCount)
        assertFalse(mutation.state.fabVisible)
    }

    @Test
    fun `scrolling back to bottom resumes auto scroll`() {
        val state =
            onScrollDistanceChanged(
                current = DetailScrollState(autoScrollEnabled = false, newMsgCount = 2, fabVisible = true),
                distanceFromBottomDp = 40f,
            )

        assertTrue(state.autoScrollEnabled)
        assertEquals(0, state.newMsgCount)
        assertFalse(state.fabVisible)
    }

    @Test
    fun `status color mapping matches requirement`() {
        assertEquals(Color(0xFF10B981), detailStatusColor("running"))
        assertEquals(Color(0xFF059669), detailStatusColor("completed"))
        assertEquals(Color(0xFFEF4444), detailStatusColor("failed"))
        assertEquals(Color(0xFF6B7280), detailStatusColor("cancelled"))
        assertEquals(Color(0xFF9CA3AF), detailStatusColor("queued"))
    }

    @Test
    fun `input placeholder text follows session status`() {
        assertEquals("输入消息...", inputPlaceholderForStatus("running"))
        assertEquals("会话已结束", inputPlaceholderForStatus("completed"))
        assertEquals("会话已失败", inputPlaceholderForStatus("failed"))
        assertEquals("会话已取消", inputPlaceholderForStatus("cancelled"))
    }

    @Test
    fun `event type maps to message item kind`() {
        assertEquals(MessageItemKind.User, messageItemKindForEventType("user_message"))
        assertEquals(MessageItemKind.Agent, messageItemKindForEventType("assistant_delta"))
        assertEquals(MessageItemKind.Agent, messageItemKindForEventType("assistant_message"))
        assertEquals(MessageItemKind.ToolCall, messageItemKindForEventType("tool_call_started"))
        assertEquals(MessageItemKind.ToolCall, messageItemKindForEventType("tool_call_completed"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_status_changed"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_started"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_result"))
        assertEquals(MessageItemKind.StatusChange, messageItemKindForEventType("session_error"))
    }

    @Test
    fun `relative timestamp formatting supports now minutes hours and date`() {
        val now = Instant.parse("2026-03-31T12:00:00Z")

        assertEquals("刚刚", formatRelativeTimestamp("2026-03-31T11:59:45Z", now))
        assertEquals("15 分钟前", formatRelativeTimestamp("2026-03-31T11:45:00Z", now))
        assertEquals("2 小时前", formatRelativeTimestamp("2026-03-31T10:00:00Z", now))
        assertEquals("2026-03-29", formatRelativeTimestamp("2026-03-29T12:00:00Z", now))
    }
}
