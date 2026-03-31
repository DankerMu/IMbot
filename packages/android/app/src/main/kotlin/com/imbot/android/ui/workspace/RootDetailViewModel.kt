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
        private val rootPath = savedStateHandle.get<String>(PATH_ARG).orEmpty().normalizeWorkspacePath()
        private var sessionsJob: Job? = null

        private val _uiState =
            MutableStateFlow(
                RootDetailUiState(
                    rootLabel = rootPath.defaultRootLabel(),
                    currentPath = rootPath,
                    breadcrumbs = rootPath.toRootBreadcrumbs(rootPath),
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
            val targetPath = clampToRoot(path)
            if (targetPath == _uiState.value.currentPath) {
                return
            }
            loadPath(targetPath)
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
            loadPath(clampToRoot(parentPath))
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
            val targetPath = clampToRoot(path)
            observeSessions(targetPath)

            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        currentPath = targetPath,
                        breadcrumbs = targetPath.toRootBreadcrumbs(rootPath),
                        isLoading = true,
                        error = null,
                    )
                }

                runCatching {
                    workspaceRepository.browseDirectory(
                        hostId = hostId,
                        path = targetPath,
                    )
                }.onSuccess { result ->
                    val resolvedPath = clampToRoot(result.path)
                    _uiState.update { current ->
                        current.copy(
                            currentPath = resolvedPath,
                            breadcrumbs = resolvedPath.toRootBreadcrumbs(rootPath),
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

        private fun clampToRoot(path: String): String {
            val candidate = path.normalizeWorkspacePath()
            return when {
                candidate.isBlank() -> rootPath
                rootPath == "/" -> candidate
                candidate == rootPath || candidate.startsWith("$rootPath/") -> candidate
                else -> rootPath
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

internal fun String.toRootBreadcrumbs(rootPath: String): List<DirectoryBreadcrumb> {
    val normalizedRoot = rootPath.normalizeWorkspacePath()
    val normalizedCurrent = normalizeWorkspacePath()
    return when {
        normalizedRoot.isBlank() || normalizedCurrent.isBlank() -> emptyList()
        normalizedRoot == "/" -> normalizedCurrent.toBreadcrumbs()
        else -> {
            var currentPath = normalizedRoot
            val relativeSegments =
                normalizedCurrent.removePrefix(normalizedRoot)
                    .trimStart('/')
                    .split('/')
                    .filter(String::isNotBlank)

            buildList {
                add(
                    DirectoryBreadcrumb(
                        label = normalizedRoot.defaultRootLabel(),
                        path = normalizedRoot,
                    ),
                )
                relativeSegments.forEach { segment ->
                    currentPath = "$currentPath/$segment"
                    add(
                        DirectoryBreadcrumb(
                            label = segment,
                            path = currentPath,
                        ),
                    )
                }
            }
        }
    }
}

private fun String.normalizeWorkspacePath(): String =
    when {
        isBlank() -> ""
        this == "/" -> "/"
        else -> trimEnd('/').ifBlank { "/" }
    }
