package com.imbot.android.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.imbot.android.ui.navigation.AppRoute

private val topLevelRoutes =
    setOf(
        AppRoute.HOME,
        AppRoute.WORKSPACE,
        AppRoute.SETTINGS,
    )

fun AnimatedContentTransitionScope<NavBackStackEntry>.imbotEnterTransition(): EnterTransition =
    if (isTabCrossfade()) {
        fadeIn(
            animationSpec =
                androidx.compose.animation.core.tween(
                    durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                    easing = IMbotAnimations.standardEasing,
                ),
        )
    } else {
        slideInHorizontally(
            animationSpec =
                tween(
                    durationMillis = IMbotAnimations.PAGE_ENTER_MS,
                    easing = IMbotAnimations.pageEnterEasing,
                ),
            initialOffsetX = { fullWidth -> fullWidth },
        ) +
            fadeIn(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.PAGE_ENTER_MS,
                        easing = IMbotAnimations.pageEnterEasing,
                    ),
            )
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.imbotExitTransition(): ExitTransition =
    if (isTabCrossfade()) {
        fadeOut(
            animationSpec =
                androidx.compose.animation.core.tween(
                    durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                    easing = IMbotAnimations.standardEasing,
                ),
        )
    } else {
        slideOutHorizontally(
            animationSpec =
                tween(
                    durationMillis = IMbotAnimations.PAGE_ENTER_MS,
                    easing = IMbotAnimations.pageEnterEasing,
                ),
            targetOffsetX = { fullWidth -> -fullWidth / 4 },
        ) +
            fadeOut(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.PAGE_ENTER_MS,
                        easing = IMbotAnimations.pageEnterEasing,
                    ),
            )
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.imbotPopEnterTransition(): EnterTransition =
    if (isTabCrossfade()) {
        fadeIn(
            animationSpec =
                androidx.compose.animation.core.tween(
                    durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                    easing = IMbotAnimations.standardEasing,
                ),
        )
    } else {
        slideInHorizontally(
            animationSpec =
                tween(
                    durationMillis = IMbotAnimations.PAGE_EXIT_MS,
                    easing = IMbotAnimations.pageEnterEasing,
                ),
            initialOffsetX = { fullWidth -> -fullWidth / 4 },
        ) +
            fadeIn(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.PAGE_EXIT_MS,
                        easing = IMbotAnimations.pageEnterEasing,
                    ),
            )
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.imbotPopExitTransition(): ExitTransition =
    if (isTabCrossfade()) {
        fadeOut(
            animationSpec =
                androidx.compose.animation.core.tween(
                    durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                    easing = IMbotAnimations.standardEasing,
                ),
        )
    } else {
        slideOutHorizontally(
            animationSpec =
                tween(
                    durationMillis = IMbotAnimations.PAGE_EXIT_MS,
                    easing = IMbotAnimations.pageExitEasing,
                ),
            targetOffsetX = { fullWidth -> fullWidth },
        ) +
            fadeOut(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.PAGE_EXIT_MS,
                        easing = IMbotAnimations.pageExitEasing,
                    ),
            )
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabCrossfade(): Boolean =
    initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes
