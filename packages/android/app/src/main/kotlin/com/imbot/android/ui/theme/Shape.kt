@file:Suppress("MatchingDeclarationName")

package com.imbot.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class IMbotSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
)

data class IMbotShadowTokens(
    val elevation: Dp = 2.dp,
    val ambientAlpha: Float = 0.08f,
    val spotAlpha: Float = 0.04f,
    val darkBorderWidth: Dp = 1.dp,
)

data class IMbotComponentShapes(
    val card: Shape = RoundedCornerShape(16.dp),
    val chip: Shape = RoundedCornerShape(8.dp),
    val button: Shape = RoundedCornerShape(999.dp),
    val pill: Shape = RoundedCornerShape(999.dp),
    val bottomSheet: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    val dialog: Shape = RoundedCornerShape(20.dp),
    val input: Shape = RoundedCornerShape(12.dp),
    val codeBlock: Shape = RoundedCornerShape(12.dp),
)

val IMbotMaterialShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )

val LocalIMbotComponentShapes = staticCompositionLocalOf { IMbotComponentShapes() }
val LocalIMbotSpacing = staticCompositionLocalOf { IMbotSpacing() }
val LocalIMbotShadowTokens = staticCompositionLocalOf { IMbotShadowTokens() }
