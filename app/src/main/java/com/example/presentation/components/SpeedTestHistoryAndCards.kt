package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.tools.ClientNetworkInfo
import com.example.domain.tools.SpeedTestResult
import com.example.domain.tools.SpeedTestServerRecord
import com.example.domain.tools.SpeedTestTargetMode
import com.example.ui.theme.CbnCobaltBlue
import com.example.ui.theme.CbnDeepSlate
import com.example.ui.theme.CbnSlateBorder
import com.example.ui.theme.CbnSlateMuted
import com.example.ui.theme.CbnSparklineCyan
import com.example.ui.theme.CbnTrackSlate
import com.example.ui.theme.Edge100gBadgeBg
import com.example.ui.theme.Edge100gBadgeText
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.PillBacklogLilac
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PillProgressSky
import com.example.ui.theme.PingDuluNavy
import java.util.Locale

@Composable
internal fun SpeedTestDualModeSelector(
    selectedMode: SpeedTestTargetMode,
    enabled: Boolean,
    onSelectMode: (SpeedTestTargetMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CbnDeepSlate,
        border = BorderStroke(1.dp, CbnSlateBorder.copy(alpha = 0.75f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("speedtest_mode_selector")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SpeedTestTargetMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                val tag = if (mode == SpeedTestTargetMode.PUBLIC_EDGE_100G) {
                    "speedtest_mode_public"
                } else {
                    "speedtest_mode_local"
                }
                val bgColor = when {
                    isSelected && mode == SpeedTestTargetMode.PUBLIC_EDGE_100G -> CbnCobaltBlue
                    isSelected -> PingDuluNavy
                    else -> Color.Transparent
                }
                val contentColor = if (isSelected) Color.White else CbnSlateMuted

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable(
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { onSelectMode(mode) }
                        )
                        .semantics(mergeDescendants = true) {}
                        .testTag(tag),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.shortTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClientDeviceInfoCard(
    clientInfo: ClientNetworkInfo?,
    isDetecting: Boolean,
    fallbackIp: String?,
    fallbackIsp: String?,
    targetMode: SpeedTestTargetMode,
    localServerHost: String,
    latestResult: SpeedTestResult?,
    isSavedToServer: Boolean,
    modifier: Modifier = Modifier
) {
    val ispLine = when {
        isDetecting && clientInfo == null -> "Mendeteksi ISP & lokasi..."
        clientInfo != null && clientInfo.ispWithLocation.isNotBlank() -> clientInfo.ispWithLocation
        !fallbackIsp.isNullOrBlank() -> fallbackIsp
        else -> "ISP Publik • Indonesia"
    }
    val ipLine = when {
        clientInfo != null && clientInfo.isDetectedReal -> clientInfo.ip
        !fallbackIp.isNullOrBlank() -> fallbackIp
        isDetecting -> "Mendeteksi IP..."
        else -> "—"
    }
    val targetLabel = if (targetMode == SpeedTestTargetMode.PUBLIC_EDGE_100G) {
        "Target: Cloudflare Edge 100G (Internet Publik Riil)"
    } else {
        val cleanHost = localServerHost.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .ifBlank { "ping.fjrx.my.id" }
        "Target: Server PingDulu Lokal ($cleanHost)"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CbnDeepSlate,
        border = BorderStroke(1.dp, CbnSlateBorder.copy(alpha = 0.7f)),
        modifier = modifier
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CbnSparklineCyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = "Perangkat klien",
                            tint = CbnSparklineCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = ispLine,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("speedtest_client_isp_text")
                        )
                        Text(
                            text = ipLine,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = CbnSparklineCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("speedtest_client_ip_text")
                        )
                    }
                }

                latestResult?.takeIf { it.isSuccess }?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSavedToServer) {
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
                                imageVector = if (isSavedToServer) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = if (isSavedToServer) PillDoneMint else CbnSlateMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isSavedToServer) "Tersimpan" else "Lokal",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSavedToServer) PillDoneMint else CbnSlateMuted
                            )
                        }
                    }
                }
            }

            Text(
                text = targetLabel,
                style = MaterialTheme.typography.bodySmall,
                color = CbnSlateMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SaveSpeedTestRecordCard(
    userNotes: String,
    onNotesChange: (String) -> Unit,
    targetMode: SpeedTestTargetMode,
    isSaving: Boolean,
    isSaved: Boolean,
    saveError: String?,
    hasAuthToken: Boolean,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("speedtest_save_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (targetMode == SpeedTestTargetMode.PUBLIC_EDGE_100G) {
                        Edge100gBadgeBg
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = targetMode.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (targetMode == SpeedTestTargetMode.PUBLIC_EDGE_100G) {
                            Edge100gBadgeText
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "Simpan Hasil ke Server PingDulu",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = userNotes,
                onValueChange = onNotesChange,
                enabled = !isSaving,
                label = { Text("Catatan Lokasi / Pelanggan (Opsional)") },
                placeholder = { Text("Misal: ODP-12 Blok C / Router Lantai 2") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speedtest_notes_input")
            )

            if (!saveError.isNullOrBlank()) {
                Text(
                    text = saveError,
                    style = MaterialTheme.typography.bodySmall,
                    color = P1Crimson
                )
            }

            Button(
                onClick = onSaveClick,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("speedtest_save_record_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaved) PillDoneMint else PingDuluNavy,
                    contentColor = Color.White
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = when {
                        isSaving -> "Menyimpan ke Server..."
                        isSaved -> "Tersimpan ke Riwayat Server"
                        !hasAuthToken -> "Simpan ke Riwayat (Perlu Login)"
                        else -> "Simpan ke Riwayat"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeedTestServerHistoryCard(
    records: List<SpeedTestServerRecord>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("speedtest_server_history_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                        text = "RIWAYAT SERVER (${records.size}/30)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("speedtest_refresh_history_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Muat ulang riwayat server",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (records.isEmpty() && !isLoading) {
                Text(
                    text = "Belum ada catatan speedtest tersimpan di server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                records.take(15).forEachIndexed { idx, record ->
                    if (idx > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Parsed Target Mode + Real ISP Badges
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val mode = record.targetMode ?: SpeedTestTargetMode.LOCAL_LAN_SERVER
                            val isPublic = mode == SpeedTestTargetMode.PUBLIC_EDGE_100G
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isPublic) Edge100gBadgeBg else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = mode.badgeLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPublic) Edge100gBadgeText else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (!record.ispBadgeLabel.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                ) {
                                    Text(
                                        text = record.ispBadgeLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val metaLine = listOfNotNull(
                                    record.technicianName,
                                    record.clientIp
                                ).joinToString(" · ").ifBlank { "Teknisi Lapangan" }
                                Text(
                                    text = metaLine,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (record.cleanUserNote.isNotBlank()) {
                                    Text(
                                        text = record.cleanUserNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (record.createdAtLabel.isNotBlank()) {
                                    Text(
                                        text = record.createdAtLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.0fms", record.pingMs),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PillDoneMint
                                )
                                Text(
                                    text = String.format(Locale.US, "↓%.1f", record.downloadMbps),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PillProgressSky
                                )
                                Text(
                                    text = String.format(Locale.US, "↑%.1f", record.uploadMbps),
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
internal fun CbnMetricCard(
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
