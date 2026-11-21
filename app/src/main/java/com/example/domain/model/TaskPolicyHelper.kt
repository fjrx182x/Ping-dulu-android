package com.example.domain.model

/**
 * Mirrors PingDulu Backend `checkTaskPolicy` (Patch Ke-2):
 * 1. Tickets created by Android technicians (`creator_client == "android"` or `creator_role == "technician"`):
 *    Only the creating technician OR an admin may complete or delete the ticket.
 * 2. Tickets created by Web Admin (or legacy tasks without creator metadata):
 *    Only an admin OR an assigned technician may complete or delete the ticket.
 * 3. Unassigned tickets (`assignees.isEmpty() && assignedToId == null`):
 *    Surface the dynamic "Ambil Task" (`POST /api/tasks/:id/claim`) CTA first.
 */
object TaskPolicyHelper {

    fun isUnassigned(task: Task): Boolean {
        val hasLeadId = task.assignedToId != null && task.assignedToId > 0L
        return task.assignees.isEmpty() && !hasLeadId
    }

    fun isCurrentUserAssigned(
        task: Task,
        currentUserId: Long,
        currentUserName: String
    ): Boolean {
        if (currentUserId > 0L && task.isAssignedToUser(currentUserId)) {
            return true
        }
        val cleanName = currentUserName.trim()
        if (cleanName.isNotEmpty()) {
            return task.assignees.any { assignee ->
                val target = assignee.name.trim()
                target.isNotEmpty() && target.equals(cleanName, ignoreCase = true)
            }
        }
        return false
    }

    fun canCompleteOrDelete(
        task: Task,
        currentUserId: Long,
        currentUserName: String,
        currentUserRole: String
    ): Boolean {
        val isAdmin = currentUserRole.trim().equals("admin", ignoreCase = true)

        // 1. Created by technician on Android
        val isAndroidTechOrigin = task.creatorClient.equals("android", ignoreCase = true) ||
            task.creatorRole.equals("technician", ignoreCase = true)
        if (isAndroidTechOrigin) {
            val isCreatorById = task.createdById != null &&
                task.createdById > 0L &&
                currentUserId > 0L &&
                task.createdById == currentUserId
            val isCreatorByName = !task.createdByName.isNullOrBlank() &&
                currentUserName.isNotBlank() &&
                task.createdByName.trim().equals(currentUserName.trim(), ignoreCase = true)
            return isCreatorById || isCreatorByName || isAdmin
        }

        // 2. Created by web admin (or legacy ticket without creator metadata)
        val isAssigned = isCurrentUserAssigned(
            task = task,
            currentUserId = currentUserId,
            currentUserName = currentUserName
        )
        return isAdmin || isAssigned
    }

    fun unauthorizedReason(task: Task): String {
        if (isUnassigned(task)) {
            return "Tiket belum memiliki teknisi. Ketuk \"Ambil Task\" terlebih dahulu untuk mengambil alih pengerjaan."
        }
        val isAndroidTechOrigin = task.creatorClient.equals("android", ignoreCase = true) ||
            task.creatorRole.equals("technician", ignoreCase = true)
        return if (isAndroidTechOrigin) {
            val creatorLabel = task.createdByName?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            "Hanya teknisi pembuat$creatorLabel atau admin yang dapat menyelesaikan atau menghapus tiket ini."
        } else {
            "Hanya teknisi yang ditugaskan atau admin pembuat yang dapat menyelesaikan atau menghapus tiket ini."
        }
    }
}
