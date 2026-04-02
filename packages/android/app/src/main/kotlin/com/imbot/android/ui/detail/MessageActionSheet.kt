@file:Suppress("FunctionName", "MatchingDeclarationName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal sealed interface MessageAction {
    data class CopyMessage(
        val text: String,
    ) : MessageAction

    data object SelectText : MessageAction
}

internal fun hasActions(item: MessageItem): Boolean =
    when (item) {
        is MessageItem.AgentMessage -> !item.isStreaming
        is MessageItem.UserMessage -> true
        is MessageItem.ToolCall -> item.toolName.isNotBlank()
        is MessageItem.StatusChange -> false
    }

internal fun availableActions(item: MessageItem): List<MessageAction> {
    if (!hasActions(item)) {
        return emptyList()
    }

    return when (item) {
        is MessageItem.AgentMessage ->
            buildList {
                copyableText(item)?.let { text ->
                    add(MessageAction.CopyMessage(text))
                }
                add(MessageAction.SelectText)
            }

        is MessageItem.UserMessage ->
            buildList {
                copyableText(item)?.let { text ->
                    add(MessageAction.CopyMessage(text))
                }
                add(MessageAction.SelectText)
            }

        is MessageItem.ToolCall ->
            copyableText(item)?.let { text ->
                listOf(MessageAction.CopyMessage(text))
            } ?: emptyList()

        is MessageItem.StatusChange -> emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionSheet(
    actions: List<MessageAction>,
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            actions.forEach { action ->
                ActionRow(
                    label = action.label(),
                    icon = action.icon(),
                    onClick = {
                        onAction(action)
                    },
                )
            }

            if (actions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            DisabledActionRow(
                label = "分享",
                icon = Icons.Filled.Share,
            )
            DisabledActionRow(
                label = "引用回复",
                icon = Icons.AutoMirrored.Filled.Reply,
            )
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DisabledActionRow(
    label: String,
    icon: ImageVector,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(0.45f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun MessageAction.label(): String =
    when (this) {
        is MessageAction.CopyMessage -> "复制消息"
        MessageAction.SelectText -> "选择文本"
    }

private fun MessageAction.icon(): ImageVector =
    when (this) {
        is MessageAction.CopyMessage -> Icons.Filled.ContentCopy
        MessageAction.SelectText -> Icons.Filled.TextFields
    }
