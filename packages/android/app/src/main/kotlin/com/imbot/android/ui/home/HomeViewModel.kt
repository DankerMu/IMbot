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
    val selectedSessionIds: Set<String> = emptySet(),
    val isDeletingSelection: Boolean = false,
) {
    val isSelectionMode: Boolean
        get() = selectedSessionIds.isNotEmpty()

    val allVisibleSelected: Boolean
        get() = sessions.isNotEmpty() && sessions.all { session -> session.id in selectedSessionIds }
}

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
            val filteredSessions = applyFilterAndSort(allSessions, provider)
            _uiState.update { current ->
                val selectedSessionIds = reconcileSelection(filteredSessions, current.selectedSessionIds)
                current.copy(
                    filter = provider,
                    sessions = filteredSessions,
                    selectedSessionIds = selectedSessionIds,
                    isDeletingSelection = current.isDeletingSelection && selectedSessionIds.isNotEmpty(),
                )
            }
        }

        fun refresh() {
            refresh(initialLoad = false)
        }

        fun enterSelectionMode(sessionId: String) {
            _uiState.update { current ->
                current.copy(
                    selectedSessionIds = current.selectedSessionIds + sessionId,
                )
            }
        }

        fun toggleSessionSelection(sessionId: String) {
            _uiState.update { current ->
                current.copy(
                    selectedSessionIds = toggleSelectedSessionIds(current.selectedSessionIds, sessionId),
                )
            }
        }

        fun clearSelection() {
            _uiState.update { current ->
                current.copy(
                    selectedSessionIds = emptySet(),
                    isDeletingSelection = false,
                )
            }
        }

        fun toggleSelectAllVisibleSessions() {
            _uiState.update { current ->
                current.copy(
                    selectedSessionIds =
                        if (current.allVisibleSelected) {
                            emptySet()
                        } else {
                            current.sessions.mapTo(linkedSetOf(), SessionEntity::id)
                        },
                )
            }
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

        fun deleteSelectedSessions() {
            val selectedIds = _uiState.value.selectedSessionIds.toList()
            if (selectedIds.isEmpty() || _uiState.value.isDeletingSelection) {
                return
            }

            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(isDeletingSelection = true)
                }

                val failedIds = linkedSetOf<String>()
                selectedIds.forEach { sessionId ->
                    runCatching {
                        sessionRepository.deleteSession(sessionId)
                    }.onFailure {
                        failedIds += sessionId
                    }
                }

                _uiState.update { current ->
                    val failedSelection = reconcileSelection(current.sessions, failedIds)
                    current.copy(
                        selectedSessionIds = failedSelection,
                        isDeletingSelection = false,
                        error =
                            when {
                                failedIds.isEmpty() -> current.error
                                failedIds.size == selectedIds.size -> "删除会话失败，请重试"
                                else -> "已删除部分会话，剩余 ${failedIds.size} 个删除失败"
                            },
                    )
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
            val selectedSessionIds = reconcileSelection(filteredSessions, _uiState.value.selectedSessionIds)

            _uiState.update { current ->
                current.copy(
                    sessions = filteredSessions,
                    filter = currentFilter,
                    isLoading = allSessions.isEmpty() && !initialRefreshFinished,
                    isRefreshing = isRefreshing,
                    isConnected = relayWsClient.connectionState.value is ConnectionState.Connected,
                    runningSessionCount = allSessions.count { session -> isRunningStatus(session.status) },
                    selectedSessionIds = selectedSessionIds,
                    isDeletingSelection = current.isDeletingSelection && selectedSessionIds.isNotEmpty(),
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

internal fun toggleSelectedSessionIds(
    selectedSessionIds: Set<String>,
    sessionId: String,
): Set<String> =
    linkedSetOf<String>().apply {
        addAll(selectedSessionIds)
        if (!add(sessionId)) {
            remove(sessionId)
        }
    }

internal fun reconcileSelection(
    visibleSessions: List<SessionEntity>,
    selectedSessionIds: Set<String>,
): Set<String> {
    if (selectedSessionIds.isEmpty()) {
        return emptySet()
    }

    val visibleIds = visibleSessions.mapTo(hashSetOf(), SessionEntity::id)
    return selectedSessionIds.filterTo(linkedSetOf()) { sessionId -> sessionId in visibleIds }
}
