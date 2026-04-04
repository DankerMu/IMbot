@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalProviderColors
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.assistantBubbleBackground
import com.imbot.android.ui.theme.assistantMessageTextColor
import com.imbot.android.ui.theme.userBubbleBackground
import com.imbot.android.ui.theme.userBubbleTextColor

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
    val componentShapes = LocalIMbotComponentShapes.current
    val useDarkTheme = LocalUseDarkTheme.current
    val avatarBackground = userBubbleBackground(useDarkTheme)
    val avatarTextColor = userBubbleTextColor(useDarkTheme)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                color =
                    selectedBubbleColor(
                        baseColor = userBubbleBackground(useDarkTheme),
                        isSelectionMode = isSelectionMode,
                    ),
                shape = componentShapes.userMessageBubble,
                modifier =
                    Modifier
                        .widthIn(max = 300.dp)
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
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = userBubbleTextColor(useDarkTheme),
                    )
                }
            }

            MessageAvatar(
                label = "我",
                backgroundColor = avatarBackground,
                contentColor = avatarTextColor,
            )
        }
        Text(
            text = formatRelativeTimestamp(item.timestamp),
            modifier = Modifier.padding(end = 48.dp),
            style = MaterialTheme.typography.labelSmall,
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
    val componentShapes = LocalIMbotComponentShapes.current
    val hapticFeedback = LocalHapticFeedback.current
    val useDarkTheme = LocalUseDarkTheme.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(item.content.length <= 5_000) }
    val content =
        if (expanded || isSelectionMode) {
            item.content
        } else {
            item.content.take(5_000).trimEnd() + "\n\n..."
        }
    val parsedBlocks = remember(content) { parseMarkdownBlocks(content) }
    val plainParagraph = parsedBlocks.singleOrNull() as? MarkdownBlock.Paragraph
    val renderAsPlainText =
        plainParagraph != null &&
            shouldRenderAssistantMessageAsPlainText(
                content = content,
                parsedBlocks = parsedBlocks,
            )
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val bubbleBorder =
        remember(outlineVariant, useDarkTheme) {
            BorderStroke(
                width = 0.5.dp,
                color =
                    if (useDarkTheme) {
                        outlineVariant.copy(alpha = 0.45f)
                    } else {
                        outlineVariant.copy(alpha = 0.72f)
                    },
            )
        }
    val bubbleModifier =
        Modifier
            .widthIn(max = 316.dp)
            .messageLongPressable(
                item = item,
                onLongPress = onLongPress,
                hapticFeedback = hapticFeedback,
                selectionModeActive = selectionModeActive,
                onExitSelectionMode = onExitSelectionMode,
                isSelectionMode = isSelectionMode,
            )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MessageAvatar(
                label = providerShortLabel(provider),
                backgroundColor = badgeColor.copy(alpha = 0.16f),
                contentColor = badgeColor,
            )

            Surface(
                modifier = bubbleModifier,
                color =
                    selectedBubbleColor(
                        baseColor = assistantBubbleBackground(useDarkTheme),
                        isSelectionMode = isSelectionMode,
                    ),
                shape = componentShapes.assistantMessageBubble,
                border = bubbleBorder,
                shadowElevation = if (useDarkTheme) 0.dp else 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier.defaultMinSize(minHeight = 24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        SelectableBubbleContent(isSelectionMode = isSelectionMode) {
                            if (renderAsPlainText) {
                                Text(
                                    text = plainParagraph?.text.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = assistantMessageTextColor(useDarkTheme),
                                )
                            } else {
                                MarkdownText(
                                    markdown = content,
                                    contentColor = assistantMessageTextColor(useDarkTheme),
                                )
                            }
                        }
                    }
                    if (!expanded && !isSelectionMode) {
                        TextButton(
                            onClick = {
                                expanded = true
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text("展开更多")
                        }
                    }
                }
            }
        }

        Text(
            text = formatRelativeTimestamp(item.timestamp),
            modifier = Modifier.padding(start = 48.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val assistantInlineMarkdownRegexes =
    listOf(
        Regex("""`[^`\n]+`"""),
        Regex("""\[[^\]\n]+]\([^)]+\)"""),
        Regex("""\*\*[^*\n]+\*\*"""),
        Regex("""__[^_\n]+__"""),
        Regex("""~~[^~\n]+~~"""),
        Regex("""(?<!\*)\*[^*\n]+\*(?!\*)"""),
        Regex("""(?<!_)_[^_\n]+_(?!_)"""),
        Regex("""\$[^$\n]+\$"""),
    )

internal fun shouldRenderAssistantMessageAsPlainText(
    content: String,
    parsedBlocks: List<MarkdownBlock>,
): Boolean {
    if (content.contains('\n')) {
        return false
    }
    if (parsedBlocks.singleOrNull() !is MarkdownBlock.Paragraph) {
        return false
    }
    return assistantInlineMarkdownRegexes.none { regex -> regex.containsMatchIn(content) }
}

@Composable
private fun MessageAvatar(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(36.dp)
                .background(
                    color = backgroundColor,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
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

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    selectedBubbleColor(
                        baseColor = Color.Transparent,
                        isSelectionMode = isSelectionMode,
                    ),
                )
                .messageLongPressable(
                    item = item,
                    onLongPress = onLongPress,
                    hapticFeedback = hapticFeedback,
                    selectionModeActive = selectionModeActive,
                    onExitSelectionMode = onExitSelectionMode,
                    isSelectionMode = isSelectionMode,
                )
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .background(
                        color = statusBubbleDotColor(item.status),
                        shape = CircleShape,
                    ),
        )
        Text(
            text = item.message?.takeIf(String::isNotBlank) ?: statusLabel(item.status),
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
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
