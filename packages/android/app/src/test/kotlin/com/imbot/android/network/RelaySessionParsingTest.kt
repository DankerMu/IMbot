package com.imbot.android.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RelaySessionParsingTest {
    @Test
    fun `unwraps wrapped session response objects`() {
        val session =
            JSONObject(
                """
                {
                  "session": {
                    "id": "sess-1",
                    "provider": "claude"
                  }
                }
                """.trimIndent(),
            ).requireRelaySessionObject()

        assertEquals("sess-1", session.getString("id"))
    }

    @Test
    fun `accepts raw session response objects`() {
        val session =
            JSONObject(
                """
                {
                  "id": "sess-2",
                  "provider": "claude",
                  "status": "queued"
                }
                """.trimIndent(),
            ).requireRelaySessionObject()

        assertEquals("sess-2", session.getString("id"))
    }

    @Test
    fun `parses idle session status from relay payload`() {
        val session =
            JSONObject(
                """
                {
                  "id": "sess-3",
                  "provider": "claude",
                  "host_id": "macbook-1",
                  "workspace_cwd": "/tmp/demo",
                  "status": "idle",
                  "created_at": "2026-04-01T10:00:00Z",
                  "updated_at": "2026-04-01T10:05:00Z",
                  "last_active_at": "2026-04-01T10:05:00Z"
                }
                """.trimIndent(),
            ).toRelaySession()

        assertEquals("sess-3", session.id)
        assertEquals("idle", session.status)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects responses without a session object`() {
        JSONObject("""{"ok":true}""").requireRelaySessionObject()
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects non-session objects that happen to have an id`() {
        JSONObject("""{"id":"req-1","error":"not found"}""").requireRelaySessionObject()
    }
}
