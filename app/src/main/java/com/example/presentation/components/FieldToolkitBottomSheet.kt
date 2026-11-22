package com.example.presentation.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.tools.FieldNetworkTools
import com.example.domain.tools.PingProbeResult
import com.example.domain.tools.ProbeStatus
import com.example.domain.tools.SubnetCalculationResult
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class FieldToolkitTab(val label: String) {
    PING("Ping"),
    SPEEDTEST("Speed Test"),
    SUBNET("Subnet"),
    RJ45_POE("RJ45 & PoE")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldToolkitBottomSheet(
    initialTab: FieldToolkitTab,
    serverHostHint: String,
    authToken: String? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("toolkit_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PingDuluNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedTab) {
                                FieldToolkitTab.PING -> Icons.Rounded.NetworkCheck
                                FieldToolkitTab.SPEEDTEST -> Icons.Rounded.Speed
                                FieldToolkitTab.SUBNET -> Icons.Rounded.Lan
                                FieldToolkitTab.RJ45_POE -> Icons.Rounded.Cable
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Field Ops Toolkit",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Utilitas jaringan & LAN/offline untuk teknisi lapangan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("toolkit_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup toolkit"
                    )
                }
            }

            // Horizontally Scrollable 4-Tab Pill Switcher (prevents crowding on 360dp compact screens)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FieldToolkitTab.entries.forEach { tab ->
                        val active = selectedTab == tab
                        val tabTag = when (tab) {
                            FieldToolkitTab.PING -> "toolkit_tab_ping"
                            FieldToolkitTab.SPEEDTEST -> "toolkit_tab_speedtest"
                            FieldToolkitTab.SUBNET -> "toolkit_tab_subnet"
                            FieldToolkitTab.RJ45_POE -> "toolkit_tab_rj45_poe"
                        }
                        val tabIcon = when (tab) {
                            FieldToolkitTab.PING -> Icons.Rounded.NetworkCheck
                            FieldToolkitTab.SPEEDTEST -> Icons.Rounded.Speed
                            FieldToolkitTab.SUBNET -> Icons.Rounded.Lan
                            FieldToolkitTab.RJ45_POE -> Icons.Rounded.Cable
                        }
                        Surface(
                            onClick = { selectedTab = tab },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 84.dp, minHeight = 48.dp)
                                .testTag(tabTag),
                            shape = RoundedCornerShape(11.dp),
                            color = if (active) PingDuluNavy else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = null,
                                    tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                FieldToolkitTab.PING -> PingProbeSection(serverHostHint = serverHostHint)
                FieldToolkitTab.SPEEDTEST -> SpeedTestSection(
                    serverHostHint = serverHostHint,
                    authToken = authToken
                )
                FieldToolkitTab.SUBNET -> SubnetCalculatorSection()
                FieldToolkitTab.RJ45_POE -> Rj45PoeReferenceSection()
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PingProbeSection(serverHostHint: String) {
    val cleanedServerHost = remember(serverHostHint) {
        serverHostHint
            .trim()
            .replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .substringBefore("/")
            .substringBefore(":")
            .ifBlank { "ping.fjrx.my.id" }
    }

    var hostInput by rememberSaveable { mutableStateOf(cleanedServerHost) }
    var selectedPort by rememberSaveable { mutableIntStateOf(443) }
    var activeProbeJob by remember { mutableStateOf<Job?>(null) }
    var latestResult by remember { mutableStateOf<PingProbeResult?>(null) }
    val probeHistory = remember { mutableStateListOf<PingProbeResult>() }
    val coroutineScope = rememberCoroutineScope()
    val isProbing = activeProbeJob?.isActive == true

    val hostPresets = remember(cleanedServerHost) {
        listOf(cleanedServerHost, "1.1.1.1", "8.8.8.8", "192.168.1.1").distinct()
    }
    val portPresets = remember { listOf(443, 80, 53, 22) }

    fun executeOrCancelProbe() {
        if (isProbing) {
            activeProbeJob?.cancel()
            activeProbeJob = null
            return
        }
        activeProbeJob = coroutineScope.launch {
            try {
                val result = FieldNetworkTools.probeHost(
                    rawHost = hostInput,
                    port = selectedPort,
                    attempts = 3,
                    timeoutMs = 2_000
                )
                latestResult = result
                probeHistory.add(0, result)
                if (probeHistory.size > 5) {
                    probeHistory.removeAt(probeHistory.lastIndex)
                }
            } finally {
                activeProbeJob = null
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = hostInput,
            onValueChange = { hostInput = it },
            label = { Text("Host / Alamat IP Target") },
            placeholder = { Text("Contoh: ping.fjrx.my.id atau 192.168.1.1") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Router,
                    contentDescription = null
                )
            },
            supportingText = {
                Text("Metode: 3× TCP Handshake RTT (DNS diukur terpisah)")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (!isProbing && hostInput.isNotBlank()) executeOrCancelProbe()
            }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ping_host_input")
        )

        // Quick Host Presets
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TARGET CEPAT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hostPresets.forEach { preset ->
                    val selected = hostInput.trim().equals(preset, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { hostInput = preset },
                        label = {
                            Text(
                                text = preset,
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

        // Port Presets
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "PORT TCP PROBE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                portPresets.forEach { port ->
                    val selected = selectedPort == port
                    val portLabel = when (port) {
                        443 -> "443 HTTPS"
                        80 -> "80 HTTP"
                        53 -> "53 DNS"
                        22 -> "22 SSH"
                        else -> port.toString()
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { selectedPort = port },
                        label = {
                            Text(
                                text = portLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
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

        // Action Button (Start / Cancel)
        Button(
            onClick = { executeOrCancelProbe() },
            enabled = isProbing || hostInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("ping_run_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isProbing) P1Crimson else PingDuluNavy,
                contentColor = Color.White
            )
        ) {
            if (isProbing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("Menguji (3×) · Ketuk Batal", fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Uji TCP Latency (3×)", fontWeight = FontWeight.Bold)
            }
        }

        // Latest Result Card
        latestResult?.let { result ->
            PingResultCard(result = result)
        }

        // Probe History
        if (probeHistory.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "RIWAYAT UJI TERAKHIR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                probeHistory.drop(1).forEach { entry ->
                    val dotColor = when (entry.status) {
                        ProbeStatus.REACHABLE -> PillDoneMint
                        ProbeStatus.PARTIAL_LOSS, ProbeStatus.PORT_CLOSED -> P2Amber
                        else -> P1Crimson
                    }
                    val rightLabel = when (entry.status) {
                        ProbeStatus.REACHABLE -> FieldNetworkTools.formatLatencyMs(entry.avgLatencyMs)
                        ProbeStatus.PARTIAL_LOSS -> "${entry.successCount}/${entry.totalAttempts} · ${FieldNetworkTools.formatLatencyMs(entry.avgLatencyMs)}"
                        ProbeStatus.PORT_CLOSED -> "RST (${FieldNetworkTools.formatLatencyMs(entry.avgLatencyMs)})"
                        else -> entry.status.badgeLabel
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = "${entry.host}:${entry.port}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = rightLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = dotColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PingResultCard(result: PingProbeResult) {
    val extendedColors = PingDuluTheme.extendedColors
    val badgeColors = when (result.status) {
        ProbeStatus.REACHABLE -> extendedColors.syncOnline
        ProbeStatus.PARTIAL_LOSS, ProbeStatus.PORT_CLOSED -> extendedColors.priorityP2
        else -> extendedColors.priorityP1
    }
    val statusIcon = when (result.status) {
        ProbeStatus.REACHABLE -> Icons.Rounded.CheckCircle
        ProbeStatus.PARTIAL_LOSS, ProbeStatus.PORT_CLOSED -> Icons.Rounded.WarningAmber
        else -> Icons.Rounded.ErrorOutline
    }
    val headlineLabel = when (result.status) {
        ProbeStatus.REACHABLE -> "TERBUKA · ${result.successCount}/${result.totalAttempts} OK"
        ProbeStatus.PARTIAL_LOSS -> "TIDAK STABIL · ${result.successCount}/${result.totalAttempts} OK"
        ProbeStatus.PORT_CLOSED -> "HOST HIDUP · PORT TERTUTUP"
        else -> result.status.badgeLabel
    }
    val primaryMetricText = when (result.status) {
        ProbeStatus.REACHABLE, ProbeStatus.PARTIAL_LOSS -> FieldNetworkTools.formatLatencyMs(result.avgLatencyMs)
        ProbeStatus.PORT_CLOSED -> "RST ${FieldNetworkTools.formatLatencyMs(result.avgLatencyMs)}"
        else -> "--"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ping_result_card"),
        shape = RoundedCornerShape(16.dp),
        color = badgeColors.container,
        border = BorderStroke(1.dp, badgeColors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = badgeColors.content,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = headlineLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeColors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = primaryMetricText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = badgeColors.content
                )
            }

            Text(
                text = result.detailMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Raw Per-Attempt Samples (Transparent — no hidden averaging)
            if (result.attempts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    result.attempts.forEach { attempt ->
                        val chipText = when (attempt.status) {
                            ProbeStatus.REACHABLE -> "#${attempt.sequence} ${FieldNetworkTools.formatLatencyMs(attempt.latencyMs)}"
                            ProbeStatus.PORT_CLOSED -> "#${attempt.sequence} RST ${FieldNetworkTools.formatLatencyMs(attempt.latencyMs)}"
                            ProbeStatus.TIMEOUT -> "#${attempt.sequence} Timeout"
                            else -> "#${attempt.sequence} Gagal"
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, badgeColors.border.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = chipText,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Min / Avg / Max + Separate DNS timing
            if (result.avgLatencyMs != null) {
                Text(
                    text = "Min ${FieldNetworkTools.formatLatencyMs(result.minLatencyMs)} · " +
                        "Avg ${FieldNetworkTools.formatLatencyMs(result.avgLatencyMs)} · " +
                        "Max ${FieldNetworkTools.formatLatencyMs(result.maxLatencyMs)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "IP: ${result.resolvedIp ?: "-"}:${result.port}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = result.dnsLookupMs?.let {
                        "DNS: ${FieldNetworkTools.formatLatencyMs(it)}"
                    } ?: "DNS: IP langsung",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubnetCalculatorSection() {
    var ipInput by rememberSaveable { mutableStateOf("192.168.10.1") }
    var prefixLength by rememberSaveable { mutableIntStateOf(24) }
    val cidrPresets = remember { listOf(24, 26, 27, 28, 29, 30, 16) }

    val result = remember(ipInput, prefixLength) {
        FieldNetworkTools.calculateSubnet(ipInput, prefixLength)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("Alamat IPv4") },
                placeholder = { Text("192.168.10.1") },
                singleLine = true,
                isError = result == null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("subnet_ip_input")
            )

            // CIDR Prefix Stepper
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedIconButton(
                    onClick = { if (prefixLength > 0) prefixLength-- },
                    enabled = prefixLength > 0,
                    modifier = Modifier.testTag("subnet_prefix_minus")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Kurangi prefix CIDR"
                    )
                }
                Text(
                    text = "/$prefixLength",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedIconButton(
                    onClick = { if (prefixLength < 32) prefixLength++ },
                    enabled = prefixLength < 32,
                    modifier = Modifier.testTag("subnet_prefix_plus")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Tambah prefix CIDR"
                    )
                }
            }
        }

        // CIDR Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cidrPresets.forEach { preset ->
                val selected = prefixLength == preset
                FilterChip(
                    selected = selected,
                    onClick = { prefixLength = preset },
                    label = {
                        Text(
                            text = "/$preset",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PingDuluNavy,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        if (result == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = PingDuluTheme.extendedColors.priorityP2.container,
                border = BorderStroke(1.dp, P2Amber.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "Format IPv4 tidak valid. Masukkan 4 oktet bernilai 0–255 (contoh: 10.10.20.1).",
                    style = MaterialTheme.typography.bodySmall,
                    color = PingDuluTheme.extendedColors.priorityP2.content,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            SubnetResultCard(result = result)
        }
    }
}

@Composable
private fun SubnetResultCard(result: SubnetCalculationResult) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subnet_result_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.cidrNotation,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PingDuluTheme.extendedColors.categoryNet.container,
                    border = BorderStroke(1.dp, PingDuluTheme.extendedColors.categoryNet.border)
                ) {
                    Text(
                        text = "${result.usableHostCount} host",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PingDuluTheme.extendedColors.categoryNet.content,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = result.addressType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SubnetMetricRow(label = "Network ID", value = result.networkAddress)
            SubnetMetricRow(label = "Broadcast", value = result.broadcastAddress)
            SubnetMetricRow(label = "Subnet Mask", value = result.subnetMask)
            SubnetMetricRow(label = "Wildcard Mask", value = result.wildcardMask)
            SubnetMetricRow(
                label = "Range Host",
                value = "${result.firstUsableHost} – ${result.lastUsableHost}"
            )
        }
    }
}

@Composable
private fun SubnetMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
