package com.imbot.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SessionResponse(
    val sessionId: String,
    val rawJson: String,
)

private data class RelayErrorResponse(
    val code: String,
    val message: String,
)

@Singleton
class RelayHttpClient
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        suspend fun createSession(
            relayUrl: String,
            token: String,
            provider: String,
            hostId: String,
            cwd: String,
            prompt: String,
            permissionMode: String,
        ): Result<SessionResponse> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val baseUrl =
                        relayUrl.toRelayBaseHttpUrl()
                            ?: error("Relay URL is invalid: $relayUrl")
                    val requestBody =
                        JSONObject()
                            .put("provider", provider)
                            .put("host_id", hostId)
                            .put("cwd", cwd)
                            .put("prompt", prompt)
                            .put("permission_mode", permissionMode)
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)

                    val request =
                        Request.Builder()
                            .url(
                                baseUrl.newBuilder()
                                    .encodedPath("/v1/sessions")
                                    .build(),
                            )
                            .header("Authorization", "Bearer $token")
                            .post(requestBody)
                            .build()

                    okHttpClient.newCall(request).await().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val errorResponse = bodyText.toRelayErrorResponse()
                            val message =
                                buildString {
                                    if (errorResponse?.message?.isNotBlank() == true) {
                                        append(errorResponse.message)
                                    } else {
                                        append("Create session failed with HTTP ${response.code}")
                                        if (errorResponse?.code?.isNotBlank() == true) {
                                            append(" (${errorResponse.code})")
                                        }
                                    }
                                }
                            error(message)
                        }

                        val root = bodyText.toJsonObjectOrNull() ?: error("Relay returned malformed JSON")
                        val sessionObject = root.optJSONObject("session") ?: error("Relay response is missing session")
                        val sessionId = sessionObject.optString("id")
                        require(sessionId.isNotBlank()) { "Relay response is missing session.id" }

                        SessionResponse(
                            sessionId = sessionId,
                            rawJson = bodyText,
                        )
                    }
                }
            }

        private companion object {
            val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        }
    }

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        enqueue(
            object : okhttp3.Callback {
                override fun onFailure(
                    call: Call,
                    e: java.io.IOException,
                ) {
                    if (continuation.isCancelled) {
                        return
                    }
                    continuation.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(response)
                }
            },
        )

        continuation.invokeOnCancellation {
            cancel()
        }
    }

internal fun String.toRelayBaseHttpUrl() =
    when {
        startsWith("wss://") -> replaceFirst("wss://", "https://")
        startsWith("https://") -> this
        else -> null
    }?.toHttpUrlOrNull()

private fun String.toJsonObjectOrNull(): JSONObject? =
    try {
        JSONObject(this)
    } catch (_: Exception) {
        null
    }

private fun String.toRelayErrorResponse(): RelayErrorResponse? {
    val root = toJsonObjectOrNull() ?: return null
    return RelayErrorResponse(
        code = root.optString("error"),
        message = root.optString("message"),
    )
}
