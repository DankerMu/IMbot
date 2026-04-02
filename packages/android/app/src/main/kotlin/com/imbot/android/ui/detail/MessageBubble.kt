@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.components.StreamingCursor
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalProviderColors
import com.imbot.android.ui.theme.LocalStatusColors

internal val AgentBubbleShape =
    RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp,
    )

internal val UserBubbleShape =
    RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 4.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp,
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    item: MessageItem,
    provider: String,
    isSessionActive: Boolean = false,
    isLatestPendingApproval: Boolean = true,
    isSending: Boolean = false,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onLongPress: ((MessageItem) -> Unit)? = null,
    selectionModeActive: Boolean = false,
    onExitSelectionMode: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is MessageItem.UserMessage ->
            UserMessageBubble(
                item = item,
                onLongPress = onLongPress,
                selectionModeActive = selectionModeActive,
                onExitSelectionMode = onExitSelectionMode,
                isSelectionMode = isSelectionMode,
                modifier = modifier,
            )

        is MessageItem.AgentMessage ->
            AgentMessageBubble(
                item = item,
                provider = provider,
                onLongPress = onLongPress,
                selectionModeActive = selectionModeActive,
                onExitSelectionMode = onExitSelectionMode,
                isSelectionMode = isSelectionMode,
                modifier = modifier,
            )

        is MessageItem.InteractiveToolCall -> Unit
        is MessageItem.StatusChange ->
            StatusChangeBubble(
                item = item,
                isSessionActive = isSessionActive,
                isLatestPendingApproval = isLatestPendingApproval,
                isSending = isSending,
                onApprove = onApprove,
                onDeny = onDeny,
                onLongPress = onLongPress,
                selectionModeActive = selectionModeActive,
                onExitSelectionMode = onExitSelectionMode,
                isSelectionMode = isSelectionMode,
                modifier = modifier,
            )
        is MessageItem.ToolCall -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessageBubble(
    item: MessageItem.UserMessage,
    onLongPress: ((MessageItem) -> Unit)? = null,
    selectionModeActive: Boolean = false,
    onExitSelectionMode: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color =
                selectedBubbleColor(
                    baseColor = MaterialTheme.colorScheme.primaryContainer,
                    isSelectionMode = isSelectionMode,
                ),
            shape = UserBubbleShape,
            shadowElevation = 0.5.dp,
            modifier =
                Modifier
                    .messageLongPressable(
                        item = item,
                        onLongPress = onLongPress,
                        hapticFeedback = hapticFeedback,
                        selectionModeActive = selectionModeActive,
                        onExitSelectionMode = onExitSelectionMode,
                        isSelectionMode = isSelectionMode,
                    ),
        ) {
            SelectableBubbleContent(isSelectionMode = isSelectionMode) {
                Text(
                    text = item.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = formatRelativeTimestamp(item.timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentMessageBubble(
    item: MessageItem.AgentMessage,
    provider: String,
    onLongPress: ((MessageItem) -> Unit)? = null,
    selectionModeActive: Boolean = false,
    onExitSelectionMode: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val badgeColor = providerColor(provider, LocalProviderColors.current)
    val hapticFeedback = LocalHapticFeedback.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(item.content.length <= 5_000) }
    val content =
        if (expanded || isSelectionMode) {
            item.content
        } else {
            item.content.take(5_000).trimEnd() + "\n\n..."
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
                        .size(30.dp)
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
                color =
                    selectedBubbleColor(
                        baseColor = MaterialTheme.colorScheme.surfaceContainer,
                        isSelectionMode = isSelectionMode,
                    ),
                shape = AgentBubbleShape,
                shadowElevation = 0.5.dp,
                modifier =
                    Modifier
                        .weight(1f)
                        .messageLongPressable(
                            item = item,
                            onLongPress = onLongPress,
                            hapticFeedback = hapticFeedback,
                            selectionModeActive = selectionModeActive,
                            onExitSelectionMode = onExitSelectionMode,
                            isSelectionMode = isSelectionMode,
                        ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SelectableBubbleContent(isSelectionMode = isSelectionMode) {
                        MarkdownText(content)
                    }
                    if (!expanded && !isSelectionMode) {
                        TextButton(
                            onClick = {
                                expanded = true
                            },
                        ) {
                            Text("展开更多")
                        }
                    }
                    StreamingCursor(isStreaming = item.isStreaming)
                }
            }
        }

        Text(
            text = formatRelativeTimestamp(item.timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusChangeBubble(
    item: MessageItem.StatusChange,
    isSessionActive: Boolean,
    isLatestPendingApproval: Boolean,
    isSending: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onLongPress: ((MessageItem) -> Unit)?,
    selectionModeActive: Boolean,
    onExitSelectionMode: (() -> Unit)?,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    val hapticFeedback = LocalHapticFeedback.current
    if (item.eventType == "approval_required" || item.eventType == "approval_resolved") {
        ApprovalCard(
            item = item,
            isSessionActive = isSessionActive,
            isLatestPending = isLatestPendingApproval,
            isSending = isSending,
            onApprove = onApprove,
            onDeny = onDeny,
            onLongPress = onLongPress,
            selectionModeActive = selectionModeActive,
            onExitSelectionMode = onExitSelectionMode,
            modifier = modifier,
        )
        return
    }

    val statusColor = detailStatusColor(item.status, LocalStatusColors.current)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color =
                selectedBubbleColor(
                    baseColor =
                        statusColor.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface),
                    isSelectionMode = isSelectionMode,
                ),
            shape = componentShapes.pill,
            modifier =
                Modifier.messageLongPressable(
                    item = item,
                    onLongPress = onLongPress,
                    hapticFeedback = hapticFeedback,
                    selectionModeActive = selectionModeActive,
                    onExitSelectionMode = onExitSelectionMode,
                    isSelectionMode = isSelectionMode,
                ),
        ) {
            Text(
                text = item.message?.takeIf(String::isNotBlank) ?: statusLabel(item.status),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun selectedBubbleColor(
    baseColor: Color,
    isSelectionMode: Boolean,
): Color =
    if (isSelectionMode) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f).compositeOver(baseColor)
    } else {
        baseColor
    }

@Composable
private fun SelectableBubbleContent(
    isSelectionMode: Boolean,
    content: @Composable () -> Unit,
) {
    if (isSelectionMode) {
        SelectionContainer(content = content)
    } else {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.messageLongPressable(
    item: MessageItem,
    onLongPress: ((MessageItem) -> Unit)?,
    hapticFeedback: HapticFeedback,
    selectionModeActive: Boolean,
    onExitSelectionMode: (() -> Unit)?,
    isSelectionMode: Boolean,
): Modifier {
    val canLongPress = onLongPress != null && hasActions(item)
    return when {
        isSelectionMode -> this
        selectionModeActive && onExitSelectionMode != null -> {
            if (canLongPress) {
                combinedClickable(
                    onClick = onExitSelectionMode,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress?.invoke(item)
                    },
                )
            } else {
                clickable(onClick = onExitSelectionMode)
            }
        }
        !canLongPress -> this
        else ->
            combinedClickable(
                onClick = {},
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress?.invoke(item)
                },
            )
    }
}
