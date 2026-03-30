package com.imbot.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.ConnectionState
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWsClient
import com.imbot.android.network.ServerMessage
import com.imbot.android.network.toRelayBaseHttpUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val relayWsClient: RelayWsClient,
        private val relayHttpClient: RelayHttpClient,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _relayUrl = MutableStateFlow("")
        val relayUrl: StateFlow<String> = _relayUrl.asStateFlow()

        private val _token = MutableStateFlow("")
        val token: StateFlow<String> = _token.asStateFlow()

        private val _hostId = MutableStateFlow("macbook-1")
        val hostId: StateFlow<String> = _hostId.asStateFlow()

        private val _cwd = MutableStateFlow("/tmp/project")
        val cwd: StateFlow<String> = _cwd.asStateFlow()

        private val _prompt = MutableStateFlow("Say hello from IMbot Android prototype.")
        val prompt: StateFlow<String> = _prompt.asStateFlow()

        private val _events = MutableStateFlow<List<String>>(emptyList())
        val events: StateFlow<List<String>> = _events.asStateFlow()

        private val _sessionId = MutableStateFlow<String?>(null)
        val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

        private val _isCreating = MutableStateFlow(false)
        val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

        private val _noticeMessage = MutableStateFlow<String?>(null)
        val noticeMessage: StateFlow<String?> = _noticeMessage.asStateFlow()

        private val _noticeIsError = MutableStateFlow(false)
        val noticeIsError: StateFlow<Boolean> = _noticeIsError.asStateFlow()

        val connectionState: StateFlow<ConnectionState> = relayWsClient.connectionState

        init {
            val savedSettings = settingsRepository.load()
            _relayUrl.value = savedSettings.relayUrl
            _token.value = savedSettings.token

            if (savedSettings.isConfigured()) {
                val validationError = savedSettings.relayValidationError()
                if (validationError == null) {
                    relayWsClient.connect(savedSettings.relayUrl, savedSettings.token)
                } else {
                    updateNotice(
                        message = validationError,
                        isError = true,
                    )
                }
            }

            viewModelScope.launch {
                relayWsClient.rawMessages.collect { rawMessage ->
                    appendEvent(rawMessage)
                }
            }

            viewModelScope.launch {
                relayWsClient.messages.collect { message ->
                    message.toNoticeState()?.let { notice ->
                        updateNotice(
                            message = notice.message,
                            isError = notice.isError,
                        )
                    }
                }
            }
        }

        fun onRelayUrlChanged(value: String) {
            _relayUrl.value = value
        }

        fun onTokenChanged(value: String) {
            _token.value = value
        }

        fun onPrototypeInputChanged(
            field: PrototypeInputField,
            value: String,
        ) {
            when (field) {
                PrototypeInputField.HostId -> _hostId.value = value
                PrototypeInputField.Cwd -> _cwd.value = value
                PrototypeInputField.Prompt -> _prompt.value = value
            }
        }

        fun saveSettings() {
            val settings =
                RelaySettings(
                    relayUrl = _relayUrl.value.trim(),
                    token = _token.value.trim(),
                )
            val validationError = settings.relayValidationError()
            if (validationError != null) {
                updateNotice(
                    message = validationError,
                    isError = true,
                )
                return
            }

            applyRelaySettings(
                settings = settings,
                resetSession = true,
            )
        }

        fun createSession() {
            val settings =
                RelaySettings(
                    relayUrl = _relayUrl.value.trim(),
                    token = _token.value.trim(),
                )
            if (_isCreating.value || !settings.isConfigured()) {
                return
            }

            viewModelScope.launch {
                _isCreating.value = true
                updateNotice()

                val validationError = settings.relayValidationError()
                if (validationError != null) {
                    updateNotice(
                        message = validationError,
                        isError = true,
                    )
                    _isCreating.value = false
                    return@launch
                }

                if (settings != settingsRepository.load()) {
                    applyRelaySettings(
                        settings = settings,
                        resetSession = true,
                    )
                }

                relayHttpClient.createSession(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    provider = "claude",
                    hostId = _hostId.value.trim(),
                    cwd = _cwd.value.trim(),
                    prompt = _prompt.value.trim(),
                    permissionMode = "bypassPermissions",
                ).onSuccess { response ->
                    resetPrototypeSession()
                    _sessionId.value = response.sessionId
                    relayWsClient.subscribe(response.sessionId)
                    if (connectionState.value !is ConnectionState.Connected) {
                        updateNotice(message = DISCONNECTED_SESSION_WARNING)
                    }
                }.onFailure { error ->
                    updateNotice(
                        message = error.message ?: "Failed to create session",
                        isError = true,
                    )
                }

                _isCreating.value = false
            }
        }

        private fun applyRelaySettings(
            settings: RelaySettings,
            resetSession: Boolean,
        ) {
            settingsRepository.save(settings)
            if (resetSession) {
                resetPrototypeSession()
                relayWsClient.clearSubscription()
            }
            updateNotice()
            relayWsClient.connect(settings.relayUrl, settings.token)
        }

        private fun resetPrototypeSession() {
            _sessionId.value = null
            _events.value = emptyList()
        }

        private fun updateNotice(
            message: String? = null,
            isError: Boolean = false,
        ) {
            _noticeMessage.value = null
            _noticeIsError.value = false
            if (!message.isNullOrBlank()) {
                _noticeMessage.value = message
                _noticeIsError.value = isError
            }
        }

        private fun appendEvent(rawEvent: String) {
            _events.update { current ->
                (current + rawEvent).takeLast(MAX_EVENTS)
            }
        }

        private companion object {
            const val DISCONNECTED_SESSION_WARNING =
                "Session created while disconnected. Reconnect WebSocket to receive real-time events."
            const val MAX_EVENTS = 5_000
        }
    }

private data class NoticeState(
    val message: String,
    val isError: Boolean,
)

enum class PrototypeInputField {
    HostId,
    Cwd,
    Prompt,
}

private const val SECURE_RELAY_URL_ERROR = "Relay URL must use https:// or wss://."

private fun RelaySettings.relayValidationError(): String? =
    when {
        relayUrl.isBlank() -> null
        relayUrl.toRelayBaseHttpUrl() == null -> SECURE_RELAY_URL_ERROR
        else -> null
    }

private fun ServerMessage.toNoticeState(): NoticeState? =
    when (this) {
        is ServerMessage.Error ->
            NoticeState(
                message = message,
                isError = true,
            )

        is ServerMessage.HostStatus ->
            NoticeState(
                message = "Host $hostId: $status",
                isError = status != "online",
            )

        is ServerMessage.Status ->
            NoticeState(
                message = "Session status: $status",
                isError = status == "failed",
            )

        is ServerMessage.Event,
        ServerMessage.Pong,
        -> null
    }
