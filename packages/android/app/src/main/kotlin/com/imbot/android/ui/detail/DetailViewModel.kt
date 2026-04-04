package com.imbot.android.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imbot.android.data.RelaySettings
import com.imbot.android.data.SettingsRepository
import com.imbot.android.data.relayValidationError
import com.imbot.android.data.repository.SessionRepository
import com.imbot.android.network.ConnectionState
import com.imbot.android.network.RelayHttpClient
import com.imbot.android.network.RelaySession
import com.imbot.android.network.RelayWsClient
import com.imbot.android.network.ServerMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val SESSION_ID_ARG = "sessionId"

internal data class ConnectionBannerUiState(
    val message: String,
    val isSuccess: Boolean,
)

internal data class DetailUiState(
    val session: RelaySession? = null,
    val effectiveStatus: String? = null,
    val messages: List<MessageItem> = emptyList(),
    val commandChip: SkillItem? = null,
    val showSlashSheet: Boolean = false,
    val messageMenuTarget: MessageItem? = null,
    val selectionModeMessageId: String? = null,
    val isLoading: Boolean = true,
    val isConnected: Boolean = false,
    val isCatchingUp: Boolean = false,
    val error: String? = null,
    val canSend: Boolean = false,
    val isSending: Boolean = false,
    val isResuming: Boolean = false,
    val isCancelling: Boolean = false,
    val isCompleting: Boolean = false,
    val isDeleting: Boolean = false,
    val scrollState: DetailScrollState = DetailScrollState(),
    val connectionBanner: ConnectionBannerUiState? = null,
)

internal sealed interface DetailEvent {
    data class ScrollToBottom(
        val targetIndex: Int,
    ) : DetailEvent

    data class NavigateBack(
        val refreshHome: Boolean,
    ) : DetailEvent
}

@HiltViewModel
@Suppress("TooManyFunctions", "LargeClass")
class DetailViewModel
    @Inject
    constructor(
        private val relayHttpClient: RelayHttpClient,
        private val sessionRepository: SessionRepository,
        private val relayWsClient: RelayWsClient,
        private val settingsRepository: SettingsRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val sessionId = savedStateHandle.get<String>(SESSION_ID_ARG).orEmpty().trim()
        private val eventProcessor = EventProcessor()
        private val optimisticMessages = mutableListOf<MessageItem.UserMessage>()
        private val localInteractiveAnswers = mutableMapOf<String, String>()

        private val _uiState =
            MutableStateFlow(
                DetailUiState(
                    isConnected = relayWsClient.connectionState.value is ConnectionState.Connected,
                ),
            )
        internal val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<DetailEvent>()
        internal val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

        private var loadJob: Job? = null
        private var catchUpJob: Job? = null
        private var lastConnectionState: ConnectionState = relayWsClient.connectionState.value
        private var historyLoaded = false
        private val catchUpBuffer = mutableListOf<ServerMessage.Event>()
        private var isCatchingUp = false
        private var interactiveAnswerSyncJob: Job? = null

        init {
            observeConnectionState()
            observeSessionEvents()
            loadSession()
        }

        fun loadSession() {
            if (loadJob?.isActive == true) {
                return
            }
            if (sessionId.isBlank()) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = "会话 ID 无效",
                    )
                }
                return
            }

            loadJob =
                viewModelScope.launch {
                    val settings = requireValidSettings() ?: return@launch
                    beginCatchUp()
                    eventProcessor.reset()
                    optimisticMessages.clear()
                    localInteractiveAnswers.clear()
                    relayWsClient.subscribe(sessionId)

                    _uiState.update { current ->
                        current.copy(
                            isLoading = true,
                            isCatchingUp = false,
                            error = null,
                            messages = emptyList(),
                            commandChip = null,
                            showSlashSheet = false,
                            messageMenuTarget = null,
                            selectionModeMessageId = null,
                            effectiveStatus = null,
                            scrollState = DetailScrollState(),
                        )
                    }

                    runCatching {
                        relayHttpClient.getSession(
                            relayUrl = settings.relayUrl,
                            token = settings.token,
                            sessionId = sessionId,
                        ).getOrThrow()
                    }.onSuccess { session ->
                        interactiveAnswerSyncJob?.cancel()
                        publishSession(session)
                        fetchEventsSince(
                            settings = settings,
                            sinceSeq = 0,
                            showCatchUpIndicator = false,
                            showRecoveryBanner = false,
                        )
                        historyLoaded = true
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                            )
                        }
                        if (canResumeSession(_uiState.value.effectiveStatus ?: _uiState.value.session?.status)) {
                            resumeSession()
                        }
                    }.onFailure { error ->
                        finishCatchUp(processBufferedEvents = false)
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                error = error.message ?: "加载会话失败",
                            )
                        }
                    }
                }
        }

        fun sendMessage(text: String) {
            val state = _uiState.value
            val assembledText =
                state.commandChip?.let { commandChip ->
                    assembleSlashCommand(commandChip.command, text)
                } ?: text.trim()
            if (assembledText.isBlank()) {
                return
            }

            sendSessionInput(
                text = assembledText,
                allowRunningInput = false,
                preserveWhitespace = state.commandChip != null,
            )
        }

        fun submitToolAnswer(answer: String) {
            val targetCallId = latestPendingInteractiveToolCallId() ?: return
            submitToolAnswer(targetCallId, answer)
        }

        fun submitToolAnswer(
            callId: String,
            answer: String,
        ) {
            if (callId != latestPendingInteractiveToolCallId()) {
                return
            }
            sendSessionInput(
                text = answer.trim(),
                allowRunningInput = true,
                interactiveToolCallId = callId,
                interactiveAnswer = answer,
            )
        }

        fun approveToolCall() {
            approveToolCall(latestPendingApprovalCallId() ?: return)
        }

        fun approveToolCall(callId: String) {
            if (callId.isBlank() || callId != latestPendingApprovalCallId()) {
                return
            }
            sendSessionInput(
                text = approvalInputText(approved = true),
                allowRunningInput = true,
            )
        }

        fun denyToolCall() {
            denyToolCall(latestPendingApprovalCallId() ?: return)
        }

        fun denyToolCall(callId: String) {
            if (callId.isBlank() || callId != latestPendingApprovalCallId()) {
                return
            }
            sendSessionInput(
                text = approvalInputText(approved = false),
                allowRunningInput = true,
            )
        }

        fun onSlashTrigger() {
            _uiState.update { current ->
                if (current.commandChip != null || current.showSlashSheet) {
                    current
                } else {
                    current.copy(showSlashSheet = true)
                }
            }
        }

        internal fun onSkillSelected(skill: SkillItem) {
            _uiState.update { current ->
                current.copy(
                    commandChip = skill,
                    showSlashSheet = false,
                )
            }
        }

        fun onDismissCommand() {
            _uiState.update { current ->
                current.copy(commandChip = null)
            }
        }

        fun onDismissSlashSheet() {
            _uiState.update { current ->
                current.copy(showSlashSheet = false)
            }
        }

        private fun sendSessionInput(
            text: String,
            allowRunningInput: Boolean,
            interactiveToolCallId: String? = null,
            interactiveAnswer: String? = null,
            preserveWhitespace: Boolean = false,
        ) {
            val normalizedText = if (preserveWhitespace) text else text.trim()
            val isInteractiveToolAnswer = interactiveToolCallId != null
            val state = _uiState.value
            val canSendInput = canSendInput(state, allowRunningInput)

            if (text.trim().isBlank() || state.isSending || !canSendInput) {
                return
            }

            val settings = if (isInteractiveToolAnswer) null else requireValidSettings() ?: return
            addOptimisticMessage(normalizedText, isInteractiveToolAnswer)
            interactiveToolCallId?.let { callId ->
                interactiveAnswer?.let { answer ->
                    localInteractiveAnswers[callId] = answer
                    eventProcessor.recordInteractiveToolAnswer(callId, answer)
                }
            }
            _uiState.update { current ->
                current.copy(
                    isSending = true,
                    canSend = false,
                    commandChip = null,
                    showSlashSheet = false,
                )
            }
            publishMessages(
                newMessages = combinedMessages(),
                allowAutoScroll = true,
            )

            viewModelScope.launch {
                val result = submitSessionInputRequest(settings, interactiveToolCallId, normalizedText)
                result.onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            session =
                                current.session?.copy(
                                    status = "running",
                                ),
                            effectiveStatus = "running",
                            isSending = false,
                            canSend = false,
                        )
                    }
                    if (isInteractiveToolAnswer) {
                        interactiveAnswerSyncJob?.cancel()
                        interactiveAnswerSyncJob =
                            launch {
                                syncInteractiveAnswerProgress(
                                    callId = interactiveToolCallId!!,
                                )
                            }
                    }
                }.onFailure { error ->
                    interactiveToolCallId?.let { id ->
                        localInteractiveAnswers.remove(id)
                        eventProcessor.clearInteractiveToolAnswer(id, "发送失败，点击重试")
                    }
                    if (!isInteractiveToolAnswer) {
                        removeOptimisticMessage(normalizedText)
                    }
                    publishMessages(
                        newMessages = combinedMessages(),
                        allowAutoScroll = false,
                    )
                    _uiState.update { current ->
                        current.copy(
                            isSending = false,
                            canSend = canInputToSession(current.effectiveStatus),
                            error = error.message ?: "发送消息失败",
                        )
                    }
                }
            }
        }

        fun cancelSession() {
            val state = _uiState.value
            val session = state.session
            val status = state.effectiveStatus ?: session?.status
            val canCancel = session != null && !state.isCancelling && canCancelSession(status)
            val settings = if (canCancel) requireValidSettings() else null
            if (!canCancel || session == null || settings == null) {
                return
            }

            _uiState.update { current ->
                current.copy(
                    isCancelling = true,
                )
            }

            viewModelScope.launch {
                relayHttpClient.cancelSession(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    sessionId = sessionId,
                ).onSuccess { updatedSession ->
                    publishSession(updatedSession)
                    _uiState.update { current ->
                        current.copy(
                            isCancelling = false,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isCancelling = false,
                            error = error.message ?: "取消会话失败",
                        )
                    }
                }
            }
        }

        fun resumeSession() {
            val state = _uiState.value
            val session = state.session
            val status = state.effectiveStatus ?: session?.status
            val canResume = session != null && !state.isResuming && canResumeSession(status)
            if (!canResume || session == null) {
                return
            }

            val settings = requireValidSettings() ?: return
            _uiState.update { current ->
                current.copy(
                    isResuming = true,
                )
            }

            viewModelScope.launch {
                relayHttpClient.resumeSession(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    sessionId = sessionId,
                ).onSuccess { updatedSession ->
                    publishSession(updatedSession)
                    _uiState.update { current ->
                        current.copy(
                            isResuming = false,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isResuming = false,
                            error = error.message ?: "恢复会话失败",
                        )
                    }
                }
            }
        }

        fun completeSession() {
            val state = _uiState.value
            val session = state.session
            val status = state.effectiveStatus ?: session?.status
            val canComplete = session != null && !state.isCompleting && canCompleteSession(status)
            if (!canComplete || session == null) {
                return
            }

            val settings = requireValidSettings() ?: return
            _uiState.update { current ->
                current.copy(
                    isCompleting = true,
                )
            }

            viewModelScope.launch {
                relayHttpClient.completeSession(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    sessionId = sessionId,
                ).onSuccess { updatedSession ->
                    publishSession(updatedSession)
                    _uiState.update { current ->
                        current.copy(
                            isCompleting = false,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isCompleting = false,
                            error = error.message ?: "结束会话失败",
                        )
                    }
                }
            }
        }

        fun deleteSession() {
            if (_uiState.value.isDeleting) {
                return
            }

            val settings = requireValidSettings() ?: return
            _uiState.update { current ->
                current.copy(
                    isDeleting = true,
                )
            }

            viewModelScope.launch {
                relayHttpClient.deleteSession(
                    relayUrl = settings.relayUrl,
                    token = settings.token,
                    sessionId = sessionId,
                ).onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            isDeleting = false,
                        )
                    }
                    _events.emit(DetailEvent.NavigateBack(refreshHome = true))
                }.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isDeleting = false,
                            error = error.message ?: "删除会话失败",
                        )
                    }
                }
            }
        }

        fun onScrollPositionChanged(
            nearBottom: Boolean,
            userInitiatedScrollAway: Boolean,
        ) {
            _uiState.update { current ->
                current.copy(
                    scrollState =
                        onScrollPositionUpdate(
                            current = current.scrollState,
                            nearBottom = nearBottom,
                            userInitiatedScrollAway = userInitiatedScrollAway,
                        ),
                )
            }
        }

        fun onFabTapped() {
            scrollToBottomRequested()
        }

        fun scrollToBottomRequested() {
            val mutation = resumeAutoScroll(_uiState.value.scrollState)
            _uiState.update { current ->
                current.copy(
                    scrollState = mutation.state,
                )
            }
            emitScrollToBottom()
        }

        fun clearError() {
            _uiState.update { current ->
                current.copy(
                    error = null,
                )
            }
        }

        fun onMessageLongPress(item: MessageItem) {
            if (!hasActions(item)) {
                return
            }

            _uiState.update { current ->
                current.copy(
                    messageMenuTarget = item,
                    selectionModeMessageId = null,
                )
            }
        }

        fun onDismissMessageMenu() {
            _uiState.update { current ->
                current.copy(
                    messageMenuTarget = null,
                )
            }
        }

        fun onEnterSelectionMode(messageId: String) {
            if (messageId.isBlank()) {
                return
            }

            _uiState.update { current ->
                current.copy(
                    messageMenuTarget = null,
                    selectionModeMessageId = messageId,
                )
            }
        }

        fun onExitSelectionMode() {
            _uiState.update { current ->
                current.copy(
                    selectionModeMessageId = null,
                )
            }
        }

        override fun onCleared() {
            interactiveAnswerSyncJob?.cancel()
            relayWsClient.clearSubscription()
            super.onCleared()
        }

        internal suspend fun handleSessionEvent(
            event: ServerMessage.Event,
            allowAutoScroll: Boolean = true,
        ) {
            if (event.eventType == "user_message") {
                removeOptimisticMessage(event.payload.stringValue("text").orEmpty())
            }

            eventProcessor.process(event)
            applyEventToSession(event)
            publishMessages(
                newMessages = combinedMessages(),
                allowAutoScroll = allowAutoScroll,
            )
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                relayWsClient.connectionState.collect { connectionState ->
                    _uiState.update { current ->
                        current.copy(
                            isConnected = connectionState is ConnectionState.Connected,
                            connectionBanner = disconnectedBanner(connectionState, current.connectionBanner),
                        )
                    }

                    val shouldRecover =
                        historyLoaded &&
                            connectionState is ConnectionState.Connected &&
                            lastConnectionState is ConnectionState.Disconnected
                    lastConnectionState = connectionState

                    if (shouldRecover) {
                        recoverAfterReconnect()
                    }
                }
            }
        }

        private fun observeSessionEvents() {
            viewModelScope.launch {
                relayWsClient.events.collect { event ->
                    if (event.sessionId == sessionId) {
                        if (isCatchingUp) {
                            catchUpBuffer += event
                        } else {
                            handleSessionEvent(event)
                        }
                    }
                }
            }
        }

        private fun recoverAfterReconnect() {
            if (catchUpJob?.isActive == true) {
                return
            }

            val settings = requireValidSettings() ?: return
            beginCatchUp()
            catchUpJob =
                viewModelScope.launch {
                    fetchEventsSince(
                        settings = settings,
                        sinceSeq = eventProcessor.lastProcessedSeq(),
                        showCatchUpIndicator = true,
                        showRecoveryBanner = true,
                    )
                }
        }

        private suspend fun fetchEventsSince(
            settings: RelaySettings,
            sinceSeq: Int,
            showCatchUpIndicator: Boolean,
            showRecoveryBanner: Boolean,
        ) {
            if (showCatchUpIndicator) {
                _uiState.update { current ->
                    current.copy(
                        isCatchingUp = true,
                    )
                }
            }

            val catchUpSucceeded =
                pullEventsSince(
                    settings = settings,
                    sinceSeq = sinceSeq,
                    allowAutoScroll = false,
                    failureMessage = "同步消息失败",
                )

            finishCatchUp(processBufferedEvents = catchUpSucceeded)
            if (catchUpSucceeded) {
                refreshSessionSnapshot(settings)
            }

            if (catchUpSucceeded) {
                if (showRecoveryBanner) {
                    viewModelScope.launch {
                        showRecoveredBanner()
                    }
                }
                if (_uiState.value.scrollState.autoScrollEnabled && _uiState.value.messages.isNotEmpty()) {
                    emitScrollToBottom(_uiState.value.messages.lastIndex)
                }
            }

            if (showCatchUpIndicator) {
                _uiState.update { current ->
                    current.copy(
                        isCatchingUp = false,
                    )
                }
            }
        }

        private suspend fun refreshSessionSnapshot(settings: RelaySettings) {
            relayHttpClient.getSession(
                relayUrl = settings.relayUrl,
                token = settings.token,
                sessionId = sessionId,
            ).onSuccess { session ->
                val currentStatus = _uiState.value.effectiveStatus ?: _uiState.value.session?.status
                if (shouldIgnoreSessionSnapshotStatus(currentStatus, session.status)) {
                    return@onSuccess
                }
                publishSession(session)
            }
        }

        private suspend fun showRecoveredBanner() {
            _uiState.update { current ->
                current.copy(
                    connectionBanner = ConnectionBannerUiState(message = "已恢复", isSuccess = true),
                )
            }
            delay(2_000)
            _uiState.update { current ->
                current.copy(
                    connectionBanner =
                        current.connectionBanner?.takeUnless { banner ->
                            banner.isSuccess
                        },
                )
            }
        }

        private fun publishSession(session: RelaySession) {
            val fallbackAnswers = interactiveAnswerFallbacks()
            eventProcessor.reconcileInteractiveToolCalls(session.status, fallbackAnswers)
            val newMessages =
                presentInteractiveMessages(
                    messages = combinedMessages(),
                    sessionStatus = session.status,
                )
            val effectiveStatus = effectiveSessionStatus(session.status, newMessages)
            _uiState.update { current ->
                current.copy(
                    session = session,
                    effectiveStatus = effectiveStatus,
                    messages = newMessages,
                    canSend = canInputToSession(effectiveStatus) && !current.isSending,
                )
            }
        }

        private fun applyEventToSession(event: ServerMessage.Event) {
            val currentSession = _uiState.value.session ?: return

            when (event.eventType) {
                "session_started" -> {
                    publishSession(
                        currentSession.copy(
                            status = "running",
                            errorMessage = currentSession.errorMessage,
                        ),
                    )
                }

                "session_idle" -> {
                    publishSession(
                        currentSession.copy(
                            status = "idle",
                            errorMessage = currentSession.errorMessage,
                        ),
                    )
                }

                "session_status_changed", "session_result" -> {
                    val status = event.payload.stringValue("status").orEmpty()
                    if (status.isNotBlank()) {
                        publishSession(
                            currentSession.copy(
                                status = status,
                                errorMessage = currentSession.errorMessage,
                            ),
                        )
                    }
                }

                "session_error" -> {
                    val message = event.payload.stringValue("message")
                    val errorCode = event.payload.stringValue("error_code")
                    publishSession(
                        currentSession.copy(
                            status = "failed",
                            errorMessage = message,
                        ),
                    )
                    if (errorCode != "provider_unreachable") {
                        _uiState.update { current ->
                            current.copy(
                                error = message ?: current.error,
                            )
                        }
                    }
                }
            }
        }

        private suspend fun syncInteractiveAnswerProgress(callId: String) {
            val settings = requireValidSettings() ?: return
            val deadlineMs = System.currentTimeMillis() + INTERACTIVE_ANSWER_SYNC_TIMEOUT_MS

            var syncing = true
            while (syncing && System.currentTimeMillis() < deadlineMs) {
                val syncSucceeded =
                    pullEventsSince(
                        settings = settings,
                        sinceSeq = eventProcessor.lastProcessedSeq(),
                        allowAutoScroll = true,
                        failureMessage = null,
                    )
                val sessionStatus =
                    _uiState.value.effectiveStatus ?: _uiState.value.session?.status
                val stillPending = latestPendingInteractiveToolCallId() == callId
                syncing = syncSucceeded && (stillPending || sessionStatus == "running")
                if (syncing) {
                    delay(INTERACTIVE_ANSWER_SYNC_INTERVAL_MS)
                }
            }
        }

        private suspend fun pullEventsSince(
            settings: RelaySettings,
            sinceSeq: Int,
            allowAutoScroll: Boolean,
            failureMessage: String?,
        ): Boolean =
            runCatching {
                var cursor = sinceSeq
                var hasMore: Boolean

                do {
                    val page =
                        relayHttpClient.getSessionEvents(
                            relayUrl = settings.relayUrl,
                            token = settings.token,
                            sessionId = sessionId,
                            sinceSeq = cursor,
                        ).getOrThrow()

                    val sortedEvents = page.events.sortedBy(ServerMessage.Event::seq)
                    sortedEvents.forEach { event ->
                        handleSessionEvent(
                            event = event,
                            allowAutoScroll = allowAutoScroll,
                        )
                    }
                    cursor = maxOf(cursor, sortedEvents.maxOfOrNull(ServerMessage.Event::seq) ?: cursor)
                    hasMore = page.hasMore
                } while (hasMore)
            }.onFailure { error ->
                if (failureMessage != null) {
                    _uiState.update { current ->
                        current.copy(
                            error = error.message ?: failureMessage,
                        )
                    }
                }
            }.isSuccess

        private fun publishMessages(
            newMessages: List<MessageItem>,
            allowAutoScroll: Boolean,
        ) {
            val previousState = _uiState.value
            val renderedMessages =
                presentInteractiveMessages(
                    messages = newMessages,
                    sessionStatus = previousState.effectiveStatus ?: previousState.session?.status,
                )
            if (previousState.messages == renderedMessages) {
                return
            }

            val mutation =
                if (allowAutoScroll) {
                    onTimelineChanged(
                        current = previousState.scrollState,
                        itemCountChanged = renderedMessages.size != previousState.messages.size,
                    )
                } else {
                    ScrollMutation(previousState.scrollState, shouldScrollToBottom = false)
                }

            _uiState.update { current ->
                val effectiveStatus = effectiveSessionStatus(current.session?.status, renderedMessages)
                current.copy(
                    messages = renderedMessages,
                    effectiveStatus = effectiveStatus,
                    canSend = canInputToSession(effectiveStatus) && !current.isSending,
                    selectionModeMessageId =
                        if (renderedMessages.size != current.messages.size) {
                            null
                        } else {
                            current.selectionModeMessageId
                        },
                    scrollState = mutation.state,
                )
            }

            if (mutation.shouldScrollToBottom) {
                emitScrollToBottom(renderedMessages.lastIndex)
            }
        }

        private fun combinedMessages(): List<MessageItem> = eventProcessor.snapshot() + optimisticMessages

        private fun interactiveAnswerFallbacks(): Map<String, String> =
            buildMap {
                localInteractiveAnswers.forEach { (callId, answer) ->
                    answer.takeIf(String::isNotBlank)?.let { put(callId, it) }
                }
            }

        private fun presentInteractiveMessages(
            messages: List<MessageItem>,
            sessionStatus: String?,
        ): List<MessageItem> {
            val fallbackAnswers = interactiveAnswerFallbacks()
            var mutated = false
            val rendered =
                messages.map { item ->
                    val interactive = item as? MessageItem.InteractiveToolCall ?: return@map item
                    val mergedAnswer = interactive.answer ?: fallbackAnswers[interactive.id]
                    val terminalStatus = sessionStatus in setOf("completed", "failed", "cancelled")
                    val shouldResolve =
                        when {
                            terminalStatus -> true
                            sessionStatus == "idle" -> !mergedAnswer.isNullOrBlank()
                            else -> false
                        }
                    val nextItem =
                        interactive.copy(
                            isAnswered = interactive.isAnswered || shouldResolve,
                            answer = mergedAnswer,
                            errorMessage =
                                if ((interactive.isAnswered || shouldResolve || !mergedAnswer.isNullOrBlank())) {
                                    null
                                } else {
                                    interactive.errorMessage
                                },
                        )
                    if (nextItem != interactive) {
                        mutated = true
                    }
                    nextItem
                }
            return if (mutated) rendered else messages
        }

        private fun canSendInput(
            state: DetailUiState,
            allowRunningInput: Boolean,
        ): Boolean =
            if (allowRunningInput) {
                canRespondToInteractiveRequest(state.effectiveStatus ?: state.session?.status)
            } else {
                state.canSend
            }

        private fun addOptimisticMessage(
            normalizedText: String,
            isInteractiveToolAnswer: Boolean,
        ) {
            if (isInteractiveToolAnswer) {
                return
            }
            optimisticMessages +=
                MessageItem.UserMessage(
                    id = generateMessageItemId(),
                    text = normalizedText,
                    timestamp = Instant.now().toString(),
                )
        }

        private suspend fun submitSessionInputRequest(
            settings: RelaySettings?,
            interactiveToolCallId: String?,
            normalizedText: String,
        ): Result<Unit> =
            interactiveToolCallId?.let { callId ->
                sessionRepository.answerInteractiveTool(
                    sessionId = sessionId,
                    callId = callId,
                    answer = normalizedText,
                    questionIndex = 0,
                )
            } ?: relayHttpClient.sendMessage(
                relayUrl = settings!!.relayUrl,
                token = settings.token,
                sessionId = sessionId,
                text = normalizedText,
            )

        private fun latestPendingInteractiveToolCallId(): String? {
            return findLatestPendingInteractiveToolCallId(
                messages = _uiState.value.messages,
                sessionStatus = _uiState.value.effectiveStatus ?: _uiState.value.session?.status,
            )
        }

        private fun latestPendingApprovalCallId(): String? {
            return findLatestPendingApprovalCallId(
                messages = _uiState.value.messages,
                sessionStatus = _uiState.value.effectiveStatus ?: _uiState.value.session?.status,
            )
        }

        private fun removeOptimisticMessage(text: String) {
            val normalizedText = text.trim()
            val index =
                optimisticMessages.indexOfFirst { message ->
                    message.text == normalizedText
                }
            if (index >= 0) {
                optimisticMessages.removeAt(index)
            }
        }

        private fun requireValidSettings(): RelaySettings? {
            val settings = settingsRepository.load()
            val validationError = settings.relayValidationError()
            if (validationError != null) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = validationError,
                    )
                }
                return null
            }
            return settings
        }

        private fun emitScrollToBottom() {
            emitScrollToBottom(_uiState.value.messages.lastIndex)
        }

        private fun emitScrollToBottom(targetIndex: Int) {
            if (targetIndex < 0) {
                return
            }
            viewModelScope.launch {
                _events.emit(DetailEvent.ScrollToBottom(targetIndex = targetIndex))
            }
        }

        private fun beginCatchUp() {
            isCatchingUp = true
            catchUpBuffer.clear()
        }

        private suspend fun finishCatchUp(processBufferedEvents: Boolean) {
            val bufferedEvents =
                catchUpBuffer
                    .sortedBy(ServerMessage.Event::seq)
                    .distinctBy(ServerMessage.Event::seq)
            catchUpBuffer.clear()
            isCatchingUp = false

            if (processBufferedEvents) {
                bufferedEvents.forEach { event ->
                    handleSessionEvent(
                        event = event,
                        allowAutoScroll = false,
                    )
                }
            }
        }

        private fun disconnectedBanner(
            connectionState: ConnectionState,
            currentBanner: ConnectionBannerUiState?,
        ): ConnectionBannerUiState? =
            when (connectionState) {
                ConnectionState.Connected -> currentBanner?.takeIf(ConnectionBannerUiState::isSuccess)
                ConnectionState.Connecting,
                is ConnectionState.Disconnected,
                ->
                    ConnectionBannerUiState(
                        message = "网络不稳定，正在重连...",
                        isSuccess = false,
                    )

                ConnectionState.NotConfigured -> null
            }
    }

private const val INTERACTIVE_ANSWER_SYNC_INTERVAL_MS = 500L
private const val INTERACTIVE_ANSWER_SYNC_TIMEOUT_MS = 15_000L
