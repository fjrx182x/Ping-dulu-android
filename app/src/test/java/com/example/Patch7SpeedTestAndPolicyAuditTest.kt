package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
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
import com.example.domain.model.ActiveTaskLimitExceededException
import com.example.domain.model.ActiveTaskLimitPolicy
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.FieldTechnician
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.tools.CbnSpeedGaugeMath
import com.example.domain.tools.FieldNetworkTools
import com.example.domain.tools.SpeedTestPhase
import com.example.presentation.components.SpeedTestSection
import com.example.presentation.components.TaskDetailBottomSheet
import com.example.ui.theme.PingDuluFieldOpsTheme
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Isolated Patch Ke-7 Verification Suite (in-memory Fake DAO + loopback HttpServer only;
 * NEVER touches or deletes real tasks).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Patch7SpeedTestAndPolicyAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val todayEpochDay = DateEpochUtil.toEpochDay(2026, 11, 22)

    @Test
    fun cbnSpeedGaugeMath_maps200MbpsToExact270DegreePeakAndClampsSafely() {
        assertEquals(0f, CbnSpeedGaugeMath.speedToSweepAngle(0f), 0.01f)
        assertEquals(0f, CbnSpeedGaugeMath.speedToSweepAngle(-50f), 0.01f)
        assertEquals(0f, CbnSpeedGaugeMath.speedToSweepAngle(Float.NaN), 0.01f)

        // 200 Mbps is tick index 4 out of 8 intervals -> sweep 133°, canvas angle 137 + 133 = 270° (12 o'clock!)
        assertEquals(133f, CbnSpeedGaugeMath.speedToSweepAngle(200f), 0.01f)
        assertEquals(270f, CbnSpeedGaugeMath.speedToCanvasAngle(200f), 0.01f)

        // 1g (1000 Mbps) maps to full 266° sweep; values above 1g clamp safely
        assertEquals(266f, CbnSpeedGaugeMath.speedToSweepAngle(1000f), 0.01f)
        assertEquals(266f, CbnSpeedGaugeMath.speedToSweepAngle(2500f), 0.01f)

        // Midpoint between 100 Mbps (idx 3) and 200 Mbps (idx 4) -> 3.5 * 33.25 = 116.375°
        assertEquals(116.375f, CbnSpeedGaugeMath.speedToSweepAngle(150f), 0.05f)
    }

    @Test
    fun speedTestEngine_emitsSparklinesAndPostsRecordsBestEffortWithBearerAuth() = runBlocking {
        val postedRecordBody = AtomicReference<String?>()
        val postedRecordAuth = AtomicReference<String?>()
        val returnRecords500 = AtomicInteger(0)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/speedtest/ip") { ex ->
            val payload = """{"rawIp":"103.20.10.5","isp_name":"Iconnet","processedString":"103.20.10.5 - Iconnet"}""".toByteArray()
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        }
        server.createContext("/api/speedtest/empty") { ex ->
            ex.requestBody.use { it.readBytes() }
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.close()
        }
        server.createContext("/api/speedtest/garbage") { ex ->
            val chunk = ByteArray(256 * 1024) { 0x5A }
            ex.sendResponseHeaders(200, chunk.size.toLong())
            ex.responseBody.use { it.write(chunk) }
        }
        server.createContext("/api/speedtest/records") { ex ->
            postedRecordAuth.set(ex.requestHeaders.getFirst("Authorization"))
            postedRecordBody.set(ex.requestBody.bufferedReader().use { it.readText() })
            if (returnRecords500.get() == 1) {
                ex.sendResponseHeaders(500, 0)
            } else {
                val ok = """{"success":true,"id":77}""".toByteArray()
                ex.sendResponseHeaders(201, ok.size.toLong())
                ex.responseBody.use { it.write(ok) }
            }
            ex.responseBody.close()
        }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val seenPhases = mutableSetOf<SpeedTestPhase>()

            // Run 1: /api/speedtest/records returns 201 -> savedToServer == true
            val res1 = FieldNetworkTools.runSpeedTest(
                serverBaseUrl = baseUrl,
                authToken = "jwt-patch7-token",
                pingSamples = 4,
                downloadDurationMs = 350L,
                uploadDurationMs = 350L,
                uploadChunkBytes = 32 * 1024,
                onProgress = { seenPhases.add(it.phase) }
            )

            assertTrue("Expected success: ${res1.errorMessage}", res1.isSuccess)
            assertTrue("Expected savedToServer=true", res1.savedToServer)
            assertEquals("103.20.10.5", res1.clientIp)
            assertEquals("Iconnet", res1.ispName)
            assertTrue("Expected non-empty download sparkline", res1.downloadSamples.isNotEmpty())
            assertTrue("Expected non-empty upload sparkline", res1.uploadSamples.isNotEmpty())
            assertTrue("Expected JITTER phase visited", SpeedTestPhase.JITTER in seenPhases)
            assertEquals("Bearer jwt-patch7-token", postedRecordAuth.get())
            assertTrue(postedRecordBody.get().orEmpty().contains("\"download_mbps\""))
            assertTrue(postedRecordBody.get().orEmpty().contains("\"isp_name\":\"Iconnet\""))

            // Run 2: /api/speedtest/records returns 500 -> test STILL succeeds (best-effort)
            returnRecords500.set(1)
            val res2 = FieldNetworkTools.runSpeedTest(
                serverBaseUrl = baseUrl,
                authToken = "jwt-patch7-token",
                pingSamples = 3,
                downloadDurationMs = 300L,
                uploadDurationMs = 300L,
                uploadChunkBytes = 32 * 1024
            )
            assertTrue("Speedtest must still succeed when record save returns 500", res2.isSuccess)
            assertFalse("savedToServer must be false when record save fails", res2.savedToServer)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun repository_preservesChecklistOnClaimAndWork_andRollsBackOn409ClaimLimit() = runBlocking {
        val existingChecklist = listOf(
            ChecklistItem(id = 501L, label = "Cek redaman ODP", isDone = true),
            ChecklistItem(id = 502L, label = "Uji ping gateway", isDone = false)
        )
        val initialEntity = TaskEntity(
            id = 88L,
            ticketCode = "NET-88",
            title = "Audit Link Metro",
            category = "NET",
            scheduledDateEpochDay = todayEpochDay,
            scheduledTimeMinutes = 540,
            timeSlotLabel = "09:00",
            priority = "P2",
            stage = Stage.BACKLOG.apiValue,
            technicalNotes = "Catatan uji",
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = "POP Barat",
            slaTargetTime = "",
            checklist = existingChecklist,
            assignees = listOf(Assignee(10L, "Budi", "technician", isLead = true)),
            isSynced = true,
            createdAtMillis = 1_700_000_000_000L,
            dueDateMillis = null,
            assignedToId = 10L,
            labels = listOf("Jaringan"),
            updatedAtMillis = 1_700_000_000_000L
        )

        val dao = InMemoryPatch7TaskDao(listOf(initialEntity))
        val api = FakePatch7ApiService()
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { api })

        // 1. workTask receives server task payload with empty checklist -> local checklist MUST remain intact!
        val workRes = repo.workTask(88L)
        assertTrue(workRes.isSuccess)
        assertEquals(2, dao.getTaskByIdOnce(88L)?.checklist?.size)

        // 2. claimTask receives server task payload with empty checklist -> local checklist MUST remain intact!
        val claimRes = repo.claimTask(88L)
        assertTrue(claimRes.isSuccess)
        assertEquals(2, dao.getTaskByIdOnce(88L)?.checklist?.size)

        // 3. addChecklistStep appends server-assigned positive step ID to Room
        val addRes = repo.addChecklistStep(88L, "Dokumentasi foto OTDR")
        assertTrue(addRes.isSuccess)
        assertEquals(901L, addRes.getOrNull()?.id)
        assertEquals(3, dao.getTaskByIdOnce(88L)?.checklist?.size)

        // 4. delegateTechnicians on HTTP 409 CLAIM_LIMIT_EXCEEDED rolls back Room & returns ActiveTaskLimitExceededException
        val assigneesBeforeDelegate = dao.getTaskByIdOnce(88L)?.assignees?.map { it.id }
        api.delegateReturn409Limit = true
        val delegateRes = repo.delegateTechnicians(88L, listOf(10L, 20L))
        assertTrue(delegateRes.isFailure)
        val delegateEx = delegateRes.exceptionOrNull() as? ActiveTaskLimitExceededException
        assertNotNull("Expected ActiveTaskLimitExceededException on 409", delegateEx)
        assertEquals(ActiveTaskLimitPolicy.DELEGATION_LIMIT_MESSAGE, delegateEx?.serverMessage)
        assertEquals(assigneesBeforeDelegate, dao.getTaskByIdOnce(88L)?.assignees?.map { it.id })

        // 5. createQuickTask on HTTP 409 CLAIM_LIMIT_EXCEEDED MUST NOT retry ticket_code & MUST delete temp local row!
        api.createReturn409Limit = true
        api.createCalls = 0
        var caughtLimitEx: ActiveTaskLimitExceededException? = null
        try {
            repo.createQuickTask(
                title = "Tiket ke-6 yang ditolak server",
                category = TicketCategory.NET,
                priority = Priority.P2_HIGH,
                epochDay = todayEpochDay,
                location = "Site Uji",
                assigneeIds = listOf(20L)
            )
        } catch (ex: ActiveTaskLimitExceededException) {
            caughtLimitEx = ex
        }
        assertNotNull("Expected ActiveTaskLimitExceededException from createQuickTask", caughtLimitEx)
        assertEquals("Must NOT retry ticket_code on CLAIM_LIMIT_EXCEEDED 409", 1, api.createCalls)
        assertEquals("Temp negative-ID task must be rolled back from Room", 1, dao.countAll())
    }

    @Test
    fun ui_cbnSpeedTestSectionRendersGaugeStepBarAndSparklinesCleanly() {
        composeRule.setContent {
            PingDuluFieldOpsTheme {
                SpeedTestSection(
                    serverHostHint = "https://ping.fjrx.my.id",
                    authToken = "demo-jwt"
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("speedtest_cbn_gauge").assertIsDisplayed()
        composeRule.onNodeWithTag("speedtest_step_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("sparkline_download").assertIsDisplayed()
        composeRule.onNodeWithTag("sparkline_upload").assertIsDisplayed()
        composeRule.onNodeWithTag("speedtest_run_button").assertIsDisplayed()
    }

    @Test
    fun ui_taskDetailBottomSheetSupportsAddChecklistAndDelegateTechnicianPicker() {
        val addedSteps = mutableListOf<String>()
        var delegatedIds: List<Long>? = null
        val activeTask = Task(
            id = 55L,
            ticketCode = "NET-55",
            title = "Perbaikan Router Distribusi",
            category = TicketCategory.NET,
            scheduledDateEpochDay = todayEpochDay,
            timeSlotLabel = "10:00",
            priority = Priority.P2_HIGH,
            stage = Stage.IN_PROGRESS,
            technicalNotes = "Cek BGP peer",
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = "NOC Lt.3",
            slaTargetTime = "",
            checklist = listOf(ChecklistItem(1L, "Cek power supply", isDone = false)),
            assignees = listOf(Assignee(10L, "Budi", "technician", isLead = true)),
            assignedToId = 10L
        )
        val techs = listOf(
            FieldTechnician(10L, "budi", "Budi", "technician"),
            FieldTechnician(20L, "andi", "Andi", "technician")
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                TaskDetailBottomSheet(
                    task = activeTask,
                    onDismiss = {},
                    onToggleChecklistItem = { _, _ -> },
                    onToggleTimer = {},
                    onMarkCompleted = {},
                    onReopenTask = {},
                    currentUserId = 10L,
                    currentUserName = "Budi",
                    currentUserRole = "technician",
                    availableTechnicians = techs,
                    onAddChecklistStep = { _, label -> addedSteps.add(label) },
                    onDelegateTechnicians = { _, ids -> delegatedIds = ids }
                )
            }
        }
        composeRule.waitForIdle()

        // Add checklist step via input + button
        composeRule.onNodeWithTag("sheet_add_checklist_input").performScrollTo().performTextInput("Uji redaman SFP")
        composeRule.onNodeWithTag("sheet_add_checklist_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("Uji redaman SFP"), addedSteps)

        // Open + Tambah Teknisi picker, select Andi (#20), and confirm
        composeRule.onNodeWithTag("sheet_add_technician_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delegate_technician_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("delegate_tech_option_20").performClick()
        composeRule.onNodeWithTag("delegate_tech_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(10L, 20L), delegatedIds)
    }
}

private class InMemoryPatch7TaskDao(initial: List<TaskEntity>) : TaskDao {
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
    override suspend fun deleteStaleSyncedTasks(activeIds: List<Long>) = Unit
    override suspend fun deleteAllSyncedTasks() { map.entries.removeAll { it.value.isSynced }; emit() }
}

private class FakePatch7ApiService : PingDuluApiService {
    var delegateReturn409Limit: Boolean = false
    var createReturn409Limit: Boolean = false
    var createCalls: Int = 0

    private val jsonMedia = "application/json".toMediaType()

    override suspend fun workTask(taskId: Long): Response<WorkTaskResponseDto> {
        return Response.success(
            WorkTaskResponseDto(
                success = true,
                stage = Stage.IN_PROGRESS.apiValue,
                task = TaskDto(
                    id = taskId,
                    ticketCode = "NET-$taskId",
                    title = "Audit Link Metro",
                    stage = Stage.IN_PROGRESS.apiValue,
                    checklist = emptyList() // Simulate partial payload with empty checklist
                )
            )
        )
    }

    override suspend fun claimTask(taskId: Long): Response<ClaimTaskResponseDto> {
        return Response.success(
            ClaimTaskResponseDto(
                success = true,
                task = TaskDto(
                    id = taskId,
                    ticketCode = "NET-$taskId",
                    title = "Audit Link Metro",
                    stage = Stage.BACKLOG.apiValue,
                    checklist = emptyList() // Simulate partial payload with empty checklist
                )
            )
        )
    }

    override suspend fun addChecklistStep(taskId: Long, body: AddChecklistStepRequestDto): Response<ResponseBody> {
        val raw = """{"step":{"id":901,"label":"${body.label}","is_done":0}}"""
        return Response.success(raw.toResponseBody(jsonMedia))
    }

    override suspend fun patchTask(id: Long, body: TaskPatchRequestDto): Response<ResponseBody> {
        if (delegateReturn409Limit) {
            val errJson = """{"error":"${ActiveTaskLimitPolicy.DELEGATION_LIMIT_MESSAGE}","limit":5,"active_count":5,"type":"CLAIM_LIMIT_EXCEEDED"}"""
            return Response.error(409, errJson.toResponseBody(jsonMedia))
        }
        return Response.success("""{"success":true}""".toResponseBody(jsonMedia))
    }

    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> {
        createCalls++
        if (createReturn409Limit) {
            val errJson = """{"error":"${ActiveTaskLimitPolicy.DELEGATION_LIMIT_MESSAGE}","limit":5,"active_count":5,"type":"CLAIM_LIMIT_EXCEEDED"}"""
            return Response.error(409, errJson.toResponseBody(jsonMedia))
        }
        return Response.success(CreateTaskResponseDto(success = true, id = 999L))
    }

    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> = error("unused")
    override suspend fun getUsersRaw(): Response<ResponseBody> = error("unused")
    override suspend fun getLabelsRaw(): Response<ResponseBody> = error("unused")
    override suspend fun getTasks(): Response<TaskResponseDto> = error("unused")
    override suspend fun getTaskDetailRaw(taskId: Long): Response<ResponseBody> = error("unused")
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = Response.success(Unit)
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = Response.success(Unit)
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
    override suspend fun postSpeedTestRecord(body: SpeedTestRecordRequestDto): Response<ResponseBody> = Response.success("""{"success":true}""".toResponseBody(jsonMedia))
}
