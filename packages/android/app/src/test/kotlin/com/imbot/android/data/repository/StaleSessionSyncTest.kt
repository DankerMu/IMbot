package com.imbot.android.data.repository

import com.imbot.android.data.local.SessionEntity
import com.imbot.android.network.RelaySessionPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleSessionSyncTest {
    @Test
    fun `local minus remote deletes stale sessions`() {
        val staleIds =
            computeStaleSessionIds(
                localPage = listOf(session("A"), session("B"), session("C"), session("D")),
                remoteSessionIds = setOf("A", "C"),
            )

        assertEquals(listOf("B", "D"), staleIds)
    }

    @Test
    fun `remote sessions missing locally do not trigger deletions`() {
        val staleIds =
            computeStaleSessionIds(
                localPage = listOf(session("A"), session("B")),
                remoteSessionIds = setOf("A", "B", "C"),
            )

        assertEquals(emptyList<String>(), staleIds)
    }

    @Test
    fun `empty local page produces no deletions`() {
        val staleIds =
            computeStaleSessionIds(
                localPage = emptyList(),
                remoteSessionIds = setOf("A", "B"),
            )

        assertEquals(emptyList<String>(), staleIds)
    }

    @Test
    fun `pagination only diffs within loaded page range`() {
        val staleIds =
            computeStaleSessionIds(
                localPage = listOf(session("C"), session("D")),
                remoteSessionIds = setOf("C"),
            )

        assertEquals(listOf("D"), staleIds)
    }

    @Test
    fun `running sessions are protected from stale deletion`() {
        val staleIds =
            computeStaleSessionIds(
                localPage = listOf(session("A", status = "running"), session("B")),
                remoteSessionIds = emptySet(),
            )

        assertEquals(listOf("B"), staleIds)
    }

    @Test
    fun `queued sessions are protected from stale deletion`() {
        val staleIds =
            computeStaleSessionIds(
                localPage = listOf(session("A", status = "queued"), session("B")),
                remoteSessionIds = emptySet(),
            )

        assertEquals(listOf("B"), staleIds)
    }

    @Test
    fun `partial remote pages do not delete missing local sessions`() {
        assertFalse(
            shouldDeleteMissingSessionsFromPage(
                RelaySessionPage(
                    sessions = emptyList(),
                    total = 10,
                    limit = 5,
                    offset = 0,
                ),
            ),
        )
    }

    @Test
    fun `last remote page may delete missing local sessions`() {
        assertTrue(
            shouldDeleteMissingSessionsFromPage(
                RelaySessionPage(
                    sessions = emptyList(),
                    total = 5,
                    limit = 5,
                    offset = 5,
                ),
            ),
        )
    }
}

private fun session(
    id: String,
    status: String = "completed",
) = SessionEntity(
    id = id,
    provider = "claude",
    hostId = "macbook-1",
    workspaceCwd = "/tmp/$id",
    initialPrompt = null,
    model = null,
    status = status,
    errorMessage = null,
    createdAt = "2026-03-31T10:00:00Z",
    updatedAt = "2026-03-31T10:00:00Z",
    lastActiveAt = "2026-03-31T10:00:00Z",
)
