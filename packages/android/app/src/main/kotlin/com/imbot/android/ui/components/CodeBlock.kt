@file:Suppress("FunctionName")

package com.imbot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imbot.android.ui.theme.CodeTextStyle
import com.imbot.android.ui.theme.CodeTheme
import com.imbot.android.ui.theme.CodeTokenizer
import com.imbot.android.ui.theme.LocalCodeTheme
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.MAX_HIGHLIGHT_SIZE
import com.imbot.android.ui.theme.TokenSpan
import com.imbot.android.ui.theme.normalizeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal const val CODE_COPY_FEEDBACK_DURATION_MS = 2_000L
internal const val MAX_CODE_GUTTER_LINES = 500

internal fun extractCodeLanguageLabel(infoString: String?): String? {
    val firstToken = infoString?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()
    return firstToken?.takeIf { normalizeLanguage(it) != null }
}

internal fun buildCodeLineNumbers(code: String): List<String> {
    val lineCount =
        if (code.isEmpty()) {
            0
        } else {
            code.count { it == '\n' } + 1
        }
    if (lineCount == 0 || lineCount > MAX_CODE_GUTTER_LINES) {
        return emptyList()
    }
    return (1..lineCount).map(Int::toString)
}

@Composable
fun CodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val codeTheme = LocalCodeTheme.current
    val componentShapes = LocalIMbotComponentShapes.current
    val normalizedLanguage = remember(language) { normalizeLanguage(language) }
    val languageLabel = remember(language) { extractCodeLanguageLabel(language) }
    val lineNumbers = remember(code) { buildCodeLineNumbers(code) }
    var copyFeedbackToken by remember(code) { mutableIntStateOf(0) }
    var showCopiedState by remember(code) { mutableStateOf(false) }
    val shouldHighlight =
        remember(code, normalizedLanguage) {
            normalizedLanguage != null && code.isNotEmpty() && code.length <= MAX_HIGHLIGHT_SIZE
        }
    val tokens by
        produceState(initialValue = emptyList<TokenSpan>(), code, normalizedLanguage, shouldHighlight) {
            value =
                if (!shouldHighlight) {
                    emptyList()
                } else {
                    withContext(Dispatchers.Default) {
                        CodeTokenizer.tokenize(code = code, language = normalizedLanguage)
                    }
                }
        }
    val defaultCodeColor = MaterialTheme.colorScheme.onSurface
    val highlightedText =
        remember(code, tokens, codeTheme, defaultCodeColor, shouldHighlight) {
            if (!shouldHighlight) {
                AnnotatedString(code)
            } else {
                buildCodeAnnotatedString(
                    code = code,
                    tokens = tokens,
                    codeTheme = codeTheme,
                    defaultColor = defaultCodeColor,
                )
            }
        }
    val lineNumberText =
        remember(lineNumbers) {
            lineNumbers.joinToString(separator = "\n")
        }

    LaunchedEffect(copyFeedbackToken) {
        if (copyFeedbackToken == 0) {
            return@LaunchedEffect
        }
        showCopiedState = true
        delay(CODE_COPY_FEEDBACK_DURATION_MS)
        showCopiedState = false
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (languageLabel != null) {
                        Text(
                            text = languageLabel,
                            modifier =
                                Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = componentShapes.chip,
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(modifier = Modifier.height(1.dp))
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                            copyFeedbackToken++
                        },
                    ) {
                        Icon(
                            imageVector = if (showCopiedState) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = if (showCopiedState) "代码已复制" else "复制代码",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .defaultMinSize(minHeight = 20.dp)
                            .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (lineNumbers.isNotEmpty()) {
                        Text(
                            text = lineNumberText,
                            modifier = Modifier.width(34.dp),
                            style =
                                CodeTextStyle.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    textAlign = TextAlign.End,
                                ),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = 12.dp)
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        )
                    }

                    SelectionContainer {
                        Text(
                            text = highlightedText,
                            style = CodeTextStyle,
                            modifier = Modifier.fillMaxWidth(),
                            softWrap = false,
                        )
                    }
                }
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
