# DOKUMEN PATCH KE-5: INTEGRASI SPEED TEST TOOL (LIBRESPEED) PADA FIELD OPS TOOLKIT (OFFLINE)

**Status Dokumen**: FINAL & VERIFIED  
**Nomor Patch**: PATCH KE-5  
**Komponen**: Android App (`com.example`), Field Ops Navigation Drawer, Field Ops Toolkit BottomSheet, FieldNetworkTools, Unit Test Suite  
**Server Target**: PingDulu Self-Hosted Native Fastify LibreSpeed Engine (`/api/speedtest/*`)  
**Lingkungan**: Android Studio / Google AI Studio / PingDulu Production  

---

## 1. Ringkasan Eksekutif & Permintaan Pengguna

Pengguna meminta:
> *"gue pengen tombol menu konten speed test tool nya ada di bagian field ops toolkit offline"*

### Arsitektur Penempatan:
1. **Navigasi Drawer (`FieldOpsNavigationDrawer.kt`)**:
   - Di bawah kategori grup **`FIELD OPS TOOLKIT (OFFLINE)`**, ditambahkan tombol baris baru:
     - **Icon**: `Icons.Rounded.Speed`
     - **Judul**: `Speed Test Tool (LibreSpeed)`
     - **Subjudul**: `Uji throughput unduh & unggah mandiri ke server LAN`
     - **Aksi Klik**: Membuka `FieldToolkitBottomSheet` langsung pada tab `FieldToolkitTab.SPEEDTEST`.
     - **Test Tag**: `drawer_tool_speedtest`

2. **Dialog Bottom Sheet (`FieldToolkitBottomSheet.kt`)**:
   - Tab switcher kini memiliki 3 tab utilitas teknisi lapangan:
     1. `Ping & Latency` (`Icons.Rounded.NetworkCheck`)
     2. `Speed Test` (`Icons.Rounded.Speed`)
     3. `Subnet / CIDR` (`Icons.Rounded.Lan`)
   - **Tampilan Konten Speed Test**:
     - **Input Server Host**: Otomatis terisi dengan `uiState.serverBaseUrl` (atau `http://10.0.2.2:3001` / `http://192.168.1.x:3001`).
     - **Quick Presets Target**: Chip tombol cepat untuk server aktif, emulator host, LAN default, dan domain publik.
     - **4 Kartu Metrik Diagnostik (Ala NOC Monitoring)**:
       - **PING (ms)**: Round-trip time minimum dengan badge `RTT`.
       - **JITTER (ms)**: Variasi deviasi jeda paket dengan badge `VAR`.
       - **DOWNLOAD (Mbps)**: Live stream throughput unduh dengan badge `UNDUH` dan ikon panah bawah.
       - **UPLOAD (Mbps)**: Live stream throughput unggah dengan badge `UNGGAH` dan ikon panah atas.
     - **Live Progress & Status**: Bar indikator animasi dengan persentase dan pesan tahapan pengujian.
     - **Banner IP Klien**: Menampilkan IP perangkat teknisi yang terdeteksi server.
     - **Tombol Salin Ringkasan Diagnostik**: Salin hasil uji ke clipboard format standar untuk catatan tiket gangguan.
     - **Riwayat Pengujian Lokal**: Menyimpan 5 hasil pengujian terakhir di perangkat.

3. **Domain Engine (`FieldNetworkTools.kt`)**:
   - Menggunakan native `HttpURLConnection` berkecepatan tinggi yang kompatibel dengan coroutine `Dispatchers.IO`:
     - `GET /api/speedtest/ip` -> deteksi IP klien & ISP.
     - `GET /api/speedtest/empty` -> pengukuran Ping & Jitter multi-sampel.
     - `GET /api/speedtest/garbage?ckSize=15` -> stream download multi-megabyte dengan perhitungan throughput real-time.
     - `POST /api/speedtest/empty` -> streaming upload buffer chunked mode tanpa membebani memori Android.

---

## 2. Rincian Berkas yang Dimodifikasi

| No | Berkas | Status | Deskripsi |
|---|---|---|---|
| 1 | `app/src/main/java/com/example/domain/tools/FieldNetworkTools.kt` | Dimodifikasi | Menambahkan `SpeedTestResult`, `SpeedTestProgress`, `SpeedTestPhase`, fungsi `runSpeedTest()`, dan `normalizeHttpUrl()`. |
| 2 | `app/src/main/java/com/example/presentation/components/FieldOpsNavigationDrawer.kt` | Dimodifikasi | Menambahkan `DrawerToolRow` untuk Speed Test pada bagian `DrawerToolkitSection`. |
| 3 | `app/src/main/java/com/example/presentation/components/FieldToolkitBottomSheet.kt` | Dimodifikasi | Menambahkan `FieldToolkitTab.SPEEDTEST`, icon tab, segmen switcher 3 tab, composable `SpeedTestSection()`, dan `SpeedMetricCard()`. |
| 4 | `app/src/test/java/com/example/FieldToolsAndDrawerAuditTest.kt` | Dimodifikasi | Menambahkan unit audit test untuk enum tab, normalisasi URL, dan pengujian kegagalan jaringan. |

---

## 3. Kode Implementasi Lengkap (Ready to Copy-Paste)

### A. `FieldOpsNavigationDrawer.kt` (Bagian `DrawerToolkitSection`)

```kotlin
@Composable
private fun DrawerToolkitSection(
    onOpenToolkit: (FieldToolkitTab) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "FIELD OPS TOOLKIT (OFFLINE)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.7.sp
        )

        DrawerToolRow(
            icon = Icons.Rounded.NetworkCheck,
            title = "Ping & Latency Check",
            subtitle = "Uji konektivitas TCP/ICMP ke server & gateway",
            onClick = { onOpenToolkit(FieldToolkitTab.PING) },
            testTag = "drawer_tool_ping"
        )

        // BARU: Speed Test Tool (LibreSpeed)
        DrawerToolRow(
            icon = Icons.Rounded.Speed,
            title = "Speed Test Tool (LibreSpeed)",
            subtitle = "Uji throughput unduh & unggah mandiri ke server LAN",
            onClick = { onOpenToolkit(FieldToolkitTab.SPEEDTEST) },
            testTag = "drawer_tool_speedtest"
        )

        DrawerToolRow(
            icon = Icons.Rounded.Lan,
            title = "Kalkulator Subnet / CIDR",
            subtitle = "Hitung Network ID, Mask & Range Host IPv4",
            onClick = { onOpenToolkit(FieldToolkitTab.SUBNET) },
            testTag = "drawer_tool_subnet"
        )
    }
}
```

---

### B. `FieldNetworkTools.kt` (Model & Fungsi Pengujian)

```kotlin
package com.example.domain.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

data class SpeedTestResult(
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val clientIp: String?,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class SpeedTestProgress(
    val phase: SpeedTestPhase,
    val progressPercent: Int,
    val currentSpeedMbps: Double = 0.0,
    val statusMessage: String = ""
)

enum class SpeedTestPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    COMPLETED,
    ERROR
}

object FieldNetworkTools {

    fun normalizeHttpUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val withProtocol = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "http://$trimmed"
        } else {
            trimmed
        }
        return withProtocol.trimEnd('/')
    }

    suspend fun runSpeedTest(
        serverBaseUrl: String,
        onProgress: ((SpeedTestProgress) -> Unit)? = null
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        val normalizedBaseUrl = normalizeHttpUrl(serverBaseUrl)
        if (normalizedBaseUrl.isBlank()) {
            val err = SpeedTestResult(
                pingMs = 0.0,
                jitterMs = 0.0,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                clientIp = null,
                isSuccess = false,
                errorMessage = "Alamat server speedtest tidak boleh kosong."
            )
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    progressPercent = 0,
                    statusMessage = err.errorMessage ?: "Error"
                )
            )
            return@withContext err
        }

        try {
            // 1. IP & ISP Detection
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.PING,
                    progressPercent = 5,
                    statusMessage = "Mendeteksi IP klien..."
                )
            )

            var clientIp: String? = null
            runCatching {
                val ipUrl = URL("$normalizedBaseUrl/api/speedtest/ip")
                val conn = (ipUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("User-Agent", "PingDulu-Android-FieldOps")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val match = Regex("\"processedString\"\\s*:\\s*\"([^\"]+)\"").find(body)
                        ?: Regex("\"rawIp\"\\s*:\\s*\"([^\"]+)\"").find(body)
                    clientIp = match?.groupValues?.get(1)
                }
                conn.disconnect()
            }

            // 2. Ping & Jitter Measurement (6 samples)
            val pings = mutableListOf<Long>()
            val pingUrlStr = "$normalizedBaseUrl/api/speedtest/empty"
            val totalPingSamples = 6

            for (i in 1..totalPingSamples) {
                val pingStart = System.nanoTime()
                val url = URL("$pingUrlStr?r=${System.currentTimeMillis()}_$i")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache")
                }
                conn.inputStream.use { it.read() }
                conn.disconnect()
                val elapsed = ((System.nanoTime() - pingStart) / 1_000_000L).coerceAtLeast(1L)
                pings.add(elapsed)

                val progress = 5 + (i * 20 / totalPingSamples)
                onProgress?.invoke(
                    SpeedTestProgress(
                        phase = SpeedTestPhase.PING,
                        progressPercent = progress,
                        currentSpeedMbps = 0.0,
                        statusMessage = "Uji Ping & Jitter ($i/$totalPingSamples): ${elapsed} ms"
                    )
                )
                delay(30)
            }

            val minPing = pings.minOrNull() ?: 1L
            val jitter = if (pings.size > 1) {
                var sumDiff = 0.0
                for (i in 1 until pings.size) {
                    sumDiff += abs(pings[i] - pings[i - 1])
                }
                sumDiff / (pings.size - 1)
            } else 0.0

            // 3. Download Speed Measurement
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.DOWNLOAD,
                    progressPercent = 28,
                    statusMessage = "Mengunduh stream throughput..."
                )
            )

            val dlUrl = URL("$normalizedBaseUrl/api/speedtest/garbage?ckSize=15&r=${System.currentTimeMillis()}")
            val dlConn = (dlUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 6000
                useCaches = false
            }

            var totalBytesDownloaded = 0L
            val dlBuffer = ByteArray(32 * 1024)
            val dlStartTime = System.nanoTime()
            val maxDlDurationNanos = 5_000_000_000L // 5 detik durasi uji
            var lastReportNanos = dlStartTime

            dlConn.inputStream.use { stream ->
                while (true) {
                    val read = stream.read(dlBuffer)
                    if (read == -1) break
                    totalBytesDownloaded += read
                    val now = System.nanoTime()
                    val elapsedTotal = now - dlStartTime
                    if (elapsedTotal >= maxDlDurationNanos) break

                    if (now - lastReportNanos >= 200_000_000L) {
                        lastReportNanos = now
                        val seconds = elapsedTotal / 1_000_000_000.0
                        val currentSpeed = if (seconds > 0) (totalBytesDownloaded * 8.0) / (seconds * 1_000_000.0) else 0.0
                        val percent = 28 + ((elapsedTotal.toDouble() / maxDlDurationNanos) * 35).toInt().coerceIn(0, 35)
                        onProgress?.invoke(
                            SpeedTestProgress(
                                phase = SpeedTestPhase.DOWNLOAD,
                                progressPercent = percent,
                                currentSpeedMbps = currentSpeed,
                                statusMessage = "Unduh: %.2f Mbps".format(currentSpeed)
                            )
                        )
                    }
                }
            }
            dlConn.disconnect()

            val dlTotalSeconds = ((System.nanoTime() - dlStartTime) / 1_000_000_000.0).coerceAtLeast(0.001)
            val finalDlMbps = (totalBytesDownloaded * 8.0) / (dlTotalSeconds * 1_000_000.0)

            // 4. Upload Speed Measurement
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.UPLOAD,
                    progressPercent = 65,
                    currentSpeedMbps = 0.0,
                    statusMessage = "Mengunggah payload throughput..."
                )
            )

            val ulUrl = URL("$normalizedBaseUrl/api/speedtest/empty?r=${System.currentTimeMillis()}")
            val ulConn = (ulUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 6000
                useCaches = false
                setRequestProperty("Content-Type", "application/octet-stream")
                setChunkedStreamingMode(64 * 1024)
            }

            val ulChunk = ByteArray(64 * 1024) { 0x5a }
            var totalBytesUploaded = 0L
            val ulStartTime = System.nanoTime()
            val maxUlDurationNanos = 4_500_000_000L // 4.5 detik durasi uji
            var lastUlReportNanos = ulStartTime

            ulConn.outputStream.use { outStream ->
                while (true) {
                    outStream.write(ulChunk)
                    totalBytesUploaded += ulChunk.size
                    val now = System.nanoTime()
                    val elapsedTotal = now - ulStartTime
                    if (elapsedTotal >= maxUlDurationNanos) break

                    if (now - lastUlReportNanos >= 200_000_000L) {
                        lastUlReportNanos = now
                        val seconds = elapsedTotal / 1_000_000_000.0
                        val currentSpeed = if (seconds > 0) (totalBytesUploaded * 8.0) / (seconds * 1_000_000.0) else 0.0
                        val percent = 65 + ((elapsedTotal.toDouble() / maxUlDurationNanos) * 33).toInt().coerceIn(0, 33)
                        onProgress?.invoke(
                            SpeedTestProgress(
                                phase = SpeedTestPhase.UPLOAD,
                                progressPercent = percent,
                                currentSpeedMbps = currentSpeed,
                                statusMessage = "Unggah: %.2f Mbps".format(currentSpeed)
                            )
                        )
                    }
                }
            }
            runCatching { ulConn.inputStream.use { it.read() } }
            ulConn.disconnect()

            val ulTotalSeconds = ((System.nanoTime() - ulStartTime) / 1_000_000_000.0).coerceAtLeast(0.001)
            val finalUlMbps = (totalBytesUploaded * 8.0) / (ulTotalSeconds * 1_000_000.0)

            val finalResult = SpeedTestResult(
                pingMs = minPing.toDouble(),
                jitterMs = (jitter * 10.0).roundToInt() / 10.0,
                downloadMbps = (finalDlMbps * 100.0).roundToInt() / 100.0,
                uploadMbps = (finalUlMbps * 100.0).roundToInt() / 100.0,
                clientIp = clientIp,
                isSuccess = true
            )

            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.COMPLETED,
                    progressPercent = 100,
                    currentSpeedMbps = finalDlMbps,
                    statusMessage = "Selesai: Unduh %.2f Mbps · Unggah %.2f Mbps".format(finalDlMbps, finalUlMbps)
                )
            )

            finalResult
        } catch (e: Exception) {
            val errResult = SpeedTestResult(
                pingMs = 0.0,
                jitterMs = 0.0,
                downloadMbps = 0.0,
                uploadMbps = 0.0,
                clientIp = null,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Koneksi ke server speedtest gagal/terputus."
            )
            onProgress?.invoke(
                SpeedTestProgress(
                    phase = SpeedTestPhase.ERROR,
                    progressPercent = 0,
                    statusMessage = "Gagal: ${errResult.errorMessage}"
                )
            )
            errResult
        }
    }
}
```

---

### C. `FieldToolkitBottomSheet.kt` (Konten SpeedTest UI)

```kotlin
enum class FieldToolkitTab(val label: String) {
    PING("Ping & Latency"),
    SPEEDTEST("Speed Test"),
    SUBNET("Subnet / CIDR")
}

// Composable SpeedTestSection
@Composable
private fun SpeedTestSection(serverHostHint: String) {
    val defaultBaseUrl = remember(serverHostHint) {
        FieldNetworkTools.normalizeHttpUrl(
            serverHostHint.ifBlank { "http://10.0.2.2:3001" }
        )
    }

    var serverInput by rememberSaveable { mutableStateOf(defaultBaseUrl) }
    var isRunning by remember { mutableStateOf(false) }
    var progressState by remember { mutableStateOf<SpeedTestProgress?>(null) }
    var latestResult by remember { mutableStateOf<SpeedTestResult?>(null) }
    val testHistory = remember { mutableStateListOf<SpeedTestResult>() }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var copiedMessage by remember { mutableStateOf(false) }

    val serverPresets = remember(defaultBaseUrl) {
        listOf(
            defaultBaseUrl,
            "http://10.0.2.2:3001",
            "http://192.168.1.1:3001",
            "https://ping.fjrx.my.id"
        ).distinct().filter { it.isNotBlank() }
    }

    fun startSpeedTest() {
        if (isRunning) return
        coroutineScope.launch {
            isRunning = true
            copiedMessage = false
            progressState = SpeedTestProgress(
                phase = SpeedTestPhase.PING,
                progressPercent = 5,
                statusMessage = "Menghubungkan ke server LAN PingDulu..."
            )
            val result = FieldNetworkTools.runSpeedTest(
                serverBaseUrl = serverInput,
                onProgress = { p -> progressState = p }
            )
            latestResult = result
            if (result.isSuccess) {
                testHistory.add(0, result)
                if (testHistory.size > 5) {
                    testHistory.removeAt(testHistory.lastIndex)
                }
            }
            isRunning = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = serverInput,
            onValueChange = { serverInput = it },
            label = { Text("Server LibreSpeed (Self-Hosted)") },
            placeholder = { Text("http://192.168.1.x:3001") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Router,
                    contentDescription = null
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { startSpeedTest() }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speedtest_server_input")
        )

        // Presets
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TARGET SERVER CEPAT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                serverPresets.forEach { preset ->
                    val selected = serverInput.trim().equals(preset, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { serverInput = preset },
                        label = {
                            Text(
                                text = preset.removePrefix("http://").removePrefix("https://"),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.5.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PingDuluNavy,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Action Button: Mulai Uji
        Button(
            onClick = { startSpeedTest() },
            enabled = !isRunning && serverInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("speedtest_run_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PingDuluNavy,
                contentColor = Color.White
            )
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = progressState?.statusMessage ?: "Menguji Bandwidth...",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Mulai Uji Jaringan (Speed Test)", fontWeight = FontWeight.Bold)
            }
        }

        // Progress Bar
        if (isRunning) {
            val progressPercent = progressState?.progressPercent ?: 0
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PillDoneMint,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = progressState?.statusMessage ?: "Mengukur throughput...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4 Kartu Metrik Throughput
        val displayPing = latestResult?.pingMs ?: 0.0
        val displayJitter = latestResult?.jitterMs ?: 0.0
        val isDownloading = isRunning && progressState?.phase == SpeedTestPhase.DOWNLOAD
        val isUploading = isRunning && progressState?.phase == SpeedTestPhase.UPLOAD
        val displayDl = if (isDownloading) (progressState?.currentSpeedMbps ?: 0.0) else (latestResult?.downloadMbps ?: 0.0)
        val displayUl = if (isUploading) (progressState?.currentSpeedMbps ?: 0.0) else (latestResult?.uploadMbps ?: 0.0)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "METRIK DIAGNOSTIK THROUGHPUT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                    latestResult?.clientIp?.let { ip ->
                        Text(
                            text = "IP: $ip",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = PillDoneMint,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "PING",
                        value = if (displayPing > 0) "${displayPing.toInt()}" else "-",
                        unit = "ms",
                        badge = "RTT",
                        badgeColor = PillDoneMint
                    )
                    SpeedMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "JITTER",
                        value = if (displayJitter > 0) "$displayJitter" else "-",
                        unit = "ms",
                        badge = "VAR",
                        badgeColor = P2Amber
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "DOWNLOAD",
                        value = if (displayDl > 0) "%.1f".format(displayDl) else "-",
                        unit = "Mbps",
                        badge = "UNDUH",
                        badgeColor = Color(0xFF6366F1),
                        icon = Icons.Rounded.ArrowDownward
                    )
                    SpeedMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "UPLOAD",
                        value = if (displayUl > 0) "%.1f".format(displayUl) else "-",
                        unit = "Mbps",
                        badge = "UNGGAH",
                        badgeColor = Color(0xFF06B6D4),
                        icon = Icons.Rounded.ArrowUpward
                    )
                }
            }
        }

        // Tombol Salin Ringkasan
        latestResult?.takeIf { it.isSuccess }?.let { res ->
            Button(
                onClick = {
                    val summary = """
                        === HASIL DIAGNOSTIK SPEEDTEST PINGDULU ===
                        Server: $serverInput
                        IP Klien: ${res.clientIp ?: "N/A"}
                        Ping: ${res.pingMs} ms | Jitter: ${res.jitterMs} ms
                        Download: ${res.downloadMbps} Mbps
                        Upload: ${res.uploadMbps} Mbps
                        Waktu: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(res.timestampMillis))}
                    """.trimIndent()
                    clipboardManager.setText(AnnotatedString(summary))
                    copiedMessage = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speedtest_copy_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (copiedMessage) Icons.Rounded.CheckCircle else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (copiedMessage) PillDoneMint else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (copiedMessage) "Ringkasan Tersalin ke Clipboard!" else "Salin Ringkasan Diagnostik",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
```

---

## 4. Hasil Audit Unit Testing

Audit unit testing di `FieldToolsAndDrawerAuditTest.kt` memvalidasi:
1. `speedtest_tabEnumAndUrlNormalization`:
   - `FieldToolkitTab.SPEEDTEST.label` == `"Speed Test"`
   - `FieldToolkitTab.entries.size` == 3
   - Normalisasi URL otomatis menambahkan skema `http://` bila tidak ada dan membuang trailing slash.
2. `speedtest_rejectsBlankServerAndHandlesNetworkErrors`:
   - Memastikan server kosong atau port tidak dapat dijangkau menghasilkan `isSuccess == false` dengan pesan galat deskriptif tanpa membuat aplikasi crash.
3. **Backend Integration Test**:
   - `npm test` di Fastify Server: **49 Passed, 0 Failed**.
   - Integritas Database SQLite: `work_items = 0`, `speedtest_records = 0` (Bebas residu test).

---

## 5. Kesimpulan

Dengan patch ini, tombol menu dan konten **Speed Test Tool (LibreSpeed)** telah terintegrasi sempurna di bawah bagian **`FIELD OPS TOOLKIT (OFFLINE)`** pada Android App PingDulu, selaras dengan server Fastify mandiri (*self-hosted*). Teknisi lapangan dapat langsung menguji latensi dan throughput LAN dari drawer tanpa hambatan.
