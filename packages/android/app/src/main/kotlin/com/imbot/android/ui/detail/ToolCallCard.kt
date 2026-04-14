@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.IMbotAnimations
import com.imbot.android.ui.theme.SuccessColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolCallCard(
    item: MessageItem.ToolCall,
    onLongPress: ((MessageItem) -> Unit)? = null,
    selectionModeActive: Boolean = false,
    onExitSelectionMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(item.callId) { mutableStateOf(item.isRunning) }
    val hapticFeedback = LocalHapticFeedback.current
    val canLongPress = onLongPress != null && hasActions(item)
    val category = classifyTool(item.toolName)
    val accentColor = category.accentColor()
    val statusColor =
        when {
            item.isRunning -> accentColor
            item.isError -> MaterialTheme.colorScheme.error
            else -> SuccessColor
        }
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec =
                tween(
                    durationMillis = IMbotAnimations.TOOL_EXPAND_MS,
                    easing = IMbotAnimations.standardEasing,
                ),
            label = "tool-chevron",
        )

    LaunchedEffect(item.isRunning) {
        if (item.isRunning) {
            expanded = true
        } else {
            expanded = false
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(
                    androidx.compose.foundation.shape.RoundedCornerShape(
                        topEnd = 8.dp,
                        bottomEnd = 8.dp,
                    ),
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(statusColor),
        )

        Column(modifier = Modifier.weight(1f)) {
            ToolCallHeader(
                item = item,
                category = category,
                accentColor = accentColor,
                expanded = expanded,
                chevronRotation = chevronRotation,
                canLongPress = canLongPress,
                selectionModeActive = selectionModeActive,
                onExitSelectionMode = onExitSelectionMode,
                onToggleExpanded = { expanded = !expanded },
                onLongPress = onLongPress,
                hapticFeedback = hapticFeedback,
            )
            ToolCallBody(
                item = item,
                category = category,
                expanded = expanded,
                selectionModeActive = selectionModeActive,
                onExitSelectionMode = onExitSelectionMode,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCallHeader(
    item: MessageItem.ToolCall,
    category: ToolCategory,
    accentColor: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
    chevronRotation: Float,
    canLongPress: Boolean,
    selectionModeActive: Boolean,
    onExitSelectionMode: (() -> Unit)?,
    onToggleExpanded: () -> Unit,
    onLongPress: ((MessageItem) -> Unit)?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toolCallInteractions(
                    item = item,
                    canLongPress = canLongPress,
                    selectionModeActive = selectionModeActive,
                    onExitSelectionMode = onExitSelectionMode,
                    onToggleExpanded = onToggleExpanded,
                    onLongPress = onLongPress,
                    hapticFeedback = hapticFeedback,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            modifier =
                Modifier
                    .size(14.dp)
                    .rotate(chevronRotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Icon(
            imageVector = category.icon,
            contentDescription = category.label,
            modifier = Modifier.size(16.dp),
            tint = accentColor,
        )

        Text(
            text = buildToolSummary(category, item),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )

        ToolStatusIndicator(
            isRunning = item.isRunning,
            isError = item.isError,
        )
    }
}

@Composable
private fun ToolCallBody(
    item: MessageItem.ToolCall,
    category: ToolCategory,
    expanded: Boolean,
    selectionModeActive: Boolean,
    onExitSelectionMode: (() -> Unit)?,
) {
    AnimatedVisibility(
        visible = expanded,
        enter =
            expandVertically(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.TOOL_EXPAND_MS,
                        easing = IMbotAnimations.standardEasing,
                    ),
            ),
        exit =
            shrinkVertically(
                animationSpec =
                    tween(
                        durationMillis = IMbotAnimations.TOOL_EXPAND_MS,
                        easing = IMbotAnimations.standardEasing,
                    ),
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .let { baseModifier ->
                        if (selectionModeActive && onExitSelectionMode != null) {
                            baseModifier.clickable(onClick = onExitSelectionMode)
                        } else {
                            baseModifier
                        }
                    },
        ) {
            ToolCallContent(
                category = category,
                item = item,
            )
        }
    }
}

@Composable
private fun ToolCallContent(
    category: ToolCategory,
    item: MessageItem.ToolCall,
) {
    when (category) {
        ToolCategory.BASH -> BashToolContent(item)
        ToolCategory.READ -> ReadToolContent(item)
        ToolCategory.WRITE -> WriteToolContent(item)
        ToolCategory.SEARCH -> SearchToolContent(item)
        ToolCategory.SKILL -> SkillToolContent(item)
        ToolCategory.OTHER -> GenericToolContent(item)
    }
}

@Composable
private fun ToolStatusIndicator(
    isRunning: Boolean,
    isError: Boolean,
) {
    when {
        isRunning -> {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        isError -> {
            Icon(
                imageVector = Icons.Outlined.Cancel,
                contentDescription = "失败",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        else -> {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "完成",
                modifier = Modifier.size(14.dp),
                tint = SuccessColor,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.toolCallInteractions(
    item: MessageItem.ToolCall,
    canLongPress: Boolean,
    selectionModeActive: Boolean,
    onExitSelectionMode: (() -> Unit)?,
    onToggleExpanded: () -> Unit,
    onLongPress: ((MessageItem) -> Unit)?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
): Modifier =
    when {
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
        else ->
            combinedClickable(
                onClick = onToggleExpanded,
                onLongClick =
                    if (canLongPress) {
                        {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress?.invoke(item)
                        }
                    } else {
                        null
                    },
            )
    }
