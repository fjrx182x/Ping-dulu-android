package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TaskAttachment
import com.example.domain.model.TicketCategory

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["scheduled_date_epoch_day"]),
        Index(value = ["stage"]),
        Index(value = ["is_synced"]),
        Index(value = ["client_sync_id"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "ticket_code")
    val ticketCode: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "scheduled_date_epoch_day")
    val scheduledDateEpochDay: Long,

    @ColumnInfo(name = "scheduled_time_minutes")
    val scheduledTimeMinutes: Int? = null,

    @ColumnInfo(name = "time_slot_label")
    val timeSlotLabel: String,

    @ColumnInfo(name = "priority")
    val priority: String,

    @ColumnInfo(name = "stage")
    val stage: String,

    @ColumnInfo(name = "technical_notes")
    val technicalNotes: String,

    @ColumnInfo(name = "tracked_seconds")
    val trackedSeconds: Long,

    @ColumnInfo(name = "is_timer_running")
    val isTimerRunning: Boolean,

    @ColumnInfo(name = "timer_started_at_millis")
    val timerStartedAtMillis: Long?,

    @ColumnInfo(name = "location")
    val location: String,

    @ColumnInfo(name = "sla_target_time")
    val slaTargetTime: String,

    @ColumnInfo(name = "checklist_json")
    val checklist: List<ChecklistItem>,

    @ColumnInfo(name = "assignees_json")
    val assignees: List<Assignee>,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean,

    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAtMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "due_date")
    val dueDateMillis: Long? = null,

    @ColumnInfo(name = "assigned_to_id")
    val assignedToId: Long? = null,

    @ColumnInfo(name = "labels_json", defaultValue = "'[]'")
    val labels: List<String> = emptyList(),

    @ColumnInfo(name = "updated_at_millis")
    val updatedAtMillis: Long,

    @ColumnInfo(name = "client_sync_id")
    val clientSyncId: String? = null,

    @ColumnInfo(name = "is_recurring", defaultValue = "0")
    val isRecurring: Boolean = false,

    @ColumnInfo(name = "recurrence_interval")
    val recurrenceInterval: String? = null,

    @ColumnInfo(name = "is_blocked", defaultValue = "0")
    val isBlocked: Boolean = false,

    @ColumnInfo(name = "blocked_reason")
    val blockedReason: String? = null,

    @ColumnInfo(name = "snoozed_until_millis")
    val snoozedUntilMillis: Long? = null,

    @ColumnInfo(name = "attachments_json", defaultValue = "'[]'")
    val attachments: List<TaskAttachment> = emptyList(),

    @ColumnInfo(name = "created_by_id")
    val createdById: Long? = null,

    @ColumnInfo(name = "created_by_name")
    val createdByName: String? = null,

    @ColumnInfo(name = "creator_client")
    val creatorClient: String? = null,

    @ColumnInfo(name = "creator_role")
    val creatorRole: String? = null
)

fun TaskEntity.toDomain(): Task {
    return Task(
        id = id,
        ticketCode = ticketCode,
        title = title,
        category = TicketCategory.fromString(category),
        scheduledDateEpochDay = scheduledDateEpochDay,
        scheduledTimeMinutes = scheduledTimeMinutes,
        timeSlotLabel = timeSlotLabel,
        priority = Priority.fromString(priority),
        stage = Stage.fromString(stage),
        technicalNotes = technicalNotes,
        trackedSeconds = trackedSeconds.coerceAtLeast(0L),
        isTimerRunning = isTimerRunning,
        timerStartedAtMillis = timerStartedAtMillis,
        location = location,
        slaTargetTime = slaTargetTime,
        checklist = checklist,
        assignees = assignees,
        isSynced = isSynced,
        createdAtMillis = if (createdAtMillis > 0L) createdAtMillis else updatedAtMillis,
        dueDateMillis = dueDateMillis,
        assignedToId = assignedToId,
        labels = labels,
        updatedAtMillis = updatedAtMillis,
        clientSyncId = clientSyncId,
        isRecurring = isRecurring,
        recurrenceInterval = recurrenceInterval,
        isBlocked = isBlocked,
        blockedReason = blockedReason,
        snoozedUntilMillis = snoozedUntilMillis,
        attachments = attachments,
        createdById = createdById,
        createdByName = createdByName,
        creatorClient = creatorClient,
        creatorRole = creatorRole
    )
}

fun Task.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        ticketCode = ticketCode,
        title = title,
        category = category.code,
        scheduledDateEpochDay = scheduledDateEpochDay,
        scheduledTimeMinutes = scheduledTimeMinutes,
        timeSlotLabel = timeSlotLabel,
        priority = priority.apiValue,
        stage = stage.apiValue,
        technicalNotes = technicalNotes,
        trackedSeconds = trackedSeconds.coerceAtLeast(0L),
        isTimerRunning = isTimerRunning,
        timerStartedAtMillis = timerStartedAtMillis,
        location = location,
        slaTargetTime = slaTargetTime,
        checklist = checklist,
        assignees = assignees,
        isSynced = isSynced,
        createdAtMillis = createdAtMillis,
        dueDateMillis = dueDateMillis,
        assignedToId = assignedToId,
        labels = labels,
        updatedAtMillis = updatedAtMillis,
        clientSyncId = clientSyncId,
        isRecurring = isRecurring,
        recurrenceInterval = recurrenceInterval,
        isBlocked = isBlocked,
        blockedReason = blockedReason,
        snoozedUntilMillis = snoozedUntilMillis,
        attachments = attachments,
        createdById = createdById,
        createdByName = createdByName,
        creatorClient = creatorClient,
        creatorRole = creatorRole
    )
}
