@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imbot.android.ui.theme.LocalIMbotComponentShapes

internal enum class CommandChipVariant {
    Standalone,
    Inline,
}

@Composable
internal fun CommandChip(
    skill: SkillItem,
    onDismiss: () -> Unit,
    variant: CommandChipVariant = CommandChipVariant.Standalone,
    modifier: Modifier = Modifier,
) {
    val componentShapes = LocalIMbotComponentShapes.current
    val isInline = variant == CommandChipVariant.Inline
    val containerColor =
        if (isInline) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        }
    val borderColor =
        if (isInline) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
        }
    val contentColor =
        if (isInline) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        }
    val minHeight = if (isInline) 22.dp else 24.dp
    val horizontalSpacing = if (isInline) 3.dp else 4.dp
    val iconTouchTarget = if (isInline) 12.dp else 14.dp
    val iconSize = if (isInline) 8.dp else 10.dp
    val textStyle =
        MaterialTheme.typography.labelSmall.copy(
            letterSpacing = if (isInline) 0.sp else 0.05.sp,
        )

    Surface(
        modifier = modifier.heightIn(min = minHeight),
        shape = componentShapes.pill,
        color = containerColor,
        border = BorderStroke(0.75.dp, borderColor),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = if (isInline) 7.dp else 8.dp,
                    end = if (isInline) 5.dp else 6.dp,
                    top = 3.dp,
                    bottom = 3.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "/${skill.command}",
                style = textStyle,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
            Box(
                modifier =
                    Modifier
                        .size(iconTouchTarget)
                        .clickable(onClick = onDismiss),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "移除命令",
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(iconSize),
                    tint = contentColor.copy(alpha = 0.72f),
                )
            }
        }
    }
}
