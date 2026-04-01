package com.imbot.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.HealthzHost
import com.imbot.android.network.HealthzResponse
import com.imbot.android.network.RelayApiException
import com.imbot.android.network.RelayHost
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelayWsClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

data class OnboardingUiState(
    val relayUrl: String = "",
    val token: String = "",
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
)

sealed interface TestResult {
    data class Success(
        val response: HealthzResponse,
    ) : TestResult

    data class Error(
        val message: String,
    ) : TestResult
}

sealed interface OnboardingEvent {
    data object NavigateHome : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val relayHttpClient: RelayHttpClient,
        private val settingsRepository: SettingsRepository,
        private val relayWsClient: RelayWsClient,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<OnboardingEvent>()
        val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

        fun updateUrl(url: String) {
            _uiState.update { current ->
                current.copy(
                    relayUrl = url,
                    testResult = null,
                )
            }
        }

        fun updateToken(token: String) {
            _uiState.update { current ->
                current.copy(
                    token = token,
                    testResult = null,
                )
            }
        }

        @Suppress("ReturnCount")
        fun testConnection() {
            val current = _uiState.value
            if (current.isTesting) {
                return
            }

            val relayUrl = current.relayUrl.trim()
            val token = current.token.trim()

            when {
                relayUrl.isBlank() || token.isBlank() -> {
                    publishError("请填写完整的连接信息")
                    return
                }
                !isValidRelayUrl(relayUrl) -> {
                    publishError("请输入有效的 Relay URL")
                    return
                }
            }

            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(
                        isTesting = true,
                        testResult = null,
                    )
                }

                loadAuthenticatedHealth(
                    relayUrl = relayUrl,
                    token = token,
                ).onSuccess { response ->
                    _uiState.update { state ->
                        state.copy(
                            isTesting = false,
                            testResult = TestResult.Success(response),
                        )
                    }
                }.onFailure { error ->
                    publishError(mapTestError(error), isTesting = false)
                }
            }
        }

        fun saveAndProceed() {
            val state = _uiState.value
            if (state.testResult !is TestResult.Success) {
                return
            }

            val settings =
                RelaySettings(
                    relayUrl = state.relayUrl.trim(),
                    token = state.token.trim(),
                )
            settingsRepository.save(settings)
            relayWsClient.connect(settings.relayUrl, settings.token)

            viewModelScope.launch {
                _events.emit(OnboardingEvent.NavigateHome)
            }
        }

        private fun publishError(
            message: String,
            isTesting: Boolean = false,
        ) {
            _uiState.update { current ->
                current.copy(
                    isTesting = isTesting,
                    testResult = TestResult.Error(message),
                )
            }
        }

        private suspend fun loadAuthenticatedHealth(
            relayUrl: String,
            token: String,
        ): Result<HealthzResponse> =
            runCatching {
                val authenticatedHosts =
                    relayHttpClient.getHosts(
                        relayUrl = relayUrl,
                        token = token,
                    ).getOrThrow()

                relayHttpClient.testConnection(
                    relayUrl = relayUrl,
                    token = token,
                ).getOrElse { error ->
                    if (error is RelayApiException &&
                        (error.statusCode == 401 || error.code == "unauthenticated")
                    ) {
                        throw error
                    }

                    return@runCatching HealthzResponse(
                        version = "unknown",
                        hosts = authenticatedHosts.map(RelayHost::toHealthzHost),
                    )
                }.withAuthenticatedHosts(authenticatedHosts)
            }

        private fun mapTestError(error: Throwable): String =
            when (error) {
                is RelayApiException ->
                    if (error.statusCode == 401 || error.code == "unauthenticated") {
                        "认证失败"
                    } else {
                        error.message
                    }

                is SocketTimeoutException -> "连接超时"
                is IOException -> "无法连接"
                else -> error.message ?: "连接失败"
            }
    }

internal fun isValidRelayUrl(value: String): Boolean {
    val parsed = value.toHttpUrlOrNull() ?: return false
    return parsed.scheme == "https"
}

internal fun HealthzResponse.macbookHost(): HealthzHost? =
    hosts.firstOrNull { host ->
        host.type == "macbook" || host.type == "companion"
    }

internal fun HealthzResponse.openClawHost(): HealthzHost? =
    hosts.firstOrNull { host ->
        host.type == "relay_local" || host.type == "relay"
    }

internal fun HealthzResponse.withAuthenticatedHosts(hosts: List<RelayHost>): HealthzResponse =
    copy(hosts = hosts.map(RelayHost::toHealthzHost))

internal fun RelayHost.toHealthzHost(): HealthzHost =
    HealthzHost(
        id = id,
        name = name,
        type = type,
        status = status,
    )
