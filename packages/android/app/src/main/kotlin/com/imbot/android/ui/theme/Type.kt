package com.imbot.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.imbot.android.R

val InterFontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

val JetBrainsMonoFamily =
    FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    )

val CodeFontFamily = JetBrainsMonoFamily

val CodeTextStyle =
    TextStyle(
        fontFamily = CodeFontFamily,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    )

val IMbotTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.2).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.9).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.55).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.28).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.22).sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.08).sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        bodySmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Normal,
            ),
        labelLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.12.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.28.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
            ),
    )
