@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.LocalUseDarkTheme

private const val MAX_DIFF_LINES = 100

@Composable
internal fun DiffView(
    oldText: String,
    newText: String,
    modifier: Modifier = Modifier,
) {
    val oldAllLines = oldText.lines()
    val newAllLines = newText.lines()
    val oldLines = oldAllLines.take(MAX_DIFF_LINES)
    val newLines = newAllLines.take(MAX_DIFF_LINES)
    val oldTruncated = oldAllLines.size > MAX_DIFF_LINES
    val newTruncated = newAllLines.size > MAX_DIFF_LINES
    val isDarkTheme = LocalUseDarkTheme.current
    val removedBackground = androidx.compose.ui.graphics.Color(0x1AFF3B30)
    val removedForeground =
        if (isDarkTheme) {
            androidx.compose.ui.graphics.Color(0xFFFF6B6B)
        } else {
            androidx.compose.ui.graphics.Color(0xFFCC3333)
        }
    val addedBackground = androidx.compose.ui.graphics.Color(0x1A34C759)
    val addedForeground =
        if (isDarkTheme) {
            androidx.compose.ui.graphics.Color(0xFF4ADE80)
        } else {
            androidx.compose.ui.graphics.Color(0xFF228B22)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        SelectionContainer {
            Column(modifier = Modifier.fillMaxWidth()) {
                oldLines.forEach { line ->
                    Text(
                        text = "- $line",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = removedForeground,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(removedBackground)
                                .padding(horizontal = 12.dp, vertical = 1.dp),
                    )
                }
                newLines.forEach { line ->
                    Text(
                        text = "+ $line",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = addedForeground,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(addedBackground)
                                .padding(horizontal = 12.dp, vertical = 1.dp),
                    )
                }
            }
        }

        if (oldTruncated || newTruncated) {
            Text(
                text = "... truncated",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
