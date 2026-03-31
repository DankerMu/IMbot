package com.imbot.android.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.imbot.android.IMbotApplication
import com.imbot.android.data.repository.SessionRepository
import com.imbot.android.network.RelayWsClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ForegroundServiceState {
    ACTIVE,
    COOLING_DOWN,
    STOPPED,
}

internal interface ReconnectControllable {
    fun pauseReconnection()

    fun resumeReconnection()

    fun forceReconnect()

    fun isConnected(): Boolean
}

internal class ForegroundServiceLifecycleController(
    private val scope: CoroutineScope,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val onStopRequested: () -> Unit = {},
) {
    private val _state = MutableStateFlow(ForegroundServiceState.STOPPED)
    val state: StateFlow<ForegroundServiceState> = _state.asStateFlow()

    private var appInForeground = false
    private var runningSessionCount = 0
    private var cooldownJob: Job? = null

    fun onAppForegrounded() {
        appInForeground = true
        transitionToActive()
    }

    fun onAppBackgrounded() {
        appInForeground = false
        reevaluate()
    }

    fun onRunningSessionCountChanged(count: Int) {
        runningSessionCount = count.coerceAtLeast(0)
        reevaluate()
    }

    fun close() {
        cooldownJob?.cancel()
    }

    private fun reevaluate() {
        when {
            appInForeground || runningSessionCount > 0 -> transitionToActive()
            _state.value != ForegroundServiceState.STOPPED -> transitionToCoolingDown()
        }
    }

    private fun transitionToActive() {
        cooldownJob?.cancel()
        cooldownJob = null
        _state.value = ForegroundServiceState.ACTIVE
    }

    private fun transitionToCoolingDown() {
        if (_state.value == ForegroundServiceState.STOPPED) {
            return
        }
        _state.value = ForegroundServiceState.COOLING_DOWN
        if (cooldownJob?.isActive == true) {
            return
        }

        cooldownJob =
            scope.launch {
                delay(cooldownMs)
                if (!appInForeground && runningSessionCount == 0) {
                    _state.value = ForegroundServiceState.STOPPED
                    onStopRequested()
                }
            }
    }
}

internal class NetworkReconnectController(
    private val scope: CoroutineScope,
    private val reconnectControllable: ReconnectControllable,
    private val debounceMs: Long = NETWORK_RECONNECT_DEBOUNCE_MS,
) {
    private var reconnectJob: Job? = null

    fun onAvailable() {
        reconnectControllable.resumeReconnection()
        if (reconnectControllable.isConnected()) {
            return
        }

        reconnectJob?.cancel()
        reconnectJob =
            scope.launch {
                delay(debounceMs)
                if (!reconnectControllable.isConnected()) {
                    reconnectControllable.forceReconnect()
                }
            }
    }

    fun onCapabilitiesChanged() {
        onAvailable()
    }

    fun onLost() {
        reconnectJob?.cancel()
        reconnectControllable.pauseReconnection()
    }

    fun close() {
        reconnectJob?.cancel()
    }
}

@AndroidEntryPoint
class SessionService : Service() {
    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var relayWsClient: RelayWsClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val lifecycleController =
        ForegroundServiceLifecycleController(
            scope = serviceScope,
            onStopRequested = ::stopSelf,
        )

    private val networkController =
        NetworkReconnectController(
            scope = serviceScope,
            reconnectControllable =
                object : ReconnectControllable {
                    override fun pauseReconnection() {
                        relayWsClient.pauseReconnection()
                    }

                    override fun resumeReconnection() {
                        relayWsClient.resumeReconnection()
                    }

                    override fun forceReconnect() {
                        relayWsClient.forceReconnect()
                    }

                    override fun isConnected(): Boolean = relayWsClient.isConnected()
                },
        )

    private val processLifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lifecycleController.onAppForegrounded()
            }

            override fun onStop(owner: LifecycleOwner) {
                lifecycleController.onAppBackgrounded()
            }
        }

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkController.onAvailable()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                networkController.onCapabilitiesChanged()
            }

            override fun onLost(network: Network) {
                networkController.onLost()
            }
        }

    private var connectivityManager: ConnectivityManager? = null
    private var runningSessionCount = 0

    override fun onCreate() {
        super.onCreate()
        startForeground(SERVICE_NOTIFICATION_ID, buildNotification(runningSessionCount))
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        seedForegroundState()
        registerNetworkCallback()
        observeSessions()
        observeLifecycleState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lifecycleController.onAppForegrounded()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectivityManager?.runCatching {
            unregisterNetworkCallback(networkCallback)
        }
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        lifecycleController.close()
        networkController.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun seedForegroundState() {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleController.onAppForegrounded()
        } else {
            lifecycleController.onAppBackgrounded()
        }
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
    }

    private fun observeSessions() {
        serviceScope.launch {
            sessionRepository.getSessions().collectLatest { sessions ->
                runningSessionCount = sessions.count { session -> session.status.isForegroundActiveStatus() }
                lifecycleController.onRunningSessionCountChanged(runningSessionCount)
                updateNotification()
            }
        }
    }

    private fun observeLifecycleState() {
        serviceScope.launch {
            lifecycleController.state.collectLatest { state ->
                if (state == ForegroundServiceState.STOPPED) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    updateNotification()
                }
            }
        }
    }

    private fun updateNotification() {
        NotificationManagerCompat.from(this).notify(
            SERVICE_NOTIFICATION_ID,
            buildNotification(runningSessionCount),
        )
    }

    private fun buildNotification(activeSessionCount: Int): Notification =
        NotificationCompat.Builder(this, IMbotApplication.SERVICE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("IMbot")
            .setContentText(
                if (activeSessionCount > 0) {
                    "$activeSessionCount 个会话运行中"
                } else {
                    "IMbot 运行中"
                },
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SessionService::class.java),
            )
        }
    }
}

private fun String.isForegroundActiveStatus(): Boolean = this == "queued" || this == "running"

private const val SERVICE_NOTIFICATION_ID = 2001
private const val DEFAULT_COOLDOWN_MS = 5 * 60 * 1_000L
private const val NETWORK_RECONNECT_DEBOUNCE_MS = 1_000L
