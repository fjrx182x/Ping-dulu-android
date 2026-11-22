package com.example.domain.repository

import com.example.domain.model.FieldTechnician
import com.example.domain.model.OfficialDefaultLabels
import com.example.domain.model.OfficialLabel
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TaskAttachment
import com.example.domain.model.TaskComment
import com.example.domain.model.TicketCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface TaskRepository {
    fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>>
    fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>>
    fun observeAllTasks(): Flow<List<Task>>
    fun observeActiveHeroTask(): Flow<Task?>
    fun observeTaskById(taskId: Long): Flow<Task?>
    fun observeUnsyncedCount(): Flow<Int>
    fun observeLastSyncError(): Flow<String?> = flowOf(null)
    fun getLastSyncError(): String? = null
    fun observeTechnicians(): Flow<List<FieldTechnician>> = flowOf(emptyList())
    fun observeOfficialLabels(): Flow<List<OfficialLabel>> = flowOf(OfficialDefaultLabels)

    suspend fun initializeOfflineCache()
    suspend fun refreshCatalogsFromServer() {}
    suspend fun refreshTasksFromServer(): SyncResult
    suspend fun pullTasksFromServerOnly(): SyncResult = refreshTasksFromServer()
    suspend fun toggleChecklistItem(taskId: Long, checklistId: Long)
    suspend fun toggleTaskTimer(taskId: Long)
    suspend fun persistTimerTick(taskId: Long, newTrackedSeconds: Long)
    suspend fun updateTaskStage(taskId: Long, newStage: Stage)
    suspend fun markTaskCompleted(taskId: Long)
    suspend fun createQuickTask(
        title: String,
        category: TicketCategory,
        priority: Priority,
        epochDay: Long,
        location: String,
        scheduledTimeMinutes: Int? = null,
        technicalNotes: String = "",
        labels: List<String> = emptyList(),
        assigneeIds: List<Long> = emptyList(),
        labelIds: List<Long> = emptyList(),
        checklistSteps: List<String> = emptyList(),
        isRecurring: Boolean = false,
        recurrenceInterval: String? = null
    ): Task

    suspend fun fetchTaskAttachments(taskId: Long): Result<List<TaskAttachment>> =
        Result.success(emptyList())

    suspend fun uploadTaskAttachment(
        taskId: Long,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<TaskAttachment> = Result.failure(UnsupportedOperationException("Not implemented"))

    suspend fun fetchTaskComments(taskId: Long): Result<List<TaskComment>> =
        Result.success(emptyList())

    suspend fun postTaskComment(
        taskId: Long,
        commentText: String
    ): Result<TaskComment> = Result.failure(UnsupportedOperationException("Not implemented"))

    suspend fun fetchTaskActivities(taskId: Long): Result<com.example.domain.model.TaskActivityFeed> =
        Result.success(com.example.domain.model.TaskActivityFeed())

    suspend fun snoozeTask(
        taskId: Long,
        snoozeUntilMillis: Long,
        newEpochDay: Long? = null,
        newTimeMinutes: Int? = null
    ): Result<Unit> = Result.success(Unit)

    suspend fun setTaskBlocked(
        taskId: Long,
        isBlocked: Boolean,
        reason: String? = null
    ): Result<Unit> = Result.success(Unit)

    suspend fun workTask(taskId: Long): Result<String> {
        updateTaskStage(taskId, Stage.IN_PROGRESS)
        return Result.success("Tiket sedang dikerjakan.")
    }

    suspend fun claimTask(taskId: Long): Result<Task> =
        Result.failure(UnsupportedOperationException("Not implemented"))

    suspend fun addChecklistStep(taskId: Long, label: String): Result<com.example.domain.model.ChecklistItem> =
        Result.failure(UnsupportedOperationException("Not implemented"))

    suspend fun delegateTechnicians(taskId: Long, assigneeIds: List<Long>): Result<Task> =
        Result.failure(UnsupportedOperationException("Not implemented"))

    suspend fun completeTaskWithPolicy(taskId: Long): Result<Unit> {
        markTaskCompleted(taskId)
        return Result.success(Unit)
    }

    suspend fun deleteTask(taskId: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented"))

    suspend fun fetchHolidaysForYear(year: Int): List<com.example.domain.model.NationalHoliday> =
        com.example.domain.model.IndonesianHolidayCatalog.holidaysForYear(year)

    suspend fun fetchCalendarMonth(
        year: Int,
        month: Int
    ): Result<List<com.example.domain.model.NationalHoliday>> =
        Result.success(fetchHolidaysForYear(year).filter {
            com.example.domain.model.DateEpochUtil.yearMonthFromEpochDay(it.epochDay) == (year to month)
        })

    suspend fun login(username: String, password: String): Result<String>
    suspend fun clearSyncedCacheOnLogout()
}
