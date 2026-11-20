package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StageUpdateDto(
    @Json(name = "stage")
    val stage: String
)

@JsonClass(generateAdapter = true)
data class TimerUpdateDto(
    @Json(name = "tracked_seconds")
    val trackedSeconds: Long,
    @Json(name = "is_running")
    val isRunning: Boolean,
    @Json(name = "timer_started_at_millis")
    val timerStartedAtMillis: Long? = null
)

@JsonClass(generateAdapter = true)
data class ChecklistSyncDto(
    @Json(name = "checklist")
    val checklist: List<ChecklistItemDto>
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
    val scheduledTimeMinutes: Int,
    @Json(name = "priority")
    val priority: String,
    @Json(name = "stage")
    val stage: String,
    @Json(name = "location")
    val location: String,
    @Json(name = "technical_notes")
    val technicalNotes: String,
    @Json(name = "sla_target_time")
    val slaTargetTime: String
)
