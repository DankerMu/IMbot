package com.imbot.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val PrimaryLight = Color(0xFF1A73E8)
val PrimaryDark = Color(0xFF8AB4F8)
val SecondaryLight = Color(0xFF34A853)
val SecondaryDark = Color(0xFF81C995)
val ErrorLight = Color(0xFFEA4335)
val ErrorDark = Color(0xFFF28B82)
val SurfaceLight = Color(0xFFFAFAFA)
val SurfaceDark = Color(0xFF1E1E1E)
val BackgroundLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF121212)

val ClaudeAmber = Color(0xFFD97706)
val BookViolet = Color(0xFF7C3AED)
val OpenClawRed = Color(0xFFDC2626)

data class ProviderColors(
    val claude: Color = ClaudeAmber,
    val book: Color = BookViolet,
    val openclaw: Color = OpenClawRed,
)

data class StatusColors(
    val queued: Color,
    val running: Color,
    val completed: Color,
    val failed: Color,
    val cancelled: Color,
)

val LightStatusColors =
    StatusColors(
        queued = Color(0xFF9CA3AF),
        running = Color(0xFF10B981),
        completed = Color(0xFF059669),
        failed = Color(0xFFEF4444),
        cancelled = Color(0xFF6B7280),
    )

val DarkStatusColors =
    StatusColors(
        queued = Color(0xFF6B7280),
        running = Color(0xFF34D399),
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
        else -> Color(0xFF9CA3AF)
    }

fun statusColorFor(
    status: String,
    colors: StatusColors = LightStatusColors,
): Color =
    when (status.lowercase()) {
        "queued" -> colors.queued
        "running" -> colors.running
        "completed" -> colors.completed
        "failed" -> colors.failed
        "cancelled" -> colors.cancelled
        else -> colors.queued
    }
