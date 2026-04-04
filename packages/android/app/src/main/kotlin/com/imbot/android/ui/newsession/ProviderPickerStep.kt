@file:Suppress("FunctionName")

package com.imbot.android.ui.newsession

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imbot.android.network.RelayHost
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.ProviderBook
import com.imbot.android.ui.theme.ProviderClaude
import com.imbot.android.ui.theme.ProviderOpenClaw
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow

@Suppress("CyclomaticComplexMethod")
@Composable
fun ProviderPickerStep(
    hosts: List<RelayHost>,
    selectedProvider: String?,
    isLoading: Boolean,
    error: String?,
    onSelectProvider: (provider: String, hostId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "选择 Provider",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "每个 provider 对应独立的 session 运行面与工作空间。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (error != null) {
            InlineErrorBanner(
                message = error,
                onRetry = onRetry,
            )
        }

        if (isLoading) {
            ProviderLoadingState(modifier = Modifier.fillMaxWidth())
        } else {
            providerSpecs.forEach { provider ->
                val host = hosts.findProviderHost(provider.id)
                val isOnline = host?.status == "online"
                val selected = selectedProvider == provider.id

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .appleChrome(
                                shape = componentShapes.card,
                                isDarkTheme = isDarkTheme,
                                outlineColor =
                                    when {
                                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                shadowTokens = shadowTokens,
                            )
                            .clickable(enabled = isOnline) {
                                onSelectProvider(provider.id, host!!.id)
                            },
                    shape = componentShapes.card,
                    color =
                        when {
                            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            !isOnline -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                    border =
                        if (selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        } else {
                            null
                        },
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(52.dp)
                                    .background(
                                        color = provider.tint.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(16.dp),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = provider.icon,
                                contentDescription = provider.title,
                                tint = provider.tint,
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = provider.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color =
                                    if (isOnline) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                text = provider.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = host?.name ?: provider.fallbackHostName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        ProviderStatusPill(
                            label =
                                when {
                                    host == null -> "未配置"
                                    isOnline -> "在线"
                                    else -> "离线"
                                },
                            online = isOnline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderStatusPill(
    label: String,
    online: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (online) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        border =
            BorderStroke(
                1.dp,
                if (online) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
                },
            ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (online) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun ProviderLoadingState(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "provider-loading")
    val alpha by
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.92f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "provider-loading-alpha",
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(providerSpecs.size) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .alpha(alpha)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large,
                        ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun InlineErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.large,
                )
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

private data class ProviderSpec(
    val id: String,
    val title: String,
    val description: String,
    val fallbackHostName: String,
    val icon: ImageVector,
    val tint: Color,
)

private val providerSpecs =
    listOf(
        ProviderSpec(
            id = "claude",
            title = "Claude Code",
            description = "Mac 上的主开发工作流，适合代码任务与长会话。",
            fallbackHostName = "MacBook",
            icon = Icons.Filled.Computer,
            tint = ProviderClaude,
        ),
        ProviderSpec(
            id = "book",
            title = "book",
            description = "与 Claude 同源二进制，独立的 novel workspace 与 session 空间。",
            fallbackHostName = "Novel Workspace",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            tint = ProviderBook,
        ),
        ProviderSpec(
            id = "openclaw",
            title = "OpenClaw",
            description = "运行在 relay VPS，本地即可直接触达的远端 provider。",
            fallbackHostName = "Relay VPS",
            icon = Icons.Filled.CloudQueue,
            tint = ProviderOpenClaw,
        ),
    )

private fun List<RelayHost>.findProviderHost(provider: String): RelayHost? =
    firstOrNull { host ->
        provider in host.providers && host.status == "online"
    } ?: firstOrNull { host ->
        provider in host.providers
    }
