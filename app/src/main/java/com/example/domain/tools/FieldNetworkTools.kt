package com.example.domain.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class PingProbeResult(
    val host: String,
    val port: Int,
    val resolvedIp: String?,
    val reachable: Boolean,
    val latencyMs: Long?,
    val detailMessage: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

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

object FieldNetworkTools {

    suspend fun probeHost(
        rawHost: String,
        port: Int = 443,
        timeoutMs: Int = 2_500
    ): PingProbeResult = withContext(Dispatchers.IO) {
        val cleanedHost = rawHost
            .trim()
            .replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .substringBefore("/")
            .substringBefore(":")

        if (cleanedHost.isBlank()) {
            return@withContext PingProbeResult(
                host = rawHost,
                port = port,
                resolvedIp = null,
                reachable = false,
                latencyMs = null,
                detailMessage = "Host atau alamat IP tidak boleh kosong."
            )
        }

        val safePort = port.coerceIn(1, 65535)
        val startNanos = System.nanoTime()

        try {
            val address = InetAddress.getByName(cleanedHost)
            val resolvedIp = address.hostAddress

            val tcpSuccess = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, safePort), timeoutMs)
                }
                true
            }.getOrDefault(false)

            val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)

            if (tcpSuccess) {
                PingProbeResult(
                    host = cleanedHost,
                    port = safePort,
                    resolvedIp = resolvedIp,
                    reachable = true,
                    latencyMs = elapsedMs,
                    detailMessage = "TCP port $safePort merespons dalam ${elapsedMs} ms."
                )
            } else {
                val icmpStart = System.nanoTime()
                val icmpReachable = runCatching { address.isReachable(timeoutMs) }.getOrDefault(false)
                val icmpMs = ((System.nanoTime() - icmpStart) / 1_000_000L).coerceAtLeast(1L)
                if (icmpReachable) {
                    PingProbeResult(
                        host = cleanedHost,
                        port = safePort,
                        resolvedIp = resolvedIp,
                        reachable = true,
                        latencyMs = icmpMs,
                        detailMessage = "Host merespons ICMP/echo (${icmpMs} ms), port TCP $safePort tertutup."
                    )
                } else {
                    PingProbeResult(
                        host = cleanedHost,
                        port = safePort,
                        resolvedIp = resolvedIp,
                        reachable = false,
                        latencyMs = null,
                        detailMessage = "Timeout/tidak dapat dijangkau di port $safePort."
                    )
                }
            }
        } catch (e: Exception) {
            PingProbeResult(
                host = cleanedHost,
                port = safePort,
                resolvedIp = null,
                reachable = false,
                latencyMs = null,
                detailMessage = e.localizedMessage ?: "Gagal menyelesaikan nama host (offline/DNS error)."
            )
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
