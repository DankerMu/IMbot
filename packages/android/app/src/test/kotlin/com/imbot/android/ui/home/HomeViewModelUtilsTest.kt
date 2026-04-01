package com.imbot.android.ui.home

import com.imbot.android.data.local.SessionEntity
import org.junit.Assert.assertEquals
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
    fun `sorts idle sessions alongside running sessions ahead of completed`() {
        val sessions =
            listOf(
                session(
                    id = "idle-newest",
                    provider = "claude",
                    status = "idle",
                    lastActiveAt = "2026-03-30T11:30:00Z",
                ),
                session(
                    id = "running-older",
                    provider = "book",
                    status = "running",
                    lastActiveAt = "2026-03-30T10:00:00Z",
                ),
                session(
                    id = "completed-middle",
                    provider = "claude",
                    status = "completed",
                    lastActiveAt = "2026-03-30T11:00:00Z",
                ),
            )

        val result = applyFilterAndSort(sessions, null).map(SessionEntity::id)

        assertEquals(
            listOf("idle-newest", "running-older", "completed-middle"),
            result,
        )
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
