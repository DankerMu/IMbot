@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow
import com.imbot.android.ui.theme.imbotFilledTextFieldColors
import com.imbot.android.ui.theme.spacing

@Composable
internal fun InputBar(
    status: String?,
    canSend: Boolean,
    isSending: Boolean,
    commandChip: SkillItem?,
    onSlashTrigger: () -> Unit,
    onDismissCommand: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val inputEnabled = canInputToSession(status) && canSend && !isSending
    val canSubmit = inputEnabled && (commandChip != null || draft.isNotBlank())
    val componentShapes = LocalIMbotComponentShapes.current
    val spacing = MaterialTheme.spacing
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow

    LaunchedEffect(commandChip?.command) {
        if (commandChip != null) {
            draft = ""
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                thickness = 1.dp,
            )
            commandChip?.let { skill ->
                CommandChip(
                    skill = skill,
                    onDismiss = onDismissCommand,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                TextField(
                    value = draft,
                    onValueChange = { updatedDraft ->
                        draft =
                            updateDraftAndMaybeTriggerSlashSheet(
                                currentDraft = draft,
                                updatedDraft = updatedDraft,
                                commandChip = commandChip,
                                onSlashTrigger = onSlashTrigger,
                            )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = inputEnabled,
                    minLines = 1,
                    maxLines = 4,
                    shape = componentShapes.pill,
                    colors = imbotFilledTextFieldColors(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = {
                        Text(commandChip?.description ?: inputPlaceholderForStatus(status))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions =
                        KeyboardActions(
                            onSend = {
                                submitDraft(
                                    canSubmit = canSubmit,
                                    draft = draft,
                                    onSend = onSend,
                                    clearDraft = { draft = "" },
                                )
                            },
                        ),
                )

                SendButton(
                    canSubmit = canSubmit,
                    isDarkTheme = isDarkTheme,
                    shadowTokens = shadowTokens,
                    onSend = {
                        submitDraft(
                            canSubmit = canSubmit,
                            draft = draft,
                            onSend = onSend,
                            clearDraft = { draft = "" },
                        )
                    },
                )
            }
        }
    }
}

private fun updateDraftAndMaybeTriggerSlashSheet(
    currentDraft: String,
    updatedDraft: String,
    commandChip: SkillItem?,
    onSlashTrigger: () -> Unit,
): String {
    val shouldTriggerSlashSheet =
        commandChip == null &&
            updatedDraft.startsWith("/") &&
            !currentDraft.startsWith("/")
    if (shouldTriggerSlashSheet) {
        onSlashTrigger()
    }
    return updatedDraft
}

private fun submitDraft(
    canSubmit: Boolean,
    draft: String,
    onSend: (String) -> Unit,
    clearDraft: () -> Unit,
) {
    if (!canSubmit) {
        return
    }

    clearDraft()
    onSend(draft)
}

@Composable
private fun SendButton(
    canSubmit: Boolean,
    isDarkTheme: Boolean,
    shadowTokens: com.imbot.android.ui.theme.IMbotShadowTokens,
    onSend: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .then(
                    if (canSubmit) {
                        Modifier.appleChrome(
                            shape = CircleShape,
                            isDarkTheme = isDarkTheme,
                            outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shadowTokens = shadowTokens,
                        )
                    } else {
                        Modifier
                    },
                ),
        shape = CircleShape,
        color =
            if (canSubmit) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onSend,
                enabled = canSubmit,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "发送",
                    tint =
                        if (canSubmit) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}
