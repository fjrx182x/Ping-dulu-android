package com.example.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AssignmentInd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.remote.PingDuluApiService
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.FieldTechnician
import com.example.domain.model.SnoozeOption
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TaskAttachment
import com.example.domain.model.TaskDetailWorkspaceState
import com.example.domain.model.TaskPolicyHelper
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme
import com.example.ui.theme.StopwatchMint

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailBottomSheet(
    task: Task,
    onDismiss: () -> Unit,
    onToggleChecklistItem: (taskId: Long, checklistId: Long) -> Unit,
    onToggleTimer: (taskId: Long) -> Unit,
    onMarkCompleted: (taskId: Long) -> Unit,
    onReopenTask: (taskId: Long) -> Unit,
    workspaceState: TaskDetailWorkspaceState = TaskDetailWorkspaceState(taskId = task.id),
    serverBaseUrl: String = PingDuluApiService.FIXED_BASE_URL,
    authToken: String? = null,
    onUploadAttachment: (taskId: Long, fileName: String, mimeType: String, bytes: ByteArray) -> Unit = { _, _, _, _ -> },
    onPostComment: (taskId: Long, commentText: String) -> Unit = { _, _ -> },
    onSnoozeTask: (taskId: Long, option: SnoozeOption) -> Unit = { _, _ -> },
    onSetTaskBlocked: (taskId: Long, isBlocked: Boolean, reason: String?) -> Unit = { _, _, _ -> },
    onRefreshWorkspace: (taskId: Long) -> Unit = {},
    currentUserId: Long = 0L,
    currentUserName: String = "",
    currentUserRole: String = "admin",
    availableTechnicians: List<FieldTechnician> = emptyList(),
    onWorkTask: (taskId: Long) -> Unit = onToggleTimer,
    onClaimTask: (taskId: Long) -> Unit = {},
    onDeleteTask: (taskId: Long) -> Unit = {},
    onAddChecklistStep: (taskId: Long, label: String) -> Unit = { _, _ -> },
    onDelegateTechnicians: (taskId: Long, assigneeIds: List<Long>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extendedColors = PingDuluTheme.extendedColors

    val isUnassigned = remember(task.assignees, task.assignedToId) {
        TaskPolicyHelper.isUnassigned(task)
    }
    val isCurrentUserAssigned = remember(task.assignees, task.assignedToId, currentUserId, currentUserName) {
        TaskPolicyHelper.isCurrentUserAssigned(task, currentUserId, currentUserName)
    }
    val canCompleteOrDelete = remember(
        task.creatorClient,
        task.creatorRole,
        task.createdById,
        task.createdByName,
        task.assignees,
        task.assignedToId,
        currentUserId,
        currentUserName,
        currentUserRole
    ) {
        TaskPolicyHelper.canCompleteOrDelete(task, currentUserId, currentUserName, currentUserRole)
    }
    val canDelegateOrAddChecklist = remember(
        task.stage,
        task.assignees,
        task.assignedToId,
        currentUserId,
        currentUserName,
        currentUserRole
    ) {
        TaskPolicyHelper.canDelegateOrAddChecklist(task, currentUserId, currentUserName, currentUserRole)
    }

    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDelegateDialog by remember { mutableStateOf(false) }
    var newChecklistStepInput by remember(task.id) { mutableStateOf("") }
    var previewAttachment by remember { mutableStateOf<TaskAttachment?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        pendingCameraUri = null
        if (success && captured != null) {
            val (uri, fallbackName) = captured
            readAttachmentPayloadFromUri(context, uri, fallbackName = fallbackName)?.let { (name, mime, bytes) ->
                onUploadAttachment(task.id, name, mime, bytes)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val target = createCameraTempPhotoUri(context)
            if (target != null) {
                pendingCameraUri = target
                takePictureLauncher.launch(target.first)
            }
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            readAttachmentPayloadFromUri(context, uri)?.let { (name, mime, bytes) ->
                onUploadAttachment(task.id, name, mime, bytes)
            }
        }
    }

    val launchCameraCapture = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val target = createCameraTempPhotoUri(context)
            if (target != null) {
                pendingCameraUri = target
                takePictureLauncher.launch(target.first)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .testTag("task_detail_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Ticket Code + Priority + Stage + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryPill(
                        category = task.category,
                        text = task.ticketCode,
                        prominent = true
                    )
                    PriorityBadge(priority = task.priority, expanded = true)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = extendedColors.accentForStage(task.stage).copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = task.stage.displayLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = extendedColors.accentForStage(task.stage)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canCompleteOrDelete) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            enabled = !workspaceState.isUpdatingAction,
                            modifier = Modifier.testTag("sheet_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.btn_delete_task),
                                tint = P1Crimson
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("sheet_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup lembar kerja",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Scrollable Detail Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(weight = 1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Blocked & Snoozed Alerts (Paket 3)
                TaskStatusAlertsBanner(
                    task = task,
                    onUnblock = { onSetTaskBlocked(task.id, false, null) }
                )

                // Title & Metadata
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = "Lokasi",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = task.location,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (task.isPlannedWork()) {
                        val clockSuffix = task.scheduledClockLabel?.let { " · $it" }.orEmpty()
                        val scheduleText = "Terjadwal · ${DateEpochUtil.formatFullDate(task.scheduledDateEpochDay)}$clockSuffix"
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .testTag("sheet_planned_schedule_banner"),
                            shape = RoundedCornerShape(10.dp),
                            color = extendedColors.priorityP3.container,
                            border = BorderStroke(1.dp, extendedColors.priorityP3.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EventAvailable,
                                    contentDescription = "Jadwal pelaksanaan pekerjaan terencana",
                                    tint = extendedColors.priorityP3.content,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = scheduleText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = extendedColors.priorityP3.content
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = "Waktu lapor",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Waktu Lapor: ${DateEpochUtil.formatReportedTime(task.createdAtMillis)}",
                            modifier = Modifier.testTag("sheet_reported_time_text"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TaskLabelFlowRow(
                        labels = task.resolvedLabels,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Quick Field Actions: Snooze & Mark Blocked (Paket 3)
                TaskQuickOpsActionRow(
                    task = task,
                    isUpdating = workspaceState.isUpdatingAction,
                    onSnoozeClick = { showSnoozeDialog = true },
                    onBlockedClick = {
                        if (task.isBlocked) {
                            onSetTaskBlocked(task.id, false, null)
                        } else {
                            showBlockedDialog = true
                        }
                    }
                )

                // Technical Notes
                if (task.technicalNotes.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "CATATAN TEKNIS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = task.technicalNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Multi-Assignee Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            Text(
                                text = "TEKNISI BERTUGAS (MULTI-ASSIGNEE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.6.sp
                            )
                            if (canDelegateOrAddChecklist) {
                                Surface(
                                    onClick = { showDelegateDialog = true },
                                    enabled = !workspaceState.isUpdatingAction,
                                    shape = RoundedCornerShape(999.dp),
                                    color = PingDuluNavy.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, PingDuluNavy.copy(alpha = 0.35f)),
                                    modifier = Modifier.testTag("sheet_add_technician_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonAdd,
                                            contentDescription = "Tambah teknisi delegasi",
                                            tint = PingDuluNavy,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "+ Tambah Teknisi",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PingDuluNavy
                                        )
                                    }
                                }
                            }
                        }
                        if (task.assignees.isEmpty()) {
                            Text(
                                text = "Belum ada teknisi ditetapkan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                task.assignees.forEach { assignee ->
                                    AssigneeLeadChip(assignee = assignee)
                                }
                            }
                        }
                    }
                }

                // Interactive Checklist Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CHECKLIST PENGERJAAN LAPANGAN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "${task.completedChecklistCount}/${task.totalChecklistCount} (${task.checklistProgressPercent}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = task.checklistProgressFraction.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(PillDoneMint, StopwatchMint)
                                    )
                                )
                        )
                    }

                    if (task.checklist.isEmpty()) {
                        Text(
                            text = "Tidak ada item checklist pada tiket ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            task.checklist.forEach { item ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .toggleable(
                                            value = item.isDone,
                                            role = Role.Checkbox,
                                            onValueChange = {
                                                onToggleChecklistItem(task.id, item.id)
                                            }
                                        )
                                        .testTag("checklist_row_${item.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (item.isDone) {
                                        extendedColors.pillDone.copy(alpha = 0.10f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (item.isDone) {
                                            extendedColors.pillDone.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Checkbox(
                                            checked = item.isDone,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = AvatarEmerald,
                                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (item.isDone) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            textDecoration = if (item.isDone) {
                                                TextDecoration.LineThrough
                                            } else {
                                                TextDecoration.None
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Patch Ke-7: Assigned technician & Admin can append new checklist sub-steps
                    if (canDelegateOrAddChecklist) {
                        val submitStep = {
                            val clean = newChecklistStepInput.trim()
                            if (clean.isNotEmpty()) {
                                onAddChecklistStep(task.id, clean)
                                newChecklistStepInput = ""
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newChecklistStepInput,
                                onValueChange = { newChecklistStepInput = it },
                                enabled = !workspaceState.isUpdatingAction,
                                placeholder = { Text("Tambah langkah checklist...", fontSize = 13.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitStep() }),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sheet_add_checklist_input")
                            )
                            Button(
                                onClick = submitStep,
                                enabled = !workspaceState.isUpdatingAction && newChecklistStepInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PingDuluNavy,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("sheet_add_checklist_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Tambah langkah checklist",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Paket 3: Field Photo & File Attachments Section
                TaskAttachmentsSection(
                    taskId = task.id,
                    workspaceState = workspaceState,
                    serverBaseUrl = serverBaseUrl,
                    authToken = authToken,
                    onTakePhotoClick = launchCameraCapture,
                    onPickFileClick = { pickFileLauncher.launch("*/*") },
                    onRefreshClick = { onRefreshWorkspace(task.id) },
                    onPreviewAttachment = { previewAttachment = it }
                )

                // Paket 3 + Opsi A: Unified Discussion & Server Activity Log Segmented Section
                TaskCommentsSection(
                    task = task,
                    workspaceState = workspaceState,
                    onPostComment = { text -> onPostComment(task.id, text) },
                    onRefreshWorkspace = { onRefreshWorkspace(task.id) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Policy Notice Banner when current user cannot complete/delete this ticket
            if (task.stage != Stage.DONE && !canCompleteOrDelete) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_policy_locked_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = P2Amber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, P2Amber.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Kebijakan akses tiket",
                            tint = P2Amber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = TaskPolicyHelper.unauthorizedReason(task),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Bottom Action Buttons: Patch Ke-2 (Ambil Task / Kerjakan / Selesai / Buka Kembali)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    task.stage == Stage.DONE -> {
                        Button(
                            onClick = { onReopenTask(task.id) },
                            enabled = !workspaceState.isUpdatingAction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sheet_reopen_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PingDuluNavy,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Buka Kembali",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    isUnassigned -> {
                        Button(
                            onClick = { onClaimTask(task.id) },
                            enabled = !workspaceState.isUpdatingAction,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sheet_claim_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PingDuluNavy,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AssignmentInd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_claim_task),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        if (canCompleteOrDelete) {
                            Button(
                                onClick = { onMarkCompleted(task.id) },
                                enabled = !workspaceState.isUpdatingAction,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("sheet_complete_button"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AvatarEmerald,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_complete_task),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    else -> {
                        val isAlreadyWorkingByMe =
                            task.stage == Stage.IN_PROGRESS && isCurrentUserAssigned
                        val workButtonLabel = when {
                            isAlreadyWorkingByMe -> "Sedang Dikerjakan"
                            task.stage == Stage.IN_PROGRESS -> "Ikut Kerjakan"
                            else -> stringResource(R.string.btn_work_task)
                        }
                        Button(
                            onClick = { onWorkTask(task.id) },
                            enabled = !isAlreadyWorkingByMe && !workspaceState.isUpdatingAction,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sheet_work_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PingDuluNavy,
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Handyman,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = workButtonLabel,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = { onMarkCompleted(task.id) },
                            enabled = canCompleteOrDelete && !workspaceState.isUpdatingAction,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sheet_complete_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AvatarEmerald,
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_complete_task),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            modifier = Modifier.testTag("delete_ticket_confirm_dialog"),
            title = {
                Text(
                    text = "Hapus Tiket ${task.ticketCode}?",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Tiket \"${task.title}\" akan dihapus permanen dari server dan perangkat ini. Tindakan ini tidak dapat dibatalkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteTask(task.id)
                    },
                    modifier = Modifier.testTag("delete_ticket_confirm_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_delete_task),
                        color = P1Crimson,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showSnoozeDialog) {
        SnoozeTicketDialog(
            onDismiss = { showSnoozeDialog = false },
            onSelectOption = { option -> onSnoozeTask(task.id, option) }
        )
    }

    if (showBlockedDialog) {
        BlockedReasonDialog(
            initialReason = task.blockedReason.orEmpty(),
            onDismiss = { showBlockedDialog = false },
            onConfirmBlocked = { reason -> onSetTaskBlocked(task.id, true, reason) }
        )
    }

    if (showDelegateDialog) {
        DelegateTechnicianDialog(
            ticketCode = task.ticketCode,
            currentAssignees = task.assignees,
            availableTechnicians = availableTechnicians,
            onDismiss = { showDelegateDialog = false },
            onConfirmSelection = { selectedIds ->
                onDelegateTechnicians(task.id, selectedIds)
            }
        )
    }

    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(
            attachment = attachment,
            serverBaseUrl = serverBaseUrl,
            authToken = authToken,
            onDismiss = { previewAttachment = null }
        )
    }
}
