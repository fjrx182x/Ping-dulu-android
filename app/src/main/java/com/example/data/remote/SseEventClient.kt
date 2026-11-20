package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
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

class SseEventClient(
    private val baseOkHttpClient: OkHttpClient
) {
    private val sseClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    fun observeEvents(
        baseUrlProvider: () -> String,
        reconnectDelayMillis: Long = 10_000L
    ): Flow<SseEvent> = flow {
        while (currentCoroutineContext().isActive) {
            val baseUrl = NetworkModule.normalizeBaseUrl(baseUrlProvider())
            val request = Request.Builder()
                .url("${baseUrl}api/events")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build()

            emit(SseEvent.StateChanged(SseConnectionState.CONNECTING))

            val call = sseClient.newCall(request)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        emit(SseEvent.StateChanged(SseConnectionState.OFFLINE))
                    } else {
                        emit(SseEvent.StateChanged(SseConnectionState.CONNECTED_LIVE))
                        val body = response.body
                        if (body != null) {
                            BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                                var currentEvent = "message"
                                val dataBuilder = StringBuilder()
                                while (currentCoroutineContext().isActive) {
                                    val line = reader.readLine() ?: break
                                    when {
                                        line.startsWith("event:") -> {
                                            currentEvent = line.removePrefix("event:").trim()
                                        }
                                        line.startsWith("data:") -> {
                                            if (dataBuilder.isNotEmpty()) dataBuilder.append('\n')
                                            dataBuilder.append(line.removePrefix("data:").trim())
                                        }
                                        line.isBlank() -> {
                                            if (dataBuilder.isNotEmpty()) {
                                                emit(
                                                    SseEvent.DataMessage(
                                                        eventType = currentEvent,
                                                        payload = dataBuilder.toString()
                                                    )
                                                )
                                                dataBuilder.clear()
                                                currentEvent = "message"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                call.cancel()
                emit(SseEvent.StateChanged(SseConnectionState.OFFLINE))
            }

            if (currentCoroutineContext().isActive) {
                delay(reconnectDelayMillis)
            }
        }
    }.flowOn(Dispatchers.IO)
}
