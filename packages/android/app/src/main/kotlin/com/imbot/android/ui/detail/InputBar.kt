@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.LocalIMbotComponentShapes

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
    // Semi-transparent background provides a subtle frosted-glass feel.
    // Compose blur() cannot replicate CSS backdrop-filter (it blurs the
    // element itself, not what's behind it), so we rely on alpha only.
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

    LaunchedEffect(commandChip?.command) {
        if (commandChip != null) {
            draft = ""
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(surfaceColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    PillTextField(
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
                        placeholder = commandChip?.description ?: inputPlaceholderForStatus(status),
                        enabled = inputEnabled,
                        shape = componentShapes.pill,
                        commandChip = commandChip,
                        onDismissCommand = onDismissCommand,
                        modifier = Modifier.weight(1f),
                        onSend = {
                            submitDraft(
                                canSubmit = canSubmit,
                                draft = draft,
                                onSend = onSend,
                                clearDraft = { draft = "" },
                            )
                        },
                    )

                    SendButton(
                        canSubmit = canSubmit,
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
}

@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    commandChip: SkillItem?,
    onDismissCommand: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var commandChipSize by remember(commandChip?.command) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val textStartPadding =
        if (commandChip == null) {
            0.dp
        } else {
            with(density) { commandChipSize.width.toDp() + 6.dp }
        }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        minLines = 1,
        maxLines = 4,
        textStyle =
            MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions =
            KeyboardActions(
                onSend = {
                    onSend()
                },
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                commandChip?.let { skill ->
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .onSizeChanged { commandChipSize = it },
                    ) {
                        CommandChip(
                            skill = skill,
                            onDismiss = onDismissCommand,
                            variant = CommandChipVariant.Inline,
                        )
                    }
                }

                TextFieldContent(
                    value = value,
                    placeholder = placeholder,
                    startPadding = textStartPadding,
                    innerTextField = innerTextField,
                )
            }
        },
    )
}

@Composable
private fun TextFieldContent(
    value: String,
    placeholder: String,
    startPadding: Dp,
    innerTextField: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = startPadding),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        innerTextField()
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
    onSend: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed && canSubmit) 0.92f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
            label = "send-button-scale",
        )

    Box(
        modifier =
            Modifier
                .size(36.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    color =
                        if (canSubmit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        },
                )
                .clickable(
                    enabled = canSubmit,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onSend,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = "发送",
            modifier = Modifier.size(18.dp),
            tint = Color.White,
        )
    }
}
