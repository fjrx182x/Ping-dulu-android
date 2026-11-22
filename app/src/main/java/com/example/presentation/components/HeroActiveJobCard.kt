package com.example.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Task
import com.example.domain.model.TaskPolicyHelper
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.HeroNavyGradient
import com.example.ui.theme.LeadStarAmber
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluNavyDark
import com.example.ui.theme.StopwatchMint
import com.example.ui.theme.SyncGreenBgDark
import com.example.ui.theme.TicketCodeTextStyle

@Composable
fun HeroActiveJobCarousel(
    tasks: List<Task>,
    currentUserId: Long = 0L,
    currentUserName: String = "",
    currentUserRole: String = "admin",
    onCompleteTask: (Long) -> Unit,
    onOpenWorksheet: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        EmptyHeroJobCard(modifier = modifier)
        return
    }

    if (tasks.size == 1) {
        val singleTask = tasks.first()
        val canComplete = remember(singleTask, currentUserId, currentUserName, currentUserRole) {
            TaskPolicyHelper.canCompleteOrDelete(
                task = singleTask,
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                currentUserRole = currentUserRole
            )
        }
        HeroActiveJobCard(
            task = singleTask,
            slideIndex = 0,
            totalSlides = 1,
            canComplete = canComplete,
            onCompleteTask = onCompleteTask,
            onOpenWorksheet = onOpenWorksheet,
            modifier = modifier
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { tasks.size })

    // Guard against page index drift if a task on the last slide is marked completed
    LaunchedEffect(tasks.size) {
        val maxIndex = (tasks.size - 1).coerceAtLeast(0)
        if (pagerState.currentPage > maxIndex) {
            pagerState.scrollToPage(maxIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 10.dp,
            verticalAlignment = Alignment.Top,
            key = { index -> tasks[index].id },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("hero_active_job_pager")
        ) { pageIndex ->
            val pageTask = tasks[pageIndex]
            val canComplete = remember(pageTask, currentUserId, currentUserName, currentUserRole) {
                TaskPolicyHelper.canCompleteOrDelete(
                    task = pageTask,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    currentUserRole = currentUserRole
                )
            }
            HeroActiveJobCard(
                task = pageTask,
                slideIndex = pageIndex,
                totalSlides = tasks.size,
                canComplete = canComplete,
                onCompleteTask = onCompleteTask,
                onOpenWorksheet = onOpenWorksheet
            )
        }

        // Slide Indicator Row (discoverable dots + slide counter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hero_pager_indicator"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(tasks.size) { idx ->
                    val isCurrent = pagerState.currentPage == idx
                    val dotWidth by animateDpAsState(
                        targetValue = if (isCurrent) 20.dp else 7.dp,
                        animationSpec = spring(),
                        label = "hero_dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) PingDuluNavy
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwipeLeft,
                    contentDescription = "Geser untuk melihat task aktif lain",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Geser · ${pagerState.currentPage + 1}/${tasks.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun HeroActiveJobCard(
    task: Task?,
    liveSeconds: Long = 0L,
    slideIndex: Int = 0,
    totalSlides: Int = 1,
    canComplete: Boolean = true,
    onToggleTimer: (Long) -> Unit = {},
    onCompleteTask: (Long) -> Unit = {},
    onOpenWorksheet: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(22.dp)

    if (task == null) {
        EmptyHeroJobCard(modifier = modifier)
        return
    }

    var showCompleteConfirmDialog by remember(task.id) { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "hero_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = task.checklistProgressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "hero_checklist_progress"
    )

    val statusBadgeText = if (totalSlides > 1) {
        "SEDANG DIKERJAKAN ${slideIndex + 1} DARI $totalSlides"
    } else {
        "SEDANG DIKERJAKAN"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_active_job_card"),
        shape = cardShape,
        color = Color.Transparent,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(brush = HeroNavyGradient)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.20f),
                    shape = cardShape
                )
        ) {
            // Subtle decorative ambient glow in corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 28.dp, y = 28.dp)
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row: Live Status Badge (with 1 dari N when >1) + Ticket Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(1f, fill = false),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .alpha(pulseAlpha)
                                    .clip(CircleShape)
                                    .background(StopwatchMint)
                            )
                            Text(
                                text = statusBadgeText,
                                modifier = Modifier.testTag("hero_slide_status_badge_${slideIndex + 1}"),
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                    ) {
                        Text(
                            text = task.ticketCode,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = TicketCodeTextStyle,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Task Title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Planned Work Badge & Colored Label Badges on Navy Hero Surface
                if (task.isPlannedWork()) {
                    PlannedWorkBadge(
                        task = task,
                        compact = false,
                        onDarkSurface = true,
                        showDateWhenFuture = true
                    )
                }

                TaskLabelRow(
                    labels = task.resolvedLabels,
                    maxVisible = 3,
                    compact = true,
                    onDarkSurface = true
                )

                // Waktu Lapor & Location Metadata
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTimeFilled,
                            contentDescription = "Waktu lapor",
                            tint = LeadStarAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Dilaporkan: ${DateEpochUtil.formatReportedTime(task.createdAtMillis)}",
                            modifier = Modifier.testTag("hero_reported_time_text"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.94f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "Lokasi pekerjaan",
                            tint = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = task.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Inset Sub-panel: Multi-Assignee + Checklist Progress + Quick Field Actions
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.11f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                StackedAvatarGroup(
                                    assignees = task.assignees,
                                    avatarSize = 24.dp,
                                    ringColor = PingDuluNavyDark,
                                    showLeadIndicator = true
                                )
                                val teamNames = task.assignees.joinToString(" + ") {
                                    it.name.substringBefore(" ")
                                }.ifBlank { "Belum ditugaskan" }
                                Text(
                                    text = teamNames,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SyncGreenBgDark.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, StopwatchMint.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "Checklist ${task.completedChecklistCount}/${task.totalChecklistCount} (${task.checklistProgressPercent}%)",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = StopwatchMint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.30f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = animatedProgress)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(PillDoneMint, StopwatchMint)
                                        )
                                    )
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                        // Quick Action Buttons (Lembar Kerja & Selesaikan — Rute Lokasi dihapus)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenWorksheet(task.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("hero_worksheet_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.16f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Assignment,
                                    contentDescription = "Buka lembar kerja checklist",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Lembar Kerja",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            FilledTonalButton(
                                onClick = { showCompleteConfirmDialog = true },
                                enabled = canComplete,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("hero_complete_button_${task.id}"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = AvatarEmerald,
                                    contentColor = Color.White,
                                    disabledContainerColor = Color.White.copy(alpha = 0.10f),
                                    disabledContentColor = Color.White.copy(alpha = 0.45f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selesaikan tiket ini",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Selesaikan",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCompleteConfirmDialog) {
        val unfinishedChecklistCount = (task.totalChecklistCount - task.completedChecklistCount).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { showCompleteConfirmDialog = false },
            modifier = Modifier.testTag("hero_complete_confirm_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = AvatarEmerald,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Selesaikan Tiket ${task.ticketCode}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Progres checklist saat ini: ${task.completedChecklistCount}/${task.totalChecklistCount} (${task.checklistProgressPercent}%).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (unfinishedChecklistCount > 0) {
                        Text(
                            text = "Masih ada $unfinishedChecklistCount langkah checklist yang belum dicentang. Tetap tandai tiket ini selesai?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCompleteConfirmDialog = false },
                    modifier = Modifier.testTag("hero_complete_cancel_button")
                ) {
                    Text("Batal")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompleteConfirmDialog = false
                        onCompleteTask(task.id)
                    },
                    modifier = Modifier.testTag("hero_complete_confirm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AvatarEmerald,
                        contentColor = Color.White
                    )
                ) {
                    Text("Ya, Selesaikan", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun EmptyHeroJobCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_empty_card"),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(brush = HeroNavyGradient)
                .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PillDoneMint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Tidak ada pekerjaan aktif",
                    tint = StopwatchMint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Belum Ada Tiket Aktif Berjalan",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    text = "Ketuk salah satu tiket di jadwal untuk menekan Kerjakan dan membuka lembar kerja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.80f)
                )
            }
        }
    }
}
