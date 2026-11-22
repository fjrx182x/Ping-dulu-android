package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.remote.AuthInterceptor
import com.example.data.remote.NetworkModule
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.TokenProvider
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.presentation.login.LoginScreen
import com.example.presentation.schedule.ScheduleViewModel
import com.example.ui.theme.PingDuluFieldOpsTheme
import com.example.util.ConnectivityMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealTimeSyncAndLoginAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreen_usernameStartsEmptyWithoutHardcodedFajarPlaceholder() {
        var submittedUsername: String? = null
        var submittedPassword: String? = null

        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                LoginScreen(
                    isLoggingIn = false,
                    errorMessage = null,
                    onClearError = {},
                    onLogin = { user, pass ->
                        submittedUsername = user
                        submittedPassword = pass
                    }
                )
            }
        }

        // Username field must NOT contain or display "fajar" anywhere
        composeRule.onNodeWithText("fajar", ignoreCase = true, useUnmergedTree = true).assertDoesNotExist()

        // Tap username field -> neutral Indonesian placeholder appears instead of "fajar"
        composeRule.onNodeWithTag("login_username_input").performClick()
        composeRule.onNodeWithText("Masukkan username teknisi", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("fajar", ignoreCase = true, useUnmergedTree = true).assertDoesNotExist()

        // Technician can type any account cleanly
        composeRule.onNodeWithTag("login_username_input").performScrollTo().performTextInput("budi")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("rahasia123")
        composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("budi", submittedUsername)
        assertEquals("rahasia123", submittedPassword)
    }

    @Test
    fun authInterceptor_preservesSseTextEventStreamAcceptHeader() {
        val interceptor = AuthInterceptor(TokenProvider { "jwt_live_token" })
        var sseAcceptHeader: String? = null
        var sseAuthHeader: String? = null
        var jsonAcceptHeader: String? = null

        fun runIntercept(req: Request, onProceed: (Request) -> Unit) {
            interceptor.intercept(object : Interceptor.Chain {
                override fun request(): Request = req
                override fun proceed(request: Request): OkHttpResponse {
                    onProceed(request)
                    return OkHttpResponse.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody())
                        .build()
                }
                override fun connection() = null
                override fun call() = throw UnsupportedOperationException()
                override fun connectTimeoutMillis() = 5_000
                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
                override fun readTimeoutMillis() = 5_000
                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
                override fun writeTimeoutMillis() = 5_000
                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            })
        }

        val sseReq = Request.Builder()
            .url("https://ping.fjrx.my.id/api/events")
            .header("Accept", "text/event-stream")
            .build()
        runIntercept(sseReq) { forwarded ->
            sseAcceptHeader = forwarded.header("Accept")
            sseAuthHeader = forwarded.header("Authorization")
        }

        val apiReq = Request.Builder()
            .url("https://ping.fjrx.my.id/api/tasks")
            .build()
        runIntercept(apiReq) { forwarded ->
            jsonAcceptHeader = forwarded.header("Accept")
        }

        assertEquals("text/event-stream", sseAcceptHeader)
        assertEquals("Bearer jwt_live_token", sseAuthHeader)
        assertEquals("application/json", jsonAcceptHeader)
    }

    @Test
    fun createTaskResponseDto_parsesAllFastifyShapesWithoutCrash() {
        val moshi = NetworkModule.createMoshi()
        val adapter = moshi.adapter(CreateTaskResponseDto::class.java)
        val localFallback = TaskEntity(
            id = -999L,
            ticketCode = "NET-500",
            title = "Ganti Kabel Patch Cord",
            category = "NET",
            scheduledDateEpochDay = 20776L,
            scheduledTimeMinutes = null,
            timeSlotLabel = "",
            priority = "P2_HIGH",
            stage = "BACKLOG",
            technicalNotes = "Catatan",
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = "Server Room",
            slaTargetTime = "",
            checklist = emptyList(),
            assignees = emptyList(),
            isSynced = false,
            createdAtMillis = 1_700_000_000_000L,
            dueDateMillis = null,
            assignedToId = 3L,
            labels = listOf("Jaringan"),
            updatedAtMillis = 1_700_000_000_000L
        )

        // Shape 1: Wrapped { "data": { ... } }
        val dataWrapped = adapter.fromJson(
            """{"data":{"id":201,"ticket_code":"NET-201","title":"Wrapped Data","project_or_category":"NET"}}"""
        )!!.resolveCreatedEntity(localFallback)!!
        assertEquals(201L, dataWrapped.id)
        assertEquals("NET-201", dataWrapped.ticketCode)
        assertTrue(dataWrapped.isSynced)

        // Shape 2: Wrapped { "task": { ... } }
        val taskWrapped = adapter.fromJson(
            """{"task":{"id":202,"ticket_code":"NET-202","title":"Wrapped Task","project_or_category":"NET"}}"""
        )!!.resolveCreatedEntity(localFallback)!!
        assertEquals(202L, taskWrapped.id)
        assertEquals("NET-202", taskWrapped.ticketCode)
        assertTrue(taskWrapped.isSynced)

        // Shape 3: Flat TaskDto
        val flatDto = adapter.fromJson(
            """{"id":203,"ticket_code":"NET-203","title":"Flat Task","project_or_category":"NET"}"""
        )!!.resolveCreatedEntity(localFallback)!!
        assertEquals(203L, flatDto.id)
        assertEquals("Flat Task", flatDto.title)
        assertTrue(flatDto.isSynced)

        // Shape 4: Minimal confirmation { "success": true, "id": 204 }
        val minimalDto = adapter.fromJson(
            """{"success":true,"id":204,"ticket_code":"NET-204"}"""
        )!!.resolveCreatedEntity(localFallback)!!
        assertEquals(204L, minimalDto.id)
        assertEquals("NET-204", minimalDto.ticketCode)
        assertEquals("Ganti Kabel Patch Cord", minimalDto.title)
        assertTrue(minimalDto.isSynced)

        // Shape 5: Empty {} returns null (repository falls back to GET /api/tasks)
        val emptyDto = adapter.fromJson("""{}""")!!.resolveCreatedEntity(localFallback)
        assertNull(emptyDto)
    }

    @Test
    fun repository_createQuickTask_syncsImmediatelyOnlineAndHandles409RetryAndEmpty201() = runTest {
        // Case 1: Immediate online sync with { "data": { ... } } envelope
        val dao1 = AuditFakeDao()
        val api1 = FlexibleCreateApiService(
            createHandler = { req ->
                Response.success(
                    201,
                    CreateTaskResponseDto(
                        data = TaskDto(
                            id = 501L,
                            ticketCode = req.ticketCode,
                            title = req.title,
                            projectOrCategory = req.projectOrCategory,
                            scheduledDateEpochDay = req.scheduledDateEpochDay,
                            priority = req.priority,
                            stage = req.stage,
                            location = req.location
                        )
                    )
                )
            }
        )
        val repo1 = TaskRepositoryImpl(taskDao = dao1, apiServiceProvider = { api1 })
        val created1 = repo1.createQuickTask(
            title = "Ganti Switch PoE",
            category = TicketCategory.NET,
            priority = Priority.P1_CRITICAL,
            epochDay = 20776L,
            location = "Gudang Barat"
        )
        assertEquals(501L, created1.id)
        assertTrue(created1.isSynced)
        assertEquals(0, dao1.getUnsyncedTasks().size)
        assertNull(repo1.getLastSyncError())

        // Case 2: Server returns 409 UNIQUE ticket_code collision -> automatic retry succeeds immediately
        val dao2 = AuditFakeDao()
        var attemptCount = 0
        val capturedCodes = mutableListOf<String>()
        val api2 = FlexibleCreateApiService(
            createHandler = { req ->
                attemptCount++
                capturedCodes += req.ticketCode
                if (attemptCount == 1) {
                    Response.error(409, """{"error":"UNIQUE constraint failed: tasks.ticket_code"}""".toResponseBody())
                } else {
                    Response.success(201, CreateTaskResponseDto(id = 502L, ticketCode = req.ticketCode))
                }
            }
        )
        val repo2 = TaskRepositoryImpl(taskDao = dao2, apiServiceProvider = { api2 })
        val created2 = repo2.createQuickTask(
            title = "Upgrade Router Core",
            category = TicketCategory.NET,
            priority = Priority.P2_HIGH,
            epochDay = 20776L,
            location = "Rack Utama"
        )
        assertEquals(2, attemptCount)
        assertNotEquals(capturedCodes[0], capturedCodes[1])
        assertEquals(502L, created2.id)
        assertTrue(created2.isSynced)
        assertEquals(0, dao2.getUnsyncedTasks().size)

        // Case 3: Server returns 201 with empty `{}` -> falls back to GET /api/tasks and clears negative ID
        val dao3 = AuditFakeDao()
        var savedReqCode = ""
        val api3 = FlexibleCreateApiService(
            createHandler = { req ->
                savedReqCode = req.ticketCode
                Response.success(201, CreateTaskResponseDto())
            },
            getTasksHandler = {
                Response.success(
                    TaskResponseDto(
                        data = listOf(
                            TaskDto(
                                id = 503L,
                                ticketCode = savedReqCode,
                                title = "Cek UPS Server",
                                projectOrCategory = "SRV",
                                scheduledDateEpochDay = 20776L,
                                priority = "P2_HIGH",
                                stage = "BACKLOG",
                                location = "Data Center"
                            )
                        )
                    )
                )
            }
        )
        val repo3 = TaskRepositoryImpl(taskDao = dao3, apiServiceProvider = { api3 })
        val created3 = repo3.createQuickTask(
            title = "Cek UPS Server",
            category = TicketCategory.SRV,
            priority = Priority.P2_HIGH,
            epochDay = 20776L,
            location = "Data Center"
        )
        assertEquals(503L, created3.id)
        assertTrue(created3.isSynced)
        assertEquals(0, dao3.getUnsyncedTasks().size)
        assertEquals(1, dao3.countTasks()) // Negative temporary ID cleanly deleted!
    }

    @Test
    fun repository_createAndSyncFailure_surfacesExactServerHttpError() = runTest {
        val dao = AuditFakeDao()
        val rejectingApi = FlexibleCreateApiService(
            createHandler = {
                Response.error(400, """{"error":"Field location wajib diisi"}""".toResponseBody())
            },
            getTasksHandler = { Response.success(TaskResponseDto(data = emptyList())) }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { rejectingApi })

        val localTask = repo.createQuickTask(
            title = "Tes Validasi Server",
            category = TicketCategory.HRD,
            priority = Priority.P3_NORMAL,
            epochDay = 20776L,
            location = "Lt 3"
        )

        assertFalse(localTask.isSynced)
        assertTrue(repo.getLastSyncError()?.contains("HTTP 400") == true)
        assertTrue(repo.getLastSyncError()?.contains("Field location wajib diisi") == true)

        val syncRes = repo.refreshTasksFromServer() as SyncResult.Success
        assertEquals(0, syncRes.uploadedCount)
        assertEquals(1, syncRes.failedUploadCount)
        assertTrue(syncRes.lastUploadError?.contains("HTTP 400") == true)
    }

    @Test
    fun viewModel_autoSyncsAutomaticallyOnNetworkRecoveryAndAppResume() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineFlow = MutableStateFlow(false)
            val fakeMonitor = object : ConnectivityMonitor {
                override val isOnline: Flow<Boolean> = onlineFlow
            }
            val fakeRepo = CountingSyncFakeRepository()
            val vm = ScheduleViewModel(
                repository = fakeRepo,
                connectivityMonitor = fakeMonitor,
                baseEpochDayProvider = { 20776L },
                enableBackgroundTicker = false,
                enableSseStream = false,
                enableAutoSyncRetry = false
            )
            val collectorJob = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(0, fakeRepo.syncCalls)
            assertFalse(vm.uiState.value.isNetworkOnline)

            // Network transitions offline -> online: automatic sync MUST fire immediately!
            onlineFlow.value = true
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isNetworkOnline)
            assertEquals(1, fakeRepo.syncCalls)

            // App foreground resume triggers quiet real-time refresh automatically
            vm.onAppResumed()
            advanceUntilIdle()
            assertEquals(2, fakeRepo.syncCalls)

            vm.stopBackgroundJobs()
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FlexibleCreateApiService(
    private val createHandler: (CreateTaskRequestDto) -> Response<CreateTaskResponseDto>,
    private val getTasksHandler: () -> Response<TaskResponseDto> = { Response.success(TaskResponseDto()) }
) : PingDuluApiService {
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> =
        Response.success(LoginResponseDto(token = "token"))

    override suspend fun getUsersRaw(): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "[]"))

    override suspend fun getLabelsRaw(): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "[]"))

    override suspend fun getTasks(): Response<TaskResponseDto> = getTasksHandler()

    override suspend fun getTaskDetailRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.error(404, okhttp3.ResponseBody.create(null, "{}"))

    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> =
        createHandler(body)

    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = Response.success(Unit)
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = Response.success(Unit)
    override suspend fun toggleChecklistStep(
        id: Long,
        stepId: Long,
        body: ChecklistStepToggleDto
    ): Response<Unit> = Response.success(Unit)

    override suspend fun patchTask(
        id: Long,
        body: com.example.data.remote.dto.TaskPatchRequestDto
    ): Response<okhttp3.ResponseBody> = Response.success(okhttp3.ResponseBody.create(null, "{}"))

    override suspend fun getAttachmentsRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "[]"))

    override suspend fun uploadAttachment(
        taskId: Long,
        file: okhttp3.MultipartBody.Part
    ): Response<okhttp3.ResponseBody> = Response.success(okhttp3.ResponseBody.create(null, "{}"))

    override suspend fun getCommentsRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "[]"))

    override suspend fun postComment(
        taskId: Long,
        body: com.example.data.remote.dto.AddCommentRequestDto
    ): Response<okhttp3.ResponseBody> = Response.success(okhttp3.ResponseBody.create(null, "{}"))

    override suspend fun getActivitiesRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "[]"))

    override suspend fun getActivitySingularRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.error(404, okhttp3.ResponseBody.create(null, "{}"))

    override suspend fun getTaskLogsRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.error(404, okhttp3.ResponseBody.create(null, "{}"))

    override suspend fun workTask(taskId: Long): Response<com.example.data.remote.dto.WorkTaskResponseDto> =
        Response.success(com.example.data.remote.dto.WorkTaskResponseDto(success = true, stage = "IN_PROGRESS"))

    override suspend fun claimTask(taskId: Long): Response<com.example.data.remote.dto.ClaimTaskResponseDto> =
        Response.success(com.example.data.remote.dto.ClaimTaskResponseDto(success = true))

    override suspend fun deleteTask(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "{}"))

    override suspend fun getHolidays(year: Int): Response<com.example.data.remote.dto.HolidayResponseDto> =
        Response.success(com.example.data.remote.dto.HolidayResponseDto(year = year))

    override suspend fun getCalendar(year: Int, month: Int): Response<com.example.data.remote.dto.CalendarAgendaResponseDto> =
        Response.success(com.example.data.remote.dto.CalendarAgendaResponseDto(year = year, month = month))

    override suspend fun addChecklistStep(taskId: Long, body: com.example.data.remote.dto.AddChecklistStepRequestDto): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "{}"))

    override suspend fun postSpeedTestRecord(body: com.example.data.remote.dto.SpeedTestRecordRequestDto): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "{}"))
}

private class CountingSyncFakeRepository : TaskRepository {
    var syncCalls: Int = 0
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay == epochDay } }

    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow
    override fun observeActiveHeroTask(): Flow<Task?> = MutableStateFlow(null)
    override fun observeTaskById(taskId: Long): Flow<Task?> = MutableStateFlow(null)
    override fun observeUnsyncedCount(): Flow<Int> = tasksFlow.map { list -> list.count { !it.isSynced } }
    override suspend fun initializeOfflineCache() = Unit

    override suspend fun refreshTasksFromServer(): SyncResult {
        syncCalls++
        return SyncResult.Success(downloadedCount = 0, uploadedCount = 1)
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

private class AuditFakeDao : TaskDao {
    private val state = MutableStateFlow<Map<Long, TaskEntity>>(emptyMap())
    private val converters = TaskConverters()

    override fun getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>> =
        state.map { map -> map.values.filter { it.scheduledDateEpochDay == epochDay } }

    override fun getTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>> =
        state.map { map -> map.values.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun getAllTasks(): Flow<List<TaskEntity>> = state.map { it.values.toList() }
    override fun getActiveHeroTask(): Flow<TaskEntity?> = state.map { map -> map.values.firstOrNull { it.stage == Stage.IN_PROGRESS.apiValue } }
    override fun getTaskById(taskId: Long): Flow<TaskEntity?> = state.map { it[taskId] }
    override suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? = state.value[taskId]
    override suspend fun getUnsyncedTasks(): List<TaskEntity> = state.value.values.filter { !it.isSynced }
    override fun getUnsyncedCount(): Flow<Int> = state.map { map -> map.values.count { !it.isSynced } }
    override suspend fun countTasks(): Int = state.value.size
    override suspend fun upsertTasks(tasks: List<TaskEntity>) {
        val next = state.value.toMutableMap()
        tasks.forEach { next[it.id] = it }
        state.value = next
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
        val activeSet = activeIds.toSet()
        state.value = state.value.filterValues { !it.isSynced || it.id in activeSet }
    }
    override suspend fun deleteAllSyncedTasks() {
        state.value = state.value.filterValues { !it.isSynced }
    }
}
