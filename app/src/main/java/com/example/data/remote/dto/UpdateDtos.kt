package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StageUpdateDto(
    @Json(name = "stage")
    val stage: String
)

@JsonClass(generateAdapter = true)
data class TimerActionRequestDto(
    @Json(name = "action")
    val action: String // "start" | "pause" | "reset"
) {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESET = "reset"
    }
}

@JsonClass(generateAdapter = true)
data class ChecklistStepToggleDto(
    @Json(name = "is_done")
    val isDone: Int // 1 untuk selesai, 0 untuk belum
)

@JsonClass(generateAdapter = true)
data class CreateTaskRequestDto(
    @Json(name = "ticket_code")
    val ticketCode: String,
    @Json(name = "title")
    val title: String,
    @Json(name = "project_or_category")
    val projectOrCategory: String,
    @Json(name = "scheduled_date_epoch_day")
    val scheduledDateEpochDay: Long,
    @Json(name = "scheduled_time_minutes")
    val scheduledTimeMinutes: Int? = null,
    @Json(name = "priority")
    val priority: String,
    @Json(name = "stage")
    val stage: String,
    @Json(name = "location")
    val location: String,
    @Json(name = "technical_notes")
    val technicalNotes: String,
    @Json(name = "sla_target_time")
    val slaTargetTime: String? = null,
    @Json(name = "assigned_to_id")
    val assignedToId: Long? = null,
    @Json(name = "assignee_ids")
    val assigneeIds: List<Long>? = null,
    @Json(name = "labels")
    val labels: List<String>? = null,
    @Json(name = "label_ids")
    val labelIds: List<Long>? = null,
    @Json(name = "checklist")
    val checklist: List<String>? = null,
    @Json(name = "is_recurring")
    val isRecurring: Int = 0,
    @Json(name = "recurrence_interval")
    val recurrenceInterval: String? = null,
    @Json(name = "created_at")
    val createdAt: Long? = null,
    @Json(name = "client_sync_id")
    val clientSyncId: String? = null
)
