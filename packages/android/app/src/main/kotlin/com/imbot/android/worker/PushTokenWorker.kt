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
        var pushToken: String? = null

        return try {
            val settings = settingsRepository.load()
            if (!settings.isConfigured()) {
                Result.success()
            } else {
                pushToken =
                    settingsRepository.loadPendingPushToken()
                        ?: FirebaseMessaging.getInstance().token.awaitValue().trim()

                val currentPushToken = pushToken
                if (currentPushToken.isNullOrBlank()) {
                    Result.retry()
                } else {
                    relayHttpClient.registerPushToken(
                        relayUrl = settings.relayUrl,
                        token = settings.token,
                        fcmToken = currentPushToken,
                    ).fold(
                        onSuccess = {
                            settingsRepository.clearPendingPushToken(currentPushToken)
                            Result.success()
                        },
                        onFailure = {
                            settingsRepository.savePendingPushToken(currentPushToken)
                            Result.retry()
                        },
                    )
                }
            }
        } catch (_: Exception) {
            pushToken?.let(settingsRepository::savePendingPushToken)
            Result.retry()
        }
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
