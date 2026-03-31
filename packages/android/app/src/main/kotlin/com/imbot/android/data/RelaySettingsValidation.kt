package com.imbot.android.data

import com.imbot.android.network.toRelayBaseHttpUrl

private const val RELAY_URL_ERROR = "Relay URL must use https:// or wss://."

fun RelaySettings.relayValidationError(): String? =
    when {
        relayUrl.isBlank() -> null
        relayUrl.toRelayBaseHttpUrl() == null -> RELAY_URL_ERROR
        else -> null
    }
