package com.example.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class SseConnectionState {
    CONNECTING,
    CONNECTED_LIVE,
    OFFLINE
}

sealed interface SseEvent {
    data class StateChanged(val state: SseConnectionState) : SseEvent
    data class DataMessage(val eventType: String, val payload: String) : SseEvent
}

open class SseEventClient(
    private val baseOkHttpClient: OkHttpClient
) {
    private val sseClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    open fun observeEvents(
        baseUrlProvider: () -> String,
        reconnectDelayMillis: Long = DEFAULT_RECONNECT_DELAY_MILLIS,
        maxReconnectDelayMillis: Long = DEFAULT_MAX_RECONNECT_DELAY_MILLIS
    ): Flow<SseEvent> = flow {
        var currentDelay = reconnectDelayMillis.coerceAtLeast(1_000L)
        while (currentCoroutineContext().isActive) {
            val baseUrl = NetworkModule.normalizeBaseUrl(baseUrlProvider())
            val request = Request.Builder()
                .url("${baseUrl}api/events")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build()

            emit(SseEvent.StateChanged(SseConnectionState.CONNECTING))

            val call = sseClient.newCall(request)
            val cancelHandle = currentCoroutineContext().job.invokeOnCompletion {
                call.cancel()
            }
            var connectedSuccessfully = false
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        emit(SseEvent.StateChanged(SseConnectionState.OFFLINE))
                    } else {
                        connectedSuccessfully = true
                        currentDelay = reconnectDelayMillis.coerceAtLeast(1_000L)
                        emit(SseEvent.StateChanged(SseConnectionState.CONNECTED_LIVE))
                        val body = response.body
                        if (body != null) {
                            BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                                var currentEvent = "message"
                                val dataBuilder = StringBuilder()
                                while (currentCoroutineContext().isActive) {
                                    val line = reader.readLine() ?: break
                                    when {
                                        line.startsWith(":") -> {
                                            // SSE keep-alive heartbeat comment; keep stream open
                                        }
                                        line.startsWith("event:") -> {
                                            currentEvent = line.removePrefix("event:").trim()
                                        }
                                        line.startsWith("data:") -> {
                                            if (dataBuilder.isNotEmpty()) dataBuilder.append('\n')
                                            dataBuilder.append(line.removePrefix("data:").trim())
                                        }
                                        line.isBlank() -> {
                                            if (dataBuilder.isNotEmpty()) {
                                                val payload = dataBuilder.toString()
                                                if (!isNoiseOrHeartbeatEvent(currentEvent, payload)) {
                                                    emit(
                                                        SseEvent.DataMessage(
                                                            eventType = currentEvent,
                                                            payload = payload
                                                        )
                                                    )
                                                }
                                                dataBuilder.clear()
                                                currentEvent = "message"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (currentCoroutineContext().isActive) {
                            emit(SseEvent.StateChanged(SseConnectionState.OFFLINE))
                        }
                    }
                }
            } catch (ce: CancellationException) {
                call.cancel()
                throw ce
            } catch (_: Exception) {
                call.cancel()
                if (currentCoroutineContext().isActive) {
                    emit(SseEvent.StateChanged(SseConnectionState.OFFLINE))
                }
            } finally {
                cancelHandle.dispose()
            }

            if (currentCoroutineContext().isActive) {
                delay(currentDelay)
                if (!connectedSuccessfully) {
                    currentDelay = (currentDelay * 2L).coerceAtMost(maxReconnectDelayMillis)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val DEFAULT_RECONNECT_DELAY_MILLIS = 5_000L
        const val DEFAULT_MAX_RECONNECT_DELAY_MILLIS = 60_000L

        private val NOISE_TOKENS = setOf(
            "ping",
            "pong",
            "heartbeat",
            "keepalive",
            "keep-alive",
            "keep_alive",
            "connected",
            "connect",
            "welcome",
            "hello",
            "init",
            "ready",
            "ack",
            "noop",
            "health",
            "status"
        )

        private val JSON_TYPE_REGEX = Regex(
            pattern = """"(?:type|event|action)"\s*:\s*"([^"]+)"""",
            option = RegexOption.IGNORE_CASE
        )

        /**
         * Returns true when an SSE message is a transport keep-alive, heartbeat, or handshake
         * banner that should NOT trigger a full GET /api/tasks refresh.
         */
        fun isNoiseOrHeartbeatEvent(eventType: String, payload: String): Boolean {
            val normalizedType = eventType.trim().lowercase(Locale.ROOT)
            if (normalizedType.isNotEmpty() && normalizedType in NOISE_TOKENS) {
                return true
            }

            val trimmedPayload = payload.trim()
            if (trimmedPayload.isEmpty() || trimmedPayload == "{}" || trimmedPayload == "[]") {
                return true
            }

            val unwrapped = trimmedPayload.trim('"').trim().lowercase(Locale.ROOT)
            if (unwrapped == "ok" || unwrapped == "alive" || unwrapped == "null" || unwrapped in NOISE_TOKENS) {
                return true
            }

            if (trimmedPayload.startsWith("{")) {
                val matchedToken = JSON_TYPE_REGEX.find(trimmedPayload)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                if (matchedToken != null && matchedToken in NOISE_TOKENS) {
                    return true
                }
            }
            return false
        }
    }
}
