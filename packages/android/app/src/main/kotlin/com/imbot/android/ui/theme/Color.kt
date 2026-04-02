package com.imbot.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF007AFF)
val BrandBlueLight = Color(0xFFE3F2FF)
val Background = Color(0xFFF2F2F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceSecondary = Color(0xFFF9F9F9)
val SurfaceTertiary = Color(0xFFF2F2F7)
val SeparatorLight = Color(0x33000000)
val LabelPrimary = Color(0xFF000000)
val LabelSecondary = Color(0xFF6C6C70)
val LabelTertiary = Color(0xFFC7C7CC)
val SuccessColor = Color(0xFF34C759)
val SuccessOnSurface = Color(0xFF1B7A36)
val WarningColor = Color(0xFFFF9500)
val DestructiveColor = Color(0xFFFF3B30)

val BackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceSecondaryDark = Color(0xFF2C2C2E)
val SurfaceTertiaryDark = Color(0xFF3A3A3C)
val SeparatorDark = Color(0x33FFFFFF)
val LabelPrimaryDark = Color(0xFFFFFFFF)
val LabelSecondaryDark = Color(0xFF8E8E93)

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

fun providerColorFor(
    provider: String,
    colors: ProviderColors = ProviderColors(),
): Color =
    when (provider.lowercase()) {
        "claude" -> colors.claude
        "book" -> colors.book
        "openclaw" -> colors.openclaw
        else -> LabelTertiary
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
