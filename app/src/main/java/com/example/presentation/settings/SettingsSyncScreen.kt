package com.example.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.SseConnectionState
import com.example.presentation.schedule.ScheduleUiState
import com.example.presentation.schedule.SyncStatusBadge
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.HeroNavyGradient
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.P2AmberTextDark
import com.example.ui.theme.P2AmberTextLight
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.StopwatchMint
import com.example.ui.theme.SyncGreenDark
import com.example.ui.theme.SyncGreenLight
import com.example.domain.model.Stage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsSyncScreen(
    uiState: ScheduleUiState,
    onTriggerSync: () -> Unit,
    onLogout: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSpkHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val completedSpkCount = remember(uiState.allTasks) {
        uiState.allTasks.count { it.stage == Stage.DONE }
    }
    val fixedServerDisplayUrl = remember {
        PingDuluApiService.FIXED_BASE_URL.removeSuffix("/")
    }
    val lastSyncedLabel = remember(uiState.lastSyncedAtMillis) {
        if (uiState.lastSyncedAtMillis <= 0L) {
            "Belum tersinkron"
        } else {
            SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).apply {
                timeZone = com.example.domain.model.DateEpochUtil.JAKARTA_TIME_ZONE
            }.format(Date(uiState.lastSyncedAtMillis))
        }
    }
    val sseDisplayLabel = when (uiState.sseState) {
        SseConnectionState.CONNECTED_LIVE -> "Live Real-Time"
        SseConnectionState.CONNECTING -> "Menghubungkan..."
        SseConnectionState.OFFLINE -> "Offline"
    }
    val serverConnectionBadge = when {
        uiState.sseState == SseConnectionState.CONNECTED_LIVE -> "Terkoneksi"
        uiState.syncStatusBadge == SyncStatusBadge.SYNCING -> "Menyinkronkan"
        uiState.lastSyncedAtMillis > 0L -> "Terkoneksi"
        else -> "Siap Sinkron"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_sync_screen")
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Pengaturan & Sinkronisasi",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Zero-Config Server · Offline-First Room SQLite",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 104.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Account & Permanent Server Identity Card (Zero-Config)
                item(key = "account_server_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(HeroNavyGradient)
                            .padding(18.dp)
                            .testTag("settings_account_server_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(AvatarEmerald)
                                        .border(2.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = uiState.technicianName.trim().take(1).uppercase().ifEmpty { "T" },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "AKUN SEDANG LOGIN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.72f)
                                    )
                                    Text(
                                        text = "${uiState.technicianName} (${uiState.technicianRole})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("settings_logged_in_user")
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White.copy(alpha = 0.16f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.VerifiedUser,
                                            contentDescription = "Sesi terverifikasi",
                                            tint = StopwatchMint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = uiState.shiftStatus.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                                imageVector = Icons.Rounded.Lock,
                                                contentDescription = "Server terkunci permanen",
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Alamat Server Permanen",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(StopwatchMint)
                                            )
                                            Text(
                                                text = serverConnectionBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StopwatchMint,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Text(
                                        text = fixedServerDisplayUrl,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("settings_fixed_server_url")
                                    )
                                }
                            }
                        }
                    }
                }

                // 1B. Riwayat SPK & Laporan Menu Card (moved from Bottom Bar into Settings)
                item(key = "spk_history_menu_card") {
                    Surface(
                        onClick = onOpenSpkHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_spk_button"),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AvatarEmerald.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AssignmentTurnedIn,
                                        contentDescription = "Riwayat SPK & Laporan",
                                        tint = AvatarEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Riwayat SPK & Laporan",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = AvatarEmerald.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "$completedSpkCount SPK",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = AvatarEmerald,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Rekap Surat Perintah Kerja selesai & status sinkronisasi",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Buka Riwayat SPK",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. Offline Sync Status Card
                item(key = "sync_status_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Status Sinkronisasi Dua Arah",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            StatusRow("Status Koneksi", uiState.syncStatusBadge.label)
                            StatusRow("Status SSE Stream", sseDisplayLabel)
                            StatusRow(
                                label = "Auto-Sync Real-Time",
                                value = if (uiState.isNetworkOnline) "Aktif (Otomatis)" else "Menunggu Sinyal",
                                valueColor = if (uiState.isNetworkOnline) {
                                    if (uiState.isDarkTheme) SyncGreenDark else SyncGreenLight
                                } else {
                                    if (uiState.isDarkTheme) P2AmberTextDark else P2AmberTextLight
                                }
                            )
                            StatusRow("Sinkronisasi Terakhir", lastSyncedLabel)
                            StatusRow("Total Tiket Lokal (Room)", "${uiState.allTasks.size} tiket")
                            StatusRow(
                                label = "Antrean Belum Terunggah",
                                value = "${uiState.unsyncedCount} perubahan",
                                valueColor = if (uiState.unsyncedCount > 0) {
                                    if (uiState.isDarkTheme) P2AmberTextDark else P2AmberTextLight
                                } else {
                                    if (uiState.isDarkTheme) SyncGreenDark else SyncGreenLight
                                }
                            )

                            if (!uiState.lastSyncError.isNullOrBlank()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_last_sync_error_banner"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = P1Crimson.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, P1Crimson.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = "Diagnosa error sinkronisasi",
                                            tint = P1Crimson,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Detail sync terakhir: ${uiState.lastSyncError}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = P1Crimson
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Button(
                                onClick = onTriggerSync,
                                enabled = !uiState.isSyncing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_sync_now_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PingDuluNavy,
                                    contentColor = Color.White
                                )
                            ) {
                                if (uiState.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Menyinkronkan...")
                                } else {
                                    Icon(imageVector = Icons.Rounded.CloudSync, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sinkronisasi Sekarang")
                                }
                            }
                        }
                    }
                }

                // 3. Display Preferences (Dark Mode)
                item(key = "theme_prefs_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DarkMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Mode Gelap (Dark Mode)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Nyaman untuk pengerjaan ruang server & shift malam",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.isDarkTheme,
                                onCheckedChange = { onToggleTheme() },
                                modifier = Modifier.testTag("settings_dark_mode_switch")
                            )
                        }
                    }
                }

                // 4. Session Logout Card
                item(key = "session_logout_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, P1Crimson.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Sesi Akun Teknisi",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Keluar akan menghapus token sesi aktif dan cache tiket tersinkron, lalu kembali ke halaman Login PingDulu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (uiState.unsyncedCount > 0) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = P2Amber.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, P2Amber.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = "Peringatan data antrean",
                                            tint = if (uiState.isDarkTheme) P2AmberTextDark else P2AmberTextLight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${uiState.unsyncedCount} perubahan lokal belum terunggah tetap disimpan aman di perangkat.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (uiState.isDarkTheme) P2AmberTextDark else P2AmberTextLight
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onLogout,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("settings_logout_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = P1Crimson,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Keluar / Logout",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
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
private fun StatusRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
