@file:Suppress("FunctionName")

package com.imbot.android.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imbot.android.ui.theme.CodeTextStyle
import com.imbot.android.ui.theme.CodeTheme
import com.imbot.android.ui.theme.CodeTokenizer
import com.imbot.android.ui.theme.LocalCodeTheme
import com.imbot.android.ui.theme.LocalIMbotComponentShapes
import com.imbot.android.ui.theme.LocalUseDarkTheme
import com.imbot.android.ui.theme.MAX_HIGHLIGHT_SIZE
import com.imbot.android.ui.theme.TerminalBg
import com.imbot.android.ui.theme.TerminalGreen
import com.imbot.android.ui.theme.TerminalText
import com.imbot.android.ui.theme.TokenSpan
import com.imbot.android.ui.theme.codeBlockBorderColor
import com.imbot.android.ui.theme.codeBlockHeaderBackground
import com.imbot.android.ui.theme.normalizeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal const val CODE_COPY_FEEDBACK_DURATION_MS = 2_000L

private data class CodeBlockPalette(
    val headerBackground: Color,
    val bodyBackground: Color,
    val border: Color,
    val labelColor: Color,
    val actionColor: Color,
    val codeTextColor: Color,
)

@Composable
fun CodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val codeTheme = LocalCodeTheme.current
    val componentShapes = LocalIMbotComponentShapes.current
    val useDarkTheme = LocalUseDarkTheme.current
    val normalizedLanguage = remember(language) { normalizeLanguage(language) }
    val languageLabel = remember(language) { extractCodeLanguageLabel(language) }
    val isTerminal = remember(language) { isTerminalCodeLanguage(language) }
    val palette =
        codeBlockPalette(
            useDarkTheme = useDarkTheme,
            isTerminal = isTerminal,
            codeTheme = codeTheme,
        )
    var expanded by rememberSaveable(code) { mutableStateOf(false) }
    val displayState = remember(code, expanded) { resolveCodeBlockDisplayState(code, expanded) }
    val lineNumbers = remember(displayState.displayedCode) { buildCodeLineNumbers(displayState.displayedCode) }
    var copyFeedbackToken by remember(code) { mutableIntStateOf(0) }
    var showCopiedState by remember(code) { mutableStateOf(false) }
    val shouldHighlight =
        remember(code, normalizedLanguage, isTerminal) {
            normalizedLanguage != null &&
                !isTerminal &&
                code.isNotEmpty() &&
                code.length <= MAX_HIGHLIGHT_SIZE
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
    val highlightedText =
        remember(displayState.displayedCode, tokens, codeTheme, palette.codeTextColor, shouldHighlight) {
            if (!shouldHighlight) {
                AnnotatedString(displayState.displayedCode)
            } else {
                buildCodeAnnotatedString(
                    code = displayState.displayedCode,
                    tokens = tokens.filter { token -> token.end <= displayState.displayedCode.length },
                    codeTheme = codeTheme,
                    defaultColor = palette.codeTextColor,
                )
            }
        }
    val lineNumberText = remember(lineNumbers) { lineNumbers.joinToString(separator = "\n") }

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
        color = palette.bodyBackground,
        shape = componentShapes.codeBlock,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.border, shape = componentShapes.codeBlock),
        ) {
            CodeBlockHeaderBar(
                languageLabel = languageLabel,
                showCopiedState = showCopiedState,
                palette = palette,
                shape = componentShapes.codeBlockHeader,
                onCopyClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    copyFeedbackToken++
                },
            )
            CodeBlockBody(
                displayState = displayState,
                lineNumbers = lineNumbers,
                lineNumberText = lineNumberText,
                highlightedText = highlightedText,
                palette = palette,
                onExpand = {
                    expanded = true
                },
                onCollapse = {
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun CodeBlockHeaderBar(
    languageLabel: String?,
    showCopiedState: Boolean,
    palette: CodeBlockPalette,
    shape: androidx.compose.ui.graphics.Shape,
    onCopyClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(palette.headerBackground, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (languageLabel != null) {
            Text(
                text = languageLabel.lowercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                    ),
                color = palette.labelColor,
            )
        } else {
            Spacer(modifier = Modifier.size(1.dp))
        }

        Row(
            modifier = Modifier.clickable(onClick = onCopyClick),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (showCopiedState) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = if (showCopiedState) "代码已复制" else "复制代码",
                modifier = Modifier.size(14.dp),
                tint = palette.actionColor,
            )
            if (showCopiedState) {
                Text(
                    text = "已复制",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                        ),
                    color = palette.actionColor,
                )
            }
        }
    }
}

@Composable
private fun CodeBlockBody(
    displayState: CodeBlockDisplayState,
    lineNumbers: List<String>,
    lineNumberText: String,
    highlightedText: AnnotatedString,
    palette: CodeBlockPalette,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(palette.bodyBackground)
                .animateContentSize(
                    animationSpec =
                        tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing,
                        ),
                ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            CodeBlockContentRow(
                lineNumbers = lineNumbers,
                lineNumberText = lineNumberText,
                highlightedText = highlightedText,
                palette = palette,
            )
            if (displayState.isCollapsed) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            palette.bodyBackground,
                                        ),
                                ),
                            ),
                )
                CollapseToggle(
                    label = displayState.toggleLabel.orEmpty(),
                    onClick = onExpand,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp),
                )
            }
        }

        if (displayState.isCollapsible && !displayState.isCollapsed) {
            CollapseToggle(
                label = displayState.toggleLabel.orEmpty(),
                onClick = onCollapse,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun CodeBlockContentRow(
    lineNumbers: List<String>,
    lineNumberText: String,
    highlightedText: AnnotatedString,
    palette: CodeBlockPalette,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 20.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (lineNumbers.isNotEmpty()) {
            Text(
                text = lineNumberText,
                modifier = Modifier.width(34.dp),
                style =
                    CodeTextStyle.copy(
                        color = palette.actionColor.copy(alpha = 0.4f),
                        textAlign = TextAlign.End,
                    ),
            )
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(palette.border),
            )
        }

        SelectionContainer {
            Text(
                text = highlightedText,
                style = CodeTextStyle.copy(color = palette.codeTextColor),
                modifier = Modifier.fillMaxWidth(),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun CollapseToggle(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun buildCodeAnnotatedString(
    code: String,
    tokens: List<TokenSpan>,
    codeTheme: CodeTheme,
    defaultColor: Color,
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

@Composable
private fun codeBlockPalette(
    useDarkTheme: Boolean,
    isTerminal: Boolean,
    codeTheme: CodeTheme,
): CodeBlockPalette =
    if (isTerminal) {
        CodeBlockPalette(
            headerBackground = TerminalBg,
            bodyBackground = TerminalBg,
            border = codeBlockBorderColor(useDarkTheme),
            labelColor = TerminalGreen,
            actionColor = TerminalText,
            codeTextColor = TerminalText,
        )
    } else {
        CodeBlockPalette(
            headerBackground = codeBlockHeaderBackground(useDarkTheme),
            bodyBackground = codeTheme.background,
            border = codeBlockBorderColor(useDarkTheme),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionColor = MaterialTheme.colorScheme.onSurfaceVariant,
            codeTextColor = MaterialTheme.colorScheme.onSurface,
        )
    }
