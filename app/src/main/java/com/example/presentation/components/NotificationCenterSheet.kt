package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.presentation.schedule.FieldNotificationItem
import com.example.presentation.schedule.NotificationCategory
import com.example.presentation.schedule.NotificationSeverity
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P1CrimsonTextDark
import com.example.ui.theme.P1CrimsonTextLight
import com.example.ui.theme.P2Amber
import com.example.ui.theme.P2AmberTextDark
import com.example.ui.theme.P2AmberTextLight
import com.example.ui.theme.PillProgressSky
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterSheet(
    notifications: List<FieldNotificationItem>,
    unreadCount: Int,
    isSyncing: Boolean,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onTriggerSync: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by rememberSaveable { mutableStateOf(NotificationCategory.ALL) }

    val filteredItems = remember(notifications, selectedCategory) {
        if (selectedCategory == NotificationCategory.ALL) {
            notifications
        } else {
            notifications.filter { it.category == selectedCategory }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("notification_center_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "Pusat Notifikasi",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Pusat Notifikasi",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (unreadCount > 0) {
                                "$unreadCount notifikasi belum dibaca"
                            } else {
                                "Semua notifikasi telah terbaca"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (unreadCount > 0) {
                    TextButton(
                        onClick = onMarkAllRead,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("notification_mark_all_read_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tandai Dibaca",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Tabs
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NotificationCategory.entries, key = { it.name }) { category ->
                    val count = if (category == NotificationCategory.ALL) {
                        notifications.size
                    } else {
                        notifications.count { it.category == category }
                    }
                    val selected = selectedCategory == category
                    Surface(
                        onClick = { selectedCategory = category },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) {
                            PingDuluNavy
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) PingDuluNavy else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("notification_filter_${category.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = if (selected) {
                                    Color.White.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Notification Feed
            if (filteredItems.isEmpty()) {
                NotificationEmptyState(
                    category = selectedCategory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 36.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .testTag("notification_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        NotificationItemCard(
                            item = item,
                            isSyncing = isSyncing,
                            isDarkTheme = isDarkTheme,
                            onCardClick = {
                                onMarkNotificationRead(item.id)
                                if (item.taskId != null) {
                                    onDismiss()
                                    onOpenTask(item.taskId)
                                }
                            },
                            onSyncClick = {
                                onMarkNotificationRead(item.id)
                                onTriggerSync()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItemCard(
    item: FieldNotificationItem,
    isSyncing: Boolean,
    isDarkTheme: Boolean,
    onCardClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    val accentColor = when (item.severity) {
        NotificationSeverity.CRITICAL -> P1Crimson
        NotificationSeverity.WARNING -> P2Amber
        NotificationSeverity.ACTIVE -> PingDuluNavy
        NotificationSeverity.INFO -> if (item.category == NotificationCategory.SYNC) AvatarEmerald else PillProgressSky
    }
    val badgeTextColor = when (item.severity) {
        NotificationSeverity.CRITICAL -> if (isDarkTheme) P1CrimsonTextDark else P1CrimsonTextLight
        NotificationSeverity.WARNING -> if (isDarkTheme) P2AmberTextDark else P2AmberTextLight
        NotificationSeverity.ACTIVE -> MaterialTheme.colorScheme.primary
        NotificationSeverity.INFO -> PingDuluTheme.extendedColors.syncOnline.content
    }
    val leadingIcon = when {
        item.severity == NotificationSeverity.CRITICAL -> Icons.Rounded.WarningAmber
        item.severity == NotificationSeverity.ACTIVE -> Icons.Rounded.Timer
        item.isSyncAction -> Icons.Rounded.CloudUpload
        item.category == NotificationCategory.SCHEDULE -> Icons.Rounded.Schedule
        else -> Icons.Rounded.CloudDone
    }

    val containerColor = if (!item.isRead) {
        accentColor.copy(alpha = if (isDarkTheme) 0.14f else 0.06f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (!item.isRead) {
        accentColor.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onCardClick)
            .testTag("notification_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accentColor.copy(alpha = if (isDarkTheme) 0.24f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = item.badgeText,
                    tint = badgeTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        if (!item.isRead) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                        }
                        Text(
                            text = item.badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = badgeTextColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = item.timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.isSyncAction) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onSyncClick,
                        enabled = !isSyncing,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PingDuluNavy,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("notification_quick_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSyncing) "Menyinkronkan..." else "Sinkronkan Sekarang",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else if (item.taskId != null) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Buka Lembar Kerja",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationEmptyState(
    category: NotificationCategory,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("notification_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(PingDuluTheme.extendedColors.syncOnline.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Tidak ada notifikasi",
                tint = PingDuluTheme.extendedColors.syncOnline.content,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = if (category == NotificationCategory.ALL) {
                "Semua Operasional Aman"
            } else {
                "Belum ada notifikasi ${category.label}"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Peringatan tiket P1, jadwal hari ini, stopwatch aktif, dan pembaruan server akan muncul otomatis di sini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
