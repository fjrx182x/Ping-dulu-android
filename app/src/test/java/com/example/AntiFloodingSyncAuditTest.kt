package com.example

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.remote.NetworkModule
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.SseConnectionState
import com.example.data.remote.SseEvent
import com.example.data.remote.SseEventClient
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
import com.example.presentation.schedule.ScheduleViewModel
import com.example.util.ConnectivityMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AntiFloodingSyncAuditTest {

    @Test
    fun createTask_sendsUuidClientSyncId_parsesSuccessTaskAndDedupResponses_andClearsPendingQueue() = runTest {
        val moshi = NetworkModule.createMoshi()
        val adapter = moshi.adapter(CreateTaskResponseDto::class.java)

        // 1. Parse real PingDulu Fastify payload with boolean flags, object labels, explicit nulls, and dedup: true
        val trickyServerJson = """
            {
              "success": true,
              "dedup": true,
              "task": {
                "id": 77,
                "client_sync_id": "11111111-2222-4333-8444-555555555555",
                "ticket_code": "NET-77",
                "title": "Penarikan Kabel LAN",
                "project_or_category": "NET",
                "scheduled_date_epoch_day": 20776,
                "scheduled_time_minutes": null,
                "priority": "P2_HIGH",
                "stage": "BACKLOG",
                "location": "Ruang MTI",
                "due_date": null,
                "labels": [
                  { "id": 1, "name": "Jaringan", "color": "#36459B" },
                  "Kabel"
                ],
                "checklist": [
                  { "id": 10, "label": "Tarik UTP Cat6", "is_done": true },
                  { "id": 11, "label": null, "is_done": false }
                ],
                "assignees": [
                  { "id": 3, "username": "fajar", "display_name": "Fajar", "role": null, "is_lead": true }
                ]
              }
            }
        """.trimIndent()

        val parsed = adapter.fromJson(trickyServerJson)
        assertNotNull(parsed)
        assertEquals(true, parsed?.success)
        assertEquals(true, parsed?.dedup)

        // 2. Execute end-to-end repository createQuickTask and verify UUID client_sync_id + dequeue
        val dao = AntiFloodFakeDao()
        var capturedRequest: CreateTaskRequestDto? = null
        val api = AntiFloodFakeApiService(
            createHandler = { req ->
                capturedRequest = req
                val serverBody = parsed!!.copy(
                    task = parsed.task?.copy(clientSyncId = req.clientSyncId)
                )
                Response.success(200, serverBody)
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        val saved = repo.createQuickTask(
            title = "Penarikan Kabel LAN",
            category = TicketCategory.NET,
            priority = Priority.P2_HIGH,
            epochDay = 20776L,
            location = "Ruang MTI"
        )

        // Request MUST carry a valid UUID v4 client_sync_id
        val sentUuid = capturedRequest?.clientSyncId
        assertNotNull("client_sync_id wajib dikirim pada POST /api/tasks", sentUuid)
        assertEquals(sentUuid, UUID.fromString(sentUuid).toString())

        // Local negative-ID pending row MUST be replaced with server ID 77 & synced = true
        assertEquals(77L, saved.id)
        assertTrue(saved.isSynced)
        assertEquals(sentUuid, saved.clientSyncId)
        assertEquals(listOf("Jaringan", "Kabel"), saved.labels)
        assertTrue(saved.checklist[0].isDone)
        assertFalse(saved.checklist[1].isDone)
        assertEquals("Fajar", saved.assignees[0].name)
        assertTrue(saved.assignees[0].isLead)
        assertEquals(0, dao.getUnsyncedCount().first())
    }

    @Test
    fun pullTasksFromServerOnly_neverPostsPendingTasks_andReconcilesStuckRowsByClientSyncId() = runTest {
        val dao = AntiFloodFakeDao()
        val stuckUuid = UUID.randomUUID().toString()
        dao.upsertTask(
            TaskEntity(
                id = -1700000000001L,
                ticketCode = "NET-901",
                title = "Narik Kabel Printer Ruang MTI",
                category = "NET",
                scheduledDateEpochDay = 20776L,
                scheduledTimeMinutes = null,
                timeSlotLabel = "Hari Ini",
                priority = "P2_HIGH",
                stage = "BACKLOG",
                technicalNotes = "",
                trackedSeconds = 0L,
                isTimerRunning = false,
                timerStartedAtMillis = null,
                location = "Ruang MTI",
                slaTargetTime = "",
                checklist = emptyList(),
                assignees = emptyList(),
                isSynced = false,
                createdAtMillis = 1700000000001L,
                updatedAtMillis = 1700000000001L,
                clientSyncId = stuckUuid
            )
        )
        assertEquals(1, dao.getUnsyncedCount().first())

        val postCalls = AtomicInteger(0)
        var serverHasTask = false
        val api = AntiFloodFakeApiService(
            createHandler = {
                postCalls.incrementAndGet()
                Response.success(201, CreateTaskResponseDto(id = 999L))
            },
            getTasksHandler = {
                val list = if (serverHasTask) {
                    listOf(
                        TaskDto(
                            id = 55L,
                            clientSyncId = stuckUuid,
                            ticketCode = "NET-901",
                            title = "Narik Kabel Printer Ruang MTI",
                            projectOrCategory = "NET",
                            scheduledDateEpochDay = 20776L,
                            priority = "P2_HIGH",
                            stage = "BACKLOG",
                            location = "Ruang MTI"
                        )
                    )
                } else {
                    emptyList()
                }
                Response.success(TaskResponseDto(data = list))
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        // Pull #1: server does not have the task yet -> MUST NOT fire POST /api/tasks!
        val pull1 = repo.pullTasksFromServerOnly()
        assertTrue(pull1 is SyncResult.Success)
        assertEquals(0, postCalls.get())
        assertEquals(1, dao.getUnsyncedCount().first())

        // Pull #2: server already has the task with matching client_sync_id ->
        // MUST auto-reconcile negative-ID pending row WITHOUT ever calling POST /api/tasks!
        serverHasTask = true
        val pull2 = repo.pullTasksFromServerOnly()
        assertTrue(pull2 is SyncResult.Success)
        assertEquals(0, postCalls.get())
        assertEquals(0, dao.getUnsyncedCount().first())
        val allRows = dao.getAllTasks().first()
        assertEquals(1, allRows.size)
        assertEquals(55L, allRows.first().id)
        assertTrue(allRows.first().isSynced)
    }

    @Test
    fun sseEchoBurst_triggersDebouncedPullOnly_andNeverRePostsPendingQueue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val sseEventsFlow = MutableSharedFlow<SseEvent>(extraBufferCapacity = 16)
            val fakeSseClient = object : SseEventClient(OkHttpClient()) {
                override fun observeEvents(
                    baseUrlProvider: () -> String,
                    reconnectDelayMillis: Long,
                    maxReconnectDelayMillis: Long
                ): Flow<SseEvent> = sseEventsFlow
            }
            val fakeRepo = TrackingPullVsPushRepository(initialUnsyncedCount = 2)
            val vm = ScheduleViewModel(
                repository = fakeRepo,
                sseEventClient = fakeSseClient,
                baseEpochDayProvider = { 20776L },
                enableBackgroundTicker = false,
                enableSseStream = true,
                enableAutoSyncRetry = false
            )
            val collectorJob = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            // Simulate 5 rapid SSE echo events arriving within 300ms while 2 local tasks are stuck unsynced
            repeat(5) { idx ->
                sseEventsFlow.tryEmit(SseEvent.DataMessage("task_created", """{"id":$idx}"""))
                advanceTimeBy(60L)
            }

            // Before 1.5s debounce expires, zero pull/push calls should have fired
            assertEquals(0, fakeRepo.pullOnlyCalls.get())
            assertEquals(0, fakeRepo.fullPushPullCalls.get())

            // After debounce window passes, all 5 SSE events coalesce into exactly 1 PULL-ONLY call and ZERO pushes
            advanceTimeBy(ScheduleViewModel.SSE_PULL_DEBOUNCE_MILLIS + 200L)
            advanceUntilIdle()

            assertEquals(1, fakeRepo.pullOnlyCalls.get())
            assertEquals("SSE TIDAK BOLEH memicu full push/POST ulang!", 0, fakeRepo.fullPushPullCalls.get())

            vm.stopBackgroundJobs()
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun autoPushBackoff_respects15sMinimum_andCircuitBreaksOnPermanent4xxError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            assertTrue(
                "Backoff pertama minimal 15 detik agar tidak terjadi flooding",
                ScheduleViewModel.AUTO_PUSH_EXPONENTIAL_BACKOFF_MS.first() >= 15_000L
            )
            assertTrue(ScheduleViewModel.isPermanentSyncError("HTTP 400 (POST /api/tasks): Invalid payload"))
            assertTrue(ScheduleViewModel.isPermanentSyncError("HTTP 422 (POST /api/tasks): Validation failed"))
            assertFalse(ScheduleViewModel.isPermanentSyncError("HTTP 503 (POST /api/tasks): Service Unavailable"))
            assertFalse(ScheduleViewModel.isPermanentSyncError("Unable to resolve host ping.fjrx.my.id"))

            val fakeRepo = TrackingPullVsPushRepository(initialUnsyncedCount = 2)
            val onlineFlow = MutableStateFlow(true)
            val fakeMonitor = object : ConnectivityMonitor {
                override val isOnline: Flow<Boolean> = onlineFlow
            }
            val vm = ScheduleViewModel(
                repository = fakeRepo,
                connectivityMonitor = fakeMonitor,
                baseEpochDayProvider = { 20776L },
                enableBackgroundTicker = false,
                enableSseStream = false,
                enableAutoSyncRetry = true
            )
            val collectorJob = launch { vm.uiState.collect {} }
            advanceTimeBy(1_000L)

            // At 14 seconds: MUST still have 0 auto-retries (proves 1-second loop is gone!)
            advanceTimeBy(13_000L)
            assertEquals(0, fakeRepo.fullPushPullCalls.get())

            // Simulate permanent HTTP 400 validation error recorded by repository
            fakeRepo.lastError = "HTTP 400 (POST /api/tasks): Bad Request"
            advanceTimeBy(200_000L)
            advanceUntilIdle()

            // Circuit breaker MUST stop auto-retry completely when error is permanent 4xx
            assertEquals(0, fakeRepo.fullPushPullCalls.get())

            vm.stopBackgroundJobs()
            collectorJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun concurrentCreateAndRefresh_serializedBySyncMutex_neverDoublePostsSameTask() = runTest {
        val dao = AntiFloodFakeDao()
        val postCalls = AtomicInteger(0)
        val seenSyncIds = mutableListOf<String?>()

        val slowApi = AntiFloodFakeApiService(
            createHandler = { req ->
                val count = postCalls.incrementAndGet()
                seenSyncIds += req.clientSyncId
                delay(80L)
                Response.success(
                    201,
                    CreateTaskResponseDto(
                        success = true,
                        task = TaskDto(
                            id = 500L + count,
                            clientSyncId = req.clientSyncId,
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
            },
            getTasksHandler = {
                Response.success(
                    TaskResponseDto(
                        data = listOf(
                            TaskDto(
                                id = 501L,
                                clientSyncId = seenSyncIds.firstOrNull(),
                                ticketCode = "NET-10001",
                                title = "Pasang Switch Lantai 3",
                                projectOrCategory = "NET",
                                scheduledDateEpochDay = 20776L,
                                priority = "P1_CRITICAL",
                                stage = "BACKLOG",
                                location = "Lt 3"
                            )
                        )
                    )
                )
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { slowApi })

        val createDeferred = async {
            repo.createQuickTask(
                title = "Pasang Switch Lantai 3",
                category = TicketCategory.NET,
                priority = Priority.P1_CRITICAL,
                epochDay = 20776L,
                location = "Lt 3"
            )
        }
        // Trigger a concurrent refresh right as createQuickTask is mid-flight
        delay(10L)
        val refreshDeferred = async { repo.refreshTasksFromServer() }

        val created = createDeferred.await()
        refreshDeferred.await()

        assertEquals("Mutex wajib mencegah double-POST paralel untuk tiket yang sama!", 1, postCalls.get())
        assertTrue(created.isSynced)
        assertEquals(0, dao.getUnsyncedCount().first())
    }

    @Test
    fun roomMigration3To4_preservesExistingTasksAndAddsClientSyncIdColumnAndIndex() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration_v3_v4_audit_${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        // 1. Seed a real Room v3 SQLite schema on disk
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `tasks` (
                                `id` INTEGER NOT NULL,
                                `ticket_code` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `category` TEXT NOT NULL,
                                `scheduled_date_epoch_day` INTEGER NOT NULL,
                                `scheduled_time_minutes` INTEGER,
                                `time_slot_label` TEXT NOT NULL,
                                `priority` TEXT NOT NULL,
                                `stage` TEXT NOT NULL,
                                `technical_notes` TEXT NOT NULL,
                                `tracked_seconds` INTEGER NOT NULL,
                                `is_timer_running` INTEGER NOT NULL,
                                `timer_started_at_millis` INTEGER,
                                `location` TEXT NOT NULL,
                                `sla_target_time` TEXT NOT NULL,
                                `checklist_json` TEXT NOT NULL,
                                `assignees_json` TEXT NOT NULL,
                                `is_synced` INTEGER NOT NULL,
                                `created_at` INTEGER NOT NULL DEFAULT 0,
                                `due_date` INTEGER,
                                `assigned_to_id` INTEGER,
                                `labels_json` TEXT NOT NULL DEFAULT '[]',
                                `updated_at_millis` INTEGER NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_scheduled_date_epoch_day` ON `tasks` (`scheduled_date_epoch_day`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_stage` ON `tasks` (`stage`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_is_synced` ON `tasks` (`is_synced`)")
                        db.execSQL(
                            """
                            INSERT INTO `tasks` (
                                `id`, `ticket_code`, `title`, `category`,
                                `scheduled_date_epoch_day`, `scheduled_time_minutes`, `time_slot_label`,
                                `priority`, `stage`, `technical_notes`, `tracked_seconds`,
                                `is_timer_running`, `timer_started_at_millis`, `location`, `sla_target_time`,
                                `checklist_json`, `assignees_json`, `is_synced`, `created_at`,
                                `due_date`, `assigned_to_id`, `labels_json`, `updated_at_millis`
                            ) VALUES (
                                10, 'NET-10', 'Existing V3 Ticket', 'NET',
                                20776, NULL, 'Hari Ini',
                                'P2_HIGH', 'BACKLOG', 'Catatan lama', 0,
                                0, NULL, 'Server Room', '',
                                '[]', '[]', 1, 1700000000000,
                                NULL, 3, '["Jaringan"]', 1700000000000
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        openHelper.writableDatabase.close()
        openHelper.close()

        // 2. Open with current Room schema (v3 -> v4 -> v5 -> v6 -> v7)
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = roomDb.taskDao()
            val migrated = dao.getTaskByIdOnce(10L)
            assertNotNull(migrated)
            assertEquals("Existing V3 Ticket", migrated?.title)
            assertNull(migrated?.clientSyncId)

            val uuid = UUID.randomUUID().toString()
            dao.upsertTask(
                migrated!!.copy(
                    id = -100L,
                    ticketCode = "NET-LOCAL",
                    isSynced = false,
                    clientSyncId = uuid
                )
            )
            val found = dao.getTaskByClientSyncId(uuid)
            assertEquals(-100L, found?.id)

            dao.deletePendingByClientSyncId(uuid)
            assertNull(dao.getTaskByClientSyncId(uuid))
            assertNotNull(dao.getTaskByIdOnce(10L))
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }
}

private class AntiFloodFakeApiService(
    private val createHandler: suspend (CreateTaskRequestDto) -> Response<CreateTaskResponseDto>,
    private val getTasksHandler: suspend () -> Response<TaskResponseDto> = {
        Response.success(TaskResponseDto(data = emptyList()))
    }
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
    override suspend fun toggleChecklistStep(id: Long, stepId: Long, body: ChecklistStepToggleDto): Response<Unit> =
        Response.success(Unit)

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

    override suspend fun getSpeedTestRecordsRaw(): Response<okhttp3.ResponseBody> =
        Response.success(okhttp3.ResponseBody.create(null, "[]"))

    override suspend fun getMyProfile(): Response<com.example.data.remote.dto.AuthMeResponseDto> =
        Response.success(com.example.data.remote.dto.AuthMeResponseDto(success = true))

    override suspend fun uploadUserAvatar(userId: Long, file: okhttp3.MultipartBody.Part): Response<com.example.data.remote.dto.AvatarUploadResponseDto> =
        Response.success(com.example.data.remote.dto.AvatarUploadResponseDto(success = true))

    override suspend fun deleteUserAvatar(userId: Long): Response<com.example.data.remote.dto.UserMutationResponseDto> =
        Response.success(com.example.data.remote.dto.UserMutationResponseDto(success = true))

    override suspend fun updateUserProfile(userId: Long, request: com.example.data.remote.dto.UpdateUserRequestDto): Response<com.example.data.remote.dto.UserMutationResponseDto> =
        Response.success(com.example.data.remote.dto.UserMutationResponseDto(success = true))
}

private class TrackingPullVsPushRepository(
    initialUnsyncedCount: Int
) : TaskRepository {
    val fullPushPullCalls = AtomicInteger(0)
    val pullOnlyCalls = AtomicInteger(0)
    var lastError: String? = null
    private val unsyncedFlow = MutableStateFlow(initialUnsyncedCount)

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> = MutableStateFlow(emptyList())
    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> = MutableStateFlow(emptyList())
    override fun observeAllTasks(): Flow<List<Task>> = MutableStateFlow(emptyList())
    override fun observeActiveHeroTask(): Flow<Task?> = MutableStateFlow(null)
    override fun observeTaskById(taskId: Long): Flow<Task?> = MutableStateFlow(null)
    override fun observeUnsyncedCount(): Flow<Int> = unsyncedFlow
    override fun getLastSyncError(): String? = lastError

    override suspend fun initializeOfflineCache() = Unit
    override suspend fun refreshTasksFromServer(): SyncResult {
        fullPushPullCalls.incrementAndGet()
        return SyncResult.Success(downloadedCount = 0, uploadedCount = 0)
    }

    override suspend fun pullTasksFromServerOnly(): SyncResult {
        pullOnlyCalls.incrementAndGet()
        return SyncResult.Success(downloadedCount = 3, uploadedCount = 0)
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

private class AntiFloodFakeDao : TaskDao {
    private val state = MutableStateFlow<Map<Long, TaskEntity>>(emptyMap())
    private val converters = TaskConverters()

    override fun getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>> =
        state.map { map -> map.values.filter { it.scheduledDateEpochDay == epochDay } }

    override fun getTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>> =
        state.map { map -> map.values.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun getAllTasks(): Flow<List<TaskEntity>> =
        state.map { it.values.toList() }

    override fun getActiveHeroTask(): Flow<TaskEntity?> =
        state.map { map -> map.values.firstOrNull { it.stage == Stage.IN_PROGRESS.apiValue } }

    override fun getTaskById(taskId: Long): Flow<TaskEntity?> = state.map { it[taskId] }
    override suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? = state.value[taskId]
    override suspend fun getUnsyncedTasks(): List<TaskEntity> = state.value.values.filter { !it.isSynced }
    override fun getUnsyncedCount(): Flow<Int> = state.map { map -> map.values.count { !it.isSynced } }
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
        val activeSet = activeIds.toSet()
        state.value = state.value.filterValues { !it.isSynced || it.id in activeSet }
    }
    override suspend fun deleteAllSyncedTasks() {
        state.value = state.value.filterValues { !it.isSynced }
    }
}
