package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query(
        """
        SELECT * FROM tasks
        WHERE scheduled_date_epoch_day = :epochDay
        ORDER BY
            CASE priority
                WHEN 'P1_CRITICAL' THEN 1
                WHEN 'P2_HIGH' THEN 2
                ELSE 3
            END ASC,
            scheduled_time_minutes ASC,
            id ASC
        """
    )
    fun getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE scheduled_date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY scheduled_date_epoch_day ASC, scheduled_time_minutes ASC, id ASC
        """
    )
    fun getTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        ORDER BY scheduled_date_epoch_day ASC, scheduled_time_minutes ASC, id ASC
        """
    )
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE stage = 'IN_PROGRESS'
        ORDER BY
            is_timer_running DESC,
            CASE priority
                WHEN 'P1_CRITICAL' THEN 1
                WHEN 'P2_HIGH' THEN 2
                ELSE 3
            END ASC,
            updated_at_millis DESC
        LIMIT 1
        """
    )
    fun getActiveHeroTask(): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    fun getTaskById(taskId: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskByIdOnce(taskId: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE is_synced = 0 ORDER BY updated_at_millis ASC")
    suspend fun getUnsyncedTasks(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE is_synced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun countTasks(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Query(
        """
        UPDATE tasks
        SET checklist_json = :checklistJson,
            is_synced = :isSynced,
            updated_at_millis = :updatedAtMillis
        WHERE id = :taskId
        """
    )
    suspend fun updateChecklist(
        taskId: Long,
        checklistJson: String,
        isSynced: Boolean = false,
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE tasks
        SET tracked_seconds = :seconds,
            is_timer_running = :isRunning,
            timer_started_at_millis = :timerStartedAtMillis,
            is_synced = :isSynced,
            updated_at_millis = :updatedAtMillis
        WHERE id = :taskId
        """
    )
    suspend fun updateTaskTimer(
        taskId: Long,
        seconds: Long,
        isRunning: Boolean,
        timerStartedAtMillis: Long? = null,
        isSynced: Boolean = false,
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE tasks
        SET is_timer_running = 0,
            timer_started_at_millis = NULL,
            is_synced = 0,
            updated_at_millis = :updatedAtMillis
        WHERE id != :excludeTaskId AND is_timer_running = 1
        """
    )
    suspend fun pauseAllOtherTimers(
        excludeTaskId: Long,
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE tasks
        SET stage = :stage,
            is_timer_running = :isRunning,
            timer_started_at_millis = :timerStartedAtMillis,
            is_synced = :isSynced,
            updated_at_millis = :updatedAtMillis
        WHERE id = :taskId
        """
    )
    suspend fun updateTaskStage(
        taskId: Long,
        stage: String,
        isRunning: Boolean,
        timerStartedAtMillis: Long? = null,
        isSynced: Boolean = false,
        updatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query("UPDATE tasks SET is_synced = 1 WHERE id = :taskId")
    suspend fun markTaskSynced(taskId: Long)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)
}
