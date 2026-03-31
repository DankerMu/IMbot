package com.imbot.android.ui.navigation

import com.imbot.android.data.RelaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGuardTest {
    @Test
    fun `missing relayUrl starts at onboarding`() {
        val startDestination =
            resolveStartDestination(
                RelaySettings(
                    relayUrl = "",
                    token = "secret-token",
                ),
            )

        assertEquals(AppRoute.ONBOARDING, startDestination)
    }

    @Test
    fun `saved relayUrl and token start at home`() {
        val startDestination =
            resolveStartDestination(
                RelaySettings(
                    relayUrl = "https://relay.example.com",
                    token = "secret-token",
                ),
            )

        assertEquals(AppRoute.HOME, startDestination)
    }

    @Test
    fun `onboarding completion navigation replaces stack with home`() {
        val spec = onboardingCompletionNavigation()

        assertEquals(AppRoute.HOME, spec.route)
        assertEquals(AppRoute.ONBOARDING, spec.popUpTo)
        assertTrue(spec.inclusive)
    }
}
