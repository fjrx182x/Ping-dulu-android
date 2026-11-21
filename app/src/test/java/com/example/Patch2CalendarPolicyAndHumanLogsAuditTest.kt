package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.dto.AddCommentRequestDto
import com.example.data.remote.dto.CalendarAgendaResponseDto
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.ClaimTaskResponseDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.HolidayItemDto
import com.example.data.remote.dto.HolidayResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDetailResponseParser
import com.example.data.remote.dto.TaskDto
import com.example.data.remote.dto.TaskPatchRequestDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.remote.dto.WorkTaskResponseDto
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.ActivityCategory
import com.example.domain.model.Assignee
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.IndonesianHolidayCatalog
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TaskPolicyHelper
import com.example.domain.model.TicketCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
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
import retrofit2.http.Body
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Patch2CalendarPolicyAndHumanLogsAuditTest {

    private fun baseTask(
        id: Long = 100L,
        stage: Stage = Stage.BACKLOG,
        assignees: List<Assignee> = emptyList(),
        assignedToId: Long? = null,
        createdById: Long? = null,
        createdByName: String? = null,
        creatorClient: String? = null,
        creatorRole: String? = null
    ): Task = Task(
        id = id,
        ticketCode = "NET-$id",
        title = "Perbaikan Router Core Gedung A",
        category = TicketCategory.NET,
        scheduledDateEpochDay = 20778L,
        scheduledTimeMinutes = 540,
        timeSlotLabel = "09:00",
        priority = Priority.P1_CRITICAL,
        stage = stage,
        technicalNotes = "",
        trackedSeconds = 0L,
        isTimerRunning = false,
        timerStartedAtMillis = null,
        location = "Server Room Lt. 2",
        slaTargetTime = "14:00",
        checklist = emptyList(),
        assignees = assignees,
        isSynced = true,
        dueDateMillis = null,
        assignedToId = assignedToId,
        createdById = createdById,
        createdByName = createdByName,
        creatorClient = creatorClient,
        creatorRole = creatorRole
    )

    @Test
    fun taskPolicyHelper_fullPermissionMatrixEnforcesAndroidCreatorAndWebAssigneeRules() {
        val techBudi = Assignee(id = 10L, name = "Budi", role = "technician")
        val techAndi = Assignee(id = 11L, name = "Andi", role = "technician")

        // 1. Ticket created by technician on Android -> only creator or admin can complete/delete
        val androidTechTicket = baseTask(
            id = 1L,
            assignees = listOf(techBudi, techAndi),
            assignedToId = 10L,
            createdById = 10L,
            createdByName = "Budi",
            creatorClient = "android",
            creatorRole = "technician"
        )
        assertTrue(TaskPolicyHelper.canCompleteOrDelete(androidTechTicket, 10L, "Budi", "technician"))
        assertFalse(TaskPolicyHelper.canCompleteOrDelete(androidTechTicket, 11L, "Andi", "technician"))
        assertTrue(TaskPolicyHelper.canCompleteOrDelete(androidTechTicket, 1L, "Fajar", "admin"))
        assertTrue(TaskPolicyHelper.unauthorizedReason(androidTechTicket).contains("Budi"))

        // 2. Ticket created by admin on Web -> assigned technician or admin can complete/delete; unassigned tech cannot
        val webAdminTicket = baseTask(
            id = 2L,
            assignees = listOf(techAndi),
            assignedToId = 11L,
            createdById = 1L,
            createdByName = "Fajar",
            creatorClient = "web",
            creatorRole = "admin"
        )
        assertTrue(TaskPolicyHelper.canCompleteOrDelete(webAdminTicket, 11L, "Andi", "technician"))
        assertFalse(TaskPolicyHelper.canCompleteOrDelete(webAdminTicket, 10L, "Budi", "technician"))
        assertTrue(TaskPolicyHelper.canCompleteOrDelete(webAdminTicket, 1L, "Fajar", "admin"))

        // 3. Unassigned ticket -> isUnassigned = true, tech cannot complete until claimed, admin still allowed
        val unassignedTicket = baseTask(
            id = 3L,
            assignees = emptyList(),
            assignedToId = null,
            createdById = 1L,
            createdByName = "Fajar",
            creatorClient = "web",
            creatorRole = "admin"
        )
        assertTrue(TaskPolicyHelper.isUnassigned(unassignedTicket))
        assertFalse(TaskPolicyHelper.canCompleteOrDelete(unassignedTicket, 10L, "Budi", "technician"))
        assertTrue(TaskPolicyHelper.canCompleteOrDelete(unassignedTicket, 1L, "Fajar", "admin"))
        assertTrue(TaskPolicyHelper.unauthorizedReason(unassignedTicket).contains("Ambil Task"))
    }

    @Test
    fun repository_workTaskMovesBacklogToInProgressAndRetrofitMethodHasNoEmptyBody() = runTest {
        // Verify Retrofit signature has NO @Body parameter (Fastify empty-body safety)
        val workMethod = PingDuluApiService::class.java.declaredMethods.first { it.name == "workTask" }
        val claimMethod = PingDuluApiService::class.java.declaredMethods.first { it.name == "claimTask" }
        assertTrue(workMethod.parameterAnnotations.flatten().none { it is Body })
        assertTrue(claimMethod.parameterAnnotations.flatten().none { it is Body })

        val dao = Patch2InMemoryTaskDao(
            listOf(baseTask(id = 77L, stage = Stage.BACKLOG).toEntity())
        )
        val api = Patch2FakeApiService(
            onWork = {
                Response.success(
                    WorkTaskResponseDto(
                        success = true,
                        stage = "IN_PROGRESS",
                        isCollaborator = false,
                        message = "Tiket mulai dikerjakan oleh Budi."
                    )
                )
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        val result = repo.workTask(77L)
        assertTrue(result.isSuccess)
        assertEquals("Tiket mulai dikerjakan oleh Budi.", result.getOrNull())
        val updated = dao.getTaskByIdOnce(77L)!!
        assertEquals(Stage.IN_PROGRESS.apiValue, updated.stage)
        assertTrue(updated.isSynced)
    }

    @Test
    fun repository_claimTaskUpdatesAssigneesAndUnlocksPolicy() = runTest {
        val dao = Patch2InMemoryTaskDao(
            listOf(
                baseTask(
                    id = 88L,
                    stage = Stage.BACKLOG,
                    assignees = emptyList(),
                    assignedToId = null,
                    createdById = 1L,
                    createdByName = "Fajar",
                    creatorClient = "web",
                    creatorRole = "admin"
                ).toEntity()
            )
        )
        val api = Patch2FakeApiService(
            onClaim = {
                Response.success(
                    ClaimTaskResponseDto(
                        success = true,
                        message = "Tiket berhasil diambil oleh Budi.",
                        task = TaskDto(
                            id = 88L,
                            ticketCode = "NET-88",
                            title = "Perbaikan Router Core Gedung A",
                            stage = "BACKLOG",
                            assignedToId = 10L,
                            assignedToName = "Budi",
                            createdById = 1L,
                            createdByName = "Fajar",
                            creatorClient = "web",
                            creatorRole = "admin"
                        )
                    )
                )
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        val result = repo.claimTask(88L)
        assertTrue(result.isSuccess)
        val claimed = dao.getTaskByIdOnce(88L)!!.toDomain()
        assertFalse(TaskPolicyHelper.isUnassigned(claimed))
        assertEquals(10L, claimed.assignedToId)
        assertTrue(claimed.assignees.any { it.name == "Budi" })
        assertTrue(TaskPolicyHelper.canCompleteOrDelete(claimed, 10L, "Budi", "technician"))
    }

    @Test
    fun repository_http403PolicyForbiddenRollsBackRoomCleanlyOnCompleteAndDelete() = runTest {
        val initialEntity = baseTask(
            id = 91L,
            stage = Stage.IN_PROGRESS,
            assignees = listOf(Assignee(10L, "Budi", "technician")),
            assignedToId = 10L,
            createdById = 10L,
            createdByName = "Budi",
            creatorClient = "android",
            creatorRole = "technician"
        ).toEntity()
        val dao = Patch2InMemoryTaskDao(listOf(initialEntity))
        val forbiddenJson = """{"error":"POLICY_FORBIDDEN","message":"Hanya pembuat tiket (Budi) atau Admin yang boleh menyelesaikan/menghapus tiket ini."}"""
        val api = Patch2FakeApiService(
            onUpdateStage = { _, _ ->
                Response.error(403, forbiddenJson.toResponseBody("application/json".toMediaType()))
            },
            onDelete = { _ ->
                Response.error(403, forbiddenJson.toResponseBody("application/json".toMediaType()))
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        // 1. Complete rejected with 403 -> must rollback stage to IN_PROGRESS and keep isSynced = true (0 unsynced)
        val completeRes = repo.completeTaskWithPolicy(91L)
        assertTrue(completeRes.isFailure)
        assertTrue(completeRes.exceptionOrNull()?.message?.contains("Budi") == true)
        val afterComplete = dao.getTaskByIdOnce(91L)!!
        assertEquals(Stage.IN_PROGRESS.apiValue, afterComplete.stage)
        assertTrue(afterComplete.isSynced)
        assertEquals(0, dao.getUnsyncedTasks().size)

        // 2. Delete rejected with 403 -> row must remain intact in Room
        val deleteRes = repo.deleteTask(91L)
        assertTrue(deleteRes.isFailure)
        assertTrue(deleteRes.exceptionOrNull()?.message?.contains("Budi") == true)
        assertNotNull(dao.getTaskByIdOnce(91L))
    }

    @Test
    fun repository_deleteTaskRemovesRowFromRoomOn200Ok() = runTest {
        val dao = Patch2InMemoryTaskDao(listOf(baseTask(id = 95L).toEntity()))
        val api = Patch2FakeApiService(
            onDelete = { Response.success("""{"success":true}""".toResponseBody()) }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        val deleteRes = repo.deleteTask(95L)
        assertTrue(deleteRes.isSuccess)
        assertNull(dao.getTaskByIdOnce(95L))
    }

    @Test
    fun calendarAndHolidays_serverFetchAndOfflineFallbackAndMonthMathWorkCorrectly() = runTest {
        // 1. Offline SKB 3 Menteri fallback when API fails
        val dao = Patch2InMemoryTaskDao(emptyList())
        val offlineApi = Patch2FakeApiService(
            onGetHolidays = { throw IOException("No internet") }
        )
        val offlineRepo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { offlineApi })
        val fallback2026 = offlineRepo.fetchHolidaysForYear(2026)
        assertTrue(fallback2026.isNotEmpty())
        val kemerdekaan = fallback2026.firstOrNull { it.date == "2026-08-17" }
        assertNotNull(kemerdekaan)
        assertTrue(kemerdekaan!!.name.contains("Kemerdekaan"))

        // 2. Server JSON parsing for holidays
        val serverApi = Patch2FakeApiService(
            onGetHolidays = {
                Response.success(
                    HolidayResponseDto(
                        year = 2026,
                        holidays = listOf(
                            HolidayItemDto(
                                date = "2026-12-25",
                                name = "Hari Raya Natal",
                                isCutiBersama = false
                            ),
                            HolidayItemDto(
                                date = "2026-12-24",
                                name = "Cuti Bersama Natal",
                                isCutiBersama = true
                            )
                        )
                    )
                )
            }
        )
        val onlineRepo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { serverApi })
        val fromServer = onlineRepo.fetchHolidaysForYear(2026)
        assertEquals(2, fromServer.size)
        assertTrue(fromServer.any { it.date == "2026-12-24" && it.isCutiBersama })

        // 3. DateEpochUtil calendar math (API 24 safe)
        assertEquals(28, DateEpochUtil.daysInMonth(2026, 2))
        assertEquals(30, DateEpochUtil.daysInMonth(2026, 11))
        assertEquals("2026-08-17", DateEpochUtil.toIsoDateString(DateEpochUtil.dateToEpochDay("2026-08-17")))
        assertTrue(IndonesianHolidayCatalog.holidaysForYear(2025).isNotEmpty())
        assertTrue(IndonesianHolidayCatalog.holidaysForYear(2027).isNotEmpty())
    }

    @Test
    fun humanizedActivityLogs_preserveNaturalIndonesianSentencesAndClassifyCategories() {
        val rawJson = """
            {
              "data": [
                {
                  "id": 1,
                  "task_id": 50,
                  "actor_name": "Budi",
                  "action": "Tiket dibuat oleh Budi",
                  "created_at": "2026-11-21T08:00:00Z"
                },
                {
                  "id": 2,
                  "task_id": 50,
                  "actor_name": "Andi",
                  "action": "📌 Andi mengambil tiket ini",
                  "created_at": "2026-11-21T08:15:00Z"
                },
                {
                  "id": 3,
                  "task_id": 50,
                  "actor_name": "Andi",
                  "action": "Tiket dikerjakan oleh Andi",
                  "created_at": "2026-11-21T08:20:00Z"
                },
                {
                  "id": 4,
                  "task_id": 50,
                  "actor_name": "Candra",
                  "action": "Candra bergabung mengerjakan tiket",
                  "created_at": "2026-11-21T08:30:00Z"
                },
                {
                  "id": 5,
                  "task_id": 50,
                  "actor_name": "Andi",
                  "action": "Langkah \"Cek Kabel Uplink\" diselesaikan oleh Andi",
                  "created_at": "2026-11-21T08:45:00Z"
                }
              ]
            }
        """.trimIndent()

        val activities = TaskDetailResponseParser.parseActivitiesList(rawJson, fallbackTaskId = 50L)
        assertEquals(5, activities.size)

        val byId = activities.associateBy { it.id }
        assertEquals("Tiket dibuat oleh Budi", byId[1L]?.summaryText)
        assertEquals(ActivityCategory.CREATED, byId[1L]?.category)

        assertEquals("📌 Andi mengambil tiket ini", byId[2L]?.summaryText)
        assertEquals(ActivityCategory.ASSIGNMENT, byId[2L]?.category)

        assertEquals("Tiket dikerjakan oleh Andi", byId[3L]?.summaryText)
        assertEquals(ActivityCategory.STAGE_CHANGED, byId[3L]?.category)

        assertEquals("Candra bergabung mengerjakan tiket", byId[4L]?.summaryText)
        assertEquals(ActivityCategory.ASSIGNMENT, byId[4L]?.category)

        assertEquals("Langkah \"Cek Kabel Uplink\" diselesaikan oleh Andi", byId[5L]?.summaryText)
        assertEquals(ActivityCategory.CHECKLIST, byId[5L]?.category)
    }

    @Test
    fun roomMigration7To8_preservesExistingTasksAndAddsCreatorMetadataColumns() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_v7_v8_${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)

        // 1. Create a real v7 database
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
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
                                `client_sync_id` TEXT,
                                `is_recurring` INTEGER NOT NULL DEFAULT 0,
                                `recurrence_interval` TEXT,
                                `is_blocked` INTEGER NOT NULL DEFAULT 0,
                                `blocked_reason` TEXT,
                                `snoozed_until_millis` INTEGER,
                                `attachments_json` TEXT NOT NULL DEFAULT '[]',
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_scheduled_date_epoch_day` ON `tasks` (`scheduled_date_epoch_day`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_stage` ON `tasks` (`stage`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_is_synced` ON `tasks` (`is_synced`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_client_sync_id` ON `tasks` (`client_sync_id`)")
                        db.execSQL(
                            """
                            INSERT INTO tasks (
                                id, ticket_code, title, category, scheduled_date_epoch_day,
                                scheduled_time_minutes, time_slot_label, priority, stage,
                                technical_notes, tracked_seconds, is_timer_running,
                                timer_started_at_millis, location, sla_target_time,
                                checklist_json, assignees_json, is_synced, created_at, due_date,
                                labels_json, updated_at_millis, attachments_json
                            ) VALUES (
                                701, 'NET-701', 'Tiket Eksisting V7', 'NET', 20778,
                                540, '09:00', 'P1_CRITICAL', 'IN_PROGRESS',
                                'Catatan lama', 0, 0,
                                NULL, 'Rack A', '12:00',
                                '[]', '[]', 1, 1700000000000, NULL,
                                '[]', 1700000000000, '[]'
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        // 2. Open with Room v8 + MIGRATION_7_8
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = roomDb.taskDao()
            val migrated = dao.getTaskByIdOnce(701L)
            assertNotNull(migrated)
            assertEquals("Tiket Eksisting V7", migrated?.title)
            assertNull(migrated?.createdById)
            assertNull(migrated?.createdByName)
            assertNull(migrated?.creatorClient)
            assertNull(migrated?.creatorRole)

            // 3. Persist and read back creator metadata
            dao.upsertTask(
                migrated!!.copy(
                    id = 702L,
                    ticketCode = "NET-702",
                    createdById = 10L,
                    createdByName = "Budi",
                    creatorClient = "android",
                    creatorRole = "technician"
                )
            )
            val saved = dao.getTaskByIdOnce(702L)!!
            assertEquals(10L, saved.createdById)
            assertEquals("Budi", saved.createdByName)
            assertEquals("android", saved.creatorClient)
            assertEquals("technician", saved.creatorRole)
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }
}

private class Patch2FakeApiService(
    private val onWork: suspend (Long) -> Response<WorkTaskResponseDto> = {
        Response.success(WorkTaskResponseDto(success = true, stage = "IN_PROGRESS"))
    },
    private val onClaim: suspend (Long) -> Response<ClaimTaskResponseDto> = {
        Response.success(ClaimTaskResponseDto(success = true))
    },
    private val onUpdateStage: suspend (Long, StageUpdateDto) -> Response<Unit> = { _, _ ->
        Response.success(Unit)
    },
    private val onDelete: suspend (Long) -> Response<ResponseBody> = {
        Response.success("{}".toResponseBody())
    },
    private val onGetHolidays: suspend (Int) -> Response<HolidayResponseDto> = { year ->
        Response.success(HolidayResponseDto(year = year))
    }
) : PingDuluApiService {
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> =
        Response.success(LoginResponseDto(token = "token"))
    override suspend fun getUsersRaw(): Response<ResponseBody> = Response.success("[]".toResponseBody())
    override suspend fun getLabelsRaw(): Response<ResponseBody> = Response.success("[]".toResponseBody())
    override suspend fun getTasks(): Response<TaskResponseDto> = Response.success(TaskResponseDto())
    override suspend fun getTaskDetailRaw(taskId: Long): Response<ResponseBody> = Response.error(404, "{}".toResponseBody())
    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> =
        Response.success(CreateTaskResponseDto(id = 1L))
    override suspend fun patchTask(id: Long, body: TaskPatchRequestDto): Response<ResponseBody> =
        Response.success("{}".toResponseBody())
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = onUpdateStage(id, body)
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = Response.success(Unit)
    override suspend fun toggleChecklistStep(id: Long, stepId: Long, body: ChecklistStepToggleDto): Response<Unit> =
        Response.success(Unit)
    override suspend fun getAttachmentsRaw(taskId: Long): Response<ResponseBody> = Response.success("[]".toResponseBody())
    override suspend fun uploadAttachment(taskId: Long, file: MultipartBody.Part): Response<ResponseBody> =
        Response.success("{}".toResponseBody())
    override suspend fun getCommentsRaw(taskId: Long): Response<ResponseBody> = Response.success("[]".toResponseBody())
    override suspend fun postComment(taskId: Long, body: AddCommentRequestDto): Response<ResponseBody> =
        Response.success("{}".toResponseBody())
    override suspend fun getActivitiesRaw(taskId: Long): Response<ResponseBody> = Response.success("[]".toResponseBody())
    override suspend fun getActivitySingularRaw(taskId: Long): Response<ResponseBody> = Response.error(404, "{}".toResponseBody())
    override suspend fun getTaskLogsRaw(taskId: Long): Response<ResponseBody> = Response.error(404, "{}".toResponseBody())
    override suspend fun workTask(taskId: Long): Response<WorkTaskResponseDto> = onWork(taskId)
    override suspend fun claimTask(taskId: Long): Response<ClaimTaskResponseDto> = onClaim(taskId)
    override suspend fun deleteTask(taskId: Long): Response<ResponseBody> = onDelete(taskId)
    override suspend fun getHolidays(year: Int): Response<HolidayResponseDto> = onGetHolidays(year)
    override suspend fun getCalendar(year: Int, month: Int): Response<CalendarAgendaResponseDto> =
        Response.success(CalendarAgendaResponseDto(year = year, month = month))
}

private class Patch2InMemoryTaskDao(initial: List<TaskEntity>) : TaskDao {
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
