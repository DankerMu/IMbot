package com.imbot.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF4F46E5)
val BrandBlueLight = Color(0xFFEEF2FF)
val Background = Color(0xFFF5F7F9)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceSecondary = Color(0xFFF1F5F9)
val SurfaceTertiary = Color(0xFFF5F7F9)
val SeparatorLight = Color(0x33000000)
val LabelPrimary = Color(0xFF1E293B)
val LabelSecondary = Color(0xFF64748B)
val LabelTertiary = Color(0xFFCBD5E1)
val SuccessColor = Color(0xFF34C759)
val SuccessOnSurface = Color(0xFF1B7A36)
val WarningColor = Color(0xFFFF9500)
val DestructiveColor = Color(0xFFFF3B30)
val UserBubbleLight = Color(0xFF4F46E5)
val UserBubbleLightText = Color(0xFFFFFFFF)
val AssistantBubbleLight = SurfaceLight
val CodeBlockHeaderBg = Color(0xFFF8FAFB)
val CodeBlockBorder = Color(0xFFE5E7EB)
val TerminalBg = Color(0xFF0A0A0A)
val TerminalText = Color(0xFFE4E4E7)
val TerminalGreen = Color(0xFF34C759)

val BackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceSecondaryDark = Color(0xFF2C2C2E)
val SurfaceTertiaryDark = Color(0xFF3A3A3C)
val SeparatorDark = Color(0x33FFFFFF)
val LabelPrimaryDark = Color(0xFFFFFFFF)
val LabelSecondaryDark = Color(0xFF8E8E93)
val LabelTertiaryDark = Color(0xFF48484A)
val UserBubbleDark = Color(0xFFE5E7EB)
val UserBubbleDarkText = Color(0xFF1F2937)
val AssistantBubbleDark = SurfaceSecondaryDark
val CodeBlockHeaderBgDark = Color(0xFF2C2C2E)
val CodeBlockBorderDark = Color(0x1AFFFFFF)

val ProviderClaude = Color(0xFFD4956A)
val ProviderBook = Color(0xFF9B7ED8)
val ProviderOpenClaw = Color(0xFFE07070)

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
        Color(0xFFF3F4F6)
    } else {
        Color(0xFF1F2937)
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
