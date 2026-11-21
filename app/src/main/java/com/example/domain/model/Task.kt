package com.example.domain.model

data class Task(
    val id: Long,
    val ticketCode: String,
    val title: String,
    val category: TicketCategory,
    val scheduledDateEpochDay: Long,
    val scheduledTimeMinutes: Int? = null,
    val timeSlotLabel: String,
    val priority: Priority,
    val stage: Stage,
    val technicalNotes: String,
    val trackedSeconds: Long,
    val isTimerRunning: Boolean,
    val timerStartedAtMillis: Long?,
    val location: String,
    val slaTargetTime: String,
    val checklist: List<ChecklistItem>,
    val assignees: List<Assignee>,
    val isSynced: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
    val assignedToId: Long? = null,
    val labels: List<String> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val clientSyncId: String? = null,
    val isRecurring: Boolean = false,
    val recurrenceInterval: String? = null,
    val isBlocked: Boolean = false,
    val blockedReason: String? = null,
    val snoozedUntilMillis: Long? = null,
    val attachments: List<TaskAttachment> = emptyList(),
    val createdById: Long? = null,
    val createdByName: String? = null,
    val creatorClient: String? = null,
    val creatorRole: String? = null
) {
    val completedChecklistCount: Int
        get() = checklist.count { it.isDone }

    val totalChecklistCount: Int
        get() = checklist.size

    val checklistProgressFraction: Float
        get() = if (checklist.isEmpty()) 0f else completedChecklistCount.toFloat() / checklist.size.toFloat()

    val checklistProgressPercent: Int
        get() = (checklistProgressFraction * 100f).toInt()

    /**
     * Resolves display labels for cards and detail sheets: prefers server-provided labels,
     * falling back to semantic IT field tags derived from ticket category, title, and priority.
     */
    val resolvedLabels: List<String>
        get() {
            val cleaned = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleaned.isNotEmpty()) return cleaned

            val derived = mutableListOf<String>()
            val lowerTitle = title.lowercase()
            if (lowerTitle.contains("wi-fi") || lowerTitle.contains("wifi") || lowerTitle.contains("access point")) {
                derived += "Wi-Fi"
            }
            derived += when (category) {
                TicketCategory.NET -> "Jaringan"
                TicketCategory.SRV -> "Server"
                TicketCategory.HRD -> "Hardware"
                TicketCategory.SEC -> "Keamanan"
                TicketCategory.ONB -> "Onboarding"
            }
            if (priority == Priority.P1_CRITICAL) {
                derived += "Urgent"
            }
            return derived.distinct()
        }

    val hasScheduledClock: Boolean
        get() = scheduledTimeMinutes != null

    val scheduledClockLabel: String?
        get() = scheduledTimeMinutes?.let { DateEpochUtil.formatScheduledClock(it) }

    fun isPlannedWork(todayEpochDay: Long = DateEpochUtil.todayEpochDay()): Boolean {
        return scheduledTimeMinutes != null || scheduledDateEpochDay > todayEpochDay
    }

    fun isAssignedToUser(userId: Long): Boolean {
        if (userId <= 0L) return false
        return assignedToId == userId || assignees.any { it.id == userId }
    }
}
