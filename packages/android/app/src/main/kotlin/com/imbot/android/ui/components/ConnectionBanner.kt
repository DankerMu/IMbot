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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imbot.android.network.ConnectionState
import com.imbot.android.ui.theme.IMbotAnimations
import kotlinx.coroutines.delay

private data class ConnectionBannerModel(
    val message: String,
    val isSuccess: Boolean,
    val showSpinner: Boolean,
)

@Composable
fun ConnectionBanner(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    var bannerModel by remember { mutableStateOf<ConnectionBannerModel?>(null) }
    var previousConnectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.NotConfigured) }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            ConnectionState.NotConfigured -> bannerModel = null
            ConnectionState.Connecting ->
                bannerModel =
                    ConnectionBannerModel(
                        message = "网络不稳定，正在重连...",
                        isSuccess = false,
                        showSpinner = true,
                    )
            is ConnectionState.Disconnected ->
                bannerModel =
                    ConnectionBannerModel(
                        message = "无法连接服务器",
                        isSuccess = false,
                        showSpinner = true,
                    )

            ConnectionState.Connected -> {
                if (
                    previousConnectionState is ConnectionState.Disconnected ||
                    previousConnectionState == ConnectionState.Connecting
                ) {
                    bannerModel =
                        ConnectionBannerModel(
                            message = "已恢复",
                            isSuccess = true,
                            showSpinner = false,
                        )
                    delay(IMbotAnimations.BANNER_RECOVERY_DISPLAY_MS.toLong())
                    if (connectionState == ConnectionState.Connected) {
                        bannerModel = null
                    }
                } else {
                    bannerModel = null
                }
            }
        }
        previousConnectionState = connectionState
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
        val background =
            if (model.isSuccess) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        val contentColor =
            if (model.isSuccess) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }

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
