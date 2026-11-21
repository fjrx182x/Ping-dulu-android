package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.TicketCategory
import com.example.ui.theme.PingDuluNavy
import java.util.Calendar

private val QuickLabelPresets = listOf(
    "Wi-Fi",
    "Jaringan",
    "Server",
    "Hardware",
    "Keamanan",
    "Urgent"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddTaskSheet(
    todayDateLabel: String = DateEpochUtil.formatFullDate(DateEpochUtil.todayEpochDay()),
    onDismiss: () -> Unit,
    onSubmit: (
        title: String,
        category: TicketCategory,
        priority: Priority,
        location: String,
        scheduledTimeMinutes: Int?,
        technicalNotes: String,
        labels: List<String>
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TicketCategory.NET) }
    var selectedPriority by remember { mutableStateOf(Priority.P2_HIGH) }
    var selectedLabels by remember { mutableStateOf(setOf<String>()) }
    var showTitleError by remember { mutableStateOf(false) }

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

            // Optional Colored Label Badges Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LABEL TIKET (OPSIONAL)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickLabelPresets.forEach { label ->
                        val isSelected = label in selectedLabels
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedLabels = if (isSelected) {
                                    selectedLabels - label
                                } else {
                                    selectedLabels + label
                                }
                            },
                            label = { Text(label) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PingDuluNavy,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("quick_add_label_${label.lowercase()}")
                        )
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
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        onSubmit(
                            title.trim(),
                            selectedCategory,
                            selectedPriority,
                            location.trim().ifBlank { "Lokasi Lapangan" },
                            null,
                            notes.trim(),
                            selectedLabels.toList()
                        )
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
                Icon(imageVector = Icons.Rounded.AddTask, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Simpan Tiket ke Jadwal Hari Ini",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}
