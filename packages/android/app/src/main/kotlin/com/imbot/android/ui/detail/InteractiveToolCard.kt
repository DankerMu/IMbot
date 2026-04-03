@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imbot.android.ui.theme.BrandBlue
import com.imbot.android.ui.theme.BrandBlueLight
import com.imbot.android.ui.theme.LabelSecondary
import com.imbot.android.ui.theme.SuccessColor

private val CardShape = RoundedCornerShape(16.dp)
private val OptionShape = RoundedCornerShape(10.dp)
private val AnswerShape = RoundedCornerShape(12.dp)
private val HeaderShape = RoundedCornerShape(6.dp)
private val OptionBorder = BorderStroke(1.dp, Color(0xFFE2E8F0))
private val OptionSelectedBorder = BorderStroke(1.5.dp, BrandBlue)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun InteractiveToolCard(
    item: MessageItem.InteractiveToolCall,
    isSessionActive: Boolean,
    isLatestPending: Boolean,
    isSending: Boolean,
    onSubmitAnswer: (String) -> Unit,
    onLongPress: ((MessageItem) -> Unit)? = null,
    selectionModeActive: Boolean = false,
    onExitSelectionMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var answerDraft by rememberSaveable(item.id) { mutableStateOf(item.answer.orEmpty()) }
    var selectedSet by rememberSaveable(item.id) { mutableStateOf(emptySet<String>()) }
    val hapticFeedback = LocalHapticFeedback.current
    val canLongPress = onLongPress != null && hasActions(item)
    val primaryQuestion = item.primaryQuestion
    val isExpired = !item.isAnswered && !isLatestPending
    val inputEnabled = isSessionActive && !item.isAnswered && isLatestPending && !isSending
    val canSubmit =
        interactiveToolCanSubmit(
            primaryQuestion = primaryQuestion,
            selectedSet = selectedSet,
            answerDraft = answerDraft,
            inputEnabled = inputEnabled,
        )
    val dimmed = item.isAnswered || isExpired

    LaunchedEffect(item.isAnswered, item.answer) {
        answeredInteractiveToolAnswer(item)?.let { answer ->
            answerDraft = answer
        }
    }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .interactiveToolCardInteractions(
                    item = item,
                    canLongPress = canLongPress,
                    selectionModeActive = selectionModeActive,
                    onExitSelectionMode = onExitSelectionMode,
                    onLongPress = onLongPress,
                    hapticFeedback = hapticFeedback,
                ),
        shape = CardShape,
        colors =
            CardDefaults.cardColors(
                containerColor = if (dimmed) Color(0xFFF8FAFC) else Color.White,
            ),
        border = BorderStroke(1.dp, if (dimmed) Color(0xFFE2E8F0) else BrandBlue.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dimmed) 0.dp else 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (dimmed) 0.6f else 1f)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Title row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = interactiveToolCardTitle(item = item, isExpired = isExpired),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isAnswered) SuccessColor else BrandBlue,
                )
                if (item.isAnswered) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SuccessColor,
                    )
                }
            }

            // Header chip
            primaryQuestion.header?.let { header ->
                Surface(
                    color = BrandBlueLight,
                    shape = HeaderShape,
                ) {
                    Text(
                        text = header,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = BrandBlue,
                    )
                }
            }

            // Question text
            Text(
                text = primaryQuestion.question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
            )

            // Options
            val options = primaryQuestion.options?.takeIf { !item.isAnswered }
            if (options != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { option ->
                        val isSelected =
                            if (primaryQuestion.multiSelect) {
                                option.label in selectedSet
                            } else {
                                answerDraft == option.label
                            }
                        OptionChip(
                            option = option,
                            selected = isSelected,
                            enabled = inputEnabled,
                            onClick = {
                                if (primaryQuestion.multiSelect) {
                                    selectedSet =
                                        if (option.label in selectedSet) {
                                            selectedSet - option.label
                                        } else {
                                            selectedSet + option.label
                                        }
                                } else {
                                    answerDraft = option.label
                                }
                            },
                        )
                    }
                }
            }

            // Answered state
            if (item.isAnswered) {
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = AnswerShape,
                ) {
                    Text(
                        text = item.answer ?: "已提交",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LabelSecondary,
                    )
                }
            }

            // Text input + submit (non-answered, non-multiSelect)
            if (!item.isAnswered && !primaryQuestion.multiSelect) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = answerDraft,
                        onValueChange = { answerDraft = it },
                        modifier = Modifier.weight(1f),
                        enabled = inputEnabled,
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBlue,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                            ),
                        placeholder = {
                            Text(
                                "输入回答...",
                                color = LabelSecondary.copy(alpha = 0.5f),
                            )
                        },
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            val answer =
                                if (primaryQuestion.multiSelect) {
                                    selectedSet.joinToString(", ")
                                } else {
                                    answerDraft
                                }
                            onSubmitAnswer(answer)
                        },
                        enabled = canSubmit,
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = if (canSubmit) BrandBlue else Color(0xFFE2E8F0),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFFCBD5E1),
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "提交",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Multi-select submit button
            if (!item.isAnswered && primaryQuestion.multiSelect) {
                Surface(
                    onClick = {
                        onSubmitAnswer(selectedSet.joinToString(", "))
                    },
                    enabled = canSubmit,
                    color = if (canSubmit) BrandBlue else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "提交选择",
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canSubmit) Color.White else Color(0xFFCBD5E1),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            // Status note
            InteractiveToolCardStatusNote(
                isExpired = isExpired,
                isSessionActive = isSessionActive,
            )
        }
    }
}

@Composable
private fun OptionChip(
    option: ParsedOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = OptionShape,
        color = if (selected) BrandBlueLight else Color.White,
        border = if (selected) OptionSelectedBorder else OptionBorder,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurface,
            )
            option.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = LabelSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.interactiveToolCardInteractions(
    item: MessageItem.InteractiveToolCall,
    canLongPress: Boolean,
    selectionModeActive: Boolean,
    onExitSelectionMode: (() -> Unit)?,
    onLongPress: ((MessageItem) -> Unit)?,
    hapticFeedback: HapticFeedback,
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
        canLongPress ->
            combinedClickable(
                onClick = {},
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress?.invoke(item)
                },
            )
        else -> this
    }

private fun interactiveToolCardTitle(
    item: MessageItem.InteractiveToolCall,
    isExpired: Boolean,
): String =
    when {
        item.isAnswered -> "已提交回答"
        isExpired -> "已过期"
        else -> "Agent 提问"
    }

@Composable
private fun InteractiveToolCardStatusNote(
    isExpired: Boolean,
    isSessionActive: Boolean,
) {
    val note =
        when {
            isExpired -> "已过期，仅最新一条待回答卡片可交互"
            !isSessionActive -> "当前会话不可交互"
            else -> null
        } ?: return

    Text(
        text = note,
        style = MaterialTheme.typography.labelSmall,
        color = LabelSecondary.copy(alpha = 0.7f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ApprovalCard(
    item: MessageItem.StatusChange,
    isSessionActive: Boolean,
    isLatestPending: Boolean,
    isSending: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onLongPress: ((MessageItem) -> Unit)? = null,
    selectionModeActive: Boolean = false,
    onExitSelectionMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isExpired = item.eventType == "approval_required" && !isLatestPending
    val canRespond = item.eventType == "approval_required" && isLatestPending && isSessionActive && !isSending
    val hapticFeedback = LocalHapticFeedback.current
    val canLongPress = onLongPress != null && hasActions(item)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .approvalCardInteractions(
                    item = item,
                    canLongPress = canLongPress,
                    selectionModeActive = selectionModeActive,
                    onExitSelectionMode = onExitSelectionMode,
                    onLongPress = onLongPress,
                    hapticFeedback = hapticFeedback,
                ),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = approvalCardTitle(item = item, isExpired = isExpired),
                style = MaterialTheme.typography.labelLarge,
                color = BrandBlue,
                fontWeight = FontWeight.SemiBold,
            )

            item.toolName?.takeIf(String::isNotBlank)?.let { toolName ->
                Text(
                    text = "Tool: $toolName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = approvalCardBodyText(item),
                style = MaterialTheme.typography.bodyMedium,
                color = LabelSecondary,
            )

            ApprovalCardActionSection(
                eventType = item.eventType,
                canRespond = canRespond,
                isExpired = isExpired,
                isSessionActive = isSessionActive,
                onApprove = onApprove,
                onDeny = onDeny,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.approvalCardInteractions(
    item: MessageItem.StatusChange,
    canLongPress: Boolean,
    selectionModeActive: Boolean,
    onExitSelectionMode: (() -> Unit)?,
    onLongPress: ((MessageItem) -> Unit)?,
    hapticFeedback: HapticFeedback,
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
        canLongPress ->
            combinedClickable(
                onClick = {},
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress?.invoke(item)
                },
            )
        else -> this
    }
