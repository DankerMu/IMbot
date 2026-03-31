package com.imbot.android.ui.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.local.SessionEntity
import com.imbot.android.data.repository.SessionStore
import com.imbot.android.data.repository.WorkspaceRepository
import com.imbot.android.network.BrowseEntry
import com.imbot.android.ui.newsession.DirectoryBreadcrumb
import com.imbot.android.ui.newsession.toBreadcrumbs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootDetailUiState(
    val rootLabel: String = "",
    val currentPath: String = "",
    val breadcrumbs: List<DirectoryBreadcrumb> = emptyList(),
    val directories: List<BrowseEntry> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class RootDetailViewModel
    @Inject
    constructor(
        private val workspaceRepository: WorkspaceRepository,
        private val sessionStore: SessionStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val rootId = savedStateHandle.get<String>(ROOT_ID_ARG).orEmpty()
        private val hostId = savedStateHandle.get<String>(HOST_ID_ARG).orEmpty()
        private val rootPath = savedStateHandle.get<String>(PATH_ARG).orEmpty()
        private var sessionsJob: Job? = null

        private val _uiState =
            MutableStateFlow(
                RootDetailUiState(
                    rootLabel = rootPath.defaultRootLabel(),
                    currentPath = rootPath,
                    breadcrumbs = rootPath.toBreadcrumbs(),
                ),
            )
        val uiState: StateFlow<RootDetailUiState> = _uiState.asStateFlow()

        init {
            if (hostId.isBlank() || rootPath.isBlank()) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = "目录参数无效",
                    )
                }
            } else {
                loadRootMetadata()
                loadPath(rootPath)
            }
        }

        fun navigateToSubdirectory(path: String) {
            if (path.isBlank() || path == _uiState.value.currentPath) {
                return
            }
            loadPath(path)
        }

        fun navigateUp() {
            val currentPath = _uiState.value.currentPath
            if (currentPath == rootPath) {
                return
            }

            val normalized = currentPath.trimEnd('/')
            val parentPath =
                normalized.substringBeforeLast('/', missingDelimiterValue = rootPath)
                    .ifBlank { "/" }
                    .let { candidate ->
                        if (candidate.length < rootPath.length) {
                            rootPath
                        } else {
                            candidate
                        }
                    }

            loadPath(parentPath)
        }

        fun retry() {
            loadPath(_uiState.value.currentPath.ifBlank { rootPath })
        }

        private fun loadRootMetadata() {
            viewModelScope.launch {
                runCatching {
                    workspaceRepository.getHostsWithRoots()
                }.onSuccess { hosts ->
                    val matchingRoot =
                        hosts.flatMap { it.roots }
                            .firstOrNull { root ->
                                root.id == rootId
                            }

                    if (matchingRoot != null) {
                        _uiState.update { current ->
                            current.copy(
                                rootLabel = matchingRoot.label ?: matchingRoot.path.defaultRootLabel(),
                            )
                        }
                    }
                }
            }
        }

        private fun loadPath(path: String) {
            observeSessions(path)

            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        currentPath = path,
                        breadcrumbs = path.toBreadcrumbs(),
                        isLoading = true,
                        error = null,
                    )
                }

                runCatching {
                    workspaceRepository.browseDirectory(
                        hostId = hostId,
                        path = path,
                    )
                }.onSuccess { result ->
                    _uiState.update { current ->
                        current.copy(
                            currentPath = result.path,
                            breadcrumbs = result.path.toBreadcrumbs(),
                            directories = result.directories.sortedBy(BrowseEntry::name),
                            isLoading = false,
                            error = null,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            error = error.message ?: "加载目录失败",
                        )
                    }
                }
            }
        }

        private fun observeSessions(path: String) {
            sessionsJob?.cancel()
            sessionsJob =
                viewModelScope.launch {
                    sessionStore.getSessionsByPathPrefix(path).collect { sessions ->
                        _uiState.update { current ->
                            current.copy(sessions = sessions)
                        }
                    }
                }
        }

        companion object {
            const val ROOT_ID_ARG = "rootId"
            const val HOST_ID_ARG = "hostId"
            const val PATH_ARG = "path"
        }
    }
