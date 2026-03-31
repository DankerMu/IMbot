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
}
