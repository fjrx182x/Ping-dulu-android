package com.example.data.remote.dto

import com.example.domain.model.ActivityCategory
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Stage
import com.example.domain.model.TaskActivityItem
import com.example.domain.model.TaskAttachment
import com.example.domain.model.TaskComment
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.json.JSONArray
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class AddCommentRequestDto(
    @Json(name = "comment_text")
    val commentText: String
)

@JsonClass(generateAdapter = true)
data class AddChecklistStepRequestDto(
    @Json(name = "label")
    val label: String
)

@JsonClass(generateAdapter = true)
data class SpeedTestRecordRequestDto(
    @Json(name = "ping_ms")
    val pingMs: Double,
    @Json(name = "jitter_ms")
    val jitterMs: Double,
    @Json(name = "download_mbps")
    val downloadMbps: Double,
    @Json(name = "upload_mbps")
    val uploadMbps: Double,
    @Json(name = "client_ip")
    val clientIp: String? = null,
    @Json(name = "isp_name")
    val ispName: String? = null,
    @Json(name = "notes")
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class TaskPatchRequestDto(
    @Json(name = "tracked_seconds")
    val trackedSeconds: Long? = null,
    @Json(name = "scheduled_date_epoch_day")
    val scheduledDateEpochDay: Long? = null,
    @Json(name = "scheduled_time_minutes")
    val scheduledTimeMinutes: Int? = null,
    @Json(name = "snoozed_until")
    val snoozedUntilMillis: Long? = null,
    @Json(name = "is_blocked")
    val isBlocked: Int? = null,
    @Json(name = "blocked_reason")
    val blockedReason: String? = null,
    @Json(name = "assignee_ids")
    val assigneeIds: List<Long>? = null,
    @Json(name = "assigned_to_id")
    val assignedToId: Long? = null
)

/**
 * Crash-proof parser for Paket 3 endpoints:
 * - GET /api/tasks/{id} (embedded attachments), GET /api/tasks/{id}/attachments & POST /api/tasks/{id}/attachments
 * - GET /api/tasks/{id}/comments & POST /api/tasks/{id}/comments
 * Tolerates bare arrays `[...]`, wrapped `{ "attachments"/"comments"/"data"/"task"/"items": [...] }`,
 * single-object upload/post responses `{ "attachment": {...} }` / `{ "comment": {...} }`, and null fields.
 */
object TaskDetailResponseParser {

    fun parseAttachmentsList(
        rawJson: String,
        fallbackTaskId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TaskAttachment> {
        val array = extractAttachmentsArray(rawJson) ?: return emptyList()
        val result = mutableListOf<TaskAttachment>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseAttachmentObject(obj, fallbackTaskId, nowMillis, fallbackIndex = i)?.let { result += it }
        }
        return result.distinctBy { if (it.id > 0L) "id:${it.id}" else "url:${it.fileUrl}:${it.fileName}" }
    }

    fun parseSingleAttachment(
        rawJson: String,
        fallbackTaskId: Long,
        fallbackFileName: String,
        fallbackMimeType: String,
        fallbackSizeBytes: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): TaskAttachment {
        val trimmed = rawJson.trim()
        val parsed = runCatching {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val target = root.optJSONObject("attachment")
                    ?: root.optJSONObject("file")
                    ?: root.optJSONObject("data")
                    ?: root
                parseAttachmentObject(target, fallbackTaskId, nowMillis, fallbackIndex = 0)
            } else {
                null
            }
        }.getOrNull()

        return parsed ?: TaskAttachment(
            id = nowMillis,
            taskId = fallbackTaskId,
            fileName = fallbackFileName,
            fileUrl = "",
            mimeType = fallbackMimeType,
            fileSizeBytes = fallbackSizeBytes,
            createdAtMillis = nowMillis
        )
    }

    fun parseCommentsList(
        rawJson: String,
        fallbackTaskId: Long,
        nowMillis: Long = System.currentTimeMillis(),
        userNameById: Map<Long, String> = emptyMap(),
        userNameByUsername: Map<String, String> = emptyMap(),
        fallbackAuthorName: String = "Teknisi"
    ): List<TaskComment> {
        val array = extractJsonArray(rawJson, listOf("comments", "data", "items")) ?: return emptyList()
        val result = mutableListOf<TaskComment>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseCommentObject(
                obj = obj,
                fallbackTaskId = fallbackTaskId,
                nowMillis = nowMillis,
                fallbackIndex = i,
                userNameById = userNameById,
                userNameByUsername = userNameByUsername,
                fallbackAuthorName = fallbackAuthorName
            )?.let { result += it }
        }
        return result.sortedBy { it.createdAtMillis }
    }

    fun parseSingleComment(
        rawJson: String,
        fallbackTaskId: Long,
        fallbackAuthorName: String,
        fallbackAuthorRole: String,
        fallbackText: String,
        nowMillis: Long = System.currentTimeMillis(),
        userNameById: Map<Long, String> = emptyMap(),
        userNameByUsername: Map<String, String> = emptyMap()
    ): TaskComment {
        val cleanFallbackAuthor = fallbackAuthorName.ifBlank { "Teknisi" }
        val trimmed = rawJson.trim()
        val parsed = runCatching {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val target = root.optJSONObject("comment")
                    ?: root.optJSONObject("data")
                    ?: root
                parseCommentObject(
                    obj = target,
                    fallbackTaskId = fallbackTaskId,
                    nowMillis = nowMillis,
                    fallbackIndex = 0,
                    userNameById = userNameById,
                    userNameByUsername = userNameByUsername,
                    fallbackAuthorName = cleanFallbackAuthor,
                    fallbackAuthorRole = fallbackAuthorRole
                )
            } else {
                null
            }
        }.getOrNull()

        return parsed?.takeIf { it.commentText.isNotBlank() } ?: TaskComment(
            id = nowMillis,
            taskId = fallbackTaskId,
            authorName = cleanFallbackAuthor,
            authorRole = fallbackAuthorRole.ifBlank { "technician" },
            commentText = fallbackText.trim(),
            createdAtMillis = nowMillis
        )
    }

    private fun extractAttachmentsArray(rawJson: String): JSONArray? {
        val direct = extractJsonArray(rawJson, listOf("attachments", "files", "items", "data"))
        if (direct != null) return direct
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val root = JSONObject(trimmed)
            val nestedTask = root.optJSONObject("task") ?: root.optJSONObject("data")
            nestedTask?.optJSONArray("attachments") ?: nestedTask?.optJSONArray("files")
        }.getOrNull()
    }

    private fun parseAttachmentObject(
        obj: JSONObject,
        fallbackTaskId: Long,
        nowMillis: Long,
        fallbackIndex: Int
    ): TaskAttachment? {
        val fileUrl = obj.optCleanString("file_url")
            .ifBlank { obj.optCleanString("url") }
            .ifBlank { obj.optCleanString("path") }
            .ifBlank { obj.optCleanString("download_url") }
        val fileName = obj.optCleanString("original_name")
            .ifBlank { obj.optCleanString("file_name") }
            .ifBlank { obj.optCleanString("filename") }
            .ifBlank { obj.optCleanString("name") }
            .ifBlank { fileUrl.substringAfterLast('/').ifBlank { "Lampiran #${fallbackIndex + 1}" } }
        if (fileName.isBlank() && fileUrl.isBlank()) return null

        val id = obj.optLong("id", 0L).takeIf { it > 0L } ?: (nowMillis + fallbackIndex)
        val taskId = obj.optLong("task_id", fallbackTaskId).takeIf { it > 0L } ?: fallbackTaskId
        val mimeType = obj.optCleanString("mime_type")
            .ifBlank { obj.optCleanString("mimetype") }
            .ifBlank { obj.optCleanString("content_type") }
            .ifBlank { guessMimeTypeFromName(fileName) }
        val sizeBytes = obj.optLong("file_size", obj.optLong("size", 0L)).coerceAtLeast(0L)
        val uploaderObj = obj.optJSONObject("user") ?: obj.optJSONObject("uploader")
        val uploadedBy = obj.optCleanString("uploaded_by_name")
            .ifBlank { obj.optCleanString("author_name") }
            .ifBlank { uploaderObj?.optCleanString("display_name").orEmpty() }
            .ifBlank { uploaderObj?.optCleanString("username").orEmpty() }
        val createdRaw = if (obj.isNull("created_at")) null else obj.opt("created_at")
        val createdAt = DateEpochUtil.parseTimestampMillis(createdRaw, fallbackMillis = nowMillis)

        return TaskAttachment(
            id = id,
            taskId = taskId,
            fileName = fileName,
            fileUrl = fileUrl,
            mimeType = mimeType,
            fileSizeBytes = sizeBytes,
            uploadedBy = uploadedBy,
            createdAtMillis = createdAt
        )
    }

    private fun parseCommentObject(
        obj: JSONObject,
        fallbackTaskId: Long,
        nowMillis: Long,
        fallbackIndex: Int,
        userNameById: Map<Long, String> = emptyMap(),
        userNameByUsername: Map<String, String> = emptyMap(),
        fallbackAuthorName: String = "Teknisi",
        fallbackAuthorRole: String = "technician"
    ): TaskComment? {
        val text = obj.optCleanString("comment_text")
            .ifBlank { obj.optCleanString("content") }
            .ifBlank { obj.optCleanString("body") }
            .ifBlank { obj.optCleanString("text") }
            .ifBlank { obj.optCleanString("message") }
        if (text.isBlank()) return null

        val id = obj.optLong("id", 0L).takeIf { it > 0L } ?: (nowMillis + fallbackIndex)
        val taskId = obj.optLong("task_id", fallbackTaskId).takeIf { it > 0L } ?: fallbackTaskId
        val authorObj = obj.optJSONObject("user") ?: obj.optJSONObject("author")
        val plainAuthorString = (obj.opt("author") as? String)?.trim().orEmpty()
        val plainCreatedByString = (obj.opt("created_by") as? String)?.trim()
            ?.takeIf { it.isNotEmpty() && it.toLongOrNull() == null }
            .orEmpty()
        val authorUserId = obj.optLong("user_id", 0L)
            .takeIf { it > 0L }
            ?: obj.optLong("author_id", 0L).takeIf { it > 0L }
            ?: obj.optLong("created_by", 0L).takeIf { it > 0L }
            ?: obj.optLong("posted_by", 0L).takeIf { it > 0L }
            ?: obj.optLong("sender_id", 0L).takeIf { it > 0L }
            ?: authorObj?.optLong("id", 0L)?.takeIf { it > 0L }

        val rawUsername = obj.optCleanString("username")
            .ifBlank { obj.optCleanString("user_username") }
            .ifBlank { obj.optCleanString("author_username") }
            .ifBlank { authorObj?.optCleanString("username").orEmpty() }

        val explicitDisplayName = obj.optCleanString("author_name")
            .ifBlank { obj.optCleanString("author_display_name") }
            .ifBlank { obj.optCleanString("user_display_name") }
            .ifBlank { obj.optCleanString("display_name") }
            .ifBlank { obj.optCleanString("full_name") }
            .ifBlank { obj.optCleanString("created_by_name") }
            .ifBlank { obj.optCleanString("technician_name") }
            .ifBlank { obj.optCleanString("sender_name") }
            .ifBlank { obj.optCleanString("user_name") }
            .ifBlank { obj.optCleanString("name") }
            .ifBlank { plainAuthorString }
            .ifBlank { plainCreatedByString }
            .ifBlank { authorObj?.optCleanString("display_name").orEmpty() }
            .ifBlank { authorObj?.optCleanString("full_name").orEmpty() }
            .ifBlank { authorObj?.optCleanString("name").orEmpty() }

        val authorName = explicitDisplayName
            .ifBlank { authorUserId?.let { userNameById[it] }.orEmpty() }
            .ifBlank { rawUsername.lowercase().let { userNameByUsername[it] }.orEmpty() }
            .ifBlank { rawUsername }
            .ifBlank { fallbackAuthorName.ifBlank { "Teknisi" } }
        val authorRole = obj.optCleanString("author_role")
            .ifBlank { obj.optCleanString("user_role") }
            .ifBlank { obj.optCleanString("role") }
            .ifBlank { authorObj?.optCleanString("role").orEmpty() }
            .ifBlank { fallbackAuthorRole.ifBlank { "technician" } }
        val createdRaw = if (obj.isNull("created_at")) null else obj.opt("created_at")
        val createdAt = DateEpochUtil.parseTimestampMillis(createdRaw, fallbackMillis = nowMillis)

        return TaskComment(
            id = id,
            taskId = taskId,
            authorName = authorName,
            authorRole = authorRole,
            commentText = text,
            createdAtMillis = createdAt
        )
    }

    fun hasEmbeddedActivitiesArray(rawJson: String): Boolean = extractActivitiesArray(rawJson) != null

    fun parseActivitiesList(
        rawJson: String,
        fallbackTaskId: Long,
        nowMillis: Long = System.currentTimeMillis(),
        userNameById: Map<Long, String> = emptyMap(),
        userNameByUsername: Map<String, String> = emptyMap()
    ): List<TaskActivityItem> {
        val array = extractActivitiesArray(rawJson) ?: return emptyList()
        val result = mutableListOf<TaskActivityItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseActivityObject(
                obj = obj,
                fallbackTaskId = fallbackTaskId,
                nowMillis = nowMillis,
                fallbackIndex = i,
                userNameById = userNameById,
                userNameByUsername = userNameByUsername
            )?.let { result += it }
        }
        return result
            .distinctBy { if (it.id > 0L) "id:${it.id}" else "ts:${it.createdAtMillis}:${it.summaryText}" }
            .sortedByDescending { it.createdAtMillis }
    }

    private fun extractActivitiesArray(rawJson: String): JSONArray? {
        val keys = listOf("activities", "activity_logs", "activity", "history", "logs", "audit_logs", "data", "items")
        val direct = extractJsonArray(rawJson, keys)
        if (direct != null) return direct
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val root = JSONObject(trimmed)
            val nested = root.optJSONObject("task") ?: root.optJSONObject("data")
            if (nested != null) {
                listOf("activities", "activity_logs", "activity", "history", "logs", "audit_logs")
                    .firstNotNullOfOrNull { key -> nested.optJSONArray(key) }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun parseActivityObject(
        obj: JSONObject,
        fallbackTaskId: Long,
        nowMillis: Long,
        fallbackIndex: Int,
        userNameById: Map<Long, String>,
        userNameByUsername: Map<String, String>
    ): TaskActivityItem? {
        val rawAction = obj.optCleanString("action")
            .ifBlank { obj.optCleanString("event_type") }
            .ifBlank { obj.optCleanString("event") }
            .ifBlank { obj.optCleanString("type") }
        val rawDetail = obj.optCleanString("description")
            .ifBlank { obj.optCleanString("message") }
            .ifBlank { obj.optCleanString("details") }
            .ifBlank { obj.optCleanString("detail") }
            .ifBlank { obj.optCleanString("summary") }
            .ifBlank { obj.optCleanString("note") }
            .ifBlank { obj.optCleanString("metadata_text") }
        val oldVal = obj.optCleanString("old_value").ifBlank { obj.optCleanString("from_stage") }
        val newVal = obj.optCleanString("new_value").ifBlank { obj.optCleanString("to_stage") }
        if (rawAction.isBlank() && rawDetail.isBlank() && newVal.isBlank()) return null

        val id = obj.optLong("id", 0L).takeIf { it > 0L } ?: (nowMillis + fallbackIndex)
        val taskId = obj.optLong("task_id", 0L).takeIf { it > 0L }
            ?: obj.optLong("work_item_id", 0L).takeIf { it > 0L }
            ?: fallbackTaskId
        val actorObj = obj.optJSONObject("user") ?: obj.optJSONObject("actor")
        val plainActor = (obj.opt("actor") as? String)?.trim().orEmpty()
        val actorUserId = obj.optLong("user_id", 0L).takeIf { it > 0L }
            ?: obj.optLong("actor_id", 0L).takeIf { it > 0L }
            ?: obj.optLong("created_by", 0L).takeIf { it > 0L }
            ?: actorObj?.optLong("id", 0L)?.takeIf { it > 0L }

        val rawUsername = obj.optCleanString("username")
            .ifBlank { obj.optCleanString("actor_username") }
            .ifBlank { obj.optCleanString("user_username") }
            .ifBlank { actorObj?.optCleanString("username").orEmpty() }

        val explicitActor = obj.optCleanString("actor_name")
            .ifBlank { obj.optCleanString("user_display_name") }
            .ifBlank { obj.optCleanString("author_name") }
            .ifBlank { obj.optCleanString("display_name") }
            .ifBlank { obj.optCleanString("full_name") }
            .ifBlank { obj.optCleanString("user_name") }
            .ifBlank { obj.optCleanString("technician_name") }
            .ifBlank { obj.optCleanString("created_by_name") }
            .ifBlank { plainActor }
            .ifBlank { actorObj?.optCleanString("display_name").orEmpty() }
            .ifBlank { actorObj?.optCleanString("full_name").orEmpty() }
            .ifBlank { actorObj?.optCleanString("name").orEmpty() }

        val actorName = explicitActor
            .ifBlank { actorUserId?.let { userNameById[it] }.orEmpty() }
            .ifBlank { rawUsername.lowercase().let { userNameByUsername[it] }.orEmpty() }
            .ifBlank { rawUsername }
            .ifBlank { "Sistem" }

        val category = classifyActivityCategory(rawAction, rawDetail)
        val humanAction = humanizeActivityAction(rawAction, rawDetail, oldVal, newVal)
        val finalDetail = when {
            rawDetail.isNotBlank() -> rawDetail
            oldVal.isNotBlank() && newVal.isNotBlank() ->
                "${humanizeStageToken(oldVal)} → ${humanizeStageToken(newVal)}"
            newVal.isNotBlank() -> humanizeStageToken(newVal)
            else -> ""
        }

        val createdRaw = when {
            !obj.isNull("created_at") -> obj.opt("created_at")
            !obj.isNull("timestamp") -> obj.opt("timestamp")
            !obj.isNull("logged_at") -> obj.opt("logged_at")
            !obj.isNull("occurred_at") -> obj.opt("occurred_at")
            !obj.isNull("updated_at") -> obj.opt("updated_at")
            else -> null
        }
        val createdAt = DateEpochUtil.parseTimestampMillis(createdRaw, fallbackMillis = nowMillis)

        return TaskActivityItem(
            id = id,
            taskId = taskId,
            actorName = actorName,
            action = humanAction,
            detail = finalDetail,
            category = category,
            createdAtMillis = createdAt
        )
    }

    private fun classifyActivityCategory(rawAction: String, rawDetail: String): ActivityCategory {
        val combined = "$rawAction $rawDetail".lowercase()
        return when {
            combined.contains("create") || combined.contains("dibuat") || combined.contains("membuat") ->
                ActivityCategory.CREATED
            combined.contains("mengambil tiket") || combined.contains("claim") ||
                combined.contains("bergabung mengerjakan") || combined.contains("assign") ||
                combined.contains("delegasi") || combined.contains("didelegasikan") ->
                ActivityCategory.ASSIGNMENT
            combined.contains("langkah ") || combined.contains("check") ->
                ActivityCategory.CHECKLIST
            combined.contains("dikerjakan oleh") || combined.contains("diselesaikan oleh") ||
                combined.contains("dipindahkan ke") || combined.contains("stage") ||
                combined.contains("status") || combined.contains("selesai") ||
                combined.contains("done") || combined.contains("proses") || combined.contains("move") ->
                ActivityCategory.STAGE_CHANGED
            combined.contains("timer") || combined.contains("stopwatch") || combined.contains("waktu") ->
                ActivityCategory.TIMER
            combined.contains("attach") || combined.contains("upload") || combined.contains("foto") ||
                combined.contains("lampiran") || combined.contains("file") ->
                ActivityCategory.ATTACHMENT
            combined.contains("comment") || combined.contains("komentar") || combined.contains("diskusi") ->
                ActivityCategory.COMMENT
            combined.contains("block") || combined.contains("blokir") || combined.contains("snooze") ||
                combined.contains("tunda") ->
                ActivityCategory.BLOCKED_OR_SNOOZED
            else -> ActivityCategory.GENERAL
        }
    }

    private fun humanizeActivityAction(
        rawAction: String,
        rawDetail: String,
        oldVal: String,
        newVal: String
    ): String {
        val trimmed = rawAction.trim()
        if (trimmed.isEmpty()) return ""
        // Preserve Patch Ke-2 natural Indonesian narratives directly from server without altering them.
        val isNaturalNarrative = trimmed.contains(' ') && (
            trimmed.contains("oleh ", ignoreCase = true) ||
                trimmed.contains("mengambil tiket", ignoreCase = true) ||
                trimmed.contains("bergabung mengerjakan", ignoreCase = true) ||
                trimmed.startsWith("📌") ||
                trimmed.startsWith("Tiket ", ignoreCase = true) ||
                trimmed.startsWith("Langkah ", ignoreCase = true) ||
                trimmed.startsWith("Tahap ", ignoreCase = true)
            )
        if (isNaturalNarrative) return trimmed

        val looksLikeCodeToken = !trimmed.contains(' ') && (trimmed.contains('_') || trimmed == trimmed.lowercase() || trimmed == trimmed.uppercase())
        if (!looksLikeCodeToken) return trimmed
        return when (trimmed.lowercase()) {
            "created", "task_created", "create_task" -> "Membuat tiket"
            "stage_changed", "status_changed", "update_stage", "stage_updated", "moved" -> "Mengubah status"
            "work_started", "started_work", "task_worked" -> "Mulai mengerjakan tiket"
            "claimed", "task_claimed" -> "Mengambil tiket ini"
            "timer_started", "start_timer", "timer_start" -> "Mulai mengerjakan tiket"
            "timer_paused", "pause_timer", "timer_pause", "timer_stopped" -> "Menjeda pengerjaan"
            "checklist_toggled", "checklist_updated", "checklist_checked" -> "Memperbarui checklist"
            "attachment_uploaded", "upload_attachment", "file_uploaded" -> "Mengunggah lampiran"
            "comment_added", "comment_created", "add_comment" -> "Menambahkan komentar"
            "snoozed", "task_snoozed" -> "Menunda tiket (Snooze)"
            "blocked", "task_blocked" -> "Menandai terblokir"
            "unblocked", "task_unblocked" -> "Melepas blokir"
            "assigned", "assignee_updated", "delegated" -> "Memperbarui teknisi"
            "updated", "task_updated", "edited" -> if (rawDetail.isBlank() && oldVal.isBlank() && newVal.isBlank()) {
                "Memperbarui detail tiket"
            } else {
                "Memperbarui tiket"
            }
            else -> trimmed.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
        }
    }

    private fun humanizeStageToken(value: String): String {
        val upper = value.trim().uppercase()
        return when (upper) {
            Stage.BACKLOG.apiValue, "TODO", "OPEN", "PENDING" -> Stage.BACKLOG.displayLabel
            Stage.IN_PROGRESS.apiValue, "PROGRESS", "DOING", "ACTIVE" -> Stage.IN_PROGRESS.displayLabel
            "WAITING_USER", "WAITING", "PENDING_USER", "ON_HOLD" -> "Menunggu User"
            Stage.DONE.apiValue, "COMPLETED", "CLOSED" -> Stage.DONE.displayLabel
            else -> value.trim()
        }
    }

    private fun guessMimeTypeFromName(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".pdf") -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    /**
     * Crash-proof parser for `POST /api/tasks/{id}/checklist` responses.
     * Supports flat `{ id, label, is_done }`, wrapped `{ step: {...} }`, `{ checklist_step: {...} }`,
     * `{ data: {...} }`, or `{ task: { checklist: [...] } }`.
     */
    fun parseAddedChecklistStep(
        rawJson: String,
        fallbackLabel: String,
        fallbackTempId: Long
    ): ChecklistItem {
        val trimmed = rawJson.trim()
        val parsed = runCatching {
            if (!trimmed.startsWith("{")) return@runCatching null
            val root = JSONObject(trimmed)
            val candidateObj = root.optJSONObject("step")
                ?: root.optJSONObject("checklist_step")
                ?: root.optJSONObject("item")
                ?: root.optJSONObject("data")?.takeIf { it.has("id") || it.has("label") || it.has("title") }
                ?: run {
                    val nestedList = root.optJSONObject("task")?.optJSONArray("checklist")
                        ?: root.optJSONArray("checklist")
                    if (nestedList != null && nestedList.length() > 0) {
                        var match: JSONObject? = null
                        for (i in nestedList.length() - 1 downTo 0) {
                            val obj = nestedList.optJSONObject(i) ?: continue
                            val text = obj.optCleanString("label")
                                .ifEmpty { obj.optCleanString("title") }
                                .ifEmpty { obj.optCleanString("text") }
                            if (text.equals(fallbackLabel.trim(), ignoreCase = true)) {
                                match = obj
                                break
                            }
                        }
                        match ?: nestedList.optJSONObject(nestedList.length() - 1)
                    } else {
                        root
                    }
                }

            val resolvedId = candidateObj?.optLong("id", 0L)?.takeIf { it > 0L } ?: fallbackTempId
            val resolvedLabel = candidateObj?.optCleanString("label")
                ?.ifEmpty { candidateObj.optCleanString("title") }
                ?.ifEmpty { candidateObj.optCleanString("text") }
                ?.takeIf { it.isNotEmpty() }
                ?: fallbackLabel.trim()
            val rawDone = candidateObj?.opt("is_done")
            val resolvedDone = when (rawDone) {
                is Boolean -> rawDone
                is Number -> rawDone.toInt() != 0
                is String -> rawDone == "1" || rawDone.equals("true", ignoreCase = true)
                else -> false
            }
            ChecklistItem(
                id = resolvedId,
                label = resolvedLabel,
                isDone = resolvedDone
            )
        }.getOrNull()

        return parsed ?: ChecklistItem(
            id = fallbackTempId,
            label = fallbackLabel.trim(),
            isDone = false
        )
    }

    private fun extractJsonArray(rawJson: String, wrapperKeys: List<String>): JSONArray? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                wrapperKeys.firstNotNullOfOrNull { key -> root.optJSONArray(key) }
            }
        }.getOrNull()
    }

    private fun JSONObject.optCleanString(key: String): String {
        if (isNull(key)) return ""
        return optString(key, "").trim()
    }
}
