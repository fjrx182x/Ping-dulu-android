package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.local.toDomain
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.dto.AddChecklistStepRequestDto
import com.example.data.remote.dto.AddCommentRequestDto
import com.example.data.remote.dto.CatalogResponseParser
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDetailResponseParser
import com.example.data.remote.dto.TaskPatchRequestDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.remote.dto.UpdateUserRequestDto
import com.example.data.remote.dto.toEntity
import com.example.domain.model.AccountDeactivatedException
import com.example.domain.model.ActiveTaskLimitExceededException
import com.example.domain.model.ActiveTaskLimitPolicy
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DEACTIVATED_ACCOUNT_MESSAGE
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.FieldTechnician
import com.example.domain.model.IndonesianHolidayCatalog
import com.example.domain.model.NationalHoliday
import com.example.domain.model.OfficialDefaultLabels
import com.example.domain.model.OfficialLabel
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TaskActivityFeed
import com.example.domain.model.TaskAttachment
import com.example.domain.model.TaskComment
import com.example.domain.model.TaskDetailWorkspaceState
import com.example.domain.model.TicketCategory
import com.example.domain.model.UserProfile
import com.example.domain.model.isDeactivatedAccountErrorPayload
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.util.AvatarUploadHelper
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.util.UUID

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val apiServiceProvider: () -> PingDuluApiService,
    private val appPreferences: AppPreferences? = null,
    private val database: AppDatabase? = null,
    private val converters: TaskConverters = TaskConverters()
) : TaskRepository {

    private val lastSyncErrorFlow = MutableStateFlow<String?>(null)
    private val techniciansFlow = MutableStateFlow<List<FieldTechnician>>(emptyList())
    private val officialLabelsFlow = MutableStateFlow<List<OfficialLabel>>(OfficialDefaultLabels)
    private val holidayCacheByYear = ConcurrentHashMap<Int, List<NationalHoliday>>()
    private val recentlyMutatedServerTaskIds = ConcurrentHashMap<Long, Long>()
    private val syncMutex = Mutex()
    @Volatile
    private var activityEndpointMode: ActivityEndpointMode = ActivityEndpointMode.UNKNOWN

    private enum class ActivityEndpointMode {
        UNKNOWN,
        EMBEDDED_IN_DETAIL,
        ACTIVITIES_PLURAL,
        ACTIVITY_SINGULAR,
        LOGS,
        NONE
    }

    private sealed interface CreateUploadOutcome {
        data class Synced(val entity: TaskEntity) : CreateUploadOutcome
        data class LimitExceeded(val exception: ActiveTaskLimitExceededException) : CreateUploadOutcome
        data class Failed(val reason: String) : CreateUploadOutcome
    }

    private sealed interface PushOutcome {
        data object Success : PushOutcome
        data class Failure(val reason: String) : PushOutcome
    }

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

    override fun observeLastSyncError(): Flow<String?> = lastSyncErrorFlow.asStateFlow()

    override fun getLastSyncError(): String? = lastSyncErrorFlow.value

    override fun observeTechnicians(): Flow<List<FieldTechnician>> = techniciansFlow.asStateFlow()

    override fun observeOfficialLabels(): Flow<List<OfficialLabel>> = officialLabelsFlow.asStateFlow()

    override suspend fun initializeOfflineCache() {
        withContext(Dispatchers.IO) {
            appPreferences?.warmUpTokenCache()
        }
    }

    override suspend fun refreshCatalogsFromServer() = withContext(Dispatchers.IO) {
        val api = apiServiceProvider()
        runCatching {
            val usersRes = api.getUsersRaw()
            if (usersRes.isSuccessful) {
                val raw = usersRes.body()?.string().orEmpty()
                val parsed = CatalogResponseParser.parseUsersJson(raw)
                if (parsed.isNotEmpty()) {
                    techniciansFlow.value = parsed
                }
            }
        }
        runCatching {
            val labelsRes = api.getLabelsRaw()
            if (labelsRes.isSuccessful) {
                val raw = labelsRes.body()?.string().orEmpty()
                val parsed = CatalogResponseParser.parseLabelsJson(raw)
                if (parsed.isNotEmpty()) {
                    officialLabelsFlow.value = parsed
                }
            }
        }
        Unit
    }

    override suspend fun refreshTasksFromServer(): SyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val api = apiServiceProvider()
            var uploadedCount = 0
            val failedUploadIds = mutableSetOf<Long>()
            var lastUploadError: String? = null

            // 1. Push locally mutated tasks first so offline field work is never overwritten.
            val unsyncedTasks = taskDao.getUnsyncedTasks()
            for (entity in unsyncedTasks) {
                when (val outcome = pushLocalEntityToServer(api, entity)) {
                    is PushOutcome.Success -> {
                        if (entity.id > 0L) {
                            taskDao.markTaskSynced(entity.id)
                        }
                        uploadedCount++
                    }
                    is PushOutcome.Failure -> {
                        failedUploadIds.add(entity.id)
                        lastUploadError = outcome.reason
                    }
                }
            }

            // 2. Pull fresh task snapshot from PingDulu API and reconcile by ID & client_sync_id.
            pullRemoteSnapshotInternal(
                api = api,
                uploadedCount = uploadedCount,
                failedUploadIds = failedUploadIds,
                lastUploadError = lastUploadError,
                allowStalePrune = true
            )
        }
    }

    /**
     * Strict PULL-ONLY refresh used by SSE live events and fallback polling.
     * NEVER sends POST /api/tasks for pending local rows, completely preventing SSE echo flooding loops.
     * NEVER deletes existing synced task cards during background pulls (upsert-only anti-vanishing guard).
     */
    override suspend fun pullTasksFromServerOnly(): SyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            pullRemoteSnapshotInternal(
                api = apiServiceProvider(),
                uploadedCount = 0,
                failedUploadIds = emptySet(),
                lastUploadError = null,
                allowStalePrune = false
            )
        }
    }

    private suspend fun pullRemoteSnapshotInternal(
        api: PingDuluApiService,
        uploadedCount: Int,
        failedUploadIds: Set<Long>,
        lastUploadError: String?,
        allowStalePrune: Boolean
    ): SyncResult {
        return try {
            val response = api.getTasks()
            if (response.code() == 401 || response.code() == 403) {
                val rawError = readErrorBodySnippet(response)
                val isDeactivated = response.code() == 403 && isDeactivatedAccountErrorPayload(rawError)
                val msg = if (isDeactivated) {
                    DEACTIVATED_ACCOUNT_MESSAGE
                } else {
                    "Sesi token kedaluwarsa atau belum login (${response.code()}). Data lokal tetap aman."
                }
                lastSyncErrorFlow.value = msg
                return SyncResult.AuthRequired(
                    message = msg,
                    isAccountDeactivated = isDeactivated
                )
            }
            if (!response.isSuccessful) {
                val err = formatHttpError(response, "GET /api/tasks")
                lastSyncErrorFlow.value = err
                return SyncResult.OfflineFallback(
                    pendingUploadCount = taskDao.getUnsyncedTasks().size,
                    reason = err
                )
            }

            val responseBody = response.body()
            if (responseBody == null || !responseBody.hasExplicitTaskList()) {
                val err = "Respons GET /api/tasks tidak memuat daftar tiket valid. Data lokal tetap dipertahankan."
                lastSyncErrorFlow.value = err
                return SyncResult.OfflineFallback(
                    pendingUploadCount = taskDao.getUnsyncedTasks().size,
                    reason = err
                )
            }

            val remoteDtos = responseBody.resolvedTasks().filter { it.id > 0L }
            val nowMillis = System.currentTimeMillis()
            val pendingLocalIds = taskDao.getUnsyncedTasks()
                .filter { it.id > 0L }
                .map { it.id }
                .toSet()
            val existingById = runCatching { taskDao.getAllTasks().first() }
                .getOrDefault(emptyList())
                .associateBy { it.id }
            val protectedIds = failedUploadIds + pendingLocalIds
            val knownTechById = techniciansFlow.value.associateBy { it.id }
            val entitiesToUpsert = remoteDtos
                .filter { it.id !in protectedIds }
                .map { dto ->
                    val mapped = dto.toEntity(nowMillis = nowMillis)
                    val existing = existingById[mapped.id]
                    val enrichedAssignees = if (knownTechById.isNotEmpty() && mapped.assignees.isNotEmpty()) {
                        mapped.assignees.map { assignee ->
                            val known = knownTechById[assignee.id]
                            if (known != null && (assignee.name.isBlank() || assignee.name == "Teknisi" || assignee.name.startsWith("Teknisi #"))) {
                                assignee.copy(name = known.resolvedName, role = known.role)
                            } else {
                                assignee
                            }
                        }
                    } else {
                        mapped.assignees
                    }
                    val hasExplicitServerEpoch = dto.scheduledDateEpochDay != null && dto.scheduledDateEpochDay > 15_000L
                    val preservedEpochDay = if (hasExplicitServerEpoch || existing == null) {
                        mapped.scheduledDateEpochDay
                    } else {
                        existing.scheduledDateEpochDay
                    }
                    val preservedTimeMinutes = dto.scheduledTimeMinutes ?: existing?.scheduledTimeMinutes
                    val preservedTimeSlot = if (dto.scheduledTimeMinutes != null || existing == null) {
                        mapped.timeSlotLabel
                    } else {
                        existing.timeSlotLabel
                    }
                    val cachedAttachments = existing?.attachments.orEmpty()
                    mapped.copy(
                        scheduledDateEpochDay = preservedEpochDay,
                        scheduledTimeMinutes = preservedTimeMinutes,
                        timeSlotLabel = preservedTimeSlot,
                        assignees = enrichedAssignees,
                        attachments = if (cachedAttachments.isNotEmpty()) {
                            mergeAttachments(mapped.attachments, cachedAttachments)
                        } else {
                            mapped.attachments
                        }
                    )
                }
            val activeRemoteIds = remoteDtos.map { it.id }
            val upsertedClientSyncIds = entitiesToUpsert.mapNotNull {
                it.clientSyncId?.trim()?.takeIf { id -> id.isNotEmpty() }
            }

            // Anti-Mass-Wipe Safety Guard:
            // 1. NEVER call deleteAllSyncedTasks() during a pull/sync (empty or >900 response never wipes Room).
            // 2. Background SSE / polling pulls (allowStalePrune == false) NEVER delete local task cards.
            // 3. Even on explicit full sync, refuse to prune if a single response would wipe >=50% of >=5 local synced tasks,
            //    and always shield tasks created/mutated locally in the last 15 seconds against mid-flight race conditions.
            val existingSyncedTasks = existingById.values.filter { it.isSynced && it.id > 0L }
            recentlyMutatedServerTaskIds.entries.removeAll { (nowMillis - it.value) > 15_000L }
            val recentlyUpdatedSyncedIds = recentlyMutatedServerTaskIds.keys.toList()
            val safeKeepIds = (activeRemoteIds + recentlyUpdatedSyncedIds).distinct()
            val safeKeepIdSet = safeKeepIds.toSet()
            val wouldBePrunedCount = existingSyncedTasks.count { it.id !in safeKeepIdSet }
            val isSuspiciousMassDrop = existingSyncedTasks.size >= 5 &&
                wouldBePrunedCount * 2 >= existingSyncedTasks.size
            val shouldPruneStale = allowStalePrune &&
                activeRemoteIds.isNotEmpty() &&
                safeKeepIds.size <= 900 &&
                !isSuspiciousMassDrop

            val reconcileBlock: suspend () -> Unit = {
                // Reconcile local negative-ID temp rows ONLY when their confirmed positive-ID replacement is being upserted
                for (syncId in upsertedClientSyncIds) {
                    taskDao.deletePendingByClientSyncId(syncId)
                }
                if (shouldPruneStale) {
                    taskDao.deleteStaleSyncedTasks(safeKeepIds)
                }
                if (entitiesToUpsert.isNotEmpty()) {
                    taskDao.upsertTasks(entitiesToUpsert)
                }
            }

            runInTransactionIfAvailable(reconcileBlock)

            appPreferences?.updateLastSyncedAt(nowMillis)
            if (lastUploadError != null) {
                lastSyncErrorFlow.value = lastUploadError
            } else if (failedUploadIds.isEmpty() && uploadedCount > 0) {
                lastSyncErrorFlow.value = null
            }
            SyncResult.Success(
                downloadedCount = entitiesToUpsert.size,
                uploadedCount = uploadedCount,
                failedUploadCount = failedUploadIds.size,
                lastUploadError = lastUploadError
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val reason = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Tidak ada koneksi ke server PingDulu"
            lastSyncErrorFlow.value = reason
            SyncResult.OfflineFallback(
                pendingUploadCount = taskDao.getUnsyncedTasks().size,
                reason = reason
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

        // Immediate real-time push when online (positive server IDs only — never PATCH temp negative IDs)
        val toggledItem = updatedChecklist.firstOrNull { it.id == checklistId }
        if (taskId > 0L && checklistId > 0L && toggledItem != null) {
            tryRemote {
                val response = apiServiceProvider().toggleChecklistStep(
                    id = taskId,
                    stepId = checklistId,
                    body = ChecklistStepToggleDto(isDone = if (toggledItem.isDone) 1 else 0)
                )
                if (response.isSuccessful) {
                    taskDao.markTaskSynced(taskId)
                    lastSyncErrorFlow.value = null
                } else {
                    lastSyncErrorFlow.value = formatHttpError(response, "PATCH checklist")
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
                    val stageRes = api.updateStage(
                        id = taskId,
                        body = StageUpdateDto(stage = Stage.IN_PROGRESS.apiValue)
                    )
                    stageSynced = stageRes.isSuccessful
                    if (!stageSynced) {
                        lastSyncErrorFlow.value = formatHttpError(stageRes, "POST stage")
                    }
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
                if (!willRun) {
                    runCatching {
                        api.patchTask(
                            id = taskId,
                            body = TaskPatchRequestDto(trackedSeconds = existing.trackedSeconds)
                        )
                    }
                }
                if (stageSynced && timerResponse.isSuccessful) {
                    taskDao.markTaskSynced(taskId)
                    lastSyncErrorFlow.value = null
                } else if (!timerResponse.isSuccessful) {
                    lastSyncErrorFlow.value = formatHttpError(timerResponse, "POST timer")
                }
            }
        }
    }

    override suspend fun persistTimerTick(taskId: Long, newTrackedSeconds: Long) = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId) ?: return@withContext
        if (!existing.isTimerRunning) return@withContext
        // Persist local stopwatch durability without flipping isSynced=false every 10s
        // (avoids triggering auto-push network loops & "Antre Sync" alerts while timer runs).
        // Explicit Start/Pause/Complete/Stage actions sync timer state to the server.
        taskDao.updateTaskTimer(
            taskId = taskId,
            seconds = newTrackedSeconds.coerceAtLeast(0L),
            isRunning = true,
            timerStartedAtMillis = existing.timerStartedAtMillis ?: System.currentTimeMillis(),
            isSynced = existing.isSynced,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    override suspend fun updateTaskStage(taskId: Long, newStage: Stage) = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId) ?: return@withContext
        val now = System.currentTimeMillis()

        taskDao.updateTaskStage(
            taskId = taskId,
            stage = newStage.apiValue,
            isRunning = false,
            timerStartedAtMillis = null,
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
                    lastSyncErrorFlow.value = null
                } else if (response.code() == 403) {
                    // Rollback local Room state immediately on POLICY_FORBIDDEN so it never traps in Antre Sync
                    taskDao.upsertTask(existing)
                    lastSyncErrorFlow.value = extractPolicyErrorMessage(response)
                } else {
                    lastSyncErrorFlow.value = formatHttpError(response, "POST stage")
                }
            }
        }
    }

    override suspend fun markTaskCompleted(taskId: Long) {
        completeTaskWithPolicy(taskId)
    }

    override suspend fun completeTaskWithPolicy(taskId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId)
            ?: return@withContext Result.failure(IllegalStateException("Tiket tidak ditemukan."))
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
            try {
                val api = apiServiceProvider()
                if (existing.isTimerRunning) {
                    runCatching {
                        api.updateTimer(
                            id = taskId,
                            body = TimerActionRequestDto(action = TimerActionRequestDto.ACTION_PAUSE)
                        )
                    }
                }
                val stageRes = api.updateStage(
                    id = taskId,
                    body = StageUpdateDto(stage = Stage.DONE.apiValue)
                )
                when {
                    stageRes.isSuccessful -> {
                        taskDao.markTaskSynced(taskId)
                        lastSyncErrorFlow.value = null
                        return@withContext Result.success(Unit)
                    }
                    stageRes.code() == 403 -> {
                        // Rollback Room immediately on HTTP 403 POLICY_FORBIDDEN
                        taskDao.upsertTask(existing)
                        val policyMsg = extractPolicyErrorMessage(stageRes)
                        lastSyncErrorFlow.value = policyMsg
                        return@withContext Result.failure(IllegalStateException(policyMsg))
                    }
                    else -> {
                        lastSyncErrorFlow.value = formatHttpError(stageRes, "POST stage")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastSyncErrorFlow.value = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Sedang offline — antre sinkronisasi otomatis"
            }
        }
        Result.success(Unit)
    }

    override suspend fun workTask(taskId: Long): Result<String> = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId)
            ?: return@withContext Result.failure(IllegalStateException("Tiket tidak ditemukan."))
        val now = System.currentTimeMillis()
        val session = runCatching { appPreferences?.sessionFlow?.first() }.getOrNull()
        val currentUserId = session?.userId?.takeIf { it > 0L }
        val currentUserName = session?.technicianName?.takeIf { it.isNotBlank() } ?: "Teknisi"
        val currentUserRole = session?.technicianRole?.takeIf { it.isNotBlank() } ?: "technician"

        val alreadyAssigned = currentUserId != null && (
            existing.assignedToId == currentUserId ||
                existing.assignees.any { it.id == currentUserId }
            )
        val updatedAssignees = if (currentUserId != null && !alreadyAssigned) {
            existing.assignees + Assignee(
                id = currentUserId,
                name = currentUserName,
                role = currentUserRole,
                isLead = existing.assignees.isEmpty()
            )
        } else {
            existing.assignees
        }
        val optimistic = existing.copy(
            stage = Stage.IN_PROGRESS.apiValue,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            assignedToId = existing.assignedToId ?: currentUserId,
            assignees = updatedAssignees,
            isSynced = false,
            updatedAtMillis = now
        )
        taskDao.upsertTask(optimistic)

        if (taskId <= 0L) {
            return@withContext Result.success("Tiket mulai dikerjakan (tersimpan lokal).")
        }

        try {
            val response = apiServiceProvider().workTask(taskId)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    val serverDto = body?.task?.takeIf { it.id > 0L }
                    val finalized = if (serverDto != null) {
                        val serverEntity = serverDto.toEntity(nowMillis = now)
                        val hasServerEpoch = serverDto.scheduledDateEpochDay != null && serverDto.scheduledDateEpochDay > 15_000L
                        serverEntity.copy(
                            isSynced = true,
                            scheduledDateEpochDay = if (hasServerEpoch) serverEntity.scheduledDateEpochDay else existing.scheduledDateEpochDay,
                            scheduledTimeMinutes = serverDto.scheduledTimeMinutes ?: existing.scheduledTimeMinutes,
                            timeSlotLabel = if (serverDto.scheduledTimeMinutes != null) serverEntity.timeSlotLabel else existing.timeSlotLabel,
                            location = if (!serverDto.location.isNullOrBlank()) serverEntity.location else existing.location,
                            technicalNotes = if (!serverDto.technicalNotes.isNullOrBlank()) serverEntity.technicalNotes else existing.technicalNotes,
                            checklist = serverEntity.checklist.ifEmpty { existing.checklist },
                            assignees = serverEntity.assignees.ifEmpty { optimistic.assignees },
                            labels = serverEntity.labels.ifEmpty { existing.labels },
                            attachments = mergeAttachments(serverEntity.attachments, existing.attachments),
                            createdById = serverEntity.createdById ?: existing.createdById,
                            createdByName = serverEntity.createdByName ?: existing.createdByName,
                            creatorClient = serverEntity.creatorClient ?: existing.creatorClient,
                            creatorRole = serverEntity.creatorRole ?: existing.creatorRole
                        )
                    } else {
                        val resolvedStage = body?.stage?.takeIf { it.isNotBlank() }
                            ?.let { Stage.fromString(it).apiValue }
                            ?: Stage.IN_PROGRESS.apiValue
                        optimistic.copy(stage = resolvedStage, isSynced = true)
                    }
                    taskDao.upsertTask(finalized)
                    recentlyMutatedServerTaskIds[finalized.id] = now
                    lastSyncErrorFlow.value = null
                    val defaultMsg = if (body?.isCollaboratorFlag == true) {
                        "$currentUserName bergabung mengerjakan tiket."
                    } else {
                        "Tiket dikerjakan oleh $currentUserName."
                    }
                    Result.success(body?.message?.trim()?.takeIf { it.isNotEmpty() } ?: defaultMsg)
                }
                response.code() == 403 || response.code() == 409 -> {
                    taskDao.upsertTask(existing)
                    val rawBody = readErrorBodySnippet(response)
                    if (response.code() == 409 && ActiveTaskLimitPolicy.isClaimLimitExceededPayload(rawBody)) {
                        val limitEx = ActiveTaskLimitPolicy.parseServerLimitException(
                            rawBody = rawBody,
                            isDelegationContext = false
                        )
                        lastSyncErrorFlow.value = limitEx.serverMessage
                        Result.failure(limitEx)
                    } else {
                        val policyMsg = extractReadableMessage(rawBody).ifBlank {
                            "Anda tidak memiliki izin untuk melakukan tindakan ini pada tiket ini (HTTP ${response.code()})."
                        }
                        lastSyncErrorFlow.value = policyMsg
                        Result.failure(IllegalStateException(policyMsg))
                    }
                }
                else -> {
                    val err = formatHttpError(response, "POST work")
                    lastSyncErrorFlow.value = err
                    Result.success("Tiket ditandai dikerjakan secara lokal ($err).")
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            lastSyncErrorFlow.value = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Sedang offline — antre sinkronisasi otomatis"
            Result.success("Tiket dikerjakan secara lokal (antre sync saat online).")
        }
    }

    override suspend fun claimTask(taskId: Long): Result<Task> = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId)
            ?: return@withContext Result.failure(IllegalStateException("Tiket tidak ditemukan."))
        val now = System.currentTimeMillis()
        val session = runCatching { appPreferences?.sessionFlow?.first() }.getOrNull()
        val currentUserId = session?.userId?.takeIf { it > 0L } ?: 0L
        val currentUserName = session?.technicianName?.takeIf { it.isNotBlank() } ?: "Teknisi"
        val currentUserRole = session?.technicianRole?.takeIf { it.isNotBlank() } ?: "technician"

        val claimedAssignee = Assignee(
            id = currentUserId,
            name = currentUserName,
            role = currentUserRole,
            isLead = true
        )
        val optimistic = existing.copy(
            assignedToId = currentUserId.takeIf { it > 0L } ?: existing.assignedToId,
            assignees = listOf(claimedAssignee),
            isSynced = false,
            updatedAtMillis = now
        )
        taskDao.upsertTask(optimistic)

        if (taskId <= 0L) {
            return@withContext Result.success(optimistic.toDomain())
        }

        try {
            val response = apiServiceProvider().claimTask(taskId)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    val serverDto = body?.task?.takeIf { it.id > 0L }
                    val finalized = if (serverDto != null) {
                        val serverEntity = serverDto.toEntity(nowMillis = now)
                        val hasServerEpoch = serverDto.scheduledDateEpochDay != null && serverDto.scheduledDateEpochDay > 15_000L
                        serverEntity.copy(
                            isSynced = true,
                            scheduledDateEpochDay = if (hasServerEpoch) serverEntity.scheduledDateEpochDay else existing.scheduledDateEpochDay,
                            scheduledTimeMinutes = serverDto.scheduledTimeMinutes ?: existing.scheduledTimeMinutes,
                            timeSlotLabel = if (serverDto.scheduledTimeMinutes != null) serverEntity.timeSlotLabel else existing.timeSlotLabel,
                            location = if (!serverDto.location.isNullOrBlank()) serverEntity.location else existing.location,
                            technicalNotes = if (!serverDto.technicalNotes.isNullOrBlank()) serverEntity.technicalNotes else existing.technicalNotes,
                            checklist = serverEntity.checklist.ifEmpty { existing.checklist },
                            assignees = serverEntity.assignees.ifEmpty { optimistic.assignees },
                            labels = serverEntity.labels.ifEmpty { existing.labels },
                            attachments = mergeAttachments(serverEntity.attachments, existing.attachments),
                            createdById = serverEntity.createdById ?: existing.createdById,
                            createdByName = serverEntity.createdByName ?: existing.createdByName,
                            creatorClient = serverEntity.creatorClient ?: existing.creatorClient,
                            creatorRole = serverEntity.creatorRole ?: existing.creatorRole
                        )
                    } else {
                        optimistic.copy(isSynced = true)
                    }
                    taskDao.upsertTask(finalized)
                    recentlyMutatedServerTaskIds[finalized.id] = now
                    lastSyncErrorFlow.value = null
                    Result.success(finalized.toDomain())
                }
                response.code() == 403 || response.code() == 409 -> {
                    taskDao.upsertTask(existing)
                    val rawBody = readErrorBodySnippet(response)
                    if (response.code() == 409 && ActiveTaskLimitPolicy.isClaimLimitExceededPayload(rawBody)) {
                        val limitEx = ActiveTaskLimitPolicy.parseServerLimitException(
                            rawBody = rawBody,
                            isDelegationContext = false
                        )
                        lastSyncErrorFlow.value = limitEx.serverMessage
                        Result.failure(limitEx)
                    } else {
                        val errMsg = extractReadableMessage(rawBody).ifBlank {
                            "Anda tidak memiliki izin untuk mengambil tiket ini (HTTP ${response.code()})."
                        }
                        lastSyncErrorFlow.value = errMsg
                        Result.failure(IllegalStateException(errMsg))
                    }
                }
                else -> {
                    val err = formatHttpError(response, "POST claim")
                    lastSyncErrorFlow.value = err
                    Result.failure(IllegalStateException(err))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            lastSyncErrorFlow.value = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Sedang offline — klaim tersimpan lokal"
            Result.success(optimistic.toDomain())
        }
    }

    override suspend fun addChecklistStep(
        taskId: Long,
        label: String
    ): Result<ChecklistItem> = withContext(Dispatchers.IO) {
        val cleanLabel = label.trim()
        if (cleanLabel.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Langkah checklist tidak boleh kosong."))
        }
        val existing = taskDao.getTaskByIdOnce(taskId)
            ?: return@withContext Result.failure(IllegalStateException("Tiket tidak ditemukan."))
        val now = System.currentTimeMillis()
        val tempId = -now

        if (taskId <= 0L) {
            val localStep = ChecklistItem(id = tempId, label = cleanLabel, isDone = false)
            val updated = existing.copy(
                checklist = existing.checklist + localStep,
                isSynced = false,
                updatedAtMillis = now
            )
            taskDao.upsertTask(updated)
            return@withContext Result.success(localStep)
        }

        try {
            val response = apiServiceProvider().addChecklistStep(
                taskId = taskId,
                body = AddChecklistStepRequestDto(label = cleanLabel)
            )
            when {
                response.isSuccessful -> {
                    val raw = response.body()?.string().orEmpty()
                    val createdStep = TaskDetailResponseParser.parseAddedChecklistStep(
                        rawJson = raw,
                        fallbackLabel = cleanLabel,
                        fallbackTempId = tempId
                    )
                    val refreshedExisting = taskDao.getTaskByIdOnce(taskId) ?: existing
                    val mergedChecklist = (refreshedExisting.checklist + createdStep)
                        .distinctBy { if (it.id > 0L) "id:${it.id}" else "lbl:${it.label.lowercase()}" }
                    taskDao.upsertTask(
                        refreshedExisting.copy(
                            checklist = mergedChecklist,
                            isSynced = true,
                            updatedAtMillis = now
                        )
                    )
                    lastSyncErrorFlow.value = null
                    Result.success(createdStep)
                }
                response.code() == 403 || response.code() == 409 -> {
                    val errMsg = extractPolicyErrorMessage(response)
                    lastSyncErrorFlow.value = errMsg
                    Result.failure(IllegalStateException(errMsg))
                }
                else -> {
                    val err = formatHttpError(response, "POST checklist")
                    lastSyncErrorFlow.value = err
                    Result.failure(IllegalStateException(err))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            // Offline-first fallback: negative temp ID prevents remote 404 on immediate toggle
            val offlineStep = ChecklistItem(id = tempId, label = cleanLabel, isDone = false)
            taskDao.upsertTask(
                existing.copy(
                    checklist = existing.checklist + offlineStep,
                    isSynced = false,
                    updatedAtMillis = now
                )
            )
            lastSyncErrorFlow.value = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Sedang offline — langkah checklist disimpan lokal"
            Result.success(offlineStep)
        }
    }

    override suspend fun delegateTechnicians(
        taskId: Long,
        assigneeIds: List<Long>
    ): Result<Task> = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId)
            ?: return@withContext Result.failure(IllegalStateException("Tiket tidak ditemukan."))
        val cleanIds = assigneeIds.filter { it > 0L }.distinct()
        if (cleanIds.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Pilih minimal satu teknisi."))
        }
        val now = System.currentTimeMillis()
        val techById = techniciansFlow.value.associateBy { it.id }
        val existingById = existing.assignees.associateBy { it.id }
        val updatedAssignees = cleanIds.mapIndexed { idx, uid ->
            val knownTech = techById[uid]
            val prev = existingById[uid]
            Assignee(
                id = uid,
                name = knownTech?.resolvedName ?: prev?.name ?: "Teknisi #$uid",
                role = knownTech?.role ?: prev?.role ?: "technician",
                isLead = idx == 0
            )
        }
        val leadId = cleanIds.firstOrNull()
        val optimistic = existing.copy(
            assignedToId = leadId,
            assignees = updatedAssignees,
            isSynced = false,
            updatedAtMillis = now
        )
        taskDao.upsertTask(optimistic)

        if (taskId <= 0L) {
            return@withContext Result.success(optimistic.toDomain())
        }

        try {
            val response = apiServiceProvider().patchTask(
                id = taskId,
                body = TaskPatchRequestDto(
                    assigneeIds = cleanIds,
                    assignedToId = leadId
                )
            )
            when {
                response.isSuccessful -> {
                    val finalized = optimistic.copy(
                        isSynced = true,
                        checklist = existing.checklist
                    )
                    taskDao.upsertTask(finalized)
                    lastSyncErrorFlow.value = null
                    Result.success(finalized.toDomain())
                }
                response.code() == 409 -> {
                    taskDao.upsertTask(existing)
                    val rawBody = readErrorBodySnippet(response)
                    if (ActiveTaskLimitPolicy.isClaimLimitExceededPayload(rawBody)) {
                        val limitEx = ActiveTaskLimitPolicy.parseServerLimitException(
                            rawBody = rawBody,
                            isDelegationContext = true
                        )
                        lastSyncErrorFlow.value = limitEx.serverMessage
                        Result.failure(limitEx)
                    } else {
                        val errMsg = extractReadableMessage(rawBody).ifBlank {
                            "Gagal mendelegasikan teknisi (HTTP 409)."
                        }
                        lastSyncErrorFlow.value = errMsg
                        Result.failure(IllegalStateException(errMsg))
                    }
                }
                response.code() == 403 -> {
                    taskDao.upsertTask(existing)
                    val errMsg = extractPolicyErrorMessage(response)
                    lastSyncErrorFlow.value = errMsg
                    Result.failure(IllegalStateException(errMsg))
                }
                else -> {
                    taskDao.upsertTask(existing)
                    val err = formatHttpError(response, "PATCH assignees")
                    lastSyncErrorFlow.value = err
                    Result.failure(IllegalStateException(err))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            lastSyncErrorFlow.value = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Sedang offline — delegasi teknisi disimpan lokal"
            Result.success(optimistic.toDomain())
        }
    }

    override suspend fun deleteTask(taskId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId)
            ?: return@withContext Result.failure(IllegalStateException("Tiket tidak ditemukan."))

        if (taskId < 0L) {
            taskDao.deleteTaskById(taskId)
            return@withContext Result.success(Unit)
        }

        try {
            val response = apiServiceProvider().deleteTask(taskId)
            when {
                response.isSuccessful || response.code() == 404 -> {
                    taskDao.deleteTaskById(taskId)
                    lastSyncErrorFlow.value = null
                    Result.success(Unit)
                }
                response.code() == 403 -> {
                    val policyMsg = extractPolicyErrorMessage(response)
                    lastSyncErrorFlow.value = policyMsg
                    Result.failure(IllegalStateException(policyMsg))
                }
                else -> {
                    val err = formatHttpError(response, "DELETE /api/tasks/$taskId")
                    lastSyncErrorFlow.value = err
                    Result.failure(IllegalStateException(err))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Gagal menghapus tiket — periksa koneksi ke server PingDulu."
            lastSyncErrorFlow.value = msg
            Result.failure(IllegalStateException(msg, e))
        }
    }

    override suspend fun fetchHolidaysForYear(year: Int): List<NationalHoliday> = withContext(Dispatchers.IO) {
        holidayCacheByYear[year]?.let { return@withContext it }
        val fallback = IndonesianHolidayCatalog.holidaysForYear(year)
        val remote = try {
            val res = apiServiceProvider().getHolidays(year)
            if (res.isSuccessful) {
                res.body()?.holidays.orEmpty().mapNotNull { it.toDomain() }
            } else {
                emptyList()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            emptyList()
        }
        val resolved = if (remote.isNotEmpty()) remote else fallback
        holidayCacheByYear[year] = resolved
        resolved
    }

    override suspend fun fetchCalendarMonth(
        year: Int,
        month: Int
    ): Result<List<NationalHoliday>> = withContext(Dispatchers.IO) {
        val yearHolidays = fetchHolidaysForYear(year)
        val monthFallback = yearHolidays.filter {
            DateEpochUtil.yearMonthFromEpochDay(it.epochDay) == (year to month)
        }
        try {
            val res = apiServiceProvider().getCalendar(year = year, month = month)
            if (res.isSuccessful) {
                val remoteHolidays = res.body()?.holidays.orEmpty().mapNotNull { it.toDomain() }
                val merged = if (remoteHolidays.isNotEmpty()) {
                    val byEpoch = LinkedHashMap<Long, NationalHoliday>()
                    (remoteHolidays + monthFallback).forEach { holiday ->
                        byEpoch.putIfAbsent(holiday.epochDay, holiday)
                    }
                    byEpoch.values.sortedBy { it.epochDay }
                } else {
                    monthFallback
                }
                Result.success(merged)
            } else {
                Result.success(monthFallback)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            Result.success(monthFallback)
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
        labels: List<String>,
        assigneeIds: List<Long>,
        labelIds: List<Long>,
        checklistSteps: List<String>,
        isRecurring: Boolean,
        recurrenceInterval: String?
    ): Task = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val localId = -now // Negative ID denotes locally created offline task until server assigns ID
        val currentSession = appPreferences?.sessionFlow?.first()
        val currentUserId = currentSession?.userId?.takeIf { it > 0L }
        val techName = currentSession?.technicianName?.takeIf { it.isNotBlank() } ?: "Teknisi"
        val techRole = currentSession?.technicianRole?.takeIf { it.isNotBlank() } ?: "technician"
        val ticketCode = generateCollisionFreeTicketCode(category = category, nowMillis = now)
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

        val cleanCustomSteps = checklistSteps.map { it.trim() }.filter { it.isNotEmpty() }
        val resolvedChecklist = if (cleanCustomSteps.isNotEmpty()) {
            cleanCustomSteps.mapIndexed { idx, stepLabel ->
                ChecklistItem(id = now + idx + 1L, label = stepLabel, isDone = false)
            }
        } else {
            listOf(
                ChecklistItem(id = now + 1L, label = "Inspeksi fisik perangkat & konektor", isDone = false),
                ChecklistItem(id = now + 2L, label = "Pengujian fungsional & dokumentasi SPK", isDone = false)
            )
        }

        val cleanAssigneeIds = assigneeIds.filter { it > 0L }.distinct()
        val knownTechById = techniciansFlow.value.associateBy { it.id }
        val resolvedAssignees = if (cleanAssigneeIds.isNotEmpty()) {
            cleanAssigneeIds.mapIndexed { index, uid ->
                val tech = knownTechById[uid]
                Assignee(
                    id = uid,
                    name = tech?.resolvedName
                        ?: if (uid == currentUserId) techName else "Teknisi #$uid",
                    role = tech?.role
                        ?: if (uid == currentUserId) techRole else "technician",
                    isLead = index == 0
                )
            }
        } else {
            listOf(
                Assignee(
                    id = currentUserId ?: 0L,
                    name = techName,
                    role = techRole,
                    isLead = true
                )
            )
        }
        val leadAssigneeId = resolvedAssignees.firstOrNull()?.id?.takeIf { it > 0L } ?: currentUserId

        val cleanScheduledMinutes = scheduledTimeMinutes?.coerceIn(0, 23 * 60 + 59)
        val cleanRecurrenceInterval = if (isRecurring) {
            recurrenceInterval?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "WEEKLY"
        } else {
            null
        }
        val clientSyncId = UUID.randomUUID().toString()
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
            checklist = resolvedChecklist,
            assignees = resolvedAssignees,
            isSynced = false,
            createdAtMillis = now,
            dueDateMillis = null,
            assignedToId = leadAssigneeId,
            labels = derivedLabels,
            updatedAtMillis = now,
            clientSyncId = clientSyncId,
            isRecurring = isRecurring,
            recurrenceInterval = cleanRecurrenceInterval,
            createdById = currentUserId,
            createdByName = techName,
            creatorClient = "android",
            creatorRole = techRole
        )

        taskDao.upsertTask(entity)

        val positiveLabelIds = labelIds.filter { it > 0L }.distinct()
        // Serialize create upload with syncMutex so concurrent refresh/retry cannot double-POST the same task.
        val savedEntity = syncMutex.withLock {
            val stillPending = taskDao.getTaskByIdOnce(localId)
            if (stillPending == null) {
                taskDao.getTaskByClientSyncId(clientSyncId) ?: entity.copy(isSynced = true)
            } else {
                when (
                    val outcome = uploadNewTaskToServer(
                        api = apiServiceProvider(),
                        entity = stillPending,
                        explicitLabelIds = positiveLabelIds
                    )
                ) {
                    is CreateUploadOutcome.Synced -> outcome.entity
                    is CreateUploadOutcome.LimitExceeded -> {
                        // Rollback temporary negative-ID task immediately when server enforces 5 active task limit
                        taskDao.deleteTaskById(localId)
                        taskDao.deletePendingByClientSyncId(clientSyncId)
                        throw outcome.exception
                    }
                    is CreateUploadOutcome.Failed -> stillPending
                }
            }
        }

        savedEntity.toDomain()
    }

    override suspend fun fetchTaskAttachments(taskId: Long): Result<List<TaskAttachment>> =
        withContext(Dispatchers.IO) {
            val existingEntity = taskDao.getTaskByIdOnce(taskId)
            val cached = existingEntity?.attachments.orEmpty()
            if (taskId <= 0L) return@withContext Result.success(cached)

            val api = apiServiceProvider()
            var remoteAttachments: List<TaskAttachment> = emptyList()
            var detailEndpointSucceeded = false

            // 1. Primary path: PingDulu embeds attachments inside GET /api/tasks/{id} (or GET /api/tasks).
            try {
                val detailRes = api.getTaskDetailRaw(taskId)
                if (detailRes.isSuccessful) {
                    detailEndpointSucceeded = true
                    val raw = detailRes.body()?.string().orEmpty()
                    remoteAttachments = TaskDetailResponseParser.parseAttachmentsList(
                        rawJson = raw,
                        fallbackTaskId = taskId
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Fall through to optional dedicated endpoint / local cache
            }

            // 2. Optional dedicated route: GET /api/tasks/{id}/attachments.
            // If server returns HTTP 404/405/501 (route not exposed), fall back silently to Room cache
            // instead of showing a false-positive red error banner to the technician.
            if (remoteAttachments.isEmpty()) {
                try {
                    val res = api.getAttachmentsRaw(taskId)
                    if (res.isSuccessful) {
                        val raw = res.body()?.string().orEmpty()
                        remoteAttachments = TaskDetailResponseParser.parseAttachmentsList(
                            rawJson = raw,
                            fallbackTaskId = taskId
                        )
                    } else if (res.code() != 404 && res.code() != 405 && res.code() != 501) {
                        if (!detailEndpointSucceeded && cached.isEmpty()) {
                            return@withContext Result.failure(
                                IllegalStateException(formatHttpError(res, "GET attachments"))
                            )
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    if (!detailEndpointSucceeded && cached.isEmpty()) {
                        return@withContext Result.failure(
                            IllegalStateException(
                                e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Gagal memuat lampiran.",
                                e
                            )
                        )
                    }
                }
            }

            val merged = mergeAttachments(remoteAttachments, cached)
            if (existingEntity != null && merged != cached) {
                taskDao.upsertTask(existingEntity.copy(attachments = merged))
            }
            Result.success(merged)
        }

    override suspend fun uploadTaskAttachment(
        taskId: Long,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<TaskAttachment> = withContext(Dispatchers.IO) {
        if (taskId <= 0L) {
            return@withContext Result.failure(
                IllegalStateException("Tiket belum tersinkron ke server. Sinkronkan dahulu sebelum unggah lampiran.")
            )
        }
        if (bytes.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Berkas kosong tidak dapat diunggah."))
        }
        if (bytes.size.toLong() > TaskDetailWorkspaceState.MAX_ATTACHMENT_BYTES) {
            return@withContext Result.failure(
                IllegalStateException("Ukuran berkas melebihi batas maksimal 10 MB.")
            )
        }
        try {
            val safeFileName = fileName.trim().ifBlank { "bukti_${System.currentTimeMillis()}.jpg" }
            val safeMime = mimeType.trim().ifBlank { "application/octet-stream" }
            val requestBody = bytes.toRequestBody(safeMime.toMediaTypeOrNull())
            val multipart = MultipartBody.Part.createFormData("file", safeFileName, requestBody)
            val res = apiServiceProvider().uploadAttachment(taskId = taskId, file = multipart)
            if (res.isSuccessful) {
                val raw = res.body()?.string().orEmpty()
                val parsed = TaskDetailResponseParser.parseSingleAttachment(
                    rawJson = raw,
                    fallbackTaskId = taskId,
                    fallbackFileName = safeFileName,
                    fallbackMimeType = safeMime,
                    fallbackSizeBytes = bytes.size.toLong()
                )
                // Persist uploaded attachment metadata into Room cache so reopening the sheet or offline view retains it
                val existingEntity = taskDao.getTaskByIdOnce(taskId)
                if (existingEntity != null) {
                    val merged = mergeAttachments(existingEntity.attachments, listOf(parsed))
                    taskDao.upsertTask(existingEntity.copy(attachments = merged))
                }
                Result.success(parsed)
            } else {
                Result.failure(IllegalStateException(formatHttpError(res, "POST attachment")))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Gagal mengunggah berkas ke server.",
                    e
                )
            )
        }
    }

    override suspend fun fetchTaskComments(taskId: Long): Result<List<TaskComment>> =
        withContext(Dispatchers.IO) {
            if (taskId <= 0L) return@withContext Result.success(emptyList())
            try {
                val (userDirectoryById, userDirectoryByUsername) = buildUserNameDirectories(taskId)
                val res = apiServiceProvider().getCommentsRaw(taskId)
                if (res.isSuccessful) {
                    val raw = res.body()?.string().orEmpty()
                    Result.success(
                        TaskDetailResponseParser.parseCommentsList(
                            rawJson = raw,
                            fallbackTaskId = taskId,
                            userNameById = userDirectoryById,
                            userNameByUsername = userDirectoryByUsername
                        )
                    )
                } else if (res.code() == 404 || res.code() == 405) {
                    Result.success(emptyList())
                } else {
                    Result.failure(IllegalStateException(formatHttpError(res, "GET comments")))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Gagal memuat komentar.",
                        e
                    )
                )
            }
        }

    override suspend fun postTaskComment(
        taskId: Long,
        commentText: String
    ): Result<TaskComment> = withContext(Dispatchers.IO) {
        val cleanText = commentText.trim()
        if (cleanText.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Komentar tidak boleh kosong."))
        }
        if (taskId <= 0L) {
            return@withContext Result.failure(
                IllegalStateException("Tiket belum tersinkron ke server. Sinkronkan dahulu untuk mengirim komentar.")
            )
        }
        try {
            val session = appPreferences?.sessionFlow?.first()
            val fallbackAuthor = session?.technicianName?.ifBlank { "Teknisi" } ?: "Teknisi"
            val fallbackRole = session?.technicianRole?.ifBlank { "technician" } ?: "technician"
            val (userDirectoryById, userDirectoryByUsername) = buildUserNameDirectories(taskId)
            val res = apiServiceProvider().postComment(
                taskId = taskId,
                body = AddCommentRequestDto(commentText = cleanText)
            )
            if (res.isSuccessful) {
                val raw = res.body()?.string().orEmpty()
                val parsed = TaskDetailResponseParser.parseSingleComment(
                    rawJson = raw,
                    fallbackTaskId = taskId,
                    fallbackAuthorName = fallbackAuthor,
                    fallbackAuthorRole = fallbackRole,
                    fallbackText = cleanText,
                    userNameById = userDirectoryById,
                    userNameByUsername = userDirectoryByUsername
                )
                Result.success(parsed)
            } else {
                Result.failure(IllegalStateException(formatHttpError(res, "POST comment")))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Gagal mengirim komentar.",
                    e
                )
            )
        }
    }

    override suspend fun fetchTaskActivities(taskId: Long): Result<TaskActivityFeed> =
        withContext(Dispatchers.IO) {
            if (taskId <= 0L) return@withContext Result.success(TaskActivityFeed(emptyList(), isFromServer = false))
            val api = apiServiceProvider()
            val (byId, byUsername) = buildUserNameDirectories(taskId)

            suspend fun tryParseEndpoint(
                requireExplicitKey: Boolean = false,
                call: suspend () -> Response<okhttp3.ResponseBody>
            ): Pair<Boolean, List<com.example.domain.model.TaskActivityItem>> {
                return try {
                    val res = call()
                    if (res.isSuccessful) {
                        val raw = res.body()?.string().orEmpty()
                        if (requireExplicitKey && !TaskDetailResponseParser.hasEmbeddedActivitiesArray(raw)) {
                            false to emptyList()
                        } else {
                            true to TaskDetailResponseParser.parseActivitiesList(
                                rawJson = raw,
                                fallbackTaskId = taskId,
                                userNameById = byId,
                                userNameByUsername = byUsername
                            )
                        }
                    } else {
                        false to emptyList()
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    false to emptyList()
                }
            }

            when (activityEndpointMode) {
                ActivityEndpointMode.EMBEDDED_IN_DETAIL -> {
                    val (ok, list) = tryParseEndpoint(requireExplicitKey = true) { api.getTaskDetailRaw(taskId) }
                    return@withContext Result.success(TaskActivityFeed(items = list, isFromServer = ok))
                }
                ActivityEndpointMode.ACTIVITIES_PLURAL -> {
                    val (ok, list) = tryParseEndpoint { api.getActivitiesRaw(taskId) }
                    return@withContext Result.success(TaskActivityFeed(items = list, isFromServer = ok))
                }
                ActivityEndpointMode.ACTIVITY_SINGULAR -> {
                    val (ok, list) = tryParseEndpoint { api.getActivitySingularRaw(taskId) }
                    return@withContext Result.success(TaskActivityFeed(items = list, isFromServer = ok))
                }
                ActivityEndpointMode.LOGS -> {
                    val (ok, list) = tryParseEndpoint { api.getTaskLogsRaw(taskId) }
                    return@withContext Result.success(TaskActivityFeed(items = list, isFromServer = ok))
                }
                ActivityEndpointMode.NONE -> {
                    return@withContext Result.success(TaskActivityFeed(emptyList(), isFromServer = false))
                }
                ActivityEndpointMode.UNKNOWN -> {
                    // Probe once in priority order and cache the active server route to avoid redundant 404s.
                    val (detailOk, detailList) = tryParseEndpoint(requireExplicitKey = true) {
                        api.getTaskDetailRaw(taskId)
                    }
                    if (detailOk) {
                        activityEndpointMode = ActivityEndpointMode.EMBEDDED_IN_DETAIL
                        return@withContext Result.success(TaskActivityFeed(items = detailList, isFromServer = true))
                    }

                    val (pluralOk, pluralList) = tryParseEndpoint { api.getActivitiesRaw(taskId) }
                    if (pluralOk) {
                        activityEndpointMode = ActivityEndpointMode.ACTIVITIES_PLURAL
                        return@withContext Result.success(TaskActivityFeed(items = pluralList, isFromServer = true))
                    }

                    val (singularOk, singularList) = tryParseEndpoint { api.getActivitySingularRaw(taskId) }
                    if (singularOk) {
                        activityEndpointMode = ActivityEndpointMode.ACTIVITY_SINGULAR
                        return@withContext Result.success(TaskActivityFeed(items = singularList, isFromServer = true))
                    }

                    val (logsOk, logsList) = tryParseEndpoint { api.getTaskLogsRaw(taskId) }
                    if (logsOk) {
                        activityEndpointMode = ActivityEndpointMode.LOGS
                        return@withContext Result.success(TaskActivityFeed(items = logsList, isFromServer = true))
                    }

                    activityEndpointMode = ActivityEndpointMode.NONE
                    return@withContext Result.success(TaskActivityFeed(emptyList(), isFromServer = false))
                }
            }
        }

    private suspend fun buildUserNameDirectories(taskId: Long): Pair<Map<Long, String>, Map<String, String>> {
        // Lazily hydrate /api/users if not loaded yet so non-assignee commenters still show real names.
        if (techniciansFlow.value.isEmpty()) {
            runCatching {
                val usersRes = apiServiceProvider().getUsersRaw()
                if (usersRes.isSuccessful) {
                    val raw = usersRes.body()?.string().orEmpty()
                    val parsed = CatalogResponseParser.parseUsersJson(raw)
                    if (parsed.isNotEmpty()) {
                        techniciansFlow.value = parsed
                    }
                }
            }
        }

        val byId = LinkedHashMap<Long, String>()
        val byUsername = LinkedHashMap<String, String>()

        techniciansFlow.value.forEach { tech ->
            if (tech.id > 0L && tech.resolvedName.isNotBlank()) {
                byId[tech.id] = tech.resolvedName
            }
            if (tech.username.isNotBlank() && tech.resolvedName.isNotBlank()) {
                byUsername[tech.username.trim().lowercase()] = tech.resolvedName
            }
        }

        taskDao.getTaskByIdOnce(taskId)?.assignees?.forEach { assignee ->
            if (assignee.id > 0L && assignee.name.isNotBlank() && !byId.containsKey(assignee.id)) {
                byId[assignee.id] = assignee.name
            }
        }

        // Cross-task assignee fallback (useful when offline or before /api/users succeeds)
        runCatching { taskDao.getAllTasks().first() }.getOrDefault(emptyList()).forEach { task ->
            task.assignees.forEach { assignee ->
                if (assignee.id > 0L && assignee.name.isNotBlank() && !byId.containsKey(assignee.id)) {
                    byId[assignee.id] = assignee.name
                }
            }
        }

        val session = runCatching { appPreferences?.sessionFlow?.first() }.getOrNull()
        if (session != null && session.userId > 0L && session.technicianName.isNotBlank()) {
            byId[session.userId] = session.technicianName
        }

        return byId to byUsername
    }

    private fun mergeAttachments(
        primary: List<TaskAttachment>,
        secondary: List<TaskAttachment>
    ): List<TaskAttachment> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val merged = LinkedHashMap<String, TaskAttachment>()
        for (item in primary + secondary) {
            val key = when {
                item.fileUrl.isNotBlank() -> "url:${item.fileUrl}"
                item.id > 0L -> "id:${item.id}"
                else -> "name:${item.fileName}:${item.createdAtMillis}"
            }
            if (!merged.containsKey(key)) {
                merged[key] = item
            }
        }
        return merged.values.sortedBy { it.createdAtMillis }
    }

    override suspend fun snoozeTask(
        taskId: Long,
        snoozeUntilMillis: Long,
        newEpochDay: Long?,
        newTimeMinutes: Int?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId)
            ?: return@withContext Result.failure(IllegalStateException("Tiket tidak ditemukan."))
        val now = System.currentTimeMillis()
        val updatedEpoch = newEpochDay ?: existing.scheduledDateEpochDay
        val updatedMinutes = newTimeMinutes ?: existing.scheduledTimeMinutes
        val updatedEntity = existing.copy(
            scheduledDateEpochDay = updatedEpoch,
            scheduledTimeMinutes = updatedMinutes,
            timeSlotLabel = DateEpochUtil.formatMinutesToTimeSlot(updatedMinutes),
            snoozedUntilMillis = snoozeUntilMillis,
            isSynced = false,
            updatedAtMillis = now
        )
        taskDao.upsertTask(updatedEntity)

        if (taskId > 0L) {
            tryRemote {
                val res = apiServiceProvider().patchTask(
                    id = taskId,
                    body = TaskPatchRequestDto(
                        scheduledDateEpochDay = updatedEpoch,
                        scheduledTimeMinutes = updatedMinutes,
                        snoozedUntilMillis = snoozeUntilMillis
                    )
                )
                if (res.isSuccessful) {
                    taskDao.markTaskSynced(taskId)
                    lastSyncErrorFlow.value = null
                } else {
                    lastSyncErrorFlow.value = formatHttpError(res, "PATCH snooze")
                }
            }
        }
        Result.success(Unit)
    }

    override suspend fun setTaskBlocked(
        taskId: Long,
        isBlocked: Boolean,
        reason: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskByIdOnce(taskId)
            ?: return@withContext Result.failure(IllegalStateException("Tiket tidak ditemukan."))
        val now = System.currentTimeMillis()
        val cleanReason = if (isBlocked) {
            reason?.trim()?.takeIf { it.isNotEmpty() } ?: "Menunggu vendor / sparepart"
        } else {
            null
        }
        val shouldPauseTimer = isBlocked && existing.isTimerRunning
        val updatedEntity = existing.copy(
            isBlocked = isBlocked,
            blockedReason = cleanReason,
            isTimerRunning = if (shouldPauseTimer) false else existing.isTimerRunning,
            timerStartedAtMillis = if (shouldPauseTimer) null else existing.timerStartedAtMillis,
            isSynced = false,
            updatedAtMillis = now
        )
        taskDao.upsertTask(updatedEntity)

        if (taskId > 0L) {
            tryRemote {
                val api = apiServiceProvider()
                if (shouldPauseTimer) {
                    runCatching {
                        api.updateTimer(
                            id = taskId,
                            body = TimerActionRequestDto(action = TimerActionRequestDto.ACTION_PAUSE)
                        )
                    }
                }
                val res = api.patchTask(
                    id = taskId,
                    body = TaskPatchRequestDto(
                        trackedSeconds = if (shouldPauseTimer) existing.trackedSeconds else null,
                        isBlocked = if (isBlocked) 1 else 0,
                        blockedReason = cleanReason.orEmpty()
                    )
                )
                if (res.isSuccessful) {
                    taskDao.markTaskSynced(taskId)
                    lastSyncErrorFlow.value = null
                } else {
                    lastSyncErrorFlow.value = formatHttpError(res, "PATCH blocked")
                }
            }
        }
        Result.success(Unit)
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
                val userDto = body.user
                // Soft-deactivation guard even if backend returns 200 with is_active = 0
                if (userDto != null && !userDto.isUserActive) {
                    return@withContext Result.failure(
                        AccountDeactivatedException(DEACTIVATED_ACCOUNT_MESSAGE)
                    )
                }
                lastSyncErrorFlow.value = null
                appPreferences?.saveAuthSession(
                    token = token,
                    technicianName = userDto?.resolvedName ?: cleanUsername.ifBlank { "Teknisi" },
                    role = userDto?.role ?: "technician",
                    userId = userDto?.id ?: 0L,
                    username = userDto?.resolvedUsername?.ifBlank { cleanUsername } ?: cleanUsername,
                    avatarUrl = userDto?.resolvedAvatarUrl,
                    department = userDto?.resolvedDepartment,
                    subDepartment = userDto?.resolvedSubDepartment,
                    isActive = userDto?.resolvedIsActive ?: 1
                )
                Result.success(token)
            } else {
                val rawError = readErrorBodySnippet(response)
                if (response.code() == 403) {
                    // Server returns HTTP 403 specifically when users.is_active == 0
                    val deactivatedMsg = if (isDeactivatedAccountErrorPayload(rawError) || rawError.isBlank()) {
                        DEACTIVATED_ACCOUNT_MESSAGE
                    } else {
                        extractReadableMessage(rawError).ifBlank { DEACTIVATED_ACCOUNT_MESSAGE }
                    }
                    return@withContext Result.failure(AccountDeactivatedException(deactivatedMsg))
                }
                val message = when (response.code()) {
                    401 -> "Username atau kata sandi salah (HTTP 401)."
                    else -> {
                        val parsed = extractReadableMessage(rawError)
                        if (parsed.isNotBlank()) {
                            "Login gagal (HTTP ${response.code()}): $parsed"
                        } else {
                            "Login gagal (HTTP ${response.code()}). Periksa koneksi ke ping.fjrx.my.id."
                        }
                    }
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
        lastSyncErrorFlow.value = null
        taskDao.deleteAllSyncedTasks()
    }

    override suspend fun refreshMyProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val response = apiServiceProvider().getMyProfile()
            if (response.code() == 403) {
                val rawError = readErrorBodySnippet(response)
                if (isDeactivatedAccountErrorPayload(rawError) || rawError.isBlank()) {
                    return@withContext Result.failure(AccountDeactivatedException(DEACTIVATED_ACCOUNT_MESSAGE))
                }
                return@withContext Result.failure(IllegalStateException(formatHttpError(response, "GET /api/auth/me", rawError)))
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException(formatHttpError(response, "GET /api/auth/me")))
            }
            val userDto = response.body()?.resolvedUser
                ?: return@withContext Result.failure(IllegalStateException("Respons profil tidak memuat data pengguna."))
            if (!userDto.isUserActive) {
                return@withContext Result.failure(AccountDeactivatedException(DEACTIVATED_ACCOUNT_MESSAGE))
            }
            val profile = userDto.toUserProfile()
            appPreferences?.updateUserProfile(
                userId = profile.id.takeIf { it > 0L },
                username = profile.username.takeIf { it.isNotBlank() },
                displayName = profile.displayName.takeIf { it.isNotBlank() },
                role = profile.role.takeIf { it.isNotBlank() },
                avatarUrl = profile.avatarUrl,
                clearAvatarIfNull = true,
                department = profile.department.orEmpty(),
                subDepartment = profile.subDepartment.orEmpty(),
                isActive = profile.isActive
            )
            Result.success(profile)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadMyAvatar(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedMime = AvatarUploadHelper.normalizeMimeType(mimeType).ifEmpty { "image/jpeg" }
            if (normalizedMime !in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Hanya file gambar (JPEG, PNG, WebP, GIF) yang diperbolehkan.")
                )
            }
            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("File gambar kosong."))
            }
            if (bytes.size > AvatarUploadHelper.MAX_FILE_SIZE_BYTES) {
                val sizeMb = String.format(java.util.Locale.US, "%.1f", bytes.size / (1024f * 1024f))
                return@withContext Result.failure(
                    IllegalArgumentException("Ukuran gambar ($sizeMb MB) melebihi batas maksimal 10 MB.")
                )
            }

            val userId = resolveCurrentUserIdForProfile()
                ?: return@withContext Result.failure(
                    IllegalStateException("ID pengguna tidak tersedia. Silakan login ulang.")
                )
            val safeFileName = fileName.trim().ifBlank {
                "avatar_${System.currentTimeMillis()}.${AvatarUploadHelper.extensionForMime(normalizedMime)}"
            }
            val part = MultipartBody.Part.createFormData(
                "file",
                safeFileName,
                bytes.toRequestBody(normalizedMime.toMediaType())
            )
            val response = apiServiceProvider().uploadUserAvatar(userId = userId, file = part)
            if (response.code() == 403) {
                val rawError = readErrorBodySnippet(response)
                if (isDeactivatedAccountErrorPayload(rawError)) {
                    return@withContext Result.failure(AccountDeactivatedException(DEACTIVATED_ACCOUNT_MESSAGE))
                }
                return@withContext Result.failure(IllegalStateException(formatHttpError(response, "POST avatar", rawError)))
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException(formatHttpError(response, "POST avatar")))
            }
            val body = response.body()
            val newAvatarUrl = body?.avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
            if (newAvatarUrl != null) {
                appPreferences?.updateAvatarUrl(newAvatarUrl)
                runCatching { refreshCatalogsFromServer() }
                Result.success(newAvatarUrl)
            } else {
                // Fallback: re-fetch /api/auth/me if server omitted avatar_url in 200 payload
                val refreshed = refreshMyProfile().getOrNull()?.avatarUrl
                if (!refreshed.isNullOrBlank()) {
                    Result.success(refreshed)
                } else {
                    Result.failure(IllegalStateException("Server tidak mengembalikan URL avatar baru."))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMyAvatar(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = resolveCurrentUserIdForProfile()
                ?: return@withContext Result.failure(
                    IllegalStateException("ID pengguna tidak tersedia. Silakan login ulang.")
                )
            val response = apiServiceProvider().deleteUserAvatar(userId = userId)
            if (response.code() == 403) {
                val rawError = readErrorBodySnippet(response)
                if (isDeactivatedAccountErrorPayload(rawError)) {
                    return@withContext Result.failure(AccountDeactivatedException(DEACTIVATED_ACCOUNT_MESSAGE))
                }
                return@withContext Result.failure(IllegalStateException(formatHttpError(response, "DELETE avatar", rawError)))
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException(formatHttpError(response, "DELETE avatar")))
            }
            appPreferences?.updateAvatarUrl(null)
            runCatching { refreshCatalogsFromServer() }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMyProfile(
        displayName: String?,
        password: String?,
        department: String?,
        subDepartment: String?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val userId = resolveCurrentUserIdForProfile()
                ?: return@withContext Result.failure(
                    IllegalStateException("ID pengguna tidak tersedia. Silakan login ulang.")
                )
            val cleanDisplayName = displayName?.trim()?.takeIf { it.isNotEmpty() }
            val cleanPassword = password?.takeIf { it.isNotEmpty() }
            val cleanDept = department?.trim()
            val cleanSubDept = subDepartment?.trim()
            if (cleanPassword != null && cleanPassword.length < 4) {
                return@withContext Result.failure(
                    IllegalArgumentException("Kata sandi baru minimal 4 karakter.")
                )
            }
            val request = UpdateUserRequestDto(
                displayName = cleanDisplayName,
                password = cleanPassword,
                department = cleanDept,
                subDepartment = cleanSubDept
            )
            val response = apiServiceProvider().updateUserProfile(userId = userId, request = request)
            if (response.code() == 403) {
                val rawError = readErrorBodySnippet(response)
                if (isDeactivatedAccountErrorPayload(rawError)) {
                    return@withContext Result.failure(AccountDeactivatedException(DEACTIVATED_ACCOUNT_MESSAGE))
                }
                return@withContext Result.failure(IllegalStateException(formatHttpError(response, "PATCH profile", rawError)))
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException(formatHttpError(response, "PATCH profile")))
            }
            val returnedUser = response.body()?.user?.toUserProfile()
            if (returnedUser != null) {
                appPreferences?.updateUserProfile(
                    userId = returnedUser.id.takeIf { it > 0L },
                    username = returnedUser.username.takeIf { it.isNotBlank() },
                    displayName = returnedUser.displayName.takeIf { it.isNotBlank() },
                    role = returnedUser.role.takeIf { it.isNotBlank() },
                    avatarUrl = returnedUser.avatarUrl,
                    clearAvatarIfNull = true,
                    department = returnedUser.department.orEmpty(),
                    subDepartment = returnedUser.subDepartment.orEmpty(),
                    isActive = returnedUser.isActive
                )
                runCatching { refreshCatalogsFromServer() }
                Result.success(returnedUser)
            } else {
                appPreferences?.updateUserProfile(
                    displayName = cleanDisplayName,
                    department = cleanDept,
                    subDepartment = cleanSubDept
                )
                val fallbackPrefs = runCatching { appPreferences?.sessionFlow?.first() }.getOrNull()
                Result.success(
                    UserProfile(
                        id = userId,
                        username = fallbackPrefs?.technicianUsername.orEmpty(),
                        displayName = cleanDisplayName ?: fallbackPrefs?.technicianName.orEmpty().ifBlank { "Teknisi" },
                        role = fallbackPrefs?.technicianRole.orEmpty().ifBlank { "technician" },
                        avatarUrl = fallbackPrefs?.avatarUrl,
                        department = cleanDept ?: fallbackPrefs?.department,
                        subDepartment = cleanSubDept ?: fallbackPrefs?.subDepartment,
                        isActive = fallbackPrefs?.isActive ?: 1
                    )
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveCurrentUserIdForProfile(): Long? {
        val cached = appPreferences?.getUserId()?.takeIf { it > 0L }
        if (cached != null) return cached
        val fromStore = runCatching { appPreferences?.sessionFlow?.first()?.userId }
            .getOrNull()
            ?.takeIf { it > 0L }
        if (fromStore != null) return fromStore
        return refreshMyProfile().getOrNull()?.id?.takeIf { it > 0L }
    }

    private suspend fun generateCollisionFreeTicketCode(
        category: TicketCategory,
        nowMillis: Long
    ): String {
        val existingTasks = runCatching { taskDao.getAllTasks().first() }.getOrDefault(emptyList())
        val prefix = "${category.code}-"
        val existingCodes = existingTasks.map { it.ticketCode.uppercase() }.toSet()
        val maxCategoryNumber = existingTasks.mapNotNull { task ->
            if (task.ticketCode.startsWith(prefix, ignoreCase = true)) {
                task.ticketCode.substring(prefix.length).toIntOrNull()
            } else {
                null
            }
        }.maxOrNull() ?: 0

        var candidateSeq = maxOf(maxCategoryNumber + 1, existingTasks.size + 1)
        var candidate = "$prefix$candidateSeq"
        while (candidate.uppercase() in existingCodes) {
            candidateSeq++
            candidate = "$prefix$candidateSeq"
        }

        // If local cache is empty (e.g., first offline ticket before initial pull), avoid colliding with NET-1
        if (existingTasks.isEmpty()) {
            val shortUnique = ((nowMillis / 1000L) % 90_000L) + 10_000L
            return "$prefix$shortUnique"
        }
        return candidate
    }

    private suspend fun uploadNewTaskToServer(
        api: PingDuluApiService,
        entity: TaskEntity,
        explicitLabelIds: List<Long> = emptyList()
    ): CreateUploadOutcome {
        return try {
            // Ensure legacy pre-v4 pending local rows also carry a stable UUID idempotency key before upload
            val stableSyncId = entity.clientSyncId?.trim()?.takeIf { it.isNotEmpty() }
                ?: UUID.randomUUID().toString()
            var activeEntity = if (entity.clientSyncId == stableSyncId) {
                entity
            } else {
                entity.copy(clientSyncId = stableSyncId).also { taskDao.upsertTask(it) }
            }

            var response = api.createTask(buildCreateRequest(activeEntity, explicitLabelIds))

            // Distinguish HTTP 409 CLAIM_LIMIT_EXCEEDED (5 active task WIP limit) from ticket_code collision!
            if (!response.isSuccessful) {
                val rawError = readErrorBodySnippet(response)
                if (response.code() == 409 && ActiveTaskLimitPolicy.isClaimLimitExceededPayload(rawError)) {
                    val isDelegation = entity.assignees.any { assignee ->
                        assignee.id > 0L && assignee.id != entity.createdById
                    } || entity.creatorRole.equals("admin", ignoreCase = true)
                    val limitEx = ActiveTaskLimitPolicy.parseServerLimitException(
                        rawBody = rawError,
                        isDelegationContext = isDelegation
                    )
                    lastSyncErrorFlow.value = limitEx.serverMessage
                    return CreateUploadOutcome.LimitExceeded(limitEx)
                }

                val looksLikeTicketCodeCollision = (response.code() == 409 &&
                    !ActiveTaskLimitPolicy.isClaimLimitExceededPayload(rawError)) ||
                    rawError.contains("unique", ignoreCase = true) ||
                    rawError.contains("duplicate", ignoreCase = true) ||
                    rawError.contains("ticket_code", ignoreCase = true)

                if (looksLikeTicketCodeCollision) {
                    val uniqueSuffix = ((System.currentTimeMillis() / 100L) % 900_000L) + 100_000L
                    val retriedCode = "${entity.category}-$uniqueSuffix"
                    activeEntity = activeEntity.copy(ticketCode = retriedCode)
                    taskDao.upsertTask(activeEntity)
                    response = api.createTask(buildCreateRequest(activeEntity, explicitLabelIds))
                } else {
                    val reason = formatHttpError(response, "POST /api/tasks", rawError)
                    lastSyncErrorFlow.value = reason
                    return CreateUploadOutcome.Failed(reason)
                }
            }

            if (!response.isSuccessful) {
                val retryRawError = readErrorBodySnippet(response)
                if (response.code() == 409 && ActiveTaskLimitPolicy.isClaimLimitExceededPayload(retryRawError)) {
                    val isDelegation = entity.assignees.any { assignee ->
                        assignee.id > 0L && assignee.id != entity.createdById
                    } || entity.creatorRole.equals("admin", ignoreCase = true)
                    val limitEx = ActiveTaskLimitPolicy.parseServerLimitException(
                        rawBody = retryRawError,
                        isDelegationContext = isDelegation
                    )
                    lastSyncErrorFlow.value = limitEx.serverMessage
                    return CreateUploadOutcome.LimitExceeded(limitEx)
                }
                val reason = formatHttpError(response, "POST /api/tasks", retryRawError)
                lastSyncErrorFlow.value = reason
                return CreateUploadOutcome.Failed(reason)
            }

            val now = System.currentTimeMillis()
            val responseBody = response.body()
            if (responseBody?.success == false) {
                val reason = responseBody.message?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "Server menolak penyimpanan tiket (success=false)."
                lastSyncErrorFlow.value = reason
                return CreateUploadOutcome.Failed(reason)
            }
            val resolvedEntity = responseBody?.resolveCreatedEntity(
                fallbackLocal = activeEntity,
                nowMillis = now
            )

            if (resolvedEntity != null && resolvedEntity.id > 0L) {
                val finalized = resolvedEntity.copy(
                    isSynced = true,
                    clientSyncId = stableSyncId,
                    checklist = resolvedEntity.checklist.ifEmpty { activeEntity.checklist },
                    assignees = resolvedEntity.assignees.ifEmpty { activeEntity.assignees },
                    labels = resolvedEntity.labels.ifEmpty { activeEntity.labels },
                    isRecurring = resolvedEntity.isRecurring || activeEntity.isRecurring,
                    recurrenceInterval = resolvedEntity.recurrenceInterval ?: activeEntity.recurrenceInterval
                )
                runInTransactionIfAvailable {
                    taskDao.deleteTaskById(activeEntity.id)
                    taskDao.deletePendingByClientSyncId(stableSyncId)
                    taskDao.upsertTask(finalized)
                }
                recentlyMutatedServerTaskIds[finalized.id] = now
                appPreferences?.updateLastSyncedAt(now)
                lastSyncErrorFlow.value = null
                return CreateUploadOutcome.Synced(finalized)
            }

            // Fallback for 200/201 Created responses that returned `{}` without an ID:
            // Pull authoritative task list from server and ONLY remove the temporary negative-ID row
            // AFTER confirming the newly created positive-ID task actually exists on the server.
            val refreshRes = api.getTasks()
            if (refreshRes.isSuccessful) {
                val remoteEntities = refreshRes.body()?.resolvedTasks().orEmpty()
                    .filter { it.id > 0L }
                    .map { it.toEntity(nowMillis = now) }

                val matched = remoteEntities.firstOrNull {
                    it.clientSyncId.equals(stableSyncId, ignoreCase = true)
                } ?: remoteEntities.firstOrNull {
                    it.ticketCode.equals(activeEntity.ticketCode, ignoreCase = true)
                } ?: remoteEntities.firstOrNull {
                    it.title.equals(activeEntity.title, ignoreCase = true)
                }

                if (matched != null) {
                    runInTransactionIfAvailable {
                        taskDao.deleteTaskById(activeEntity.id)
                        taskDao.deletePendingByClientSyncId(stableSyncId)
                        taskDao.upsertTasks(remoteEntities)
                    }
                    appPreferences?.updateLastSyncedAt(now)
                    lastSyncErrorFlow.value = null
                    CreateUploadOutcome.Synced(matched)
                } else {
                    // Never delete local pending task when server confirmation is absent
                    if (remoteEntities.isNotEmpty()) {
                        taskDao.upsertTasks(remoteEntities)
                    }
                    val reason = "Server belum mengembalikan tiket baru; data tetap disimpan aman secara lokal."
                    lastSyncErrorFlow.value = reason
                    CreateUploadOutcome.Failed(reason)
                }
            } else {
                val reason = formatHttpError(refreshRes, "GET /api/tasks setelah create")
                lastSyncErrorFlow.value = reason
                CreateUploadOutcome.Failed(reason)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val reason = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Gagal mengirim tiket ke server PingDulu"
            lastSyncErrorFlow.value = reason
            CreateUploadOutcome.Failed(reason)
        }
    }

    private fun buildCreateRequest(
        entity: TaskEntity,
        explicitLabelIds: List<Long> = emptyList()
    ): CreateTaskRequestDto {
        val resolvedAssigneeIds = entity.assignees
            .map { it.id }
            .filter { it > 0L }
            .distinct()
            .ifEmpty { listOfNotNull(entity.assignedToId?.takeIf { it > 0L }) }

        val labelIdByName = officialLabelsFlow.value
            .filter { it.id > 0L }
            .associate { it.name.trim().lowercase() to it.id }
        val resolvedLabelIds = (
            explicitLabelIds.filter { it > 0L } +
                entity.labels.mapNotNull { labelIdByName[it.trim().lowercase()] }
            ).distinct()

        val resolvedChecklistLabels = entity.checklist
            .map { it.label.trim() }
            .filter { it.isNotEmpty() }

        return CreateTaskRequestDto(
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
            assignedToId = resolvedAssigneeIds.firstOrNull(),
            assigneeIds = resolvedAssigneeIds.takeIf { it.isNotEmpty() },
            labels = entity.labels.takeIf { it.isNotEmpty() },
            labelIds = resolvedLabelIds.takeIf { it.isNotEmpty() },
            checklist = resolvedChecklistLabels.takeIf { it.isNotEmpty() },
            isRecurring = if (entity.isRecurring) 1 else 0,
            recurrenceInterval = if (entity.isRecurring) entity.recurrenceInterval else null,
            createdAt = entity.createdAtMillis.takeIf { it > 0L },
            clientSyncId = entity.clientSyncId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private suspend fun pushLocalEntityToServer(
        api: PingDuluApiService,
        entity: TaskEntity
    ): PushOutcome {
        return try {
            if (entity.id < 0L) {
                when (val outcome = uploadNewTaskToServer(api, entity)) {
                    is CreateUploadOutcome.Synced -> PushOutcome.Success
                    is CreateUploadOutcome.LimitExceeded -> {
                        // NEVER silently delete an offline-created task during background sync!
                        // Keep it safely in Room ("Antre Sync") until the technician completes 1 active task.
                        PushOutcome.Failure(outcome.exception.serverMessage)
                    }
                    is CreateUploadOutcome.Failed -> PushOutcome.Failure(outcome.reason)
                }
            } else {
                val stageRes = api.updateStage(entity.id, StageUpdateDto(stage = entity.stage))
                if (!stageRes.isSuccessful) {
                    return PushOutcome.Failure(formatHttpError(stageRes, "POST stage #${entity.id}"))
                }
                if (entity.isTimerRunning || entity.trackedSeconds > 0L) {
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
                    if (!timerRes.isSuccessful) {
                        return PushOutcome.Failure(formatHttpError(timerRes, "POST timer #${entity.id}"))
                    }
                }
                for (item in entity.checklist) {
                    if (item.id <= 0L) continue
                    val stepRes = api.toggleChecklistStep(
                        id = entity.id,
                        stepId = item.id,
                        body = ChecklistStepToggleDto(isDone = if (item.isDone) 1 else 0)
                    )
                    // HTTP 404 means this checklist step was deleted/replaced on server; skip so
                    // it never permanently traps the task in the "Antre Sync" pending queue.
                    if (!stepRes.isSuccessful && stepRes.code() != 404) {
                        return PushOutcome.Failure(
                            formatHttpError(stepRes, "PATCH checklist #${entity.id}/${item.id}")
                        )
                    }
                }
                if (entity.isBlocked || entity.snoozedUntilMillis != null || (!entity.isTimerRunning && entity.trackedSeconds > 0L)) {
                    runCatching {
                        api.patchTask(
                            id = entity.id,
                            body = TaskPatchRequestDto(
                                trackedSeconds = entity.trackedSeconds.takeIf { !entity.isTimerRunning && it > 0L },
                                scheduledDateEpochDay = entity.scheduledDateEpochDay,
                                scheduledTimeMinutes = entity.scheduledTimeMinutes,
                                snoozedUntilMillis = entity.snoozedUntilMillis,
                                isBlocked = if (entity.isBlocked) 1 else 0,
                                blockedReason = entity.blockedReason.orEmpty()
                            )
                        )
                    }
                }
                PushOutcome.Success
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            PushOutcome.Failure(e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Koneksi terputus")
        }
    }

    private suspend fun runInTransactionIfAvailable(block: suspend () -> Unit) {
        val db = database
        if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }

    private fun readErrorBodySnippet(response: Response<*>): String {
        return runCatching {
            response.errorBody()?.string().orEmpty().trim()
        }.getOrDefault("")
    }

    private fun formatHttpError(
        response: Response<*>,
        operation: String,
        preReadBody: String? = null
    ): String {
        val raw = (preReadBody ?: readErrorBodySnippet(response))
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
        val concise = extractReadableMessage(raw).take(110)
        return if (concise.isNotBlank()) {
            "HTTP ${response.code()} ($operation): $concise"
        } else {
            "HTTP ${response.code()} ($operation)"
        }
    }

    private fun extractPolicyErrorMessage(response: Response<*>): String {
        val raw = readErrorBodySnippet(response)
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
        val parsed = extractReadableMessage(raw)
        return parsed.ifBlank {
            "Anda tidak memiliki izin untuk melakukan tindakan ini pada tiket ini (HTTP ${response.code()})."
        }
    }

    private fun extractReadableMessage(raw: String): String {
        if (raw.isBlank()) return ""
        // Prioritize human-readable "message" / "detail" over machine error codes like "POLICY_FORBIDDEN"
        val prioritizedKeys = listOf("message", "detail", "error")
        for (key in prioritizedKeys) {
            val regex = Regex("""\"$key\"\s*:\s*\"([^\"]+)\"""")
            val match = regex.find(raw)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotEmpty()) return candidate
            }
        }
        return raw.take(110)
    }

    private suspend inline fun tryRemote(block: () -> Unit) {
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            // Offline-first: capture concise diagnostic while Room retains isSynced = false for auto-retry.
            lastSyncErrorFlow.value = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Sedang offline — antre sinkronisasi otomatis"
        }
    }
}
