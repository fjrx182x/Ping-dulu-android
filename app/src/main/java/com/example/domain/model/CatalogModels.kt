package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class RecurrenceInterval(
    val apiValue: String,
    val displayLabel: String
) {
    DAILY("DAILY", "Harian"),
    WEEKDAYS("WEEKDAYS", "Hari Kerja"),
    WEEKLY("WEEKLY", "Mingguan"),
    MONTHLY("MONTHLY", "Bulanan");

    companion object {
        fun fromString(raw: String?): RecurrenceInterval? {
            if (raw.isNullOrBlank()) return null
            val normalized = raw.trim().uppercase()
            return entries.firstOrNull { it.apiValue == normalized }
        }
    }
}

@Immutable
data class FieldTechnician(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: String
) {
    val resolvedName: String
        get() = displayName.trim().ifBlank { username.trim().ifBlank { "Teknisi #$id" } }

    val roleDisplayLabel: String
        get() = when (role.trim().lowercase()) {
            "admin" -> "Admin"
            "lead", "supervisor" -> "Lead"
            else -> "Teknisi"
        }
}

@Immutable
data class OfficialLabel(
    val id: Long,
    val name: String,
    val colorHex: String
)

/**
 * Complete offline-first catalog of PingDulu's 15 official IT Ops labels.
 * Positive server IDs from GET /api/labels replace these negative fallback IDs once online.
 */
val OfficialDefaultLabels: List<OfficialLabel> = listOf(
    OfficialLabel(id = -1L, name = "Jaringan", colorHex = "#36459B"),
    OfficialLabel(id = -2L, name = "Server", colorHex = "#7C3AED"),
    OfficialLabel(id = -3L, name = "Keamanan", colorHex = "#E11D48"),
    OfficialLabel(id = -4L, name = "Hardware", colorHex = "#D97706"),
    OfficialLabel(id = -5L, name = "Wi-Fi", colorHex = "#2563EB"),
    OfficialLabel(id = -6L, name = "CCTV", colorHex = "#0891B2"),
    OfficialLabel(id = -7L, name = "Printer", colorHex = "#EA580C"),
    OfficialLabel(id = -8L, name = "Email/Akun", colorHex = "#0D9488"),
    OfficialLabel(id = -9L, name = "Urgent", colorHex = "#EF4444"),
    OfficialLabel(id = -10L, name = "Maintenance", colorHex = "#10B981"),
    OfficialLabel(id = -11L, name = "Hotfix", colorHex = "#F43F5E"),
    OfficialLabel(id = -12L, name = "Database", colorHex = "#6366F1"),
    OfficialLabel(id = -13L, name = "Cloud", colorHex = "#0284C7"),
    OfficialLabel(id = -14L, name = "Kabel/LAN", colorHex = "#4F46E5"),
    OfficialLabel(id = -15L, name = "Onboarding", colorHex = "#14B8A6")
)

@Immutable
data class QuickTaskDraft(
    val title: String,
    val category: TicketCategory,
    val priority: Priority,
    val location: String,
    val scheduledTimeMinutes: Int?,
    val technicalNotes: String,
    val labels: List<String>,
    val labelIds: List<Long>,
    val assigneeIds: List<Long>,
    val checklistSteps: List<String>,
    val isRecurring: Boolean,
    val recurrenceInterval: RecurrenceInterval?,
    val targetEpochDay: Long?
)
