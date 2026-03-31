package com.imbot.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.data.repository.SessionRepository
import com.imbot.android.network.ConnectionState
import com.imbot.android.network.RelayWsClient
import com.imbot.android.network.ServerMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val filter: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false,
    val runningSessionCount: Int = 0,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val relayWsClient: RelayWsClient,
        private val sessionRepository: SessionRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                HomeUiState(
                    filter = settingsRepository.loadSessionProviderFilter(),
                    isConnected = relayWsClient.connectionState.value is ConnectionState.Connected,
                ),
            )
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private var allSessions: List<SessionEntity> = emptyList()
        private var initialRefreshFinished = false
        private var refreshJob: Job? = null

        init {
            observeSessions()
            observeConnectionState()
            observeServerMessages()
            refresh(initialLoad = true)
        }

        fun applyFilter(provider: String?) {
            settingsRepository.saveSessionProviderFilter(provider)
            _uiState.update { current ->
                current.copy(
                    filter = provider,
                    sessions = applyFilterAndSort(allSessions, provider),
                )
            }
        }

        fun refresh() {
            refresh(initialLoad = false)
        }

        fun deleteSession(sessionId: String) {
            viewModelScope.launch {
                runCatching {
                    sessionRepository.deleteSession(sessionId)
                }.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(error = error.message ?: "删除会话失败")
                    }
                }
            }
        }

        fun clearError() {
            _uiState.update { current ->
                current.copy(error = null)
            }
        }

        private fun observeSessions() {
            viewModelScope.launch {
                sessionRepository.getSessions().collect { sessions ->
                    allSessions = sessions
                    relayWsClient.setTrackedSessionIds(
                        sessions
                            .filter { session -> isLiveStatus(session.status) }
                            .map(SessionEntity::id)
                            .toSet(),
                    )
                    publishState()
                }
            }
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                relayWsClient.connectionState.collect {
                    publishState()
                }
            }
        }

        private fun observeServerMessages() {
            viewModelScope.launch {
                relayWsClient.messages.collect { message ->
                    if (message is ServerMessage.Status) {
                        sessionRepository.updateSessionStatus(
                            sessionId = message.sessionId,
                            status = message.status,
                        )
                    }
                }
            }
        }

        private fun refresh(initialLoad: Boolean) {
            if (refreshJob?.isActive == true) {
                return
            }

            refreshJob =
                viewModelScope.launch {
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = !initialLoad,
                            error = if (initialLoad) current.error else null,
                        )
                    }

                    runCatching {
                        sessionRepository.refreshFromApi()
                    }.onFailure { error ->
                        if (!initialLoad) {
                            _uiState.update { current ->
                                current.copy(error = error.message ?: "刷新失败，请检查网络")
                            }
                        } else if (allSessions.isEmpty()) {
                            _uiState.update { current ->
                                current.copy(error = error.message ?: "加载失败，请检查网络")
                            }
                        }
                    }

                    initialRefreshFinished = true
                    publishState(isRefreshing = false)
                }
        }

        private fun publishState(isRefreshing: Boolean = _uiState.value.isRefreshing) {
            val currentFilter = _uiState.value.filter
            val filteredSessions = applyFilterAndSort(allSessions, currentFilter)

            _uiState.update { current ->
                current.copy(
                    sessions = filteredSessions,
                    filter = currentFilter,
                    isLoading = allSessions.isEmpty() && !initialRefreshFinished,
                    isRefreshing = isRefreshing,
                    isConnected = relayWsClient.connectionState.value is ConnectionState.Connected,
                    runningSessionCount = allSessions.count { session -> isRunningStatus(session.status) },
                )
            }
        }
    }

internal fun applyFilterAndSort(
    sessions: List<SessionEntity>,
    filter: String?,
): List<SessionEntity> =
    sessions
        .filter { session -> filter == null || session.provider == filter }
        .sortedWith(
            compareByDescending<SessionEntity> { session -> isRunningStatus(session.status) }
                .thenByDescending(SessionEntity::lastActiveAt),
        )
