@file:Suppress("TooManyFunctions")

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

data class RelaySession(
    val id: String,
    val provider: String,
    val hostId: String,
    val workspaceCwd: String,
    val initialPrompt: String?,
    val model: String?,
    val status: String,
    val errorMessage: String?,
    val createdAt: String,
    val updatedAt: String,
    val lastActiveAt: String,
)

data class SessionResponse(
    val sessionId: String,
    val rawJson: String,
)

data class RelayHost(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val providers: List<String>,
)

data class RelayWorkspaceRoot(
    val id: String,
    val hostId: String,
    val provider: String,
    val path: String,
    val label: String?,
)

data class BrowseResult(
    val path: String,
    val directories: List<BrowseEntry>,
)

data class BrowseEntry(
    val name: String,
    val path: String,
)

private data class RelayErrorResponse(
    val code: String,
    val message: String,
)

@Suppress("TooManyFunctions")
@Singleton
open class RelayHttpClient
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        open suspend fun getHosts(
            relayUrl: String,
            token: String,
        ): Result<List<RelayHost>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val request =
                        Request.Builder()
                            .url(
                                requireRelayBaseUrl(relayUrl)
                                    .newBuilder()
                                    .encodedPath("/v1/hosts")
                                    .build(),
                            )
                            .header("Authorization", "Bearer $token")
                            .get()
                            .build()

                    okHttpClient.newCall(request).await().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            error(buildRelayErrorMessage(response, bodyText, "Load hosts"))
                        }

                        val root = bodyText.toJsonObjectOrNull() ?: error("Relay returned malformed JSON")
                        val hostsArray = root.optJSONArray("hosts") ?: error("Relay response is missing hosts")

                        buildList {
                            for (index in 0 until hostsArray.length()) {
                                val hostObject =
                                    hostsArray.optJSONObject(index)
                                        ?: error("Relay returned malformed host payload")
                                add(hostObject.toRelayHost())
                            }
                        }
                    }
                }
            }

        open suspend fun getHostRoots(
            relayUrl: String,
            token: String,
            hostId: String,
        ): Result<List<RelayWorkspaceRoot>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val request =
                        Request.Builder()
                            .url(
                                requireRelayBaseUrl(relayUrl)
                                    .newBuilder()
                                    .addPathSegments("v1/hosts")
                                    .addPathSegment(hostId)
                                    .addPathSegment("roots")
                                    .build(),
                            )
                            .header("Authorization", "Bearer $token")
                            .get()
                            .build()

                    okHttpClient.newCall(request).await().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            error(buildRelayErrorMessage(response, bodyText, "Load workspace roots"))
                        }

                        val root = bodyText.toJsonObjectOrNull() ?: error("Relay returned malformed JSON")
                        val rootsArray = root.optJSONArray("roots") ?: error("Relay response is missing roots")

                        buildList {
                            for (index in 0 until rootsArray.length()) {
                                val rootObject =
                                    rootsArray.optJSONObject(index)
                                        ?: error("Relay returned malformed workspace root payload")
                                add(rootObject.toRelayWorkspaceRoot())
                            }
                        }
                    }
                }
            }

        open suspend fun browseDirectory(
            relayUrl: String,
            token: String,
            hostId: String,
            path: String,
        ): Result<BrowseResult> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val request =
                        Request.Builder()
                            .url(
                                requireRelayBaseUrl(relayUrl)
                                    .newBuilder()
                                    .addPathSegments("v1/hosts")
                                    .addPathSegment(hostId)
                                    .addPathSegment("browse")
                                    .addQueryParameter("path", path)
                                    .build(),
                            )
                            .header("Authorization", "Bearer $token")
                            .get()
                            .build()

                    okHttpClient.newCall(request).await().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            error(buildRelayErrorMessage(response, bodyText, "Browse directory"))
                        }

                        val root = bodyText.toJsonObjectOrNull() ?: error("Relay returned malformed JSON")
                        root.toBrowseResult()
                    }
                }
            }

        suspend fun getSessions(
            relayUrl: String,
            token: String,
        ): Result<List<RelaySession>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val request =
                        Request.Builder()
                            .url(
                                requireRelayBaseUrl(relayUrl)
                                    .newBuilder()
                                    .encodedPath("/v1/sessions")
                                    .build(),
                            )
                            .header("Authorization", "Bearer $token")
                            .get()
                            .build()

                    okHttpClient.newCall(request).await().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            error(buildRelayErrorMessage(response, bodyText, "Load sessions"))
                        }

                        val root = bodyText.toJsonObjectOrNull() ?: error("Relay returned malformed JSON")
                        val sessionsArray =
                            root.optJSONArray("sessions") ?: error("Relay response is missing sessions")

                        buildList {
                            for (index in 0 until sessionsArray.length()) {
                                val sessionObject =
                                    sessionsArray.optJSONObject(index)
                                        ?: error("Relay returned malformed session payload")
                                add(sessionObject.toRelaySession())
                            }
                        }
                    }
                }
            }

        open suspend fun createSession(
            relayUrl: String,
            token: String,
            provider: String,
            hostId: String,
            cwd: String,
            prompt: String,
            permissionMode: String,
            model: String? = null,
        ): Result<SessionResponse> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val baseUrl = requireRelayBaseUrl(relayUrl)
                    val requestBody =
                        JSONObject()
                            .put("provider", provider)
                            .put("host_id", hostId)
                            .put("cwd", cwd)
                            .put("prompt", prompt)
                            .put("permission_mode", permissionMode)
                            .also { payload ->
                                if (!model.isNullOrBlank()) {
                                    payload.put("model", model)
                                }
                            }
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
                            error(buildRelayErrorMessage(response, bodyText, "Create session"))
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

        suspend fun deleteSession(
            relayUrl: String,
            token: String,
            sessionId: String,
        ): Result<Unit> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val request =
                        Request.Builder()
                            .url(
                                requireRelayBaseUrl(relayUrl)
                                    .newBuilder()
                                    .addPathSegments("v1/sessions")
                                    .addPathSegment(sessionId)
                                    .build(),
                            )
                            .header("Authorization", "Bearer $token")
                            .delete()
                            .build()

                    okHttpClient.newCall(request).await().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            error(buildRelayErrorMessage(response, bodyText, "Delete session"))
                        }
                    }
                }
            }

        suspend fun registerPushToken(
            relayUrl: String,
            token: String,
            fcmToken: String,
        ): Result<Unit> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val baseUrl = requireRelayBaseUrl(relayUrl)
                    val requestBody =
                        JSONObject()
                            .put("fcm_token", fcmToken)
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)

                    val request =
                        Request.Builder()
                            .url(
                                baseUrl.newBuilder()
                                    .encodedPath("/v1/push/register")
                                    .build(),
                            )
                            .header("Authorization", "Bearer $token")
                            .post(requestBody)
                            .build()

                    okHttpClient.newCall(request).await().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            error(buildRelayErrorMessage(response, bodyText, "Register push token"))
                        }
                    }
                }
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

private fun requireRelayBaseUrl(relayUrl: String): okhttp3.HttpUrl {
    return relayUrl.toRelayBaseHttpUrl() ?: error("Relay URL is invalid: $relayUrl")
}

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

private fun buildRelayErrorMessage(
    response: Response,
    bodyText: String,
    action: String,
): String {
    val errorResponse = bodyText.toRelayErrorResponse()
    return buildString {
        if (errorResponse?.message?.isNotBlank() == true) {
            append(errorResponse.message)
        } else {
            append("$action failed with HTTP ${response.code}")
            if (errorResponse?.code?.isNotBlank() == true) {
                append(" (${errorResponse.code})")
            }
        }
    }
}

private fun JSONObject.toRelaySession(): RelaySession {
    val id = optString("id")
    require(id.isNotBlank()) { "Relay response is missing session.id" }

    return RelaySession(
        id = id,
        provider = optString("provider"),
        hostId = optString("host_id"),
        workspaceCwd = optString("workspace_cwd"),
        initialPrompt = optString("initial_prompt").ifBlank { null },
        model = optString("model").ifBlank { null },
        status = optString("status"),
        errorMessage = optString("error_message").ifBlank { null },
        createdAt = optString("created_at"),
        updatedAt = optString("updated_at").ifBlank { optString("created_at") },
        lastActiveAt = optString("last_active_at").ifBlank { optString("updated_at") },
    )
}

private fun JSONObject.toRelayHost(): RelayHost {
    val id = optString("id")
    require(id.isNotBlank()) { "Relay response is missing host.id" }

    val providersArray = optJSONArray("providers")
    val providers =
        buildList {
            if (providersArray != null) {
                for (index in 0 until providersArray.length()) {
                    val provider = providersArray.optString(index)
                    if (provider.isNotBlank()) {
                        add(provider)
                    }
                }
            }
        }

    return RelayHost(
        id = id,
        name = optString("name"),
        type = optString("type"),
        status = optString("status"),
        providers = providers,
    )
}

private fun JSONObject.toRelayWorkspaceRoot(): RelayWorkspaceRoot {
    val id = optString("id")
    require(id.isNotBlank()) { "Relay response is missing root.id" }

    return RelayWorkspaceRoot(
        id = id,
        hostId = optString("host_id"),
        provider = optString("provider"),
        path = optString("path"),
        label = optString("label").ifBlank { null },
    )
}

private fun JSONObject.toBrowseResult(): BrowseResult {
    val path = optString("path")
    require(path.isNotBlank()) { "Relay response is missing browse path" }
    val directoriesArray = optJSONArray("directories") ?: error("Relay response is missing directories")

    return BrowseResult(
        path = path,
        directories =
            buildList {
                for (index in 0 until directoriesArray.length()) {
                    val directoryObject =
                        directoriesArray.optJSONObject(index)
                            ?: error("Relay returned malformed browse directory payload")
                    add(directoryObject.toBrowseEntry())
                }
            },
    )
}

private fun JSONObject.toBrowseEntry(): BrowseEntry {
    val path = optString("path")
    require(path.isNotBlank()) { "Relay response is missing directory path" }

    return BrowseEntry(
        name = optString("name"),
        path = path,
    )
}
