package com.example.domain.tools

import com.example.domain.model.DateEpochUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Honest classification of a TCP connectivity probe.
 * Unlike InetAddress.isReachable() (which silently falls back to blocked TCP port 7 on Android),
 * every state below reflects the exact OS socket outcome.
 */
enum class ProbeStatus(val badgeLabel: String) {
    REACHABLE("TERJANGKAU"),
    PARTIAL_LOSS("TIDAK STABIL"),
    PORT_CLOSED("PORT TERTUTUP"),
    TIMEOUT("TIMEOUT"),
    DNS_ERROR("DNS GAGAL"),
    UNREACHABLE("TIDAK ADA RUTE"),
    INVALID_INPUT("INPUT TIDAK VALID")
}

data class ProbeAttempt(
    val sequence: Int,
    val status: ProbeStatus,
    val latencyMs: Double?,
    val errorDetail: String? = null
)

data class PingProbeResult(
    val host: String,
    val port: Int,
    val resolvedIp: String?,
    val status: ProbeStatus,
    val dnsLookupMs: Double?,
    val attempts: List<ProbeAttempt>,
    val detailMessage: String,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val reachable: Boolean
        get() = status == ProbeStatus.REACHABLE || status == ProbeStatus.PARTIAL_LOSS

    val totalAttempts: Int
        get() = attempts.size

    val successCount: Int
        get() = attempts.count { it.status == ProbeStatus.REACHABLE }

    val packetLossPercent: Int
        get() = if (totalAttempts == 0) 100 else (((totalAttempts - successCount) * 100) / totalAttempts)

    private val measuredLatencies: List<Double>
        get() = attempts.mapNotNull { it.latencyMs }

    val minLatencyMs: Double?
        get() = measuredLatencies.minOrNull()

    val avgLatencyMs: Double?
        get() = measuredLatencies.takeIf { it.isNotEmpty() }?.average()

    val maxLatencyMs: Double?
        get() = measuredLatencies.maxOrNull()

    val latencyMs: Long?
        get() = avgLatencyMs?.toLong()
}

data class SubnetCalculationResult(
    val cidrNotation: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val subnetMask: String,
    val wildcardMask: String,
    val firstUsableHost: String,
    val lastUsableHost: String,
    val usableHostCount: Long,
    val addressType: String
)

enum class SpeedTestPhase {
    IDLE,
    PING,
    JITTER,
    DOWNLOAD,
    UPLOAD,
    COMPLETED,
    ERROR
}

data class SpeedTestProgress(
    val phase: SpeedTestPhase,
    val progressPercent: Int,
    val currentSpeedMbps: Double = 0.0,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val downloadSamples: List<Float> = emptyList(),
    val uploadSamples: List<Float> = emptyList(),
    val clientIp: String? = null,
    val ispName: String? = null,
    val statusMessage: String = ""
)

data class SpeedTestResult(
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val clientIp: String?,
    val ispName: String? = null,
    val serverUrl: String = "",
    val downloadSamples: List<Float> = emptyList(),
    val uploadSamples: List<Float> = emptyList(),
    val savedToServer: Boolean = false,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Pure non-linear piecewise mapping for the CBN / Ookla 266-degree speedometer gauge.
 * - Canvas 0° = 3 o'clock; Start = 137° (bottom-left), Total Sweep = 266° (clockwise to bottom-right)
 * - 9 tick marks (`0, 10, 50, 100, 200, 300, 500, 750, 1g`) split the 266° arc into 8 equal 33.25° intervals
 * - Index 4 (`200 Mbps`) maps to sweep `133.0°` -> Canvas angle `137° + 133° = 270°` (exact 12 o'clock peak!)
 */
object CbnSpeedGaugeMath {
    const val START_ANGLE_DEGREES: Float = 137f
    const val TOTAL_SWEEP_DEGREES: Float = 266f
    const val PEAK_ANGLE_DEGREES: Float = 270f

    val TICK_VALUES_MBPS: List<Float> = listOf(
        0f, 10f, 50f, 100f, 200f, 300f, 500f, 750f, 1000f
    )
    val TICK_LABELS: List<String> = listOf(
        "0", "10", "50", "100", "200", "300", "500", "750", "1g"
    )

    fun speedToSweepAngle(mbps: Float): Float {
        if (!mbps.isFinite() || mbps <= 0f) return 0f
        if (mbps >= TICK_VALUES_MBPS.last()) return TOTAL_SWEEP_DEGREES

        val segmentSweep = TOTAL_SWEEP_DEGREES / (TICK_VALUES_MBPS.size - 1)
        for (i in 0 until TICK_VALUES_MBPS.lastIndex) {
            val low = TICK_VALUES_MBPS[i]
            val high = TICK_VALUES_MBPS[i + 1]
            if (mbps <= high) {
                val fraction = ((mbps - low) / (high - low)).coerceIn(0f, 1f)
                return (i * segmentSweep) + (fraction * segmentSweep)
            }
        }
        return TOTAL_SWEEP_DEGREES
    }

    fun speedToCanvasAngle(mbps: Float): Float = START_ANGLE_DEGREES + speedToSweepAngle(mbps)

    fun tickCanvasAngle(index: Int): Float {
        val safeIdx = index.coerceIn(0, TICK_VALUES_MBPS.lastIndex)
        val segmentSweep = TOTAL_SWEEP_DEGREES / (TICK_VALUES_MBPS.size - 1)
        return START_ANGLE_DEGREES + (safeIdx * segmentSweep)
    }
}

/**
 * Internal transport abstraction so unit tests can verify DNS isolation, timeout,
 * and partial-loss math deterministically while production always uses real OS sockets.
 */
internal interface NetworkProbeTransport {
    fun resolveHost(host: String): InetAddress
    suspend fun connectTcp(address: InetAddress, port: Int, timeoutMs: Int): Double
}

internal object SystemSocketProbeTransport : NetworkProbeTransport {
    override fun resolveHost(host: String): InetAddress {
        val all = InetAddress.getAllByName(host)
        // Prefer IPv4 when both A and AAAA exist to avoid false failures on IPv4-only LANs/emulators
        return all.firstOrNull { it is Inet4Address } ?: all.first()
    }

    override suspend fun connectTcp(address: InetAddress, port: Int, timeoutMs: Int): Double {
        return suspendCancellableCoroutine { continuation ->
            val socket = Socket()
            continuation.invokeOnCancellation {
                runCatching { socket.close() }
            }
            val startNanos = System.nanoTime()
            try {
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                val rttMs = (System.nanoTime() - startNanos) / 1_000_000.0
                runCatching { socket.close() }
                if (continuation.isActive) {
                    continuation.resume(rttMs)
                }
            } catch (e: Exception) {
                runCatching { socket.close() }
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }
}

object FieldNetworkTools {

    private val IPV4_REGEX = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
    private val HOSTNAME_ALLOWED_REGEX = Regex("^[A-Za-z0-9._-]+$")

    fun formatLatencyMs(ms: Double?): String {
        if (ms == null || ms.isNaN() || ms.isInfinite()) return "--"
        return when {
            ms < 1.0 -> "<1 ms"
            ms < 10.0 -> String.format(Locale.US, "%.1f ms", ms)
            else -> "${ms.toLong()} ms"
        }
    }

    suspend fun probeHost(
        rawHost: String,
        port: Int = 443,
        attempts: Int = 3,
        timeoutMs: Int = 2_000,
        intervalMs: Long = 150L
    ): PingProbeResult = probeHostInternal(
        rawHost = rawHost,
        port = port,
        attempts = attempts,
        timeoutMs = timeoutMs,
        intervalMs = intervalMs,
        transport = SystemSocketProbeTransport
    )

    internal suspend fun probeHostInternal(
        rawHost: String,
        port: Int = 443,
        attempts: Int = 3,
        timeoutMs: Int = 2_000,
        intervalMs: Long = 150L,
        transport: NetworkProbeTransport
    ): PingProbeResult = withContext(Dispatchers.IO) {
        val trimmed = rawHost
            .trim()
            .replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .substringBefore("/")

        // Extract inline :port only if numeric, otherwise strip suffix safely
        val hostPart = trimmed.substringBefore(":").trim()
        val inlinePort = trimmed.substringAfter(":", "").toIntOrNull()?.takeIf { it in 1..65535 }
        val safePort = (inlinePort ?: port).coerceIn(1, 65535)
        val safeAttempts = attempts.coerceIn(1, 5)
        val safeTimeout = timeoutMs.coerceIn(300, 10_000)

        val looksNumericOnly = hostPart.isNotEmpty() &&
            hostPart.split(".").all { it.isNotEmpty() && it.all(Char::isDigit) }
        val isIpLiteral = isValidIpv4Literal(hostPart)

        if (hostPart.isBlank() ||
            !HOSTNAME_ALLOWED_REGEX.matches(hostPart) ||
            (looksNumericOnly && !isIpLiteral)
        ) {
            return@withContext PingProbeResult(
                host = rawHost.trim(),
                port = safePort,
                resolvedIp = null,
                status = ProbeStatus.INVALID_INPUT,
                dnsLookupMs = null,
                attempts = emptyList(),
                detailMessage = "Format host atau alamat IP tidak valid."
            )
        }

        // Step 1: Resolve DNS separately so DNS lookup time NEVER contaminates TCP RTT
        val dnsStartNanos = System.nanoTime()
        val resolvedAddress = try {
            transport.resolveHost(hostPart)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            return@withContext PingProbeResult(
                host = hostPart,
                port = safePort,
                resolvedIp = null,
                status = ProbeStatus.DNS_ERROR,
                dnsLookupMs = null,
                attempts = emptyList(),
                detailMessage = "DNS gagal menyelesaikan '$hostPart' (offline atau domain tidak ditemukan)."
            )
        } catch (e: Exception) {
            return@withContext PingProbeResult(
                host = hostPart,
                port = safePort,
                resolvedIp = null,
                status = ProbeStatus.DNS_ERROR,
                dnsLookupMs = null,
                attempts = emptyList(),
                detailMessage = e.localizedMessage ?: "Gagal menyelesaikan host '$hostPart'."
            )
        }

        val dnsMs = if (isIpLiteral) {
            null
        } else {
            (System.nanoTime() - dnsStartNanos) / 1_000_000.0
        }
        val resolvedIp = resolvedAddress.hostAddress

        // Step 2: Execute N honest TCP 3-way handshake probes directly to the resolved IP
        val results = mutableListOf<ProbeAttempt>()
        for (seq in 1..safeAttempts) {
            currentCoroutineContext().ensureActive()
            val attemptStartNanos = System.nanoTime()
            val attempt = try {
                val rttMs = transport.connectTcp(resolvedAddress, safePort, safeTimeout)
                ProbeAttempt(
                    sequence = seq,
                    status = ProbeStatus.REACHABLE,
                    latencyMs = rttMs
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                ProbeAttempt(
                    sequence = seq,
                    status = ProbeStatus.TIMEOUT,
                    latencyMs = null,
                    errorDetail = "Timeout > ${safeTimeout} ms"
                )
            } catch (e: ConnectException) {
                // ECONNREFUSED means remote host IS alive on the network and returned TCP RST,
                // but no service is listening on this port. Never mislabel this as OFFLINE/TIMEOUT!
                val rstMs = (System.nanoTime() - attemptStartNanos) / 1_000_000.0
                val msg = e.message.orEmpty()
                val isUnreachable = msg.contains("ENETUNREACH", ignoreCase = true) ||
                    msg.contains("EHOSTUNREACH", ignoreCase = true) ||
                    msg.contains("unreachable", ignoreCase = true)
                if (isUnreachable) {
                    ProbeAttempt(
                        sequence = seq,
                        status = ProbeStatus.UNREACHABLE,
                        latencyMs = null,
                        errorDetail = "Tidak ada rute jaringan"
                    )
                } else {
                    ProbeAttempt(
                        sequence = seq,
                        status = ProbeStatus.PORT_CLOSED,
                        latencyMs = rstMs,
                        errorDetail = "TCP RST (port tertutup)"
                    )
                }
            } catch (e: NoRouteToHostException) {
                ProbeAttempt(
                    sequence = seq,
                    status = ProbeStatus.UNREACHABLE,
                    latencyMs = null,
                    errorDetail = "Tidak ada rute ke host"
                )
            } catch (e: SocketException) {
                ProbeAttempt(
                    sequence = seq,
                    status = ProbeStatus.UNREACHABLE,
                    latencyMs = null,
                    errorDetail = e.localizedMessage ?: "Socket error"
                )
            } catch (e: Exception) {
                ProbeAttempt(
                    sequence = seq,
                    status = ProbeStatus.UNREACHABLE,
                    latencyMs = null,
                    errorDetail = e.localizedMessage ?: "Gagal terhubung"
                )
            }

            results.add(attempt)

            // Fail-fast if network has no route at all (no reason to wait 3x)
            if (attempt.status == ProbeStatus.UNREACHABLE) {
                break
            }
            if (seq < safeAttempts && intervalMs > 0L) {
                delay(intervalMs)
            }
        }

        val okCount = results.count { it.status == ProbeStatus.REACHABLE }
        val closedCount = results.count { it.status == ProbeStatus.PORT_CLOSED }
        val unreachableCount = results.count { it.status == ProbeStatus.UNREACHABLE }

        val overallStatus = when {
            okCount == results.size && results.isNotEmpty() -> ProbeStatus.REACHABLE
            okCount > 0 -> ProbeStatus.PARTIAL_LOSS
            closedCount > 0 -> ProbeStatus.PORT_CLOSED
            unreachableCount > 0 -> ProbeStatus.UNREACHABLE
            else -> ProbeStatus.TIMEOUT
        }

        val avgMeasured = results.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()
        val detail = when (overallStatus) {
            ProbeStatus.REACHABLE -> {
                "TCP handshake ke port $safePort sukses $okCount/${results.size} (rata-rata ${formatLatencyMs(avgMeasured)})."
            }
            ProbeStatus.PARTIAL_LOSS -> {
                val loss = (((results.size - okCount) * 100) / results.size)
                "Koneksi tidak stabil: $okCount/${results.size} sukses (gagal $loss%) di port $safePort."
            }
            ProbeStatus.PORT_CLOSED -> {
                "Host hidup & membalas TCP RST (${formatLatencyMs(avgMeasured)}), tetapi port $safePort tertutup/tidak melayani."
            }
            ProbeStatus.UNREACHABLE -> {
                "Tidak ada rute jaringan menuju ${resolvedIp ?: hostPart} (periksa Wi-Fi/VPN/gateway)."
            }
            ProbeStatus.TIMEOUT -> {
                "Tidak ada balasan dari port $safePort setelah ${results.size}× uji (host offline atau paket diblokir firewall)."
            }
            else -> "Pemeriksaan selesai."
        }

        PingProbeResult(
            host = hostPart,
            port = safePort,
            resolvedIp = resolvedIp,
            status = overallStatus,
            dnsLookupMs = dnsMs,
            attempts = results,
            detailMessage = detail
        )
    }

    private fun isValidIpv4Literal(input: String): Boolean {
        if (!IPV4_REGEX.matches(input)) return false
        return input.split(".").all { octet ->
            val v = octet.toIntOrNull()
            v != null && v in 0..255
        }
    }

    fun calculateSubnet(ipInput: String, prefixLength: Int): SubnetCalculationResult? {
        if (prefixLength !in 0..32) return null
        val octets = ipInput.trim().split(".")
        if (octets.size != 4) return null

        var ipLong = 0L
        for (octetStr in octets) {
            val octet = octetStr.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            ipLong = (ipLong shl 8) or octet.toLong()
        }

        val maskLong: Long = if (prefixLength == 0) {
            0L
        } else {
            (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        }
        val wildcardLong = maskLong.inv() and 0xFFFFFFFFL
        val networkLong = ipLong and maskLong
        val broadcastLong = networkLong or wildcardLong

        val (firstUsable, lastUsable, usableCount) = when (prefixLength) {
            32 -> Triple(networkLong, networkLong, 1L)
            31 -> Triple(networkLong, broadcastLong, 2L)
            else -> {
                val totalAddresses = 1L shl (32 - prefixLength)
                val usable = (totalAddresses - 2L).coerceAtLeast(0L)
                Triple(networkLong + 1L, broadcastLong - 1L, usable)
            }
        }

        val firstOctet = ((networkLong ushr 24) and 0xFFL).toInt()
        val secondOctet = ((networkLong ushr 16) and 0xFFL).toInt()
        val addressType = when {
            firstOctet == 10 -> "Private LAN (RFC 1918 · 10.0.0.0/8)"
            firstOctet == 172 && secondOctet in 16..31 -> "Private LAN (RFC 1918 · 172.16.0.0/12)"
            firstOctet == 192 && secondOctet == 168 -> "Private LAN (RFC 1918 · 192.168.0.0/16)"
            firstOctet == 127 -> "Loopback (127.0.0.0/8)"
            firstOctet == 169 && secondOctet == 254 -> "Link-Local APIPA (169.254.0.0/16)"
            firstOctet in 224..239 -> "Multicast"
            else -> "Public / Routable IPv4"
        }

        return SubnetCalculationResult(
            cidrNotation = "${longToIpv4(networkLong)}/$prefixLength",
            networkAddress = longToIpv4(networkLong),
            broadcastAddress = longToIpv4(broadcastLong),
            subnetMask = longToIpv4(maskLong),
            wildcardMask = longToIpv4(wildcardLong),
            firstUsableHost = longToIpv4(firstUsable),
            lastUsableHost = longToIpv4(lastUsable),
            usableHostCount = usableCount,
            addressType = addressType
        )
    }

    private fun longToIpv4(value: Long): String {
        val o1 = (value ushr 24) and 0xFFL
        val o2 = (value ushr 16) and 0xFFL
        val o3 = (value ushr 8) and 0xFFL
        val o4 = value and 0xFFL
        return "$o1.$o2.$o3.$o4"
    }

    /**
     * Normalizes user-entered speedtest base URL.
     * - Production domain (`ping.fjrx.my.id`) defaults to `https://` when scheme is omitted.
     * - Local LAN IPs / custom hosts default to `http://` when scheme is omitted.
     */
    fun normalizeHttpUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val withProtocol = if (
            !trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val hostOnly = trimmed.substringBefore("/").substringBefore(":").lowercase(Locale.US)
            if (hostOnly == "ping.fjrx.my.id" || hostOnly.endsWith(".ping.fjrx.my.id")) {
                "https://$trimmed"
            } else {
                "http://$trimmed"
            }
        } else {
            trimmed
        }
        return withProtocol.trimEnd('/')
    }

    /**
     * Formats diagnostic speed test summary in WIB (Asia/Jakarta) with Locale.US decimal dots
     * ready to paste into field ops ticket comments.
     */
    fun formatSpeedTestSummary(result: SpeedTestResult): String {
        val wibFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss 'WIB'", Locale.forLanguageTag("id-ID")).apply {
            timeZone = DateEpochUtil.JAKARTA_TIME_ZONE
        }
        val formattedTime = wibFormat.format(Date(result.timestampMillis))
        val pingStr = String.format(Locale.US, "%.1f", result.pingMs)
        val jitterStr = String.format(Locale.US, "%.1f", result.jitterMs)
        val dlStr = String.format(Locale.US, "%.2f", result.downloadMbps)
        val ulStr = String.format(Locale.US, "%.2f", result.uploadMbps)
        val serverLine = result.serverUrl.ifBlank { "-" }
        return """
            === HASIL DIAGNOSTIK SPEEDTEST PINGDULU ===
            Server: $serverLine
            IP Klien: ${result.clientIp ?: "N/A"}
            Ping: $pingStr ms | Jitter: $jitterStr ms
            Download: $dlStr Mbps
            Upload: $ulStr Mbps
            Waktu: $formattedTime
        """.trimIndent()
    }

    /**
     * Runs a self-hosted LibreSpeed diagnostic test against a PingDulu Fastify server:
     * 1) GET  /api/speedtest/ip
     * 2) GET  /api/speedtest/empty (multi-sample Ping & Jitter)
     * 3) GET  /api/speedtest/garbage?ckSize=15 (Download throughput)
     * 4) POST /api/speedtest/empty (Bounded Upload throughput verified via HTTP 2xx)
     *
     * Attaches `Authorization: Bearer <authToken>` when provided.
     * Strictly validates HTTP 2xx status codes and supports cooperative cancellation.
     */
    suspend fun runSpeedTest(
        serverBaseUrl: String,
        authToken: String? = null,
        pingSamples: Int = 6,
        downloadDurationMs: Long = 5_000L,
        uploadDurationMs: Long = 4_500L,
        uploadChunkBytes: Int = 512 * 1024,
        maxUploadTotalBytes: Long = 32L * 1024L * 1024L,
        onProgress: (suspend (SpeedTestProgress) -> Unit)? = null
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        val normalizedBaseUrl = normalizeHttpUrl(serverBaseUrl)
        if (normalizedBaseUrl.isBlank()) {
            val err = SpeedTestResult(
                pingMs = 0.0,
                jitterMs = 0.0,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                clientIp = null,
                serverUrl = "",
                isSuccess = false,
                errorMessage = "Alamat server speedtest tidak boleh kosong."
            )
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    progressPercent = 0,
                    statusMessage = err.errorMessage.orEmpty()
                )
            )
            return@withContext err
        }

        val safePingSamples = pingSamples.coerceIn(2, 10)
        val safeDlDurationNanos = downloadDurationMs.coerceIn(300L, 15_000L) * 1_000_000L
        val safeUlDurationNanos = uploadDurationMs.coerceIn(300L, 15_000L) * 1_000_000L
        val safeUploadChunkBytes = uploadChunkBytes.coerceIn(16 * 1024, 1024 * 1024)
        val safeMaxUploadBytes = maxUploadTotalBytes.coerceAtLeast(safeUploadChunkBytes.toLong())
        val bearerToken = authToken?.trim()?.takeIf { it.isNotEmpty() }

        try {
            currentCoroutineContext().ensureActive()

            // 1. IP & ISP Detection (also validates auth early if server requires Bearer token)
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.PING,
                    progressPercent = 5,
                    statusMessage = "Mendeteksi IP klien & memverifikasi sesi..."
                )
            )

            var clientIp: String? = null
            var ispName: String? = null
            run {
                val ipConn = openSpeedTestConnection(
                    urlString = "$normalizedBaseUrl/api/speedtest/ip",
                    method = "GET",
                    bearerToken = bearerToken,
                    connectTimeoutMs = 4_000,
                    readTimeoutMs = 4_000
                )
                val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                    if (cause != null) runCatching { ipConn.disconnect() }
                }
                try {
                    val code = ipConn.responseCode
                    if (code == 401 || code == 403) {
                        throw SpeedTestHttpException(code, formatHttpErrorMessage(code, "/api/speedtest/ip"))
                    }
                    if (code in 200..299) {
                        val body = ipConn.inputStream.bufferedReader().use { it.readText() }
                        val processed = Regex("\"processedString\"\\s*:\\s*\"([^\"]+)\"").find(body)
                            ?.groupValues?.get(1)?.trim()
                        val rawIp = Regex("\"rawIp\"\\s*:\\s*\"([^\"]+)\"").find(body)
                            ?.groupValues?.get(1)?.trim()
                        val rawIsp = Regex("\"isp(?:_name|Name)?\"\\s*:\\s*\"([^\"]+)\"").find(body)
                            ?.groupValues?.get(1)?.trim()
                        ispName = rawIsp?.takeIf { it.isNotEmpty() }
                            ?: processed?.takeIf { it.contains(" - ") }?.substringAfter(" - ")?.trim()
                                ?.takeIf { it.isNotEmpty() }
                        clientIp = if (ispName != null) {
                            rawIp?.takeIf { it.isNotEmpty() }
                                ?: processed?.substringBefore(" - ")?.trim()?.takeIf { it.isNotEmpty() }
                        } else {
                            processed?.takeIf { it.isNotEmpty() }
                                ?: rawIp?.takeIf { it.isNotEmpty() }
                        }
                    }
                } finally {
                    cancelHandle?.dispose()
                    runCatching { ipConn.disconnect() }
                }
            }

            // 2. Ping & Jitter Measurement
            val pingsMs = mutableListOf<Double>()
            val pingUrlBase = "$normalizedBaseUrl/api/speedtest/empty"

            for (i in 1..safePingSamples) {
                currentCoroutineContext().ensureActive()
                val conn = openSpeedTestConnection(
                    urlString = "$pingUrlBase?r=${System.currentTimeMillis()}_$i",
                    method = "GET",
                    bearerToken = bearerToken,
                    connectTimeoutMs = 4_000,
                    readTimeoutMs = 4_000
                )
                val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                    if (cause != null) runCatching { conn.disconnect() }
                }
                val sampleStart = System.nanoTime()
                try {
                    val code = conn.responseCode
                    validateSuccessHttpCode(code, "/api/speedtest/empty")
                    conn.inputStream.use { stream ->
                        val drain = ByteArray(256)
                        while (stream.read(drain) != -1) {
                            currentCoroutineContext().ensureActive()
                        }
                    }
                } finally {
                    cancelHandle?.dispose()
                    runCatching { conn.disconnect() }
                }

                val elapsedMs = ((System.nanoTime() - sampleStart) / 1_000_000.0).coerceAtLeast(0.1)
                pingsMs.add(elapsedMs)

                val runningMinPing = round1(pingsMs.minOrNull() ?: elapsedMs)
                val runningJitter = round1(computeJitter(pingsMs))
                val isJitterSubPhase = i > (safePingSamples / 2)
                val progress = 8 + (i * 18 / safePingSamples)
                onProgress?.invoke(
                    SpeedTestProgress(
                        phase = if (isJitterSubPhase) SpeedTestPhase.JITTER else SpeedTestPhase.PING,
                        progressPercent = progress,
                        pingMs = runningMinPing,
                        jitterMs = if (pingsMs.size > 1) runningJitter else null,
                        clientIp = clientIp,
                        ispName = ispName,
                        statusMessage = if (isJitterSubPhase) {
                            String.format(Locale.US, "Mengukur Jitter (%d/%d): %.1f ms", i, safePingSamples, runningJitter)
                        } else {
                            String.format(Locale.US, "Mengukur Ping (%d/%d): %.1f ms", i, safePingSamples, elapsedMs)
                        }
                    )
                )
                if (i < safePingSamples) {
                    delay(30)
                }
            }

            val finalPingMs = round1(pingsMs.minOrNull() ?: 0.1)
            val finalJitterMs = round1(computeJitter(pingsMs))

            // 3. Download Speed Measurement (bounded quota ckSize=15 + sliding window for smooth sparkline)
            currentCoroutineContext().ensureActive()
            val downloadSamples = mutableListOf<Float>()
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.DOWNLOAD,
                    progressPercent = 28,
                    pingMs = finalPingMs,
                    jitterMs = finalJitterMs,
                    clientIp = clientIp,
                    ispName = ispName,
                    statusMessage = "Mengukur Unduh..."
                )
            )

            var totalBytesDownloaded = 0L
            var dlMeasuredNanos = 0L
            val dlConn = openSpeedTestConnection(
                urlString = "$normalizedBaseUrl/api/speedtest/garbage?ckSize=15&r=${System.currentTimeMillis()}",
                method = "GET",
                bearerToken = bearerToken,
                connectTimeoutMs = 5_000,
                readTimeoutMs = 8_000
            )
            val dlCancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                if (cause != null) runCatching { dlConn.disconnect() }
            }
            try {
                val dlCode = dlConn.responseCode
                validateSuccessHttpCode(dlCode, "/api/speedtest/garbage")

                val dlBuffer = ByteArray(64 * 1024)
                val dlStartTime = System.nanoTime()
                var lastReportNanos = dlStartTime
                val windowMarks = ArrayDeque<Pair<Long, Long>>()
                windowMarks.addLast(dlStartTime to 0L)
                val slidingWindowNanos = 1_500_000_000L

                dlConn.inputStream.use { stream ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = stream.read(dlBuffer)
                        if (read == -1) break
                        totalBytesDownloaded += read
                        val now = System.nanoTime()
                        val elapsedTotal = now - dlStartTime
                        if (elapsedTotal >= safeDlDurationNanos) {
                            dlMeasuredNanos = elapsedTotal
                            break
                        }

                        if (now - lastReportNanos >= 120_000_000L) {
                            lastReportNanos = now
                            windowMarks.addLast(now to totalBytesDownloaded)
                            while (windowMarks.size > 2 && now - windowMarks.first().first > slidingWindowNanos) {
                                windowMarks.removeFirst()
                            }
                            val anchor = windowMarks.first()
                            val winNanos = (now - anchor.first).coerceAtLeast(1_000_000L)
                            val winBytes = (totalBytesDownloaded - anchor.second).coerceAtLeast(0L)
                            val currentSpeed = if (winBytes > 0L) {
                                bytesToMbps(winBytes, winNanos)
                            } else {
                                bytesToMbps(totalBytesDownloaded, elapsedTotal)
                            }
                            appendBoundedSample(downloadSamples, currentSpeed.toFloat())
                            val percent = 28 + ((elapsedTotal.toDouble() / safeDlDurationNanos) * 35).toInt().coerceIn(0, 35)
                            onProgress?.invoke(
                                SpeedTestProgress(
                                    phase = SpeedTestPhase.DOWNLOAD,
                                    progressPercent = percent,
                                    currentSpeedMbps = currentSpeed,
                                    pingMs = finalPingMs,
                                    jitterMs = finalJitterMs,
                                    downloadMbps = round2(currentSpeed),
                                    downloadSamples = downloadSamples.toList(),
                                    clientIp = clientIp,
                                    ispName = ispName,
                                    statusMessage = String.format(Locale.US, "Mengukur Unduh: %.2f Mbps", currentSpeed)
                                )
                            )
                        }
                    }
                }
                if (dlMeasuredNanos == 0L) {
                    dlMeasuredNanos = (System.nanoTime() - dlStartTime).coerceAtLeast(1_000_000L)
                }
            } finally {
                dlCancelHandle?.dispose()
                runCatching { dlConn.disconnect() }
            }

            if (totalBytesDownloaded <= 0L) {
                throw IOException("Server mengembalikan 0 byte pada pengujian unduh (/api/speedtest/garbage).")
            }
            val finalDlMbps = round2(bytesToMbps(totalBytesDownloaded, dlMeasuredNanos))
            if (downloadSamples.isEmpty()) {
                downloadSamples.add(finalDlMbps.toFloat())
            } else {
                appendBoundedSample(downloadSamples, finalDlMbps.toFloat())
            }

            // 4. Upload Speed Measurement (sequential bounded POSTs verified via HTTP 2xx)
            currentCoroutineContext().ensureActive()
            val uploadSamples = mutableListOf<Float>()
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.UPLOAD,
                    progressPercent = 65,
                    pingMs = finalPingMs,
                    jitterMs = finalJitterMs,
                    downloadMbps = finalDlMbps,
                    downloadSamples = downloadSamples.toList(),
                    clientIp = clientIp,
                    ispName = ispName,
                    statusMessage = "Mengukur Unggah..."
                )
            )

            val ulPayload = ByteArray(safeUploadChunkBytes) { idx -> ((idx * 31) and 0x7F).toByte() }
            var totalBytesUploaded = 0L
            var totalUploadTransferNanos = 0L
            val ulWindowStart = System.nanoTime()
            var lastUlReportNanos = ulWindowStart
            var uploadChunkIndex = 0

            while (true) {
                currentCoroutineContext().ensureActive()
                val windowElapsed = System.nanoTime() - ulWindowStart
                if (uploadChunkIndex > 0 && (windowElapsed >= safeUlDurationNanos || totalBytesUploaded >= safeMaxUploadBytes)) {
                    break
                }
                uploadChunkIndex++

                val ulConn = openSpeedTestConnection(
                    urlString = "$normalizedBaseUrl/api/speedtest/empty?r=${System.currentTimeMillis()}_u$uploadChunkIndex",
                    method = "POST",
                    bearerToken = bearerToken,
                    connectTimeoutMs = 5_000,
                    readTimeoutMs = 10_000
                ).apply {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setFixedLengthStreamingMode(ulPayload.size)
                }
                val ulCancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                    if (cause != null) runCatching { ulConn.disconnect() }
                }

                val chunkStartNanos = System.nanoTime()
                try {
                    ulConn.outputStream.use { out ->
                        var offset = 0
                        val sliceSize = 32 * 1024
                        while (offset < ulPayload.size) {
                            currentCoroutineContext().ensureActive()
                            val count = minOf(sliceSize, ulPayload.size - offset)
                            out.write(ulPayload, offset, count)
                            offset += count
                        }
                        out.flush()
                    }
                    // Verify server actually received & accepted the POST before counting throughput
                    val ulCode = ulConn.responseCode
                    validateSuccessHttpCode(ulCode, "POST /api/speedtest/empty")
                    runCatching {
                        ulConn.inputStream.use { stream ->
                            val drain = ByteArray(256)
                            while (stream.read(drain) != -1) {
                                currentCoroutineContext().ensureActive()
                            }
                        }
                    }
                } finally {
                    ulCancelHandle?.dispose()
                    runCatching { ulConn.disconnect() }
                }

                val chunkNanos = (System.nanoTime() - chunkStartNanos).coerceAtLeast(1_000_000L)
                totalBytesUploaded += ulPayload.size
                totalUploadTransferNanos += chunkNanos

                val now = System.nanoTime()
                val totalWindowElapsed = now - ulWindowStart
                val instantChunkMbps = bytesToMbps(ulPayload.size.toLong(), chunkNanos)
                val smoothedUlMbps = bytesToMbps(totalBytesUploaded, totalUploadTransferNanos)
                if (now - lastUlReportNanos >= 120_000_000L || uploadChunkIndex == 1) {
                    lastUlReportNanos = now
                    appendBoundedSample(uploadSamples, ((smoothedUlMbps * 0.65) + (instantChunkMbps * 0.35)).toFloat())
                    val percent = 65 + ((totalWindowElapsed.toDouble() / safeUlDurationNanos) * 33).toInt().coerceIn(0, 33)
                    onProgress?.invoke(
                        SpeedTestProgress(
                            phase = SpeedTestPhase.UPLOAD,
                            progressPercent = percent,
                            currentSpeedMbps = smoothedUlMbps,
                            pingMs = finalPingMs,
                            jitterMs = finalJitterMs,
                            downloadMbps = finalDlMbps,
                            uploadMbps = round2(smoothedUlMbps),
                            downloadSamples = downloadSamples.toList(),
                            uploadSamples = uploadSamples.toList(),
                            clientIp = clientIp,
                            ispName = ispName,
                            statusMessage = String.format(Locale.US, "Mengukur Unggah: %.2f Mbps", smoothedUlMbps)
                        )
                    )
                }

                if (totalWindowElapsed >= safeUlDurationNanos || totalBytesUploaded >= safeMaxUploadBytes) {
                    break
                }
            }

            if (totalBytesUploaded <= 0L) {
                throw IOException("Gagal mengunggah payload uji ke server speedtest.")
            }
            val finalUlMbps = round2(bytesToMbps(totalBytesUploaded, totalUploadTransferNanos))
            if (uploadSamples.isEmpty()) {
                uploadSamples.add(finalUlMbps.toFloat())
            } else {
                appendBoundedSample(uploadSamples, finalUlMbps.toFloat())
            }

            // 5. Best-effort persistence to POST /api/speedtest/records (never fails completed test on LAN/offline)
            val savedToServer = postSpeedTestRecordBestEffort(
                normalizedBaseUrl = normalizedBaseUrl,
                bearerToken = bearerToken,
                pingMs = finalPingMs,
                jitterMs = finalJitterMs,
                downloadMbps = finalDlMbps,
                uploadMbps = finalUlMbps,
                clientIp = clientIp,
                ispName = ispName,
                notes = "Android Field Ops CBN Speed Test"
            )

            val finalResult = SpeedTestResult(
                pingMs = finalPingMs,
                jitterMs = finalJitterMs,
                downloadMbps = finalDlMbps,
                uploadMbps = finalUlMbps,
                clientIp = clientIp,
                ispName = ispName,
                serverUrl = normalizedBaseUrl,
                downloadSamples = downloadSamples.toList(),
                uploadSamples = uploadSamples.toList(),
                savedToServer = savedToServer,
                isSuccess = true
            )

            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.COMPLETED,
                    progressPercent = 100,
                    currentSpeedMbps = finalDlMbps,
                    pingMs = finalPingMs,
                    jitterMs = finalJitterMs,
                    downloadMbps = finalDlMbps,
                    uploadMbps = finalUlMbps,
                    downloadSamples = downloadSamples.toList(),
                    uploadSamples = uploadSamples.toList(),
                    clientIp = clientIp,
                    ispName = ispName,
                    statusMessage = "Pengujian Selesai"
                )
            )

            finalResult
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Check if the coroutine was actually cancelled while blocking on socket I/O
            currentCoroutineContext().ensureActive()
            val friendlyMessage = when (e) {
                is SpeedTestHttpException -> e.message ?: "HTTP ${e.statusCode}"
                is UnknownHostException -> "Host tidak ditemukan (DNS gagal). Periksa alamat server atau koneksi."
                is SocketTimeoutException -> "Koneksi timeout ke server speedtest."
                is ConnectException -> "Server menolak koneksi (port tidak aktif atau firewall memblokir)."
                else -> e.localizedMessage ?: "Koneksi ke server speedtest gagal/terputus."
            }
            val errResult = SpeedTestResult(
                pingMs = 0.0,
                jitterMs = 0.0,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                clientIp = null,
                serverUrl = normalizedBaseUrl,
                isSuccess = false,
                errorMessage = friendlyMessage
            )
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    progressPercent = 0,
                    statusMessage = "Gagal: $friendlyMessage"
                )
            )
            errResult
        }
    }

    /**
     * Best-effort upload of completed speedtest metrics to `POST /api/speedtest/records`.
     * Never throws or invalidates a successful diagnostic measurement if server is LAN-only/offline.
     */
    internal suspend fun postSpeedTestRecordBestEffort(
        normalizedBaseUrl: String,
        bearerToken: String?,
        pingMs: Double,
        jitterMs: Double,
        downloadMbps: Double,
        uploadMbps: Double,
        clientIp: String?,
        ispName: String?,
        notes: String?
    ): Boolean {
        return try {
            currentCoroutineContext().ensureActive()
            val conn = openSpeedTestConnection(
                urlString = "$normalizedBaseUrl/api/speedtest/records",
                method = "POST",
                bearerToken = bearerToken,
                connectTimeoutMs = 3_500,
                readTimeoutMs = 4_000
            ).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                if (cause != null) runCatching { conn.disconnect() }
            }
            try {
                val jsonPayload = buildString {
                    append("{")
                    append("\"ping_ms\":").append(String.format(Locale.US, "%.1f", pingMs)).append(",")
                    append("\"jitter_ms\":").append(String.format(Locale.US, "%.1f", jitterMs)).append(",")
                    append("\"download_mbps\":").append(String.format(Locale.US, "%.2f", downloadMbps)).append(",")
                    append("\"upload_mbps\":").append(String.format(Locale.US, "%.2f", uploadMbps)).append(",")
                    append("\"client_ip\":").append(jsonNullableString(clientIp)).append(",")
                    append("\"isp_name\":").append(jsonNullableString(ispName)).append(",")
                    append("\"notes\":").append(jsonNullableString(notes))
                    append("}")
                }.toByteArray(Charsets.UTF_8)

                conn.setFixedLengthStreamingMode(jsonPayload.size)
                conn.outputStream.use { it.write(jsonPayload) }
                val code = conn.responseCode
                runCatching { conn.inputStream?.close() }
                code in 200..299
            } finally {
                cancelHandle?.dispose()
                runCatching { conn.disconnect() }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

    private fun appendBoundedSample(target: MutableList<Float>, value: Float, maxSize: Int = 36) {
        if (!value.isFinite()) return
        target.add(value.coerceAtLeast(0f))
        if (target.size > maxSize) {
            target.removeAt(0)
        }
    }

    private fun jsonNullableString(raw: String?): String {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return "null"
        val escaped = clean
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
        return "\"$escaped\""
    }

    private fun openSpeedTestConnection(
        urlString: String,
        method: String,
        bearerToken: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        val url = URL(urlString)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PingDulu-Android-FieldOps")
            setRequestProperty("Cache-Control", "no-cache, no-store")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
        }
    }

    private fun validateSuccessHttpCode(code: Int, endpoint: String) {
        if (code !in 200..299) {
            throw SpeedTestHttpException(code, formatHttpErrorMessage(code, endpoint))
        }
    }

    private fun formatHttpErrorMessage(code: Int, endpoint: String): String {
        return when (code) {
            401, 403 -> "HTTP $code — Autentikasi Bearer ditolak di $endpoint. Pastikan sudah login di menu Pengaturan."
            404 -> "HTTP 404 — Endpoint $endpoint belum tersedia di server target."
            413 -> "HTTP 413 — Payload upload ditolak (melebihi bodyLimit server)."
            in 300..399 -> "HTTP $code — Server mengalihkan permintaan (gunakan https:// untuk domain produksi)."
            in 500..599 -> "HTTP $code — Terjadi gangguan internal pada server speedtest."
            else -> "HTTP $code — Respons tidak valid dari $endpoint."
        }
    }

    private fun computeJitter(samples: List<Double>): Double {
        if (samples.size <= 1) return 0.0
        var sumDiff = 0.0
        for (i in 1 until samples.size) {
            sumDiff += abs(samples[i] - samples[i - 1])
        }
        return sumDiff / (samples.size - 1)
    }

    private fun bytesToMbps(bytes: Long, elapsedNanos: Long): Double {
        val seconds = (elapsedNanos / 1_000_000_000.0).coerceAtLeast(0.001)
        return (bytes * 8.0) / (seconds * 1_000_000.0)
    }

    private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0
    private fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0

    private class SpeedTestHttpException(val statusCode: Int, message: String) : IOException(message)
}
