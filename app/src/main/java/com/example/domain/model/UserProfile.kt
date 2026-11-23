package com.example.domain.model

import androidx.compose.runtime.Immutable

const val DEACTIVATED_ACCOUNT_MESSAGE =
    "Akun Anda telah dinonaktifkan oleh administrator. Silakan hubungi tim IT/Admin."

class AccountDeactivatedException(
    override val message: String = DEACTIVATED_ACCOUNT_MESSAGE
) : IllegalStateException(message)

@Immutable
data class UserProfile(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: String = "technician",
    val avatarUrl: String? = null,
    val department: String? = null,
    val subDepartment: String? = null,
    val isActive: Int = 1,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null
) {
    val isUserActive: Boolean
        get() = isActive == 1

    val isAdmin: Boolean
        get() = role.trim().equals("admin", ignoreCase = true)

    val isTechnician: Boolean
        get() = isAdmin || role.trim().equals("technician", ignoreCase = true)

    val resolvedName: String
        get() = displayName.trim().ifBlank { username.trim().ifBlank { "Teknisi" } }

    val departmentDisplay: String?
        get() = formatDepartmentDisplay(department, subDepartment)
}

fun formatDepartmentDisplay(department: String?, subDepartment: String?): String? {
    val parts = listOfNotNull(
        department?.trim()?.takeIf { it.isNotEmpty() },
        subDepartment?.trim()?.takeIf { it.isNotEmpty() }
    )
    return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
}

/**
 * Resolves a relative server avatar path (e.g. "/api/avatars/avatar_10_1795372899.png")
 * into a full absolute URL using [serverBaseUrl]. Returns null when [avatarUrl] is null/blank.
 */
fun resolveAvatarUrl(serverBaseUrl: String, avatarUrl: String?): String? {
    val clean = avatarUrl?.trim()
    if (clean.isNullOrEmpty()) return null
    if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
        return clean
    }
    val cleanBase = serverBaseUrl.trim().trimEnd('/')
    val cleanPath = clean.trimStart('/')
    if (cleanBase.isEmpty()) return "/$cleanPath"
    return "$cleanBase/$cleanPath"
}

/**
 * Detects whether an HTTP 403 / error JSON payload indicates a soft-deactivated account (`is_active = 0`).
 */
fun isDeactivatedAccountErrorPayload(rawBody: String?): Boolean {
    if (rawBody.isNullOrBlank()) return false
    val lower = rawBody.lowercase()
    return lower.contains("dinonaktifkan") ||
        lower.contains("nonaktif") ||
        lower.contains("deactivated") ||
        lower.contains("account_disabled") ||
        lower.contains("account disabled") ||
        lower.contains("user is inactive") ||
        lower.contains("is_active")
}
