@file:Suppress("FunctionName")

package com.imbot.android.ui.newsession

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "选择 Provider",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "系统会自动匹配对应的主机，离线 Provider 不可选择。",
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
                val statusLabel =
                    when {
                        host == null -> "未配置"
                        isOnline -> "在线"
                        else -> "离线"
                    }

                OutlinedCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isOnline) {
                                onSelectProvider(provider.id, host!!.id)
                            },
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        CardDefaults.outlinedCardColors(
                            containerColor =
                                when {
                                    selectedProvider == provider.id ->
                                        MaterialTheme.colorScheme.primaryContainer
                                    !isOnline -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.surface
                                },
                        ),
                    border =
                        CardDefaults.outlinedCardBorder(
                            enabled = true,
                        ).copy(
                            brush =
                                androidx.compose.ui.graphics.SolidColor(
                                    when {
                                        selectedProvider == provider.id -> MaterialTheme.colorScheme.primary
                                        !isOnline -> MaterialTheme.colorScheme.outlineVariant
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                ),
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(52.dp)
                                    .background(
                                        color = provider.tint.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(18.dp),
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
                                text = host?.name ?: provider.fallbackHostName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(statusLabel)
                            },
                        )
                    }
                }
            }
        }
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
                        .height(108.dp)
                        .alpha(alpha)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(24.dp),
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
                    shape = RoundedCornerShape(18.dp),
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
    val fallbackHostName: String,
    val icon: ImageVector,
    val tint: Color,
)

private val providerSpecs =
    listOf(
        ProviderSpec(
            id = "claude",
            title = "Claude Code",
            fallbackHostName = "MacBook",
            icon = Icons.Filled.Computer,
            tint = Color(0xFFE56B2B),
        ),
        ProviderSpec(
            id = "book",
            title = "book",
            fallbackHostName = "Novel Workspace",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            tint = Color(0xFF2E7D5B),
        ),
        ProviderSpec(
            id = "openclaw",
            title = "OpenClaw",
            fallbackHostName = "Relay VPS",
            icon = Icons.Filled.CloudQueue,
            tint = Color(0xFFB24C3C),
        ),
    )

private fun List<RelayHost>.findProviderHost(provider: String): RelayHost? =
    firstOrNull { host ->
        provider in host.providers && host.status == "online"
    } ?: firstOrNull { host ->
        provider in host.providers
    }
