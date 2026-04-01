@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun InputBar(
    status: String?,
    canSend: Boolean,
    isSending: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val inputEnabled = canInputToSession(status) && canSend && !isSending
    val canSubmit = draft.isNotBlank() && inputEnabled

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                enabled = inputEnabled,
                minLines = 1,
                maxLines = 4,
                placeholder = {
                    Text(inputPlaceholderForStatus(status))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions =
                    KeyboardActions(
                        onSend = {
                            if (canSubmit) {
                                val message = draft.trim()
                                draft = ""
                                onSend(message)
                            }
                        },
                    ),
            )

            IconButton(
                onClick = {
                    if (!canSubmit) {
                        return@IconButton
                    }
                    val message = draft.trim()
                    draft = ""
                    onSend(message)
                },
                enabled = canSubmit,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint =
                        if (canSubmit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                )
            }
        }
    }
}
