@file:Suppress("FunctionName", "TooManyFunctions")

package com.imbot.android.ui.detail

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.imbot.android.ui.theme.LocalUseDarkTheme
import kotlin.math.ceil

@Composable
internal fun MarkdownKatexInlineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val inlineCodeBackground = markdownInlineCodeBackground(LocalUseDarkTheme.current)
    val html =
        remember(text, style, colors, density, inlineCodeBackground) {
            buildKatexDocumentHtml(
                bodyHtml = buildMarkdownInlineHtml(text),
                textColor = colors.onSurface.toCssColor(),
                linkColor = colors.primary.toCssColor(),
                inlineCodeBackground = inlineCodeBackground.toCssColor(includeAlpha = true),
                fontSizePx = style.resolveFontSizeCssPx(density),
                lineHeightPx = style.resolveLineHeightCssPx(density),
                fontWeight = style.fontWeight?.weight ?: 400,
                fontStyle = style.fontStyle ?: FontStyle.Normal,
                textAlign = TextAlign.Start,
            )
        }

    KatexWebView(
        html = html,
        modifier = modifier,
        minHeight = style.resolveLineHeightDp(density),
    )
}

@Composable
internal fun MarkdownKatexMathBlock(
    expression: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleMedium
    val density = LocalDensity.current
    val inlineCodeBackground = markdownInlineCodeBackground(LocalUseDarkTheme.current)
    val html =
        remember(expression, style, colors, density, inlineCodeBackground) {
            buildKatexDocumentHtml(
                bodyHtml = encodeHtml("$$${expression.trim()}$$"),
                textColor = colors.onSurface.toCssColor(),
                linkColor = colors.primary.toCssColor(),
                inlineCodeBackground = inlineCodeBackground.toCssColor(includeAlpha = true),
                fontSizePx = style.resolveFontSizeCssPx(density),
                lineHeightPx = style.resolveLineHeightCssPx(density),
                fontWeight = style.fontWeight?.weight ?: 500,
                fontStyle = style.fontStyle ?: FontStyle.Normal,
                textAlign = TextAlign.Center,
            )
        }

    Surface(
        color = colors.surfaceVariant.copy(alpha = 0.38f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        KatexWebView(
            html = html,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            minHeight =
                if (style.resolveLineHeightDp(density) > 48.dp) {
                    style.resolveLineHeightDp(density)
                } else {
                    48.dp
                },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KatexWebView(
    html: String,
    modifier: Modifier = Modifier,
    minHeight: Dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var contentHeightPx by remember(html) { mutableIntStateOf(0) }
    val minHeightDp = if (minHeight > 24.dp) minHeight else 24.dp
    val backgroundArgb = Color.Transparent.toArgb()
    val webView =
        remember(context) {
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.loadsImagesAutomatically = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                addJavascriptInterface(
                    KatexHeightBridge { reportedHeight ->
                        contentHeightPx = reportedHeight
                    },
                    "IMbotBridge",
                )
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            return when {
                                url.startsWith("http://") || url.startsWith("https://") -> {
                                    openExternalUrl(context, url)
                                    true
                                }

                                else -> true // block javascript:, file://, data: and all other schemes
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): android.webkit.WebResourceResponse? {
                            val url = request?.url?.toString().orEmpty()
                            val blocked =
                                url.startsWith("file://") &&
                                    !url.startsWith("file:///android_asset/katex/")
                            return if (blocked) {
                                android.webkit.WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    204,
                                    "Blocked",
                                    emptyMap(),
                                    null,
                                )
                            } else {
                                super.shouldInterceptRequest(view, request)
                            }
                        }
                    }
            }
        }

    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface("IMbotBridge")
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeightDp)
                .then(
                    if (contentHeightPx > 0) {
                        Modifier.height(with(density) { contentHeightPx.toDp() })
                    } else {
                        Modifier
                    },
                ),
        update = { view ->
            view.setBackgroundColor(backgroundArgb)
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(
                    "file:///android_asset/",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

@Suppress("CyclomaticComplexMethod")
internal fun buildMarkdownInlineHtml(text: String): String {
    val builder = StringBuilder()
    var currentIndex = 0

    INLINE_TOKEN_REGEX.findAll(text).forEach { match ->
        if (match.range.first > currentIndex) {
            builder.append(encodeHtml(text.substring(currentIndex, match.range.first)).replace("\n", "<br/>"))
        }

        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                val inner = buildMarkdownInlineHtml(token.removePrefix("**").removeSuffix("**"))
                builder.append("<strong>$inner</strong>")
            }

            token.startsWith("*") && token.endsWith("*") ->
                builder.append("<em>${buildMarkdownInlineHtml(token.removePrefix("*").removeSuffix("*"))}</em>")

            token.startsWith("~~") && token.endsWith("~~") ->
                builder.append("<del>${buildMarkdownInlineHtml(token.removePrefix("~~").removeSuffix("~~"))}</del>")

            token.startsWith("`") && token.endsWith("`") ->
                builder.append("<code>${encodeHtml(token.removePrefix("`").removeSuffix("`"))}</code>")

            token.startsWith("$") && token.endsWith("$") ->
                builder.append(encodeHtml(token))

            token.startsWith("[") -> {
                val linkMatch = LINK_REGEX.matchEntire(token)
                val label = linkMatch?.groupValues?.get(1).orEmpty()
                val url = linkMatch?.groupValues?.get(2).orEmpty()
                if (label.isNotBlank() && url.isNotBlank() && isSafeUrlScheme(url)) {
                    builder.append(
                        "<a href=\"${encodeHtml(url)}\">${buildMarkdownInlineHtml(label)}</a>",
                    )
                } else {
                    builder.append(encodeHtml(token))
                }
            }

            else -> builder.append(encodeHtml(token))
        }

        currentIndex = match.range.last + 1
    }

    if (currentIndex < text.length) {
        builder.append(encodeHtml(text.substring(currentIndex)).replace("\n", "<br/>"))
    }

    return builder.toString()
}

internal fun buildKatexDocumentHtml(
    bodyHtml: String,
    textColor: String,
    linkColor: String,
    inlineCodeBackground: String,
    fontSizePx: Float,
    lineHeightPx: Float,
    fontWeight: Int,
    fontStyle: FontStyle,
    textAlign: TextAlign,
): String {
    val normalizedFontSize = ceil(fontSizePx.toDouble()).toInt().coerceAtLeast(14)
    val normalizedLineHeight = ceil(lineHeightPx.toDouble()).toInt().coerceAtLeast(normalizedFontSize)
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
            <link rel="stylesheet" href="katex/katex.min.css"/>
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    background: transparent;
                }
                body {
                    color: $textColor;
                    font-family: sans-serif;
                    font-size: ${normalizedFontSize}px;
                    line-height: ${normalizedLineHeight}px;
                    font-weight: $fontWeight;
                    font-style: ${fontStyle.toCssValue()};
                    text-align: ${textAlign.toCssValue()};
                    overflow-wrap: anywhere;
                    word-break: break-word;
                }
                #content {
                    min-height: 1px;
                }
                a {
                    color: $linkColor;
                    text-decoration: underline;
                }
                code {
                    background: $inlineCodeBackground;
                    border-radius: 6px;
                    padding: 0.08em 0.28em;
                    font-family: monospace;
                    font-size: 0.92em;
                }
                .katex-display {
                    margin: 0.32em 0 0.16em;
                    overflow-x: auto;
                    overflow-y: hidden;
                    padding-bottom: 0.08em;
                }
            </style>
            <script defer src="katex/katex.min.js"></script>
            <script defer src="katex/auto-render.min.js"></script>
            <script>
                function reportHeight() {
                    var root = document.documentElement;
                    var body = document.body;
                    var height = Math.ceil(Math.max(
                        root.scrollHeight,
                        body.scrollHeight,
                        root.offsetHeight,
                        body.offsetHeight
                    ) * (window.devicePixelRatio || 1));
                    if (window.IMbotBridge && window.IMbotBridge.onContentHeight) {
                        window.IMbotBridge.onContentHeight(height);
                    }
                }

                function renderKatex() {
                    var content = document.getElementById("content");
                    renderMathInElement(content, {
                        delimiters: [
                            {left: "$$", right: "$$", display: true},
                            {left: "$", right: "$", display: false}
                        ],
                        throwOnError: false,
                        strict: "ignore",
                        ignoredTags: ["script", "noscript", "style", "textarea", "pre", "code", "option"]
                    });

                    if (window.ResizeObserver) {
                        new ResizeObserver(reportHeight).observe(document.body);
                    }

                    requestAnimationFrame(function() {
                        setTimeout(reportHeight, 0);
                    });
                }

                window.addEventListener("load", reportHeight);
                document.addEventListener("DOMContentLoaded", renderKatex);
            </script>
        </head>
        <body>
            <div id="content">$bodyHtml</div>
        </body>
        </html>
        """.trimIndent()
}

private fun encodeHtml(text: String): String =
    buildString(text.length) {
        text.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }

private fun TextStyle.resolveFontSizeCssPx(density: androidx.compose.ui.unit.Density): Float =
    with(density) {
        if (fontSize == TextUnit.Unspecified) {
            16.sp.toDp().value
        } else {
            fontSize.toDp().value
        }
    }

private fun TextStyle.resolveLineHeightCssPx(density: androidx.compose.ui.unit.Density): Float =
    with(density) {
        if (lineHeight == TextUnit.Unspecified) {
            resolveFontSizeCssPx(density) * 1.4f
        } else {
            lineHeight.toDp().value
        }
    }

private fun TextStyle.resolveLineHeightDp(density: androidx.compose.ui.unit.Density): Dp =
    with(density) {
        if (lineHeight == TextUnit.Unspecified) {
            22.dp
        } else {
            lineHeight.toDp()
        }
    }

private fun FontStyle.toCssValue(): String =
    when (this) {
        FontStyle.Italic -> "italic"
        else -> "normal"
    }

private fun TextAlign.toCssValue(): String =
    when (this) {
        TextAlign.Center -> "center"
        TextAlign.End, TextAlign.Right -> "right"
        TextAlign.Justify -> "justify"
        else -> "left"
    }

private fun Color.toCssColor(includeAlpha: Boolean = false): String {
    val argb = toArgb()
    return if (includeAlpha) {
        String.format(java.util.Locale.ROOT, "#%08X", argb)
    } else {
        String.format(java.util.Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    }
}

private fun isSafeUrlScheme(url: String): Boolean {
    val lower = url.trimStart().lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/") || lower.startsWith("#")
}

private fun openExternalUrl(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private class KatexHeightBridge(
    private val onHeightChanged: (Int) -> Unit,
) {
    @JavascriptInterface
    fun onContentHeight(height: Int) {
        onHeightChanged(height)
    }
}
