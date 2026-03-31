@file:Suppress("FunctionName")

package com.imbot.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imbot.android.network.ConnectionState
import com.imbot.android.ui.theme.IMbotAnimations

private data class ConnectionBannerModel(
    val message: String,
    val showSpinner: Boolean,
)

@Composable
fun ConnectionBanner(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val bannerModel =
        when (connectionState) {
            ConnectionState.NotConfigured, ConnectionState.Connected -> null
            ConnectionState.Connecting ->
                ConnectionBannerModel(
                    message = "网络不稳定，正在重连...",
                    showSpinner = true,
                )
            is ConnectionState.Disconnected ->
                ConnectionBannerModel(
                    message = "无法连接服务器",
                    showSpinner = false,
                )
        }

    AnimatedVisibility(
        visible = bannerModel != null,
        modifier = modifier,
        enter =
            slideInVertically(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                        easing = IMbotAnimations.standardEasing,
                    ),
                initialOffsetY = { -it },
            ) +
                fadeIn(
                    animationSpec =
                        tween(
                            durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                            easing = IMbotAnimations.standardEasing,
                        ),
                ) +
                expandVertically(
                    animationSpec =
                        tween(
                            durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                            easing = IMbotAnimations.standardEasing,
                        ),
                    expandFrom = Alignment.Top,
                ),
        exit =
            slideOutVertically(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                        easing = IMbotAnimations.standardEasing,
                    ),
                targetOffsetY = { -it },
            ) +
                fadeOut(
                    animationSpec =
                        tween(
                            durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                            easing = IMbotAnimations.standardEasing,
                        ),
                ) +
                shrinkVertically(
                    animationSpec =
                        tween(
                            durationMillis = IMbotAnimations.MESSAGE_FADE_MS,
                            easing = IMbotAnimations.standardEasing,
                        ),
                    shrinkTowards = Alignment.Top,
                ),
        label = "connection-banner",
    ) {
        val model = bannerModel ?: return@AnimatedVisibility
        val background = MaterialTheme.colorScheme.errorContainer
        val contentColor = MaterialTheme.colorScheme.onErrorContainer

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = background,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model.showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 2.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                }
                Text(
                    text = model.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
        }
    }
}
