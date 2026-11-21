package com.example.data.remote.dto

import com.example.domain.model.DateEpochUtil
import com.example.domain.model.NationalHoliday
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkTaskResponseDto(
    @Json(name = "success")
    val success: Boolean? = null,
    @Json(name = "stage")
    val stage: String? = null,
    @Json(name = "is_collaborator")
    val isCollaborator: Any? = null,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "task")
    val task: TaskDto? = null
) {
    val isCollaboratorFlag: Boolean
        get() = parseFlexibleBoolean(isCollaborator)
}

@JsonClass(generateAdapter = true)
data class ClaimTaskResponseDto(
    @Json(name = "success")
    val success: Boolean? = null,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "task")
    val task: TaskDto? = null
)

@JsonClass(generateAdapter = true)
data class HolidayItemDto(
    @Json(name = "date")
    val date: String? = null,
    @Json(name = "epoch_day")
    val epochDay: Long? = null,
    @Json(name = "name")
    val name: String? = null,
    @Json(name = "is_cuti_bersama")
    val isCutiBersama: Any? = null
) {
    fun toDomain(): NationalHoliday? {
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cleanDate = date?.trim().orEmpty()
        val resolvedEpochDay = epochDay?.takeIf { it > 15_000L }
            ?: cleanDate.takeIf { it.isNotEmpty() }?.let { DateEpochUtil.dateToEpochDay(it) }
            ?: return null
        return NationalHoliday(
            date = cleanDate.ifBlank { DateEpochUtil.toIsoDateString(resolvedEpochDay) },
            epochDay = resolvedEpochDay,
            name = cleanName,
            isCutiBersama = parseFlexibleBoolean(isCutiBersama)
        )
    }
}

@JsonClass(generateAdapter = true)
data class HolidayResponseDto(
    @Json(name = "year")
    val year: Int? = null,
    @Json(name = "holidays")
    val holidays: List<HolidayItemDto>? = null
)

@JsonClass(generateAdapter = true)
data class CalendarAgendaResponseDto(
    @Json(name = "year")
    val year: Int? = null,
    @Json(name = "month")
    val month: Int? = null,
    @Json(name = "holidays")
    val holidays: List<HolidayItemDto>? = null,
    @Json(name = "tasks")
    val tasks: List<TaskDto>? = null
)
