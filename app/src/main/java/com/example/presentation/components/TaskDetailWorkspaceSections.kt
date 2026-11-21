package com.example.presentation.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.model.ActivityCategory
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.SnoozeOption
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TaskActivityItem
import com.example.domain.model.TaskAttachment
import com.example.domain.model.TaskComment
import com.example.domain.model.TaskDetailWorkspaceState
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.AvatarSky
import com.example.ui.theme.AvatarViolet
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme
import java.io.File

@Composable
internal fun TaskStatusAlertsBanner(
    task: Task,
    onUnblock: () -> Unit
) {
    val extended = PingDuluTheme.extendedColors

    if (task.isBlocked) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_blocked_banner"),
            shape = RoundedCornerShape(12.dp),
            color = extended.priorityP1.container,
            border = BorderStroke(1.dp, extended.priorityP1.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = "Tiket terblokir",
                    tint = extended.priorityP1.content,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TIKET TERBLOKIR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = extended.priorityP1.content
                    )
                    Text(
                        text = task.blockedReason?.takeIf { it.isNotBlank() }
                            ?: "Menunggu vendor atau sparepart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onUnblock,
                    modifier = Modifier.testTag("sheet_unblock_inline_button")
                ) {
                    Text(
                        text = "Lepas",
                        fontWeight = FontWeight.Bold,
                        color = extended.priorityP1.content
                    )
                }
            }
        }
    }

    val snoozedUntil = task.snoozedUntilMillis
    if (snoozedUntil != null && snoozedUntil > 0L) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_snoozed_banner"),
            shape = RoundedCornerShape(12.dp),
            color = extended.priorityP2.container,
            border = BorderStroke(1.dp, extended.priorityP2.border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Snooze,
                    contentDescription = "Tiket ditunda",
                    tint = extended.priorityP2.content,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Snooze aktif hingga ${DateEpochUtil.formatReportedTime(snoozedUntil)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = extended.priorityP2.content
                )
            }
        }
    }
}

@Composable
internal fun TaskQuickOpsActionRow(
    task: Task,
    isUpdating: Boolean,
    onSnoozeClick: () -> Unit,
    onBlockedClick: () -> Unit
) {
    val extended = PingDuluTheme.extendedColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onSnoozeClick,
            enabled = !isUpdating,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, extended.priorityP2.border),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("sheet_snooze_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Snooze,
                contentDescription = null,
                tint = extended.priorityP2.content,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Snooze",
                style = MaterialTheme.typography.labelMedium,
                color = extended.priorityP2.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onBlockedClick,
            enabled = !isUpdating,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (task.isBlocked) extended.pillDone else extended.priorityP1.border
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier
                .weight(1.25f)
                .heightIn(min = 48.dp)
                .testTag("sheet_blocked_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                tint = if (task.isBlocked) extended.pillDone else extended.priorityP1.content,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (task.isBlocked) "Lepas Blokir" else "Tandai Terblokir",
                style = MaterialTheme.typography.labelMedium,
                color = if (task.isBlocked) extended.pillDone else extended.priorityP1.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun TaskAttachmentsSection(
    taskId: Long,
    workspaceState: TaskDetailWorkspaceState,
    serverBaseUrl: String,
    authToken: String?,
    onTakePhotoClick: () -> Unit,
    onPickFileClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onPreviewAttachment: (TaskAttachment) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sheet_attachments_section"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = PingDuluNavy,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LAMPIRAN & BUKTI FOTO (${workspaceState.attachments.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }
                if (taskId > 0L) {
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Muat ulang lampiran",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTakePhotoClick,
                    enabled = !workspaceState.isUploadingAttachment,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("sheet_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = PingDuluNavy,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ambil Foto",
                        style = MaterialTheme.typography.labelMedium,
                        color = PingDuluNavy,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onPickFileClick,
                    enabled = !workspaceState.isUploadingAttachment,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("sheet_pick_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        tint = PingDuluNavy,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pilih Berkas",
                        style = MaterialTheme.typography.labelMedium,
                        color = PingDuluNavy,
                        maxLines = 1
                    )
                }
            }

            if (workspaceState.isUploadingAttachment) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp)),
                        color = PingDuluNavy
                    )
                    Text(
                        text = "Mengunggah bukti ke server (maks. 10 MB)...",
                        style = MaterialTheme.typography.labelSmall,
                        color = PingDuluNavy
                    )
                }
            } else if (workspaceState.isLoadingAttachments && workspaceState.attachments.isEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                )
            }

            if (workspaceState.attachments.isEmpty() && !workspaceState.isLoadingAttachments) {
                Text(
                    text = if (taskId <= 0L) {
                        "Sinkronkan tiket ke server untuk melampirkan bukti foto lapangan."
                    } else {
                        "Belum ada bukti foto/berkas. Ketuk Ambil Foto atau Pilih Berkas (maks. 10 MB)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (workspaceState.attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_attachments_row"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(workspaceState.attachments, key = { "${it.id}_${it.fileName}" }) { attachment ->
                        AttachmentThumbnailCard(
                            attachment = attachment,
                            serverBaseUrl = serverBaseUrl,
                            authToken = authToken,
                            onClick = { onPreviewAttachment(attachment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumbnailCard(
    attachment: TaskAttachment,
    serverBaseUrl: String,
    authToken: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val resolvedUrl = remember(attachment.fileUrl, serverBaseUrl) {
        attachment.resolvedFullUrl(serverBaseUrl)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(116.dp)
            .testTag("attachment_card_${attachment.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (attachment.isImage && resolvedUrl.isNotBlank()) {
                    val request = remember(resolvedUrl, authToken) {
                        ImageRequest.Builder(context)
                            .data(resolvedUrl)
                            .crossfade(true)
                            .apply {
                                if (!authToken.isNullOrBlank()) {
                                    addHeader("Authorization", "Bearer $authToken")
                                }
                            }
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (attachment.isImage) Icons.Rounded.Image else Icons.Rounded.Description,
                        contentDescription = attachment.fileName,
                        tint = PingDuluNavy,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (attachment.formattedSizeLabel.isNotBlank()) {
                    Text(
                        text = attachment.formattedSizeLabel,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private enum class DetailFeedTab {
    DISCUSSION,
    ACTIVITY
}

@Composable
internal fun TaskCommentsSection(
    task: Task,
    workspaceState: TaskDetailWorkspaceState,
    onPostComment: (String) -> Unit,
    onRefreshWorkspace: () -> Unit = {}
) {
    var draftComment by remember(task.id) { mutableStateOf("") }
    var selectedTab by remember(task.id) { mutableStateOf(DetailFeedTab.DISCUSSION) }
    var showAllActivities by remember(task.id) { mutableStateOf(false) }

    val resolvedActivities = remember(
        task,
        workspaceState.activities,
        workspaceState.isActivitiesFromServer,
        workspaceState.attachments,
        workspaceState.comments
    ) {
        if (workspaceState.isActivitiesFromServer) {
            workspaceState.activities
        } else {
            buildLocalFallbackActivities(task, workspaceState)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sheet_comments_section"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Opsi A: Compact Segmented Pill Header (Diskusi | Riwayat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FeedSegmentedPill(
                            label = "Diskusi (${workspaceState.comments.size})",
                            icon = Icons.Rounded.Forum,
                            selected = selectedTab == DetailFeedTab.DISCUSSION,
                            onClick = { selectedTab = DetailFeedTab.DISCUSSION },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_tab_discussion")
                        )
                        FeedSegmentedPill(
                            label = "Riwayat (${resolvedActivities.size})",
                            icon = Icons.Rounded.History,
                            selected = selectedTab == DetailFeedTab.ACTIVITY,
                            onClick = { selectedTab = DetailFeedTab.ACTIVITY },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_tab_activity")
                        )
                    }
                }
                if (task.id > 0L) {
                    IconButton(
                        onClick = onRefreshWorkspace,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("sheet_refresh_feed_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Muat ulang diskusi dan riwayat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (selectedTab == DetailFeedTab.DISCUSSION) {
                if (workspaceState.isLoadingComments && workspaceState.comments.isEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                    )
                } else if (workspaceState.comments.isEmpty()) {
                    Text(
                        text = "Belum ada catatan progres di tiket ini. Tambahkan update lapangan pertama di bawah.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        workspaceState.comments.forEach { comment ->
                            CommentBubbleRow(comment = comment)
                        }
                    }
                }

                if (!workspaceState.statusNotice.isNullOrBlank()) {
                    Text(
                        text = workspaceState.statusNotice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draftComment,
                        onValueChange = { draftComment = it },
                        placeholder = { Text("Tulis progres / hasil pengecekan...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_comment_input")
                    )

                    FilledIconButton(
                        onClick = {
                            val clean = draftComment.trim()
                            if (clean.isNotEmpty()) {
                                onPostComment(clean)
                                draftComment = ""
                            }
                        },
                        enabled = draftComment.isNotBlank() && !workspaceState.isPostingComment,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = PingDuluNavy,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sheet_send_comment_button")
                    ) {
                        if (workspaceState.isPostingComment) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Kirim komentar teknis",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // Compact Activity Log Timeline (Server-First, Local Summary Fallback)
                ActivityTimelineContent(
                    activities = resolvedActivities,
                    isLoading = workspaceState.isLoadingActivities,
                    isFromServer = workspaceState.isActivitiesFromServer,
                    showAll = showAllActivities,
                    onToggleShowAll = { showAllActivities = !showAllActivities }
                )
            }
        }
    }
}

@Composable
private fun FeedSegmentedPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier.heightIn(min = 38.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) PingDuluNavy else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) PingDuluNavy else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActivityTimelineContent(
    activities: List<TaskActivityItem>,
    isLoading: Boolean,
    isFromServer: Boolean,
    showAll: Boolean,
    onToggleShowAll: () -> Unit
) {
    val visibleItems = if (showAll || activities.size <= 6) activities else activities.take(6)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sheet_activity_timeline"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JEJAK AKTIVITAS TIKET",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isFromServer) {
                    AvatarEmerald.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFromServer) AvatarEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    )
                    Text(
                        text = if (isFromServer) "Sinkron Server" else "Ringkasan Lokal",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFromServer) AvatarEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("activity_source_badge")
                    )
                }
            }
        }

        if (isLoading && activities.isEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
            )
        } else if (activities.isEmpty()) {
            Text(
                text = "Belum ada riwayat aktivitas tercatat untuk tiket ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                visibleItems.forEachIndexed { index, item ->
                    ActivityTimelineRow(
                        item = item,
                        isLast = index == visibleItems.lastIndex
                    )
                }
            }

            if (activities.size > 6) {
                TextButton(
                    onClick = onToggleShowAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("activity_toggle_all_button")
                ) {
                    Text(
                        text = if (showAll) {
                            "Ringkas"
                        } else {
                            "Lihat semua (${activities.size})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = PingDuluNavy
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityTimelineRow(
    item: TaskActivityItem,
    isLast: Boolean
) {
    val extendedColors = PingDuluTheme.extendedColors
    val dotColor = when (item.category) {
        ActivityCategory.CREATED -> AvatarEmerald
        ActivityCategory.STAGE_CHANGED -> AvatarSky
        ActivityCategory.TIMER -> extendedColors.priorityP2.content
        ActivityCategory.CHECKLIST -> AvatarEmerald
        ActivityCategory.ATTACHMENT -> PingDuluNavy
        ActivityCategory.COMMENT -> AvatarViolet
        ActivityCategory.BLOCKED_OR_SNOOZED -> extendedColors.priorityP1.content
        ActivityCategory.ASSIGNMENT -> AvatarViolet
        ActivityCategory.GENERAL -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_item_${item.id}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Minimal vertical timeline rail (dot + thin hairline)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        val isHumanizedNarrative = remember(item.action) {
            val act = item.action.trim()
            act.contains("oleh ", ignoreCase = true) ||
                act.contains("mengambil tiket", ignoreCase = true) ||
                act.contains("bergabung mengerjakan", ignoreCase = true) ||
                act.startsWith("📌") ||
                act.startsWith("Tiket ", ignoreCase = true) ||
                act.startsWith("Langkah ", ignoreCase = true) ||
                act.startsWith("Tahap ", ignoreCase = true)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 2.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (isHumanizedNarrative) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = DateEpochUtil.formatReportedTime(item.createdAtMillis),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.actorName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = DateEpochUtil.formatReportedTime(item.createdAtMillis),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = item.summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildLocalFallbackActivities(
    task: Task,
    workspaceState: TaskDetailWorkspaceState
): List<TaskActivityItem> {
    val leadActor = task.assignees.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "Sistem"
    val items = mutableListOf<TaskActivityItem>()

    items += TaskActivityItem(
        id = -1001L,
        taskId = task.id,
        actorName = leadActor,
        action = "Tiket dibuat",
        detail = "${task.ticketCode} · ${task.priority.displayLabel}",
        category = ActivityCategory.CREATED,
        createdAtMillis = task.createdAtMillis
    )

    if (task.assignees.isNotEmpty()) {
        items += TaskActivityItem(
            id = -1002L,
            taskId = task.id,
            actorName = "Tim Lapangan",
            action = "Teknisi bertugas",
            detail = task.assignees.joinToString(", ") { it.name },
            category = ActivityCategory.ASSIGNMENT,
            createdAtMillis = task.createdAtMillis + 1L
        )
    }

    if (task.totalChecklistCount > 0 && task.completedChecklistCount > 0) {
        items += TaskActivityItem(
            id = -1003L,
            taskId = task.id,
            actorName = leadActor,
            action = "Progres checklist",
            detail = "${task.completedChecklistCount}/${task.totalChecklistCount} langkah selesai (${task.checklistProgressPercent}%)",
            category = ActivityCategory.CHECKLIST,
            createdAtMillis = task.updatedAtMillis
        )
    }

    if (task.stage == Stage.IN_PROGRESS) {
        items += TaskActivityItem(
            id = -1004L,
            taskId = task.id,
            actorName = leadActor,
            action = "Tiket dikerjakan oleh $leadActor",
            detail = "",
            category = ActivityCategory.STAGE_CHANGED,
            createdAtMillis = task.updatedAtMillis
        )
    }

    if (task.isBlocked) {
        items += TaskActivityItem(
            id = -1005L,
            taskId = task.id,
            actorName = leadActor,
            action = "Tiket ditandai terblokir",
            detail = task.blockedReason?.takeIf { it.isNotBlank() } ?: "Menunggu vendor / sparepart",
            category = ActivityCategory.BLOCKED_OR_SNOOZED,
            createdAtMillis = task.updatedAtMillis
        )
    }

    task.snoozedUntilMillis?.takeIf { it > 0L }?.let { snoozeUntil ->
        items += TaskActivityItem(
            id = -1006L,
            taskId = task.id,
            actorName = leadActor,
            action = "Jadwal ditunda (Snooze)",
            detail = "Hingga ${DateEpochUtil.formatReportedTime(snoozeUntil)}",
            category = ActivityCategory.BLOCKED_OR_SNOOZED,
            createdAtMillis = task.updatedAtMillis
        )
    }

    workspaceState.attachments.forEach { att ->
        items += TaskActivityItem(
            id = -2000L - att.id,
            taskId = task.id,
            actorName = att.uploadedBy.ifBlank { leadActor },
            action = "Mengunggah lampiran",
            detail = att.fileName,
            category = ActivityCategory.ATTACHMENT,
            createdAtMillis = att.createdAtMillis
        )
    }

    workspaceState.comments.forEach { comment ->
        items += TaskActivityItem(
            id = -3000L - comment.id,
            taskId = task.id,
            actorName = comment.authorName,
            action = "Menambahkan komentar",
            detail = comment.commentText,
            category = ActivityCategory.COMMENT,
            createdAtMillis = comment.createdAtMillis
        )
    }

    return items.sortedByDescending { it.createdAtMillis }
}

@Composable
private fun CommentBubbleRow(comment: TaskComment) {
    val avatarBg = remember(comment.authorName) {
        avatarColorForAssignee(
            com.example.domain.model.Assignee(
                id = comment.authorName.trim().lowercase().hashCode().toLong(),
                name = comment.authorName,
                role = comment.authorRole
            )
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comment_item_${comment.id}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.authorInitial,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.authorName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = DateEpochUtil.formatReportedTime(comment.createdAtMillis),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = comment.commentText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun SnoozeTicketDialog(
    onDismiss: () -> Unit,
    onSelectOption: (SnoozeOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tunda Tiket (Snooze)",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pilih waktu penundaan eksekusi tiket dalam zona WIB:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SnoozeOption.entries.forEach { option ->
                    Surface(
                        onClick = {
                            onSelectOption(option)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("snooze_option_${option.idTag}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockedReasonDialog(
    initialReason: String,
    onDismiss: () -> Unit,
    onConfirmBlocked: (String) -> Unit
) {
    var reason by remember(initialReason) { mutableStateOf(initialReason) }
    val presets = listOf("Menunggu Vendor", "Menunggu Sparepart", "Menunggu Akses Lokasi")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tandai Tiket Terblokir",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Catat hambatan lapangan agar tim NOC dan admin dapat memantau tindak lanjut tiket.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = reason.equals(preset, ignoreCase = true),
                            onClick = { reason = preset },
                            label = { Text(preset, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PingDuluNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan Hambatan") },
                    placeholder = { Text("Contoh: Menunggu modul SFP 10G dari vendor") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_reason_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmBlocked(reason.trim().ifBlank { "Menunggu vendor / sparepart" })
                    onDismiss()
                },
                modifier = Modifier.testTag("blocked_confirm_button")
            ) {
                Text("Tandai Terblokir", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
internal fun AttachmentPreviewDialog(
    attachment: TaskAttachment,
    serverBaseUrl: String,
    authToken: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resolvedUrl = remember(attachment.fileUrl, serverBaseUrl) {
        attachment.resolvedFullUrl(serverBaseUrl)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (attachment.isImage && resolvedUrl.isNotBlank()) {
                    val request = remember(resolvedUrl, authToken) {
                        ImageRequest.Builder(context)
                            .data(resolvedUrl)
                            .crossfade(true)
                            .apply {
                                if (!authToken.isNullOrBlank()) {
                                    addHeader("Authorization", "Bearer $authToken")
                                }
                            }
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 320.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                val meta = buildList {
                    if (attachment.formattedSizeLabel.isNotBlank()) add(attachment.formattedSizeLabel)
                    if (attachment.uploadedBy.isNotBlank()) add("Oleh ${attachment.uploadedBy}")
                    add(DateEpochUtil.formatReportedTime(attachment.createdAtMillis))
                }.joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

internal fun createCameraTempPhotoUri(context: Context): Pair<Uri, String>? {
    return runCatching {
        val photosDir = File(context.cacheDir, "field_photos").apply { mkdirs() }
        val fileName = "bukti_${System.currentTimeMillis()}.jpg"
        val photoFile = File(photosDir, fileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        uri to fileName
    }.getOrNull()
}

internal fun readAttachmentPayloadFromUri(
    context: Context,
    uri: Uri,
    fallbackName: String = "lampiran_${System.currentTimeMillis()}"
): Triple<String, String, ByteArray>? {
    return runCatching {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var displayName: String? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) displayName = cursor.getString(idx)
            }
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val finalName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName
        Triple(finalName, mimeType, bytes)
    }.getOrNull()
}
