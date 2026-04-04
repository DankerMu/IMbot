package com.imbot.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF5C66D6)
val BrandBlueLight = Color(0xFFE8EBFF)
val Background = Color(0xFFF3EFE7)
val SurfaceLight = Color(0xFFFCFAF5)
val SurfaceSecondary = Color(0xFFF1ECE2)
val SurfaceTertiary = Color(0xFFE7E0D4)
val SeparatorLight = Color(0xFFD8D0C4)
val LabelPrimary = Color(0xFF181611)
val LabelSecondary = Color(0xFF6D675E)
val LabelTertiary = Color(0xFFB8B0A5)
val LabelQuaternary = Color(0xFF90887C)
val InlineCodeAccent = Color(0xFF9E5D39)
val InlineCodeBackground = Color(0xFFF1EBE2)
val SuccessColor = Color(0xFF1D8C66)
val SuccessOnSurface = Color(0xFF14533B)
val WarningColor = Color(0xFFC07A1D)
val DestructiveColor = Color(0xFFBF4A3F)
val UserBubbleLight = Color(0xFF22252B)
val UserBubbleLightText = Color(0xFFF9F5EE)
val AssistantBubbleLight = SurfaceLight
val CodeBlockHeaderBg = Color(0xFFF0EBE1)
val CodeBlockBorder = Color(0xFFD8D0C4)
val TerminalBg = Color(0xFF12100E)
val TerminalText = Color(0xFFF2ECE3)
val TerminalGreen = Color(0xFF46D1A1)

val BackgroundDark = Color(0xFF100F0D)
val SurfaceDark = Color(0xFF181614)
val SurfaceSecondaryDark = Color(0xFF1F1C19)
val SurfaceTertiaryDark = Color(0xFF2B2723)
val SeparatorDark = Color(0xFF3E392F)
val LabelPrimaryDark = Color(0xFFF6F1E8)
val LabelSecondaryDark = Color(0xFFB7AFA3)
val LabelTertiaryDark = Color(0xFF6E685E)
val UserBubbleDark = Color(0xFFF1ECE2)
val UserBubbleDarkText = Color(0xFF201E1A)
val AssistantBubbleDark = SurfaceSecondaryDark
val CodeBlockHeaderBgDark = Color(0xFF24201D)
val CodeBlockBorderDark = Color(0x663E392F)

val ProviderClaude = Color(0xFFC58C68)
val ProviderBook = Color(0xFF8E7AD9)
val ProviderOpenClaw = Color(0xFFDA7268)

val PrimaryLight = BrandBlue
val PrimaryDark = BrandBlue
val SecondaryLight = SuccessColor
val SecondaryDark = SuccessColor
val ErrorLight = DestructiveColor
val ErrorDark = DestructiveColor
val BackgroundLight = Background
val ClaudeAmber = ProviderClaude
val BookViolet = ProviderBook
val OpenClawRed = ProviderOpenClaw

data class ProviderColors(
    val claude: Color = ProviderClaude,
    val book: Color = ProviderBook,
    val openclaw: Color = ProviderOpenClaw,
)

data class StatusColors(
    val queued: Color,
    val running: Color,
    val idle: Color,
    val completed: Color,
    val failed: Color,
    val cancelled: Color,
)

val LightStatusColors =
    StatusColors(
        queued = Color(0xFF9CA3AF),
        running = Color(0xFF10B981),
        idle = Color(0xFF2196F3),
        completed = Color(0xFF059669),
        failed = Color(0xFFEF4444),
        cancelled = Color(0xFF6B7280),
    )

val DarkStatusColors =
    StatusColors(
        queued = Color(0xFF6B7280),
        running = Color(0xFF34D399),
        idle = Color(0xFF64B5F6),
        completed = Color(0xFF6EE7B7),
        failed = Color(0xFFFCA5A5),
        cancelled = Color(0xFF9CA3AF),
    )

val LocalProviderColors = staticCompositionLocalOf { ProviderColors() }
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

fun assistantBubbleBackground(useDarkTheme: Boolean): Color =
    if (useDarkTheme) {
        AssistantBubbleDark
    } else {
        AssistantBubbleLight
    }

fun assistantMessageTextColor(useDarkTheme: Boolean): Color =
    if (useDarkTheme) {
        Color(0xFFF3EDE4)
    } else {
        Color(0xFF241F1A)
    }

fun userBubbleBackground(useDarkTheme: Boolean): Color =
    if (useDarkTheme) {
        UserBubbleDark
    } else {
        UserBubbleLight
    }

fun userBubbleTextColor(useDarkTheme: Boolean): Color =
    if (useDarkTheme) {
        UserBubbleDarkText
    } else {
        UserBubbleLightText
    }

fun codeBlockHeaderBackground(useDarkTheme: Boolean): Color =
    if (useDarkTheme) {
        CodeBlockHeaderBgDark
    } else {
        CodeBlockHeaderBg
    }

fun codeBlockBorderColor(useDarkTheme: Boolean): Color =
    if (useDarkTheme) {
        CodeBlockBorderDark
    } else {
        CodeBlockBorder
    }

fun providerColorFor(
    provider: String,
    colors: ProviderColors = ProviderColors(),
): Color =
    when (provider.lowercase()) {
        "claude" -> colors.claude
        "book" -> colors.book
        "openclaw" -> colors.openclaw
        else -> colors.claude.copy(alpha = 0.5f)
    }

fun statusColorFor(
    status: String,
    colors: StatusColors = LightStatusColors,
): Color =
    when (status.lowercase()) {
        "queued" -> colors.queued
        "running" -> colors.running
        "idle" -> colors.idle
        "completed" -> colors.completed
        "failed" -> colors.failed
        "cancelled" -> colors.cancelled
        else -> colors.queued
    }
