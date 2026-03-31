package com.imbot.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ErrorState(
    val relayConnected: Boolean = true,
    val isReconnecting: Boolean = false,
    val hostStatuses: Map<String, Boolean> = emptyMap(),
    val sessionErrors: Map<String, String> = emptyMap(),
)

@Singleton
class ErrorStateManager
    @Inject
    constructor() {
        private val _errorState = MutableStateFlow(ErrorState())
        val errorState: StateFlow<ErrorState> = _errorState.asStateFlow()

        fun setRelayConnected(connected: Boolean) {
            _errorState.update { current ->
                current.copy(
                    relayConnected = connected,
                    isReconnecting = !connected,
                )
            }
        }

        fun setHostStatus(
            hostId: String,
            online: Boolean,
        ) {
            val normalizedHostId = hostId.trim()
            if (normalizedHostId.isBlank()) {
                return
            }

            _errorState.update { current ->
                current.copy(
                    hostStatuses = current.hostStatuses + (normalizedHostId to online),
                )
            }
        }

        fun setSessionError(
            sessionId: String,
            message: String,
        ) {
            val normalizedSessionId = sessionId.trim()
            val normalizedMessage = message.trim()
            if (normalizedSessionId.isBlank() || normalizedMessage.isBlank()) {
                return
            }

            _errorState.update { current ->
                current.copy(
                    sessionErrors = current.sessionErrors + (normalizedSessionId to normalizedMessage),
                )
            }
        }

        fun clearSessionError(sessionId: String) {
            val normalizedSessionId = sessionId.trim()
            if (normalizedSessionId.isBlank()) {
                return
            }

            _errorState.update { current ->
                current.copy(
                    sessionErrors = current.sessionErrors - normalizedSessionId,
                )
            }
        }
    }
