package com.imbot.android.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundServiceLifecycleTest {
    @Test
    fun `app open enters active state`() =
        runTest {
            val controller = ForegroundServiceLifecycleController(scope = this)

            controller.onAppForegrounded()

            assertEquals(ForegroundServiceState.ACTIVE, controller.state.value)
        }

    @Test
    fun `background with no running sessions enters cooling down`() =
        runTest {
            val controller = ForegroundServiceLifecycleController(scope = this)

            controller.onAppForegrounded()
            controller.onAppBackgrounded()

            assertEquals(ForegroundServiceState.COOLING_DOWN, controller.state.value)
        }

    @Test
    fun `cooling down for five minutes stops service`() =
        runTest {
            var stopped = false
            val controller =
                ForegroundServiceLifecycleController(
                    scope = this,
                    onStopRequested = { stopped = true },
                )

            controller.onAppForegrounded()
            controller.onAppBackgrounded()
            advanceTimeBy(5 * 60 * 1_000L)
            runCurrent()

            assertEquals(ForegroundServiceState.STOPPED, controller.state.value)
            assertTrue(stopped)
        }

    @Test
    fun `foreground during cooling down returns to active and cancels timer`() =
        runTest {
            var stopped = false
            val controller =
                ForegroundServiceLifecycleController(
                    scope = this,
                    onStopRequested = { stopped = true },
                )

            controller.onAppForegrounded()
            controller.onAppBackgrounded()
            advanceTimeBy(60_000L)
            controller.onAppForegrounded()
            advanceTimeBy(5 * 60 * 1_000L)
            runCurrent()

            assertEquals(ForegroundServiceState.ACTIVE, controller.state.value)
            assertFalse(stopped)
        }

    @Test
    fun `new session during cooling down returns to active and cancels timer`() =
        runTest {
            var stopped = false
            val controller =
                ForegroundServiceLifecycleController(
                    scope = this,
                    onStopRequested = { stopped = true },
                )

            controller.onAppForegrounded()
            controller.onAppBackgrounded()
            advanceTimeBy(60_000L)
            controller.onRunningSessionCountChanged(1)
            advanceTimeBy(5 * 60 * 1_000L)
            runCurrent()

            assertEquals(ForegroundServiceState.ACTIVE, controller.state.value)
            assertFalse(stopped)
        }

    @Test
    fun `background with running sessions stays active`() =
        runTest {
            val controller = ForegroundServiceLifecycleController(scope = this)

            controller.onAppForegrounded()
            controller.onRunningSessionCountChanged(1)
            controller.onAppBackgrounded()

            assertEquals(ForegroundServiceState.ACTIVE, controller.state.value)
        }

    @Test
    fun `stopped state pauses websocket reconnection`() {
        val fake = FakeLifecycleReconnectControllable(connected = true)
        val controller = ServiceLifecycleReconnectController(fake)

        controller.onStateChanged(ForegroundServiceState.STOPPED)

        assertEquals(1, fake.pauseReconnectionCalls)
        assertEquals(0, fake.resumeReconnectionCalls)
        assertEquals(0, fake.forceReconnectCalls)
    }

    @Test
    fun `active state resumes websocket and forces reconnect when disconnected`() {
        val fake = FakeLifecycleReconnectControllable(connected = false)
        val controller = ServiceLifecycleReconnectController(fake)

        controller.onStateChanged(ForegroundServiceState.ACTIVE)

        assertEquals(0, fake.pauseReconnectionCalls)
        assertEquals(1, fake.resumeReconnectionCalls)
        assertEquals(1, fake.forceReconnectCalls)
    }

    @Test
    fun `active state does not force reconnect when already connected`() {
        val fake = FakeLifecycleReconnectControllable(connected = true)
        val controller = ServiceLifecycleReconnectController(fake)

        controller.onStateChanged(ForegroundServiceState.ACTIVE)

        assertEquals(0, fake.pauseReconnectionCalls)
        assertEquals(1, fake.resumeReconnectionCalls)
        assertEquals(0, fake.forceReconnectCalls)
    }
}

private class FakeLifecycleReconnectControllable(
    private var connected: Boolean,
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
