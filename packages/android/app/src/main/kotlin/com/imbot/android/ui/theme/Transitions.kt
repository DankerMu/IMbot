package com.imbot.android.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
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
            animationSpec = IMbotAnimations.GentleSpring,
        )
    } else {
        slideInHorizontally(
            animationSpec =
                spring(
                    dampingRatio = IMbotAnimations.DefaultSpring.dampingRatio,
                    stiffness = IMbotAnimations.DefaultSpring.stiffness,
                ),
            initialOffsetX = { fullWidth -> fullWidth },
        ) +
            fadeIn(
                animationSpec = IMbotAnimations.GentleSpring,
            )
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.imbotExitTransition(): ExitTransition =
    if (isTabCrossfade()) {
        fadeOut(
            animationSpec = IMbotAnimations.GentleSpring,
        )
    } else {
        slideOutHorizontally(
            animationSpec =
                spring(
                    dampingRatio = IMbotAnimations.GentleSpring.dampingRatio,
                    stiffness = IMbotAnimations.GentleSpring.stiffness,
                ),
            targetOffsetX = { fullWidth -> -fullWidth / 4 },
        ) +
            fadeOut(
                animationSpec = IMbotAnimations.GentleSpring,
            )
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.imbotPopEnterTransition(): EnterTransition =
    if (isTabCrossfade()) {
        fadeIn(
            animationSpec = IMbotAnimations.GentleSpring,
        )
    } else {
        slideInHorizontally(
            animationSpec =
                spring(
                    dampingRatio = IMbotAnimations.GentleSpring.dampingRatio,
                    stiffness = IMbotAnimations.GentleSpring.stiffness,
                ),
            initialOffsetX = { fullWidth -> -fullWidth / 4 },
        ) +
            fadeIn(
                animationSpec = IMbotAnimations.GentleSpring,
            )
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.imbotPopExitTransition(): ExitTransition =
    if (isTabCrossfade()) {
        fadeOut(
            animationSpec = IMbotAnimations.GentleSpring,
        )
    } else {
        slideOutHorizontally(
            animationSpec =
                spring(
                    dampingRatio = IMbotAnimations.DefaultSpring.dampingRatio,
                    stiffness = IMbotAnimations.DefaultSpring.stiffness,
                ),
            targetOffsetX = { fullWidth -> fullWidth },
        ) +
            fadeOut(
                animationSpec = IMbotAnimations.GentleSpring,
            )
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabCrossfade(): Boolean =
    initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes
