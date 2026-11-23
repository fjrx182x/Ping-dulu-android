package com.example.data.remote.dto

import com.example.domain.model.UserProfile
import com.example.domain.model.formatDepartmentDisplay
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequestDto(
    @Json(name = "username")
    val username: String,
    @Json(name = "password")
    val password: String
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id")
    val id: Long = 0L,
    @Json(name = "username")
    val username: String? = null,
    @Json(name = "display_name")
    val displayName: String? = null,
    @Json(name = "name")
    val name: String? = null,
    @Json(name = "role")
    val role: String = "technician",
    @Json(name = "avatar_url")
    val avatarUrl: String? = null,
    @Json(name = "department")
    val department: String? = null,
    @Json(name = "sub_department")
    val subDepartment: String? = null,
    @Json(name = "is_active")
    val isActive: Any? = null,
    @Json(name = "created_at")
    val createdAt: Any? = null,
    @Json(name = "updated_at")
    val updatedAt: Any? = null
) {
    val resolvedName: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: name?.trim()?.takeIf { it.isNotEmpty() }
            ?: username?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Teknisi"

    val resolvedUsername: String
        get() = username?.trim().orEmpty()

    val resolvedAvatarUrl: String?
        get() = avatarUrl?.trim()?.takeIf { it.isNotEmpty() }

    val resolvedDepartment: String?
        get() = department?.trim()?.takeIf { it.isNotEmpty() }

    val resolvedSubDepartment: String?
        get() = subDepartment?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Defaults to 1 (active) when the backend omits `is_active` for backward compatibility.
     */
    val resolvedIsActive: Int
        get() = when (isActive) {
            null -> 1
            else -> if (parseFlexibleBoolean(isActive)) 1 else 0
        }

    val isUserActive: Boolean
        get() = resolvedIsActive == 1

    val departmentDisplay: String?
        get() = formatDepartmentDisplay(resolvedDepartment, resolvedSubDepartment)

    fun toUserProfile(): UserProfile = UserProfile(
        id = id,
        username = resolvedUsername,
        displayName = resolvedName,
        role = role.trim().ifBlank { "technician" },
        avatarUrl = resolvedAvatarUrl,
        department = resolvedDepartment,
        subDepartment = resolvedSubDepartment,
        isActive = resolvedIsActive
    )
}

@JsonClass(generateAdapter = true)
data class LoginResponseDto(
    @Json(name = "token")
    val token: String? = null,
    @Json(name = "access_token")
    val accessToken: String? = null,
    @Json(name = "user")
    val user: UserDto? = null
) {
    val resolvedToken: String?
        get() = token?.takeIf { it.isNotBlank() } ?: accessToken?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class AuthMeResponseDto(
    @Json(name = "success")
    val success: Boolean? = null,
    @Json(name = "user")
    val user: UserDto? = null,
    @Json(name = "data")
    val data: UserDto? = null
) {
    val resolvedUser: UserDto?
        get() = user ?: data
}

@JsonClass(generateAdapter = true)
data class AvatarUploadResponseDto(
    @Json(name = "success")
    val success: Boolean? = null,
    @Json(name = "avatar_url")
    val avatarUrl: String? = null,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "error")
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class UserMutationResponseDto(
    @Json(name = "success")
    val success: Boolean? = null,
    @Json(name = "user")
    val user: UserDto? = null,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "error")
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateUserRequestDto(
    @Json(name = "display_name")
    val displayName: String? = null,
    @Json(name = "password")
    val password: String? = null,
    @Json(name = "department")
    val department: String? = null,
    @Json(name = "sub_department")
    val subDepartment: String? = null
)
