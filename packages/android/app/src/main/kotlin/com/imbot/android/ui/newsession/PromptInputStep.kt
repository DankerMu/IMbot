@file:Suppress("FunctionName")

package com.imbot.android.ui.newsession

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.appleChrome
import com.imbot.android.ui.theme.appleShadow
import com.imbot.android.ui.theme.imbotFilledTextFieldColors

@Composable
fun PromptInputStep(
    provider: String?,
    cwd: String?,
    prompt: String,
    model: String,
    onPromptChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val componentShapes = LocalIMbotComponentShapes.current
    val isDarkTheme = LocalUseDarkTheme.current
    val shadowTokens = MaterialTheme.appleShadow

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "输入 Prompt",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .appleChrome(
                        shape = componentShapes.card,
                        isDarkTheme = isDarkTheme,
                        outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shadowTokens = shadowTokens,
                    ),
            shape = componentShapes.card,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryRow(
                    label = "Provider",
                    value = providerDisplayName(provider),
                )
                SummaryRow(
                    label = "目录",
                    value = cwd.orEmpty(),
                    secondary = cwd?.takeLastSegments(2),
                )
            }
        }

        TextField(
            value = prompt,
            onValueChange = onPromptChanged,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Prompt")
            },
            placeholder = {
                Text("说点什么开始...")
            },
            minLines = 6,
            maxLines = 10,
            shape = componentShapes.input,
            colors = imbotFilledTextFieldColors(),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        Box {
            Surface(
                shape = componentShapes.button,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            modelMenuExpanded = true
                        },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Model: $model")
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
                    )
                }
            }

            DropdownMenu(
                expanded = modelMenuExpanded,
                onDismissRequest = {
                    modelMenuExpanded = false
                },
            ) {
                listOf("sonnet", "opus", "haiku").forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option)
                        },
                        onClick = {
                            modelMenuExpanded = false
                            onModelChanged(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    secondary: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = secondary ?: value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (secondary != null && secondary != value) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun providerDisplayName(provider: String?): String =
    when (provider) {
        "claude" -> "Claude Code"
        "book" -> "book"
        "openclaw" -> "OpenClaw"
        else -> "未选择"
    }

private fun String.takeLastSegments(count: Int): String {
    val segments = split("/").filter(String::isNotBlank)
    if (segments.isEmpty()) {
        return this
    }
    return segments.takeLast(count).joinToString(separator = " / ")
}
