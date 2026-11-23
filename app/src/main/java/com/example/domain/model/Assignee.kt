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
    val isLead: Boolean = false,
    @Json(name = "username")
    val username: String? = null,
    @Json(name = "avatar_url")
    val avatarUrl: String? = null,
    @Json(name = "department")
    val department: String? = null,
    @Json(name = "sub_department")
    val subDepartment: String? = null,
    @Json(name = "is_active")
    val isActive: Int = 1
) {
    val initial: String
        get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    val isUserActive: Boolean
        get() = isActive == 1

    val departmentDisplay: String?
        get() = formatDepartmentDisplay(department, subDepartment)
}
