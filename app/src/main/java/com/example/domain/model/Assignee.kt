package com.example.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Assignee(
    @Json(name = "id")
    val id: Long,
    @Json(name = "name")
    val name: String,
    @Json(name = "role")
    val role: String = "technician",
    @Json(name = "is_lead")
    val isLead: Boolean = false
) {
    val initial: String
        get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}
