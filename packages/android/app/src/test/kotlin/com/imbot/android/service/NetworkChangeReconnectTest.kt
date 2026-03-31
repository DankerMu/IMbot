package com.imbot.android.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkChangeReconnectTest {
    @Test
    fun `onAvailable debounces reconnect`() =
        runTest {
            val fake = FakeReconnectControllable()
            val controller = NetworkReconnectController(scope = this, reconnectControllable = fake)

            controller.onAvailable()
            advanceTimeBy(999L)
            assertEquals(0, fake.forceReconnectCalls)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(1, fake.forceReconnectCalls)
        }

    @Test
    fun `multiple onAvailable callbacks only reconnect once`() =
        runTest {
            val fake = FakeReconnectControllable()
            val controller = NetworkReconnectController(scope = this, reconnectControllable = fake)

            controller.onAvailable()
            advanceTimeBy(500L)
            controller.onAvailable()
            advanceTimeBy(500L)
            assertEquals(0, fake.forceReconnectCalls)

            advanceTimeBy(500L)
            runCurrent()
            assertEquals(1, fake.forceReconnectCalls)
        }

    @Test
    fun `onLost pauses reconnect attempts`() =
        runTest {
            val fake = FakeReconnectControllable()
            val controller = NetworkReconnectController(scope = this, reconnectControllable = fake)

            controller.onLost()

            assertEquals(1, fake.pauseReconnectionCalls)
        }

    @Test
    fun `onLost then onAvailable resumes and reconnects`() =
        runTest {
            val fake = FakeReconnectControllable()
            val controller = NetworkReconnectController(scope = this, reconnectControllable = fake)

            controller.onLost()
            controller.onAvailable()
            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(1, fake.pauseReconnectionCalls)
            assertEquals(1, fake.resumeReconnectionCalls)
            assertEquals(1, fake.forceReconnectCalls)
        }

    @Test
    fun `onAvailable while already connected force reconnects after debounce`() =
        runTest {
            val fake = FakeReconnectControllable(connected = true)
            val controller = NetworkReconnectController(scope = this, reconnectControllable = fake)

            controller.onAvailable()
            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(1, fake.forceReconnectCalls)
        }
}

private class FakeReconnectControllable(
    private var connected: Boolean = false,
) : ReconnectControllable {
    var pauseReconnectionCalls = 0
    var resumeReconnectionCalls = 0
    var forceReconnectCalls = 0

    override fun pauseReconnection() {
        pauseReconnectionCalls++
        connected = false
    }

    override fun resumeReconnection() {
        resumeReconnectionCalls++
    }

    override fun forceReconnect() {
        forceReconnectCalls++
        connected = true
    }

    override fun isConnected(): Boolean = connected
}
