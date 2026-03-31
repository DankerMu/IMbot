@file:Suppress("MatchingDeclarationName")

package com.imbot.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class IMbotComponentShapes(
    val card: Shape = RoundedCornerShape(12.dp),
    val chip: Shape = RoundedCornerShape(8.dp),
    val button: Shape = RoundedCornerShape(20.dp),
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val dialog: Shape = RoundedCornerShape(28.dp),
    val input: Shape = RoundedCornerShape(12.dp),
    val codeBlock: Shape = RoundedCornerShape(8.dp),
)

val IMbotMaterialShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

val LocalIMbotComponentShapes = staticCompositionLocalOf { IMbotComponentShapes() }
