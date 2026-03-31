package com.imbot.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.repository.SessionStore
import com.imbot.android.data.repository.WorkspaceRepository
import com.imbot.android.network.ConnectionState
import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayWsClient
import com.imbot.android.network.ServerMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val relayUrl: String = "",
    val themeMode: String = "system",
    val connectionState: ConnectionState = ConnectionState.NotConfigured,
    val macbookStatus: String = "offline",
    val openClawStatus: String = "offline",
)

sealed interface SettingsEvent {
    data class ShowMessage(
        val message: String,
    ) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val relayWsClient: RelayWsClient,
        private val sessionStore: SessionStore,
        private val workspaceRepository: WorkspaceRepository,
    ) : ViewModel() {
        private val hostTypeById = mutableMapOf<String, String>()
        private val _hostStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
        val hostStatuses: StateFlow<Map<String, String>> = _hostStatuses.asStateFlow()

        private val _uiState =
            MutableStateFlow(
                SettingsUiState(
                    relayUrl = settingsRepository.load().relayUrl,
                    themeMode = settingsRepository.loadThemeMode(),
                    connectionState = relayWsClient.connectionState.value,
                ),
            )
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<SettingsEvent>()
        val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

        init {
            observeRelayUrl()
            observeThemeMode()
            observeConnectionState()
            observeHostStatuses()
            loadCurrentHosts()
        }

        fun setTheme(mode: String) {
            settingsRepository.saveThemeMode(mode)
        }

        fun updateRelayUrl(url: String) {
            val currentSettings = settingsRepository.load()
            val updatedSettings =
                RelaySettings(
                    relayUrl = url.trim(),
                    token = currentSettings.token,
                )
            settingsRepository.save(updatedSettings)
            relayWsClient.connect(updatedSettings.relayUrl, updatedSettings.token)
        }

        fun clearCache() {
            viewModelScope.launch {
                runCatching {
                    sessionStore.clearLocalCache()
                }.onSuccess {
                    _events.emit(SettingsEvent.ShowMessage("已清除"))
                }.onFailure { error ->
                    _events.emit(SettingsEvent.ShowMessage(error.message ?: "清除失败，请重试"))
                }
            }
        }

        private fun observeRelayUrl() {
            viewModelScope.launch {
                settingsRepository.observeRelayUrl().collect { relayUrl ->
                    _uiState.update { current ->
                        current.copy(relayUrl = relayUrl)
                    }
                }
            }
        }

        private fun observeThemeMode() {
            viewModelScope.launch {
                settingsRepository.observeThemeMode().collect { themeMode ->
                    _uiState.update { current ->
                        current.copy(themeMode = themeMode)
                    }
                }
            }
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                relayWsClient.connectionState.collect { connectionState ->
                    _uiState.update { current ->
                        current.copy(connectionState = connectionState)
                    }
                }
            }
        }

        private fun observeHostStatuses() {
            viewModelScope.launch {
                relayWsClient.messages.collect { message ->
                    if (message is ServerMessage.HostStatus) {
                        updateHostStatus(message.hostId, message.status)
                    }
                }
            }
        }

        private fun loadCurrentHosts() {
            viewModelScope.launch {
                runCatching {
                    workspaceRepository.getHostsWithRoots()
                }.onSuccess { hosts ->
                    hosts.forEach { hostWithRoots ->
                        rememberHost(hostWithRoots.host)
                        updateHostStatus(hostWithRoots.host.id, hostWithRoots.host.status)
                    }
                }
            }
        }

        private fun rememberHost(host: RelayHost) {
            hostTypeById[host.id] = host.type
        }

        private fun updateHostStatus(
            hostId: String,
            status: String,
        ) {
            val nextStatuses = _hostStatuses.value + (hostId to status)
            _hostStatuses.value = nextStatuses

            val macbookStatus =
                hostTypeById
                    .filterValues { type -> type == "macbook" || type == "companion" }
                    .keys
                    .firstNotNullOfOrNull(nextStatuses::get)
                    ?: _uiState.value.macbookStatus

            val openClawStatus =
                hostTypeById
                    .filterValues { type -> type == "relay_local" || type == "relay" }
                    .keys
                    .firstNotNullOfOrNull(nextStatuses::get)
                    ?: _uiState.value.openClawStatus

            _uiState.update { current ->
                current.copy(
                    macbookStatus = macbookStatus,
                    openClawStatus = openClawStatus,
                )
            }
        }
    }
