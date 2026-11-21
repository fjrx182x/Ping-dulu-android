package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class TaskAttachment(
    val id: Long,
    val taskId: Long,
    val fileName: String,
    val fileUrl: String,
    val mimeType: String = "application/octet-stream",
    val fileSizeBytes: Long = 0L,
    val uploadedBy: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val isImage: Boolean
        get() {
            val lowerMime = mimeType.lowercase()
            if (lowerMime.startsWith("image/")) return true
            val lowerName = fileName.lowercase()
            return lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp") ||
                lowerName.endsWith(".gif")
        }

    fun resolvedFullUrl(baseUrl: String): String {
        val raw = fileUrl.trim()
        if (raw.isEmpty()) return ""
        if (raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true) ||
            raw.startsWith("content://", ignoreCase = true) ||
            raw.startsWith("file://", ignoreCase = true)
        ) {
            return raw
        }
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (raw.startsWith("/")) raw else "/$raw"
        return "$cleanBase$cleanPath"
    }

    val formattedSizeLabel: String
        get() = when {
            fileSizeBytes <= 0L -> ""
            fileSizeBytes < 1024L -> "${fileSizeBytes} B"
            fileSizeBytes < 1024L * 1024L -> "${fileSizeBytes / 1024L} KB"
            else -> String.format("%.1f MB", fileSizeBytes.toDouble() / (1024.0 * 1024.0))
        }
}

@Immutable
data class TaskComment(
    val id: Long,
    val taskId: Long,
    val authorName: String,
    val authorRole: String = "technician",
    val commentText: String,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val authorInitial: String
        get() = authorName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T"
}

enum class ActivityCategory {
    CREATED,
    STAGE_CHANGED,
    TIMER,
    CHECKLIST,
    ATTACHMENT,
    COMMENT,
    BLOCKED_OR_SNOOZED,
    ASSIGNMENT,
    GENERAL
}

@Immutable
data class TaskActivityItem(
    val id: Long,
    val taskId: Long,
    val actorName: String,
    val action: String,
    val detail: String = "",
    val category: ActivityCategory = ActivityCategory.GENERAL,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val summaryText: String
        get() = when {
            detail.isNotBlank() && action.isNotBlank() && !detail.contains(action, ignoreCase = true) ->
                "$action — $detail"
            detail.isNotBlank() -> detail
            action.isNotBlank() -> action
            else -> "Aktivitas tiket diperbarui"
        }
}

@Immutable
data class TaskActivityFeed(
    val items: List<TaskActivityItem> = emptyList(),
    val isFromServer: Boolean = false
)

enum class SnoozeOption(
    val idTag: String,
    val label: String,
    val subtitle: String
) {
    ONE_HOUR("1h", "Tunda 1 Jam", "Geser pengingat +60 menit (WIB)"),
    THREE_HOURS("3h", "Tunda 3 Jam", "Geser pengingat +3 jam (WIB)"),
    TOMORROW_MORNING("tomorrow", "Besok Pagi (09:00 WIB)", "Jadwalkan ulang ke besok jam 09:00")
}

@Immutable
data class TaskDetailWorkspaceState(
    val taskId: Long? = null,
    val attachments: List<TaskAttachment> = emptyList(),
    val comments: List<TaskComment> = emptyList(),
    val activities: List<TaskActivityItem> = emptyList(),
    val isActivitiesFromServer: Boolean = false,
    val isLoadingAttachments: Boolean = false,
    val isUploadingAttachment: Boolean = false,
    val isLoadingComments: Boolean = false,
    val isLoadingActivities: Boolean = false,
    val isPostingComment: Boolean = false,
    val isUpdatingAction: Boolean = false,
    val statusNotice: String? = null
) {
    companion object {
        const val MAX_ATTACHMENT_BYTES: Long = 10L * 1024L * 1024L // 10 MB server limit
    }
}
