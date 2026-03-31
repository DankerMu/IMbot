@file:Suppress("FunctionName")

package com.imbot.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.imbot.android.data.ErrorState
import com.imbot.android.ui.theme.ClaudeAmber
import com.imbot.android.ui.theme.IMbotAnimations

sealed class ErrorScope {
    data object GLOBAL : ErrorScope()

    data object WORKSPACE : ErrorScope()

    data class SESSION(
        val id: String,
    ) : ErrorScope()
}

sealed interface ResolvedErrorBanner {
    data object Connection : ResolvedErrorBanner

    data class HostOffline(
        val message: String,
    ) : ResolvedErrorBanner

    data class SessionError(
        val message: String,
    ) : ResolvedErrorBanner
}

enum class ConnectionBannerState {
    Hidden,
    Disconnected,
    Recovered,
}

@Composable
internal fun rememberConnectionBannerState(relayConnected: Boolean): ConnectionBannerState {
    var showRecovered by remember { mutableStateOf(false) }
    var wasDisconnected by remember { mutableStateOf(!relayConnected) }

    LaunchedEffect(relayConnected) {
        if (!relayConnected) {
            wasDisconnected = true
            showRecovered = false
            return@LaunchedEffect
        }

        if (wasDisconnected) {
            showRecovered = true
            kotlinx.coroutines.delay(IMbotAnimations.BANNER_RECOVERY_DISPLAY_MS.toLong())
            showRecovered = false
            wasDisconnected = false
        }
    }

    return when {
        !relayConnected -> ConnectionBannerState.Disconnected
        showRecovered -> ConnectionBannerState.Recovered
        else -> ConnectionBannerState.Hidden
    }
}

@Composable
fun ErrorBannerHost(
    errorState: ErrorState,
    scope: ErrorScope,
    modifier: Modifier = Modifier,
    hostId: String? = null,
) {
    val connectionBannerState = rememberConnectionBannerState(errorState.relayConnected)
    val inlineBanner =
        if (connectionBannerState == ConnectionBannerState.Hidden) {
            resolveErrorBanner(
                errorState = errorState,
                scope = scope,
                hostId = hostId,
            )
        } else {
            null
        }

    when (inlineBanner) {
        ResolvedErrorBanner.Connection, null -> {
            ConnectionBanner(
                state = connectionBannerState,
                modifier = modifier,
            )
        }

        is ResolvedErrorBanner.HostOffline ->
            InlineErrorBanner(
                message = inlineBanner.message,
                modifier = modifier,
            )

        is ResolvedErrorBanner.SessionError ->
            InlineErrorBanner(
                message = inlineBanner.message,
                modifier = modifier,
            )
    }
}

internal fun resolveErrorBanner(
    errorState: ErrorState,
    scope: ErrorScope,
    hostId: String? = null,
): ResolvedErrorBanner? =
    when {
        !errorState.relayConnected || errorState.isReconnecting -> ResolvedErrorBanner.Connection
        scope != ErrorScope.GLOBAL && isHostOffline(errorState, hostId) ->
            ResolvedErrorBanner.HostOffline("MacBook 离线，请检查 companion 是否运行")

        scope is ErrorScope.SESSION && errorState.sessionErrors[scope.id] != null ->
            ResolvedErrorBanner.SessionError(errorState.sessionErrors.getValue(scope.id))

        else -> null
    }

private fun isHostOffline(
    errorState: ErrorState,
    hostId: String?,
): Boolean =
    when {
        hostId != null -> errorState.hostStatuses[hostId] == false
        else -> errorState.hostStatuses.values.any { online -> !online }
    }

@Composable
private fun InlineErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFF4E5),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = ClaudeAmber,
        )
    }
}
