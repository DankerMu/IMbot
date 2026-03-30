package com.imbot.android.network

sealed interface ConnectionState {
    data object NotConfigured : ConnectionState

    data object Connecting : ConnectionState

    data object Connected : ConnectionState

    data class Disconnected(
        val reason: String,
    ) : ConnectionState
}
