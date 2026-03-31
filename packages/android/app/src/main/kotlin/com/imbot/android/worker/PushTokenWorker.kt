package com.imbot.android.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.imbot.android.data.SettingsRepository
import com.imbot.android.network.RelayHttpClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PushTokenWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    private val settingsRepository = SettingsRepository(appContext)
    private val relayHttpClient =
        RelayHttpClient(
            OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build(),
        )

    override suspend fun doWork(): Result {
        val settings = settingsRepository.load()
        if (!settings.isConfigured()) {
            return Result.success()
        }

        val pushToken =
            settingsRepository.loadPendingPushToken()
                ?: FirebaseMessaging.getInstance().token.awaitValue().trim()

        if (pushToken.isBlank()) {
            return Result.retry()
        }

        return relayHttpClient.registerPushToken(
            relayUrl = settings.relayUrl,
            token = settings.token,
            fcmToken = pushToken,
        ).fold(
            onSuccess = {
                settingsRepository.clearPendingPushToken(pushToken)
                Result.success()
            },
            onFailure = {
                settingsRepository.savePendingPushToken(pushToken)
                Result.retry()
            },
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "push-token-refresh"
        private const val IMMEDIATE_WORK_NAME = "push-token-register"

        private val networkConstraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        fun enqueuePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<PushTokenWorker>(12, TimeUnit.HOURS)
                    .setConstraints(networkConstraints)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueImmediate(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<PushTokenWorker>()
                    .setConstraints(networkConstraints)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

private suspend fun <T> Task<T>.awaitValue(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
            }
        }
    }
