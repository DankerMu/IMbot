package com.imbot.android.ui.newsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.relayValidationError
import com.imbot.android.network.BrowseEntry
import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWorkspaceRoot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_MODEL = "sonnet"

data class DirectoryBreadcrumb(
    val label: String,
    val path: String,
)

data class NewSessionUiState(
    val step: Int = 0,
    val provider: String? = null,
    val hostId: String? = null,
    val hosts: List<RelayHost> = emptyList(),
    val roots: List<RelayWorkspaceRoot> = emptyList(),
    val browseEntries: List<BrowseEntry> = emptyList(),
    val browsePath: String? = null,
    val pendingBrowsePath: String? = null,
    val breadcrumbs: List<DirectoryBreadcrumb> = emptyList(),
    val cwd: String? = null,
    val prompt: String = "",
    val model: String = DEFAULT_MODEL,
    val isCreating: Boolean = false,
    val error: String? = null,
    val directoryError: String? = null,
    val directoryWarning: String? = null,
    val isLoadingHosts: Boolean = false,
    val isLoadingRoots: Boolean = false,
    val isLoadingBrowse: Boolean = false,
)

sealed interface NewSessionEvent {
    data class SessionCreated(
        val sessionId: String,
    ) : NewSessionEvent
}

@HiltViewModel
@Suppress("TooManyFunctions")
class NewSessionViewModel
    @Inject
    constructor(
        private val relayHttpClient: RelayHttpClient,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(NewSessionUiState())
        val uiState: StateFlow<NewSessionUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<NewSessionEvent>()
        val events: SharedFlow<NewSessionEvent> = _events.asSharedFlow()
        private var requestGeneration = 0

        init {
            loadHosts()
        }

        @Suppress("CyclomaticComplexMethod")
        fun loadHosts() {
            if (_uiState.value.isLoadingHosts) {
                return
            }

            viewModelScope.launch {
                val settings = requireValidSettings() ?: return@launch
                _uiState.update { current ->
                    current.copy(
                        isLoadingHosts = true,
                    )
                }

                relayHttpClient.getHosts(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                ).onSuccess { hosts ->
                    _uiState.update { current ->
                        val resolvedHost =
                            current.provider
                                ?.let { provider -> findHostForProvider(provider, hosts) }
                                ?.takeIf { host -> host.status == STATUS_ONLINE }
                        val keepSelection =
                            resolvedHost != null && current.hostId == resolvedHost.id

                        current.copy(
                            hosts = hosts,
                            provider =
                                if (keepSelection) {
                                    current.provider
                                } else {
                                    current.provider?.takeIf { resolvedHost != null }
                                },
                            hostId = resolvedHost?.id,
                            roots = if (keepSelection) current.roots else emptyList(),
                            browseEntries = if (keepSelection) current.browseEntries else emptyList(),
                            browsePath = if (keepSelection) current.browsePath else null,
                            pendingBrowsePath = if (keepSelection) current.pendingBrowsePath else null,
                            breadcrumbs = if (keepSelection) current.breadcrumbs else emptyList(),
                            cwd = if (keepSelection) current.cwd else null,
                            directoryError = if (keepSelection) current.directoryError else null,
                            isLoadingRoots = if (keepSelection) current.isLoadingRoots else false,
                            isLoadingBrowse = if (keepSelection) current.isLoadingBrowse else false,
                            isLoadingHosts = false,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isLoadingHosts = false,
                            error = error.message ?: "加载主机失败",
                        )
                    }
                }
            }
        }

        fun selectProvider(
            provider: String,
            hostId: String,
        ) {
            val current = _uiState.value
            if (current.provider == provider && current.hostId == hostId) {
                return
            }

            requestGeneration++
            _uiState.update { state ->
                state.copy(
                    provider = provider,
                    hostId = hostId,
                    roots = emptyList(),
                    browseEntries = emptyList(),
                    browsePath = null,
                    pendingBrowsePath = null,
                    breadcrumbs = emptyList(),
                    cwd = null,
                    directoryError = null,
                    directoryWarning = null,
                    isLoadingRoots = false,
                    isLoadingBrowse = false,
                    model = DEFAULT_MODEL,
                )
            }
        }

        fun loadRoots() {
            val state = _uiState.value
            val hostId = state.hostId
            val provider = state.provider
            if (state.isLoadingRoots || hostId == null || provider == null) {
                return
            }
            val gen = requestGeneration

            viewModelScope.launch {
                val settings = requireValidSettings() ?: return@launch
                if (gen != requestGeneration) {
                    return@launch
                }
                _uiState.update { current ->
                    current.copy(
                        isLoadingRoots = true,
                        directoryError = null,
                        pendingBrowsePath = null,
                    )
                }

                relayHttpClient.getHostRoots(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    hostId = hostId,
                ).onSuccess { roots ->
                    if (gen != requestGeneration) {
                        return@onSuccess
                    }
                    val filteredRoots = filterRootsForProvider(provider, roots)
                    _uiState.update { current ->
                        current.copy(
                            roots = filteredRoots,
                            browseEntries = emptyList(),
                            browsePath = null,
                            pendingBrowsePath = null,
                            breadcrumbs = emptyList(),
                            cwd =
                                current.cwd?.takeIf { selectedPath ->
                                    filteredRoots.any { root ->
                                        selectedPath == root.path || selectedPath.startsWith("${root.path}/")
                                    }
                                },
                            isLoadingRoots = false,
                        )
                    }
                }.onFailure { error ->
                    if (gen != requestGeneration) {
                        return@onFailure
                    }
                    _uiState.update { current ->
                        current.copy(
                            isLoadingRoots = false,
                            pendingBrowsePath = null,
                            directoryError = error.message ?: "加载目录失败",
                        )
                    }
                }
            }
        }

        fun browseDirectory(path: String) {
            val state = _uiState.value
            val hostId = state.hostId ?: return
            if (state.isLoadingBrowse) {
                return
            }
            val gen = requestGeneration

            viewModelScope.launch {
                val settings = requireValidSettings() ?: return@launch
                if (gen != requestGeneration) {
                    return@launch
                }
                _uiState.update { current ->
                    current.copy(
                        isLoadingBrowse = true,
                        directoryError = null,
                        pendingBrowsePath = path,
                    )
                }

                relayHttpClient.browseDirectory(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    hostId = hostId,
                    path = path,
                ).onSuccess { result ->
                    if (gen != requestGeneration) {
                        return@onSuccess
                    }
                    val maxEntries = 200
                    val truncated = result.directories.size > maxEntries
                    val cappedEntries = result.directories.take(maxEntries)
                    _uiState.update { current ->
                        current.copy(
                            browsePath = result.path,
                            browseEntries = cappedEntries,
                            breadcrumbs = result.path.toBreadcrumbs(),
                            pendingBrowsePath = null,
                            isLoadingBrowse = false,
                            directoryError = null,
                            directoryWarning = if (truncated) "目录条目过多，仅显示前 $maxEntries 项" else null,
                        )
                    }
                }.onFailure { error ->
                    if (gen != requestGeneration) {
                        return@onFailure
                    }
                    _uiState.update { current ->
                        current.copy(
                            isLoadingBrowse = false,
                            directoryError = error.message ?: "浏览目录失败",
                        )
                    }
                }
            }
        }

        fun selectDirectory(path: String) {
            if (path.isBlank()) {
                return
            }

            _uiState.update { current ->
                current.copy(cwd = path)
            }
        }

        fun updatePrompt(text: String) {
            _uiState.update { current ->
                current.copy(prompt = text)
            }
        }

        fun updateModel(model: String) {
            if (model !in SUPPORTED_MODELS) {
                return
            }

            _uiState.update { current ->
                current.copy(model = model)
            }
        }

        @Suppress("CyclomaticComplexMethod")
        fun createSession() {
            val current = _uiState.value
            if (current.isCreating) {
                return
            }

            val provider = current.provider
            val hostId = current.hostId
            val cwd = current.cwd
            val prompt = current.prompt.trim()

            val validationError =
                when {
                    provider.isNullOrBlank() -> "请先选择 Provider"
                    hostId.isNullOrBlank() -> "当前 Provider 未关联可用主机"
                    cwd.isNullOrBlank() -> "请先选择目录"
                    prompt.isBlank() -> "请输入 prompt"
                    else -> null
                }
            if (validationError != null) {
                publishError(validationError)
                return
            }

            val selectedProvider = provider.orEmpty()
            val selectedHostId = hostId.orEmpty()
            val selectedCwd = cwd.orEmpty()

            viewModelScope.launch {
                val settings = requireValidSettings() ?: return@launch
                _uiState.update { state ->
                    state.copy(
                        isCreating = true,
                    )
                }

                val latestHostsResult =
                    relayHttpClient.getHosts(
                        relayUrl = settings.relayUrl,
                        token = settings.token,
                    )

                val latestHosts =
                    latestHostsResult.getOrElse { error ->
                        _uiState.update { state ->
                            state.copy(
                                isCreating = false,
                                error = error.message ?: "刷新主机状态失败",
                            )
                        }
                        return@launch
                    }

                val selectedHost =
                    latestHosts.firstOrNull { host ->
                        host.id == selectedHostId && selectedProvider in host.providers
                    }

                if (selectedHost == null || selectedHost.status != STATUS_ONLINE) {
                    _uiState.update { state ->
                        state.copy(
                            hosts = latestHosts,
                            isCreating = false,
                            error = providerOfflineMessage(selectedProvider),
                        )
                    }
                    return@launch
                }

                relayHttpClient.createSession(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    provider = selectedProvider,
                    hostId = selectedHostId,
                    cwd = selectedCwd,
                    prompt = prompt,
                    permissionMode = PERMISSION_MODE_BYPASS,
                    model = _uiState.value.model,
                ).onSuccess { response ->
                    _uiState.update { state ->
                        state.copy(
                            hosts = latestHosts,
                            isCreating = false,
                        )
                    }
                    _events.emit(NewSessionEvent.SessionCreated(response.sessionId))
                }.onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            hosts = latestHosts,
                            isCreating = false,
                            error = error.message ?: "创建会话失败",
                        )
                    }
                }
            }
        }

        fun goToStep(step: Int) {
            val normalizedStep = step.coerceIn(FIRST_STEP, LAST_STEP)
            _uiState.update { current -> current.copy(step = normalizedStep) }
            when (normalizedStep) {
                STEP_PROVIDER -> loadHosts()
                STEP_DIRECTORY -> {
                    val current = _uiState.value
                    if (current.roots.isEmpty()) {
                        loadRoots()
                    }
                }
            }
        }

        fun clearError() {
            _uiState.update { current ->
                current.copy(error = null)
            }
        }

        private fun publishError(message: String) {
            _uiState.update { current ->
                current.copy(error = message)
            }
        }

        private fun requireValidSettings(): com.imbot.android.data.RelaySettings? {
            val settings = settingsRepository.load()
            val errorMessage =
                when {
                    !settings.isConfigured() -> "请先在设置页完成 Relay 配置"
                    else -> settings.relayValidationError()
                }

            return if (errorMessage != null) {
                publishError(errorMessage)
                null
            } else {
                settings
            }
        }

        private companion object {
            const val FIRST_STEP = 0
            const val STEP_PROVIDER = 0
            const val STEP_DIRECTORY = 1
            const val LAST_STEP = 2
            const val STATUS_ONLINE = "online"
            const val PERMISSION_MODE_BYPASS = "bypassPermissions"
            const val DEFAULT_MODEL = "sonnet"
            val SUPPORTED_MODELS = setOf("sonnet", "opus", "haiku")
        }
    }

internal fun filterRootsForProvider(
    provider: String,
    roots: List<RelayWorkspaceRoot>,
): List<RelayWorkspaceRoot> =
    when (provider) {
        "book" -> roots.filter { root -> root.provider == provider }
        else -> roots
    }

internal fun findHostForProvider(
    provider: String,
    hosts: List<RelayHost>,
): RelayHost? =
    hosts.firstOrNull { host ->
        provider in host.providers && host.status == "online"
    } ?: hosts.firstOrNull { host ->
        provider in host.providers
    }

internal fun providerOfflineMessage(provider: String): String =
    when (provider) {
        "claude" -> "Claude Code 所在主机当前离线，请返回上一步重新选择。"
        "book" -> "book 所在主机当前离线，请返回上一步重新选择。"
        "openclaw" -> "OpenClaw 当前不可用，请稍后重试。"
        else -> "当前 Provider 不可用，请稍后重试。"
    }

internal fun String.toBreadcrumbs(): List<DirectoryBreadcrumb> {
    val segments = split("/").filter(String::isNotBlank)
    return when {
        isBlank() -> emptyList()
        segments.isEmpty() -> listOf(DirectoryBreadcrumb(label = "/", path = "/"))
        else -> {
            var currentPath = ""
            buildList {
                add(DirectoryBreadcrumb(label = "/", path = "/"))
                segments.forEach { segment ->
                    currentPath = if (currentPath.isEmpty()) "/$segment" else "$currentPath/$segment"
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
