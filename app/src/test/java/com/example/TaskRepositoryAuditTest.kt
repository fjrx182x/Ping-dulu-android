package com.example

import com.example.data.local.SeedTaskData
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.remote.AuthInterceptor
import com.example.data.remote.NetworkModule
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.TokenProvider
import com.example.data.remote.dto.AssigneeDto
import com.example.data.remote.dto.ChecklistItemDto
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.remote.dto.UserDto
import com.example.data.remote.dto.toDomain
import com.example.data.remote.dto.toEntity
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.http.PATCH
import retrofit2.http.POST
import java.io.IOException

class TaskRepositoryAuditTest {

    @Test
    fun normalizeBaseUrl_handlesMissingSchemeAndTrailingSlash() {
        assertEquals("https://ping.fjrx.my.id/", NetworkModule.normalizeBaseUrl("ping.fjrx.my.id"))
        assertEquals("https://ping.fjrx.my.id/", NetworkModule.normalizeBaseUrl("https://ping.fjrx.my.id"))
        assertEquals("http://192.168.1.10:3000/", NetworkModule.normalizeBaseUrl("http://192.168.1.10:3000"))
        assertEquals(NetworkModule.DEFAULT_BASE_URL, NetworkModule.normalizeBaseUrl("   "))
        assertEquals(NetworkModule.DEFAULT_BASE_URL, NetworkModule.normalizeBaseUrl("https://invalid host with spaces"))
    }

    @Test
    fun authInterceptor_injectsBearerHeaderWhenTokenPresent() {
        var capturedAuthHeader: String? = null
        val interceptor = AuthInterceptor(TokenProvider { "secret_token_123" })
        val request = Request.Builder().url("https://ping.fjrx.my.id/api/tasks").build()

        val fakeChain = object : okhttp3.Interceptor.Chain {
            override fun request(): Request = request
            override fun proceed(request: Request): OkHttpResponse {
                capturedAuthHeader = request.header("Authorization")
                return OkHttpResponse.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody())
                    .build()
            }
            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 10_000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 10_000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 10_000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(fakeChain)
        assertEquals("Bearer secret_token_123", capturedAuthHeader)
    }

    @Test
    fun taskDto_mapsIntegerBooleansFromPingDuluApiCorrectly() {
        val dto = TaskDto(
            id = 59L,
            ticketCode = "NET-1",
            title = "Troubleshooting Wi-Fi",
            projectOrCategory = "NET",
            scheduledDateEpochDay = 20776L,
            scheduledTimeMinutes = 540,
            priority = "P1_CRITICAL",
            stage = "IN_PROGRESS",
            trackedSeconds = 2712L,
            timerStartedAtMillis = 1795113557000L,
            location = "Ruang Server Lt. 2",
            slaTargetTime = "11:30 WIB",
            checklist = listOf(
                ChecklistItemDto(101L, "Cek PoE", isDone = 1),
                ChecklistItemDto(102L, "Reboot AP", isDone = 0)
            ),
            assignees = listOf(
                AssigneeDto(id = 3L, username = "fajar", displayName = "Fajar", role = "admin", isLead = 1),
                AssigneeDto(id = 2L, username = "budi", displayName = null, name = "Budi", role = "technician", isLead = 0)
            )
        )

        val entity = dto.toEntity(nowMillis = 1000L)
        assertTrue(entity.isTimerRunning)
        assertTrue(entity.checklist[0].isDone)
        assertFalse(entity.checklist[1].isDone)
        assertEquals("Fajar", entity.assignees[0].name)
        assertTrue(entity.assignees[0].isLead)
        assertEquals("Budi", entity.assignees[1].name)
        assertFalse(entity.assignees[1].isLead)
        assertEquals("09:00 - 10:30", entity.timeSlotLabel)
    }

    @Test
    fun authAndAssigneeDtos_moshiSerializesUsernameAndParsesDisplayNameWithoutCrash() {
        val moshi = NetworkModule.createMoshi()

        // 1. LoginRequestDto MUST serialize "username" (never "email")
        val reqJson = moshi.adapter(LoginRequestDto::class.java)
            .toJson(LoginRequestDto(username = "fajar", password = "secret"))
        assertTrue(reqJson.contains("\"username\":\"fajar\""))
        assertFalse(reqJson.contains("\"email\""))

        // 2. LoginResponseDto parses real PingDulu user payload with display_name
        val loginResJson = """
            {
              "token": "jwt_token_xyz",
              "user": { "id": 3, "username": "fajar", "display_name": "Fajar", "role": "admin" }
            }
        """.trimIndent()
        val parsedLogin = moshi.adapter(LoginResponseDto::class.java).fromJson(loginResJson)!!
        assertEquals("jwt_token_xyz", parsedLogin.resolvedToken)
        assertEquals("Fajar", parsedLogin.user?.resolvedName)
        assertEquals("admin", parsedLogin.user?.role)

        // 3. AssigneeDto parses server users table rows WITHOUT "name" field without JsonDataException
        val assigneeDisplayOnlyJson = """
            { "id": 3, "username": "fajar", "display_name": "Fajar S.", "role": "admin", "is_lead": 1 }
        """.trimIndent()
        val assigneeUsernameOnlyJson = """
            { "id": 4, "username": "rizky", "display_name": "  ", "role": "technician", "is_lead": 0 }
        """.trimIndent()
        val assigneeEmptyJson = """
            { "id": 5 }
        """.trimIndent()

        val adapter = moshi.adapter(AssigneeDto::class.java)
        assertEquals("Fajar S.", adapter.fromJson(assigneeDisplayOnlyJson)!!.toDomain().name)
        assertEquals("rizky", adapter.fromJson(assigneeUsernameOnlyJson)!!.toDomain().name)
        assertEquals("Teknisi", adapter.fromJson(assigneeEmptyJson)!!.toDomain().name)
    }

    @Test
    fun apiContract_enforcesFixedBaseUrlPostStageTimerActionAndChecklistPatch() {
        assertEquals("https://ping.fjrx.my.id/", PingDuluApiService.FIXED_BASE_URL)
        assertEquals(PingDuluApiService.FIXED_BASE_URL, NetworkModule.DEFAULT_BASE_URL)

        // Verify Retrofit HTTP method & path annotations on PingDuluApiService
        val stageMethod = PingDuluApiService::class.java.methods.first { it.name == "updateStage" }
        val stagePost = stageMethod.getAnnotation(POST::class.java)
        assertEquals("api/tasks/{id}/stage", stagePost?.value)
        assertEquals(null, stageMethod.getAnnotation(PATCH::class.java))

        val timerMethod = PingDuluApiService::class.java.methods.first { it.name == "updateTimer" }
        assertEquals("api/tasks/{id}/timer", timerMethod.getAnnotation(POST::class.java)?.value)

        val checklistMethod = PingDuluApiService::class.java.methods.first { it.name == "toggleChecklistStep" }
        assertEquals("api/tasks/{id}/checklist/{stepId}", checklistMethod.getAnnotation(PATCH::class.java)?.value)

        // Verify Moshi serialization for TimerActionRequestDto & ChecklistStepToggleDto
        val moshi = NetworkModule.createMoshi()
        val timerStartJson = moshi.adapter(TimerActionRequestDto::class.java)
            .toJson(TimerActionRequestDto(TimerActionRequestDto.ACTION_START))
        assertEquals("{\"action\":\"start\"}", timerStartJson)

        val checklistDoneJson = moshi.adapter(ChecklistStepToggleDto::class.java)
            .toJson(ChecklistStepToggleDto(isDone = 1))
        assertEquals("{\"is_done\":1}", checklistDoneJson)
    }

    @Test
    fun repository_toggleChecklistOffline_updatesLocalImmediatelyAndMarksUnsynced() = runTest {
        val fakeDao = InMemoryTaskDao(SeedTaskData.createInitialTasks(baseEpochDay = 20776L))
        val offlineApi = FailingPingDuluApiService()
        val repository = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { offlineApi }
        )

        // Toggle item 103 (initially false) while offline
        repository.toggleChecklistItem(taskId = 59L, checklistId = 103L)

        val updated = fakeDao.getTaskByIdOnce(59L)!!
        val item103 = updated.checklist.first { it.id == 103L }
        assertTrue(item103.isDone)
        assertFalse(updated.isSynced)

        // Refresh while offline must gracefully return OfflineFallback without losing local change
        val syncResult = repository.refreshTasksFromServer()
        assertTrue(syncResult is SyncResult.OfflineFallback)
        assertEquals(1, (syncResult as SyncResult.OfflineFallback).pendingUploadCount)
        assertTrue(fakeDao.getTaskByIdOnce(59L)!!.checklist.first { it.id == 103L }.isDone)
    }

    @Test
    fun repository_toggleTimerOnBacklogTask_promotesToInProgressAndPausesOthers() = runTest {
        val fakeDao = InMemoryTaskDao(SeedTaskData.createInitialTasks(baseEpochDay = 20776L))
        val offlineApi = FailingPingDuluApiService()
        val repository = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { offlineApi }
        )

        // Initially 59 is running, 60 is BACKLOG and paused
        assertTrue(fakeDao.getTaskByIdOnce(59L)!!.isTimerRunning)
        assertEquals(Stage.BACKLOG.apiValue, fakeDao.getTaskByIdOnce(60L)!!.stage)

        // Start timer on 60
        repository.toggleTaskTimer(taskId = 60L)

        val task59 = fakeDao.getTaskByIdOnce(59L)!!
        val task60 = fakeDao.getTaskByIdOnce(60L)!!
        assertFalse(task59.isTimerRunning)
        assertFalse(task59.isSynced)
        assertTrue(task60.isTimerRunning)
        assertEquals(Stage.IN_PROGRESS.apiValue, task60.stage)
    }

    @Test
    fun repository_initializeOfflineCache_doesNotInjectDummySeedTasks() = runTest {
        val emptyDao = InMemoryTaskDao(emptyList())
        val repository = TaskRepositoryImpl(
            taskDao = emptyDao,
            apiServiceProvider = { FailingPingDuluApiService() }
        )

        repository.initializeOfflineCache()

        assertEquals(0, emptyDao.countTasks())
    }

    @Test
    fun repository_loginSendsUsernameAndHandlesErrorResponses() = runTest {
        val fakeDao = InMemoryTaskDao(emptyList())
        val recordingApi = RecordingPingDuluApiService(
            loginHandler = { req ->
                if (req.username == "fajar" && req.password == "valid_pass") {
                    Response.success(
                        LoginResponseDto(
                            token = "jwt_prod_token",
                            user = UserDto(
                                id = 3L,
                                username = "fajar",
                                displayName = "Fajar Sigit",
                                role = "admin"
                            )
                        )
                    )
                } else {
                    Response.error(401, """{"error":"Unauthorized"}""".toResponseBody())
                }
            }
        )
        val repository = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { recordingApi }
        )

        val okResult = repository.login("  fajar  ", "valid_pass")
        assertTrue(okResult.isSuccess)
        assertEquals("jwt_prod_token", okResult.getOrNull())
        assertEquals("fajar", recordingApi.capturedLoginRequests.single().username)

        val authFailResult = repository.login("fajar", "wrong_pass")
        assertTrue(authFailResult.isFailure)
        assertTrue(authFailResult.exceptionOrNull()?.message?.contains("401") == true)

        val offlineRepo = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { FailingPingDuluApiService() }
        )
        val offlineResult = offlineRepo.login("fajar", "valid_pass")
        assertTrue(offlineResult.isFailure)
        assertTrue(offlineResult.exceptionOrNull()?.message?.contains("ping.fjrx.my.id") == true)
    }

    @Test
    fun repository_refreshTasksPushesViaPostStageTimerActionChecklistPatchAndReconcilesStaleRows() = runTest {
        val seed = SeedTaskData.createInitialTasks(baseEpochDay = 20776L)
        val staleSyncedTask = seed[0].copy(id = 10L, ticketCode = "OLD-10", isSynced = true)
        val unsyncedPushOkTask = seed[1].copy(
            id = 60L,
            ticketCode = "SRV-60",
            stage = Stage.IN_PROGRESS.apiValue,
            isTimerRunning = true,
            isSynced = false
        )
        val unsyncedPushFailTask = seed[2].copy(
            id = 61L,
            ticketCode = "HRD-61",
            stage = Stage.DONE.apiValue,
            isTimerRunning = false,
            isSynced = false
        )

        val fakeDao = InMemoryTaskDao(listOf(staleSyncedTask, unsyncedPushOkTask, unsyncedPushFailTask))
        val remoteFreshDto = TaskDto(
            id = 70L,
            ticketCode = "NET-70",
            title = "Server Ticket Baru",
            projectOrCategory = "NET",
            scheduledDateEpochDay = 20776L,
            scheduledTimeMinutes = 600,
            priority = "P1_CRITICAL",
            stage = "BACKLOG",
            assignees = listOf(
                AssigneeDto(id = 3L, username = "fajar", displayName = "Fajar", role = "admin", isLead = 1)
            )
        )
        val remoteOkDto = TaskDto(
            id = 60L,
            ticketCode = "SRV-60",
            title = unsyncedPushOkTask.title,
            projectOrCategory = "SRV",
            scheduledDateEpochDay = 20776L,
            scheduledTimeMinutes = 660,
            priority = "P2_HIGH",
            stage = "IN_PROGRESS",
            timerStartedAtMillis = 1795113557000L
        )
        val remoteFailDto = TaskDto(
            id = 61L,
            ticketCode = "HRD-61",
            title = "Server Version Should Not Overwrite Failed Local",
            projectOrCategory = "HRD",
            scheduledDateEpochDay = 20776L,
            scheduledTimeMinutes = 720,
            priority = "P3_NORMAL",
            stage = "BACKLOG"
        )

        val recordingApi = RecordingPingDuluApiService(
            tasksResponse = Response.success(
                TaskResponseDto(tasks = listOf(remoteOkDto, remoteFailDto, remoteFreshDto))
            ),
            failingTaskIds = setOf(61L)
        )
        val repository = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { recordingApi }
        )

        val syncResult = repository.refreshTasksFromServer()

        assertTrue(syncResult is SyncResult.Success)
        val success = syncResult as SyncResult.Success
        assertEquals(1, success.uploadedCount) // Only task 60 uploaded; task 61 failed
        assertEquals(2, success.downloadedCount) // Task 60 & 70 upserted; task 61 skipped to preserve unsynced local work

        // Verify Fastify contract calls for task 60
        assertEquals(Stage.IN_PROGRESS.apiValue, recordingApi.capturedStageCalls[60L]?.stage)
        assertEquals(TimerActionRequestDto.ACTION_START, recordingApi.capturedTimerCalls[60L]?.action)
        assertEquals(
            unsyncedPushOkTask.checklist.size,
            recordingApi.capturedChecklistCalls.count { it.first == 60L }
        )

        // Stale synced task 10 removed; fresh server task 70 present; failed local task 61 preserved intact
        assertEquals(null, fakeDao.getTaskByIdOnce(10L))
        assertEquals("NET-70", fakeDao.getTaskByIdOnce(70L)?.ticketCode)
        val preserved61 = fakeDao.getTaskByIdOnce(61L)!!
        assertEquals(Stage.DONE.apiValue, preserved61.stage)
        assertFalse(preserved61.isSynced)
    }

    @Test
    fun repository_clearSyncedCacheOnLogout_removesSyncedButPreservesUnsynced() = runTest {
        val seed = SeedTaskData.createInitialTasks(baseEpochDay = 20776L)
        val syncedTask = seed[0].copy(id = 100L, isSynced = true)
        val unsyncedTask = seed[1].copy(id = 101L, isSynced = false)
        val fakeDao = InMemoryTaskDao(listOf(syncedTask, unsyncedTask))
        val repository = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { FailingPingDuluApiService() }
        )

        repository.clearSyncedCacheOnLogout()

        assertEquals(null, fakeDao.getTaskByIdOnce(100L))
        assertEquals(101L, fakeDao.getTaskByIdOnce(101L)?.id)
    }

    @Test
    fun repository_createQuickTask_persistsExplicitLabelsAndNoDueDateOrSla() = runTest {
        val fakeDao = InMemoryTaskDao(emptyList())
        val repository = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { FailingPingDuluApiService() }
        )

        val created = repository.createQuickTask(
            title = "AP Lobby Mati Total",
            category = TicketCategory.NET,
            priority = Priority.P1_CRITICAL,
            epochDay = 20776L,
            location = "Lobby Utama",
            scheduledTimeMinutes = 615,
            technicalNotes = "Cek PoE switch",
            labels = listOf("Wi-Fi", "Urgent")
        )

        assertEquals(listOf("Wi-Fi", "Urgent"), created.labels)
        assertEquals(null, created.dueDateMillis)
        assertEquals("", created.slaTargetTime)
        assertTrue(created.createdAtMillis > 0L)
        assertFalse(created.isSynced)
    }

    @Test
    fun repository_onlineMutationsCallPostStageTimerActionAndPatchChecklistAndMarkSynced() = runTest {
        val seed = SeedTaskData.createInitialTasks(baseEpochDay = 20776L)
        val fakeDao = InMemoryTaskDao(seed)
        val recordingApi = RecordingPingDuluApiService()
        val repository = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { recordingApi }
        )

        // 1. Online checklist toggle -> PATCH /api/tasks/59/checklist/103 with is_done = 1
        repository.toggleChecklistItem(taskId = 59L, checklistId = 103L)
        assertTrue(fakeDao.getTaskByIdOnce(59L)!!.isSynced)
        val checklistCall = recordingApi.capturedChecklistCalls.single()
        assertEquals(59L, checklistCall.first)
        assertEquals(103L, checklistCall.second)
        assertEquals(1, checklistCall.third.isDone)

        // 2. Online timer start on BACKLOG task 60 -> POST /stage (IN_PROGRESS) + POST /timer (start)
        repository.toggleTaskTimer(taskId = 60L)
        assertTrue(fakeDao.getTaskByIdOnce(60L)!!.isSynced)
        assertEquals(Stage.IN_PROGRESS.apiValue, recordingApi.capturedStageCalls[60L]?.stage)
        assertEquals(TimerActionRequestDto.ACTION_START, recordingApi.capturedTimerCalls[60L]?.action)

        // 3. Online markTaskCompleted on running task 60 -> POST /timer (pause) + POST /stage (DONE)
        repository.markTaskCompleted(taskId = 60L)
        val completed60 = fakeDao.getTaskByIdOnce(60L)!!
        assertEquals(Stage.DONE.apiValue, completed60.stage)
        assertFalse(completed60.isTimerRunning)
        assertTrue(completed60.isSynced)
        assertEquals(TimerActionRequestDto.ACTION_PAUSE, recordingApi.capturedTimerCalls[60L]?.action)
        assertEquals(Stage.DONE.apiValue, recordingApi.capturedStageCalls[60L]?.stage)
    }

    @Test
    fun taskDto_moshiParsesCreatedAtOptionalDueDateAssignedToIdAndLabels() {
        val moshi = NetworkModule.createMoshi()
        val adapter = moshi.adapter(TaskDto::class.java)

        val isoJson = """
            {
              "id": 91,
              "ticket_code": "NET-91",
              "title": "AP Lobby Mati Total",
              "project_or_category": "NET",
              "priority": "P1_CRITICAL",
              "stage": "BACKLOG",
              "created_at": "2026-11-20T19:15:00Z",
              "due_date": null,
              "assigned_to_id": 7,
              "labels": ["Wi-Fi", "Jaringan", "Urgent"]
            }
        """.trimIndent()

        val parsedIso = adapter.fromJson(isoJson)!!.toEntity(nowMillis = 1_000L)
        assertEquals(null, parsedIso.dueDateMillis)
        assertEquals(7L, parsedIso.assignedToId)
        assertEquals(listOf("Wi-Fi", "Jaringan", "Urgent"), parsedIso.labels)
        assertEquals(
            com.example.domain.model.DateEpochUtil.parseTimestampMillisOrNull("2026-11-20T19:15:00Z"),
            parsedIso.createdAtMillis
        )
        assertEquals(
            com.example.domain.model.DateEpochUtil.epochDayFromMillis(parsedIso.createdAtMillis),
            parsedIso.scheduledDateEpochDay
        )

        val numericEpochJson = """
            {
              "id": 92,
              "title": "Switch Core Hang",
              "created_at": 1795113557000
            }
        """.trimIndent()
        val parsedNumeric = adapter.fromJson(numericEpochJson)!!.toEntity(nowMillis = 1_000L)
        assertEquals(1795113557000L, parsedNumeric.createdAtMillis)
        assertEquals(null, parsedNumeric.dueDateMillis)
    }
}

private class RecordingPingDuluApiService(
    private val loginHandler: (LoginRequestDto) -> Response<LoginResponseDto> = {
        Response.success(LoginResponseDto(token = "token"))
    },
    private val tasksResponse: Response<TaskResponseDto> = Response.success(TaskResponseDto()),
    private val failingTaskIds: Set<Long> = emptySet()
) : PingDuluApiService {
    val capturedLoginRequests = mutableListOf<LoginRequestDto>()
    val capturedStageCalls = mutableMapOf<Long, StageUpdateDto>()
    val capturedTimerCalls = mutableMapOf<Long, TimerActionRequestDto>()
    val capturedChecklistCalls = mutableListOf<Triple<Long, Long, ChecklistStepToggleDto>>()

    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> {
        capturedLoginRequests += body
        return loginHandler(body)
    }

    override suspend fun getUsersRaw(): Response<okhttp3.ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun getLabelsRaw(): Response<okhttp3.ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun getTasks(): Response<TaskResponseDto> = tasksResponse

    override suspend fun getTaskDetailRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.error(404, "not found".toResponseBody())

    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> =
        Response.error(500, "not stubbed".toResponseBody())

    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> {
        if (id in failingTaskIds) return Response.error(500, "fail".toResponseBody())
        capturedStageCalls[id] = body
        return Response.success(Unit)
    }

    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> {
        if (id in failingTaskIds) return Response.error(500, "fail".toResponseBody())
        capturedTimerCalls[id] = body
        return Response.success(Unit)
    }

    override suspend fun toggleChecklistStep(
        id: Long,
        stepId: Long,
        body: ChecklistStepToggleDto
    ): Response<Unit> {
        if (id in failingTaskIds) return Response.error(500, "fail".toResponseBody())
        capturedChecklistCalls += Triple(id, stepId, body)
        return Response.success(Unit)
    }

    override suspend fun patchTask(
        id: Long,
        body: com.example.data.remote.dto.TaskPatchRequestDto
    ): Response<okhttp3.ResponseBody> = Response.success("{}".toResponseBody())

    override suspend fun getAttachmentsRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun uploadAttachment(
        taskId: Long,
        file: okhttp3.MultipartBody.Part
    ): Response<okhttp3.ResponseBody> = Response.success("{}".toResponseBody())

    override suspend fun getCommentsRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun postComment(
        taskId: Long,
        body: com.example.data.remote.dto.AddCommentRequestDto
    ): Response<okhttp3.ResponseBody> = Response.success("{}".toResponseBody())

    override suspend fun getActivitiesRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun getActivitySingularRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.error(404, "{}".toResponseBody())

    override suspend fun getTaskLogsRaw(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.error(404, "{}".toResponseBody())

    override suspend fun workTask(taskId: Long): Response<com.example.data.remote.dto.WorkTaskResponseDto> =
        Response.success(com.example.data.remote.dto.WorkTaskResponseDto(success = true, stage = "IN_PROGRESS"))

    override suspend fun claimTask(taskId: Long): Response<com.example.data.remote.dto.ClaimTaskResponseDto> =
        Response.success(com.example.data.remote.dto.ClaimTaskResponseDto(success = true))

    override suspend fun deleteTask(taskId: Long): Response<okhttp3.ResponseBody> =
        Response.success("{}".toResponseBody())

    override suspend fun getHolidays(year: Int): Response<com.example.data.remote.dto.HolidayResponseDto> =
        Response.success(com.example.data.remote.dto.HolidayResponseDto(year = year))

    override suspend fun getCalendar(year: Int, month: Int): Response<com.example.data.remote.dto.CalendarAgendaResponseDto> =
        Response.success(com.example.data.remote.dto.CalendarAgendaResponseDto(year = year, month = month))

    override suspend fun addChecklistStep(taskId: Long, body: com.example.data.remote.dto.AddChecklistStepRequestDto): Response<okhttp3.ResponseBody> =
        Response.success("{}".toResponseBody())

    override suspend fun postSpeedTestRecord(body: com.example.data.remote.dto.SpeedTestRecordRequestDto): Response<okhttp3.ResponseBody> =
        Response.success("{}".toResponseBody())

    override suspend fun getSpeedTestRecordsRaw(): Response<okhttp3.ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun getMyProfile(): Response<com.example.data.remote.dto.AuthMeResponseDto> =
        Response.success(com.example.data.remote.dto.AuthMeResponseDto(success = true))

    override suspend fun uploadUserAvatar(userId: Long, file: okhttp3.MultipartBody.Part): Response<com.example.data.remote.dto.AvatarUploadResponseDto> =
        Response.success(com.example.data.remote.dto.AvatarUploadResponseDto(success = true))

    override suspend fun deleteUserAvatar(userId: Long): Response<com.example.data.remote.dto.UserMutationResponseDto> =
        Response.success(com.example.data.remote.dto.UserMutationResponseDto(success = true))

    override suspend fun updateUserProfile(userId: Long, request: com.example.data.remote.dto.UpdateUserRequestDto): Response<com.example.data.remote.dto.UserMutationResponseDto> =
        Response.success(com.example.data.remote.dto.UserMutationResponseDto(success = true))
}

private class FailingPingDuluApiService : PingDuluApiService {
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> = throw IOException("Offline")
    override suspend fun getUsersRaw(): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun getLabelsRaw(): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun getTasks(): Response<TaskResponseDto> = throw IOException("Offline")
    override suspend fun getTaskDetailRaw(taskId: Long): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> = throw IOException("Offline")
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = throw IOException("Offline")
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = throw IOException("Offline")
    override suspend fun toggleChecklistStep(
        id: Long,
        stepId: Long,
        body: ChecklistStepToggleDto
    ): Response<Unit> = throw IOException("Offline")

    override suspend fun patchTask(
        id: Long,
        body: com.example.data.remote.dto.TaskPatchRequestDto
    ): Response<okhttp3.ResponseBody> = throw IOException("Offline")

    override suspend fun getAttachmentsRaw(taskId: Long): Response<okhttp3.ResponseBody> = throw IOException("Offline")

    override suspend fun uploadAttachment(
        taskId: Long,
        file: okhttp3.MultipartBody.Part
    ): Response<okhttp3.ResponseBody> = throw IOException("Offline")

    override suspend fun getCommentsRaw(taskId: Long): Response<okhttp3.ResponseBody> = throw IOException("Offline")

    override suspend fun postComment(
        taskId: Long,
        body: com.example.data.remote.dto.AddCommentRequestDto
    ): Response<okhttp3.ResponseBody> = throw IOException("Offline")

    override suspend fun getActivitiesRaw(taskId: Long): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun getActivitySingularRaw(taskId: Long): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun getTaskLogsRaw(taskId: Long): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun workTask(taskId: Long): Response<com.example.data.remote.dto.WorkTaskResponseDto> = throw IOException("Offline")
    override suspend fun claimTask(taskId: Long): Response<com.example.data.remote.dto.ClaimTaskResponseDto> = throw IOException("Offline")
    override suspend fun deleteTask(taskId: Long): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun getHolidays(year: Int): Response<com.example.data.remote.dto.HolidayResponseDto> = throw IOException("Offline")
    override suspend fun getCalendar(year: Int, month: Int): Response<com.example.data.remote.dto.CalendarAgendaResponseDto> = throw IOException("Offline")
    override suspend fun addChecklistStep(taskId: Long, body: com.example.data.remote.dto.AddChecklistStepRequestDto): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun postSpeedTestRecord(body: com.example.data.remote.dto.SpeedTestRecordRequestDto): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun getSpeedTestRecordsRaw(): Response<okhttp3.ResponseBody> = throw IOException("Offline")
    override suspend fun getMyProfile(): Response<com.example.data.remote.dto.AuthMeResponseDto> = throw IOException("Offline")
    override suspend fun uploadUserAvatar(userId: Long, file: okhttp3.MultipartBody.Part): Response<com.example.data.remote.dto.AvatarUploadResponseDto> = throw IOException("Offline")
    override suspend fun deleteUserAvatar(userId: Long): Response<com.example.data.remote.dto.UserMutationResponseDto> = throw IOException("Offline")
    override suspend fun updateUserProfile(userId: Long, request: com.example.data.remote.dto.UpdateUserRequestDto): Response<com.example.data.remote.dto.UserMutationResponseDto> = throw IOException("Offline")
}

private class InMemoryTaskDao(initial: List<TaskEntity>) : TaskDao {
    private val state = MutableStateFlow(initial.associateBy { it.id })
    private val converters = TaskConverters()

    override fun getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>> =
        state.map { map -> map.values.filter { it.scheduledDateEpochDay == epochDay } }

    override fun getTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>> =
        state.map { map -> map.values.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun getAllTasks(): Flow<List<TaskEntity>> = state.map { it.values.toList() }

    override fun getActiveHeroTask(): Flow<TaskEntity?> =
        state.map { map -> map.values.firstOrNull { it.stage == Stage.IN_PROGRESS.apiValue } }

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

    override suspend fun updateChecklist(
        taskId: Long,
        checklistJson: String,
        isSynced: Boolean,
        updatedAtMillis: Long
    ) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (
            taskId to existing.copy(
                checklist = converters.toChecklistList(checklistJson),
                isSynced = isSynced,
                updatedAtMillis = updatedAtMillis
            )
            )
    }

    override suspend fun updateTaskTimer(
        taskId: Long,
        seconds: Long,
        isRunning: Boolean,
        timerStartedAtMillis: Long?,
        isSynced: Boolean,
        updatedAtMillis: Long
    ) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (
            taskId to existing.copy(
                trackedSeconds = seconds,
                isTimerRunning = isRunning,
                timerStartedAtMillis = timerStartedAtMillis,
                isSynced = isSynced,
                updatedAtMillis = updatedAtMillis
            )
            )
    }

    override suspend fun pauseAllOtherTimers(excludeTaskId: Long, updatedAtMillis: Long) {
        state.value = state.value.mapValues { (id, entity) ->
            if (id != excludeTaskId && entity.isTimerRunning) {
                entity.copy(
                    isTimerRunning = false,
                    timerStartedAtMillis = null,
                    isSynced = false,
                    updatedAtMillis = updatedAtMillis
                )
            } else {
                entity
            }
        }
    }

    override suspend fun updateTaskStage(
        taskId: Long,
        stage: String,
        isRunning: Boolean,
        timerStartedAtMillis: Long?,
        isSynced: Boolean,
        updatedAtMillis: Long
    ) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (
            taskId to existing.copy(
                stage = stage,
                isTimerRunning = isRunning,
                timerStartedAtMillis = timerStartedAtMillis,
                isSynced = isSynced,
                updatedAtMillis = updatedAtMillis
            )
            )
    }

    override suspend fun markTaskSynced(taskId: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(isSynced = true))
    }

    override suspend fun getTaskByClientSyncId(clientSyncId: String): TaskEntity? {
        return state.value.values.firstOrNull { it.clientSyncId == clientSyncId }
    }

    override suspend fun deleteTaskById(taskId: Long) {
        state.value = state.value - taskId
    }

    override suspend fun deletePendingByClientSyncId(clientSyncId: String) {
        state.value = state.value.filterValues { entity ->
            !(entity.id < 0L && entity.clientSyncId == clientSyncId)
        }
    }

    override suspend fun deleteStaleSyncedTasks(activeIds: List<Long>) {
        val activeSet = activeIds.toSet()
        state.value = state.value.filterValues { entity ->
            !entity.isSynced || entity.id in activeSet
        }
    }

    override suspend fun deleteAllSyncedTasks() {
        state.value = state.value.filterValues { entity -> !entity.isSynced }
    }
}
