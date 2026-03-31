@file:Suppress("FunctionName")

package com.imbot.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.imbot.android.ui.theme.IMbotAnimations

@Composable
fun StreamingCursor(
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isStreaming) {
        return
    }

    val alpha by
        rememberInfiniteTransition(label = "streaming-cursor").animateFloat(
            initialValue = IMbotAnimations.CURSOR_ALPHA_MIN,
            targetValue = IMbotAnimations.CURSOR_ALPHA_MAX,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = IMbotAnimations.CURSOR_BLINK_MS,
                            easing = IMbotAnimations.pulseEasing,
                        ),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "streaming-cursor-alpha",
        )

    Text(
        text = "▊",
        modifier = modifier.alpha(alpha),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
