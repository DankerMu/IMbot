@file:Suppress("FunctionName")

package com.imbot.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape

@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val transition = rememberInfiniteTransition(label = "shimmer-skeleton")
    val progress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1_500,
                            easing = LinearEasing,
                        ),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmer-progress",
        )

    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val highlightColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(baseColor)
                .drawWithContent {
                    drawContent()
                    val width = size.width.coerceAtLeast(1f)
                    val shimmerWidth = width * 0.75f
                    val startX = (progress * (width + shimmerWidth * 2f)) - shimmerWidth
                    drawRect(
                        brush =
                            Brush.linearGradient(
                                colors = listOf(baseColor, highlightColor, baseColor),
                                start = Offset(startX - shimmerWidth, 0f),
                                end = Offset(startX + shimmerWidth, size.height),
                            ),
                    )
                },
    )
}
