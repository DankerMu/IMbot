package com.imbot.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.ConnectionState
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
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

        val connectionState: StateFlow<ConnectionState> = relayWsClient.connectionState

        init {
            val savedSettings = settingsRepository.load()
            _relayUrl.value = savedSettings.relayUrl
            _token.value = savedSettings.token

            if (savedSettings.isConfigured()) {
                relayWsClient.connect(savedSettings.relayUrl, savedSettings.token)
            }

            viewModelScope.launch {
                relayWsClient.rawMessages.collect { rawMessage ->
                    appendEvent(rawMessage)
                }
            }
        }

        fun onRelayUrlChanged(value: String) {
            _relayUrl.value = value
        }

        fun onTokenChanged(value: String) {
            _token.value = value
        }

        fun onHostIdChanged(value: String) {
            _hostId.value = value
        }

        fun onCwdChanged(value: String) {
            _cwd.value = value
        }

        fun onPromptChanged(value: String) {
            _prompt.value = value
        }

        fun saveSettings() {
            val settings =
                RelaySettings(
                    relayUrl = _relayUrl.value.trim(),
                    token = _token.value.trim(),
                )
            settingsRepository.save(settings)
            relayWsClient.connect(settings.relayUrl, settings.token)
        }

        fun createSession() {
            if (_isCreating.value || connectionState.value !is ConnectionState.Connected) {
                return
            }

            viewModelScope.launch {
                _isCreating.value = true

                relayHttpClient.createSession(
                    relayUrl = _relayUrl.value.trim(),
                    token = _token.value.trim(),
                    provider = "claude",
                    hostId = _hostId.value.trim(),
                    cwd = _cwd.value.trim(),
                    prompt = _prompt.value.trim(),
                    permissionMode = "bypassPermissions",
                ).onSuccess { response ->
                    _sessionId.value = response.sessionId
                    relayWsClient.subscribe(response.sessionId)
                    appendEvent(
                        JSONObject()
                            .put("type", "local")
                            .put("action", "session_created")
                            .put("session_id", response.sessionId)
                            .toString(),
                    )
                }.onFailure { error ->
                    appendEvent(
                        JSONObject()
                            .put("type", "local_error")
                            .put("message", error.message ?: "Failed to create session")
                            .toString(),
                    )
                }

                _isCreating.value = false
            }
        }

        private fun appendEvent(rawEvent: String) {
            _events.update { current ->
                (current + rawEvent).takeLast(MAX_EVENTS)
            }
        }

        private companion object {
            const val MAX_EVENTS = 5_000
        }
    }
