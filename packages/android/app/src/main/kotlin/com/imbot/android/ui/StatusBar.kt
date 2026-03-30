@file:Suppress("FunctionName")

package com.imbot.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imbot.android.R
import com.imbot.android.network.ConnectionState

@Composable
fun StatusBar(connectionState: ConnectionState) {
    val (label, accentColor) =
        when (connectionState) {
            ConnectionState.NotConfigured -> {
                stringResource(R.string.connection_not_configured) to MaterialTheme.colorScheme.outline
            }

            ConnectionState.Connecting -> {
                stringResource(R.string.connection_connecting) to Color(0xFFE6A700)
            }

            ConnectionState.Connected -> {
                stringResource(R.string.connection_connected) to Color(0xFF1B873F)
            }

            is ConnectionState.Disconnected -> {
                val message =
                    "${stringResource(R.string.connection_disconnected)}: ${connectionState.reason}"
                message to MaterialTheme.colorScheme.error
            }
        }

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = accentColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = accentColor.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(12.dp),
    )
}
