package com.imbot.android.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.imbot.android.IMbotApplication
import com.imbot.android.MainActivity
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.RelayHttpClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {
    @Inject
    lateinit var relayHttpClient: RelayHttpClient

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return
        }

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: return
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"].orEmpty()
        showNotification(
            title = title,
            body = body,
            deepLink = buildDeepLink(remoteMessage.data),
            requestCode = remoteMessage.messageId?.hashCode() ?: title.hashCode(),
            action = remoteMessage.data["action"],
            sessionId = remoteMessage.data["session_id"],
        )
    }

    override fun onNewToken(token: String) {
        serviceScope.launch {
            val normalizedToken = token.trim()
            if (normalizedToken.isBlank()) {
                return@launch
            }

            val settings = settingsRepository.load()
            if (!settings.isConfigured()) {
                settingsRepository.savePendingPushToken(normalizedToken)
                return@launch
            }

            relayHttpClient.registerPushToken(
                relayUrl = settings.relayUrl,
                token = settings.token,
                fcmToken = normalizedToken,
            ).onSuccess {
                settingsRepository.clearPendingPushToken(normalizedToken)
            }.onFailure {
                settingsRepository.savePendingPushToken(normalizedToken)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildDeepLink(data: Map<String, String>): Uri =
        when (data["action"]) {
            "open_session" -> {
                val sessionId = data["session_id"].orEmpty().trim()
                if (sessionId.isBlank()) {
                    Uri.parse("imbot://home")
                } else {
                    Uri.parse("imbot://session/$sessionId")
                }
            }

            else -> Uri.parse("imbot://home")
        }

    @SuppressLint("MissingPermission")
    private fun showNotification(
        title: String,
        body: String,
        deepLink: Uri,
        requestCode: Int,
        action: String?,
        sessionId: String?,
    ) {
        val intent =
            Intent(Intent.ACTION_VIEW, deepLink, this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("action", action)
                putExtra("session_id", sessionId)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(this, IMbotApplication.SESSION_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        NotificationManagerCompat.from(this).notify(requestCode, notification)
    }
}
