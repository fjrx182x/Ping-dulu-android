package com.example.presentation.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Stage
import com.example.presentation.components.HeroActiveJobCarousel
import com.example.presentation.components.Horizon7DayStrip
import com.example.presentation.components.NotificationCenterSheet
import com.example.presentation.components.StatusFilterPillRow
import com.example.presentation.components.TaskGridCard
import com.example.presentation.components.TaskListCard
import com.example.presentation.components.UserAvatar
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme

@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onDaySelected: (Long) -> Unit,
    onStatusFilterSelected: (StatusFilter) -> Unit,
    onLayoutModeSelected: (LayoutMode) -> Unit,
    onTaskClicked: (Long) -> Unit,
    onCompleteHeroTask: (Long) -> Unit,
    onTriggerSync: () -> Unit,
    onToggleTheme: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onOpenDrawer: () -> Unit,
    onResetDrawerFilters: () -> Unit,
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showNotificationCenterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("schedule_screen")
    ) {
        ScheduleTopAppBar(
            uiState = uiState,
            onMenuClick = onOpenDrawer,
            onToggleTheme = onToggleTheme,
            onNotificationClick = {
                showNotificationCenterSheet = true
            },
            onProfileClick = onOpenProfile,
            onTriggerSync = onTriggerSync
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            val gridColumns = when {
                uiState.layoutMode == LayoutMode.LIST_1_COLUMN -> 1
                maxWidth >= 720.dp -> 3
                else -> 2
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .testTag("schedule_tasks_grid"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 104.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Hero Active Job Carousel (single card or multi-task slide)
                item(
                    key = "hero_section",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    val carouselTasks = if (uiState.activeHeroTasks.isNotEmpty()) {
                        uiState.activeHeroTasks
                    } else {
                        listOfNotNull(uiState.activeHeroTask)
                    }
                    HeroActiveJobCarousel(
                        tasks = carouselTasks,
                        currentUserId = uiState.currentUserId,
                        currentUserName = uiState.technicianName,
                        currentUserRole = uiState.technicianRole,
                        onCompleteTask = onCompleteHeroTask,
                        onOpenWorksheet = onTaskClicked
                    )
                }

                // 2. 7-Day Rolling Horizon Strip
                item(
                    key = "horizon_7day_section",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Horizon7DayStrip(
                        days = uiState.scheduleDays,
                        selectedEpochDay = uiState.selectedEpochDay,
                        horizonSpanLabel = uiState.horizonSpanLabel,
                        onDaySelected = onDaySelected
                    )
                }

                // 3. Behance Status Filter Pills + Grid/List Switcher
                item(
                    key = "status_filter_section",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    StatusFilterPillRow(
                        selectedFilter = uiState.selectedStatusFilter,
                        counts = uiState.filterCounts,
                        layoutMode = uiState.layoutMode,
                        onFilterSelected = onStatusFilterSelected,
                        onLayoutModeSelected = onLayoutModeSelected
                    )
                }

                // 3b. Active Drawer Filters Summary Strip (when filtered via ≡ menu)
                if (uiState.activeDrawerFilterCount > 0) {
                    item(
                        key = "active_drawer_filters_strip",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        ActiveDrawerFilterStrip(
                            uiState = uiState,
                            onResetDrawerFilters = onResetDrawerFilters
                        )
                    }
                }

                // 4. Section Title Row
                item(
                    key = "tasks_header",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.selectedFullDateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${uiState.filteredTasks.size} tiket ditampilkan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 5. Empty State or 2-Column Grid / 1-Column List Cards
                if (uiState.filteredTasks.isEmpty()) {
                    item(
                        key = "empty_state",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        EmptyDayTasksCard(
                            hasActiveDrawerFilters = uiState.activeDrawerFilterCount > 0,
                            onResetDrawerFilters = onResetDrawerFilters
                        )
                    }
                } else if (uiState.layoutMode == LayoutMode.GRID_2_COLUMN) {
                    items(
                        items = uiState.filteredTasks,
                        key = { "grid_${it.id}" }
                    ) { task ->
                        TaskGridCard(
                            task = task,
                            onClick = { onTaskClicked(task.id) }
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredTasks,
                        key = { "list_${it.id}" },
                        span = { GridItemSpan(maxLineSpan) }
                    ) { task ->
                        TaskListCard(
                            task = task,
                            onClick = { onTaskClicked(task.id) }
                        )
                    }
                }
            }
        }
    }

    if (showNotificationCenterSheet) {
        NotificationCenterSheet(
            notifications = uiState.notifications,
            unreadCount = uiState.unreadNotificationCount,
            isSyncing = uiState.isSyncing,
            isDarkTheme = uiState.isDarkTheme,
            onDismiss = { showNotificationCenterSheet = false },
            onMarkNotificationRead = onMarkNotificationRead,
            onMarkAllRead = onMarkAllNotificationsRead,
            onOpenTask = onTaskClicked,
            onTriggerSync = onTriggerSync
        )
    }
}

@Composable
private fun ScheduleTopAppBar(
    uiState: ScheduleUiState,
    onMenuClick: () -> Unit,
    onToggleTheme: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTriggerSync: () -> Unit
) {
    val extendedColors = PingDuluTheme.extendedColors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Menu + Brand Logo + Right Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier.testTag("top_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "Buka menu navigasi dan toolkit lapangan",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (uiState.activeDrawerFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(PingDuluNavy)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    .testTag("top_menu_filter_badge"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.activeDrawerFilterCount.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    com.example.presentation.components.PingDuluLogoMark(
                        size = 36.dp,
                        cornerRadius = 10.dp,
                        contentDescription = "PingDulu Logo",
                        modifier = Modifier.testTag("topbar_pingdulu_logo")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "PingDulu",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = extendedColors.categoryNet.container,
                            border = BorderStroke(1.dp, extendedColors.categoryNet.border)
                        ) {
                            Text(
                                text = "FIELD OPS",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = extendedColors.categoryNet.content,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("top_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) {
                                Icons.Rounded.LightMode
                            } else {
                                Icons.Rounded.DarkMode
                            },
                            contentDescription = "Ganti tema terang atau gelap",
                            tint = if (uiState.isDarkTheme) P2Amber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(
                            onClick = onNotificationClick,
                            modifier = Modifier.testTag("top_notification_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = "Notifikasi operasional",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.unreadNotificationCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-5).dp, y = 6.dp)
                                    .widthIn(min = 18.dp)
                                    .height(18.dp)
                                    .clip(CircleShape)
                                    .background(P1Crimson)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (uiState.unreadNotificationCount > 9) {
                                        "9+"
                                    } else {
                                        uiState.unreadNotificationCount.toString()
                                    },
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onProfileClick,
                        modifier = Modifier.testTag("top_profile_avatar_button")
                    ) {
                        UserAvatar(
                            avatarUrl = uiState.technicianAvatarUrl,
                            displayName = uiState.technicianName,
                            size = 36.dp,
                            isActive = uiState.technicianIsActive,
                            showActiveBadge = true,
                            serverBaseUrl = uiState.serverBaseUrl,
                            ringColor = MaterialTheme.colorScheme.surface,
                            ringWidth = 1.5.dp
                        )
                    }
                }
            }

            // Row 2: Greeting + Live Sync Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.greetingTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Jadwal Agenda Lapangan 7 Hari",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SyncStatusPill(
                    badge = uiState.syncStatusBadge,
                    unsyncedCount = uiState.unsyncedCount,
                    onClick = onTriggerSync
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SyncStatusPill(
    badge: SyncStatusBadge,
    unsyncedCount: Int,
    onClick: () -> Unit
) {
    val extendedColors = PingDuluTheme.extendedColors
    val (bgColor, fgColor, borderColor, dotColor) = when (badge) {
        SyncStatusBadge.SYNCED_LIVE, SyncStatusBadge.SYNCED_LOCAL -> listOf(
            extendedColors.syncOnline.container,
            extendedColors.syncOnline.content,
            extendedColors.syncOnline.border,
            PillDoneMint
        )
        SyncStatusBadge.SYNCING, SyncStatusBadge.PENDING_UPLOAD -> listOf(
            extendedColors.priorityP2.container,
            extendedColors.priorityP2.content,
            extendedColors.priorityP2.border,
            P2Amber
        )
        SyncStatusBadge.OFFLINE_CACHED -> listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val labelText = when (badge) {
        SyncStatusBadge.PENDING_UPLOAD -> "Antre Sync ($unsyncedCount)"
        else -> badge.label
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.testTag("top_sync_pill"),
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (badge == SyncStatusBadge.SYNCING) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = "Sedang menyinkronkan",
                    tint = fgColor,
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = labelText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = fgColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ActiveDrawerFilterStrip(
    uiState: ScheduleUiState,
    onResetDrawerFilters: () -> Unit
) {
    val activeLabels = buildList {
        if (uiState.onlyMyTickets) add("Tiket Saya")
        if (uiState.onlyCriticalP1) add("P1 Critical")
        uiState.selectedCategoryFilter?.let { add("Kategori: ${it.code}") }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_drawer_filter_strip"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FilterAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeLabels.forEach { label ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = PingDuluNavy
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            TextButton(
                onClick = onResetDrawerFilters,
                modifier = Modifier.testTag("schedule_reset_drawer_filters")
            ) {
                Text(
                    text = "Reset",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyDayTasksCard(
    hasActiveDrawerFilters: Boolean,
    onResetDrawerFilters: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_tasks_state"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = if (hasActiveDrawerFilters) {
                    "Tidak ada tiket sesuai filter menu"
                } else {
                    "Tidak ada tiket di kategori ini"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasActiveDrawerFilters) {
                    "Filter fokus atau kategori dari menu sedang aktif. Reset filter untuk menampilkan semua tiket hari ini."
                } else {
                    "Pilih filter status lain, pindah tanggal di strip 7 hari, atau tekan tombol (+) untuk membuat tiket cepat."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hasActiveDrawerFilters) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onResetDrawerFilters,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("empty_reset_drawer_filters")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterAltOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Reset Filter Menu", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
