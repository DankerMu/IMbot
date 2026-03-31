package com.imbot.android.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.repository.HostWithRoots
import com.imbot.android.data.repository.WorkspaceRepository
import com.imbot.android.network.BrowseEntry
import com.imbot.android.network.RelayApiException
import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayWorkspaceRoot
import com.imbot.android.network.RelayWsClient
import com.imbot.android.network.ServerMessage
import com.imbot.android.ui.newsession.DirectoryBreadcrumb
import com.imbot.android.ui.newsession.toBreadcrumbs
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

data class WorkspaceUiState(
    val hosts: List<HostWithRoots> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val pendingRemoval: PendingRootRemoval? = null,
)

data class AddRootUiState(
    val isVisible: Boolean = false,
    val provider: String? = null,
    val hostId: String? = null,
    val hostName: String? = null,
    val currentPath: String = "",
    val breadcrumbs: List<DirectoryBreadcrumb> = emptyList(),
    val directories: List<BrowseEntry> = emptyList(),
    val label: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
    val isLabelDirty: Boolean = false,
)

data class PendingRootRemoval(
    val hostId: String,
    val rootId: String,
    val label: String,
)

sealed interface WorkspaceEvent {
    data class ShowMessage(
        val message: String,
    ) : WorkspaceEvent
}

@HiltViewModel
@Suppress("TooManyFunctions")
class WorkspaceViewModel
    @Inject
    constructor(
        private val workspaceRepository: WorkspaceRepository,
        private val relayWsClient: RelayWsClient,
    ) : ViewModel() {
        private var browseGeneration = 0
        private val _uiState = MutableStateFlow(WorkspaceUiState())
        val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

        private val _addRootState = MutableStateFlow(AddRootUiState())
        val addRootState: StateFlow<AddRootUiState> = _addRootState.asStateFlow()

        private val _events = MutableSharedFlow<WorkspaceEvent>()
        val events: SharedFlow<WorkspaceEvent> = _events.asSharedFlow()

        init {
            observeHostStatuses()
            refresh(initialLoad = true)
        }

        fun refresh() {
            refresh(initialLoad = false)
        }

        fun showAddRootSheet() {
            browseGeneration++
            _addRootState.value = AddRootUiState(isVisible = true)
        }

        fun dismissAddRootSheet() {
            browseGeneration++
            _addRootState.value = AddRootUiState()
        }

        fun selectProvider(provider: String) {
            val hosts = _uiState.value.hosts.map(HostWithRoots::host)
            val resolvedHost = resolveWorkspaceHost(provider, hosts)
            val hostName = resolvedHost?.name
            val hostId = resolvedHost?.id
            val canBrowse = resolvedHost != null && resolvedHost.status == STATUS_ONLINE
            val initialPath = resolvedHost?.takeIf { canBrowse }?.let { defaultBrowsePath(provider, it) }.orEmpty()

            browseGeneration++
            _addRootState.update { current ->
                current.copy(
                    provider = provider,
                    hostId = hostId,
                    hostName = hostName,
                    currentPath = initialPath,
                    breadcrumbs = initialPath.toBreadcrumbs(),
                    directories = emptyList(),
                    label = initialPath.takeIf(String::isNotBlank)?.defaultRootLabel().orEmpty(),
                    error =
                        when {
                            resolvedHost == null -> "未找到可用主机"
                            resolvedHost.status != STATUS_ONLINE -> "${resolvedHost.name} 离线，无法浏览目录"
                            else -> null
                        },
                    warning = null,
                    isLoading = false,
                    isSubmitting = false,
                    isLabelDirty = false,
                )
            }

            if (resolvedHost != null && resolvedHost.status == STATUS_ONLINE && initialPath.isNotBlank()) {
                browseAddRootDirectory(initialPath)
            }
        }

        fun browseAddRootDirectory(path: String) {
            val state = _addRootState.value
            val hostId = state.hostId ?: return
            if (state.isLoading) {
                return
            }
            val generation = ++browseGeneration

            viewModelScope.launch {
                if (generation != browseGeneration) {
                    return@launch
                }
                _addRootState.update { current ->
                    current.copy(
                        isLoading = true,
                        error = null,
                        warning = null,
                    )
                }

                runCatching {
                    workspaceRepository.browseDirectory(
                        hostId = hostId,
                        path = path,
                    )
                }.onSuccess { result ->
                    if (generation != browseGeneration) {
                        return@onSuccess
                    }
                    val sortedDirectories = result.directories.sortedBy(BrowseEntry::name)
                    val truncated = sortedDirectories.size > MAX_BROWSE_ENTRIES
                    _addRootState.update { current ->
                        current.copy(
                            currentPath = result.path,
                            breadcrumbs = result.path.toBreadcrumbs(),
                            directories = sortedDirectories.take(MAX_BROWSE_ENTRIES),
                            label =
                                if (current.isLabelDirty) {
                                    current.label
                                } else {
                                    current.label.takeIf { it.isNotBlank() } ?: result.path.defaultRootLabel()
                                },
                            isLoading = false,
                            error = null,
                            warning = if (truncated) "目录条目过多，仅显示前 $MAX_BROWSE_ENTRIES 项" else null,
                        )
                    }
                }.onFailure { error ->
                    if (generation != browseGeneration) {
                        return@onFailure
                    }
                    _addRootState.update { current ->
                        current.copy(
                            isLoading = false,
                            error = error.message ?: "浏览目录失败",
                            warning = null,
                        )
                    }
                }
            }
        }

        fun updateAddRootLabel(label: String) {
            _addRootState.update { current ->
                current.copy(
                    label = label,
                    isLabelDirty = true,
                )
            }
        }

        fun browseAddRootUp() {
            val breadcrumbs = _addRootState.value.breadcrumbs
            if (breadcrumbs.size <= 1) {
                return
            }
            val parentPath = breadcrumbs[breadcrumbs.lastIndex - 1].path
            browseAddRootDirectory(parentPath)
        }

        fun submitAddRoot() {
            val state = _addRootState.value
            if (state.isSubmitting) {
                return
            }

            val provider = state.provider
            val hostId = state.hostId
            val path = state.currentPath
            if (provider.isNullOrBlank() || hostId.isNullOrBlank() || path.isBlank()) {
                _addRootState.update { current ->
                    current.copy(error = "请先选择目录")
                }
                return
            }

            viewModelScope.launch {
                _addRootState.update { current ->
                    current.copy(
                        isSubmitting = true,
                        error = null,
                    )
                }

                val finalLabel = state.label.trim().ifBlank { path.defaultRootLabel() }

                runCatching {
                    workspaceRepository.addRoot(
                        hostId = hostId,
                        provider = provider,
                        path = path,
                        label = finalLabel,
                    )
                }.onSuccess { root ->
                    _uiState.update { current ->
                        current.copy(
                            hosts =
                                current.hosts.map { hostWithRoots ->
                                    if (hostWithRoots.host.id != hostId) {
                                        hostWithRoots
                                    } else {
                                        hostWithRoots.copy(
                                            roots =
                                                (hostWithRoots.roots + root)
                                                    .sortedBy(RelayWorkspaceRoot::createdAt),
                                        )
                                    }
                                },
                        )
                    }
                    dismissAddRootSheet()
                }.onFailure { error ->
                    _addRootState.update { current ->
                        current.copy(
                            isSubmitting = false,
                            error = mapAddRootError(error),
                        )
                    }
                }
            }
        }

        fun requestRemoveRoot(
            hostId: String,
            rootId: String,
            label: String,
        ) {
            _uiState.update { current ->
                current.copy(
                    pendingRemoval =
                        PendingRootRemoval(
                            hostId = hostId,
                            rootId = rootId,
                            label = label,
                        ),
                )
            }
        }

        fun dismissRemoveRootDialog() {
            _uiState.update { current ->
                current.copy(pendingRemoval = null)
            }
        }

        fun confirmRemoveRoot() {
            val pendingRemoval = _uiState.value.pendingRemoval ?: return
            dismissRemoveRootDialog()

            viewModelScope.launch {
                val result =
                    runCatching {
                        workspaceRepository.removeRoot(
                            hostId = pendingRemoval.hostId,
                            rootId = pendingRemoval.rootId,
                        )
                    }

                if (result.isSuccess) {
                    _uiState.update { current ->
                        current.copy(
                            hosts =
                                current.hosts.map { hostWithRoots ->
                                    if (hostWithRoots.host.id != pendingRemoval.hostId) {
                                        hostWithRoots
                                    } else {
                                        hostWithRoots.copy(
                                            roots =
                                                hostWithRoots.roots.filterNot { root ->
                                                    root.id == pendingRemoval.rootId
                                                },
                                        )
                                    }
                                },
                        )
                    }
                    _events.emit(WorkspaceEvent.ShowMessage("已移除"))
                } else {
                    _events.emit(WorkspaceEvent.ShowMessage("删除失败，请重试"))
                }
            }
        }

        private fun refresh(initialLoad: Boolean) {
            val current = _uiState.value
            if (current.isLoading && initialLoad) {
                viewModelScope.launch {
                    loadWorkspace(initialLoad = true)
                }
                return
            }
            if (current.isRefreshing || _uiState.value.isLoading) {
                return
            }

            viewModelScope.launch {
                loadWorkspace(initialLoad = initialLoad)
            }
        }

        private suspend fun loadWorkspace(initialLoad: Boolean) {
            _uiState.update { current ->
                current.copy(
                    isLoading = initialLoad,
                    isRefreshing = !initialLoad,
                    error = if (initialLoad) null else current.error,
                )
            }

            val result =
                runCatching {
                    workspaceRepository.getHostsWithRoots()
                }

            result.onSuccess { hosts ->
                _uiState.update { current ->
                    current.copy(
                        hosts = hosts,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            }
            result.onFailure { error ->
                val message = error.message ?: "加载目录失败"
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (current.hosts.isEmpty()) message else current.error,
                    )
                }
            }

            if (result.isFailure && _uiState.value.hosts.isNotEmpty()) {
                _events.emit(WorkspaceEvent.ShowMessage("刷新失败"))
            }
        }

        private fun observeHostStatuses() {
            viewModelScope.launch {
                relayWsClient.messages.collect { message ->
                    if (message is ServerMessage.HostStatus) {
                        _uiState.update { current ->
                            current.copy(
                                hosts =
                                    current.hosts.map { hostWithRoots ->
                                        if (hostWithRoots.host.id != message.hostId) {
                                            hostWithRoots
                                        } else {
                                            hostWithRoots.copy(
                                                host = hostWithRoots.host.copy(status = message.status),
                                            )
                                        }
                                    },
                            )
                        }
                        _addRootState.update { current ->
                            if (current.hostId == message.hostId && message.status != STATUS_ONLINE) {
                                browseGeneration++
                                current.copy(
                                    currentPath = "",
                                    breadcrumbs = emptyList(),
                                    directories = emptyList(),
                                    isLoading = false,
                                    error = "${current.hostName ?: "主机"} 离线，无法浏览目录",
                                    warning = null,
                                )
                            } else {
                                current
                            }
                        }
                    }
                }
            }
        }
    }

internal fun resolveWorkspaceHost(
    provider: String,
    hosts: List<RelayHost>,
): RelayHost? =
    when (provider) {
        "openclaw" ->
            hosts.firstOrNull { host ->
                host.type == "relay_local" || host.type == "relay" || host.id == "relay-local"
            }

        "claude", "book" ->
            hosts.firstOrNull { host ->
                host.type == "macbook" || host.type == "companion"
            }

        else -> null
    }

internal fun defaultBrowsePath(
    provider: String,
    host: RelayHost,
): String =
    when {
        provider == "openclaw" -> "/"
        host.type == "macbook" || host.type == "companion" -> "/Users"
        else -> "/"
    }

private fun mapAddRootError(error: Throwable): String =
    when (error) {
        is RelayApiException ->
            when {
                error.statusCode == 409 || error.code == "state_conflict" -> "该目录已添加"
                error.statusCode == 502 || error.code == "host_offline" -> "主机离线"
                else -> error.message
            }

        else -> error.message ?: "添加失败，请重试"
    }

internal fun String.defaultRootLabel(): String = trimEnd('/').substringAfterLast('/').ifBlank { this }

private const val STATUS_ONLINE = "online"
private const val MAX_BROWSE_ENTRIES = 200
