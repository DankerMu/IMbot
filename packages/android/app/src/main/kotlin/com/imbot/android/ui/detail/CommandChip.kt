@file:Suppress("FunctionName")

package com.imbot.android.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imbot.android.ui.theme.BrandBlue
import com.imbot.android.ui.theme.LocalIMbotComponentShapes

@Composable
internal fun CommandChip(
    skill: SkillItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val componentShapes = LocalIMbotComponentShapes.current

    Surface(
        modifier = modifier,
        shape = componentShapes.pill,
        color = BrandBlue,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "/${skill.command}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "移除命令",
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = Color.White,
                )
            }
        }
    }
}
