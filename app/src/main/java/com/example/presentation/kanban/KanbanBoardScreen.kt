package com.example.presentation.kanban

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.sp
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.presentation.components.CategoryPill
import com.example.presentation.components.PriorityBadge
import com.example.presentation.components.StackedAvatarGroup
import com.example.presentation.schedule.ScheduleUiState
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme

@Composable
fun KanbanBoardScreen(
    uiState: ScheduleUiState,
    onTaskClicked: (Long) -> Unit,
    onMoveStage: (Long, Stage) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStage by rememberSaveable { mutableStateOf(Stage.IN_PROGRESS) }
    val extendedColors = PingDuluTheme.extendedColors

    val backlogCount = uiState.allTasks.count { it.stage == Stage.BACKLOG }
    val inProgressCount = uiState.allTasks.count { it.stage == Stage.IN_PROGRESS }
    val doneCount = uiState.allTasks.count { it.stage == Stage.DONE }

    val stageTasks = uiState.allTasks
        .filter { it.stage == selectedStage }
        .sortedWith(compareBy({ it.priority.sortRank }, { it.scheduledDateEpochDay }, { it.scheduledTimeMinutes }))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("kanban_board_screen")
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Papan Kanban Lapangan",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Semua tiket lintas jadwal 7 hari · Pindahkan stage langsung secara offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 3 Behance Stage Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StageBoardPill(
                        stage = Stage.BACKLOG,
                        count = backlogCount,
                        dotColor = extendedColors.pillBacklog,
                        selected = selectedStage == Stage.BACKLOG,
                        onClick = { selectedStage = Stage.BACKLOG },
                        modifier = Modifier.weight(1f)
                    )
                    StageBoardPill(
                        stage = Stage.IN_PROGRESS,
                        count = inProgressCount,
                        dotColor = extendedColors.pillInProgress,
                        selected = selectedStage == Stage.IN_PROGRESS,
                        onClick = { selectedStage = Stage.IN_PROGRESS },
                        modifier = Modifier.weight(1f)
                    )
                    StageBoardPill(
                        stage = Stage.DONE,
                        count = doneCount,
                        dotColor = extendedColors.pillDone,
                        selected = selectedStage == Stage.DONE,
                        onClick = { selectedStage = Stage.DONE },
                        modifier = Modifier.weight(1f)
                    )
                }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (stageTasks.isEmpty()) {
                    item(key = "kanban_empty") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Kolom ${selectedStage.displayLabel} kosong",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Pindahkan tiket dari kolom lain atau tambah tiket baru.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(items = stageTasks, key = { it.id }) { task ->
                        KanbanStageTaskCard(
                            task = task,
                            onCardClick = { onTaskClicked(task.id) },
                            onMoveStage = { nextStage -> onMoveStage(task.id, nextStage) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageBoardPill(
    stage: Stage,
    count: Int,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("kanban_stage_tab_${stage.name.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PingDuluNavy else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) PingDuluNavy else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = " ${stage.displayLabel} ($count)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun KanbanStageTaskCard(
    task: Task,
    onCardClick: () -> Unit,
    onMoveStage: (Stage) -> Unit
) {
    val extendedColors = PingDuluTheme.extendedColors
    val accentColor = extendedColors.accentForStage(task.stage)

    Surface(
        onClick = onCardClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kanban_card_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.65f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                    CategoryPill(category = task.category, text = task.ticketCode)
                    PriorityBadge(priority = task.priority)
                }
                Text(
                    text = DateEpochUtil.formatFullDate(task.scheduledDateEpochDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${task.location} · Checklist ${task.completedChecklistCount}/${task.totalChecklistCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StackedAvatarGroup(
                    assignees = task.assignees,
                    avatarSize = 22.dp,
                    ringColor = MaterialTheme.colorScheme.surface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Quick Stage Transition Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (task.stage != Stage.BACKLOG) {
                    QuickStageActionButton(
                        label = "Ke Antrean",
                        icon = Icons.Rounded.Schedule,
                        tint = extendedColors.pillBacklog,
                        onClick = { onMoveStage(Stage.BACKLOG) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (task.stage != Stage.IN_PROGRESS) {
                    QuickStageActionButton(
                        label = "Kerjakan",
                        icon = Icons.Rounded.PlayArrow,
                        tint = extendedColors.pillInProgress,
                        onClick = { onMoveStage(Stage.IN_PROGRESS) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (task.stage != Stage.DONE) {
                    QuickStageActionButton(
                        label = "Selesai",
                        icon = Icons.Rounded.CheckCircle,
                        tint = extendedColors.pillDone,
                        onClick = { onMoveStage(Stage.DONE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStageActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = " $label",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
