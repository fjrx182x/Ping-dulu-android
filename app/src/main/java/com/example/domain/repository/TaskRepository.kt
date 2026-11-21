package com.example.domain.repository

import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>>
    fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>>
    fun observeAllTasks(): Flow<List<Task>>
    fun observeActiveHeroTask(): Flow<Task?>
    fun observeTaskById(taskId: Long): Flow<Task?>
    fun observeUnsyncedCount(): Flow<Int>

    suspend fun initializeOfflineCache()
    suspend fun refreshTasksFromServer(): SyncResult
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
        labels: List<String> = emptyList()
    ): Task

    suspend fun login(username: String, password: String): Result<String>
    suspend fun clearSyncedCacheOnLogout()
}
