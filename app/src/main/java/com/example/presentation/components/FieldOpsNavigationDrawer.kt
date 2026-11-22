package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ShiftStatus
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.presentation.schedule.ScheduleUiState
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.HeroNavyGradient
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme
import com.example.ui.theme.StopwatchMint
import com.example.ui.theme.TicketCodeTextStyle

@Composable
fun FieldOpsDrawerSheet(
    uiState: ScheduleUiState,
    onSelectShiftStatus: (ShiftStatus) -> Unit,
    onToggleOnlyMyTickets: () -> Unit,
    onToggleOnlyCriticalP1: () -> Unit,
    onSelectCategory: (TicketCategory?) -> Unit,
    onResetFilters: () -> Unit,
    onOpenToolkit: (FieldToolkitTab) -> Unit,
    onOpenTask: (Long) -> Unit,
    onTriggerSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCount = uiState.allTasks.count { it.stage != Stage.DONE }
    val p1Count = uiState.allTasks.count {
        it.priority == Priority.P1_CRITICAL && it.stage != Stage.DONE
    }
    val doneCount = uiState.allTasks.count { it.stage == Stage.DONE }

    ModalDrawerSheet(
        modifier = modifier
            .widthIn(max = 324.dp)
            .fillMaxHeight()
            .testTag("drawer_sheet"),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // 1. Deep Cobalt Navy Profile & Shift Banner
            DrawerProfileShiftHeader(
                technicianName = uiState.technicianName,
                technicianRole = uiState.technicianRole,
                shiftStatus = uiState.shiftStatus,
                activeCount = activeCount,
                p1Count = p1Count,
                doneCount = doneCount,
                onSelectShiftStatus = onSelectShiftStatus
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 2. Focus & Category Filters
                DrawerFilterSection(
                    uiState = uiState,
                    onToggleOnlyMyTickets = onToggleOnlyMyTickets,
                    onToggleOnlyCriticalP1 = onToggleOnlyCriticalP1,
                    onSelectCategory = onSelectCategory,
                    onResetFilters = onResetFilters
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 3. Field Ops Toolkit (Offline-Ready)
                DrawerToolkitSection(onOpenToolkit = onOpenToolkit)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 4. Sync Queue Inspector
                DrawerSyncQueueSection(
                    unsyncedTasks = uiState.unsyncedTasks,
                    isSyncing = uiState.isSyncing,
                    onOpenTask = onOpenTask,
                    onTriggerSync = onTriggerSync
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Footer Metadata
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = uiState.serverBaseUrl,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Room SQLite Offline-First · PingDulu Field Ops v1.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerProfileShiftHeader(
    technicianName: String,
    technicianRole: String,
    shiftStatus: ShiftStatus,
    activeCount: Int,
    p1Count: Int,
    doneCount: Int,
    onSelectShiftStatus: (ShiftStatus) -> Unit
) {
    val initial = technicianName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "F"
    val shiftDotColor = when (shiftStatus) {
        ShiftStatus.ON_DUTY -> StopwatchMint
        ShiftStatus.STANDBY -> P2Amber
        ShiftStatus.BREAK -> Color.White.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeroNavyGradient)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Identity Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(AvatarEmerald)
                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = technicianName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(shiftDotColor)
                        )
                        Text(
                            text = "${technicianRole.uppercase()} · ${shiftStatus.label}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Shift Status Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShiftStatus.entries.forEach { status ->
                    val selected = shiftStatus == status
                    val tag = when (status) {
                        ShiftStatus.ON_DUTY -> "drawer_shift_on_duty"
                        ShiftStatus.STANDBY -> "drawer_shift_standby"
                        ShiftStatus.BREAK -> "drawer_shift_break"
                    }
                    Surface(
                        onClick = { onSelectShiftStatus(status) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag(tag),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color.White else Color.White.copy(alpha = 0.24f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = status.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) PingDuluNavy else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Mini KPI Strip
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DrawerKpiItem(label = "Aktif", value = "$activeCount", valueColor = Color.White)
                    DrawerKpiItem(
                        label = "P1 Kritis",
                        value = "$p1Count",
                        valueColor = if (p1Count > 0) P2Amber else StopwatchMint
                    )
                    DrawerKpiItem(
                        label = "Selesai",
                        value = "$doneCount",
                        valueColor = StopwatchMint
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerKpiItem(
    label: String,
    value: String,
    valueColor: Color,
    isMono: Boolean = false
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.72f)
        )
        Text(
            text = value,
            fontFamily = if (isMono) JetBrainsMonoFontFamily else null,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (isMono) 12.sp else 14.sp,
            color = valueColor
        )
    }
}

@Composable
private fun DrawerFilterSection(
    uiState: ScheduleUiState,
    onToggleOnlyMyTickets: () -> Unit,
    onToggleOnlyCriticalP1: () -> Unit,
    onSelectCategory: (TicketCategory?) -> Unit,
    onResetFilters: () -> Unit
) {
    val extendedColors = PingDuluTheme.extendedColors

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FILTER FOKUS & KATEGORI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.7.sp
            )
            if (uiState.activeDrawerFilterCount > 0) {
                TextButton(
                    onClick = onResetFilters,
                    modifier = Modifier.testTag("drawer_reset_filters")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterAltOff,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Reset (${uiState.activeDrawerFilterCount})", fontSize = 11.5.sp)
                }
            }
        }

        // Toggle 1: Only My Tickets
        DrawerToggleRow(
            icon = Icons.Rounded.Person,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Tiket Saya Saja",
            subtitle = "Hanya tiket dengan nama ${uiState.technicianName}",
            checked = uiState.onlyMyTickets,
            onToggle = onToggleOnlyMyTickets,
            testTag = "drawer_filter_my_tickets"
        )

        // Toggle 2: Only P1 Critical
        DrawerToggleRow(
            icon = Icons.Rounded.PriorityHigh,
            iconTint = P1Crimson,
            title = "Hanya P1 Critical",
            subtitle = "Fokus gangguan darurat & SLA ketat",
            checked = uiState.onlyCriticalP1,
            onToggle = onToggleOnlyCriticalP1,
            testTag = "drawer_filter_p1_only"
        )

        // Category Filter Items
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryDrawerRow(
                label = "Semua Kategori",
                codeBadge = "ALL",
                count = uiState.allTasks.size,
                selected = uiState.selectedCategoryFilter == null,
                badgeBg = MaterialTheme.colorScheme.surfaceVariant,
                badgeFg = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelectCategory(null) },
                testTag = "drawer_category_all"
            )

            TicketCategory.entries.forEach { category ->
                val catColors = extendedColors.colorsForCategory(category)
                val count = uiState.allTasks.count { it.category == category }
                CategoryDrawerRow(
                    label = category.displayName,
                    codeBadge = category.code,
                    count = count,
                    selected = uiState.selectedCategoryFilter == category,
                    badgeBg = catColors.container,
                    badgeFg = catColors.content,
                    onClick = { onSelectCategory(category) },
                    testTag = "drawer_category_${category.code.lowercase()}"
                )
            }
        }
    }
}

@Composable
private fun DrawerToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = { onToggle() }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = PingDuluNavy,
                    checkedThumbColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun CategoryDrawerRow(
    label: String,
    codeBadge: String,
    count: Int,
    selected: Boolean,
    badgeBg: Color,
    badgeFg: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PingDuluNavy.copy(alpha = 0.12f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, PingDuluNavy.copy(alpha = 0.35f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) PingDuluNavy else badgeBg
            ) {
                Text(
                    text = codeBadge,
                    style = TicketCodeTextStyle,
                    color = if (selected) Color.White else badgeFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = count.toString(),
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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

        DrawerToolRow(
            icon = Icons.Rounded.Speed,
            title = "Speed Test Tool (LibreSpeed)",
            subtitle = "Uji throughput unduh & unggah (Self-Hosted / LAN)",
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

        DrawerToolRow(
            icon = Icons.Rounded.Cable,
            title = "Panduan RJ45 & PoE",
            subtitle = "Pinout T568A/B, Crossover, Console & Budget PoE",
            onClick = { onOpenToolkit(FieldToolkitTab.RJ45_POE) },
            testTag = "drawer_tool_rj45_poe"
        )
    }
}

@Composable
private fun DrawerToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PingDuluNavy.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Buka $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DrawerSyncQueueSection(
    unsyncedTasks: List<Task>,
    isSyncing: Boolean,
    onOpenTask: (Long) -> Unit,
    onTriggerSync: () -> Unit
) {
    val extendedColors = PingDuluTheme.extendedColors

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ANTREAN SYNC OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.7.sp
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (unsyncedTasks.isEmpty()) {
                    extendedColors.syncOnline.container
                } else {
                    extendedColors.priorityP2.container
                }
            ) {
                Text(
                    text = if (unsyncedTasks.isEmpty()) "0 Antre" else "${unsyncedTasks.size} Tiket",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (unsyncedTasks.isEmpty()) {
                        extendedColors.syncOnline.content
                    } else {
                        extendedColors.priorityP2.content
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        if (unsyncedTasks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = extendedColors.syncOnline.container,
                border = BorderStroke(1.dp, extendedColors.syncOnline.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = extendedColors.syncOnline.content,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Semua data sudah tersinkron",
                            style = MaterialTheme.typography.titleSmall,
                            color = extendedColors.syncOnline.content
                        )
                        Text(
                            text = "Tidak ada perubahan offline yang tertunda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                unsyncedTasks.take(4).forEach { task ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenTask(task.id) }
                            .testTag("drawer_unsynced_task_${task.id}"),
                        shape = RoundedCornerShape(12.dp),
                        color = extendedColors.priorityP2.container.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, extendedColors.priorityP2.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudUpload,
                                contentDescription = "Menunggu unggah",
                                tint = P2Amber,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.ticketCode,
                                    style = TicketCodeTextStyle,
                                    color = extendedColors.priorityP2.content
                                )
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Buka lembar kerja ${task.ticketCode}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (unsyncedTasks.size > 4) {
                    Text(
                        text = "+${unsyncedTasks.size - 4} tiket lainnya menunggu sinkronisasi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Button(
            onClick = onTriggerSync,
            enabled = !isSyncing,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("drawer_sync_now_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (unsyncedTasks.isEmpty()) PillDoneMint else PingDuluNavy,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = if (unsyncedTasks.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.Sync,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (isSyncing) "Menyinkronkan..." else "Sync Ulang Sekarang",
                fontWeight = FontWeight.Bold
            )
        }
    }
}
