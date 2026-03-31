package com.imbot.android.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
class RelayWsClient
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val reconnectDelaysMs = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.NotConfigured)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
        val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

        private val _events = MutableSharedFlow<ServerMessage.Event>(extraBufferCapacity = 64)
        val events: SharedFlow<ServerMessage.Event> = _events.asSharedFlow()

        private val _rawMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val rawMessages: SharedFlow<String> = _rawMessages.asSharedFlow()

        private var configuredRelayUrl: String? = null
        private var configuredToken: String? = null
        private var activeSessionId: String? = null
        private var trackedSessionIds: Set<String> = emptySet()
        private var currentSocket: WebSocket? = null
        private var reconnectJob: Job? = null
        private var shouldReconnect = false
        private var reconnectAttempt = 0

        fun connect(
            relayUrl: String,
            token: String,
        ) {
            configuredRelayUrl = relayUrl.trim()
            configuredToken = token.trim()

            if (configuredRelayUrl.isNullOrBlank() || configuredToken.isNullOrBlank()) {
                shouldReconnect = false
                reconnectAttempt = 0
                reconnectJob?.cancel()
                currentSocket?.cancel()
                currentSocket = null
                _connectionState.value = ConnectionState.NotConfigured
                return
            }

            shouldReconnect = true
            reconnectAttempt = 0
            reconnectJob?.cancel()
            openSocket()
        }

        fun disconnect() {
            shouldReconnect = false
            reconnectAttempt = 0
            reconnectJob?.cancel()
            currentSocket?.close(1000, "client disconnect")
            currentSocket = null
            _connectionState.value = ConnectionState.Disconnected("Disconnected by client")
        }

        fun send(message: String): Boolean = currentSocket?.send(message) == true

        fun subscribe(sessionId: String) {
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

        fun clearSubscription() {
            val previousSubscriptions = currentSubscriptionIds()
            activeSessionId = null
            syncSubscriptions(previousSubscriptions, currentSubscriptionIds())
        }

        fun setTrackedSessionIds(sessionIds: Set<String>) {
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

            if (socketUrl == null) {
                _connectionState.value = ConnectionState.Disconnected("Invalid relay URL")
                return
            }

            reconnectJob?.cancel()
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
                            _connectionState.value = ConnectionState.Connected
                            currentSubscriptionIds().forEach(::sendSubscribe)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            if (currentSocket !== webSocket) {
                                return
                            }

                            scope.launch {
                                val parsedMessage = parseServerMessage(text)
                                if (parsedMessage != null) {
                                    if (parsedMessage is ServerMessage.Event) {
                                        _events.emit(parsedMessage)
                                        _rawMessages.emit(text)
                                    }
                                    _messages.emit(parsedMessage)
                                }
                            }
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

            currentSocket = null
            _connectionState.value = ConnectionState.Disconnected(reason)
            if (shouldReconnect) {
                scheduleReconnect()
            }
        }

        private fun scheduleReconnect() {
            if (reconnectJob?.isActive == true) {
                return
            }

            reconnectJob =
                scope.launch {
                    val delayMs = reconnectDelaysMs[minOf(reconnectAttempt, reconnectDelaysMs.lastIndex)]
                    reconnectAttempt += 1
                    delay(delayMs)
                    if (shouldReconnect) {
                        openSocket()
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

private fun String.toRelayWebSocketUrl(token: String): String? {
    val baseUrl = toRelayBaseHttpUrl() ?: return null
    return baseUrl.newBuilder()
        .scheme(if (baseUrl.isHttps) "wss" else "ws")
        .encodedPath("/v1/ws")
        .setQueryParameter("token", token)
        .build()
        .toString()
}
