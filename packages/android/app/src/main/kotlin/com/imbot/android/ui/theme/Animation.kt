package com.imbot.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

enum class MotionCurve {
    Emphasized,
    EmphasizedDecelerate,
    EmphasizedAccelerate,
    Standard,
    Linear,
}

object IMbotAnimations {
    const val PAGE_ENTER_MS = 300
    const val PAGE_EXIT_MS = 250
    const val SHARED_ELEMENT_MS = 400
    const val MESSAGE_FADE_MS = 200
    const val TOOL_EXPAND_MS = 200
    const val STATUS_MORPH_MS = 300
    const val PULSE_MS = 1500
    const val THEME_CROSSFADE_MS = 400
    const val STAGGER_DELAY_MS = 50
    const val BANNER_RECOVERY_DISPLAY_MS = 2000
    const val CURSOR_BLINK_MS = 500
    const val MESSAGE_OFFSET_DP = 24
    const val STAGGER_ITEM_LIMIT = 10
    const val STATUS_BAR_HEIGHT_DP = 2
    const val STATUS_DOT_SIZE_DP = 8
    const val STATUS_BADGE_DOT_SIZE_DP = 8
    const val CURSOR_ALPHA_MIN = 0.3f
    const val CURSOR_ALPHA_MAX = 1.0f
    const val PULSE_ALPHA_MIN = 0.3f
    const val PULSE_ALPHA_MAX = 1.0f

    val pageEnterCurve = MotionCurve.EmphasizedDecelerate
    val pageExitCurve = MotionCurve.EmphasizedAccelerate
    val sharedElementCurve = MotionCurve.Emphasized
    val messageCurve = MotionCurve.Standard
    val pulseCurve = MotionCurve.Linear

    val DefaultSpring: SpringSpec<Float> =
        spring(
            dampingRatio = 0.5f,
            stiffness = 400f,
        )
    val GentleSpring: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 200f,
        )
    val pageEnterEasing: Easing = CubicBezierEasing(0.1f, 0.7f, 0.1f, 1.0f)
    val pageExitEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.2f)
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val pulseEasing: Easing = LinearEasing
    val sharedElementEasing: Easing = EmphasizedPathEasing
}

private object EmphasizedPathEasing : Easing {
    private val segments =
        listOf(
            CubicSegment(
                p0 = Point(0f, 0f),
                p1 = Point(0.05f, 0f),
                p2 = Point(0.133333f, 0.06f),
                p3 = Point(0.166666f, 0.4f),
            ),
            CubicSegment(
                p0 = Point(0.166666f, 0.4f),
                p1 = Point(0.208333f, 0.82f),
                p2 = Point(0.25f, 1f),
                p3 = Point(1f, 1f),
            ),
        )

    override fun transform(fraction: Float): Float {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        val segment = segments.firstOrNull { clampedFraction <= it.p3.x } ?: segments.last()
        val t = solveSegmentParameter(segment, clampedFraction)
        return cubicValue(segment.p0.y, segment.p1.y, segment.p2.y, segment.p3.y, t)
    }

    private fun solveSegmentParameter(
        segment: CubicSegment,
        targetX: Float,
    ): Float {
        var low = 0f
        var high = 1f
        repeat(16) {
            val midpoint = (low + high) / 2f
            val midpointX =
                cubicValue(
                    segment.p0.x,
                    segment.p1.x,
                    segment.p2.x,
                    segment.p3.x,
                    midpoint,
                )
            if (midpointX < targetX) {
                low = midpoint
            } else {
                high = midpoint
            }
        }
        return (low + high) / 2f
    }
}

private data class Point(
    val x: Float,
    val y: Float,
)

private data class CubicSegment(
    val p0: Point,
    val p1: Point,
    val p2: Point,
    val p3: Point,
)

private fun cubicValue(
    p0: Float,
    p1: Float,
    p2: Float,
    p3: Float,
    t: Float,
): Float {
    val inverseT = 1f - t
    return (inverseT * inverseT * inverseT * p0) +
        (3f * inverseT * inverseT * t * p1) +
        (3f * inverseT * t * t * p2) +
        (t * t * t * p3)
}
