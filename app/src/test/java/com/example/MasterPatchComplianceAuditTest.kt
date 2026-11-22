package com.example

import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.remote.NetworkModule
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.dto.AddCommentRequestDto
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDetailResponseParser
import com.example.data.remote.dto.TaskDto
import com.example.data.remote.dto.TaskPatchRequestDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.remote.dto.toEntity
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.ChecklistItem
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MasterPatchComplianceAuditTest {

    @Test
    fun taskDto_fallsBackToAssigneeNameAndAssignedToId_andEnrichesFromUsersCatalog() = runTest {
        val moshi = NetworkModule.createMoshi()
        val adapter = moshi.adapter(TaskDto::class.java)

        // Case 1: Empty assignees array, but assigned_to_id + assignee_name provided
        val singleAssigneeJson = """
            {
              "id": 101,
              "ticket_code": "NET-101",
              "title": "Perbaikan Router Cabang",
              "project_or_category": "NET",
              "scheduled_date_epoch_day": 20776,
              "priority": "P1_CRITICAL",
              "stage": "BACKLOG",
              "assigned_to_id": 5,
              "assignee_name": "Budi Santoso",
              "assignees": [],
              "due_date": null
            }
        """.trimIndent()
        val parsed1 = adapter.fromJson(singleAssigneeJson)
        assertNotNull(parsed1)
        val entity1 = parsed1!!.toEntity(nowMillis = 1_700_000_000_000L)
        assertEquals(1, entity1.assignees.size)
        assertEquals(5L, entity1.assignees.first().id)
        assertEquals("Budi Santoso", entity1.assignees.first().name)
        assertTrue(entity1.assignees.first().isLead)
        assertEquals(5L, entity1.assignedToId)

        // Case 2: Only assignee_ids provided -> enriched from /api/users catalog during pull
        val dao = MasterPatchFakeDao()
        val api = MasterPatchFakeApiService(
            usersJson = """[{"id":7,"username":"fajar","display_name":"Fajar SP","role":"admin"},{"id":8,"username":"purnomo","display_name":"Purnomo","role":"technician"}]""",
            tasksProvider = {
                Response.success(
                    TaskResponseDto(
                        data = listOf(
                            TaskDto(
                                id = 102L,
                                ticketCode = "SRV-102",
                                title = "Audit UPS Ruang Server",
                                projectOrCategory = "SRV",
                                scheduledDateEpochDay = 20776L,
                                priority = "P2_HIGH",
                                stage = "IN_PROGRESS",
                                assigneeIds = listOf(7L, 8L),
                                assignees = emptyList()
                            )
                        )
                    )
                )
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })
        repo.refreshCatalogsFromServer()
        val syncResult = repo.pullTasksFromServerOnly()
        assertTrue(syncResult is SyncResult.Success)

        val stored = dao.getTaskByIdOnce(102L)
        assertNotNull(stored)
        assertEquals(listOf("Fajar SP", "Purnomo"), stored!!.assignees.map { it.name })
        assertTrue(stored.assignees[0].isLead)
        assertFalse(stored.assignees[1].isLead)
    }

    @Test
    fun taskDto_sortsChecklistBySortOrder_preservesUpdatedAt_andHandlesWaitingUserAndWorkItemLogs() {
        val moshi = NetworkModule.createMoshi()
        val adapter = moshi.adapter(TaskDto::class.java)

        val serverJson = """
            {
              "id": 202,
              "ticket_code": "HRD-202",
              "title": "Ganti SSD Laptop Finance",
              "project_or_category": "HRD",
              "scheduled_date_epoch_day": 20776,
              "priority": "P2_HIGH",
              "stage": "WAITING_USER",
              "created_at": 1700000001000,
              "updated_at": 1700000099000,
              "checklist": [
                { "id": 30, "label": "Langkah Ketiga", "is_done": 0, "sort_order": 2 },
                { "id": 10, "label": "Langkah Pertama", "is_done": 1, "sort_order": 0 },
                { "id": 20, "label": "Langkah Kedua", "is_done": true, "sort_order": 1 }
              ]
            }
        """.trimIndent()

        val entity = adapter.fromJson(serverJson)!!.toEntity(nowMillis = 1_700_000_500_000L)
        assertEquals(
            "Stage WAITING_USER tidak boleh jatuh ke BACKLOG",
            Stage.IN_PROGRESS.apiValue,
            entity.stage
        )
        assertEquals(1_700_000_099_000L, entity.updatedAtMillis)
        assertEquals(
            listOf("Langkah Pertama", "Langkah Kedua", "Langkah Ketiga"),
            entity.checklist.map { it.label }
        )
        assertEquals(listOf(true, true, false), entity.checklist.map { it.isDone })

        // ActivityLogDto format from Master Patch: work_item_id + user_name + message + WAITING_USER stage token
        val logsJson = """
            {
              "logs": [
                {
                  "id": 901,
                  "work_item_id": 202,
                  "user_name": "Fajar SP",
                  "action": "stage_changed",
                  "from_stage": "IN_PROGRESS",
                  "to_stage": "WAITING_USER",
                  "created_at": 1700000099000
                },
                {
                  "id": 902,
                  "work_item_id": 202,
                  "user_name": "Purnomo",
                  "message": "Menunggu user menyerahkan unit laptop",
                  "created_at": 1700000105000
                }
              ]
            }
        """.trimIndent()
        val parsedLogs = TaskDetailResponseParser.parseActivitiesList(logsJson, fallbackTaskId = 999L)
        assertEquals(2, parsedLogs.size)
        assertEquals(202L, parsedLogs[0].taskId)
        assertEquals("Purnomo", parsedLogs[0].actorName)
        assertEquals("Menunggu user menyerahkan unit laptop", parsedLogs[0].detail)
        assertEquals("Fajar SP", parsedLogs[1].actorName)
        assertEquals("Proses → Menunggu User", parsedLogs[1].detail)
    }

    @Test
    fun createTask_http200WithSuccessFalse_failsSafelyWithoutFalseSync() = runTest {
        val dao = MasterPatchFakeDao()
        val getTasksCalls = AtomicInteger(0)
        val api = MasterPatchFakeApiService(
            createHandler = {
                Response.success(
                    200,
                    CreateTaskResponseDto(
                        success = false,
                        message = "Slot jadwal bentrok di server"
                    )
                )
            },
            tasksProvider = {
                getTasksCalls.incrementAndGet()
                Response.success(TaskResponseDto(data = emptyList()))
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        val result = repo.createQuickTask(
            title = "Cek Switch Lantai 2",
            category = TicketCategory.NET,
            priority = Priority.P2_HIGH,
            epochDay = 20776L,
            location = "Lt 2"
        )

        assertFalse("HTTP 200 dengan success=false tidak boleh dianggap synced", result.isSynced)
        assertEquals("Slot jadwal bentrok di server", repo.getLastSyncError())
        assertEquals("Tidak boleh memicu fallback GET /api/tasks saat success=false", 0, getTasksCalls.get())
        assertEquals(1, dao.getUnsyncedCount().first())
    }

    @Test
    fun pushLocalEntity_skipsStaleChecklist404_soAntreSyncClearsCleanly() = runTest {
        val dao = MasterPatchFakeDao()
        dao.upsertTask(
            TaskEntity(
                id = 88L,
                ticketCode = "NET-88",
                title = "Perbaikan Fiber",
                category = "NET",
                scheduledDateEpochDay = 20776L,
                scheduledTimeMinutes = 540,
                timeSlotLabel = "09:00 - 10:30",
                priority = "P1_CRITICAL",
                stage = "IN_PROGRESS",
                technicalNotes = "",
                trackedSeconds = 300L,
                isTimerRunning = false,
                timerStartedAtMillis = null,
                location = "Site A",
                slaTargetTime = "",
                checklist = listOf(
                    ChecklistItem(id = 1001L, label = "Langkah lama dihapus di web", isDone = true),
                    ChecklistItem(id = 1002L, label = "Langkah aktif", isDone = true)
                ),
                assignees = emptyList(),
                isSynced = false,
                isBlocked = true,
                blockedReason = "Menunggu kunci panel",
                createdAtMillis = 1_700_000_000_000L,
                updatedAtMillis = 1_700_000_000_000L
            )
        )

        val patchCalls = AtomicInteger(0)
        val api = MasterPatchFakeApiService(
            checklistHandler = { _, stepId, _ ->
                if (stepId == 1001L) {
                    Response.error(404, ResponseBody.create(null, """{"error":"Checklist step not found"}"""))
                } else {
                    Response.success(Unit)
                }
            },
            patchHandler = { _, _ ->
                patchCalls.incrementAndGet()
                Response.success(ResponseBody.create(null, """{"success":true}"""))
            },
            tasksProvider = {
                Response.success(
                    TaskResponseDto(
                        data = listOf(
                            TaskDto(
                                id = 88L,
                                ticketCode = "NET-88",
                                title = "Perbaikan Fiber",
                                projectOrCategory = "NET",
                                scheduledDateEpochDay = 20776L,
                                priority = "P1_CRITICAL",
                                stage = "IN_PROGRESS",
                                isBlocked = 1,
                                blockedReason = "Menunggu kunci panel"
                            )
                        )
                    )
                )
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        val result = repo.refreshTasksFromServer()
        assertTrue(result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertEquals(1, success.uploadedCount)
        assertEquals(0, success.failedUploadCount)
        assertEquals("Badge Antre Sync harus kosong setelah 404 checklist dilewati", 0, dao.getUnsyncedCount().first())
        assertEquals("Offline blocked/trackedSeconds wajib ikut di-patch saat sync", 1, patchCalls.get())
    }
}

private class MasterPatchFakeApiService(
    private val usersJson: String = "[]",
    private val createHandler: suspend (CreateTaskRequestDto) -> Response<CreateTaskResponseDto> = {
        Response.success(CreateTaskResponseDto(success = true, id = 1L))
    },
    private val tasksProvider: suspend () -> Response<TaskResponseDto> = {
        Response.success(TaskResponseDto(data = emptyList()))
    },
    private val checklistHandler: suspend (Long, Long, ChecklistStepToggleDto) -> Response<Unit> = { _, _, _ ->
        Response.success(Unit)
    },
    private val patchHandler: suspend (Long, TaskPatchRequestDto) -> Response<ResponseBody> = { _, _ ->
        Response.success(ResponseBody.create(null, "{}"))
    }
) : PingDuluApiService {
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> =
        Response.success(LoginResponseDto(token = "token"))
    override suspend fun getUsersRaw(): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, usersJson))
    override suspend fun getLabelsRaw(): Response<ResponseBody> =
        Response.success(ResponseBody.create(null, "[]"))
    override suspend fun getTasks(): Response<TaskResponseDto> = tasksProvider()
    override suspend fun getTaskDetailRaw(taskId: Long): Response<ResponseBody> =
        Response.error(404, ResponseBody.create(null, "{}"))
    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> =
        createHandler(body)
    override suspend fun patchTask(id: Long, body: TaskPatchRequestDto): Response<ResponseBody> =
        patchHandler(id, body)
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = Response.success(Unit)
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = Response.success(Unit)
    override suspend fun toggleChecklistStep(id: Long, stepId: Long, body: ChecklistStepToggleDto): Response<Unit> =
        checklistHandler(id, stepId, body)
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

private class MasterPatchFakeDao : TaskDao {
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
