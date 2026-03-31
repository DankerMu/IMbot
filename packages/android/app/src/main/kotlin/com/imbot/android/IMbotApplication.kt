package com.imbot.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.imbot.android.data.SettingsRepository
import com.imbot.android.worker.PushTokenWorker
import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IMbotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        PushTokenWorker.enqueuePeriodic(this)

        val settingsRepository = SettingsRepository(this)
        if (settingsRepository.load().isConfigured() || settingsRepository.loadPendingPushToken() != null) {
            PushTokenWorker.enqueueImmediate(this)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                SESSION_NOTIFICATION_CHANNEL_ID,
                "会话通知",
                NotificationManager.IMPORTANCE_HIGH,
            )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val SESSION_NOTIFICATION_CHANNEL_ID = "imbot_sessions"
    }
}
