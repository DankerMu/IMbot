@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.IMbotAnimations
import com.imbot.android.ui.theme.LocalIMbotComponentShapes

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
    val componentShapes = LocalIMbotComponentShapes.current
    val hapticFeedback = LocalHapticFeedback.current
    val canLongPress = onLongPress != null && hasActions(item)
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
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = componentShapes.card,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                            onToggleExpanded = { expanded = !expanded },
                            onLongPress = onLongPress,
                            hapticFeedback = hapticFeedback,
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 2.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.toolName.ifBlank { "工具调用" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.title.ifBlank { item.callId },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.rotate(chevronRotation),
                )
            }

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
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .let { baseModifier ->
                                if (selectionModeActive && onExitSelectionMode != null) {
                                    baseModifier.clickable(onClick = onExitSelectionMode)
                                } else {
                                    baseModifier
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item.args?.takeIf(String::isNotBlank)?.let { args ->
                        ToolCallSection(
                            title = "参数",
                            content = args,
                        )
                    }
                    item.result?.takeIf(String::isNotBlank)?.let { result ->
                        ToolCallSection(
                            title = "结果",
                            content = result,
                        )
                    }
                }
            }
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

@Composable
private fun ToolCallSection(
    title: String,
    content: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
