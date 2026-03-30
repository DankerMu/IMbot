package com.imbot.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayUrlParsingTest {
    @Test
    fun `accepts https relay urls`() {
        val parsed = "https://relay.example.com".toRelayBaseHttpUrl()

        requireNotNull(parsed)
        assertEquals("https", parsed.scheme)
        assertEquals("relay.example.com", parsed.host)
    }

    @Test
    fun `converts wss relay urls to https base urls`() {
        val parsed = "wss://relay.example.com".toRelayBaseHttpUrl()

        requireNotNull(parsed)
        assertEquals("https", parsed.scheme)
        assertEquals("relay.example.com", parsed.host)
    }

    @Test
    fun `rejects insecure relay urls`() {
        assertNull("http://relay.example.com".toRelayBaseHttpUrl())
        assertNull("ws://relay.example.com".toRelayBaseHttpUrl())
        assertNull("relay.example.com".toRelayBaseHttpUrl())
    }
}
