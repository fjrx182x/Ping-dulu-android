package com.example

import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.SseConnectionState
import com.example.data.remote.SseEvent
import com.example.data.remote.SseEventClient
import com.example.data.remote.dto.AddCommentRequestDto
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskPatchRequestDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.presentation.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEfficiencyAndNotificationAuditTest {

    @Test
    fun sseHeartbeatAndKeepaliveEvents_areIgnored_andNeverTriggerServerPull() = runTest {
        // 1. Unit-level filter verification
        assertTrue(SseEventClient.isNoiseOrHeartbeatEvent("ping", "ping"))
        assertTrue(SseEventClient.isNoiseOrHeartbeatEvent("heartbeat", """{"ts":12345}"""))
        assertTrue(SseEventClient.isNoiseOrHeartbeatEvent("connected", """{"status":"ok"}"""))
        assertTrue(SseEventClient.isNoiseOrHeartbeatEvent("keep-alive", "ok"))
        assertTrue(SseEventClient.isNoiseOrHeartbeatEvent("message", """{"type":"ping"}"""))
        assertTrue(SseEventClient.isNoiseOrHeartbeatEvent("message", """{"event":"heartbeat","ts":999}"""))
        assertTrue(SseEventClient.isNoiseOrHeartbeatEvent("message", "{}"))
        assertTrue(SseEventClient.isNoiseOrHeartbeatEvent("message", ""))

        // Real task mutations MUST NOT be treated as noise
        assertFalse(SseEventClient.isNoiseOrHeartbeatEvent("task_created", """{"id":42}"""))
        assertFalse(SseEventClient.isNoiseOrHeartbeatEvent("task_updated", """{"type":"task_updated","id":42}"""))
        assertFalse(SseEventClient.isNoiseOrHeartbeatEvent("message", """{"type":"comment_added","taskId":42}"""))
        assertFalse(SseEventClient.isNoiseOrHeartbeatEvent("message", """{"id":99,"stage":"IN_PROGRESS"}"""))

        // 2. End-to-end ViewModel verification
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val sseFlow = MutableSharedFlow<SseEvent>(extraBufferCapacity = 16)
            val fakeSse = object : SseEventClient(OkHttpClient()) {
                override fun observeEvents(
                    baseUrlProvider: () -> String,
                    reconnectDelayMillis: Long,
                    maxReconnectDelayMillis: Long
                ): Flow<SseEvent> = sseFlow
            }
            val fakeRepo = EfficiencyFakeRepository()
            val vm = ScheduleViewModel(
                repository = fakeRepo,
                sseEventClient = fakeSse,
                baseEpochDayProvider = { 20776L },
                enableBackgroundTicker = false,
                enableSseStream = true,
                enableAutoSyncRetry = false
            )
            val collector = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            sseFlow.tryEmit(SseEvent.StateChanged(SseConnectionState.CONNECTED_LIVE))
            sseFlow.tryEmit(SseEvent.DataMessage("ping", "ping"))
            sseFlow.tryEmit(SseEvent.DataMessage("heartbeat", """{"ts":123}"""))
            sseFlow.tryEmit(SseEvent.DataMessage("connected", """{"message":"connected"}"""))
            sseFlow.tryEmit(SseEvent.DataMessage("message", """{"type":"keepalive"}"""))
            sseFlow.tryEmit(SseEvent.DataMessage("message", "{}"))

            advanceTimeBy(ScheduleViewModel.SSE_PULL_DEBOUNCE_MILLIS + 500L)
            advanceUntilIdle()

            assertEquals("Heartbeat/keepalive SSE tidak boleh memicu GET /api/tasks", 0, fakeRepo.pullOnlyCalls.get())
            assertTrue(vm.uiState.value.notifications.none { it.badgeText == "LIVE SSE" })

            vm.stopBackgroundJobs()
            collector.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun realSseMutationEvent_pullsSilentlyAfter3sDebounce_withoutNotificationSpam() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            assertEquals(3_000L, ScheduleViewModel.SSE_PULL_DEBOUNCE_MILLIS)
            val sseFlow = MutableSharedFlow<SseEvent>(extraBufferCapacity = 16)
            val fakeSse = object : SseEventClient(OkHttpClient()) {
                override fun observeEvents(
                    baseUrlProvider: () -> String,
                    reconnectDelayMillis: Long,
                    maxReconnectDelayMillis: Long
                ): Flow<SseEvent> = sseFlow
            }
            val fakeRepo = EfficiencyFakeRepository()
            val vm = ScheduleViewModel(
                repository = fakeRepo,
                sseEventClient = fakeSse,
                baseEpochDayProvider = { 20776L },
                enableBackgroundTicker = false,
                enableSseStream = true,
                enableAutoSyncRetry = false
            )
            val collector = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            sseFlow.tryEmit(SseEvent.DataMessage("task_updated", """{"id":101}"""))

            // Still inside 3s debounce window
            advanceTimeBy(2_500L)
            assertEquals(0, fakeRepo.pullOnlyCalls.get())

            // Past 3s debounce window -> exactly 1 silent pull and ZERO LIVE SSE notification spam
            advanceTimeBy(700L)
            advanceUntilIdle()
            assertEquals(1, fakeRepo.pullOnlyCalls.get())
            assertTrue(
                "SSE pull rutin tidak boleh menambah notifikasi LIVE SSE",
                vm.uiState.value.notifications.none { it.badgeText == "LIVE SSE" }
            )

            vm.stopBackgroundJobs()
            collector.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun localMutation_suppressesImmediateSseSelfEcho_within4Seconds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            var fakeNowMillis = 100_000L
            val sseFlow = MutableSharedFlow<SseEvent>(extraBufferCapacity = 16)
            val fakeSse = object : SseEventClient(OkHttpClient()) {
                override fun observeEvents(
                    baseUrlProvider: () -> String,
                    reconnectDelayMillis: Long,
                    maxReconnectDelayMillis: Long
                ): Flow<SseEvent> = sseFlow
            }
            val fakeRepo = EfficiencyFakeRepository()
            val vm = ScheduleViewModel(
                repository = fakeRepo,
                sseEventClient = fakeSse,
                baseEpochDayProvider = { 20776L },
                enableBackgroundTicker = false,
                enableSseStream = true,
                enableAutoSyncRetry = false,
                clockMillisProvider = { fakeNowMillis }
            )
            val collector = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            // Local checklist edit at t = 100_000ms
            vm.toggleChecklistItem(taskId = 10L, checklistId = 1L)
            advanceUntilIdle()

            // Server echoes our own mutation 1.2s later (inside 4s suppression window)
            fakeNowMillis = 101_200L
            sseFlow.tryEmit(SseEvent.DataMessage("task_updated", """{"id":10}"""))
            advanceTimeBy(ScheduleViewModel.SSE_PULL_DEBOUNCE_MILLIS + 500L)
            advanceUntilIdle()

            assertEquals("Echo SSE dari aksi lokal sendiri harus diabaikan", 0, fakeRepo.pullOnlyCalls.get())

            // Remote teammate update arrives 5s after our local edit (outside 4s window) -> pulls cleanly!
            fakeNowMillis = 105_000L
            sseFlow.tryEmit(SseEvent.DataMessage("task_updated", """{"id":22}"""))
            advanceTimeBy(ScheduleViewModel.SSE_PULL_DEBOUNCE_MILLIS + 500L)
            advanceUntilIdle()

            assertEquals(1, fakeRepo.pullOnlyCalls.get())

            vm.stopBackgroundJobs()
            collector.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun persistTimerTick_preservesIsSyncedFlag_andNeverTriggersUnsyncedLoop() = runTest {
        val dao = TimerTickFakeDao()
        val syncedRunningTask = TaskEntity(
            id = 55L,
            ticketCode = "NET-55",
            title = "Ganti SFP Core Switch",
            category = "NET",
            scheduledDateEpochDay = 20776L,
            scheduledTimeMinutes = 600,
            timeSlotLabel = "10:00 WIB",
            priority = "P1_CRITICAL",
            stage = "IN_PROGRESS",
            technicalNotes = "",
            trackedSeconds = 120L,
            isTimerRunning = true,
            timerStartedAtMillis = 1_700_000_000_000L,
            location = "NOC",
            slaTargetTime = "",
            checklist = emptyList(),
            assignees = emptyList(),
            isSynced = true,
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_000_000_000L
        )
        dao.upsertTask(syncedRunningTask)

        val repo = TaskRepositoryImpl(
            taskDao = dao,
            apiServiceProvider = { NoOpEfficiencyApiService() }
        )

        // Periodic 10s stopwatch tick checkpoint updates trackedSeconds locally WITHOUT flipping isSynced to false
        repo.persistTimerTick(taskId = 55L, newTrackedSeconds = 130L)
        val afterTick = dao.getTaskByIdOnce(55L)!!
        assertEquals(130L, afterTick.trackedSeconds)
        assertTrue("persistTimerTick wajib mempertahankan isSynced=true agar tidak memicu auto-push loop", afterTick.isSynced)
        assertEquals(0, dao.getUnsyncedCount().first())

        // If task was genuinely unsynced (offline edit), persistTimerTick preserves isSynced=false
        dao.upsertTask(afterTick.copy(isSynced = false))
        repo.persistTimerTick(taskId = 55L, newTrackedSeconds = 140L)
        val afterUnsyncedTick = dao.getTaskByIdOnce(55L)!!
        assertEquals(140L, afterUnsyncedTick.trackedSeconds)
        assertFalse(afterUnsyncedTick.isSynced)
        assertEquals(1, dao.getUnsyncedCount().first())
    }

    @Test
    fun appBackgrounded_pausesSseAndFallbackPolling_andResumeRespects45sCooldown() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            assertEquals(90_000L, ScheduleViewModel.FALLBACK_POLL_INTERVAL_MILLIS)
            assertEquals(45_000L, ScheduleViewModel.RESUME_SYNC_COOLDOWN_MILLIS)

            var fakeNowMillis = 200_000L
            val activeSseConnections = AtomicInteger(0)
            val totalSseStarts = AtomicInteger(0)
            val fakeSse = object : SseEventClient(OkHttpClient()) {
                override fun observeEvents(
                    baseUrlProvider: () -> String,
                    reconnectDelayMillis: Long,
                    maxReconnectDelayMillis: Long
                ): Flow<SseEvent> = flow {
                    totalSseStarts.incrementAndGet()
                    activeSseConnections.incrementAndGet()
                    emit(SseEvent.StateChanged(SseConnectionState.OFFLINE))
                    try {
                        awaitCancellation()
                    } finally {
                        activeSseConnections.decrementAndGet()
                    }
                }
            }
            val fakeRepo = EfficiencyFakeRepository()
            val vm = ScheduleViewModel(
                repository = fakeRepo,
                sseEventClient = fakeSse,
                baseEpochDayProvider = { 20776L },
                enableBackgroundTicker = false,
                enableSseStream = true,
                enableAutoSyncRetry = true,
                clockMillisProvider = { fakeNowMillis }
            )
            val collector = launch { vm.uiState.collect {} }
            advanceTimeBy(100L)

            // Foreground: SSE stream is active
            assertEquals(1, activeSseConnections.get())

            // App goes to background -> SSE stream MUST disconnect immediately & fallback polling MUST pause
            vm.onAppBackgrounded()
            advanceTimeBy(100L)
            assertEquals("SSE stream harus diputus saat app di background", 0, activeSseConnections.get())
            assertEquals(SseConnectionState.OFFLINE, vm.uiState.value.sseState)

            // Stay in background for 5 minutes -> ZERO fallback polling requests!
            advanceTimeBy(300_000L)
            assertEquals("Fallback polling tidak boleh berjalan saat app di background", 0, fakeRepo.pullOnlyCalls.get())

            // Return to foreground -> SSE resumes & first resume sync runs silently (no SYNC SUKSES spam)
            vm.onAppResumed()
            advanceTimeBy(100L)
            assertEquals(1, activeSseConnections.get())
            assertEquals(2, totalSseStarts.get())
            assertEquals(1, fakeRepo.fullPushPullCalls.get())
            assertTrue(
                "Silent onAppResumed sync tidak boleh spam notifikasi SYNC SUKSES",
                vm.uiState.value.notifications.none { it.badgeText == "SYNC SUKSES" }
            )

            // Rapid resume 20s later (inside 45s cooldown) -> skipped!
            fakeNowMillis += 20_000L
            vm.onAppResumed()
            advanceTimeBy(100L)
            assertEquals(1, fakeRepo.fullPushPullCalls.get())

            // Resume 50s after first resume (past 45s cooldown) -> triggers sync cleanly
            fakeNowMillis += 30_000L
            vm.onAppResumed()
            advanceTimeBy(100L)
            assertEquals(2, fakeRepo.fullPushPullCalls.get())

            vm.stopBackgroundJobs()
            collector.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class EfficiencyFakeRepository : TaskRepository {
    val fullPushPullCalls = AtomicInteger(0)
    val pullOnlyCalls = AtomicInteger(0)

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> = MutableStateFlow(emptyList())
    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> = MutableStateFlow(emptyList())
    override fun observeAllTasks(): Flow<List<Task>> = MutableStateFlow(emptyList())
    override fun observeActiveHeroTask(): Flow<Task?> = MutableStateFlow(null)
    override fun observeTaskById(taskId: Long): Flow<Task?> = MutableStateFlow(null)
    override fun observeUnsyncedCount(): Flow<Int> = MutableStateFlow(0)

    override suspend fun initializeOfflineCache() = Unit
    override suspend fun refreshTasksFromServer(): SyncResult {
        fullPushPullCalls.incrementAndGet()
        return SyncResult.Success(downloadedCount = 4, uploadedCount = 0)
    }

    override suspend fun pullTasksFromServerOnly(): SyncResult {
        pullOnlyCalls.incrementAndGet()
        return SyncResult.Success(downloadedCount = 4, uploadedCount = 0)
    }

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
    ): Task = throw UnsupportedOperationException()

    override suspend fun login(username: String, password: String): Result<String> = Result.success("token")
    override suspend fun clearSyncedCacheOnLogout() = Unit
}

private class TimerTickFakeDao : TaskDao {
    private val state = MutableStateFlow<Map<Long, TaskEntity>>(emptyMap())
    private val converters = TaskConverters()

    override fun getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>> =
        state.map { it.values.filter { row -> row.scheduledDateEpochDay == epochDay } }

    override fun getTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>> =
        state.map { it.values.filter { row -> row.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun getAllTasks(): Flow<List<TaskEntity>> = state.map { it.values.toList() }
    override fun getActiveHeroTask(): Flow<TaskEntity?> = state.map { it.values.firstOrNull { r -> r.isTimerRunning } }
    override fun getTaskById(taskId: Long): Flow<TaskEntity?> = state.map { it[taskId] }
    override suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? = state.value[taskId]
    override suspend fun getUnsyncedTasks(): List<TaskEntity> = state.value.values.filter { !it.isSynced }
    override fun getUnsyncedCount(): Flow<Int> = state.map { it.values.count { row -> !row.isSynced } }
    override suspend fun countTasks(): Int = state.value.size
    override suspend fun upsertTasks(tasks: List<TaskEntity>) {
        state.value = state.value + tasks.associateBy { it.id }
    }
    override suspend fun upsertTask(task: TaskEntity) {
        state.value = state.value + (task.id to task)
    }
    override suspend fun updateChecklist(taskId: Long, checklistJson: String, isSynced: Boolean, updatedAtMillis: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(checklist = converters.toChecklistList(checklistJson), isSynced = isSynced, updatedAtMillis = updatedAtMillis))
    }
    override suspend fun updateTaskTimer(taskId: Long, seconds: Long, isRunning: Boolean, timerStartedAtMillis: Long?, isSynced: Boolean, updatedAtMillis: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(trackedSeconds = seconds, isTimerRunning = isRunning, timerStartedAtMillis = timerStartedAtMillis, isSynced = isSynced, updatedAtMillis = updatedAtMillis))
    }
    override suspend fun pauseAllOtherTimers(excludeTaskId: Long, updatedAtMillis: Long) = Unit
    override suspend fun updateTaskStage(taskId: Long, stage: String, isRunning: Boolean, timerStartedAtMillis: Long?, isSynced: Boolean, updatedAtMillis: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(stage = stage, isTimerRunning = isRunning, timerStartedAtMillis = timerStartedAtMillis, isSynced = isSynced, updatedAtMillis = updatedAtMillis))
    }
    override suspend fun markTaskSynced(taskId: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(isSynced = true))
    }
    override suspend fun getTaskByClientSyncId(clientSyncId: String): TaskEntity? =
        state.value.values.firstOrNull { it.clientSyncId == clientSyncId }
    override suspend fun deleteTaskById(taskId: Long) {
        state.value = state.value - taskId
    }
    override suspend fun deletePendingByClientSyncId(clientSyncId: String) {
        state.value = state.value.filterValues { !(it.id < 0L && it.clientSyncId == clientSyncId) }
    }
    override suspend fun deleteStaleSyncedTasks(activeIds: List<Long>) {
        val set = activeIds.toSet()
        state.value = state.value.filterValues { !it.isSynced || it.id in set }
    }
    override suspend fun deleteAllSyncedTasks() {
        state.value = state.value.filterValues { !it.isSynced }
    }
}

private class NoOpEfficiencyApiService : PingDuluApiService {
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> =
        Response.success(LoginResponseDto(token = "token"))
    override suspend fun getUsersRaw(): Response<ResponseBody> = Response.success(ResponseBody.create(null, "[]"))
    override suspend fun getLabelsRaw(): Response<ResponseBody> = Response.success(ResponseBody.create(null, "[]"))
    override suspend fun getTasks(): Response<TaskResponseDto> = Response.success(TaskResponseDto())
    override suspend fun getTaskDetailRaw(taskId: Long): Response<ResponseBody> =
        Response.error(404, ResponseBody.create(null, "{}"))
    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> =
        Response.success(CreateTaskResponseDto(id = 1L))
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = Response.success(Unit)
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = Response.success(Unit)
    override suspend fun toggleChecklistStep(id: Long, stepId: Long, body: ChecklistStepToggleDto): Response<Unit> =
        Response.success(Unit)
    override suspend fun patchTask(id: Long, body: TaskPatchRequestDto): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "{}"))
    override suspend fun getAttachmentsRaw(taskId: Long): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "[]"))
    override suspend fun uploadAttachment(taskId: Long, file: MultipartBody.Part): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "{}"))
    override suspend fun getCommentsRaw(taskId: Long): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "[]"))
    override suspend fun postComment(taskId: Long, body: AddCommentRequestDto): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "{}"))
    override suspend fun getActivitiesRaw(taskId: Long): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "[]"))
    override suspend fun getActivitySingularRaw(taskId: Long): Response<ResponseBody> =
        Response.error(404, ResponseBody.create(null, "{}"))
    override suspend fun getTaskLogsRaw(taskId: Long): Response<ResponseBody> =
        Response.error(404, ResponseBody.create(null, "{}"))
    override suspend fun workTask(taskId: Long): Response<com.example.data.remote.dto.WorkTaskResponseDto> =
        Response.success(com.example.data.remote.dto.WorkTaskResponseDto(success = true, stage = "IN_PROGRESS"))
    override suspend fun claimTask(taskId: Long): Response<com.example.data.remote.dto.ClaimTaskResponseDto> =
        Response.success(com.example.data.remote.dto.ClaimTaskResponseDto(success = true))
    override suspend fun deleteTask(taskId: Long): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "{}"))
    override suspend fun getHolidays(year: Int): Response<com.example.data.remote.dto.HolidayResponseDto> =
        Response.success(com.example.data.remote.dto.HolidayResponseDto(year = year))
    override suspend fun getCalendar(year: Int, month: Int): Response<com.example.data.remote.dto.CalendarAgendaResponseDto> =
        Response.success(com.example.data.remote.dto.CalendarAgendaResponseDto(year = year, month = month))
    override suspend fun addChecklistStep(taskId: Long, body: com.example.data.remote.dto.AddChecklistStepRequestDto): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "{}"))
    override suspend fun postSpeedTestRecord(body: com.example.data.remote.dto.SpeedTestRecordRequestDto): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "{}"))
}
