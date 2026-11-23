package com.example

import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.local.toDomain
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.dto.AddChecklistStepRequestDto
import com.example.data.remote.dto.AddCommentRequestDto
import com.example.data.remote.dto.CalendarAgendaResponseDto
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.ClaimTaskResponseDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.HolidayResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.SpeedTestRecordRequestDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDto
import com.example.data.remote.dto.TaskPatchRequestDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.remote.dto.WorkTaskResponseDto
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.ActiveTaskLimitPolicy
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.presentation.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Isolated Zero-Data-Loss & Anti-Vanishing Task Card Audit Suite.
 * Uses strictly in-memory fakes (never touches or deletes real tasks).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskCardAntiVanishingAuditTest {

    private val todayEpochDay = DateEpochUtil.toEpochDay(2026, 11, 22)

    private fun sampleEntity(
        id: Long,
        epochDay: Long = todayEpochDay,
        stage: Stage = Stage.BACKLOG,
        isSynced: Boolean = true,
        updatedAtMillis: Long = 1_700_000_000_000L
    ): TaskEntity = TaskEntity(
        id = id,
        ticketCode = "NET-$id",
        title = "Tugas Penting #$id",
        category = "NET",
        scheduledDateEpochDay = epochDay,
        scheduledTimeMinutes = 540,
        timeSlotLabel = "09:00 - 10:30",
        priority = Priority.P2_HIGH.apiValue,
        stage = stage.apiValue,
        technicalNotes = "Catatan teknis #$id",
        trackedSeconds = 0L,
        isTimerRunning = false,
        timerStartedAtMillis = null,
        location = "Site #$id",
        slaTargetTime = "",
        checklist = listOf(ChecklistItem(id = id * 10 + 1, label = "Langkah 1", isDone = false)),
        assignees = listOf(Assignee(id = 10L, name = "Fajar", role = "admin", isLead = true)),
        isSynced = isSynced,
        createdAtMillis = 1_700_000_000_000L,
        dueDateMillis = null,
        assignedToId = 10L,
        labels = listOf("Jaringan"),
        updatedAtMillis = updatedAtMillis
    )

    @Test
    fun syncGuards_neverWipeTasksOnEmptyMalformedPartialOrSsePullResponses() = runBlocking {
        val initial = (1L..6L).map { id -> sampleEntity(id = id) }
        val dao = InMemoryAntiVanishDao(initial)
        val api = AntiVanishFakeApiService()
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        // 1. Malformed HTTP 200 `{}` (neither data nor tasks array) -> MUST return OfflineFallback and preserve ALL 6 tasks
        api.tasksResponse = Response.success(TaskResponseDto(data = null, tasks = null))
        val malformedRes = repo.refreshTasksFromServer()
        assertTrue(malformedRes is SyncResult.OfflineFallback)
        assertEquals(6, dao.countAll())

        // 2. Empty HTTP 200 `{"tasks": []}` -> MUST NOT wipe local tasks!
        api.tasksResponse = Response.success(TaskResponseDto(tasks = emptyList()))
        val emptyRes = repo.refreshTasksFromServer()
        assertTrue(emptyRes is SyncResult.Success)
        assertEquals(6, dao.countAll())

        // 3. Background SSE / polling pull returning partial subset (only id=1) -> NEVER deletes 2..6
        api.tasksResponse = Response.success(
            TaskResponseDto(
                tasks = listOf(
                    TaskDto(id = 1L, ticketCode = "NET-1", title = "Updated #1", stage = "IN_PROGRESS")
                )
            )
        )
        val ssePullRes = repo.pullTasksFromServerOnly()
        assertTrue(ssePullRes is SyncResult.Success)
        assertEquals(6, dao.countAll())
        assertEquals("Updated #1", dao.getTaskByIdOnce(1L)?.title)

        // 4. Explicit refresh returning suspicious mass drop (2 out of 6 = 67% drop) -> anti-mass-wipe blocks deletion!
        api.tasksResponse = Response.success(
            TaskResponseDto(
                tasks = listOf(
                    TaskDto(id = 1L, ticketCode = "NET-1", title = "Keep #1", stage = "IN_PROGRESS"),
                    TaskDto(id = 2L, ticketCode = "NET-2", title = "Keep #2", stage = "BACKLOG")
                )
            )
        )
        val partialFullSync = repo.refreshTasksFromServer()
        assertTrue(partialFullSync is SyncResult.Success)
        assertEquals("Suspicious mass drop must NEVER wipe local task cards", 6, dao.countAll())
    }

    @Test
    fun partialServerDtos_preserveLocalScheduleDateAndNeverDeleteUnconfirmedOrOfflineTasks() = runBlocking {
        val tomorrowEpoch = todayEpochDay + 1L
        val existing = sampleEntity(id = 42L, epochDay = tomorrowEpoch, stage = Stage.BACKLOG)
        val offlinePending = sampleEntity(id = -9999L, epochDay = todayEpochDay, isSynced = false)
        val dao = InMemoryAntiVanishDao(listOf(existing, offlinePending))
        val api = AntiVanishFakeApiService()
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        // 1. workTask receives partial DTO with scheduledDateEpochDay = null -> MUST keep tomorrowEpoch!
        val workRes = repo.workTask(42L)
        assertTrue(workRes.isSuccess)
        val afterWork = dao.getTaskByIdOnce(42L)
        assertNotNull(afterWork)
        assertEquals(tomorrowEpoch, afterWork?.scheduledDateEpochDay)
        assertEquals(540, afterWork?.scheduledTimeMinutes)
        assertEquals("Site #42", afterWork?.location)

        // 2. claimTask receives partial DTO with scheduledDateEpochDay = null -> MUST keep tomorrowEpoch!
        val claimRes = repo.claimTask(42L)
        assertTrue(claimRes.isSuccess)
        assertEquals(tomorrowEpoch, dao.getTaskByIdOnce(42L)?.scheduledDateEpochDay)

        // 3. Background refresh pushes offlinePending (-9999L) and gets 409 CLAIM_LIMIT_EXCEEDED ->
        //    MUST NOT silently delete the user's offline task from Room!
        api.createReturn409Limit = true
        api.tasksResponse = Response.success(
            TaskResponseDto(
                tasks = listOf(
                    TaskDto(id = 42L, ticketCode = "NET-42", title = "Tugas Penting #42", stage = "IN_PROGRESS")
                )
            )
        )
        repo.refreshTasksFromServer()
        assertNotNull(
            "Offline task must remain safely in Room when background push hits 409 limit",
            dao.getTaskByIdOnce(-9999L)
        )

        // 4. createQuickTask when server returns empty 201 `{}` and GET /api/tasks doesn't have the new task yet ->
        //    MUST keep pending local task intact instead of deleting it!
        api.createReturn409Limit = false
        api.createReturnEmpty201 = true
        val created = repo.createQuickTask(
            title = "Tiket Penting Tidak Boleh Hilang",
            category = TicketCategory.NET,
            priority = Priority.P1_CRITICAL,
            epochDay = todayEpochDay,
            location = "POP Pusat"
        )
        assertTrue("Unconfirmed task must remain locally with negative ID until confirmed", created.id < 0L)
        assertNotNull(dao.getTaskByIdOnce(created.id))
    }

    @Test
    fun agendaViewModel_carriesOverUnfinishedPastTasksIntoTodaySoCardsNeverVanishAtMidnight() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                val yesterdayBacklog = sampleEntity(
                    id = 101L,
                    epochDay = todayEpochDay - 1L,
                    stage = Stage.BACKLOG
                ).toDomain()
                val twoDaysAgoInProgress = sampleEntity(
                    id = 102L,
                    epochDay = todayEpochDay - 2L,
                    stage = Stage.IN_PROGRESS
                ).toDomain()
                val yesterdayDone = sampleEntity(
                    id = 103L,
                    epochDay = todayEpochDay - 1L,
                    stage = Stage.DONE
                ).toDomain()
                val todayBacklog = sampleEntity(
                    id = 104L,
                    epochDay = todayEpochDay,
                    stage = Stage.BACKLOG
                ).toDomain()

                val fakeRepo = AntiVanishViewModelFakeRepo(
                    listOf(yesterdayBacklog, twoDaysAgoInProgress, yesterdayDone, todayBacklog)
                )
                val vm = ScheduleViewModel(
                    repository = fakeRepo,
                    baseEpochDayProvider = { todayEpochDay },
                    hourOfDayProvider = { 9 },
                    enableBackgroundTicker = false,
                    enableSseStream = false
                )

                val collector = backgroundScope.launch { vm.uiState.collect() }
                advanceUntilIdle()

                val state = vm.uiState.value
                val visibleTodayIds = state.filteredTasks.map { it.id }.toSet()

                // Unfinished tasks from yesterday & 2 days ago MUST remain visible on Today's Agenda!
                assertTrue("Yesterday BACKLOG must stay visible today", 101L in visibleTodayIds)
                assertTrue("2-days-ago IN_PROGRESS must stay visible today", 102L in visibleTodayIds)
                assertTrue("Today BACKLOG must stay visible today", 104L in visibleTodayIds)
                // Completed past task does not clutter Today (still visible in Kanban/Calendar/SPK)
                assertTrue("Completed yesterday task stays out of today's active carry-over", 103L !in visibleTodayIds)
                assertEquals(3, state.scheduleDays.first().taskCount)

                vm.stopBackgroundJobs()
                collector.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class InMemoryAntiVanishDao(initial: List<TaskEntity>) : TaskDao {
    private val map = LinkedHashMap<Long, TaskEntity>().apply {
        initial.forEach { put(it.id, it) }
    }
    private val flow = MutableStateFlow(map.values.toList())

    fun countAll(): Int = map.size
    private fun emit() { flow.value = map.values.toList() }

    override fun getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>> =
        flow.map { list -> list.filter { it.scheduledDateEpochDay == epochDay } }
    override fun getTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>> =
        flow.map { list -> list.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }
    override fun getAllTasks(): Flow<List<TaskEntity>> = flow
    override fun getActiveHeroTask(): Flow<TaskEntity?> =
        flow.map { list -> list.firstOrNull { it.stage == Stage.IN_PROGRESS.apiValue } }
    override fun getTaskById(taskId: Long): Flow<TaskEntity?> =
        flow.map { list -> list.firstOrNull { it.id == taskId } }
    override suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? = map[taskId]
    override suspend fun getTaskByClientSyncId(clientSyncId: String): TaskEntity? =
        map.values.firstOrNull { it.clientSyncId == clientSyncId }
    override suspend fun getUnsyncedTasks(): List<TaskEntity> = map.values.filter { !it.isSynced }
    override fun getUnsyncedCount(): Flow<Int> = flow.map { list -> list.count { !it.isSynced } }
    override suspend fun countTasks(): Int = map.size
    override suspend fun upsertTasks(tasks: List<TaskEntity>) { tasks.forEach { map[it.id] = it }; emit() }
    override suspend fun upsertTask(task: TaskEntity) { map[task.id] = task; emit() }
    override suspend fun updateChecklist(taskId: Long, checklistJson: String, isSynced: Boolean, updatedAtMillis: Long) = Unit
    override suspend fun updateTaskTimer(taskId: Long, seconds: Long, isRunning: Boolean, timerStartedAtMillis: Long?, isSynced: Boolean, updatedAtMillis: Long) = Unit
    override suspend fun pauseAllOtherTimers(excludeTaskId: Long, updatedAtMillis: Long) = Unit
    override suspend fun updateTaskStage(taskId: Long, stage: String, isRunning: Boolean, timerStartedAtMillis: Long?, isSynced: Boolean, updatedAtMillis: Long) = Unit
    override suspend fun markTaskSynced(taskId: Long) { map[taskId]?.let { map[taskId] = it.copy(isSynced = true) }; emit() }
    override suspend fun deleteTaskById(taskId: Long) { map.remove(taskId); emit() }
    override suspend fun deletePendingByClientSyncId(clientSyncId: String) {
        map.entries.removeAll { it.key < 0L && it.value.clientSyncId == clientSyncId }
        emit()
    }
    override suspend fun deleteStaleSyncedTasks(activeIds: List<Long>) {
        val keep = activeIds.toSet()
        map.entries.removeAll { it.value.isSynced && it.key !in keep }
        emit()
    }
    override suspend fun deleteAllSyncedTasks() {
        map.entries.removeAll { it.value.isSynced }
        emit()
    }
}

private class AntiVanishFakeApiService : PingDuluApiService {
    var tasksResponse: Response<TaskResponseDto> = Response.success(TaskResponseDto(tasks = emptyList()))
    var createReturn409Limit: Boolean = false
    var createReturnEmpty201: Boolean = false
    private val jsonMedia = "application/json".toMediaType()

    override suspend fun getTasks(): Response<TaskResponseDto> = tasksResponse

    override suspend fun workTask(taskId: Long): Response<WorkTaskResponseDto> =
        Response.success(
            WorkTaskResponseDto(
                success = true,
                stage = Stage.IN_PROGRESS.apiValue,
                task = TaskDto(
                    id = taskId,
                    ticketCode = "NET-$taskId",
                    title = "Tugas Penting #$taskId",
                    stage = Stage.IN_PROGRESS.apiValue,
                    scheduledDateEpochDay = null, // Partial server response omitting schedule date
                    location = "",
                    checklist = emptyList()
                )
            )
        )

    override suspend fun claimTask(taskId: Long): Response<ClaimTaskResponseDto> =
        Response.success(
            ClaimTaskResponseDto(
                success = true,
                task = TaskDto(
                    id = taskId,
                    ticketCode = "NET-$taskId",
                    title = "Tugas Penting #$taskId",
                    stage = Stage.BACKLOG.apiValue,
                    scheduledDateEpochDay = null,
                    checklist = emptyList()
                )
            )
        )

    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> {
        if (createReturn409Limit) {
            val errJson = """{"error":"${ActiveTaskLimitPolicy.SELF_CLAIM_LIMIT_MESSAGE}","limit":5,"active_count":5,"type":"CLAIM_LIMIT_EXCEEDED"}"""
            return Response.error(409, errJson.toResponseBody(jsonMedia))
        }
        if (createReturnEmpty201) {
            return Response.success(201, CreateTaskResponseDto())
        }
        return Response.success(CreateTaskResponseDto(success = true, id = 888L))
    }

    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> = error("unused")
    override suspend fun getUsersRaw(): Response<ResponseBody> = error("unused")
    override suspend fun getLabelsRaw(): Response<ResponseBody> = error("unused")
    override suspend fun getTaskDetailRaw(taskId: Long): Response<ResponseBody> = error("unused")
    override suspend fun patchTask(id: Long, body: TaskPatchRequestDto): Response<ResponseBody> = Response.success("""{"success":true}""".toResponseBody(jsonMedia))
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = Response.success(Unit)
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = Response.success(Unit)
    override suspend fun addChecklistStep(taskId: Long, body: AddChecklistStepRequestDto): Response<ResponseBody> = error("unused")
    override suspend fun toggleChecklistStep(id: Long, stepId: Long, body: ChecklistStepToggleDto): Response<Unit> = Response.success(Unit)
    override suspend fun getAttachmentsRaw(taskId: Long): Response<ResponseBody> = error("unused")
    override suspend fun uploadAttachment(taskId: Long, file: MultipartBody.Part): Response<ResponseBody> = error("unused")
    override suspend fun getCommentsRaw(taskId: Long): Response<ResponseBody> = error("unused")
    override suspend fun postComment(taskId: Long, body: AddCommentRequestDto): Response<ResponseBody> = error("unused")
    override suspend fun getActivitiesRaw(taskId: Long): Response<ResponseBody> = error("unused")
    override suspend fun getActivitySingularRaw(taskId: Long): Response<ResponseBody> = error("unused")
    override suspend fun getTaskLogsRaw(taskId: Long): Response<ResponseBody> = error("unused")
    override suspend fun deleteTask(taskId: Long): Response<ResponseBody> = error("unused")
    override suspend fun getHolidays(year: Int): Response<HolidayResponseDto> = error("unused")
    override suspend fun getCalendar(year: Int, month: Int): Response<CalendarAgendaResponseDto> = error("unused")
    override suspend fun postSpeedTestRecord(body: SpeedTestRecordRequestDto): Response<ResponseBody> = error("unused")
    override suspend fun getSpeedTestRecordsRaw(): Response<ResponseBody> = error("unused")
    override suspend fun getMyProfile(): Response<com.example.data.remote.dto.AuthMeResponseDto> = error("unused")
    override suspend fun uploadUserAvatar(userId: Long, file: MultipartBody.Part): Response<com.example.data.remote.dto.AvatarUploadResponseDto> = error("unused")
    override suspend fun deleteUserAvatar(userId: Long): Response<com.example.data.remote.dto.UserMutationResponseDto> = error("unused")
    override suspend fun updateUserProfile(userId: Long, request: com.example.data.remote.dto.UpdateUserRequestDto): Response<com.example.data.remote.dto.UserMutationResponseDto> = error("unused")
}

private class AntiVanishViewModelFakeRepo(initial: List<Task>) : TaskRepository {
    private val tasksFlow = MutableStateFlow(initial)
    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay == epochDay } }
    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }
    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow
    override fun observeActiveHeroTask(): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.stage == Stage.IN_PROGRESS } }
    override fun observeTaskById(taskId: Long): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.id == taskId } }
    override fun observeUnsyncedCount(): Flow<Int> = MutableStateFlow(0)
    override suspend fun initializeOfflineCache() = Unit
    override suspend fun refreshTasksFromServer(): SyncResult = SyncResult.Success(0, 0)
    override suspend fun toggleChecklistItem(taskId: Long, checklistId: Long) = Unit
    override suspend fun toggleTaskTimer(taskId: Long) = Unit
    override suspend fun persistTimerTick(taskId: Long, newTrackedSeconds: Long) = Unit
    override suspend fun updateTaskStage(taskId: Long, newStage: Stage) = Unit
    override suspend fun markTaskCompleted(taskId: Long) = Unit
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
    ): Task = error("unused")
    override suspend fun login(username: String, password: String): Result<String> = Result.success("tok")
    override suspend fun clearSyncedCacheOnLogout() = Unit
}
