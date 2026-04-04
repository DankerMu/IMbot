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
    val elevation: Dp = 8.dp,
    val ambientAlpha: Float = 0.1f,
    val spotAlpha: Float = 0.05f,
    val darkBorderWidth: Dp = 1.dp,
)

data class IMbotComponentShapes(
    val card: Shape = RoundedCornerShape(22.dp),
    val chip: Shape = RoundedCornerShape(14.dp),
    val button: Shape = RoundedCornerShape(16.dp),
    val pill: Shape = RoundedCornerShape(999.dp),
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val dialog: Shape = RoundedCornerShape(24.dp),
    val input: Shape = RoundedCornerShape(18.dp),
    val codeBlock: Shape = RoundedCornerShape(14.dp),
    val codeBlockHeader: Shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    val userMessageBubble: Shape =
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp,
        ),
    val assistantMessageBubble: Shape =
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp,
        ),
)

val IMbotMaterialShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

val LocalIMbotComponentShapes = staticCompositionLocalOf { IMbotComponentShapes() }
val LocalIMbotSpacing = staticCompositionLocalOf { IMbotSpacing() }
val LocalIMbotShadowTokens = staticCompositionLocalOf { IMbotShadowTokens() }
