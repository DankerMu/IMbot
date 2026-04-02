@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal fun interactiveToolCanSubmit(
    primaryQuestion: ParsedQuestion,
    selectedSet: Set<String>,
    answerDraft: String,
    inputEnabled: Boolean,
): Boolean =
    if (primaryQuestion.multiSelect) {
        selectedSet.isNotEmpty() && inputEnabled
    } else {
        answerDraft.trim().isNotEmpty() && inputEnabled
    }

@Composable
internal fun interactiveToolContainerColor(
    isAnswered: Boolean,
    isExpired: Boolean,
) = if (isAnswered || isExpired) {
    MaterialTheme.colorScheme.surfaceVariant
} else {
    MaterialTheme.colorScheme.surface
}

internal fun answeredInteractiveToolAnswer(item: MessageItem.InteractiveToolCall): String? =
    if (item.isAnswered) {
        item.answer.orEmpty()
    } else {
        null
    }

internal fun approvalCardTitle(
    item: MessageItem.StatusChange,
    isExpired: Boolean,
): String =
    when {
        item.eventType == "approval_required" && isExpired -> "已过期"
        item.eventType == "approval_required" -> "需要审批"
        else -> approvalDecisionLabel(item)
    }

internal fun approvalCardBodyText(item: MessageItem.StatusChange): String =
    item.description?.takeIf(String::isNotBlank) ?: item.message.orEmpty()

internal fun approvalCardStatusNote(
    isExpired: Boolean,
    isSessionActive: Boolean,
): String? =
    when {
        isExpired -> "已过期，仅最新一条审批卡片可操作"
        !isSessionActive -> "当前会话不可审批"
        else -> null
    }

@Composable
internal fun ApprovalCardActionSection(
    eventType: String?,
    canRespond: Boolean,
    isExpired: Boolean,
    isSessionActive: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    if (eventType != "approval_required") {
        return
    }

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

    approvalCardStatusNote(
        isExpired = isExpired,
        isSessionActive = isSessionActive,
    )?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
