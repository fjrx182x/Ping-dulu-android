package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Assignee
import com.example.domain.model.FieldTechnician
import com.example.ui.theme.AvatarEmerald
import com.example.ui.theme.PingDuluNavy

/**
 * Patch Ke-7 Technician Delegation Picker Dialog.
 * Allows Admin or currently assigned technicians to add/manage technicians on an active ticket
 * via `PATCH /api/tasks/:id` with 5-active-task WIP enforcement.
 */
@Composable
internal fun DelegateTechnicianDialog(
    ticketCode: String,
    currentAssignees: List<Assignee>,
    availableTechnicians: List<FieldTechnician>,
    onDismiss: () -> Unit,
    onConfirmSelection: (List<Long>) -> Unit
) {
    val initialIds = remember(currentAssignees) {
        currentAssignees.mapNotNull { it.id.takeIf { id -> id > 0L } }
    }
    val selectedIds = remember(currentAssignees) {
        mutableStateListOf<Long>().apply { addAll(initialIds) }
    }

    // Merge server catalog with existing assignees so currently assigned techs are never hidden
    val mergedCandidates = remember(availableTechnicians, currentAssignees) {
        val byId = LinkedHashMap<Long, FieldTechnician>()
        availableTechnicians.filter { it.id > 0L }.forEach { byId[it.id] = it }
        currentAssignees.filter { it.id > 0L }.forEach { assignee ->
            if (assignee.id !in byId) {
                byId[assignee.id] = FieldTechnician(
                    id = assignee.id,
                    username = assignee.name.lowercase().replace(" ", "."),
                    displayName = assignee.name,
                    role = assignee.role
                )
            }
        }
        byId.values.toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delegate_technician_dialog"),
        shape = RoundedCornerShape(22.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Delegasi Teknisi · $ticketCode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Pilih teknisi untuk bergabung mengerjakan tiket ini (batas maks. 5 task aktif per teknisi).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (mergedCandidates.isEmpty()) {
                Text(
                    text = "Daftar teknisi belum termuat. Tarik sinkronisasi server terlebih dahulu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mergedCandidates.forEach { tech ->
                        val isChecked = tech.id in selectedIds
                        val isLead = selectedIds.firstOrNull() == tech.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .toggleable(
                                    value = isChecked,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        if (checked) {
                                            if (tech.id !in selectedIds) selectedIds.add(tech.id)
                                        } else if (selectedIds.size > 1) {
                                            selectedIds.remove(tech.id)
                                        }
                                    }
                                )
                                .testTag("delegate_tech_option_${tech.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChecked) {
                                PingDuluNavy.copy(alpha = 0.10f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isChecked) PingDuluNavy.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AvatarEmerald,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isLead) "${tech.resolvedName} (Lead)" else tech.resolvedName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (tech.role.equals("admin", ignoreCase = true)) {
                                            "Admin Operasional"
                                        } else {
                                            "Teknisi Lapangan"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        onConfirmSelection(selectedIds.toList())
                    }
                    onDismiss()
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("delegate_tech_confirm_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PingDuluNavy,
                    contentColor = Color.White
                )
            ) {
                Text("Simpan Tim", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
