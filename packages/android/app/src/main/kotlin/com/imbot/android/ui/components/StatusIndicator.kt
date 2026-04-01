@file:Suppress("FunctionName", "MatchingDeclarationName")

package com.imbot.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.IMbotAnimations
import com.imbot.android.ui.theme.LocalStatusColors
import com.imbot.android.ui.theme.statusColorFor

enum class StatusIndicatorVariant {
    Dot,
    Badge,
    Bar,
}

@Composable
fun StatusIndicator(
    status: String,
    variant: StatusIndicatorVariant,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalStatusColors.current
    val targetColor = statusColorFor(status = status, colors = statusColors)
    val animatedColor by
        animateColorAsState(
            targetValue = targetColor,
            animationSpec =
                tween(
                    durationMillis = IMbotAnimations.STATUS_MORPH_MS,
                    easing = IMbotAnimations.standardEasing,
                ),
            label = "status-indicator-color",
        )
    val alpha =
        if (status == "running") {
            val pulse by
                rememberInfiniteTransition(label = "status-pulse").animateFloat(
                    initialValue = IMbotAnimations.PULSE_ALPHA_MIN,
                    targetValue = IMbotAnimations.PULSE_ALPHA_MAX,
                    animationSpec =
                        infiniteRepeatable(
                            animation =
                                tween(
                                    durationMillis = IMbotAnimations.PULSE_MS,
                                    easing = IMbotAnimations.pulseEasing,
                                ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "status-pulse-alpha",
                )
            pulse
        } else {
            1f
        }

    when (variant) {
        StatusIndicatorVariant.Dot ->
            Box(
                modifier =
                    modifier
                        .size(IMbotAnimations.STATUS_DOT_SIZE_DP.dp)
                        .alpha(alpha)
                        .background(animatedColor, CircleShape),
            )

        StatusIndicatorVariant.Badge ->
            Surface(
                modifier = modifier,
                color = animatedColor.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(IMbotAnimations.STATUS_BADGE_DOT_SIZE_DP.dp)
                                .alpha(alpha)
                                .background(animatedColor, CircleShape),
                    )
                    Text(
                        text = statusLabel(status),
                        style = MaterialTheme.typography.labelMedium,
                        color = animatedColor,
                    )
                }
            }

        StatusIndicatorVariant.Bar ->
            Box(
                modifier =
                    modifier
                        .height(IMbotAnimations.STATUS_BAR_HEIGHT_DP.dp)
                        .background(animatedColor.copy(alpha = alpha)),
            )
    }
}

private fun statusLabel(status: String): String =
    when (status) {
        "running" -> "运行中"
        "idle" -> "空闲"
        "queued" -> "排队中"
        "completed" -> "已完成"
        "failed" -> "失败"
        "cancelled" -> "已取消"
        else -> status
    }
