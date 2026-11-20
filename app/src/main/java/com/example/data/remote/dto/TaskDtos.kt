package com.example.data.remote.dto

import com.example.data.local.TaskEntity
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.TicketCategory
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TaskResponseDto(
    @Json(name = "data")
    val data: List<TaskDto>? = null,
    @Json(name = "tasks")
    val tasks: List<TaskDto>? = null
) {
    fun resolvedTasks(): List<TaskDto> = data ?: tasks ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class ChecklistItemDto(
    @Json(name = "id")
    val id: Long,
    @Json(name = "label")
    val label: String,
    @Json(name = "is_done")
    val isDone: Int = 0
)

@JsonClass(generateAdapter = true)
data class AssigneeDto(
    @Json(name = "id")
    val id: Long,
    @Json(name = "name")
    val name: String,
    @Json(name = "role")
    val role: String = "technician",
    @Json(name = "is_lead")
    val isLead: Int = 0
)

@JsonClass(generateAdapter = true)
data class TaskDto(
    @Json(name = "id")
    val id: Long,
    @Json(name = "ticket_code")
    val ticketCode: String,
    @Json(name = "title")
    val title: String,
    @Json(name = "project_or_category")
    val projectOrCategory: String? = null,
    @Json(name = "scheduled_date_epoch_day")
    val scheduledDateEpochDay: Long? = null,
    @Json(name = "scheduled_time_minutes")
    val scheduledTimeMinutes: Int? = null,
    @Json(name = "priority")
    val priority: String? = null,
    @Json(name = "stage")
    val stage: String? = null,
    @Json(name = "technical_notes")
    val technicalNotes: String? = null,
    @Json(name = "tracked_seconds")
    val trackedSeconds: Long? = null,
    @Json(name = "timer_started_at_millis")
    val timerStartedAtMillis: Long? = null,
    @Json(name = "location")
    val location: String? = null,
    @Json(name = "sla_target_time")
    val slaTargetTime: String? = null,
    @Json(name = "checklist")
    val checklist: List<ChecklistItemDto>? = null,
    @Json(name = "assignees")
    val assignees: List<AssigneeDto>? = null
)

fun ChecklistItemDto.toDomain(): ChecklistItem = ChecklistItem(
    id = id,
    label = label,
    isDone = isDone != 0
)

fun ChecklistItem.toDto(): ChecklistItemDto = ChecklistItemDto(
    id = id,
    label = label,
    isDone = if (isDone) 1 else 0
)

fun AssigneeDto.toDomain(): Assignee = Assignee(
    id = id,
    name = name,
    role = role,
    isLead = isLead != 0
)

fun TaskDto.toEntity(nowMillis: Long = System.currentTimeMillis()): TaskEntity {
    val startMinutes = scheduledTimeMinutes ?: 540
    val resolvedCategory = TicketCategory.fromString(projectOrCategory)
    val resolvedPriority = Priority.fromString(priority)
    val resolvedStage = Stage.fromString(stage)
    val isRunning = resolvedStage == Stage.IN_PROGRESS && timerStartedAtMillis != null && timerStartedAtMillis > 0L

    return TaskEntity(
        id = id,
        ticketCode = ticketCode.ifBlank { "${resolvedCategory.code}-$id" },
        title = title,
        category = resolvedCategory.code,
        scheduledDateEpochDay = scheduledDateEpochDay ?: DateEpochUtil.todayEpochDay(nowMillis),
        scheduledTimeMinutes = startMinutes,
        timeSlotLabel = DateEpochUtil.formatMinutesToTimeSlot(startMinutes),
        priority = resolvedPriority.apiValue,
        stage = resolvedStage.apiValue,
        technicalNotes = technicalNotes.orEmpty(),
        trackedSeconds = (trackedSeconds ?: 0L).coerceAtLeast(0L),
        isTimerRunning = isRunning,
        timerStartedAtMillis = if (isRunning) timerStartedAtMillis else null,
        location = location.orEmpty().ifBlank { "Lokasi Lapangan" },
        slaTargetTime = slaTargetTime.orEmpty().ifBlank { "17:00 WIB" },
        checklist = checklist.orEmpty().map { it.toDomain() },
        assignees = assignees.orEmpty().map { it.toDomain() },
        isSynced = true,
        updatedAtMillis = nowMillis
    )
}
