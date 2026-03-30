package com.imbot.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerMessageParserTest {
    @Test
    fun `parses event message`() {
        val rawMessage =
            """
            {
              "type": "event",
              "session_id": "sess-1",
              "seq": 7,
              "event_type": "assistant_delta",
              "payload": { "text": "hello" },
              "timestamp": "2026-03-29T20:00:00Z"
            }
            """.trimIndent()
        val parsed = parseServerMessage(rawMessage)

        assertTrue(parsed is ServerMessage.Event)
        parsed as ServerMessage.Event
        assertEquals("sess-1", parsed.sessionId)
        assertEquals(7, parsed.seq)
        assertEquals("assistant_delta", parsed.eventType)
        assertEquals("hello", parsed.payload?.getString("text"))
        assertEquals("2026-03-29T20:00:00Z", parsed.timestamp)
    }

    @Test
    fun `parses status message`() {
        val parsed = parseServerMessage("""{"type":"status","session_id":"sess-2","status":"running"}""")

        assertEquals(
            ServerMessage.Status(
                sessionId = "sess-2",
                status = "running",
            ),
            parsed,
        )
    }

    @Test
    fun `parses host status message`() {
        val parsed = parseServerMessage("""{"type":"host_status","host_id":"macbook-1","status":"online"}""")

        assertEquals(
            ServerMessage.HostStatus(
                hostId = "macbook-1",
                status = "online",
            ),
            parsed,
        )
    }

    @Test
    fun `parses error message`() {
        val parsed = parseServerMessage("""{"type":"error","code":"host_offline","message":"Companion offline"}""")

        assertEquals(
            ServerMessage.Error(
                code = "host_offline",
                message = "Companion offline",
            ),
            parsed,
        )
    }

    @Test
    fun `parses pong message`() {
        val parsed = parseServerMessage("""{"type":"pong"}""")

        assertEquals(ServerMessage.Pong, parsed)
    }

    @Test
    fun `returns null for malformed JSON`() {
        assertNull(parseServerMessage("{invalid"))
        assertNull(parseServerMessage("""{"type":"unknown"}"""))
    }
}
