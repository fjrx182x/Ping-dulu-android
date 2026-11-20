package com.example.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChecklistItem(
    @Json(name = "id")
    val id: Long,
    @Json(name = "label")
    val label: String,
    @Json(name = "is_done")
    val isDone: Boolean = false
)
