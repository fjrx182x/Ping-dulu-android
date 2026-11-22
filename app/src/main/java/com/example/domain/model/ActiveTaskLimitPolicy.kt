package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ActiveTaskLimitWarning(
    val title: String,
    val message: String,
    val detailSubtitle: String? = null,
    val isDelegationContext: Boolean = false,
    val activeCount: Int = ActiveTaskLimitPolicy.MAX_ACTIVE_TASKS,
    val limit: Int = ActiveTaskLimitPolicy.MAX_ACTIVE_TASKS
)

/**
 * Thrown by repository write/claim/delegation calls when the backend responds with
 * HTTP 409 Conflict (`type: "CLAIM_LIMIT_EXCEEDED"`) so the UI immediately surfaces
 * the Amber Glow "Kalem Dulu!" dialog and rolls back any optimistic local write.
 */
class ActiveTaskLimitExceededException(
    val serverMessage: String,
    val activeCount: Int = ActiveTaskLimitPolicy.MAX_ACTIVE_TASKS,
    val limit: Int = ActiveTaskLimitPolicy.MAX_ACTIVE_TASKS,
    val isDelegationContext: Boolean = false
) : IllegalStateException(serverMessage)

/**
 * Enforces the Patch Ke-7 maximum simultaneous active task limit per technician
 * (`stage != Stage.DONE` -> Antrean + Proses <= 5, 100% aligned with PingDulu Server)
 * while keeping the Hero Carousel focused strictly on `Stage.IN_PROGRESS` tasks.
 */
object ActiveTaskLimitPolicy {

    const val MAX_ACTIVE_TASKS: Int = 5

    const val DELEGATION_LIMIT_MESSAGE: String =
        "Kalem Dulu! Ada 5 task yang aktif. selesaikan dulu minimal 1 task"

    const val SELF_CLAIM_LIMIT_MESSAGE: String =
        "Kalem dulu! Kamu punya 5 task aktif nih. Selesaikan dulu minimal 1 task"

    /**
     * Counts how many active tasks (`stage != Stage.DONE`, i.e., BACKLOG + IN_PROGRESS)
     * are currently held by a specific technician. Matches server Patch Ke-7 enforcement.
     */
    fun countActiveTasksForUser(
        allTasks: List<Task>,
        userId: Long,
        userName: String = "",
        excludeTaskId: Long? = null
    ): Int {
        val activeTasks = allTasks.filter { task ->
            task.stage != Stage.DONE && (excludeTaskId == null || task.id != excludeTaskId)
        }
        if (userId <= 0L && userName.isBlank()) {
            return activeTasks.size
        }
        return activeTasks.count { task ->
            TaskPolicyHelper.isCurrentUserAssigned(
                task = task,
                currentUserId = userId,
                currentUserName = userName
            )
        }
    }

    /**
     * Validates whether the current user can take, start working on, or reopen a task.
     * - If the task is ALREADY assigned to the user and active (`stage != DONE`), starting
     *   work (`BACKLOG -> IN_PROGRESS`) is allowed without double-counting the same task.
     * - If the task is unassigned (claim), assigned to someone else (join work), or reopened
     *   from `DONE`, the user must have fewer than 5 other active (`stage != DONE`) tasks.
     */
    fun checkSelfTakeOrWorkLimit(
        allTasks: List<Task>,
        currentUserId: Long,
        currentUserName: String,
        targetTaskId: Long
    ): ActiveTaskLimitWarning? {
        val targetTask = allTasks.firstOrNull { it.id == targetTaskId }
        if (targetTask != null &&
            targetTask.stage != Stage.DONE &&
            TaskPolicyHelper.isCurrentUserAssigned(targetTask, currentUserId, currentUserName)
        ) {
            return null
        }

        val activeCount = countActiveTasksForUser(
            allTasks = allTasks,
            userId = currentUserId,
            userName = currentUserName,
            excludeTaskId = targetTaskId
        )
        if (activeCount >= MAX_ACTIVE_TASKS) {
            return ActiveTaskLimitWarning(
                title = "Kalem Dulu!",
                message = SELF_CLAIM_LIMIT_MESSAGE,
                detailSubtitle = "Saat ini kamu memegang $activeCount/$MAX_ACTIVE_TASKS task aktif (Antrean & Proses).",
                isDelegationContext = false,
                activeCount = activeCount,
                limit = MAX_ACTIVE_TASKS
            )
        }
        return null
    }

    /**
     * Validates whether creating or delegating a task to one or more technicians exceeds
     * the 5 active (`stage != Stage.DONE`) task limit for any target technician.
     */
    fun checkDelegationLimit(
        allTasks: List<Task>,
        targetAssigneeIds: List<Long>,
        availableTechnicians: List<FieldTechnician>,
        currentUserId: Long,
        currentUserName: String,
        currentUserRole: String,
        excludeTaskId: Long? = null
    ): ActiveTaskLimitWarning? {
        val cleanIds = targetAssigneeIds.filter { it > 0L }.distinct()
        val techById = availableTechnicians.associateBy { it.id }
        val isAdmin = currentUserRole.trim().equals("admin", ignoreCase = true)

        val targets: List<Pair<Long, String>> = if (cleanIds.isNotEmpty()) {
            cleanIds.map { uid ->
                val name = techById[uid]?.resolvedName
                    ?: if (uid == currentUserId) currentUserName else ""
                uid to name
            }
        } else {
            listOf(currentUserId to currentUserName)
        }

        val overloaded = targets.mapNotNull { (uid, name) ->
            val count = countActiveTasksForUser(
                allTasks = allTasks,
                userId = uid,
                userName = name,
                excludeTaskId = excludeTaskId
            )
            if (count >= MAX_ACTIVE_TASKS) {
                Triple(uid, name.ifBlank { if (uid > 0L) "Teknisi #$uid" else "Teknisi" }, count)
            } else {
                null
            }
        }

        if (overloaded.isEmpty()) return null

        val isDelegatingToOther = cleanIds.any { it > 0L && it != currentUserId }
        val useDelegationCopy = isAdmin || isDelegatingToOther
        val namesSummary = overloaded.joinToString(", ") { (_, label, count) ->
            "$label ($count/$MAX_ACTIVE_TASKS)"
        }
        val maxOverloadedCount = overloaded.maxOf { it.third }

        return if (useDelegationCopy) {
            ActiveTaskLimitWarning(
                title = "Kalem Dulu!",
                message = DELEGATION_LIMIT_MESSAGE,
                detailSubtitle = "Teknisi sudah mencapai batas aktif: $namesSummary.",
                isDelegationContext = true,
                activeCount = maxOverloadedCount,
                limit = MAX_ACTIVE_TASKS
            )
        } else {
            ActiveTaskLimitWarning(
                title = "Kalem Dulu!",
                message = SELF_CLAIM_LIMIT_MESSAGE,
                detailSubtitle = "Saat ini kamu memegang $maxOverloadedCount/$MAX_ACTIVE_TASKS task aktif (Antrean & Proses).",
                isDelegationContext = false,
                activeCount = maxOverloadedCount,
                limit = MAX_ACTIVE_TASKS
            )
        }
    }

    /**
     * Detects whether a raw HTTP 409 error payload from PingDulu Server represents
     * a 5-active-task WIP limit rejection (`CLAIM_LIMIT_EXCEEDED`).
     */
    fun isClaimLimitExceededPayload(rawBody: String): Boolean {
        if (rawBody.isBlank()) return false
        return rawBody.contains("CLAIM_LIMIT_EXCEEDED", ignoreCase = true) ||
            rawBody.contains("kalem dulu", ignoreCase = true) ||
            (rawBody.contains("5 task", ignoreCase = true) && rawBody.contains("aktif", ignoreCase = true))
    }

    /**
     * Builds an [ActiveTaskLimitExceededException] from a raw server HTTP 409 response body.
     */
    fun parseServerLimitException(
        rawBody: String,
        isDelegationContext: Boolean
    ): ActiveTaskLimitExceededException {
        val extractedError = extractJsonString(rawBody, "error")
            ?: extractJsonString(rawBody, "message")
        val limit = extractJsonInt(rawBody, "limit") ?: MAX_ACTIVE_TASKS
        val activeCount = extractJsonInt(rawBody, "active_count") ?: limit

        val resolvedMessage = when {
            !extractedError.isNullOrBlank() && extractedError.contains("kalem", ignoreCase = true) -> extractedError
            isDelegationContext -> DELEGATION_LIMIT_MESSAGE
            else -> SELF_CLAIM_LIMIT_MESSAGE
        }
        val inferredDelegation = isDelegationContext ||
            resolvedMessage.contains("Ada 5 task yang aktif", ignoreCase = true)

        return ActiveTaskLimitExceededException(
            serverMessage = resolvedMessage,
            activeCount = activeCount,
            limit = limit,
            isDelegationContext = inferredDelegation
        )
    }

    /**
     * Converts an [ActiveTaskLimitExceededException] into an [ActiveTaskLimitWarning]
     * ready to render in the Amber Glow "Kalem Dulu!" dialog.
     */
    fun warningFromException(ex: ActiveTaskLimitExceededException): ActiveTaskLimitWarning {
        val exactMessage = when {
            ex.serverMessage.contains("kalem", ignoreCase = true) -> ex.serverMessage
            ex.isDelegationContext -> DELEGATION_LIMIT_MESSAGE
            else -> SELF_CLAIM_LIMIT_MESSAGE
        }
        val subtitle = if (ex.isDelegationContext) {
            "Teknisi target saat ini memegang ${ex.activeCount}/${ex.limit} task aktif di server."
        } else {
            "Server mencatat ${ex.activeCount}/${ex.limit} task aktif yang belum selesai."
        }
        return ActiveTaskLimitWarning(
            title = "Kalem Dulu!",
            message = exactMessage,
            detailSubtitle = subtitle,
            isDelegationContext = ex.isDelegationContext,
            activeCount = ex.activeCount,
            limit = ex.limit
        )
    }

    private fun extractJsonString(raw: String, key: String): String? {
        val regex = Regex("""\"$key\"\s*:\s*\"([^\"]+)\"""")
        return regex.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractJsonInt(raw: String, key: String): Int? {
        val regex = Regex("""\"$key\"\s*:\s*(\d+)""")
        return regex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * Selects all active (`Stage.IN_PROGRESS`) tasks to display in the Hero Carousel,
     * prioritizing tasks assigned to the logged-in user first, then priority & recency.
     */
    fun selectActiveHeroTasks(
        scopedTasks: List<Task>,
        currentUserId: Long,
        currentUserName: String
    ): List<Task> {
        return scopedTasks
            .filter { it.stage == Stage.IN_PROGRESS }
            .sortedWith(
                compareByDescending<Task> { task ->
                    TaskPolicyHelper.isCurrentUserAssigned(task, currentUserId, currentUserName)
                }
                    .thenByDescending { it.isTimerRunning }
                    .thenBy { it.priority.sortRank }
                    .thenByDescending { it.updatedAtMillis }
                    .thenByDescending { it.id }
            )
    }
}
