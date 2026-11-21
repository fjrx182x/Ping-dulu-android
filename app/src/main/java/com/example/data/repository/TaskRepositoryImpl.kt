package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.local.toDomain
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.remote.dto.toEntity
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val apiServiceProvider: () -> PingDuluApiService,
    private val appPreferences: AppPreferences? = null,
    private val database: AppDatabase? = null,
    private val converters: TaskConverters = TaskConverters()
) : TaskRepository {

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> {
        return taskDao.getTasksByEpochDay(epochDay).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> {
        return taskDao.getTasksInRange(startEpochDay, endEpochDay).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeAllTasks(): Flow<List<Task>> {
        return taskDao.getAllTasks().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeActiveHeroTask(): Flow<Task?> {
        return taskDao.getActiveHeroTask().map { entity ->
            entity?.toDomain()
        }
    }

    override fun observeTaskById(taskId: Long): Flow<Task?> {
        return taskDao.getTaskById(taskId).map { entity ->
            entity?.toDomain()
        }
    }

    override fun observeUnsyncedCount(): Flow<Int> = taskDao.getUnsyncedCount()

    override suspend fun initializeOfflineCache() {
        withContext(Dispatchers.IO) {
            appPreferences?.warmUpTokenCache()
        }
    }

    override suspend fun refreshTasksFromServer(): SyncResult = withContext(Dispatchers.IO) {
        val api = apiServiceProvider()
        var uploadedCount = 0
        val failedUploadIds = mutableSetOf<Long>()

        // 1. Push locally mutated tasks first so offline field work is never overwritten.
        val unsyncedTasks = taskDao.getUnsyncedTasks()
        for (entity in unsyncedTasks) {
            val pushSuccess = pushLocalEntityToServer(api, entity)
            if (pushSuccess) {
                taskDao.markTaskSynced(entity.id)
                uploadedCount++
            } else {
                failedUploadIds.add(entity.id)
            }
        }

        // 2. Pull fresh task snapshot from PingDulu API; fall back silently to Room if offline.
        try {
            val response = api.getTasks()
            if (response.code() == 401 || response.code() == 403) {
                return@withContext SyncResult.AuthRequired(
                    message = "Sesi token kedaluwarsa atau belum login (${response.code()}). Data lokal tetap aman."
                )
            }
            if (!response.isSuccessful) {
                return@withContext SyncResult.OfflineFallback(
                    pendingUploadCount = taskDao.getUnsyncedTasks().size,
                    reason = "Server merespons HTTP ${response.code()}"
                )
            }

            val remoteDtos = response.body()?.resolvedTasks().orEmpty()
            val nowMillis = System.currentTimeMillis()
            val entitiesToUpsert = remoteDtos
                .filter { it.id !in failedUploadIds }
                .map { it.toEntity(nowMillis = nowMillis) }
            val activeRemoteIds = remoteDtos.map { it.id }

            val reconcileBlock: suspend () -> Unit = {
                if (activeRemoteIds.isEmpty() || activeRemoteIds.size > 900) {
                    taskDao.deleteAllSyncedTasks()
                } else {
                    taskDao.deleteStaleSyncedTasks(activeRemoteIds)
                }
                if (entitiesToUpsert.isNotEmpty()) {
                    taskDao.upsertTasks(entitiesToUpsert)
                }
            }

            val db = database
            if (db != null) {
                db.withTransaction { reconcileBlock() }
            } else {
                reconcileBlock()
            }

            appPreferences?.updateLastSyncedAt(nowMillis)
            SyncResult.Success(
                downloadedCount = entitiesToUpsert.size,
                uploadedCount = uploadedCount
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            SyncResult.OfflineFallback(
                pendingUploadCount = taskDao.getUnsyncedTasks().size,
                reason = e.localizedMessage ?: "Tidak ada koneksi ke server PingDulu"
            )
        }
    }

    override suspend fun toggleChecklistItem(taskId: Long, checklistId: Long) = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId) ?: return@withContext
        val updatedChecklist = existing.checklist.map { item ->
            if (item.id == checklistId) item.copy(isDone = !item.isDone) else item
        }
        val checklistJson = converters.fromChecklistList(updatedChecklist)
        val now = System.currentTimeMillis()

        // Immediate local Room update (SSOT)
        taskDao.updateChecklist(
            taskId = taskId,
            checklistJson = checklistJson,
            isSynced = false,
            updatedAtMillis = now
        )

        // Best-effort remote sync; skip remote call for offline-only negative IDs until full sync
        val toggledItem = updatedChecklist.firstOrNull { it.id == checklistId }
        if (taskId > 0L && toggledItem != null) {
            tryRemote {
                val response = apiServiceProvider().toggleChecklistStep(
                    id = taskId,
                    stepId = checklistId,
                    body = ChecklistStepToggleDto(isDone = if (toggledItem.isDone) 1 else 0)
                )
                if (response.isSuccessful) {
                    taskDao.markTaskSynced(taskId)
                }
            }
        }
    }

    override suspend fun toggleTaskTimer(taskId: Long) = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId) ?: return@withContext
        val now = System.currentTimeMillis()
        val willRun = !existing.isTimerRunning

        if (willRun) {
            // Ensure only one job stopwatch ticks at a time
            taskDao.pauseAllOtherTimers(excludeTaskId = taskId, updatedAtMillis = now)

            // Promote Backlog/Done task back to IN_PROGRESS when technician starts stopwatch
            if (existing.stage != Stage.IN_PROGRESS.apiValue) {
                taskDao.updateTaskStage(
                    taskId = taskId,
                    stage = Stage.IN_PROGRESS.apiValue,
                    isRunning = true,
                    timerStartedAtMillis = now,
                    isSynced = false,
                    updatedAtMillis = now
                )
            } else {
                taskDao.updateTaskTimer(
                    taskId = taskId,
                    seconds = existing.trackedSeconds,
                    isRunning = true,
                    timerStartedAtMillis = now,
                    isSynced = false,
                    updatedAtMillis = now
                )
            }
        } else {
            taskDao.updateTaskTimer(
                taskId = taskId,
                seconds = existing.trackedSeconds,
                isRunning = false,
                timerStartedAtMillis = null,
                isSynced = false,
                updatedAtMillis = now
            )
        }

        if (taskId > 0L) {
            tryRemote {
                val api = apiServiceProvider()
                var stageSynced = true
                if (willRun && existing.stage != Stage.IN_PROGRESS.apiValue) {
                    stageSynced = api.updateStage(
                        id = taskId,
                        body = StageUpdateDto(stage = Stage.IN_PROGRESS.apiValue)
                    ).isSuccessful
                }
                val timerResponse = api.updateTimer(
                    id = taskId,
                    body = TimerActionRequestDto(
                        action = if (willRun) {
                            TimerActionRequestDto.ACTION_START
                        } else {
                            TimerActionRequestDto.ACTION_PAUSE
                        }
                    )
                )
                if (stageSynced && timerResponse.isSuccessful) {
                    taskDao.markTaskSynced(taskId)
                }
            }
        }
    }

    override suspend fun persistTimerTick(taskId: Long, newTrackedSeconds: Long) = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId) ?: return@withContext
        if (!existing.isTimerRunning) return@withContext
        taskDao.updateTaskTimer(
            taskId = taskId,
            seconds = newTrackedSeconds.coerceAtLeast(0L),
            isRunning = true,
            timerStartedAtMillis = existing.timerStartedAtMillis ?: System.currentTimeMillis(),
            isSynced = false,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    override suspend fun updateTaskStage(taskId: Long, newStage: Stage) = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId) ?: return@withContext
        val now = System.currentTimeMillis()
        val keepRunning = newStage == Stage.IN_PROGRESS && existing.isTimerRunning

        taskDao.updateTaskStage(
            taskId = taskId,
            stage = newStage.apiValue,
            isRunning = keepRunning,
            timerStartedAtMillis = if (keepRunning) existing.timerStartedAtMillis ?: now else null,
            isSynced = false,
            updatedAtMillis = now
        )

        if (taskId > 0L) {
            tryRemote {
                val response = apiServiceProvider().updateStage(
                    id = taskId,
                    body = StageUpdateDto(stage = newStage.apiValue)
                )
                if (response.isSuccessful) {
                    taskDao.markTaskSynced(taskId)
                }
            }
        }
    }

    override suspend fun markTaskCompleted(taskId: Long) = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId) ?: return@withContext
        val now = System.currentTimeMillis()

        taskDao.updateTaskStage(
            taskId = taskId,
            stage = Stage.DONE.apiValue,
            isRunning = false,
            timerStartedAtMillis = null,
            isSynced = false,
            updatedAtMillis = now
        )

        if (taskId > 0L) {
            tryRemote {
                val api = apiServiceProvider()
                val timerOk = if (existing.isTimerRunning) {
                    api.updateTimer(
                        id = taskId,
                        body = TimerActionRequestDto(action = TimerActionRequestDto.ACTION_PAUSE)
                    ).isSuccessful
                } else {
                    true
                }
                val stageOk = api.updateStage(
                    id = taskId,
                    body = StageUpdateDto(stage = Stage.DONE.apiValue)
                ).isSuccessful
                if (timerOk && stageOk) {
                    taskDao.markTaskSynced(taskId)
                }
            }
        }
    }

    override suspend fun createQuickTask(
        title: String,
        category: TicketCategory,
        priority: Priority,
        epochDay: Long,
        location: String,
        scheduledTimeMinutes: Int?,
        technicalNotes: String,
        labels: List<String>
    ): Task = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val localId = -now // Negative ID denotes locally created offline task until server assigns ID
        val currentSession = appPreferences?.sessionFlow?.first()
        val currentUserId = currentSession?.userId?.takeIf { it > 0L }
        val techName = currentSession?.technicianName ?: "Fajar"
        val techRole = currentSession?.technicianRole ?: "admin"
        val sequenceNumber = (taskDao.countTasks() + 1)
        val ticketCode = "${category.code}-$sequenceNumber"
        val cleanLocation = location.trim().ifBlank { "Lokasi Lapangan" }
        val explicitLabels = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val derivedLabels = if (explicitLabels.isNotEmpty()) {
            explicitLabels
        } else {
            buildList {
                val lower = title.lowercase()
                if (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("access point")) {
                    add("Wi-Fi")
                }
                add(
                    when (category) {
                        TicketCategory.NET -> "Jaringan"
                        TicketCategory.SRV -> "Server"
                        TicketCategory.HRD -> "Hardware"
                        TicketCategory.SEC -> "Keamanan"
                        TicketCategory.ONB -> "Onboarding"
                    }
                )
                if (priority == Priority.P1_CRITICAL) add("Urgent")
            }.distinct()
        }

        val defaultChecklist = listOf(
            ChecklistItem(id = now + 1L, label = "Inspeksi fisik perangkat & konektor", isDone = false),
            ChecklistItem(id = now + 2L, label = "Pengujian fungsional & dokumentasi SPK", isDone = false)
        )

        val cleanScheduledMinutes = scheduledTimeMinutes?.coerceIn(0, 23 * 60 + 59)
        val entity = TaskEntity(
            id = localId,
            ticketCode = ticketCode,
            title = title.trim(),
            category = category.code,
            scheduledDateEpochDay = epochDay,
            scheduledTimeMinutes = cleanScheduledMinutes,
            timeSlotLabel = DateEpochUtil.formatMinutesToTimeSlot(cleanScheduledMinutes),
            priority = priority.apiValue,
            stage = Stage.BACKLOG.apiValue,
            technicalNotes = technicalNotes.trim().ifBlank { "Tiket cepat dibuat dari aplikasi lapangan." },
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = cleanLocation,
            slaTargetTime = "",
            checklist = defaultChecklist,
            assignees = listOf(
                Assignee(
                    id = currentUserId ?: 3L,
                    name = techName,
                    role = techRole,
                    isLead = true
                )
            ),
            isSynced = false,
            createdAtMillis = now,
            dueDateMillis = null,
            assignedToId = currentUserId,
            labels = derivedLabels,
            updatedAtMillis = now
        )

        taskDao.upsertTask(entity)

        var savedEntity = entity
        tryRemote {
            val response = apiServiceProvider().createTask(
                CreateTaskRequestDto(
                    ticketCode = entity.ticketCode,
                    title = entity.title,
                    projectOrCategory = entity.category,
                    scheduledDateEpochDay = entity.scheduledDateEpochDay,
                    scheduledTimeMinutes = entity.scheduledTimeMinutes,
                    priority = entity.priority,
                    stage = entity.stage,
                    location = entity.location,
                    technicalNotes = entity.technicalNotes,
                    slaTargetTime = entity.slaTargetTime.ifBlank { null },
                    labels = entity.labels.takeIf { it.isNotEmpty() }
                )
            )
            if (response.isSuccessful) {
                val created = response.body()
                if (created != null) {
                    val createdEntity = created.toEntity(nowMillis = System.currentTimeMillis())
                    taskDao.deleteTaskById(localId)
                    taskDao.upsertTask(createdEntity)
                    savedEntity = createdEntity
                } else {
                    taskDao.markTaskSynced(localId)
                    savedEntity = entity.copy(isSynced = true)
                }
            }
        }

        savedEntity.toDomain()
    }

    override suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUsername = username.trim()
            val response = apiServiceProvider().login(
                LoginRequestDto(username = cleanUsername, password = password)
            )
            val body = response.body()
            val token = body?.resolvedToken
            if (response.isSuccessful && !token.isNullOrBlank()) {
                appPreferences?.saveAuthSession(
                    token = token,
                    technicianName = body.user?.resolvedName ?: cleanUsername.ifBlank { "Teknisi" },
                    role = body.user?.role ?: "technician",
                    userId = body.user?.id ?: 0L
                )
                Result.success(token)
            } else {
                val message = when (response.code()) {
                    401, 403 -> "Username atau kata sandi salah (HTTP ${response.code()})."
                    else -> "Login gagal (HTTP ${response.code()}). Periksa koneksi ke ping.fjrx.my.id."
                }
                Result.failure(IllegalStateException(message))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Periksa koneksi internet Anda."
            Result.failure(
                IllegalStateException("Gagal terhubung ke ping.fjrx.my.id ($detail)", e)
            )
        }
    }

    override suspend fun clearSyncedCacheOnLogout() = withContext(Dispatchers.IO) {
        taskDao.deleteAllSyncedTasks()
    }

    private suspend fun pushLocalEntityToServer(
        api: PingDuluApiService,
        entity: TaskEntity
    ): Boolean {
        return try {
            if (entity.id < 0L) {
                val createRes = api.createTask(
                    CreateTaskRequestDto(
                        ticketCode = entity.ticketCode,
                        title = entity.title,
                        projectOrCategory = entity.category,
                        scheduledDateEpochDay = entity.scheduledDateEpochDay,
                        scheduledTimeMinutes = entity.scheduledTimeMinutes,
                        priority = entity.priority,
                        stage = entity.stage,
                        location = entity.location,
                        technicalNotes = entity.technicalNotes,
                        slaTargetTime = entity.slaTargetTime.ifBlank { null },
                        labels = entity.labels.takeIf { it.isNotEmpty() }
                    )
                )
                if (createRes.isSuccessful) {
                    val created = createRes.body()
                    if (created != null) {
                        taskDao.deleteTaskById(entity.id)
                        taskDao.upsertTask(created.toEntity())
                    }
                    true
                } else {
                    false
                }
            } else {
                val stageRes = api.updateStage(entity.id, StageUpdateDto(stage = entity.stage))
                val timerRes = api.updateTimer(
                    entity.id,
                    TimerActionRequestDto(
                        action = if (entity.isTimerRunning) {
                            TimerActionRequestDto.ACTION_START
                        } else {
                            TimerActionRequestDto.ACTION_PAUSE
                        }
                    )
                )
                val checklistAllSynced = entity.checklist.all { item ->
                    api.toggleChecklistStep(
                        id = entity.id,
                        stepId = item.id,
                        body = ChecklistStepToggleDto(isDone = if (item.isDone) 1 else 0)
                    ).isSuccessful
                }
                stageRes.isSuccessful && timerRes.isSuccessful && checklistAllSynced
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

    private suspend inline fun tryRemote(block: () -> Unit) {
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Offline-first: swallow network failures; Room retains isSynced = false for later retry.
        }
    }
}
