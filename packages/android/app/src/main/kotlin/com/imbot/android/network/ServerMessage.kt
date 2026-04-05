package com.imbot.android.network

import org.json.JSONException
import org.json.JSONObject
import java.util.logging.Level
import java.util.logging.Logger

sealed interface ServerMessage {
    data class Event(
        val sessionId: String,
        val seq: Int,
        val eventType: String,
        val payload: JSONObject?,
        val timestamp: String,
    ) : ServerMessage

    data class Status(
        val sessionId: String,
        val status: String,
    ) : ServerMessage

    data class HostStatus(
        val hostId: String,
        val status: String,
    ) : ServerMessage

    data class SessionsChanged(
        val reason: String,
        val hostId: String?,
    ) : ServerMessage

    data class Error(
        val code: String,
        val message: String,
    ) : ServerMessage

    data object Pong : ServerMessage
}

private val logger: Logger = Logger.getLogger("com.imbot.android.network.ServerMessage")

fun parseServerMessage(json: String): ServerMessage? =
    try {
        val root = JSONObject(json)
        when (root.optString("type")) {
            "event" ->
                ServerMessage.Event(
                    sessionId = root.optString("session_id"),
                    seq = root.optInt("seq"),
                    eventType = root.optString("event_type"),
                    payload = root.optJSONObject("payload"),
                    timestamp = root.optString("timestamp"),
                )

            "status" ->
                ServerMessage.Status(
                    sessionId = root.optString("session_id"),
                    status = root.optString("status"),
                )

            "host_status" ->
                ServerMessage.HostStatus(
                    hostId = root.optString("host_id"),
                    status = root.optString("status"),
                )

            "sessions_changed" ->
                ServerMessage.SessionsChanged(
                    reason = root.optString("reason"),
                    hostId = root.optString("host_id").ifBlank { null },
                )

            "error" ->
                ServerMessage.Error(
                    code = root.optString("code"),
                    message = root.optString("message"),
                )

            "pong" -> ServerMessage.Pong
            else -> null
        }
    } catch (error: JSONException) {
        logger.log(Level.WARNING, "Ignoring malformed relay frame", error)
        null
    }
