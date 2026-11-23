package com.example.domain.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Patch Ke-8: Dual-Mode Speedtest target selector.
 * Default is PUBLIC_EDGE_100G (Cloudflare Edge 100G) so field technicians measure real ISP throughput
 * instead of getting bottlenecked by the office/local PingDulu gateway.
 */
enum class SpeedTestTargetMode(
    val displayTitle: String,
    val shortTitle: String,
    val badgeLabel: String,
    val noteTag: String,
    val serverDisplayName: String
) {
    PUBLIC_EDGE_100G(
        displayTitle = "⚡ Internet Publik (Edge 100G)",
        shortTitle = "⚡ Edge 100G",
        badgeLabel = "⚡ Edge 100G",
        noteTag = "[Target: Internet Publik 100G]",
        serverDisplayName = "Cloudflare Edge 100G"
    ),
    LOCAL_LAN_SERVER(
        displayTitle = "🏢 Server PingDulu (Lokal LAN)",
        shortTitle = "🏢 Server Lokal",
        badgeLabel = "🏢 Server Lokal",
        noteTag = "[Target: Server PingDulu Lokal]",
        serverDisplayName = "Server PingDulu Lokal"
    )
}

/**
 * Dynamically detected client public network metadata (zero hardcoded ISP names).
 */
data class ClientNetworkInfo(
    val ip: String,
    val isp: String,
    val location: String
) {
    val ispWithLocation: String
        get() = listOf(isp.trim(), location.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" • ")

    val isDetectedReal: Boolean
        get() = ip.isNotBlank() && ip != "—"
}

/**
 * Parsed auto-tags from `POST /api/speedtest/records` notes field.
 */
data class ParsedSpeedTestNotes(
    val targetMode: SpeedTestTargetMode?,
    val ispLabel: String?,
    val userNote: String
)

/**
 * Server-persisted speedtest record returned by `GET /api/speedtest/records`.
 */
data class SpeedTestServerRecord(
    val id: Long,
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val clientIp: String?,
    val ispName: String?,
    val rawNotes: String?,
    val technicianName: String?,
    val createdAtLabel: String,
    val targetMode: SpeedTestTargetMode?,
    val ispBadgeLabel: String?,
    val cleanUserNote: String
)

/**
 * Dynamic Public ISP & IP detector.
 * Primary: https://ipwho.is/
 * Fallback: https://free.freeipapi.com/api/v1/json
 * Neutral offline fallback: ISP Publik / Indonesia (NEVER hardcodes any vendor name or static IP).
 */
object RealIspDetector {

    const val DEFAULT_PRIMARY_URL = "https://ipwho.is/"
    const val DEFAULT_FALLBACK_URL = "https://free.freeipapi.com/api/v1/json"

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun detect(
        client: OkHttpClient = defaultClient,
        primaryUrl: String = DEFAULT_PRIMARY_URL,
        fallbackUrl: String = DEFAULT_FALLBACK_URL
    ): ClientNetworkInfo = withContext(Dispatchers.IO) {
        // 1. Primary: ipwho.is
        fetchJsonSafely(client, primaryUrl)?.let { json ->
            if (json.optBoolean("success", true)) {
                val ip = json.optString("ip").trim()
                val conn = json.optJSONObject("connection")
                val isp = conn?.optString("isp")?.trim().takeUnless { it.isNullOrBlank() }
                    ?: conn?.optString("org")?.trim().takeUnless { it.isNullOrBlank() }
                    ?: "ISP Publik"
                val city = json.optString("city").trim()
                val country = json.optString("country").trim()
                val location = listOf(city, country).filter { it.isNotEmpty() }.joinToString(", ")
                    .ifBlank { "Indonesia" }
                if (ip.isNotEmpty()) {
                    return@withContext ClientNetworkInfo(ip = ip, isp = isp, location = location)
                }
            }
        }

        // 2. Fallback: freeipapi.com
        fetchJsonSafely(client, fallbackUrl)?.let { json ->
            val ip = json.optString("ipAddress").trim()
            val isp = json.optString("asnOrganization").trim().takeUnless { it.isEmpty() }
                ?: json.optString("isp").trim().takeUnless { it.isEmpty() }
                ?: "ISP Publik"
            val city = json.optString("cityName").trim()
            val country = json.optString("countryName").trim()
            val location = listOf(city, country).filter { it.isNotEmpty() }.joinToString(", ")
                .ifBlank { "Indonesia" }
            if (ip.isNotEmpty()) {
                return@withContext ClientNetworkInfo(ip = ip, isp = isp, location = location)
            }
        }

        // 3. Neutral fallback when completely offline or both public GeoIP APIs fail
        ClientNetworkInfo(ip = "—", isp = "ISP Publik", location = "Indonesia")
    }

    private suspend fun fetchJsonSafely(client: OkHttpClient, url: String): JSONObject? {
        if (url.isBlank()) return null
        currentCoroutineContext().ensureActive()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "PingDulu-Android-FieldOps")
            .header("Accept", "application/json")
            .build()
        val call = client.newCall(req)
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) runCatching { call.cancel() }
        }
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()?.trim().orEmpty()
                if (body.isEmpty()) null else JSONObject(body)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        } finally {
            cancelHandle?.dispose()
        }
    }
}

/**
 * Real Multi-Stream Speedtest Engine targeting Cloudflare Edge 100G (or injectable loopback URLs for testing).
 *
 * Engineered fixes over naive multi-stream loops:
 * 1. Lock-free `AtomicLong` byte counters + 100ms UI ticker with bounded `ArrayDeque` sliding window
 *    (eliminates tens of thousands of Pair allocations and mutex contention inside 32KB read loops).
 * 2. Shared read-only 512KB upload payload streamed in 16KB slices via OkHttp 4 `toMediaType()`
 *    (zero per-request 512KB buffer duplication).
 * 3. Active `CopyOnWriteArrayList<Call>` cancellation hook so Stop / Sheet dismiss aborts every socket instantly.
 */
object PublicEdgeSpeedTestEngine {

    const val DEFAULT_PING_URL = "https://speed.cloudflare.com/__down?bytes=0"
    const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=25000000"
    const val DEFAULT_UPLOAD_URL = "https://speed.cloudflare.com/__up"

    private val OCTET_STREAM = "application/octet-stream".toMediaType()
    private val sharedUploadChunk512k: ByteArray by lazy {
        ByteArray(512 * 1024) { idx -> ((idx and 0xFF) xor 0x5A).toByte() }
    }

    private val defaultTransferClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun runPublicEdgeTest(
        preDetectedClientInfo: ClientNetworkInfo? = null,
        client: OkHttpClient = defaultTransferClient,
        pingUrlBase: String = DEFAULT_PING_URL,
        downloadUrlBase: String = DEFAULT_DOWNLOAD_URL,
        uploadUrlBase: String = DEFAULT_UPLOAD_URL,
        detectIspIfMissing: Boolean = true,
        pingSamples: Int = 8,
        downloadStreams: Int = 4,
        uploadStreams: Int = 3,
        downloadDurationMs: Long = 7_000L,
        uploadDurationMs: Long = 7_000L,
        uploadChunkBytes: Int = 512 * 1024,
        onProgress: (suspend (SpeedTestProgress) -> Unit)? = null
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        val activeCalls = CopyOnWriteArrayList<Call>()
        val parentCancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                activeCalls.forEach { call -> runCatching { call.cancel() } }
            }
        }

        try {
            currentCoroutineContext().ensureActive()

            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.PING,
                    progressPercent = 4,
                    clientIp = preDetectedClientInfo?.ip?.takeIf { it != "—" },
                    ispName = preDetectedClientInfo?.ispWithLocation,
                    statusMessage = "Mendeteksi ISP & jalur Cloudflare Edge 100G..."
                )
            )

            // 1. Real ISP / Client Info detection (reuse pre-detected if valid)
            val clientInfo = when {
                preDetectedClientInfo != null && preDetectedClientInfo.isDetectedReal -> preDetectedClientInfo
                detectIspIfMissing -> RealIspDetector.detect(client)
                else -> preDetectedClientInfo ?: ClientNetworkInfo("—", "ISP Publik", "Indonesia")
            }
            val displayIp = clientInfo.ip.takeIf { it != "—" && it.isNotBlank() }
            val displayIsp = clientInfo.ispWithLocation

            // 2. Ping & Jitter (8 sequential probes to Edge /__down?bytes=0)
            val safePingSamples = pingSamples.coerceIn(2, 12)
            val rtts = mutableListOf<Double>()
            var latestPingMs = 0.0
            var latestJitterMs = 0.0

            for (i in 0 until safePingSamples) {
                currentCoroutineContext().ensureActive()
                val probeUrl = appendCacheBuster(pingUrlBase, i)
                val req = Request.Builder()
                    .url(probeUrl)
                    .header("User-Agent", "PingDulu-Android-FieldOps")
                    .header("Cache-Control", "no-cache, no-store")
                    .build()
                val call = client.newCall(req)
                activeCalls.add(call)
                try {
                    val t0 = System.nanoTime()
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IOException("HTTP ${resp.code} dari Edge Ping")
                        }
                        resp.body?.bytes()
                    }
                    val rttMs = (System.nanoTime() - t0) / 1_000_000.0
                    rtts.add(rttMs)
                    // Discard first cold TCP/TLS handshake when we already have >= 3 warm probes
                    val effectiveRtts = if (rtts.size >= 3) rtts.drop(1) else rtts
                    latestPingMs = round1(effectiveRtts.average())
                    latestJitterMs = round1(computeJitter(effectiveRtts))

                    val isJitterHalf = i >= (safePingSamples / 2)
                    onProgress?.invoke(
                        SpeedTestProgress(
                            phase = if (isJitterHalf) SpeedTestPhase.JITTER else SpeedTestPhase.PING,
                            progressPercent = 8 + ((i + 1) * 16 / safePingSamples),
                            pingMs = latestPingMs,
                            jitterMs = latestJitterMs,
                            clientIp = displayIp,
                            ispName = displayIsp,
                            statusMessage = if (isJitterHalf) {
                                String.format(Locale.US, "Mengukur Jitter Edge: %.1f ms", latestJitterMs)
                            } else {
                                String.format(Locale.US, "Mengukur Ping Edge (%d/%d): %.1f ms", i + 1, safePingSamples, latestPingMs)
                            }
                        )
                    )
                } finally {
                    activeCalls.remove(call)
                }
                if (i < safePingSamples - 1) delay(45L)
            }

            if (rtts.isEmpty()) {
                throw IOException("Server Cloudflare Edge 100G tidak merespons uji ping.")
            }

            // 3. Multi-Stream Download (4 parallel coroutines)
            val safeDlDurationMs = downloadDurationMs.coerceIn(300L, 12_000L)
            val safeDlStreams = downloadStreams.coerceIn(1, 6)
            val totalDlBytes = AtomicLong(0L)
            val dlSparkline = mutableListOf<Float>()
            val dlWindowHistory = ArrayDeque<Pair<Long, Long>>(160)
            val dlStartTimeMs = System.currentTimeMillis()
            val dlStartNanos = System.nanoTime()

            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.DOWNLOAD,
                    progressPercent = 25,
                    pingMs = latestPingMs,
                    jitterMs = latestJitterMs,
                    clientIp = displayIp,
                    ispName = displayIsp,
                    statusMessage = "Mengukur Unduh Multi-Stream (Edge 100G)..."
                )
            )

            coroutineScope {
                val dlTicker = launch {
                    var smoothedMbps = 0.0
                    while (isActive) {
                        delay(100L)
                        val now = System.currentTimeMillis()
                        val elapsedMs = (now - dlStartTimeMs).coerceAtLeast(1L)
                        val bytesNow = totalDlBytes.get()
                        dlWindowHistory.addLast(now to bytesNow)
                        while (dlWindowHistory.size > 150) dlWindowHistory.removeFirst()

                        val instMbps = computeSlidingWindowMbps(
                            window = dlWindowHistory,
                            nowMs = now,
                            elapsedMs = elapsedMs,
                            totalBytes = bytesNow
                        )
                        smoothedMbps = if (smoothedMbps <= 0.01) instMbps else (smoothedMbps * 0.45 + instMbps * 0.55)
                        val rounded = round2(smoothedMbps)
                        appendBoundedSample(dlSparkline, rounded.toFloat())

                        val progress = 25 + ((elapsedMs.toDouble() / safeDlDurationMs) * 35.0)
                            .roundToInt().coerceIn(0, 35)
                        onProgress?.invoke(
                            SpeedTestProgress(
                                phase = SpeedTestPhase.DOWNLOAD,
                                progressPercent = progress,
                                currentSpeedMbps = rounded,
                                pingMs = latestPingMs,
                                jitterMs = latestJitterMs,
                                downloadMbps = rounded,
                                downloadSamples = dlSparkline.toList(),
                                clientIp = displayIp,
                                ispName = displayIsp,
                                statusMessage = String.format(Locale.US, "Mengukur Unduh: %.2f Mbps", rounded)
                            )
                        )
                    }
                }

                val dlWorkers = (0 until safeDlStreams).map { streamIdx ->
                    launch(Dispatchers.IO) {
                        val buffer = ByteArray(32 * 1024)
                        var seq = 0
                        while (isActive && (System.currentTimeMillis() - dlStartTimeMs) < safeDlDurationMs) {
                            val req = Request.Builder()
                                .url(appendCacheBuster(downloadUrlBase, streamIdx * 1000 + seq++))
                                .header("User-Agent", "PingDulu-Android-FieldOps")
                                .header("Cache-Control", "no-cache, no-store")
                                .build()
                            val call = client.newCall(req)
                            activeCalls.add(call)
                            try {
                                call.execute().use { resp ->
                                    if (!resp.isSuccessful) {
                                        delay(80L)
                                        return@use
                                    }
                                    val stream = resp.body?.byteStream() ?: return@use
                                    while (isActive && (System.currentTimeMillis() - dlStartTimeMs) < safeDlDurationMs) {
                                        val read = stream.read(buffer)
                                        if (read == -1) break
                                        totalDlBytes.addAndGet(read.toLong())
                                    }
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (_: Exception) {
                                if (!isActive) break
                                delay(80L)
                            } finally {
                                runCatching { call.cancel() }
                                activeCalls.remove(call)
                            }
                        }
                    }
                }

                delay(safeDlDurationMs)
                // Abort in-flight download calls immediately so phase transitions smoothly to Upload
                activeCalls.forEach { call -> runCatching { call.cancel() } }
                dlWorkers.forEach { it.cancel() }
                dlTicker.cancel()
            }

            val dlElapsedNanos = (System.nanoTime() - dlStartNanos).coerceAtLeast(1_000_000L)
            val dlTotalFinal = totalDlBytes.get()
            if (dlTotalFinal <= 0L) {
                throw IOException("Gagal mengunduh data uji dari Cloudflare Edge 100G.")
            }
            val finalDlMbps = round2(
                computeSteadyThroughputMbps(
                    window = dlWindowHistory,
                    startTimeMs = dlStartTimeMs,
                    warmupMs = min(1_000L, safeDlDurationMs / 3L),
                    totalBytes = dlTotalFinal,
                    totalElapsedNanos = dlElapsedNanos
                )
            )
            appendBoundedSample(dlSparkline, finalDlMbps.toFloat())

            delay(150L)

            // 4. Multi-Stream Upload (3 parallel OkHttp streaming POSTs)
            val safeUlDurationMs = uploadDurationMs.coerceIn(300L, 12_000L)
            val safeUlStreams = uploadStreams.coerceIn(1, 4)
            val safeChunkSize = uploadChunkBytes.coerceIn(16 * 1024, sharedUploadChunk512k.size)
            val totalUlBytes = AtomicLong(0L)
            val ulSparkline = mutableListOf<Float>()
            val ulWindowHistory = ArrayDeque<Pair<Long, Long>>(160)
            val ulStartTimeMs = System.currentTimeMillis()
            val ulStartNanos = System.nanoTime()

            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.UPLOAD,
                    progressPercent = 62,
                    currentSpeedMbps = 0.0,
                    pingMs = latestPingMs,
                    jitterMs = latestJitterMs,
                    downloadMbps = finalDlMbps,
                    downloadSamples = dlSparkline.toList(),
                    clientIp = displayIp,
                    ispName = displayIsp,
                    statusMessage = "Mengukur Unggah Multi-Stream (Edge 100G)..."
                )
            )

            coroutineScope {
                val ulTicker = launch {
                    var smoothedMbps = 0.0
                    while (isActive) {
                        delay(100L)
                        val now = System.currentTimeMillis()
                        val elapsedMs = (now - ulStartTimeMs).coerceAtLeast(1L)
                        val bytesNow = totalUlBytes.get()
                        ulWindowHistory.addLast(now to bytesNow)
                        while (ulWindowHistory.size > 150) ulWindowHistory.removeFirst()

                        val instMbps = computeSlidingWindowMbps(
                            window = ulWindowHistory,
                            nowMs = now,
                            elapsedMs = elapsedMs,
                            totalBytes = bytesNow
                        )
                        smoothedMbps = if (smoothedMbps <= 0.01) instMbps else (smoothedMbps * 0.45 + instMbps * 0.55)
                        val rounded = round2(smoothedMbps)
                        appendBoundedSample(ulSparkline, rounded.toFloat())

                        val progress = 62 + ((elapsedMs.toDouble() / safeUlDurationMs) * 36.0)
                            .roundToInt().coerceIn(0, 36)
                        onProgress?.invoke(
                            SpeedTestProgress(
                                phase = SpeedTestPhase.UPLOAD,
                                progressPercent = progress,
                                currentSpeedMbps = rounded,
                                pingMs = latestPingMs,
                                jitterMs = latestJitterMs,
                                downloadMbps = finalDlMbps,
                                uploadMbps = rounded,
                                downloadSamples = dlSparkline.toList(),
                                uploadSamples = ulSparkline.toList(),
                                clientIp = displayIp,
                                ispName = displayIsp,
                                statusMessage = String.format(Locale.US, "Mengukur Unggah: %.2f Mbps", rounded)
                            )
                        )
                    }
                }

                val ulWorkers = (0 until safeUlStreams).map { streamIdx ->
                    launch(Dispatchers.IO) {
                        var seq = 0
                        while (isActive && (System.currentTimeMillis() - ulStartTimeMs) < safeUlDurationMs) {
                            val workerScope = this
                            val streamingBody = object : RequestBody() {
                                override fun contentType() = OCTET_STREAM
                                override fun contentLength(): Long = safeChunkSize.toLong()
                                override fun writeTo(sink: BufferedSink) {
                                    var offset = 0
                                    while (workerScope.isActive &&
                                        offset < safeChunkSize &&
                                        (System.currentTimeMillis() - ulStartTimeMs) < safeUlDurationMs
                                    ) {
                                        val step = min(16 * 1024, safeChunkSize - offset)
                                        sink.write(sharedUploadChunk512k, offset, step)
                                        offset += step
                                        totalUlBytes.addAndGet(step.toLong())
                                    }
                                    sink.flush()
                                }
                            }
                            val req = Request.Builder()
                                .url(appendCacheBuster(uploadUrlBase, streamIdx * 1000 + seq++))
                                .header("User-Agent", "PingDulu-Android-FieldOps")
                                .header("Cache-Control", "no-cache, no-store")
                                .post(streamingBody)
                                .build()
                            val call = client.newCall(req)
                            activeCalls.add(call)
                            try {
                                call.execute().use { resp ->
                                    resp.body?.close()
                                }
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (_: Exception) {
                                if (!isActive) break
                                delay(80L)
                            } finally {
                                runCatching { call.cancel() }
                                activeCalls.remove(call)
                            }
                        }
                    }
                }

                delay(safeUlDurationMs)
                activeCalls.forEach { call -> runCatching { call.cancel() } }
                ulWorkers.forEach { it.cancel() }
                ulTicker.cancel()
            }

            val ulElapsedNanos = (System.nanoTime() - ulStartNanos).coerceAtLeast(1_000_000L)
            val ulTotalFinal = totalUlBytes.get()
            if (ulTotalFinal <= 0L) {
                throw IOException("Gagal mengunggah payload uji ke Cloudflare Edge 100G.")
            }
            val finalUlMbps = round2(
                computeSteadyThroughputMbps(
                    window = ulWindowHistory,
                    startTimeMs = ulStartTimeMs,
                    warmupMs = min(1_000L, safeUlDurationMs / 3L),
                    totalBytes = ulTotalFinal,
                    totalElapsedNanos = ulElapsedNanos
                )
            )
            appendBoundedSample(ulSparkline, finalUlMbps.toFloat())

            val result = SpeedTestResult(
                pingMs = latestPingMs,
                jitterMs = latestJitterMs,
                downloadMbps = finalDlMbps,
                uploadMbps = finalUlMbps,
                clientIp = displayIp,
                ispName = displayIsp,
                serverUrl = SpeedTestTargetMode.PUBLIC_EDGE_100G.serverDisplayName,
                downloadSamples = dlSparkline.toList(),
                uploadSamples = ulSparkline.toList(),
                savedToServer = false,
                isSuccess = true
            )

            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.COMPLETED,
                    progressPercent = 100,
                    currentSpeedMbps = finalDlMbps,
                    pingMs = latestPingMs,
                    jitterMs = latestJitterMs,
                    downloadMbps = finalDlMbps,
                    uploadMbps = finalUlMbps,
                    downloadSamples = dlSparkline.toList(),
                    uploadSamples = ulSparkline.toList(),
                    clientIp = displayIp,
                    ispName = displayIsp,
                    statusMessage = "Pengujian Selesai (Edge 100G)"
                )
            )

            result
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            val friendly = when (e) {
                is UnknownHostException -> "Gagal menghubungi Cloudflare Edge (DNS gagal). Periksa koneksi internet perangkat."
                is SocketTimeoutException -> "Koneksi timeout ke Cloudflare Edge 100G."
                is ConnectException -> "Koneksi ke Cloudflare Edge ditolak atau terblokir jaringan."
                else -> e.localizedMessage ?: "Pengujian Internet Publik gagal."
            }
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    progressPercent = 0,
                    statusMessage = "Gagal: $friendly"
                )
            )
            SpeedTestResult(
                pingMs = 0.0,
                jitterMs = 0.0,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                clientIp = preDetectedClientInfo?.ip?.takeIf { it != "—" },
                ispName = preDetectedClientInfo?.ispWithLocation,
                serverUrl = SpeedTestTargetMode.PUBLIC_EDGE_100G.serverDisplayName,
                isSuccess = false,
                errorMessage = friendly
            )
        } finally {
            parentCancelHandle?.dispose()
            activeCalls.forEach { call -> runCatching { call.cancel() } }
            activeCalls.clear()
        }
    }

    private fun appendCacheBuster(baseUrl: String, seq: Int): String {
        val sep = if (baseUrl.contains("?")) "&" else "?"
        return "${baseUrl}${sep}r=${System.nanoTime()}_$seq"
    }

    private fun computeSlidingWindowMbps(
        window: ArrayDeque<Pair<Long, Long>>,
        nowMs: Long,
        elapsedMs: Long,
        totalBytes: Long
    ): Double {
        val cutoff = nowMs - 1_000L
        var firstInWindow: Pair<Long, Long>? = null
        var lastInWindow: Pair<Long, Long>? = null
        for (sample in window) {
            if (sample.first >= cutoff) {
                if (firstInWindow == null) firstInWindow = sample
                lastInWindow = sample
            }
        }
        if (firstInWindow != null && lastInWindow != null && lastInWindow.first > firstInWindow.first) {
            val dtSec = (lastInWindow.first - firstInWindow.first) / 1_000.0
            val dBytes = (lastInWindow.second - firstInWindow.second).coerceAtLeast(0L)
            if (dtSec >= 0.1) {
                return (dBytes * 8.0) / (dtSec * 1_000_000.0)
            }
        }
        val elapsedSec = (elapsedMs / 1_000.0).coerceAtLeast(0.05)
        return (totalBytes * 8.0) / (elapsedSec * 1_000_000.0)
    }

    private fun computeSteadyThroughputMbps(
        window: ArrayDeque<Pair<Long, Long>>,
        startTimeMs: Long,
        warmupMs: Long,
        totalBytes: Long,
        totalElapsedNanos: Long
    ): Double {
        val steadyCutoff = startTimeMs + warmupMs
        var firstSteady: Pair<Long, Long>? = null
        var lastSteady: Pair<Long, Long>? = null
        for (sample in window) {
            if (sample.first >= steadyCutoff) {
                if (firstSteady == null) firstSteady = sample
                lastSteady = sample
            }
        }
        if (firstSteady != null && lastSteady != null && lastSteady.first > firstSteady.first) {
            val dtSec = (lastSteady.first - firstSteady.first) / 1_000.0
            val dBytes = (lastSteady.second - firstSteady.second).coerceAtLeast(0L)
            if (dtSec >= 0.25 && dBytes > 0L) {
                return (dBytes * 8.0) / (dtSec * 1_000_000.0)
            }
        }
        val totalSec = (totalElapsedNanos / 1_000_000_000.0).coerceAtLeast(0.01)
        return (totalBytes * 8.0) / (totalSec * 1_000_000.0)
    }

    private fun appendBoundedSample(target: MutableList<Float>, value: Float, maxSize: Int = 36) {
        if (!value.isFinite()) return
        target.add(value.coerceAtLeast(0f))
        if (target.size > maxSize) target.removeAt(0)
    }

    private fun computeJitter(samples: List<Double>): Double {
        if (samples.size <= 1) return 0.0
        var sum = 0.0
        for (i in 1 until samples.size) {
            sum += abs(samples[i] - samples[i - 1])
        }
        return sum / (samples.size - 1)
    }

    private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0
    private fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0
}

/**
 * Handles Patch Ke-8 auto-tagging, parsing, saving (`POST /api/speedtest/records`),
 * and fetching (`GET /api/speedtest/records`) of Speedtest server history records.
 */
object SpeedTestRecordsClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val TARGET_TAG_REGEX = Regex("""\[Target:\s*([^\]]+)]""", RegexOption.IGNORE_CASE)
    private val ISP_TAG_REGEX = Regex("""\[ISP:\s*([^\]]+)]""", RegexOption.IGNORE_CASE)

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun buildTaggedNotes(
        mode: SpeedTestTargetMode,
        clientInfo: ClientNetworkInfo?,
        userNotes: String
    ): String {
        val parts = mutableListOf<String>()
        parts.add(mode.noteTag)
        val ispTagText = clientInfo?.ispWithLocation?.takeIf { it.isNotBlank() }
        if (ispTagText != null) {
            parts.add("[ISP: $ispTagText]")
        }
        val cleanNote = userNotes.trim()
        if (cleanNote.isNotEmpty()) {
            parts.add(cleanNote)
        }
        return parts.joinToString(" ")
    }

    fun parseTaggedNotes(rawNotes: String?): ParsedSpeedTestNotes {
        val text = rawNotes?.trim().orEmpty()
        if (text.isEmpty()) {
            return ParsedSpeedTestNotes(targetMode = null, ispLabel = null, userNote = "")
        }

        val targetValue = TARGET_TAG_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim()
        val parsedMode = when {
            targetValue == null -> null
            targetValue.contains("Publik", ignoreCase = true) ||
                targetValue.contains("100G", ignoreCase = true) ||
                targetValue.contains("Edge", ignoreCase = true) -> SpeedTestTargetMode.PUBLIC_EDGE_100G
            targetValue.contains("Lokal", ignoreCase = true) ||
                targetValue.contains("PingDulu", ignoreCase = true) ||
                targetValue.contains("LAN", ignoreCase = true) -> SpeedTestTargetMode.LOCAL_LAN_SERVER
            else -> null
        }

        val ispLabel = ISP_TAG_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }

        val cleanUserNote = text
            .replace(TARGET_TAG_REGEX, "")
            .replace(ISP_TAG_REGEX, "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        return ParsedSpeedTestNotes(
            targetMode = parsedMode,
            ispLabel = ispLabel,
            userNote = cleanUserNote
        )
    }

    fun parseRecordsJson(rawJson: String, maxItems: Int = 30): List<SpeedTestServerRecord> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()

        val array: JSONArray = try {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("records")
                    ?: obj.optJSONArray("data")
                    ?: obj.optJSONArray("items")
                    ?: return emptyList()
            }
        } catch (_: Exception) {
            return emptyList()
        }

        val result = mutableListOf<SpeedTestServerRecord>()
        val limit = min(array.length(), maxItems)
        for (i in 0 until limit) {
            val item = array.optJSONObject(i) ?: continue
            val rawNotes = item.optString("notes").takeIf { it.isNotBlank() && it != "null" }
            val parsedNotes = parseTaggedNotes(rawNotes)
            val ispFromColumn = item.optString("isp_name").takeIf { it.isNotBlank() && it != "null" }
            val ispBadge = parsedNotes.ispLabel ?: ispFromColumn
            val technicianName = item.optString("technician_name").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("display_name").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("username").takeIf { it.isNotBlank() && it != "null" }
            val createdAtRaw = item.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("timestamp").takeIf { it.isNotBlank() && it != "null" }
                ?: ""

            result.add(
                SpeedTestServerRecord(
                    id = item.optLong("id", i.toLong()),
                    pingMs = item.optDouble("ping_ms", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    jitterMs = item.optDouble("jitter_ms", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    downloadMbps = item.optDouble("download_mbps", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    uploadMbps = item.optDouble("upload_mbps", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    clientIp = item.optString("client_ip").takeIf { it.isNotBlank() && it != "null" },
                    ispName = ispFromColumn,
                    rawNotes = rawNotes,
                    technicianName = technicianName,
                    createdAtLabel = formatRecordTimestamp(createdAtRaw),
                    targetMode = parsedNotes.targetMode,
                    ispBadgeLabel = ispBadge,
                    cleanUserNote = parsedNotes.userNote
                )
            )
        }
        return result
    }

    suspend fun saveRecord(
        serverBaseUrl: String,
        authToken: String?,
        result: SpeedTestResult,
        clientInfo: ClientNetworkInfo?,
        mode: SpeedTestTargetMode,
        userNotes: String,
        client: OkHttpClient = defaultClient
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val base = FieldNetworkTools.normalizeHttpUrl(serverBaseUrl)
        if (base.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Alamat server PingDulu tidak valid."))
        }
        val taggedNotes = buildTaggedNotes(mode = mode, clientInfo = clientInfo, userNotes = userNotes)
        val payload = JSONObject().apply {
            put("ping_ms", (result.pingMs * 10.0).roundToInt() / 10.0)
            put("jitter_ms", (result.jitterMs * 10.0).roundToInt() / 10.0)
            put("download_mbps", (result.downloadMbps * 100.0).roundToInt() / 100.0)
            put("upload_mbps", (result.uploadMbps * 100.0).roundToInt() / 100.0)
            val ip = result.clientIp ?: clientInfo?.ip?.takeIf { it != "—" }
            if (!ip.isNullOrBlank()) put("client_ip", ip)
            val isp = result.ispName ?: clientInfo?.ispWithLocation
            if (!isp.isNullOrBlank()) put("isp_name", isp)
            put("notes", taggedNotes)
        }.toString()

        val reqBuilder = Request.Builder()
            .url("$base/api/speedtest/records")
            .header("User-Agent", "PingDulu-Android-FieldOps")
            .post(payload.toRequestBody(JSON_MEDIA))
        if (!authToken.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer ${authToken.trim()}")
        }
        val call = client.newCall(reqBuilder.build())
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) runCatching { call.cancel() }
        }
        try {
            call.execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else if (resp.code == 401 || resp.code == 403) {
                    Result.failure(IOException("Login diperlukan untuk menyimpan riwayat speedtest (HTTP ${resp.code})."))
                } else {
                    Result.failure(IOException("Gagal menyimpan riwayat speedtest (HTTP ${resp.code})."))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(IOException(e.localizedMessage ?: "Gagal terhubung ke server PingDulu."))
        } finally {
            cancelHandle?.dispose()
        }
    }

    suspend fun fetchRecords(
        serverBaseUrl: String,
        authToken: String?,
        client: OkHttpClient = defaultClient
    ): Result<List<SpeedTestServerRecord>> = withContext(Dispatchers.IO) {
        val base = FieldNetworkTools.normalizeHttpUrl(serverBaseUrl)
        if (base.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Alamat server PingDulu tidak valid."))
        }
        val reqBuilder = Request.Builder()
            .url("$base/api/speedtest/records")
            .header("User-Agent", "PingDulu-Android-FieldOps")
            .header("Accept", "application/json")
            .get()
        if (!authToken.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer ${authToken.trim()}")
        }
        val call = client.newCall(reqBuilder.build())
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) runCatching { call.cancel() }
        }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("Gagal memuat riwayat server (HTTP ${resp.code})."))
                }
                val raw = resp.body?.string().orEmpty()
                Result.success(parseRecordsJson(raw, maxItems = 30))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(IOException(e.localizedMessage ?: "Riwayat server tidak dapat dimuat."))
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun formatRecordTimestamp(raw: String): String {
        if (raw.isBlank()) return ""
        // Clean ISO-8601 / SQLite timestamps like "2026-11-22T14:30:12.000Z" or "2026-11-22 14:30:12"
        val normalized = raw.replace("T", " ").substringBefore(".").removeSuffix("Z").trim()
        return if (normalized.length >= 16) normalized.substring(5, 16) else normalized
    }
}
