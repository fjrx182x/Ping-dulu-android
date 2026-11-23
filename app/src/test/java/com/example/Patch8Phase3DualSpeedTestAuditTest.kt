package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.domain.tools.ClientNetworkInfo
import com.example.domain.tools.PublicEdgeSpeedTestEngine
import com.example.domain.tools.RealIspDetector
import com.example.domain.tools.SpeedTestPhase
import com.example.domain.tools.SpeedTestRecordsClient
import com.example.domain.tools.SpeedTestResult
import com.example.domain.tools.SpeedTestServerRecord
import com.example.domain.tools.SpeedTestTargetMode
import com.example.presentation.components.SpeedTestSection
import com.example.ui.theme.PingDuluFieldOpsTheme
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * Patch Ke-8 Phase 3 Audit Suite:
 * Verifies RealIspDetector fallback chain, Cloudflare Edge 100G multi-stream engine,
 * auto-tagged POST/GET /api/speedtest/records client, and Dual-Mode SpeedTest UI.
 * Uses only isolated loopback HttpServers & in-memory state (never touches real tasks).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Patch8Phase3DualSpeedTestAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realIspDetector_parsesPrimaryFallsBackAutomaticallyAndNeverHardcodesVendor() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/primary-ok") { ex ->
            val body = """
                {
                  "success": true,
                  "ip": "114.122.45.10",
                  "city": "Bandung",
                  "country": "Indonesia",
                  "connection": { "isp": "PT Telekomunikasi Indonesia", "org": "Telkom" }
                }
            """.trimIndent().toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/primary-fail") { ex ->
            ex.sendResponseHeaders(503, 0)
            ex.responseBody.close()
        }
        server.createContext("/fallback-ok") { ex ->
            val body = """
                {
                  "ipAddress": "103.88.210.4",
                  "asnOrganization": "Biznet Networks",
                  "cityName": "Surabaya",
                  "countryName": "Indonesia"
                }
            """.trimIndent().toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val base = "http://127.0.0.1:${server.address.port}"

            // 1. Primary ipwho.is succeeds
            val primaryInfo = RealIspDetector.detect(
                primaryUrl = "$base/primary-ok",
                fallbackUrl = "$base/fallback-ok"
            )
            assertEquals("114.122.45.10", primaryInfo.ip)
            assertEquals("PT Telekomunikasi Indonesia", primaryInfo.isp)
            assertEquals("Bandung, Indonesia", primaryInfo.location)
            assertTrue(primaryInfo.isDetectedReal)

            // 2. Primary fails -> automatic fallback to freeipapi.com
            val fallbackInfo = RealIspDetector.detect(
                primaryUrl = "$base/primary-fail",
                fallbackUrl = "$base/fallback-ok"
            )
            assertEquals("103.88.210.4", fallbackInfo.ip)
            assertEquals("Biznet Networks", fallbackInfo.isp)
            assertEquals("Surabaya, Indonesia", fallbackInfo.location)

            // 3. Both fail -> neutral offline fallback (NEVER hardcoded "Iconnet")
            val offlineInfo = RealIspDetector.detect(
                primaryUrl = "$base/primary-fail",
                fallbackUrl = "$base/primary-fail"
            )
            assertEquals("—", offlineInfo.ip)
            assertEquals("ISP Publik", offlineInfo.isp)
            assertFalse(offlineInfo.isp.contains("Iconnet", ignoreCase = true))
            assertFalse(offlineInfo.isDetectedReal)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun publicEdgeEngine_executesMultiStreamPingDownloadUploadAndCancelsCleanly() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val downloadChunk = ByteArray(256 * 1024) { 0x3C }

        server.createContext("/ping") { ex ->
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.close()
        }
        server.createContext("/download") { ex ->
            ex.sendResponseHeaders(200, downloadChunk.size.toLong())
            ex.responseBody.use { it.write(downloadChunk) }
        }
        server.createContext("/upload") { ex ->
            ex.requestBody.use { it.readBytes() }
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.close()
        }
        server.createContext("/slow-download") { ex ->
            ex.sendResponseHeaders(200, 0)
            try {
                Thread.sleep(3_000L)
            } catch (_: InterruptedException) {
            }
            runCatching { ex.responseBody.close() }
        }
        server.start()

        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val seenPhases = mutableSetOf<SpeedTestPhase>()
            val info = ClientNetworkInfo("114.122.45.10", "Telkom", "Bandung, Indonesia")

            val result = PublicEdgeSpeedTestEngine.runPublicEdgeTest(
                preDetectedClientInfo = info,
                pingUrlBase = "$base/ping",
                downloadUrlBase = "$base/download",
                uploadUrlBase = "$base/upload",
                detectIspIfMissing = false,
                pingSamples = 4,
                downloadStreams = 2,
                uploadStreams = 2,
                downloadDurationMs = 450L,
                uploadDurationMs = 450L,
                uploadChunkBytes = 64 * 1024,
                onProgress = { seenPhases.add(it.phase) }
            )

            assertTrue("Expected success: ${result.errorMessage}", result.isSuccess)
            assertEquals("Cloudflare Edge 100G", result.serverUrl)
            assertEquals("114.122.45.10", result.clientIp)
            assertEquals("Telkom • Bandung, Indonesia", result.ispName)
            assertTrue("Expected download > 0 Mbps", result.downloadMbps > 0.0)
            assertTrue("Expected upload > 0 Mbps", result.uploadMbps > 0.0)
            assertTrue("Expected non-empty DL sparkline", result.downloadSamples.isNotEmpty())
            assertTrue("Expected non-empty UL sparkline", result.uploadSamples.isNotEmpty())
            assertTrue(SpeedTestPhase.PING in seenPhases)
            assertTrue(SpeedTestPhase.DOWNLOAD in seenPhases)
            assertTrue(SpeedTestPhase.UPLOAD in seenPhases)
            assertTrue(SpeedTestPhase.COMPLETED in seenPhases)

            // Verify immediate cooperative cancellation
            var caughtCancel = false
            val cancelJob = launch {
                try {
                    PublicEdgeSpeedTestEngine.runPublicEdgeTest(
                        preDetectedClientInfo = info,
                        pingUrlBase = "$base/ping",
                        downloadUrlBase = "$base/slow-download",
                        uploadUrlBase = "$base/upload",
                        detectIspIfMissing = false,
                        pingSamples = 2,
                        downloadStreams = 2,
                        uploadStreams = 1,
                        downloadDurationMs = 5_000L,
                        uploadDurationMs = 5_000L
                    )
                } catch (_: CancellationException) {
                    caughtCancel = true
                }
            }
            delay(180L)
            cancelJob.cancel()
            cancelJob.join()
            assertTrue("CancellationException must propagate cleanly when aborted", caughtCancel)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun speedTestRecordsClient_autoTagsParsesAndRoundTripsWithBearerAuth() = runBlocking {
        val info = ClientNetworkInfo("103.88.210.4", "Biznet Networks", "Surabaya, Indonesia")

        // 1. Auto-tag builder & parser round-trip (Public Edge 100G)
        val publicTagged = SpeedTestRecordsClient.buildTaggedNotes(
            mode = SpeedTestTargetMode.PUBLIC_EDGE_100G,
            clientInfo = info,
            userNotes = "ODP-07 Blok A"
        )
        assertEquals(
            "[Target: Internet Publik 100G] [ISP: Biznet Networks • Surabaya, Indonesia] ODP-07 Blok A",
            publicTagged
        )
        val parsedPublic = SpeedTestRecordsClient.parseTaggedNotes(publicTagged)
        assertEquals(SpeedTestTargetMode.PUBLIC_EDGE_100G, parsedPublic.targetMode)
        assertEquals("Biznet Networks • Surabaya, Indonesia", parsedPublic.ispLabel)
        assertEquals("ODP-07 Blok A", parsedPublic.userNote)

        // 2. Auto-tag builder & parser round-trip (Local LAN Server)
        val localTagged = SpeedTestRecordsClient.buildTaggedNotes(
            mode = SpeedTestTargetMode.LOCAL_LAN_SERVER,
            clientInfo = info,
            userNotes = ""
        )
        val parsedLocal = SpeedTestRecordsClient.parseTaggedNotes(localTagged)
        assertEquals(SpeedTestTargetMode.LOCAL_LAN_SERVER, parsedLocal.targetMode)
        assertEquals("Biznet Networks • Surabaya, Indonesia", parsedLocal.ispLabel)
        assertEquals("", parsedLocal.userNote)

        // 3. Real HTTP POST & GET /api/speedtest/records loopback verification
        val capturedAuth = AtomicReference<String?>()
        val capturedBody = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/speedtest/records") { ex ->
            capturedAuth.set(ex.requestHeaders.getFirst("Authorization"))
            if (ex.requestMethod.equals("POST", ignoreCase = true)) {
                capturedBody.set(ex.requestBody.bufferedReader().use { it.readText() })
                val ok = """{"success":true,"id":42}""".toByteArray()
                ex.sendResponseHeaders(201, ok.size.toLong())
                ex.responseBody.use { it.write(ok) }
            } else {
                val listJson = """
                    {
                      "records": [
                        {
                          "id": 42,
                          "ping_ms": 11.4,
                          "jitter_ms": 1.8,
                          "download_mbps": 184.5,
                          "upload_mbps": 92.1,
                          "client_ip": "103.88.210.4",
                          "technician_name": "Teknisi Purnomo",
                          "created_at": "2026-11-22T15:20:00.000Z",
                          "notes": "[Target: Internet Publik 100G] [ISP: Biznet Networks • Surabaya, Indonesia] ODP-07 Blok A"
                        },
                        {
                          "id": 41,
                          "ping_ms": 2.1,
                          "jitter_ms": 0.4,
                          "download_mbps": 95.0,
                          "upload_mbps": 88.4,
                          "client_ip": "192.168.1.55",
                          "technician_name": "Teknisi Budi",
                          "created_at": "2026-11-22T14:05:00.000Z",
                          "notes": "[Target: Server PingDulu Lokal] [ISP: LAN Kantor] Cek Switch Core"
                        }
                      ]
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, listJson.size.toLong())
                ex.responseBody.use { it.write(listJson) }
            }
        }
        server.start()

        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val dummyResult = SpeedTestResult(
                pingMs = 11.4,
                jitterMs = 1.8,
                downloadMbps = 184.5,
                uploadMbps = 92.1,
                clientIp = info.ip,
                ispName = info.ispWithLocation,
                serverUrl = "Cloudflare Edge 100G",
                isSuccess = true
            )
            val saveRes = SpeedTestRecordsClient.saveRecord(
                serverBaseUrl = base,
                authToken = "jwt-patch8-secret",
                result = dummyResult,
                clientInfo = info,
                mode = SpeedTestTargetMode.PUBLIC_EDGE_100G,
                userNotes = "ODP-07 Blok A"
            )
            assertTrue("Expected saveRecord success", saveRes.isSuccess)
            assertEquals("Bearer jwt-patch8-secret", capturedAuth.get())
            assertTrue(capturedBody.get().orEmpty().contains("[Target: Internet Publik 100G]"))
            assertTrue(capturedBody.get().orEmpty().contains("ODP-07 Blok A"))

            val fetchedRes = SpeedTestRecordsClient.fetchRecords(
                serverBaseUrl = base,
                authToken = "jwt-patch8-secret"
            )
            assertTrue("Expected fetchRecords success", fetchedRes.isSuccess)
            val records = fetchedRes.getOrThrow()
            assertEquals(2, records.size)
            assertEquals(SpeedTestTargetMode.PUBLIC_EDGE_100G, records[0].targetMode)
            assertEquals("Biznet Networks • Surabaya, Indonesia", records[0].ispBadgeLabel)
            assertEquals("ODP-07 Blok A", records[0].cleanUserNote)
            assertEquals(SpeedTestTargetMode.LOCAL_LAN_SERVER, records[1].targetMode)
            assertEquals("LAN Kantor", records[1].ispBadgeLabel)
            assertEquals("Cek Switch Core", records[1].cleanUserNote)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun ui_dualModeSpeedTestSection_defaultsToPublicEdgeAndRendersBadgesAndLocalToggle() {
        val sampleInfo = ClientNetworkInfo(
            ip = "114.122.45.10",
            isp = "Telkom Indonesia",
            location = "Bandung, Indonesia"
        )
        val sampleRecords = listOf(
            SpeedTestServerRecord(
                id = 101L,
                pingMs = 12.0,
                jitterMs = 1.5,
                downloadMbps = 210.4,
                uploadMbps = 105.2,
                clientIp = "114.122.45.10",
                ispName = "Telkom Indonesia • Bandung, Indonesia",
                rawNotes = "[Target: Internet Publik 100G] [ISP: Telkom Indonesia • Bandung, Indonesia] POP Dago",
                technicianName = "Teknisi Purnomo",
                createdAtLabel = "11-22 15:20",
                targetMode = SpeedTestTargetMode.PUBLIC_EDGE_100G,
                ispBadgeLabel = "Telkom Indonesia • Bandung, Indonesia",
                cleanUserNote = "POP Dago"
            ),
            SpeedTestServerRecord(
                id = 100L,
                pingMs = 2.0,
                jitterMs = 0.3,
                downloadMbps = 98.1,
                uploadMbps = 94.6,
                clientIp = "192.168.10.22",
                ispName = "LAN Kantor",
                rawNotes = "[Target: Server PingDulu Lokal] [ISP: LAN Kantor] Core Switch",
                technicianName = "Teknisi Andi",
                createdAtLabel = "11-22 14:10",
                targetMode = SpeedTestTargetMode.LOCAL_LAN_SERVER,
                ispBadgeLabel = "LAN Kantor",
                cleanUserNote = "Core Switch"
            )
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SpeedTestSection(
                        serverHostHint = "https://ping.fjrx.my.id",
                        authToken = "demo-jwt",
                        initialDetectedInfo = sampleInfo,
                        initialServerRecords = sampleRecords,
                        autoFetchOnLaunch = false
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Default mode is PUBLIC_EDGE_100G
        composeRule.onNodeWithTag("speedtest_mode_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("speedtest_run_button").assertTextContains("Mulai Uji Edge 100G")

        // Dynamic Client Device Info Card shows real ISP + Location & Monospace IP
        composeRule.onNodeWithTag("speedtest_client_isp_text")
            .performScrollTo()
            .assertTextContains("Telkom Indonesia • Bandung, Indonesia")
        composeRule.onNodeWithTag("speedtest_client_ip_text")
            .performScrollTo()
            .assertTextContains("114.122.45.10")

        // Server History Card renders parsed Edge 100G & Server Lokal badges + clean user notes
        composeRule.onNodeWithTag("speedtest_server_history_card").performScrollTo().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("⚡ Edge 100G").fetchSemanticsNodes().size >= 2)
        assertTrue(composeRule.onAllNodesWithText("🏢 Server Lokal").fetchSemanticsNodes().size >= 2)
        composeRule.onNodeWithText("POP Dago").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Core Switch").performScrollTo().assertIsDisplayed()

        // Switch to Local LAN mode -> reveals LibreSpeed server host input & updates CTA button
        composeRule.onNodeWithTag("speedtest_mode_local").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("speedtest_run_button").performScrollTo().assertTextContains("Mulai Uji Server Lokal")
        composeRule.onNodeWithTag("speedtest_server_input").performScrollTo().assertIsDisplayed()
    }
}
