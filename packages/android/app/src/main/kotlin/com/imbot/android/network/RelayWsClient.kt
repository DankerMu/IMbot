package com.imbot.android.network

import com.imbot.android.data.ErrorStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
open class RelayWsClient
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val errorStateManager: ErrorStateManager,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val reconnectDelaysMs = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.NotConfigured)
        open val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
        open val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

        private val _events = MutableSharedFlow<ServerMessage.Event>(extraBufferCapacity = 64)
        open val events: SharedFlow<ServerMessage.Event> = _events.asSharedFlow()

        private val _rawMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
        open val rawMessages: SharedFlow<String> = _rawMessages.asSharedFlow()

        private var configuredRelayUrl: String? = null
        private var configuredToken: String? = null
        private var activeSessionId: String? = null
        private var trackedSessionIds: Set<String> = emptySet()
        private var currentSocket: WebSocket? = null
        private var reconnectJob: Job? = null
        private var heartbeatJob: Job? = null
        private var shouldReconnect = false
        private var networkAvailable = true
        private var reconnectAttempt = 0
        private var lastPongReceivedAtMs = System.currentTimeMillis()
        private val frameChannel = Channel<String>(Channel.UNLIMITED)

        init {
            scope.launch {
                for (frame in frameChannel) {
                    val parsedMessage = parseServerMessage(frame)
                    if (parsedMessage != null) {
                        handleMessageSideEffects(parsedMessage)
                        if (parsedMessage is ServerMessage.Event) {
                            _events.emit(parsedMessage)
                            _rawMessages.emit(frame)
                        }
                        _messages.emit(parsedMessage)
                    }
                }
            }
        }

        open fun connect(
            relayUrl: String,
            token: String,
        ) {
            configuredRelayUrl = relayUrl.trim()
            configuredToken = token.trim()

            if (configuredRelayUrl.isNullOrBlank() || configuredToken.isNullOrBlank()) {
                shouldReconnect = false
                networkAvailable = true
                reconnectAttempt = 0
                reconnectJob?.cancel()
                heartbeatJob?.cancel()
                currentSocket?.cancel()
                currentSocket = null
                _connectionState.value = ConnectionState.NotConfigured
                errorStateManager.setRelayConnected(true)
                return
            }

            shouldReconnect = true
            networkAvailable = true
            reconnectAttempt = 0
            reconnectJob?.cancel()
            openSocket()
        }

        open fun disconnect() {
            shouldReconnect = false
            networkAvailable = true
            reconnectAttempt = 0
            reconnectJob?.cancel()
            heartbeatJob?.cancel()
            currentSocket?.close(1000, "client disconnect")
            currentSocket = null
            _connectionState.value = ConnectionState.Disconnected("Disconnected by client")
            errorStateManager.setRelayConnected(true)
        }

        open fun send(message: String): Boolean = currentSocket?.send(message) == true

        open fun forceReconnect() {
            if (!shouldReconnect || !networkAvailable) {
                return
            }

            reconnectAttempt = 0
            reconnectJob?.cancel()
            heartbeatJob?.cancel()
            currentSocket?.cancel()
            currentSocket = null
            openSocket()
        }

        open fun pauseReconnection() {
            networkAvailable = false
            reconnectJob?.cancel()
            heartbeatJob?.cancel()
            currentSocket?.cancel()
            currentSocket = null
            if (shouldReconnect) {
                _connectionState.value = ConnectionState.Disconnected("Network unavailable")
                errorStateManager.setRelayConnected(false)
            }
        }

        open fun resumeReconnection() {
            networkAvailable = true
        }

        open fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

        open fun subscribe(sessionId: String) {
            val normalizedSessionId = sessionId.trim()
            if (normalizedSessionId.isBlank()) {
                return
            }

            val previousSessionId = activeSessionId
            if (previousSessionId == normalizedSessionId) {
                return
            }

            val previousSubscriptions = currentSubscriptionIds()
            activeSessionId = normalizedSessionId
            syncSubscriptions(previousSubscriptions, currentSubscriptionIds())
        }

        open fun clearSubscription() {
            val previousSubscriptions = currentSubscriptionIds()
            activeSessionId = null
            syncSubscriptions(previousSubscriptions, currentSubscriptionIds())
        }

        open fun setTrackedSessionIds(sessionIds: Set<String>) {
            val normalizedSessionIds = sessionIds.map(String::trim).filter(String::isNotBlank).toSet()
            if (trackedSessionIds == normalizedSessionIds) {
                return
            }

            val previousSubscriptions = currentSubscriptionIds()
            trackedSessionIds = normalizedSessionIds
            syncSubscriptions(previousSubscriptions, currentSubscriptionIds())
        }

        private fun openSocket() {
            val relayUrl = configuredRelayUrl.orEmpty()
            val token = configuredToken.orEmpty()
            val socketUrl = relayUrl.toRelayWebSocketUrl(token)

            if (!networkAvailable) {
                return
            }

            if (socketUrl == null) {
                _connectionState.value = ConnectionState.Disconnected("Invalid relay URL")
                errorStateManager.setRelayConnected(true)
                return
            }

            reconnectJob?.cancel()
            heartbeatJob?.cancel()
            currentSocket?.cancel()
            _connectionState.value = ConnectionState.Connecting

            val request =
                Request.Builder()
                    .url(socketUrl)
                    .build()

            val socket =
                okHttpClient.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            if (currentSocket !== webSocket) {
                                return
                            }

                            reconnectAttempt = 0
                            lastPongReceivedAtMs = System.currentTimeMillis()
                            _connectionState.value = ConnectionState.Connected
                            errorStateManager.setRelayConnected(true)
                            startHeartbeat(webSocket)
                            currentSubscriptionIds().forEach(::sendSubscribe)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            if (currentSocket !== webSocket) {
                                return
                            }
                            frameChannel.trySend(text)
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            handleDisconnect(webSocket, "Closed ($code): ${reason.ifBlank { "no reason" }}")
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            val reason = t.message ?: response?.message ?: "WebSocket failure"
                            handleDisconnect(webSocket, reason)
                        }
                    },
                )

            currentSocket = socket
        }

        private fun handleDisconnect(
            webSocket: WebSocket,
            reason: String,
        ) {
            if (currentSocket !== webSocket) {
                return
            }

            heartbeatJob?.cancel()
            currentSocket = null
            _connectionState.value = ConnectionState.Disconnected(reason)
            errorStateManager.setRelayConnected(false)
            if (shouldReconnect) {
                scheduleReconnect()
            }
        }

        private fun scheduleReconnect() {
            if (reconnectJob?.isActive == true || !networkAvailable) {
                return
            }

            reconnectJob =
                scope.launch {
                    val delayMs = reconnectDelaysMs[minOf(reconnectAttempt, reconnectDelaysMs.lastIndex)]
                    reconnectAttempt += 1
                    delay(delayMs)
                    if (shouldReconnect && networkAvailable) {
                        openSocket()
                    }
                }
        }

        private fun startHeartbeat(webSocket: WebSocket) {
            heartbeatJob?.cancel()
            heartbeatJob =
                scope.launch {
                    while (currentSocket === webSocket && shouldReconnect) {
                        delay(PING_INTERVAL_MS)
                        if (currentSocket !== webSocket) {
                            return@launch
                        }

                        if (System.currentTimeMillis() - lastPongReceivedAtMs >= PONG_TIMEOUT_MS) {
                            webSocket.close(1001, "Pong timeout")
                            return@launch
                        }

                        val sent =
                            webSocket.send(
                                JSONObject()
                                    .put("action", "ping")
                                    .toString(),
                            )
                        if (!sent) {
                            webSocket.cancel()
                            return@launch
                        }
                    }
                }
        }

        private fun handleMessageSideEffects(message: ServerMessage) {
            when (message) {
                is ServerMessage.Event -> handleSessionEventSideEffects(message)
                is ServerMessage.HostStatus ->
                    errorStateManager.setHostStatus(
                        message.hostId,
                        message.status == STATUS_ONLINE,
                    )
                is ServerMessage.Status -> {
                    if (message.status != STATUS_FAILED) {
                        errorStateManager.clearSessionError(message.sessionId)
                    }
                }
                is ServerMessage.Error -> Unit
                ServerMessage.Pong -> {
                    lastPongReceivedAtMs = System.currentTimeMillis()
                }
            }
        }

        private fun handleSessionEventSideEffects(event: ServerMessage.Event) {
            when (event.eventType) {
                "session_error" -> {
                    if (event.payload.stringValue("error_code") == ERROR_CODE_PROVIDER_UNREACHABLE) {
                        errorStateManager.setSessionError(
                            event.sessionId,
                            providerUnavailableMessage(event.payload.stringValue("message")),
                        )
                    }
                }

                "session_started" -> errorStateManager.clearSessionError(event.sessionId)
                "session_status_changed", "session_result" -> {
                    val status = event.payload.stringValue("status").orEmpty()
                    if (status.isNotBlank() && status != STATUS_FAILED) {
                        errorStateManager.clearSessionError(event.sessionId)
                    }
                }
            }
        }

        private fun sendSubscribe(sessionId: String) {
            val payload =
                JSONObject()
                    .put("action", "subscribe")
                    .put("session_id", sessionId)
                    .toString()
            send(payload)
        }

        private fun sendUnsubscribe(sessionId: String) {
            val payload =
                JSONObject()
                    .put("action", "unsubscribe")
                    .put("session_id", sessionId)
                    .toString()
            send(payload)
        }

        private fun currentSubscriptionIds(): Set<String> =
            buildSet {
                addAll(trackedSessionIds)
                activeSessionId?.let(::add)
            }

        private fun syncSubscriptions(
            previousSubscriptions: Set<String>,
            nextSubscriptions: Set<String>,
        ) {
            if (_connectionState.value !is ConnectionState.Connected) {
                return
            }

            previousSubscriptions.subtract(nextSubscriptions).forEach(::sendUnsubscribe)
            nextSubscriptions.subtract(previousSubscriptions).forEach(::sendSubscribe)
        }
    }

private fun providerUnavailableMessage(message: String?): String {
    val normalizedMessage = message.orEmpty().lowercase()
    return when {
        normalizedMessage.contains("openclaw") || normalizedMessage.contains("gateway") ->
            "OpenClaw 不可用，请稍后重试"

        normalizedMessage.contains("book") -> "book upstream 不可用，请稍后重试"
        else -> "Claude upstream 不可用，请稍后重试"
    }
}

private fun JSONObject?.stringValue(key: String): String? {
    val payload = this ?: return null
    val value = payload.opt(key)
    return when (value) {
        null,
        JSONObject.NULL,
        -> null
        is String -> value
        else -> value.toString()
    }?.takeIf { it.isNotBlank() }
}

private fun String.toRelayWebSocketUrl(token: String): String? {
    val baseUrl = toRelayBaseHttpUrl() ?: return null
    return baseUrl.newBuilder()
        .scheme(if (baseUrl.isHttps) "wss" else "ws")
        .encodedPath("/v1/ws")
        .setQueryParameter("token", token)
        .build()
        .toString()
}

private const val PING_INTERVAL_MS = 30_000L
private const val PONG_TIMEOUT_MS = 60_000L
private const val STATUS_FAILED = "failed"
private const val STATUS_ONLINE = "online"
private const val ERROR_CODE_PROVIDER_UNREACHABLE = "provider_unreachable"
