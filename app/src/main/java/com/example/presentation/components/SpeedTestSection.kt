package com.example.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DateEpochUtil
import com.example.domain.tools.FieldNetworkTools
import com.example.domain.tools.SpeedTestPhase
import com.example.domain.tools.SpeedTestProgress
import com.example.domain.tools.SpeedTestResult
import com.example.ui.theme.CbnCobaltBlue
import com.example.ui.theme.CbnCobaltLight
import com.example.ui.theme.CbnDeepSlate
import com.example.ui.theme.CbnSlateBorder
import com.example.ui.theme.CbnSlateMuted
import com.example.ui.theme.CbnSparklineCyan
import com.example.ui.theme.CbnTrackSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.PillBacklogLilac
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PillProgressSky
import com.example.ui.theme.PingDuluNavy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SpeedTestSection(
    serverHostHint: String,
    authToken: String?
) {
    val defaultBaseUrl = remember(serverHostHint) {
        FieldNetworkTools.normalizeHttpUrl(
            serverHostHint.ifBlank { "https://ping.fjrx.my.id" }
        )
    }

    var serverInput by rememberSaveable(defaultBaseUrl) { mutableStateOf(defaultBaseUrl) }
    var isRunning by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var progressState by remember { mutableStateOf<SpeedTestProgress?>(null) }
    var latestResult by remember { mutableStateOf<SpeedTestResult?>(null) }
    val testHistory = remember { mutableStateListOf<SpeedTestResult>() }
    var copiedMessage by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasBearerToken = !authToken.isNullOrBlank()

    // Ensure active sockets abort immediately when the BottomSheet is dismissed or tab switches
    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            activeJob = null
        }
    }

    val serverPresets = remember(defaultBaseUrl) {
        listOf(
            defaultBaseUrl,
            "https://ping.fjrx.my.id",
            "http://192.168.1.1:3001"
        ).distinct().filter { it.isNotBlank() }
    }

    fun stopSpeedTest() {
        activeJob?.cancel()
        activeJob = null
        isRunning = false
        progressState = SpeedTestProgress(
            phase = SpeedTestPhase.IDLE,
            progressPercent = 0,
            statusMessage = "Pengujian dihentikan oleh teknisi."
        )
    }

    fun startSpeedTest() {
        if (isRunning || serverInput.isBlank()) return
        copiedMessage = false
        latestResult = null
        isRunning = true
        progressState = SpeedTestProgress(
            phase = SpeedTestPhase.PING,
            progressPercent = 4,
            statusMessage = "Mengukur Ping..."
        )
        activeJob = coroutineScope.launch {
            try {
                val result = FieldNetworkTools.runSpeedTest(
                    serverBaseUrl = serverInput,
                    authToken = authToken,
                    onProgress = { progress ->
                        withContext(Dispatchers.Main.immediate) {
                            progressState = progress
                        }
                    }
                )
                latestResult = result
                if (result.isSuccess) {
                    testHistory.add(0, result)
                    if (testHistory.size > 5) {
                        testHistory.removeAt(testHistory.lastIndex)
                    }
                }
            } catch (_: CancellationException) {
                // Clean cancellation via Stop button or sheet close
            } finally {
                isRunning = false
                activeJob = null
            }
        }
    }

    val activePhase = when {
        isRunning -> progressState?.phase ?: SpeedTestPhase.PING
        latestResult?.isSuccess == true -> SpeedTestPhase.COMPLETED
        latestResult?.isSuccess == false -> SpeedTestPhase.ERROR
        else -> SpeedTestPhase.IDLE
    }

    val liveGaugeMbps = when {
        isRunning -> progressState?.currentSpeedMbps ?: 0.0
        latestResult?.isSuccess == true -> latestResult?.downloadMbps ?: 0.0
        else -> 0.0
    }

    val displayClientIp = latestResult?.clientIp ?: progressState?.clientIp
    val displayIsp = latestResult?.ispName ?: progressState?.ispName
    val displayPing = if (isRunning) {
        progressState?.pingMs ?: 0.0
    } else {
        latestResult?.takeIf { it.isSuccess }?.pingMs ?: 0.0
    }
    val displayJitter = if (isRunning) {
        progressState?.jitterMs ?: 0.0
    } else {
        latestResult?.takeIf { it.isSuccess }?.jitterMs ?: 0.0
    }
    val displayDl = if (isRunning) {
        progressState?.downloadMbps ?: 0.0
    } else {
        latestResult?.takeIf { it.isSuccess }?.downloadMbps ?: 0.0
    }
    val displayUl = if (isRunning) {
        progressState?.uploadMbps ?: 0.0
    } else {
        latestResult?.takeIf { it.isSuccess }?.uploadMbps ?: 0.0
    }
    val dlSparklineSamples = if (isRunning) {
        progressState?.downloadSamples.orEmpty()
    } else {
        latestResult?.takeIf { it.isSuccess }?.downloadSamples.orEmpty()
    }
    val ulSparklineSamples = if (isRunning) {
        progressState?.uploadSamples.orEmpty()
    } else {
        latestResult?.takeIf { it.isSuccess }?.uploadSamples.orEmpty()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. CBN / Ookla 266° Canvas Speedometer Gauge
        CbnSpeedometerGauge(
            speedMbps = liveGaugeMbps,
            phase = activePhase,
            statusMessage = progressState?.statusMessage.orEmpty()
        )

        // 2. 4-Stage Step Progress Indicator (Ping -> Jitter -> Download -> Upload)
        SpeedTestStepProgressBar(phase = activePhase)

        // 3. Glassmorphic 2x2 CBN Metric Cards (with live Mini Sparkline Area Charts on DL & UL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CbnMetricCard(
                modifier = Modifier.weight(1f),
                label = "PING",
                value = if (displayPing > 0.0) String.format(Locale.US, "%.1f", displayPing) else "-",
                unit = "ms",
                icon = Icons.Rounded.SwapHoriz,
                accentColor = PillDoneMint,
                isHighlighted = isRunning && activePhase == SpeedTestPhase.PING
            )
            CbnMetricCard(
                modifier = Modifier.weight(1f),
                label = "JITTER",
                value = if (displayJitter > 0.0) String.format(Locale.US, "%.1f", displayJitter) else "-",
                unit = "ms",
                icon = Icons.Rounded.Waves,
                accentColor = P2Amber,
                isHighlighted = isRunning && activePhase == SpeedTestPhase.JITTER
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CbnMetricCard(
                modifier = Modifier.weight(1f),
                label = "DOWNLOAD",
                value = if (displayDl > 0.0) String.format(Locale.US, "%.2f", displayDl) else "-",
                unit = "Mbps",
                icon = Icons.Rounded.ArrowDownward,
                accentColor = CbnCobaltBlue,
                isHighlighted = isRunning && activePhase == SpeedTestPhase.DOWNLOAD,
                sparklineSamples = dlSparklineSamples
            )
            CbnMetricCard(
                modifier = Modifier.weight(1f),
                label = "UPLOAD",
                value = if (displayUl > 0.0) String.format(Locale.US, "%.2f", displayUl) else "-",
                unit = "Mbps",
                icon = Icons.Rounded.ArrowUpward,
                accentColor = CbnSparklineCyan,
                isHighlighted = isRunning && activePhase == SpeedTestPhase.UPLOAD,
                sparklineSamples = ulSparklineSamples
            )
        }

        // 4. Primary Action Button (CBN Gradient "Mulai Pengujian" vs Crimson "Hentikan Pengujian")
        if (isRunning) {
            Button(
                onClick = { stopSpeedTest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp)
                    .testTag("speedtest_stop_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = P1Crimson,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = "Hentikan pengujian",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Hentikan Pengujian",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(CbnCobaltBlue, CbnCobaltLight)
                        )
                    )
            ) {
                Button(
                    onClick = { startSpeedTest() },
                    enabled = serverInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp)
                        .testTag("speedtest_run_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        disabledContainerColor = CbnTrackSlate,
                        disabledContentColor = CbnSlateMuted
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Mulai Pengujian",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 5. Explicit Error Banner when test fails (HTTP 401/404/413/500, DNS, Timeout, etc.)
        val errorMessage = latestResult?.takeIf { !it.isSuccess }?.errorMessage
        AnimatedVisibility(visible = !isRunning && !errorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = P1Crimson.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, P1Crimson.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speedtest_error_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = P1Crimson,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Pengujian Speed Test Gagal",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = P1Crimson
                        )
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 6. Network Footer (Client IP / ISP + Target Server + Server Record Save Badge)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CbnDeepSlate,
            border = BorderStroke(1.dp, CbnSlateBorder.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speedtest_network_footer")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = CbnSparklineCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        val clientLabel = when {
                            !displayIsp.isNullOrBlank() && !displayClientIp.isNullOrBlank() ->
                                "$displayIsp ($displayClientIp)"
                            !displayClientIp.isNullOrBlank() -> "IP Klien: $displayClientIp"
                            else -> "IP Klien terdeteksi saat pengujian"
                        }
                        Text(
                            text = clientLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    latestResult?.takeIf { it.isSuccess }?.let { res ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (res.savedToServer) {
                                PillDoneMint.copy(alpha = 0.16f)
                            } else {
                                CbnTrackSlate
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (res.savedToServer) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                    contentDescription = null,
                                    tint = if (res.savedToServer) PillDoneMint else CbnSlateMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (res.savedToServer) "Tersimpan" else "Lokal",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.savedToServer) PillDoneMint else CbnSlateMuted
                                )
                            }
                        }
                    }
                }

                val targetHostLabel = serverInput
                    .trim()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .ifBlank { "ping.fjrx.my.id" }
                Text(
                    text = "Server: PingDulu / $targetHostLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = CbnSlateMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 7. Mode & Auth Status Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (hasBearerToken) {
                PillDoneMint.copy(alpha = 0.12f)
            } else {
                P2Amber.copy(alpha = 0.14f)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (hasBearerToken) {
                    PillDoneMint.copy(alpha = 0.40f)
                } else {
                    P2Amber.copy(alpha = 0.45f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speedtest_auth_badge")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (hasBearerToken) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = if (hasBearerToken) PillDoneMint else P2Amber,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasBearerToken) {
                            "Self-Hosted / LAN · Bearer Auth Aktif"
                        } else {
                            "Self-Hosted / LAN · Tanpa Token Bearer"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (hasBearerToken) {
                            "Header Authorization otomatis dikirim ke /api/speedtest/*"
                        } else {
                            "Login di Pengaturan bila server mewajibkan header Bearer"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }

        // 8. Server Host Input & Quick Presets
        OutlinedTextField(
            value = serverInput,
            onValueChange = { serverInput = it },
            enabled = !isRunning,
            label = { Text("Server LibreSpeed (Self-Hosted / LAN)") },
            placeholder = { Text("https://ping.fjrx.my.id atau http://192.168.1.x:3001") },
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            serverPresets.forEach { preset ->
                val selected = serverInput.trim().trimEnd('/').equals(preset, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    enabled = !isRunning,
                    onClick = { serverInput = preset },
                    label = {
                        Text(
                            text = preset.removePrefix("https://").removePrefix("http://"),
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

        // 9. Copy Diagnostic Summary (WIB)
        latestResult?.takeIf { it.isSuccess }?.let { res ->
            Button(
                onClick = {
                    val summary = FieldNetworkTools.formatSpeedTestSummary(res)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("PingDulu SpeedTest", summary))
                    copiedMessage = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("speedtest_copy_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
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
                    text = if (copiedMessage) "Ringkasan WIB Tersalin ke Clipboard!" else "Salin Ringkasan Diagnostik (WIB)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 10. Local Test History (up to 5 recent runs)
        if (testHistory.isNotEmpty()) {
            val timeFormatter = remember {
                SimpleDateFormat("HH:mm:ss 'WIB'", Locale.forLanguageTag("id-ID")).apply {
                    timeZone = DateEpochUtil.JAKARTA_TIME_ZONE
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speedtest_history_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "RIWAYAT UJI TERAKHIR (${testHistory.size}/5)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.6.sp
                        )
                    }

                    testHistory.forEachIndexed { idx, entry ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.serverUrl
                                        .removePrefix("https://")
                                        .removePrefix("http://")
                                        .ifBlank { "Server" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = timeFormatter.format(Date(entry.timestampMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.0fms", entry.pingMs),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PillDoneMint
                                )
                                Text(
                                    text = String.format(Locale.US, "↓%.1f", entry.downloadMbps),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PillProgressSky
                                )
                                Text(
                                    text = String.format(Locale.US, "↑%.1f", entry.uploadMbps),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PillBacklogLilac
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CbnMetricCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    sparklineSamples: List<Float>? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CbnDeepSlate,
        border = BorderStroke(
            width = if (isHighlighted) 1.5.dp else 1.dp,
            color = if (isHighlighted) accentColor else CbnSlateBorder.copy(alpha = 0.75f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CbnSlateMuted,
                    letterSpacing = 0.5.sp
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = CbnSlateMuted,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            if (sparklineSamples != null) {
                SpeedSparklineChart(
                    samples = sparklineSamples,
                    accentColor = accentColor,
                    modifier = Modifier.testTag("sparkline_${label.lowercase(Locale.US)}")
                )
            }
        }
    }
}
