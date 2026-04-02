@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InteractiveToolCard(
    item: MessageItem.InteractiveToolCall,
    isSessionActive: Boolean,
    isSending: Boolean,
    onSubmitAnswer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var answerDraft by rememberSaveable(item.id) { mutableStateOf(item.answer.orEmpty()) }
    val inputEnabled = isSessionActive && !item.isAnswered && !isSending
    val canSubmit = answerDraft.trim().isNotEmpty() && inputEnabled
    val containerColor =
        if (item.isAnswered) {
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
        modifier = modifier.fillMaxWidth(),
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
                text = if (item.isAnswered) "已提交回答" else "Agent 提问",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.question,
                style = MaterialTheme.typography.bodyLarge,
            )

            item.options?.takeIf { !item.isAnswered }?.let { options ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { option ->
                        OutlinedButton(
                            onClick = {
                                answerDraft = option
                            },
                            enabled = inputEnabled,
                        ) {
                            Text(option)
                        }
                    }
                }
            }

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
            } else {
                OutlinedTextField(
                    value = answerDraft,
                    onValueChange = { answerDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = inputEnabled,
                    minLines = 2,
                    placeholder = {
                        Text("输入你的回答")
                    },
                )

                Button(
                    onClick = {
                        onSubmitAnswer(answerDraft)
                    },
                    enabled = canSubmit,
                ) {
                    Text("提交回答")
                }
            }

            if (!isSessionActive) {
                Text(
                    text = "当前会话不可交互",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ApprovalCard(
    item: MessageItem.StatusChange,
    isSessionActive: Boolean,
    isSending: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRespond = item.eventType == "approval_required" && isSessionActive && !isSending

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
                    if (item.eventType == "approval_required") {
                        "需要审批"
                    } else {
                        approvalDecisionLabel(item)
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

                if (!isSessionActive) {
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
