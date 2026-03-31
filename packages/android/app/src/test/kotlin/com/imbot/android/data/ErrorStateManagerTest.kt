package com.imbot.android.data

import com.imbot.android.ui.components.ErrorScope
import com.imbot.android.ui.components.ResolvedErrorBanner
import com.imbot.android.ui.components.resolveErrorBanner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorStateManagerTest {
    @Test
    fun `initial state starts clean`() {
        val manager = ErrorStateManager()

        val state = manager.errorState.value
        assertTrue(state.relayConnected)
        assertFalse(state.isReconnecting)
        assertTrue(state.hostStatuses.isEmpty())
        assertTrue(state.sessionErrors.isEmpty())
        assertNull(resolveErrorBanner(state, ErrorScope.GLOBAL))
    }

    @Test
    fun `setRelayConnected updates relay state`() {
        val manager = ErrorStateManager()

        manager.setRelayConnected(false)
        assertFalse(manager.errorState.value.relayConnected)
        assertTrue(manager.errorState.value.isReconnecting)

        manager.setRelayConnected(true)
        assertTrue(manager.errorState.value.relayConnected)
        assertFalse(manager.errorState.value.isReconnecting)
    }

    @Test
    fun `setHostStatus updates host availability`() {
        val manager = ErrorStateManager()

        manager.setHostStatus("macbook-1", false)
        assertEquals(false, manager.errorState.value.hostStatuses["macbook-1"])

        manager.setHostStatus("macbook-1", true)
        assertEquals(true, manager.errorState.value.hostStatuses["macbook-1"])
    }

    @Test
    fun `setSessionError and clearSessionError manage session banners`() {
        val manager = ErrorStateManager()

        manager.setSessionError("s1", "Claude upstream 不可用")
        assertEquals("Claude upstream 不可用", manager.errorState.value.sessionErrors["s1"])

        manager.clearSessionError("s1")
        assertNull(manager.errorState.value.sessionErrors["s1"])
    }

    @Test
    fun `priority relay disconnected overrides host offline`() {
        val state =
            ErrorState(
                relayConnected = false,
                isReconnecting = true,
                hostStatuses = mapOf("macbook-1" to false),
                sessionErrors = mapOf("s1" to "Claude upstream 不可用"),
            )

        assertEquals(
            ResolvedErrorBanner.Connection,
            resolveErrorBanner(
                errorState = state,
                scope = ErrorScope.SESSION("s1"),
                hostId = "macbook-1",
            ),
        )
    }

    @Test
    fun `priority host offline overrides session error`() {
        val state =
            ErrorState(
                hostStatuses = mapOf("macbook-1" to false),
                sessionErrors = mapOf("s1" to "Claude upstream 不可用"),
            )

        assertEquals(
            ResolvedErrorBanner.HostOffline("MacBook 离线，请检查 companion 是否运行"),
            resolveErrorBanner(
                errorState = state,
                scope = ErrorScope.SESSION("s1"),
                hostId = "macbook-1",
            ),
        )
    }

    @Test
    fun `concurrent updates keep final state consistent`() =
        runTest {
            val manager = ErrorStateManager()

            List(50) { index ->
                async {
                    manager.setRelayConnected(index % 2 != 0)
                    manager.setHostStatus("macbook-1", index % 3 != 0)
                    manager.setSessionError("s1", "error-$index")
                }
            }.awaitAll()

            manager.setRelayConnected(true)
            manager.setHostStatus("macbook-1", true)
            manager.setSessionError("s1", "stable")

            val state = manager.errorState.value
            assertTrue(state.relayConnected)
            assertFalse(state.isReconnecting)
            assertEquals(true, state.hostStatuses["macbook-1"])
            assertEquals("stable", state.sessionErrors["s1"])
        }

    @Test
    fun `scoped queries only surface relevant banners`() {
        val state =
            ErrorState(
                hostStatuses = mapOf("macbook-1" to true),
                sessionErrors = mapOf("s1" to "Claude upstream 不可用"),
            )

        assertNull(resolveErrorBanner(state, ErrorScope.GLOBAL))
        assertEquals(
            ResolvedErrorBanner.SessionError("Claude upstream 不可用"),
            resolveErrorBanner(
                errorState = state,
                scope = ErrorScope.SESSION("s1"),
                hostId = "macbook-1",
            ),
        )
        assertNull(
            resolveErrorBanner(
                errorState = state,
                scope = ErrorScope.SESSION("s2"),
                hostId = "macbook-1",
            ),
        )
    }
}
