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

    @Test(expected = IllegalStateException::class)
    fun `rejects responses without a session object`() {
        JSONObject("""{"ok":true}""").requireRelaySessionObject()
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects non-session objects that happen to have an id`() {
        JSONObject("""{"id":"req-1","error":"not found"}""").requireRelaySessionObject()
    }
}
