@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun MessageBubble(
    item: MessageItem,
    provider: String,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is MessageItem.UserMessage -> UserMessageBubble(item = item, modifier = modifier)
        is MessageItem.AgentMessage -> AgentMessageBubble(item = item, provider = provider, modifier = modifier)
        is MessageItem.StatusChange -> StatusChangeBubble(item = item, modifier = modifier)
        is MessageItem.ToolCall -> Unit
    }
}

@Composable
private fun UserMessageBubble(
    item: MessageItem.UserMessage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(24.dp),
        ) {
            Text(
                text = item.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = formatRelativeTimestamp(item.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgentMessageBubble(
    item: MessageItem.AgentMessage,
    provider: String,
    modifier: Modifier = Modifier,
) {
    val badgeColor = providerColor(provider)
    var expanded by rememberSaveable(item.id) { mutableStateOf(item.content.length <= 5_000) }
    val content =
        if (expanded) {
            item.content
        } else {
            item.content.take(5_000).trimEnd() + "\n\n..."
        }
    val cursorAlpha =
        if (item.isStreaming) {
            val transition = rememberInfiniteTransition(label = "streaming-cursor")
            val alpha by
                transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "streaming-alpha",
                )
            alpha
        } else {
            0f
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .background(
                            color = badgeColor.copy(alpha = 0.16f),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = providerShortLabel(provider),
                    color = badgeColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MarkdownText(content)
                    if (!expanded) {
                        TextButton(
                            onClick = {
                                expanded = true
                            },
                        ) {
                            Text("展开更多")
                        }
                    }
                    if (item.isStreaming) {
                        Text(
                            text = "▊",
                            modifier = Modifier.alpha(cursorAlpha),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Text(
            text = formatRelativeTimestamp(item.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusChangeBubble(
    item: MessageItem.StatusChange,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = detailStatusColor(item.status).copy(alpha = 0.12f),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = item.message?.takeIf(String::isNotBlank) ?: statusLabel(item.status),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = detailStatusColor(item.status),
                textAlign = TextAlign.Center,
            )
        }
    }
}
