package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequestDto(
    @Json(name = "email")
    val email: String,
    @Json(name = "password")
    val password: String
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id")
    val id: Long = 0L,
    @Json(name = "name")
    val name: String = "Teknisi",
    @Json(name = "role")
    val role: String = "technician"
)

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
