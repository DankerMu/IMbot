@file:Suppress("FunctionName")

package com.imbot.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.CodeTextStyle
import com.imbot.android.ui.theme.CodeTheme
import com.imbot.android.ui.theme.CodeTokenizer
import com.imbot.android.ui.theme.LocalCodeTheme
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.TokenSpan
import com.imbot.android.ui.theme.normalizeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = LocalSnackbarHostState.current
    val codeTheme = LocalCodeTheme.current
    val componentShapes = LocalIMbotComponentShapes.current
    val coroutineScope = rememberCoroutineScope()
    val normalizedLanguage = remember(language) { normalizeLanguage(language) }
    val tokens by
        produceState(initialValue = emptyList<TokenSpan>(), code, normalizedLanguage) {
            value =
                if (normalizedLanguage == null || code.isEmpty()) {
                    emptyList()
                } else {
                    withContext(Dispatchers.Default) {
                        CodeTokenizer.tokenize(code = code, language = normalizedLanguage)
                    }
                }
        }
    val defaultCodeColor = MaterialTheme.colorScheme.onSurface
    val highlightedText =
        remember(code, tokens, codeTheme, defaultCodeColor) {
            buildCodeAnnotatedString(
                code = code,
                tokens = tokens,
                codeTheme = codeTheme,
                defaultColor = defaultCodeColor,
            )
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = codeTheme.background,
        shape = componentShapes.codeBlock,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!language.isNullOrBlank()) {
                        Text(
                            text = language.lowercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(
                            text = highlightedText,
                            style = CodeTextStyle,
                            softWrap = false,
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    snackbarHostState?.let { hostState ->
                        coroutineScope.launch {
                            hostState.showSnackbar(
                                message = "已复制",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制代码",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildCodeAnnotatedString(
    code: String,
    tokens: List<TokenSpan>,
    codeTheme: CodeTheme,
    defaultColor: androidx.compose.ui.graphics.Color,
): AnnotatedString =
    buildAnnotatedString {
        append(code)
        addStyle(SpanStyle(color = defaultColor), 0, code.length)
        tokens.forEach { token ->
            addStyle(
                SpanStyle(color = codeTheme.colorFor(token.type)),
                token.start,
                token.end,
            )
        }
    }
