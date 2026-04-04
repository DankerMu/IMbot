@file:Suppress("FunctionName")

package com.imbot.android.ui.newsession

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.imbot.android.ui.theme.CodeFontFamily
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "给会话一个起点",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "首条消息是可选项。你也可以直接创建空会话，稍后在详情页继续发消息。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .appleChrome(
                        shape = componentShapes.card,
                        isDarkTheme = isDarkTheme,
                        outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                        shadowTokens = shadowTokens,
                    ),
            shape = componentShapes.card,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Session Context",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryBlock(
                        label = "Provider",
                        value = providerDisplayName(provider),
                        modifier = Modifier.weight(0.34f),
                    )
                    SummaryBlock(
                        label = "目录",
                        value = cwd.orEmpty(),
                        secondary = cwd?.takeLastSegments(3),
                        modifier = Modifier.weight(0.66f),
                    )
                }
            }
        }

        TextField(
            value = prompt,
            onValueChange = onPromptChanged,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("首条消息（可选）")
            },
            placeholder = {
                Text("例如：先总结当前项目结构，再给出下一步建议。")
            },
            minLines = 7,
            maxLines = 10,
            shape = componentShapes.input,
            colors = imbotFilledTextFieldColors(),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        Box {
            Surface(
                shape = componentShapes.button,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            modelMenuExpanded = true
                        },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Model",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = model,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
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
private fun SummaryBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
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
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = CodeFontFamily,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
