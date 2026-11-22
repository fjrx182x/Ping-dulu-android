package com.example

import com.example.data.local.SeedTaskData
import com.example.data.local.ShiftStatus
import com.example.data.local.toDomain
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.domain.tools.FieldNetworkTools
import com.example.domain.tools.NetworkProbeTransport
import com.example.domain.tools.PoeBudgetStatus
import com.example.domain.tools.ProbeStatus
import com.example.domain.tools.Rj45PoeReference
import com.example.domain.tools.Rj45WiringMode
import com.example.domain.tools.SpeedTestPhase
import com.example.domain.tools.SpeedTestProgress
import com.example.domain.tools.WireColorCode
import com.example.presentation.components.FieldToolkitTab
import com.example.presentation.schedule.ScheduleViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class FieldToolsAndDrawerAuditTest {

    private val testDispatcher = StandardTestDispatcher()
    private val baseEpochDay = DateEpochUtil.toEpochDay(2026, 10, 20)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun subnetCalculator_computesStandardSlash24AndSlash30AndSpecialPrefixes() {
        val slash24 = FieldNetworkTools.calculateSubnet("192.168.10.42", 24)
        assertNotNull(slash24)
        assertEquals("192.168.10.0/24", slash24!!.cidrNotation)
        assertEquals("192.168.10.0", slash24.networkAddress)
        assertEquals("192.168.10.255", slash24.broadcastAddress)
        assertEquals("255.255.255.0", slash24.subnetMask)
        assertEquals("0.0.0.255", slash24.wildcardMask)
        assertEquals("192.168.10.1", slash24.firstUsableHost)
        assertEquals("192.168.10.254", slash24.lastUsableHost)
        assertEquals(254L, slash24.usableHostCount)
        assertTrue(slash24.addressType.contains("RFC 1918"))

        val slash30 = FieldNetworkTools.calculateSubnet("10.20.30.5", 30)
        assertNotNull(slash30)
        assertEquals("10.20.30.4/30", slash30!!.cidrNotation)
        assertEquals("10.20.30.7", slash30.broadcastAddress)
        assertEquals("255.255.255.252", slash30.subnetMask)
        assertEquals("10.20.30.5", slash30.firstUsableHost)
        assertEquals("10.20.30.6", slash30.lastUsableHost)
        assertEquals(2L, slash30.usableHostCount)

        val slash31 = FieldNetworkTools.calculateSubnet("172.16.0.1", 31)
        assertNotNull(slash31)
        assertEquals(2L, slash31!!.usableHostCount)
        assertEquals("172.16.0.0", slash31.firstUsableHost)
        assertEquals("172.16.0.1", slash31.lastUsableHost)

        val slash32 = FieldNetworkTools.calculateSubnet("8.8.8.8", 32)
        assertNotNull(slash32)
        assertEquals(1L, slash32!!.usableHostCount)
        assertEquals("8.8.8.8", slash32.networkAddress)
        assertTrue(slash32.addressType.contains("Public"))

        val slash0 = FieldNetworkTools.calculateSubnet("0.0.0.0", 0)
        assertNotNull(slash0)
        assertEquals("0.0.0.0", slash0!!.subnetMask)
        assertEquals("255.255.255.255", slash0.broadcastAddress)
    }

    @Test
    fun subnetCalculator_rejectsInvalidIpsAndPrefixes() {
        assertNull(FieldNetworkTools.calculateSubnet("999.1.1.1", 24))
        assertNull(FieldNetworkTools.calculateSubnet("192.168.1", 24))
        assertNull(FieldNetworkTools.calculateSubnet("", 24))
        assertNull(FieldNetworkTools.calculateSubnet("192.168.1.1", -1))
        assertNull(FieldNetworkTools.calculateSubnet("192.168.1.1", 33))
    }

    @Test
    fun rj45PoeReference_providesValidPinoutsForAllModesAndComputesPoeBudgetAccurately() {
        // 1. T568B standard 8-pin color order
        val bPins = Rj45PoeReference.pinsForEndA(Rj45WiringMode.T568B)
        assertEquals(8, bPins.size)
        assertEquals(WireColorCode.WHITE_ORANGE, bPins[0].wireColor)
        assertEquals(WireColorCode.ORANGE, bPins[1].wireColor)
        assertEquals(WireColorCode.WHITE_GREEN, bPins[2].wireColor)
        assertEquals(WireColorCode.BLUE, bPins[3].wireColor)
        assertEquals(WireColorCode.WHITE_BLUE, bPins[4].wireColor)
        assertEquals(WireColorCode.GREEN, bPins[5].wireColor)
        assertEquals(WireColorCode.WHITE_BROWN, bPins[6].wireColor)
        assertEquals(WireColorCode.BROWN, bPins[7].wireColor)

        // 2. Crossover maps End A (T568A) <-> End B (T568B)
        val crossA = Rj45PoeReference.pinsForEndA(Rj45WiringMode.CROSSOVER)
        val crossB = Rj45PoeReference.pinsForEndB(Rj45WiringMode.CROSSOVER)
        assertEquals(WireColorCode.WHITE_GREEN, crossA[0].wireColor)
        assertEquals(WireColorCode.WHITE_ORANGE, crossB[0].wireColor)

        // 3. Console Rollover reverses pin 1..8 completely (1 <-> 8)
        val rollA = Rj45PoeReference.pinsForEndA(Rj45WiringMode.ROLLOVER)
        val rollB = Rj45PoeReference.pinsForEndB(Rj45WiringMode.ROLLOVER)
        assertEquals(rollA.first().wireColor, rollB.last().wireColor)
        assertEquals(rollA.last().wireColor, rollB.first().wireColor)

        // 4. PoE standards include IEEE 802.3af/at/bt and Passive PoE warning
        assertTrue(Rj45PoeReference.poeStandards.any { it.standardCode == "IEEE 802.3af" })
        assertTrue(Rj45PoeReference.poeStandards.any { it.standardCode == "IEEE 802.3at" })
        assertTrue(Rj45PoeReference.poeStandards.any { it.standardCode == "IEEE 802.3bt" })
        assertTrue(Rj45PoeReference.poeStandards.any { it.isPassiveWarning })

        // 5. PoE Budget Calculator (SAFE <= 80%, CAUTION 81-100%, OVERLOAD > 100%)
        val safe = Rj45PoeReference.calculatePoeBudget(
            switchBudgetWatts = 120,
            apCount = 2,      // 30W
            cameraCount = 4,  // 48W
            ptzCount = 0,
            phoneCount = 0
        ) // 78W / 120W = 65%
        assertEquals(78.0, safe.totalDemandWatts, 0.01)
        assertEquals(42.0, safe.remainingWatts, 0.01)
        assertEquals(65, safe.utilizationPercent)
        assertEquals(PoeBudgetStatus.SAFE, safe.status)

        val caution = Rj45PoeReference.calculatePoeBudget(
            switchBudgetWatts = 120,
            apCount = 4,      // 60W
            cameraCount = 4,  // 48W -> 108W = 90%
            ptzCount = 0,
            phoneCount = 0
        )
        assertEquals(PoeBudgetStatus.CAUTION, caution.status)

        val overload = Rj45PoeReference.calculatePoeBudget(
            switchBudgetWatts = 65,
            apCount = 2,      // 30W
            cameraCount = 2,  // 24W
            ptzCount = 1,     // 25W -> 79W > 65W
            phoneCount = 0
        )
        assertEquals(PoeBudgetStatus.OVERLOAD, overload.status)
        assertEquals(-14.0, overload.remainingWatts, 0.01)
    }

    @Test
    fun pingProbe_validatesBadInputAndMeasuresRealOpenAndClosedSocketsHonestly() = runTest {
        // 1. Invalid inputs must fail fast as INVALID_INPUT without fake latency
        val blankResult = FieldNetworkTools.probeHost("   ", 443)
        assertEquals(ProbeStatus.INVALID_INPUT, blankResult.status)
        assertFalse(blankResult.reachable)
        assertNull(blankResult.avgLatencyMs)
        assertTrue(blankResult.attempts.isEmpty())

        val badIpResult = FieldNetworkTools.probeHost("192.168.1.999", 443)
        assertEquals(ProbeStatus.INVALID_INPUT, badIpResult.status)
        assertFalse(badIpResult.reachable)

        val spaceHostResult = FieldNetworkTools.probeHost("ping fjrx.my.id", 443)
        assertEquals(ProbeStatus.INVALID_INPUT, spaceHostResult.status)

        // 2. Real open TCP port (3x consecutive handshakes on loopback)
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val acceptThread = Thread {
                repeat(3) {
                    runCatching { server.accept().close() }
                }
            }.apply { start() }

            // Intentionally pass default port=443 while inline URL specifies :$port to verify inline port parsing
            val result = FieldNetworkTools.probeHost(
                rawHost = "HTTP://127.0.0.1:$port/health",
                port = 443,
                attempts = 3,
                intervalMs = 0L
            )
            acceptThread.join(2_000L)

            assertEquals(ProbeStatus.REACHABLE, result.status)
            assertTrue(result.reachable)
            assertEquals("127.0.0.1", result.resolvedIp)
            assertEquals(port, result.port)
            assertEquals(3, result.totalAttempts)
            assertEquals(3, result.successCount)
            assertEquals(0, result.packetLossPercent)
            assertNull(result.dnsLookupMs) // IP literal does not report fake DNS lookup delay
            assertNotNull(result.avgLatencyMs)
            assertTrue(result.minLatencyMs!! <= result.avgLatencyMs!!)
            assertTrue(result.avgLatencyMs!! <= result.maxLatencyMs!!)
        }

        // 3. Real closed TCP port (OS returns ECONNREFUSED / TCP RST) -> MUST report PORT_CLOSED, NEVER TIMEOUT or REACHABLE
        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val closedResult = FieldNetworkTools.probeHost(
            rawHost = "127.0.0.1",
            port = closedPort,
            attempts = 2,
            intervalMs = 0L
        )
        assertEquals(ProbeStatus.PORT_CLOSED, closedResult.status)
        assertFalse(closedResult.reachable)
        assertEquals(0, closedResult.successCount)
        assertTrue(closedResult.detailMessage.contains("RST", ignoreCase = true))

        // 4. Real non-existent RFC 2606 domain -> MUST report DNS_ERROR and fail fast
        val dnsFailResult = FieldNetworkTools.probeHost(
            rawHost = "nonexistent-host.pingdulu.invalid",
            port = 443,
            attempts = 3,
            intervalMs = 0L
        )
        assertEquals(ProbeStatus.DNS_ERROR, dnsFailResult.status)
        assertFalse(dnsFailResult.reachable)
        assertNull(dnsFailResult.resolvedIp)
        assertTrue(dnsFailResult.attempts.isEmpty())
    }

    @Test
    fun pingProbe_separatesDnsFromTcpRttAndReportsPartialLossTimeoutUnreachableHonestly() = runTest {
        val loopback = InetAddress.getByName("127.0.0.1")

        // A. Slow DNS (60ms) MUST NOT pollute TCP RTT (12.5, 15.0, 17.5 -> exact avg 15.0ms)
        val samples = ArrayDeque(listOf(12.5, 15.0, 17.5))
        val dnsIsolatedResult = FieldNetworkTools.probeHostInternal(
            rawHost = "ping.fjrx.my.id",
            port = 443,
            attempts = 3,
            intervalMs = 0L,
            transport = object : NetworkProbeTransport {
                override fun resolveHost(host: String): InetAddress {
                    Thread.sleep(60L)
                    return loopback
                }

                override suspend fun connectTcp(address: InetAddress, port: Int, timeoutMs: Int): Double {
                    return samples.removeFirst()
                }
            }
        )
        assertEquals(ProbeStatus.REACHABLE, dnsIsolatedResult.status)
        assertNotNull(dnsIsolatedResult.dnsLookupMs)
        assertTrue("DNS time should reflect ~60ms lookup", dnsIsolatedResult.dnsLookupMs!! >= 45.0)
        assertEquals(12.5, dnsIsolatedResult.minLatencyMs!!, 0.001)
        assertEquals(15.0, dnsIsolatedResult.avgLatencyMs!!, 0.001)
        assertEquals(17.5, dnsIsolatedResult.maxLatencyMs!!, 0.001)

        // B. Partial packet loss (2/3 OK, 1/3 Timeout) -> PARTIAL_LOSS, 33% loss, avg over OK only
        var callIndex = 0
        val partialResult = FieldNetworkTools.probeHostInternal(
            rawHost = "1.1.1.1",
            port = 443,
            attempts = 3,
            intervalMs = 0L,
            transport = object : NetworkProbeTransport {
                override fun resolveHost(host: String): InetAddress = loopback
                override suspend fun connectTcp(address: InetAddress, port: Int, timeoutMs: Int): Double {
                    callIndex++
                    return when (callIndex) {
                        1 -> 10.0
                        2 -> throw SocketTimeoutException("Read timed out")
                        else -> 20.0
                    }
                }
            }
        )
        assertEquals(ProbeStatus.PARTIAL_LOSS, partialResult.status)
        assertEquals(2, partialResult.successCount)
        assertEquals(3, partialResult.totalAttempts)
        assertEquals(33, partialResult.packetLossPercent)
        assertEquals(15.0, partialResult.avgLatencyMs!!, 0.001)

        // C. All timeouts -> TIMEOUT, 100% loss, null latency
        val timeoutResult = FieldNetworkTools.probeHostInternal(
            rawHost = "192.0.2.1",
            port = 443,
            attempts = 3,
            intervalMs = 0L,
            transport = object : NetworkProbeTransport {
                override fun resolveHost(host: String): InetAddress = loopback
                override suspend fun connectTcp(address: InetAddress, port: Int, timeoutMs: Int): Double {
                    throw SocketTimeoutException("connect timed out")
                }
            }
        )
        assertEquals(ProbeStatus.TIMEOUT, timeoutResult.status)
        assertFalse(timeoutResult.reachable)
        assertEquals(100, timeoutResult.packetLossPercent)
        assertNull(timeoutResult.avgLatencyMs)

        // D. NoRouteToHost -> fail-fast on attempt #1 as UNREACHABLE
        val unreachableResult = FieldNetworkTools.probeHostInternal(
            rawHost = "10.255.255.1",
            port = 443,
            attempts = 3,
            intervalMs = 0L,
            transport = object : NetworkProbeTransport {
                override fun resolveHost(host: String): InetAddress = loopback
                override suspend fun connectTcp(address: InetAddress, port: Int, timeoutMs: Int): Double {
                    throw NoRouteToHostException("No route to host")
                }
            }
        )
        assertEquals(ProbeStatus.UNREACHABLE, unreachableResult.status)
        assertEquals(1, unreachableResult.totalAttempts)
        assertFalse(unreachableResult.reachable)
    }

    @Test
    fun drawerFiltersAndShiftStatus_updateScheduleUiStateCorrectly() = runTest(testDispatcher) {
        val seeded = SeedTaskData.createInitialTasks(baseEpochDay = baseEpochDay).map { it.toDomain() }
        val repo = DrawerAuditFakeRepository(seeded)
        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { baseEpochDay },
            hourOfDayProvider = { 10 },
            enableBackgroundTicker = false,
            enableSseStream = false
        )

        val collector = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        // Default state: no drawer filters, 2 tasks today (NET-1 P1, SRV-3 P2)
        assertEquals(0, vm.uiState.value.activeDrawerFilterCount)
        assertEquals(2, vm.uiState.value.filteredTasks.size)
        assertEquals(ShiftStatus.ON_DUTY, vm.uiState.value.shiftStatus)

        // Toggle P1 critical only
        vm.toggleOnlyCriticalP1()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeDrawerFilterCount)
        assertEquals(1, vm.uiState.value.filteredTasks.size)
        assertEquals(Priority.P1_CRITICAL, vm.uiState.value.filteredTasks.first().priority)

        // Add Category filter SRV (day 0 has no P1 SRV -> empty)
        vm.selectCategoryFilter(TicketCategory.SRV)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.activeDrawerFilterCount)
        assertEquals(0, vm.uiState.value.filteredTasks.size)

        // Reset drawer filters -> back to 2 tasks
        vm.resetDrawerFilters()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.activeDrawerFilterCount)
        assertEquals(2, vm.uiState.value.filteredTasks.size)

        // Toggle My Tickets filter (Fajar) -> includes NET-1 & SRV-3 where Fajar is assigned
        vm.toggleOnlyMyTickets()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeDrawerFilterCount)
        assertTrue(vm.uiState.value.filteredTasks.all { task ->
            task.assignees.any { it.name.contains("Fajar", ignoreCase = true) }
        })

        // Update shift status
        vm.setShiftStatus(ShiftStatus.STANDBY)
        advanceUntilIdle()
        assertEquals(ShiftStatus.STANDBY, vm.uiState.value.shiftStatus)
        assertTrue(vm.uiState.value.syncMessage?.contains("Standby") == true)

        // Trigger mutation -> unsyncedTasks list populates
        vm.toggleChecklistItem(59L, 1L)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.unsyncedTasks.any { it.id == 59L })

        vm.stopBackgroundJobs()
        collector.cancel()
    }

    @Test
    fun speedtest_preservesAllFourToolkitTabsAndNormalizesUrls() {
        // Must preserve RJ45_POE alongside PING, SPEEDTEST, and SUBNET
        assertEquals(4, FieldToolkitTab.entries.size)
        assertEquals(
            listOf(
                FieldToolkitTab.PING,
                FieldToolkitTab.SPEEDTEST,
                FieldToolkitTab.SUBNET,
                FieldToolkitTab.RJ45_POE
            ),
            FieldToolkitTab.entries
        )
        assertEquals("Speed Test", FieldToolkitTab.SPEEDTEST.label)

        // URL normalization rules
        assertEquals("", FieldNetworkTools.normalizeHttpUrl("   "))
        assertEquals("http://192.168.1.10:3001", FieldNetworkTools.normalizeHttpUrl("192.168.1.10:3001/"))
        assertEquals("https://ping.fjrx.my.id", FieldNetworkTools.normalizeHttpUrl("ping.fjrx.my.id/"))
        assertEquals("https://ping.fjrx.my.id", FieldNetworkTools.normalizeHttpUrl("https://ping.fjrx.my.id///"))
        assertEquals("http://10.20.30.40:3001", FieldNetworkTools.normalizeHttpUrl("http://10.20.30.40:3001/"))
    }

    @Test
    fun speedtest_executesFullLibreSpeedFlowWithBearerHeaderAndWibSummary() = runBlocking {
        val observedRequests = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
        val garbageChunk = ByteArray(128 * 1024) { 0x42 }
        val server = LoopbackSpeedTestHttpServer { method, path, headers, _ ->
            observedRequests.add("$method ${path.substringBefore('?')}" to headers["authorization"])
            when {
                method == "GET" && path.startsWith("/api/speedtest/ip") -> {
                    val json = """{"processedString":"10.10.20.42 (PingDulu NOC)","rawIp":"10.10.20.42"}"""
                    MockHttpResponse(200, "application/json", json.toByteArray())
                }
                method == "GET" && path.startsWith("/api/speedtest/empty") -> {
                    MockHttpResponse(200, "text/plain", ByteArray(0))
                }
                method == "GET" && path.startsWith("/api/speedtest/garbage") -> {
                    MockHttpResponse(200, "application/octet-stream", garbageChunk)
                }
                method == "POST" && path.startsWith("/api/speedtest/empty") -> {
                    MockHttpResponse(200, "text/plain", "OK".toByteArray())
                }
                else -> MockHttpResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }

        server.use {
            val phases = Collections.synchronizedList(mutableListOf<SpeedTestPhase>())
            val result = FieldNetworkTools.runSpeedTest(
                serverBaseUrl = "http://127.0.0.1:${server.port}",
                authToken = "secret-jwt-token",
                pingSamples = 3,
                downloadDurationMs = 350L,
                uploadDurationMs = 350L,
                uploadChunkBytes = 32 * 1024,
                maxUploadTotalBytes = 128 * 1024L,
                onProgress = { p: SpeedTestProgress -> phases.add(p.phase) }
            )

            assertTrue("Expected speed test success, got error: ${result.errorMessage}", result.isSuccess)
            assertNull(result.errorMessage)
            assertEquals("10.10.20.42 (PingDulu NOC)", result.clientIp)
            assertTrue("pingMs should be > 0", result.pingMs > 0.0)
            assertTrue("downloadMbps should be > 0", result.downloadMbps > 0.0)
            assertTrue("uploadMbps should be > 0", result.uploadMbps > 0.0)

            // Verify Authorization Bearer header was sent on EVERY speedtest endpoint
            assertTrue(observedRequests.any { it.first == "GET /api/speedtest/ip" })
            assertTrue(observedRequests.any { it.first == "GET /api/speedtest/empty" })
            assertTrue(observedRequests.any { it.first == "GET /api/speedtest/garbage" })
            assertTrue(observedRequests.any { it.first == "POST /api/speedtest/empty" })
            assertTrue(
                "All endpoints must receive Bearer header",
                observedRequests.all { it.second == "Bearer secret-jwt-token" }
            )

            // Verify phase progression
            assertTrue(phases.contains(SpeedTestPhase.PING))
            assertTrue(phases.contains(SpeedTestPhase.DOWNLOAD))
            assertTrue(phases.contains(SpeedTestPhase.UPLOAD))
            assertEquals(SpeedTestPhase.COMPLETED, phases.last())

            // Verify WIB diagnostic summary formatting
            val summary = FieldNetworkTools.formatSpeedTestSummary(result)
            assertTrue(summary.contains("WIB"))
            assertTrue(summary.contains("10.10.20.42 (PingDulu NOC)"))
            assertTrue(summary.contains("Download:"))
            assertTrue(summary.contains("Upload:"))
        }
    }

    @Test
    fun speedtest_rejectsBlankUnreachableAndHttpErrorsWithoutFalsePositives() = runBlocking {
        // 1. Blank URL
        val blankRes = FieldNetworkTools.runSpeedTest("   ")
        assertFalse(blankRes.isSuccess)
        assertNotNull(blankRes.errorMessage)

        // 2. Closed port (unreachable)
        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val closedRes = FieldNetworkTools.runSpeedTest("http://127.0.0.1:$closedPort", pingSamples = 2)
        assertFalse(closedRes.isSuccess)
        assertNotNull(closedRes.errorMessage)

        // 3. HTTP 401 Unauthorized (missing/invalid Bearer token) -> fails fast without false positive
        LoopbackSpeedTestHttpServer { _, _, _, _ ->
            MockHttpResponse(401, "application/json", """{"error":"Unauthorized"}""".toByteArray())
        }.use { server ->
            val unauthRes = FieldNetworkTools.runSpeedTest("http://127.0.0.1:${server.port}", authToken = null)
            assertFalse(unauthRes.isSuccess)
            assertTrue(unauthRes.errorMessage.orEmpty().contains("401"))
        }

        // 4. HTTP 404 on /api/speedtest/empty -> fails cleanly
        LoopbackSpeedTestHttpServer { _, path, _, _ ->
            if (path.startsWith("/api/speedtest/ip")) {
                MockHttpResponse(200, "application/json", """{"rawIp":"127.0.0.1"}""".toByteArray())
            } else {
                MockHttpResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }.use { server ->
            val notFoundRes = FieldNetworkTools.runSpeedTest("http://127.0.0.1:${server.port}", pingSamples = 2)
            assertFalse(notFoundRes.isSuccess)
            assertTrue(notFoundRes.errorMessage.orEmpty().contains("404"))
        }

        // 5. HTTP 413 Payload Too Large during Upload -> MUST NOT be swallowed as false-positive success!
        LoopbackSpeedTestHttpServer { method, path, _, _ ->
            when {
                method == "GET" && path.startsWith("/api/speedtest/ip") ->
                    MockHttpResponse(200, "application/json", """{"rawIp":"127.0.0.1"}""".toByteArray())
                method == "GET" && path.startsWith("/api/speedtest/empty") ->
                    MockHttpResponse(200, "text/plain", ByteArray(0))
                method == "GET" && path.startsWith("/api/speedtest/garbage") ->
                    MockHttpResponse(200, "application/octet-stream", ByteArray(64 * 1024) { 1 })
                method == "POST" && path.startsWith("/api/speedtest/empty") ->
                    MockHttpResponse(413, "application/json", """{"error":"Payload Too Large"}""".toByteArray())
                else -> MockHttpResponse(404, "text/plain", "Not Found".toByteArray())
            }
        }.use { server ->
            val tooLargeRes = FieldNetworkTools.runSpeedTest(
                serverBaseUrl = "http://127.0.0.1:${server.port}",
                authToken = "valid-token",
                pingSamples = 2,
                downloadDurationMs = 300L,
                uploadDurationMs = 300L,
                uploadChunkBytes = 32 * 1024
            )
            assertFalse("Upload HTTP 413 must fail, not report false positive", tooLargeRes.isSuccess)
            assertTrue(tooLargeRes.errorMessage.orEmpty().contains("413"))
        }
    }

    @Test
    fun speedtest_rethrowsCancellationExceptionCleanlyWhenAborted() = runBlocking {
        val downloadStarted = CountDownLatch(1)
        val server = LoopbackSpeedTestHttpServer { method, path, _, _ ->
            when {
                method == "GET" && path.startsWith("/api/speedtest/ip") ->
                    MockHttpResponse(200, "application/json", """{"rawIp":"127.0.0.1"}""".toByteArray())
                method == "GET" && path.startsWith("/api/speedtest/empty") ->
                    MockHttpResponse(200, "text/plain", ByteArray(0))
                method == "GET" && path.startsWith("/api/speedtest/garbage") -> {
                    downloadStarted.countDown()
                    // Delay response so cancellation occurs mid-download
                    runCatching { Thread.sleep(2_000L) }
                    MockHttpResponse(200, "application/octet-stream", ByteArray(64 * 1024))
                }
                else -> MockHttpResponse(200, "text/plain", ByteArray(0))
            }
        }

        server.use {
            val caughtCancellation = AtomicBoolean(false)
            val job = launch(Dispatchers.Default) {
                try {
                    FieldNetworkTools.runSpeedTest(
                        serverBaseUrl = "http://127.0.0.1:${server.port}",
                        authToken = "tok",
                        pingSamples = 2,
                        downloadDurationMs = 5_000L
                    )
                } catch (_: CancellationException) {
                    caughtCancellation.set(true)
                }
            }

            assertTrue("Download phase should start promptly", downloadStarted.await(3, TimeUnit.SECONDS))
            job.cancel()
            job.join()
            assertTrue("runSpeedTest must rethrow CancellationException cleanly", caughtCancellation.get())
        }
    }
}

private data class MockHttpResponse(
    val statusCode: Int,
    val contentType: String,
    val body: ByteArray
)

/**
 * Minimal deterministic HTTP/1.1 loopback server for unit-testing LibreSpeed client behavior
 * without relying on external internet or third-party mock server dependencies.
 */
private class LoopbackSpeedTestHttpServer(
    private val handler: (method: String, path: String, headers: Map<String, String>, bodyLength: Int) -> MockHttpResponse
) : AutoCloseable {
    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicBoolean(true)
    val port: Int = serverSocket.localPort

    private val workerThread = Thread {
        while (running.get()) {
            val client = try {
                serverSocket.accept()
            } catch (_: Exception) {
                break
            }
            Thread {
                client.use { socket ->
                    runCatching {
                        socket.soTimeout = 3_000
                        val input = BufferedInputStream(socket.getInputStream())
                        val requestLine = readHttpLine(input) ?: return@use
                        val parts = requestLine.split(" ")
                        if (parts.size < 2) return@use
                        val method = parts[0]
                        val path = parts[1]

                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = readHttpLine(input) ?: break
                            if (line.isEmpty()) break
                            val colon = line.indexOf(':')
                            if (colon > 0) {
                                val key = line.substring(0, colon).trim().lowercase()
                                val value = line.substring(colon + 1).trim()
                                headers[key] = value
                            }
                        }

                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        var remaining = contentLength
                        val drainBuf = ByteArray(8192)
                        while (remaining > 0) {
                            val n = input.read(drainBuf, 0, minOf(drainBuf.size, remaining))
                            if (n <= 0) break
                            remaining -= n
                        }

                        val resp = handler(method, path, headers, contentLength)
                        val reason = when (resp.statusCode) {
                            200 -> "OK"
                            401 -> "Unauthorized"
                            403 -> "Forbidden"
                            404 -> "Not Found"
                            413 -> "Payload Too Large"
                            else -> "Error"
                        }
                        val out = socket.getOutputStream()
                        val headerBytes = (
                            "HTTP/1.1 ${resp.statusCode} $reason\r\n" +
                                "Content-Type: ${resp.contentType}\r\n" +
                                "Content-Length: ${resp.body.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray()
                        out.write(headerBytes)
                        if (resp.body.isNotEmpty()) {
                            out.write(resp.body)
                        }
                        out.flush()
                    }
                }
            }.apply { isDaemon = true; start() }
        }
    }.apply { isDaemon = true; start() }

    private fun readHttpLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (prev == '\r'.code && sb.isNotEmpty()) {
                    sb.setLength(sb.length - 1)
                }
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket.close() }
        workerThread.interrupt()
    }
}

private class DrawerAuditFakeRepository(initial: List<Task>) : TaskRepository {
    private val tasksFlow = MutableStateFlow(initial)

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay == epochDay } }

    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow

    override fun observeActiveHeroTask(): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.stage == Stage.IN_PROGRESS } }

    override fun observeTaskById(taskId: Long): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.id == taskId } }

    override fun observeUnsyncedCount(): Flow<Int> =
        tasksFlow.map { list -> list.count { !it.isSynced } }

    override suspend fun initializeOfflineCache() = Unit

    override suspend fun refreshTasksFromServer(): SyncResult =
        SyncResult.OfflineFallback(pendingUploadCount = tasksFlow.value.count { !it.isSynced }, reason = "Offline")

    override suspend fun toggleChecklistItem(taskId: Long, checklistId: Long) {
        tasksFlow.value = tasksFlow.value.map { task ->
            if (task.id == taskId) {
                task.copy(
                    checklist = task.checklist.map {
                        if (it.id == checklistId) it.copy(isDone = !it.isDone) else it
                    },
                    isSynced = false
                )
            } else {
                task
            }
        }
    }

    override suspend fun toggleTaskTimer(taskId: Long) = Unit
    override suspend fun persistTimerTick(taskId: Long, newTrackedSeconds: Long) = Unit
    override suspend fun updateTaskStage(taskId: Long, newStage: Stage) = Unit
    override suspend fun markTaskCompleted(taskId: Long) = Unit
    override suspend fun createQuickTask(
        title: String,
        category: TicketCategory,
        priority: Priority,
        epochDay: Long,
        location: String,
        scheduledTimeMinutes: Int?,
        technicalNotes: String,
        labels: List<String>,
        assigneeIds: List<Long>,
        labelIds: List<Long>,
        checklistSteps: List<String>,
        isRecurring: Boolean,
        recurrenceInterval: String?
    ): Task = tasksFlow.value.first()

    override suspend fun login(username: String, password: String): Result<String> = Result.success("token")

    override suspend fun clearSyncedCacheOnLogout() {
        tasksFlow.value = tasksFlow.value.filter { !it.isSynced }
    }
}
