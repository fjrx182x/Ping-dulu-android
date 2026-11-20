package com.example.domain.model

data class Task(
    val id: Long,
    val ticketCode: String,
    val title: String,
    val category: TicketCategory,
    val scheduledDateEpochDay: Long,
    val scheduledTimeMinutes: Int,
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
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val completedChecklistCount: Int
        get() = checklist.count { it.isDone }

    val totalChecklistCount: Int
        get() = checklist.size

    val checklistProgressFraction: Float
        get() = if (checklist.isEmpty()) 0f else completedChecklistCount.toFloat() / checklist.size.toFloat()

    val checklistProgressPercent: Int
        get() = (checklistProgressFraction * 100f).toInt()
}
