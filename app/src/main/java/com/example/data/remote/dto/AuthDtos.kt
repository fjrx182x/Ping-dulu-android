package com.example.data.remote.dto

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
    val role: String = "technician"
) {
    val resolvedName: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: name?.trim()?.takeIf { it.isNotEmpty() }
            ?: username?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Teknisi"
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
