package com.example.presentation.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.domain.model.FieldTechnician
import com.example.domain.model.OfficialDefaultLabels
import com.example.domain.model.OfficialLabel
import com.example.domain.model.Priority
import com.example.domain.model.QuickTaskDraft
import com.example.domain.model.RecurrenceInterval
import com.example.domain.model.TicketCategory
import com.example.ui.theme.PingDuluNavy

enum class QuickAddScheduleMode {
    TODAY_INCIDENT,
    PLANNED_LATER
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddTaskSheet(
    todayEpochDay: Long = DateEpochUtil.todayEpochDay(),
    todayDateLabel: String = DateEpochUtil.formatFullDate(todayEpochDay),
    availableTechnicians: List<FieldTechnician> = emptyList(),
    officialLabels: List<OfficialLabel> = OfficialDefaultLabels,
    currentUserId: Long = 0L,
    currentTechnicianName: String = "",
    onDismiss: () -> Unit,
    onSubmitDraft: ((QuickTaskDraft) -> Unit)? = null,
    onSubmit: (
        title: String,
        category: TicketCategory,
        priority: Priority,
        location: String,
        scheduledTimeMinutes: Int?,
        technicalNotes: String,
        labels: List<String>,
        targetEpochDay: Long?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var checklistRawInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TicketCategory.NET) }
    var selectedPriority by remember { mutableStateOf(Priority.P2_HIGH) }
    var selectedLabelNames by remember { mutableStateOf(setOf<String>()) }
    var selectedAssigneeIds by remember { mutableStateOf(setOf<Long>()) }
    var hasUserEditedAssignees by remember { mutableStateOf(false) }
    var isRecurring by remember { mutableStateOf(false) }
    var selectedRecurrence by remember { mutableStateOf(RecurrenceInterval.WEEKLY) }
    var showTitleError by remember { mutableStateOf(false) }

    // Planned Work vs Today Incident state
    var scheduleMode by remember { mutableStateOf(QuickAddScheduleMode.TODAY_INCIDENT) }
    var plannedEpochDay by remember(todayEpochDay) { mutableLongStateOf(todayEpochDay + 1L) }
    var plannedTimeMinutes by remember { mutableIntStateOf(9 * 60) } // Default 09:00 WIB
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val resolvedLabels = remember(officialLabels) {
        officialLabels.ifEmpty { OfficialDefaultLabels }
    }

    // Auto-select current technician once catalog loads unless the user has manually edited delegation
    LaunchedEffect(availableTechnicians, currentUserId, hasUserEditedAssignees) {
        if (!hasUserEditedAssignees && currentUserId > 0L && selectedAssigneeIds.isEmpty()) {
            if (availableTechnicians.any { it.id == currentUserId }) {
                selectedAssigneeIds = setOf(currentUserId)
            }
        }
    }

    val parsedChecklistSteps = remember(checklistRawInput) {
        parseChecklistLines(checklistRawInput)
    }

    val minSelectableUtcMillis = remember(todayEpochDay) {
        DateEpochUtil.utcPickerMillisFromEpochDay(todayEpochDay)
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
                .verticalScroll(rememberScrollState())
                .testTag("quick_add_task_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tambah Tiket Lapangan Cepat",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = "Waktu lapor otomatis",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Waktu Lapor: Hari ini ($todayDateLabel) · Otomatis",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup form tambah tiket"
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (showTitleError && it.isNotBlank()) showTitleError = false
                },
                label = { Text("Judul Pekerjaan *") },
                placeholder = { Text("Contoh: Pengecekan Kabel Fiber Optic Lt. 3") },
                isError = showTitleError,
                supportingText = if (showTitleError) {
                    { Text("Judul tiket tidak boleh kosong") }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_add_title_input")
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Lokasi Lapangan") },
                placeholder = { Text("Contoh: Gedung Utama Lt. 3 / Ruang Server") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_add_location_input")
            )

            // Execution Schedule Mode: Today Incident vs Planned Later
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "WAKTU PELAKSANAAN KERJA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleModeOptionCard(
                        title = "Kerjakan Hari Ini",
                        subtitle = "Insiden langsung",
                        icon = Icons.Rounded.Bolt,
                        selected = scheduleMode == QuickAddScheduleMode.TODAY_INCIDENT,
                        onClick = { scheduleMode = QuickAddScheduleMode.TODAY_INCIDENT },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_add_mode_today")
                    )
                    ScheduleModeOptionCard(
                        title = "Jadwalkan Nanti",
                        subtitle = "Pekerjaan terencana",
                        icon = Icons.Rounded.EventAvailable,
                        selected = scheduleMode == QuickAddScheduleMode.PLANNED_LATER,
                        onClick = { scheduleMode = QuickAddScheduleMode.PLANNED_LATER },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_add_mode_planned")
                    )
                }

                AnimatedVisibility(visible = scheduleMode == QuickAddScheduleMode.PLANNED_LATER) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_add_planned_panel"),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(
                                    Triple("Besok", todayEpochDay + 1L, "quick_add_preset_tomorrow"),
                                    Triple("+2 Hari", todayEpochDay + 2L, "quick_add_preset_plus2"),
                                    Triple("+3 Hari", todayEpochDay + 3L, "quick_add_preset_plus3")
                                )
                                presets.forEach { (label, epoch, tag) ->
                                    FilterChip(
                                        selected = plannedEpochDay == epoch,
                                        onClick = { plannedEpochDay = epoch },
                                        label = {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PingDuluNavy,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag(tag)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    onClick = { showDatePickerDialog = true },
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .heightIn(min = 52.dp)
                                        .testTag("quick_add_date_picker_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CalendarMonth,
                                            contentDescription = "Pilih tanggal pelaksanaan",
                                            tint = PingDuluNavy,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "TANGGAL",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = DateEpochUtil.formatFullDate(plannedEpochDay),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    onClick = { showTimePickerDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("quick_add_time_picker_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = "Pilih jam pelaksanaan",
                                            tint = PingDuluNavy,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "JAM MULAI",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = DateEpochUtil.formatScheduledClock(plannedTimeMinutes),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
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
            }

            // Paket 2: Delegasi Teknisi (Multi-select from GET /api/users)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "TEKNISI BERTUGAS (DELEGASI)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedAssigneeIds.isNotEmpty()) {
                        Text(
                            text = "${selectedAssigneeIds.size} dipilih",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PingDuluNavy
                        )
                    }
                }

                if (availableTechnicians.isEmpty()) {
                    val activeLabel = currentTechnicianName.trim().ifBlank { "Teknisi Aktif" }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Default ditugaskan ke $activeLabel · Daftar tim dari server akan tampil otomatis saat online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableTechnicians.forEach { tech ->
                            val isSelected = tech.id in selectedAssigneeIds
                            val initial = tech.resolvedName.firstOrNull()?.uppercaseChar()?.toString() ?: "T"
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    hasUserEditedAssignees = true
                                    selectedAssigneeIds = if (isSelected) {
                                        selectedAssigneeIds - tech.id
                                    } else {
                                        selectedAssigneeIds + tech.id
                                    }
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.24f)
                                                else PingDuluNavy.copy(alpha = 0.14f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        } else {
                                            Text(
                                                text = initial,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PingDuluNavy
                                            )
                                        }
                                    }
                                },
                                label = {
                                    val suffix = if (tech.id == currentUserId) " (Anda)" else ""
                                    Text(
                                        text = "${tech.resolvedName}$suffix",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) PingDuluNavy else MaterialTheme.colorScheme.outline
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PingDuluNavy,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("quick_add_assignee_${tech.id}")
                            )
                        }
                    }
                }
            }

            // Category Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "KATEGORI TIKET",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TicketCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text("${category.code} · ${category.displayName}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PingDuluNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Priority Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PRIORITAS TIKET",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Priority.entries.forEach { priority ->
                        FilterChip(
                            selected = selectedPriority == priority,
                            onClick = { selectedPriority = priority },
                            label = { Text(priority.shortCode) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PingDuluNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Paket 2: Official 15 IT Ops Colored Label Badges from GET /api/labels
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LABEL IT OPS RESMI (OPSIONAL)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedLabelNames.isNotEmpty()) {
                        Text(
                            text = "${selectedLabelNames.size} label",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PingDuluNavy
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    resolvedLabels.forEach { label ->
                        val isSelected = label.name in selectedLabelNames
                        val dotColor = remember(label.colorHex) {
                            parseSafeHexColor(label.colorHex, PingDuluNavy)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedLabelNames = if (isSelected) {
                                    selectedLabelNames - label.name
                                } else {
                                    selectedLabelNames + label.name
                                }
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else dotColor)
                                )
                            },
                            label = { Text(label.name) },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) dotColor else MaterialTheme.colorScheme.outline
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = dotColor,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("quick_add_label_${normalizeLabelTag(label.name)}")
                        )
                    }
                }
            }

            // Paket 2: Custom Initial Checklist Steps (Multi-line, 1 line per step)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "CHECKLIST AWAL PENGERJAAN (OPSIONAL)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = checklistRawInput,
                    onValueChange = { checklistRawInput = it },
                    placeholder = {
                        Text("Tulis 1 langkah per baris, contoh:\nCek power supply switch\nUji ping ke gateway\nDokumentasi foto rak")
                    },
                    supportingText = {
                        val helperText = if (parsedChecklistSteps.isNotEmpty()) {
                            "${parsedChecklistSteps.size} langkah checklist kustom siap ditambahkan"
                        } else {
                            "1 baris = 1 item checklist · Kosongkan untuk pakai checklist standar kategori"
                        }
                        Text(helperText)
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_checklist_input")
                )
            }

            // Paket 2: Auto-Repeat / Recurring Task Schedule
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isRecurring) {
                    PingDuluNavy.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                border = BorderStroke(
                    width = if (isRecurring) 1.5.dp else 1.dp,
                    color = if (isRecurring) PingDuluNavy else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { isRecurring = !isRecurring },
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("quick_add_recurring_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isRecurring,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PingDuluNavy,
                                    checkmarkColor = Color.White
                                )
                            )
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = null,
                                tint = if (isRecurring) PingDuluNavy else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Jadwalkan Tugas Rutin (Auto-Repeat)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isRecurring) PingDuluNavy else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Server otomatis membuat tiket periode berikutnya saat selesai",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = isRecurring) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_add_recurrence_options"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RecurrenceInterval.entries.forEach { interval ->
                                val isChosen = selectedRecurrence == interval
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { selectedRecurrence = interval },
                                    label = { Text(interval.displayLabel) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PingDuluNavy,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("quick_add_recurrence_${interval.apiValue.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Catatan Teknis (Opsional)") },
                placeholder = { Text("Detail perangkat, VLAN, atau kontak PIC lokasi") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_add_notes_input")
            )

            val submitLabel = if (scheduleMode == QuickAddScheduleMode.PLANNED_LATER) {
                val dayShort = DateEpochUtil.formatShortDayMonth(plannedEpochDay)
                val clockShort = DateEpochUtil.formatScheduledClockCompact(plannedTimeMinutes)
                "Jadwalkan Tiket ($dayShort · $clockShort)"
            } else {
                "Simpan Tiket ke Jadwal Hari Ini"
            }

            Button(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        val isPlanned = scheduleMode == QuickAddScheduleMode.PLANNED_LATER
                        val resolvedTargetEpoch = if (isPlanned) plannedEpochDay else todayEpochDay
                        val resolvedTimeMinutes = if (isPlanned) plannedTimeMinutes else null
                        val resolvedLocation = location.trim().ifBlank { "Lokasi Lapangan" }
                        val chosenLabelList = selectedLabelNames.toList()
                        val chosenLabelIds = resolvedLabels
                            .filter { it.name in selectedLabelNames && it.id > 0L }
                            .map { it.id }
                        val resolvedAssignees = selectedAssigneeIds.filter { it > 0L }.distinct()
                        val draft = QuickTaskDraft(
                            title = title.trim(),
                            category = selectedCategory,
                            priority = selectedPriority,
                            location = resolvedLocation,
                            scheduledTimeMinutes = resolvedTimeMinutes,
                            technicalNotes = notes.trim(),
                            labels = chosenLabelList,
                            labelIds = chosenLabelIds,
                            assigneeIds = resolvedAssignees,
                            checklistSteps = parsedChecklistSteps,
                            isRecurring = isRecurring,
                            recurrenceInterval = if (isRecurring) selectedRecurrence else null,
                            targetEpochDay = resolvedTargetEpoch
                        )
                        if (onSubmitDraft != null) {
                            onSubmitDraft(draft)
                        } else {
                            onSubmit(
                                draft.title,
                                draft.category,
                                draft.priority,
                                draft.location,
                                draft.scheduledTimeMinutes,
                                draft.technicalNotes,
                                draft.labels,
                                draft.targetEpochDay
                            )
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("quick_add_submit_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PingDuluNavy,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (scheduleMode == QuickAddScheduleMode.PLANNED_LATER) {
                        Icons.Rounded.EventAvailable
                    } else {
                        Icons.Rounded.AddTask
                    },
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = submitLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateEpochUtil.utcPickerMillisFromEpochDay(plannedEpochDay),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= minSelectableUtcMillis
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val pickedEpoch = DateEpochUtil.epochDayFromUtcPickerMillis(millis)
                            plannedEpochDay = pickedEpoch.coerceAtLeast(todayEpochDay)
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("quick_add_date_dialog_confirm")
                ) {
                    Text("Pilih Tanggal", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = (plannedTimeMinutes / 60).coerceIn(0, 23),
            initialMinute = (plannedTimeMinutes % 60).coerceIn(0, 59),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = {
                Text(
                    text = "Pilih Jam Pelaksanaan",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        plannedTimeMinutes = timePickerState.hour * 60 + timePickerState.minute
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.testTag("quick_add_time_dialog_confirm")
                ) {
                    Text("Simpan Jam", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

private fun parseChecklistLines(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.lineSequence()
        .map { line ->
            line.trim()
                .removePrefix("- ")
                .removePrefix("* ")
                .removePrefix("• ")
                .replace(Regex("^\\d+[.)]\\s+"), "")
                .trim()
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
}

private fun normalizeLabelTag(labelName: String): String {
    return labelName.trim().lowercase()
        .replace("/", "_")
        .replace(" ", "_")
}

private fun parseSafeHexColor(hex: String, fallback: Color): Color {
    val clean = hex.trim()
    if (clean.isEmpty()) return fallback
    val normalized = if (clean.startsWith("#")) clean else "#$clean"
    return runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrDefault(fallback)
}

@Composable
private fun ScheduleModeOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            PingDuluNavy.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) PingDuluNavy else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = PingDuluNavy,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) PingDuluNavy else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) PingDuluNavy else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
