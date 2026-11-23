package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.dto.CatalogResponseParser
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
import com.example.domain.model.FieldTechnician
import com.example.domain.model.OfficialLabel
import com.example.domain.model.Priority
import com.example.domain.model.QuickTaskDraft
import com.example.domain.model.RecurrenceInterval
import com.example.domain.model.Stage
import com.example.domain.model.TicketCategory
import com.example.presentation.components.QuickAddTaskSheet
import com.example.ui.theme.PingDuluFieldOpsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Paket2QuickAddAndCatalogAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogParser_handlesBareArraysWrappedObjectsAndNullFieldsSafely() {
        val bareUsersJson = """
            [
              {"id": 3, "username": "fajar", "display_name": "Fajar Ramadhan", "role": "admin"},
              {"id": 5, "username": "budi", "name": null, "role": null}
            ]
        """.trimIndent()
        val bareUsers = CatalogResponseParser.parseUsersJson(bareUsersJson)
        assertEquals(2, bareUsers.size)
        assertEquals("Fajar Ramadhan", bareUsers[0].resolvedName)
        assertEquals("Admin", bareUsers[0].roleDisplayLabel)
        assertEquals("budi", bareUsers[1].resolvedName)
        assertEquals("Teknisi", bareUsers[1].roleDisplayLabel)

        val wrappedUsersJson = """
            {"users": [{"id": 9, "username": "citra", "name": "Citra NetOps", "role": "lead"}]}
        """.trimIndent()
        val wrappedUsers = CatalogResponseParser.parseUsersJson(wrappedUsersJson)
        assertEquals(1, wrappedUsers.size)
        assertEquals("Citra NetOps", wrappedUsers[0].resolvedName)
        assertEquals("Lead", wrappedUsers[0].roleDisplayLabel)

        val bareLabelsJson = """
            [
              {"id": 11, "name": "Jaringan", "color": "#36459B"},
              {"id": 12, "name": "Wi-Fi", "color_hex": "2563EB"}
            ]
        """.trimIndent()
        val bareLabels = CatalogResponseParser.parseLabelsJson(bareLabelsJson)
        assertEquals(2, bareLabels.size)
        assertEquals(11L, bareLabels[0].id)
        assertEquals("#36459B", bareLabels[0].colorHex)
        assertEquals("#2563EB", bareLabels[1].colorHex)

        val wrappedLabelsJson = """
            {"labels": [{"id": 15, "label": "Maintenance", "color": null}]}
        """.trimIndent()
        val wrappedLabels = CatalogResponseParser.parseLabelsJson(wrappedLabelsJson)
        assertEquals(1, wrappedLabels.size)
        assertEquals("Maintenance", wrappedLabels[0].name)
        assertEquals("#36459B", wrappedLabels[0].colorHex)
    }

    @Test
    fun repository_refreshCatalogsAndCreateQuickTask_sendsFullPaket2PayloadAndPreservesClientSyncId() = runTest {
        val capturedCreateRequests = mutableListOf<CreateTaskRequestDto>()
        val usersJson = """
            [
              {"id": 3, "username": "fajar", "display_name": "Fajar", "role": "admin"},
              {"id": 5, "username": "budi", "display_name": "Budi Santoso", "role": "technician"}
            ]
        """.trimIndent()
        val labelsJson = """
            {
              "labels": [
                {"id": 11, "name": "Jaringan", "color": "#36459B"},
                {"id": 12, "name": "Maintenance", "color": "#10B981"}
              ]
            }
        """.trimIndent()

        val fakeApi = Paket2FakeApiService(
            usersRawJson = usersJson,
            labelsRawJson = labelsJson,
            onCreateTask = { req ->
                capturedCreateRequests += req
                Response.success(
                    CreateTaskResponseDto(
                        id = 777L,
                        ticketCode = req.ticketCode,
                        task = TaskDto(
                            id = 777L,
                            ticketCode = req.ticketCode,
                            title = req.title,
                            projectOrCategory = req.projectOrCategory,
                            scheduledDateEpochDay = req.scheduledDateEpochDay,
                            scheduledTimeMinutes = req.scheduledTimeMinutes,
                            priority = req.priority,
                            stage = req.stage,
                            location = req.location,
                            technicalNotes = req.technicalNotes,
                            assignedToId = req.assignedToId,
                            labels = req.labels,
                            isRecurring = req.isRecurring,
                            recurrenceInterval = req.recurrenceInterval
                        )
                    )
                )
            }
        )
        val fakeDao = Paket2FakeDao()
        val repo = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { fakeApi },
            appPreferences = null
        )

        repo.refreshCatalogsFromServer()

        val technicians = repo.observeTechnicians().first()
        val officialLabels = repo.observeOfficialLabels().first()
        assertEquals(2, technicians.size)
        assertEquals("Budi Santoso", technicians[1].resolvedName)
        assertEquals(2, officialLabels.size)
        assertEquals(11L, officialLabels[0].id)

        val created = repo.createQuickTask(
            title = "Preventive Maintenance Core Switch",
            category = TicketCategory.NET,
            priority = Priority.P1_CRITICAL,
            epochDay = 20780L,
            location = "Data Center Lt. 2",
            scheduledTimeMinutes = 540,
            technicalNotes = "Bawa patch cord LC-LC",
            labels = listOf("Jaringan", "Maintenance"),
            assigneeIds = listOf(3L, 5L),
            labelIds = listOf(11L, 12L),
            checklistSteps = listOf("Cek redaman SFP", "Backup konfigurasi switch"),
            isRecurring = true,
            recurrenceInterval = RecurrenceInterval.WEEKLY.apiValue
        )

        assertEquals(1, capturedCreateRequests.size)
        val req = capturedCreateRequests.first()
        assertNotNull("client_sync_id UUID must be preserved", req.clientSyncId)
        UUID.fromString(req.clientSyncId!!) // validates UUID format
        assertEquals(3L, req.assignedToId)
        assertEquals(listOf(3L, 5L), req.assigneeIds)
        assertEquals(listOf("Jaringan", "Maintenance"), req.labels)
        assertEquals(listOf(11L, 12L), req.labelIds)
        assertEquals(listOf("Cek redaman SFP", "Backup konfigurasi switch"), req.checklist)
        assertEquals(1, req.isRecurring)
        assertEquals("WEEKLY", req.recurrenceInterval)

        assertTrue(created.isSynced)
        assertTrue(created.isRecurring)
        assertEquals("WEEKLY", created.recurrenceInterval)
        assertEquals(2, created.checklist.size)
        assertEquals("Cek redaman SFP", created.checklist[0].label)
    }

    @Test
    fun roomMigration_v4ToV5_preservesExistingDataAndPersistsRecurringColumns() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "pingdulu_v4_to_v5_audit_${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        // 1. Seed a genuine Room v4 SQLite table (before is_recurring / recurrence_interval existed)
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
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
                            INSERT INTO `tasks` VALUES (
                                42, 'NET-042', 'Tiket Eksisting V4', 'NET', 20780, 540, '09:00',
                                'P2_HIGH', 'BACKLOG', 'Catatan V4', 0, 0, NULL, 'Gedung B', '',
                                '[]', '[]', 1, 1795100000000, NULL, 3, '["Jaringan"]', 1795100000000,
                                '11111111-1111-4111-8111-111111111111'
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

        // 2. Open with Room v7 + MIGRATION_4_5 + MIGRATION_5_6 + MIGRATION_6_7
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
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
            val dao = db.taskDao()
            val migrated = dao.getTaskByIdOnce(42L)
            assertNotNull(migrated)
            assertEquals("Tiket Eksisting V4", migrated?.title)
            assertEquals("11111111-1111-4111-8111-111111111111", migrated?.clientSyncId)
            assertFalse(migrated!!.isRecurring)
            assertNull(migrated.recurrenceInterval)

            // 3. Persist and read back a recurring row
            dao.upsertTask(
                migrated.copy(
                    id = 43L,
                    ticketCode = "MNT-043",
                    isRecurring = true,
                    recurrenceInterval = "MONTHLY"
                )
            )
            val recurringRow = dao.getTaskByIdOnce(43L)
            assertNotNull(recurringRow)
            assertTrue(recurringRow!!.isRecurring)
            assertEquals("MONTHLY", recurringRow.recurrenceInterval)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun quickAddSheetUi_delegationLabelsChecklistAndAutoRepeatBuildCompleteDraft() {
        var submittedDraft: QuickTaskDraft? = null
        val technicians = listOf(
            FieldTechnician(id = 3L, username = "fajar", displayName = "Fajar", role = "admin"),
            FieldTechnician(id = 5L, username = "budi", displayName = "Budi", role = "technician")
        )
        val labels = listOf(
            OfficialLabel(id = 11L, name = "Jaringan", colorHex = "#36459B"),
            OfficialLabel(id = 12L, name = "Wi-Fi", colorHex = "#2563EB"),
            OfficialLabel(id = 13L, name = "Maintenance", colorHex = "#10B981")
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                QuickAddTaskSheet(
                    todayEpochDay = 20780L,
                    todayDateLabel = "Sab, 21 Nov 2026",
                    availableTechnicians = technicians,
                    officialLabels = labels,
                    currentUserId = 3L,
                    currentTechnicianName = "Fajar",
                    onDismiss = {},
                    onSubmitDraft = { submittedDraft = it }
                )
            }
        }

        // Enter title
        composeRule.onNodeWithTag("quick_add_title_input")
            .performScrollTo()
            .performTextInput("Audit Rack Switch Core")

        // Select second technician (Budi id=5) while Fajar (id=3) is auto-selected
        composeRule.onNodeWithTag("quick_add_assignee_5")
            .performScrollTo()
            .performClick()

        // Pick 2 official labels
        composeRule.onNodeWithTag("quick_add_label_jaringan")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("quick_add_label_maintenance")
            .performScrollTo()
            .performClick()

        // Enter multi-line custom checklist steps (including bullet/number prefixes to verify cleanup)
        composeRule.onNodeWithTag("quick_add_checklist_input")
            .performScrollTo()
            .performTextInput("1. Cek PSU\n- Cek fan\nCek uplink")

        // Enable Auto-Repeat and choose Mingguan (WEEKLY)
        composeRule.onNodeWithTag("quick_add_recurring_toggle")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("quick_add_recurrence_weekly")
            .performScrollTo()
            .performClick()

        // Submit
        composeRule.onNodeWithTag("quick_add_submit_button")
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()

        val draft = submittedDraft
        assertNotNull("QuickTaskDraft should be emitted on submit", draft)
        assertEquals("Audit Rack Switch Core", draft!!.title)
        assertEquals(setOf(3L, 5L), draft.assigneeIds.toSet())
        assertEquals(setOf("Jaringan", "Maintenance"), draft.labels.toSet())
        assertEquals(setOf(11L, 13L), draft.labelIds.toSet())
        assertEquals(listOf("Cek PSU", "Cek fan", "Cek uplink"), draft.checklistSteps)
        assertTrue(draft.isRecurring)
        assertEquals(RecurrenceInterval.WEEKLY, draft.recurrenceInterval)
    }
}

private class Paket2FakeApiService(
    private val usersRawJson: String,
    private val labelsRawJson: String,
    private val onCreateTask: (CreateTaskRequestDto) -> Response<CreateTaskResponseDto>
) : PingDuluApiService {
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> =
        Response.success(LoginResponseDto(token = "token"))

    override suspend fun getUsersRaw(): Response<ResponseBody> =
        Response.success(usersRawJson.toResponseBody())

    override suspend fun getLabelsRaw(): Response<ResponseBody> =
        Response.success(labelsRawJson.toResponseBody())

    override suspend fun getTasks(): Response<TaskResponseDto> =
        Response.success(TaskResponseDto(data = emptyList()))

    override suspend fun getTaskDetailRaw(taskId: Long): Response<ResponseBody> =
        Response.error(404, "{}".toResponseBody())

    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> =
        onCreateTask(body)

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
    ): Response<ResponseBody> = Response.success("{}".toResponseBody())

    override suspend fun getAttachmentsRaw(taskId: Long): Response<ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun uploadAttachment(
        taskId: Long,
        file: okhttp3.MultipartBody.Part
    ): Response<ResponseBody> = Response.success("{}".toResponseBody())

    override suspend fun getCommentsRaw(taskId: Long): Response<ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun postComment(
        taskId: Long,
        body: com.example.data.remote.dto.AddCommentRequestDto
    ): Response<ResponseBody> = Response.success("{}".toResponseBody())

    override suspend fun getActivitiesRaw(taskId: Long): Response<ResponseBody> =
        Response.success("[]".toResponseBody())

    override suspend fun getActivitySingularRaw(taskId: Long): Response<ResponseBody> =
        Response.error(404, "{}".toResponseBody())

    override suspend fun getTaskLogsRaw(taskId: Long): Response<ResponseBody> =
        Response.error(404, "{}".toResponseBody())

    override suspend fun workTask(taskId: Long): Response<com.example.data.remote.dto.WorkTaskResponseDto> =
        Response.success(com.example.data.remote.dto.WorkTaskResponseDto(success = true, stage = "IN_PROGRESS"))

    override suspend fun claimTask(taskId: Long): Response<com.example.data.remote.dto.ClaimTaskResponseDto> =
        Response.success(com.example.data.remote.dto.ClaimTaskResponseDto(success = true))

    override suspend fun deleteTask(taskId: Long): Response<ResponseBody> =
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

private class Paket2FakeDao : TaskDao {
    private val state = MutableStateFlow<Map<Long, TaskEntity>>(emptyMap())
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
    override suspend fun deleteTaskById(taskId: Long) {
        state.value = state.value - taskId
    }
    override suspend fun getTaskByClientSyncId(clientSyncId: String): TaskEntity? =
        state.value.values.firstOrNull { it.clientSyncId == clientSyncId }
    override suspend fun deletePendingByClientSyncId(clientSyncId: String) {
        state.value = state.value.filterValues { !(it.id < 0L && it.clientSyncId == clientSyncId) }
    }
    override suspend fun deleteStaleSyncedTasks(activeIds: List<Long>) {
        state.value = state.value.filterValues { !it.isSynced || it.id in activeIds }
    }
    override suspend fun deleteAllSyncedTasks() {
        state.value = state.value.filterValues { !it.isSynced }
    }
}
