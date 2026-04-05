package com.imbot.android.ui.home

import com.imbot.android.data.local.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelUtilsTest {
    @Test
    fun `applies provider filter before sorting`() {
        val sessions =
            listOf(
                session(
                    id = "1",
                    provider = "claude",
                    status = "completed",
                    lastActiveAt = "2026-03-30T09:00:00Z",
                ),
                session(
                    id = "2",
                    provider = "book",
                    status = "running",
                    lastActiveAt = "2026-03-30T10:00:00Z",
                ),
                session(
                    id = "3",
                    provider = "claude",
                    status = "running",
                    lastActiveAt = "2026-03-30T11:00:00Z",
                ),
            )

        val result = applyFilterAndSort(sessions, "claude").map(SessionEntity::id)

        assertEquals(listOf("3", "1"), result)
    }

    @Test
    fun `sorts running sessions before newer completed sessions`() {
        val sessions =
            listOf(
                session(
                    id = "older-running",
                    provider = "claude",
                    status = "running",
                    lastActiveAt = "2026-03-30T08:00:00Z",
                ),
                session(
                    id = "newer-completed",
                    provider = "claude",
                    status = "completed",
                    lastActiveAt = "2026-03-30T11:00:00Z",
                ),
                session(
                    id = "newer-running",
                    provider = "book",
                    status = "running",
                    lastActiveAt = "2026-03-30T10:00:00Z",
                ),
            )

        val result = applyFilterAndSort(sessions, null).map(SessionEntity::id)

        assertEquals(
            listOf("newer-running", "older-running", "newer-completed"),
            result,
        )
    }

    @Test
    fun `only prioritizes truly running sessions over idle and completed ones`() {
        val sessions =
            listOf(
                session(
                    id = "idle-older",
                    provider = "claude",
                    status = "idle",
                    lastActiveAt = "2026-03-30T09:30:00Z",
                ),
                session(
                    id = "running-oldest",
                    provider = "book",
                    status = "running",
                    lastActiveAt = "2026-03-30T08:00:00Z",
                ),
                session(
                    id = "completed-newest",
                    provider = "claude",
                    status = "completed",
                    lastActiveAt = "2026-03-30T11:00:00Z",
                ),
            )

        val result = applyFilterAndSort(sessions, null).map(SessionEntity::id)

        assertEquals(
            listOf("running-oldest", "completed-newest", "idle-older"),
            result,
        )
    }

    @Test
    fun `toggle selected session ids adds and removes ids`() {
        val afterSelect = toggleSelectedSessionIds(setOf("a"), "b")
        val afterDeselect = toggleSelectedSessionIds(afterSelect, "a")

        assertEquals(linkedSetOf("a", "b"), afterSelect)
        assertEquals(linkedSetOf("b"), afterDeselect)
    }

    @Test
    fun `reconcile selection drops ids not in visible list`() {
        val sessions =
            listOf(
                session(
                    id = "visible-a",
                    provider = "claude",
                    status = "running",
                    lastActiveAt = "2026-03-30T11:00:00Z",
                ),
                session(
                    id = "visible-b",
                    provider = "book",
                    status = "completed",
                    lastActiveAt = "2026-03-30T10:00:00Z",
                ),
            )

        val result = reconcileSelection(sessions, linkedSetOf("visible-b", "missing"))

        assertEquals(linkedSetOf("visible-b"), result)
    }

    @Test
    fun `all visible selected only becomes true when every visible session is selected`() {
        val sessions =
            listOf(
                session(
                    id = "1",
                    provider = "claude",
                    status = "running",
                    lastActiveAt = "2026-03-30T11:00:00Z",
                ),
                session(
                    id = "2",
                    provider = "book",
                    status = "completed",
                    lastActiveAt = "2026-03-30T10:00:00Z",
                ),
            )

        val partial =
            HomeUiState(
                sessions = sessions,
                selectedSessionIds = setOf("1"),
            )
        val complete =
            HomeUiState(
                sessions = sessions,
                selectedSessionIds = setOf("1", "2"),
            )

        assertFalse(partial.allVisibleSelected)
        assertTrue(complete.allVisibleSelected)
    }

    @Test
    fun `realtime summary events include usage and message events`() {
        assertTrue(shouldApplyRealtimeSummaryEvent("user_message"))
        assertTrue(shouldApplyRealtimeSummaryEvent("assistant_message"))
        assertTrue(shouldApplyRealtimeSummaryEvent("session_started"))
        assertTrue(shouldApplyRealtimeSummaryEvent("session_usage"))
        assertTrue(shouldApplyRealtimeSummaryEvent("session_idle"))
        assertFalse(shouldApplyRealtimeSummaryEvent("assistant_delta"))
    }

    private fun session(
        id: String,
        provider: String,
        status: String,
        lastActiveAt: String,
    ) = SessionEntity(
        id = id,
        provider = provider,
        hostId = "macbook-1",
        workspaceCwd = "/Users/danker/Desktop/AI-vault/IMbot",
        initialPrompt = "Prompt $id",
        model = "sonnet",
        status = status,
        errorMessage = null,
        createdAt = "2026-03-30T07:00:00Z",
        updatedAt = lastActiveAt,
        lastActiveAt = lastActiveAt,
    )
}
