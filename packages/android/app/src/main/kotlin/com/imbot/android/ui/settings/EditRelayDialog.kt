@file:Suppress("FunctionName")

package com.imbot.android.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.imbot.android.ui.onboarding.isValidRelayUrl
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.imbotFilledTextFieldColors

@Composable
fun EditRelayDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    val componentShapes = LocalIMbotComponentShapes.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("编辑 Relay URL")
        },
        text = {
            TextField(
                value = url,
                onValueChange = { value ->
                    url = value
                    error = null
                },
                label = {
                    Text("Relay URL")
                },
                supportingText =
                    error?.let { message ->
                        {
                            Text(message)
                        }
                    },
                singleLine = true,
                shape = componentShapes.input,
                colors = imbotFilledTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = url.trim()
                    when {
                        normalized.isBlank() -> error = "URL 不能为空"
                        !isValidRelayUrl(normalized) -> error = "请输入 https:// 开头的 URL"
                        else -> onSave(normalized)
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
