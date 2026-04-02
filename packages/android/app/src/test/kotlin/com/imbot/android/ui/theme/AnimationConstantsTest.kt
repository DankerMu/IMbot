package com.imbot.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AnimationConstantsTest {
    @Test
    fun `transition durations match spec`() {
        assertEquals(300, IMbotAnimations.PAGE_ENTER_MS)
        assertEquals(250, IMbotAnimations.PAGE_EXIT_MS)
        assertEquals(400, IMbotAnimations.SHARED_ELEMENT_MS)
    }

    @Test
    fun `easing curve names match spec`() {
        assertEquals(MotionCurve.EmphasizedDecelerate, IMbotAnimations.pageEnterCurve)
        assertEquals(MotionCurve.EmphasizedAccelerate, IMbotAnimations.pageExitCurve)
        assertEquals(MotionCurve.Emphasized, IMbotAnimations.sharedElementCurve)
        assertEquals(MotionCurve.Linear, IMbotAnimations.pulseCurve)
        assertSame(IMbotAnimations.pulseEasing, androidx.compose.animation.core.LinearEasing)
    }

    @Test
    fun `streaming cursor parameters match spec`() {
        assertEquals(500, IMbotAnimations.CURSOR_BLINK_MS)
        assertEquals(0.3f, IMbotAnimations.CURSOR_ALPHA_MIN)
        assertEquals(1.0f, IMbotAnimations.CURSOR_ALPHA_MAX)
    }

    @Test
    fun `status pulse parameters match spec`() {
        assertEquals(1500, IMbotAnimations.PULSE_MS)
        assertEquals(0.3f, IMbotAnimations.PULSE_ALPHA_MIN)
        assertEquals(1.0f, IMbotAnimations.PULSE_ALPHA_MAX)
    }

    @Test
    fun `color morph duration matches spec`() {
        assertEquals(300, IMbotAnimations.STATUS_MORPH_MS)
    }

    @Test
    fun `spring parameters match redesign spec`() {
        assertEquals(0.5f, IMbotAnimations.DefaultSpring.dampingRatio)
        assertEquals(400f, IMbotAnimations.DefaultSpring.stiffness)
        assertEquals(1.0f, IMbotAnimations.GentleSpring.dampingRatio)
        assertEquals(200f, IMbotAnimations.GentleSpring.stiffness)
    }

    @Test
    fun `connection banner recovery duration matches spec`() {
        assertEquals(2000, IMbotAnimations.BANNER_RECOVERY_DISPLAY_MS)
    }
}
