package com.example.data.remote.dto

import com.example.data.local.TaskEntity
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.TaskAttachment
import com.example.domain.model.TicketCategory
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TaskResponseDto(
    @Json(name = "data")
    val data: List<TaskDto>? = null,
    @Json(name = "tasks")
    val tasks: List<TaskDto>? = null
) {
    fun hasExplicitTaskList(): Boolean = data != null || tasks != null

    fun resolvedTasks(): List<TaskDto> = data ?: tasks ?: emptyList()
}

/**
 * Parses SQLite integers (0/1), JSON booleans (true/false), numbers (1.0/0.0), or strings ("1"/"true")
 * without throwing Moshi JsonDataException when backend formats differ across endpoints.
 */
internal fun parseFlexibleBoolean(raw: Any?): Boolean = when (raw) {
    is Boolean -> raw
    is Number -> raw.toDouble() != 0.0
    is String -> {
        val trimmed = raw.trim().lowercase()
        trimmed == "1" || trimmed == "true" || trimmed == "yes"
    }
    else -> false
}

/**
 * Supports both flat string arrays `["Jaringan", "Wi-Fi"]` and object arrays
 * `[{"id": 1, "name": "Jaringan", "color": "#..."}]` returned by Fastify/SQLite joins.
 */
internal fun parseFlexibleLabels(raw: List<Any>?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return raw.mapNotNull { item ->
        when (item) {
            is String -> item.trim().takeIf { it.isNotEmpty() }
            is Map<*, *> -> {
                val candidate = item["name"] ?: item["label"] ?: item["title"]
                candidate?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }
            else -> null
        }
    }.distinct()
}

@JsonClass(generateAdapter = true)
data class ChecklistItemDto(
    @Json(name = "id")
    val id: Long? = null,
    @Json(name = "label")
    val label: String? = null,
    @Json(name = "is_done")
    val isDone: Any? = null,
    @Json(name = "sort_order")
    val sortOrder: Int? = null
)

@JsonClass(generateAdapter = true)
data class AssigneeDto(
    @Json(name = "id")
    val id: Long? = null,
    @Json(name = "username")
    val username: String? = null,
    @Json(name = "display_name")
    val displayName: String? = null,
    @Json(name = "name")
    val name: String? = null,
    @Json(name = "role")
    val role: String? = null,
    @Json(name = "is_lead")
    val isLead: Any? = null,
    @Json(name = "avatar_url")
    val avatarUrl: String? = null,
    @Json(name = "department")
    val department: String? = null,
    @Json(name = "sub_department")
    val subDepartment: String? = null,
    @Json(name = "is_active")
    val isActive: Any? = null
) {
    val resolvedName: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: name?.trim()?.takeIf { it.isNotEmpty() }
            ?: username?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Teknisi"

    val resolvedIsActive: Int
        get() = when (isActive) {
            null -> 1
            else -> if (parseFlexibleBoolean(isActive)) 1 else 0
        }
}

@JsonClass(generateAdapter = true)
data class AttachmentDto(
    @Json(name = "id")
    val id: Long? = null,
    @Json(name = "task_id")
    val taskId: Long? = null,
    @Json(name = "original_name")
    val originalName: String? = null,
    @Json(name = "file_name")
    val fileName: String? = null,
    @Json(name = "filename")
    val filenameLegacy: String? = null,
    @Json(name = "name")
    val nameLegacy: String? = null,
    @Json(name = "file_url")
    val fileUrl: String? = null,
    @Json(name = "url")
    val urlLegacy: String? = null,
    @Json(name = "path")
    val pathLegacy: String? = null,
    @Json(name = "mime_type")
    val mimeType: String? = null,
    @Json(name = "mimetype")
    val mimetypeLegacy: String? = null,
    @Json(name = "file_size")
    val fileSize: Long? = null,
    @Json(name = "size")
    val sizeLegacy: Long? = null,
    @Json(name = "uploaded_by_name")
    val uploadedByName: String? = null,
    @Json(name = "created_at")
    val createdAt: Any? = null
) {
    fun toDomain(fallbackTaskId: Long, nowMillis: Long, index: Int): TaskAttachment? {
        val resolvedUrl = fileUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: urlLegacy?.trim()?.takeIf { it.isNotEmpty() }
            ?: pathLegacy?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
        val resolvedName = originalName?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName?.trim()?.takeIf { it.isNotEmpty() }
            ?: filenameLegacy?.trim()?.takeIf { it.isNotEmpty() }
            ?: nameLegacy?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolvedUrl.substringAfterLast('/').takeIf { it.isNotEmpty() }
            ?: ""
        if (resolvedUrl.isEmpty() && resolvedName.isEmpty()) return null
        val resolvedMime = mimeType?.trim()?.takeIf { it.isNotEmpty() }
            ?: mimetypeLegacy?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (resolvedName.lowercase().endsWith(".png")) "image/png"
            else if (resolvedName.lowercase().endsWith(".jpg") || resolvedName.lowercase().endsWith(".jpeg")) "image/jpeg"
            else "application/octet-stream"
        return TaskAttachment(
            id = id?.takeIf { it > 0L } ?: (nowMillis + index),
            taskId = taskId?.takeIf { it > 0L } ?: fallbackTaskId,
            fileName = resolvedName.ifBlank { "Lampiran #${index + 1}" },
            fileUrl = resolvedUrl,
            mimeType = resolvedMime,
            fileSizeBytes = (fileSize ?: sizeLegacy ?: 0L).coerceAtLeast(0L),
            uploadedBy = uploadedByName.orEmpty().trim(),
            createdAtMillis = DateEpochUtil.parseTimestampMillis(createdAt, fallbackMillis = nowMillis)
        )
    }
}

@JsonClass(generateAdapter = true)
data class TaskDto(
    @Json(name = "id")
    val id: Long = 0L,
    @Json(name = "client_sync_id")
    val clientSyncId: String? = null,
    @Json(name = "ticket_code")
    val ticketCode: String? = null,
    @Json(name = "title")
    val title: String? = null,
    @Json(name = "project_or_category")
    val projectOrCategory: String? = null,
    @Json(name = "scheduled_date_epoch_day")
    val scheduledDateEpochDay: Long? = null,
    @Json(name = "scheduled_time_minutes")
    val scheduledTimeMinutes: Int? = null,
    @Json(name = "priority")
    val priority: String? = null,
    @Json(name = "stage")
    val stage: String? = null,
    @Json(name = "technical_notes")
    val technicalNotes: String? = null,
    @Json(name = "tracked_seconds")
    val trackedSeconds: Long? = null,
    @Json(name = "timer_started_at_millis")
    val timerStartedAtMillis: Long? = null,
    @Json(name = "location")
    val location: String? = null,
    @Json(name = "sla_target_time")
    val slaTargetTime: String? = null,
    @Json(name = "created_at")
    val createdAt: Any? = null,
    @Json(name = "updated_at")
    val updatedAt: Any? = null,
    @Json(name = "due_date")
    val dueDate: Any? = null,
    @Json(name = "assigned_to_id")
    val assignedToId: Long? = null,
    @Json(name = "assigned_to")
    val assignedToLegacyId: Long? = null,
    @Json(name = "assignee_name")
    val assigneeName: String? = null,
    @Json(name = "assigned_to_name")
    val assignedToName: String? = null,
    @Json(name = "assignee_ids")
    val assigneeIds: List<Long>? = null,
    @Json(name = "labels")
    val labels: List<Any>? = null,
    @Json(name = "tags")
    val tags: List<Any>? = null,
    @Json(name = "checklist")
    val checklist: List<ChecklistItemDto>? = null,
    @Json(name = "assignees")
    val assignees: List<AssigneeDto>? = null,
    @Json(name = "is_recurring")
    val isRecurring: Any? = null,
    @Json(name = "recurrence_interval")
    val recurrenceInterval: String? = null,
    @Json(name = "is_blocked")
    val isBlocked: Any? = null,
    @Json(name = "blocked_reason")
    val blockedReason: String? = null,
    @Json(name = "snoozed_until")
    val snoozedUntil: Any? = null,
    @Json(name = "attachments")
    val attachments: List<AttachmentDto>? = null,
    @Json(name = "created_by_id")
    val createdById: Long? = null,
    @Json(name = "created_by_name")
    val createdByName: String? = null,
    @Json(name = "creator_client")
    val creatorClient: String? = null,
    @Json(name = "creator_role")
    val creatorRole: String? = null
)

fun ChecklistItemDto.toDomain(): ChecklistItem {
    val safeId = id ?: 0L
    return ChecklistItem(
        id = safeId,
        label = label.orEmpty().ifBlank { "Langkah #$safeId" },
        isDone = parseFlexibleBoolean(isDone)
    )
}

fun ChecklistItem.toDto(): ChecklistItemDto = ChecklistItemDto(
    id = id,
    label = label,
    isDone = if (isDone) 1 else 0
)

fun AssigneeDto.toDomain(): Assignee = Assignee(
    id = id ?: 0L,
    name = resolvedName,
    role = role.orEmpty().ifBlank { "technician" },
    isLead = parseFlexibleBoolean(isLead),
    username = username?.trim()?.takeIf { it.isNotEmpty() },
    avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
    department = department?.trim()?.takeIf { it.isNotEmpty() },
    subDepartment = subDepartment?.trim()?.takeIf { it.isNotEmpty() },
    isActive = resolvedIsActive
)

fun TaskDto.toEntity(nowMillis: Long = System.currentTimeMillis()): TaskEntity {
    val startMinutes = scheduledTimeMinutes?.takeIf { it in 0..(23 * 60 + 59) }
    val resolvedCategory = TicketCategory.fromString(projectOrCategory)
    val resolvedPriority = Priority.fromString(priority)
    val resolvedStage = Stage.fromString(stage)
    val isRunning = resolvedStage == Stage.IN_PROGRESS && timerStartedAtMillis != null && timerStartedAtMillis > 0L
    val parsedCreatedAt = DateEpochUtil.parseTimestampMillis(createdAt, fallbackMillis = nowMillis)
    val parsedUpdatedAt = DateEpochUtil.parseTimestampMillis(updatedAt, fallbackMillis = nowMillis)
    val parsedDueDate = DateEpochUtil.parseTimestampMillisOrNull(dueDate)
    val resolvedEpochDay = scheduledDateEpochDay?.takeIf { it > 15_000L }
        ?: DateEpochUtil.epochDayFromMillis(parsedCreatedAt)
    val rawAssignees = assignees.orEmpty().map { it.toDomain() }
    val cleanAssigneeName = assigneeName?.trim()?.takeIf { it.isNotEmpty() }
        ?: assignedToName?.trim()?.takeIf { it.isNotEmpty() }
    val resolvedAssignedToId = assignedToId?.takeIf { it > 0L }
        ?: assignedToLegacyId?.takeIf { it > 0L }
        ?: rawAssignees.firstOrNull { it.isLead }?.id?.takeIf { it > 0L }
        ?: rawAssignees.firstOrNull()?.id?.takeIf { it > 0L }
        ?: assigneeIds?.firstOrNull { it > 0L }
    val resolvedAssignees = if (rawAssignees.isNotEmpty()) {
        rawAssignees
    } else {
        val fallbackIds = assigneeIds.orEmpty().filter { it > 0L }.distinct()
        when {
            fallbackIds.isNotEmpty() -> fallbackIds.mapIndexed { idx, uid ->
                val resolvedName = if (idx == 0 && cleanAssigneeName != null) {
                    cleanAssigneeName
                } else {
                    "Teknisi #$uid"
                }
                Assignee(id = uid, name = resolvedName, role = "technician", isLead = idx == 0)
            }
            resolvedAssignedToId != null || cleanAssigneeName != null -> listOf(
                Assignee(
                    id = resolvedAssignedToId ?: 0L,
                    name = cleanAssigneeName ?: "Teknisi #$resolvedAssignedToId",
                    role = "technician",
                    isLead = true
                )
            )
            else -> emptyList()
        }
    }
    val orderedChecklist = checklist.orEmpty()
        .withIndex()
        .sortedWith(compareBy<IndexedValue<ChecklistItemDto>> { it.value.sortOrder ?: Int.MAX_VALUE }.thenBy { it.index })
        .map { it.value.toDomain() }
    val resolvedLabels = parseFlexibleLabels(labels ?: tags)
    val cleanClientSyncId = clientSyncId?.trim()?.takeIf { it.isNotEmpty() }

    return TaskEntity(
        id = id,
        ticketCode = ticketCode.orEmpty().ifBlank { "${resolvedCategory.code}-$id" },
        title = title.orEmpty().ifBlank { "Tugas Lapangan #$id" },
        category = resolvedCategory.code,
        scheduledDateEpochDay = resolvedEpochDay,
        scheduledTimeMinutes = startMinutes,
        timeSlotLabel = DateEpochUtil.formatMinutesToTimeSlot(startMinutes),
        priority = resolvedPriority.apiValue,
        stage = resolvedStage.apiValue,
        technicalNotes = technicalNotes.orEmpty(),
        trackedSeconds = (trackedSeconds ?: 0L).coerceAtLeast(0L),
        isTimerRunning = isRunning,
        timerStartedAtMillis = if (isRunning) timerStartedAtMillis else null,
        location = location.orEmpty().ifBlank { "Lokasi Lapangan" },
        slaTargetTime = slaTargetTime.orEmpty(),
        checklist = orderedChecklist,
        assignees = resolvedAssignees,
        isSynced = true,
        createdAtMillis = parsedCreatedAt,
        dueDateMillis = parsedDueDate,
        assignedToId = resolvedAssignedToId,
        labels = resolvedLabels,
        updatedAtMillis = parsedUpdatedAt,
        clientSyncId = cleanClientSyncId,
        isRecurring = parseFlexibleBoolean(isRecurring),
        recurrenceInterval = recurrenceInterval?.trim()?.takeIf { it.isNotEmpty() },
        isBlocked = parseFlexibleBoolean(isBlocked),
        blockedReason = blockedReason?.trim()?.takeIf { it.isNotEmpty() },
        snoozedUntilMillis = DateEpochUtil.parseTimestampMillisOrNull(snoozedUntil),
        attachments = attachments.orEmpty().mapIndexedNotNull { idx, dto ->
            dto.toDomain(fallbackTaskId = id, nowMillis = nowMillis, index = idx)
        },
        createdById = createdById?.takeIf { it > 0L },
        createdByName = createdByName?.trim()?.takeIf { it.isNotEmpty() },
        creatorClient = creatorClient?.trim()?.takeIf { it.isNotEmpty() },
        creatorRole = creatorRole?.trim()?.takeIf { it.isNotEmpty() }
    )
}

/**
 * Tolerant response parser for POST /api/tasks that supports every PingDulu Fastify/REST shape:
 * 1. Wrapped `{ "success": true, "task": { ...TaskDto } }` (including `"dedup": true`)
 * 2. Wrapped `{ "data": { ...TaskDto } }`
 * 3. Flat `TaskDto` at JSON root `{ "id": 105, "ticket_code": "NET-105", "title": "...", ... }`
 * 4. Minimal confirmation `{ "success": true, "id": 105 }` or `{ "task_id": 105, "ticket_code": "NET-105" }`
 * 5. Empty JSON object `{}` (triggers fallback refresh from GET /api/tasks)
 */
@JsonClass(generateAdapter = true)
data class CreateTaskResponseDto(
    @Json(name = "success")
    val success: Boolean? = null,
    @Json(name = "dedup")
    val dedup: Boolean? = null,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "data")
    val data: TaskDto? = null,
    @Json(name = "task")
    val task: TaskDto? = null,
    @Json(name = "id")
    val id: Long? = null,
    @Json(name = "task_id")
    val taskId: Long? = null,
    @Json(name = "client_sync_id")
    val clientSyncId: String? = null,
    @Json(name = "ticket_code")
    val ticketCode: String? = null,
    @Json(name = "title")
    val title: String? = null,
    @Json(name = "project_or_category")
    val projectOrCategory: String? = null,
    @Json(name = "scheduled_date_epoch_day")
    val scheduledDateEpochDay: Long? = null,
    @Json(name = "scheduled_time_minutes")
    val scheduledTimeMinutes: Int? = null,
    @Json(name = "priority")
    val priority: String? = null,
    @Json(name = "stage")
    val stage: String? = null,
    @Json(name = "technical_notes")
    val technicalNotes: String? = null,
    @Json(name = "tracked_seconds")
    val trackedSeconds: Long? = null,
    @Json(name = "timer_started_at_millis")
    val timerStartedAtMillis: Long? = null,
    @Json(name = "location")
    val location: String? = null,
    @Json(name = "sla_target_time")
    val slaTargetTime: String? = null,
    @Json(name = "created_at")
    val createdAt: Any? = null,
    @Json(name = "updated_at")
    val updatedAt: Any? = null,
    @Json(name = "due_date")
    val dueDate: Any? = null,
    @Json(name = "assigned_to_id")
    val assignedToId: Long? = null,
    @Json(name = "assigned_to")
    val assignedToLegacyId: Long? = null,
    @Json(name = "assignee_name")
    val assigneeName: String? = null,
    @Json(name = "assigned_to_name")
    val assignedToName: String? = null,
    @Json(name = "assignee_ids")
    val assigneeIds: List<Long>? = null,
    @Json(name = "labels")
    val labels: List<Any>? = null,
    @Json(name = "tags")
    val tags: List<Any>? = null,
    @Json(name = "checklist")
    val checklist: List<ChecklistItemDto>? = null,
    @Json(name = "assignees")
    val assignees: List<AssigneeDto>? = null,
    @Json(name = "created_by_id")
    val createdById: Long? = null,
    @Json(name = "created_by_name")
    val createdByName: String? = null,
    @Json(name = "creator_client")
    val creatorClient: String? = null,
    @Json(name = "creator_role")
    val creatorRole: String? = null
) {
    fun hasFullTaskPayload(): Boolean =
        (data != null && data.id > 0L) ||
            (task != null && task.id > 0L) ||
            ((id ?: taskId ?: 0L) > 0L && !title.isNullOrBlank())

    fun resolveCreatedEntity(
        fallbackLocal: TaskEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): TaskEntity? {
        data?.takeIf { it.id > 0L }?.let {
            val mapped = it.toEntity(nowMillis = nowMillis)
            val hasServerEpoch = it.scheduledDateEpochDay != null && it.scheduledDateEpochDay > 15_000L
            return mapped.copy(
                scheduledDateEpochDay = if (hasServerEpoch) mapped.scheduledDateEpochDay else fallbackLocal.scheduledDateEpochDay,
                scheduledTimeMinutes = it.scheduledTimeMinutes ?: fallbackLocal.scheduledTimeMinutes,
                timeSlotLabel = if (it.scheduledTimeMinutes != null) mapped.timeSlotLabel else fallbackLocal.timeSlotLabel,
                location = if (!it.location.isNullOrBlank()) mapped.location else fallbackLocal.location,
                technicalNotes = if (!it.technicalNotes.isNullOrBlank()) mapped.technicalNotes else fallbackLocal.technicalNotes,
                clientSyncId = mapped.clientSyncId ?: fallbackLocal.clientSyncId,
                createdById = mapped.createdById ?: fallbackLocal.createdById,
                createdByName = mapped.createdByName ?: fallbackLocal.createdByName,
                creatorClient = mapped.creatorClient ?: fallbackLocal.creatorClient,
                creatorRole = mapped.creatorRole ?: fallbackLocal.creatorRole
            )
        }
        task?.takeIf { it.id > 0L }?.let {
            val mapped = it.toEntity(nowMillis = nowMillis)
            val hasServerEpoch = it.scheduledDateEpochDay != null && it.scheduledDateEpochDay > 15_000L
            return mapped.copy(
                scheduledDateEpochDay = if (hasServerEpoch) mapped.scheduledDateEpochDay else fallbackLocal.scheduledDateEpochDay,
                scheduledTimeMinutes = it.scheduledTimeMinutes ?: fallbackLocal.scheduledTimeMinutes,
                timeSlotLabel = if (it.scheduledTimeMinutes != null) mapped.timeSlotLabel else fallbackLocal.timeSlotLabel,
                location = if (!it.location.isNullOrBlank()) mapped.location else fallbackLocal.location,
                technicalNotes = if (!it.technicalNotes.isNullOrBlank()) mapped.technicalNotes else fallbackLocal.technicalNotes,
                clientSyncId = mapped.clientSyncId ?: fallbackLocal.clientSyncId,
                createdById = mapped.createdById ?: fallbackLocal.createdById,
                createdByName = mapped.createdByName ?: fallbackLocal.createdByName,
                creatorClient = mapped.creatorClient ?: fallbackLocal.creatorClient,
                creatorRole = mapped.creatorRole ?: fallbackLocal.creatorRole
            )
        }

        val resolvedId = id?.takeIf { it > 0L } ?: taskId?.takeIf { it > 0L } ?: return null
        val resolvedSyncId = clientSyncId?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackLocal.clientSyncId
        if (!title.isNullOrBlank()) {
            return TaskDto(
                id = resolvedId,
                clientSyncId = resolvedSyncId,
                ticketCode = ticketCode ?: fallbackLocal.ticketCode,
                title = title,
                projectOrCategory = projectOrCategory ?: fallbackLocal.category,
                scheduledDateEpochDay = scheduledDateEpochDay ?: fallbackLocal.scheduledDateEpochDay,
                scheduledTimeMinutes = scheduledTimeMinutes ?: fallbackLocal.scheduledTimeMinutes,
                priority = priority ?: fallbackLocal.priority,
                stage = stage ?: fallbackLocal.stage,
                technicalNotes = technicalNotes ?: fallbackLocal.technicalNotes,
                trackedSeconds = trackedSeconds ?: fallbackLocal.trackedSeconds,
                timerStartedAtMillis = timerStartedAtMillis,
                location = location ?: fallbackLocal.location,
                slaTargetTime = slaTargetTime ?: fallbackLocal.slaTargetTime,
                createdAt = createdAt ?: fallbackLocal.createdAtMillis,
                updatedAt = updatedAt ?: nowMillis,
                dueDate = dueDate,
                assignedToId = assignedToId ?: fallbackLocal.assignedToId,
                assignedToLegacyId = assignedToLegacyId,
                assigneeName = assigneeName,
                assignedToName = assignedToName,
                assigneeIds = assigneeIds,
                labels = labels ?: fallbackLocal.labels,
                tags = tags,
                checklist = checklist,
                assignees = assignees,
                createdById = createdById ?: fallbackLocal.createdById,
                createdByName = createdByName ?: fallbackLocal.createdByName,
                creatorClient = creatorClient ?: fallbackLocal.creatorClient,
                creatorRole = creatorRole ?: fallbackLocal.creatorRole
            ).toEntity(nowMillis = nowMillis)
        }

        // Minimal 200/201 response like { "success": true, "id": 105 } or { "task_id": 105 }
        return fallbackLocal.copy(
            id = resolvedId,
            ticketCode = ticketCode?.takeIf { it.isNotBlank() } ?: fallbackLocal.ticketCode,
            isSynced = true,
            updatedAtMillis = nowMillis,
            clientSyncId = resolvedSyncId
        )
    }
}
