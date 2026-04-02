@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    val canLongPress = onLongPress != null
    val primaryQuestion = item.primaryQuestion
    val isExpired = !item.isAnswered && !isLatestPending
    val inputEnabled = isSessionActive && !item.isAnswered && isLatestPending && !isSending
    val canSubmit =
        if (primaryQuestion.multiSelect) {
            selectedSet.isNotEmpty() && inputEnabled
        } else {
            answerDraft.trim().isNotEmpty() && inputEnabled
        }
    val containerColor =
        if (item.isAnswered || isExpired) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        }

    LaunchedEffect(item.isAnswered, item.answer) {
        if (item.isAnswered) {
            answerDraft = item.answer.orEmpty()
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
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = interactiveToolCardTitle(item = item, isExpired = isExpired),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            primaryQuestion.header?.let { header ->
                InteractiveToolQuestionHeader(header = header)
            }
            Text(
                text = primaryQuestion.question,
                style = MaterialTheme.typography.bodyLarge,
            )

            InteractiveToolOptionsSection(
                item = item,
                inputEnabled = inputEnabled,
                selectedSet = selectedSet,
                onToggleSelected = { label ->
                    selectedSet =
                        if (label in selectedSet) {
                            selectedSet - label
                        } else {
                            selectedSet + label
                        }
                },
                onSelectOption = { label ->
                    answerDraft = label
                },
            )

            InteractiveToolAnswerSection(
                item = item,
                answerDraft = answerDraft,
                inputEnabled = inputEnabled,
                canSubmit = canSubmit,
                showTextField = !primaryQuestion.multiSelect,
                onAnswerDraftChanged = { answerDraft = it },
                onSubmitAnswer = {
                    val submittedAnswer =
                        if (primaryQuestion.multiSelect) {
                            selectedSet.joinToString(", ")
                        } else {
                            answerDraft
                        }
                    onSubmitAnswer(submittedAnswer)
                },
            )
            InteractiveToolCardStatusNote(
                isExpired = isExpired,
                isSessionActive = isSessionActive,
            )
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

@Composable
private fun InteractiveToolQuestionHeader(header: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = header,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InteractiveToolOptionsSection(
    item: MessageItem.InteractiveToolCall,
    inputEnabled: Boolean,
    selectedSet: Set<String>,
    onToggleSelected: (String) -> Unit,
    onSelectOption: (String) -> Unit,
) {
    val primaryQuestion = item.primaryQuestion
    val options = primaryQuestion.options?.takeIf { !item.isAnswered } ?: return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            if (primaryQuestion.multiSelect) {
                FilterChip(
                    selected = option.label in selectedSet,
                    onClick = {
                        onToggleSelected(option.label)
                    },
                    enabled = inputEnabled,
                    label = {
                        InteractiveToolOptionText(option = option)
                    },
                )
            } else {
                OutlinedButton(
                    onClick = {
                        onSelectOption(option.label)
                    },
                    enabled = inputEnabled,
                ) {
                    InteractiveToolOptionText(option = option)
                }
            }
        }
    }
}

@Composable
private fun InteractiveToolOptionText(option: ParsedOption) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium,
        )
        option.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
private fun InteractiveToolAnswerSection(
    item: MessageItem.InteractiveToolCall,
    answerDraft: String,
    inputEnabled: Boolean,
    canSubmit: Boolean,
    showTextField: Boolean,
    onAnswerDraftChanged: (String) -> Unit,
    onSubmitAnswer: () -> Unit,
) {
    if (item.isAnswered) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = item.answer ?: "已提交",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    if (showTextField) {
        OutlinedTextField(
            value = answerDraft,
            onValueChange = onAnswerDraftChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = inputEnabled,
            minLines = 2,
            placeholder = {
                Text("输入你的回答")
            },
        )
    }

    Button(
        onClick = onSubmitAnswer,
        enabled = canSubmit,
    ) {
        Text("提交回答")
    }

    item.errorMessage?.let { error ->
        Text(
            text = error,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ApprovalCard(
    item: MessageItem.StatusChange,
    isSessionActive: Boolean,
    isLatestPending: Boolean,
    isSending: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExpired = item.eventType == "approval_required" && !isLatestPending
    val canRespond = item.eventType == "approval_required" && isLatestPending && isSessionActive && !isSending

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text =
                    when {
                        item.eventType == "approval_required" && isExpired -> "已过期"
                        item.eventType == "approval_required" -> "需要审批"
                        else -> approvalDecisionLabel(item)
                    },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
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
                text = item.description ?: item.message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.eventType == "approval_required") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onApprove,
                        enabled = canRespond,
                    ) {
                        Text("批准")
                    }
                    OutlinedButton(
                        onClick = onDeny,
                        enabled = canRespond,
                    ) {
                        Text("拒绝")
                    }
                }

                if (isExpired) {
                    Text(
                        text = "已过期，仅最新一条审批卡片可操作",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!isSessionActive) {
                    Text(
                        text = "当前会话不可审批",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
