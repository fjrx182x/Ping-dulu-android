package com.example.domain.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlin.coroutines.resume

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
}
