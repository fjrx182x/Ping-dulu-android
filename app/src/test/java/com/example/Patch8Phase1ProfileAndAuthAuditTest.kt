package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppPreferences
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.dto.AddChecklistStepRequestDto
import com.example.data.remote.dto.AddCommentRequestDto
import com.example.data.remote.dto.AuthMeResponseDto
import com.example.data.remote.dto.AvatarUploadResponseDto
import com.example.data.remote.dto.CalendarAgendaResponseDto
import com.example.data.remote.dto.CatalogResponseParser
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.ClaimTaskResponseDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.HolidayResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.SpeedTestRecordRequestDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskPatchRequestDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.remote.dto.UpdateUserRequestDto
import com.example.data.remote.dto.UserDto
import com.example.data.remote.dto.UserMutationResponseDto
import com.example.data.remote.dto.WorkTaskResponseDto
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.AccountDeactivatedException
import com.example.domain.model.Assignee
import com.example.domain.model.DEACTIVATED_ACCOUNT_MESSAGE
import com.example.domain.model.Stage
import com.example.domain.model.resolveAvatarUrl
import com.example.presentation.schedule.ScheduleViewModel
import com.example.util.AvatarUploadHelper
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Patch8Phase1ProfileAndAuthAuditTest {

    @Test
    fun resolveAvatarUrl_handlesRelativeAbsoluteAndBlankInputs() {
        assertNull(resolveAvatarUrl("https://ping.fjrx.my.id/", null))
        assertNull(resolveAvatarUrl("https://ping.fjrx.my.id/", "   "))
        assertEquals(
            "https://ping.fjrx.my.id/api/avatars/avatar_10_1795372899.png",
            resolveAvatarUrl("https://ping.fjrx.my.id/", "/api/avatars/avatar_10_1795372899.png")
        )
        assertEquals(
            "https://ping.fjrx.my.id/api/avatars/avatar_10.png",
            resolveAvatarUrl("https://ping.fjrx.my.id", "api/avatars/avatar_10.png")
        )
        assertEquals(
            "https://cdn.example.com/pic.webp",
            resolveAvatarUrl("https://ping.fjrx.my.id/", "https://cdn.example.com/pic.webp")
        )
    }

    @Test
    fun catalogParser_parsesAvatarDepartmentSubDepartmentAndIsActive() {
        val raw = """
            {
              "success": true,
              "users": [
                {
                  "id": 10,
                  "username": "purnomo",
                  "display_name": "Teknisi Purnomo",
                  "role": "technician",
                  "avatar_url": "/api/avatars/avatar_10.png",
                  "department": "Infrastruktur",
                  "sub_department": "Fiber Optic",
                  "is_active": 1
                },
                {
                  "id": 11,
                  "username": "resigned_tech",
                  "display_name": "Budi Nonaktif",
                  "role": "technician",
                  "department": "NOC",
                  "is_active": 0
                },
                {
                  "id": 12,
                  "username": "legacy_user",
                  "display_name": "Legacy Active",
                  "role": "admin"
                }
              ]
            }
        """.trimIndent()

        val parsed = CatalogResponseParser.parseUsersJson(raw)
        assertEquals(3, parsed.size)

        val purnomo = parsed.first { it.id == 10L }
        assertEquals("/api/avatars/avatar_10.png", purnomo.avatarUrl)
        assertEquals("Infrastruktur • Fiber Optic", purnomo.departmentDisplay)
        assertTrue(purnomo.isUserActive)

        val resigned = parsed.first { it.id == 11L }
        assertFalse(resigned.isUserActive)
        assertEquals("NOC", resigned.departmentDisplay)

        val legacy = parsed.first { it.id == 12L }
        assertTrue(legacy.isUserActive)
        assertTrue(legacy.isAdmin)
    }

    @Test
    fun assigneeConverter_isBackwardCompatibleWithRoomV8Json() {
        val converters = TaskConverters()
        // Old Room v8 JSON without Patch Ke-8 keys
        val legacyJson = """[{"id":7,"name":"Fajar","role":"admin","is_lead":true}]"""
        val restored = converters.toAssigneeList(legacyJson)
        assertEquals(1, restored.size)
        assertEquals(7L, restored.first().id)
        assertEquals("Fajar", restored.first().name)
        assertTrue(restored.first().isUserActive)
        assertNull(restored.first().avatarUrl)

        // Round-trip new Patch Ke-8 fields
        val enriched = listOf(
            Assignee(
                id = 10L,
                name = "Purnomo",
                role = "technician",
                isLead = false,
                username = "purnomo",
                avatarUrl = "/api/avatars/avatar_10.png",
                department = "Infrastruktur",
                subDepartment = "OSP",
                isActive = 1
            )
        )
        val encoded = converters.fromAssigneeList(enriched)
        val decoded = converters.toAssigneeList(encoded)
        assertEquals("/api/avatars/avatar_10.png", decoded.first().avatarUrl)
        assertEquals("Infrastruktur • OSP", decoded.first().departmentDisplay)
        assertEquals("purnomo", decoded.first().username)
    }

    @Test
    fun avatarUploadHelper_enforces10MbCapAndImageMimeTypes() {
        // 1. Rejects non-image MIME
        val pdfResult = AvatarUploadHelper.prepareAvatarFromStream(
            inputStream = ByteArrayInputStream("pdf-data".toByteArray()),
            rawMimeType = "application/pdf",
            suggestedFileName = "doc.pdf"
        )
        assertTrue(pdfResult.isFailure)
        assertTrue(pdfResult.exceptionOrNull()?.message.orEmpty().contains("gambar", ignoreCase = true))

        // 2. Fast-fails declared size > 10 MB
        val oversizeDeclared = AvatarUploadHelper.prepareAvatarFromStream(
            inputStream = ByteArrayInputStream(ByteArray(16)),
            rawMimeType = "image/jpeg",
            declaredSizeBytes = 12L * 1024L * 1024L
        )
        assertTrue(oversizeDeclared.isFailure)
        assertTrue(oversizeDeclared.exceptionOrNull()?.message.orEmpty().contains("10 MB"))

        // 3. Cuts off stream > 10 MB even when declaredSize is unknown
        val oversizeBytes = ByteArray(AvatarUploadHelper.MAX_FILE_SIZE_BYTES + 2048) { 1 }
        val oversizeStream = AvatarUploadHelper.prepareAvatarFromStream(
            inputStream = ByteArrayInputStream(oversizeBytes),
            rawMimeType = "image/png",
            declaredSizeBytes = null
        )
        assertTrue(oversizeStream.isFailure)
        assertTrue(oversizeStream.exceptionOrNull()?.message.orEmpty().contains("10 MB"))

        // 4. Accepts valid WebP <= 10 MB and builds multipart field "file"
        val validBytes = ByteArray(4096) { 0x42 }
        val valid = AvatarUploadHelper.prepareAvatarFromStream(
            inputStream = ByteArrayInputStream(validBytes),
            rawMimeType = "image/webp",
            suggestedFileName = "photo.webp"
        )
        assertTrue(valid.isSuccess)
        val payload = valid.getOrThrow()
        assertEquals("photo.webp", payload.fileName)
        assertEquals("image/webp", payload.mimeType)
        val part = payload.toMultipartPart()
        assertTrue(part.headers.toString().contains("""name="file""""))
    }

    @Test
    fun login_withHttp403DeactivatedAccount_blocksSessionSaveAndReturnsDeactivatedException() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPreferences(context)
        prefs.clearAuthToken()

        val fakeApi = Patch8FakeApiService(
            loginHandler = {
                Response.error(
                    403,
                    """{"error":"Akun Anda dinonaktifkan. Silakan hubungi administrator."}"""
                        .toResponseBody("application/json".toMediaType())
                )
            }
        )
        val repo = TaskRepositoryImpl(
            taskDao = Patch8InMemoryDao(),
            apiServiceProvider = { fakeApi },
            appPreferences = prefs
        )

        val result = repo.login("purnomo", "secret")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AccountDeactivatedException)
        assertEquals(DEACTIVATED_ACCOUNT_MESSAGE, result.exceptionOrNull()?.message)

        // Token must NOT be saved
        val session = prefs.sessionFlow.first()
        assertEquals("", session.authToken)
        assertNull(prefs.getToken())
    }

    @Test
    fun runtimeDeactivation_onAuthMe_autoLogsOutWhilePreservingUnsyncedOfflineTasks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPreferences(context)
        prefs.saveAuthSession(
            token = "valid-jwt-token",
            technicianName = "Teknisi Purnomo",
            role = "technician",
            userId = 10L,
            username = "purnomo",
            department = "Infrastruktur",
            subDepartment = "Fiber Optic",
            isActive = 1
        )

        val unsyncedOfflineTask = TaskEntity(
            id = -101L,
            ticketCode = "NET-999",
            title = "Perbaikan ODP Offline",
            category = "NET",
            scheduledDateEpochDay = 20_000L,
            scheduledTimeMinutes = 540,
            timeSlotLabel = "09:00",
            priority = "P1",
            stage = Stage.IN_PROGRESS.apiValue,
            technicalNotes = "Draft offline",
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = "Site A",
            slaTargetTime = "",
            checklist = emptyList(),
            assignees = emptyList(),
            isSynced = false,
            updatedAtMillis = 1_700_000_000_000L
        )
        val syncedTask = unsyncedOfflineTask.copy(
            id = 55L,
            ticketCode = "NET-55",
            title = "Tiket Tersinkron",
            isSynced = true
        )
        val dao = Patch8InMemoryDao(listOf(unsyncedOfflineTask, syncedTask))

        val fakeApi = Patch8FakeApiService(
            getMyProfileHandler = {
                Response.success(
                    AuthMeResponseDto(
                        success = true,
                        user = UserDto(
                            id = 10L,
                            username = "purnomo",
                            displayName = "Teknisi Purnomo",
                            role = "technician",
                            isActive = 0
                        )
                    )
                )
            }
        )
        val repo = TaskRepositoryImpl(
            taskDao = dao,
            apiServiceProvider = { fakeApi },
            appPreferences = prefs
        )
        val vm = ScheduleViewModel(
            repository = repo,
            appPreferences = prefs,
            enableBackgroundTicker = false,
            enableSseStream = false,
            enableAutoSyncRetry = false
        )

        val collector = launch(Dispatchers.Unconfined) { vm.uiState.collect {} }
        try {
            withTimeout(5_000L) {
                while (vm.uiState.value.loginErrorMessage == null) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    delay(30)
                }
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(DEACTIVATED_ACCOUNT_MESSAGE, vm.uiState.value.loginErrorMessage)
            assertFalse(vm.uiState.value.isLoggedIn)

            // Critical Zero-Data-Loss check: unsynced offline task MUST survive auto-logout!
            val remainingTasks = dao.getAllTasks().first()
            assertEquals(1, remainingTasks.size)
            assertEquals(-101L, remainingTasks.first().id)
            assertFalse(remainingTasks.first().isSynced)
        } finally {
            collector.cancel()
            prefs.clearAuthToken()
        }
    }

    @Test
    fun avatarUploadDeleteAndProfileUpdate_syncWithApiAndPersistOfflineInDataStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPreferences(context)
        prefs.saveAuthSession(
            token = "jwt-active",
            technicianName = "Purnomo",
            role = "technician",
            userId = 10L,
            username = "purnomo",
            avatarUrl = null,
            department = "Infrastruktur",
            subDepartment = "Fiber Optic",
            isActive = 1
        )

        var uploadedUserId: Long? = null
        var deletedUserId: Long? = null
        var patchedRequest: UpdateUserRequestDto? = null

        val fakeApi = Patch8FakeApiService(
            uploadAvatarHandler = { userId, _ ->
                uploadedUserId = userId
                Response.success(
                    AvatarUploadResponseDto(
                        success = true,
                        avatarUrl = "/api/avatars/avatar_10_1795372899.png",
                        message = "Foto profil berhasil diunggah"
                    )
                )
            },
            deleteAvatarHandler = { userId ->
                deletedUserId = userId
                Response.success(UserMutationResponseDto(success = true, message = "Avatar dihapus"))
            },
            updateProfileHandler = { userId, req ->
                patchedRequest = req
                Response.success(
                    UserMutationResponseDto(
                        success = true,
                        user = UserDto(
                            id = userId,
                            username = "purnomo",
                            displayName = req.displayName ?: "Teknisi Purnomo Senior",
                            role = "technician",
                            avatarUrl = null,
                            department = req.department ?: "IT Network Operations",
                            subDepartment = req.subDepartment ?: "Fiber Optic & OSP",
                            isActive = 1
                        )
                    )
                )
            }
        )

        val repo = TaskRepositoryImpl(
            taskDao = Patch8InMemoryDao(),
            apiServiceProvider = { fakeApi },
            appPreferences = prefs
        )

        // 1. Upload avatar
        val uploadRes = repo.uploadMyAvatar("selfie.png", "image/png", ByteArray(256) { 7 })
        assertTrue(uploadRes.isSuccess)
        assertEquals(10L, uploadedUserId)
        assertEquals("/api/avatars/avatar_10_1795372899.png", prefs.sessionFlow.first().avatarUrl)

        // 2. Delete avatar
        val deleteRes = repo.deleteMyAvatar()
        assertTrue(deleteRes.isSuccess)
        assertEquals(10L, deletedUserId)
        assertNull(prefs.sessionFlow.first().avatarUrl)

        // 3. Self-service password & display name update
        val updateRes = repo.updateMyProfile(
            displayName = "Teknisi Purnomo Senior",
            password = "NewPassword2026",
            department = "IT Network Operations",
            subDepartment = "Fiber Optic & OSP"
        )
        assertTrue(updateRes.isSuccess)
        assertEquals("NewPassword2026", patchedRequest?.password)
        val afterUpdate = prefs.sessionFlow.first()
        assertEquals("Teknisi Purnomo Senior", afterUpdate.technicianName)
        assertEquals("IT Network Operations • Fiber Optic & OSP", afterUpdate.departmentDisplay)

        // Cleanup DataStore so subsequent tests start clean
        prefs.clearAuthToken()
    }
}

private class Patch8InMemoryDao(initial: List<TaskEntity> = emptyList()) : TaskDao {
    private val state = MutableStateFlow(initial)
    override fun getAllTasks(): Flow<List<TaskEntity>> = state
    override fun getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>> =
        state.map { list -> list.filter { it.scheduledDateEpochDay == epochDay } }
    override fun getTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>> =
        state.map { list -> list.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }
    override fun getActiveHeroTask(): Flow<TaskEntity?> =
        state.map { list -> list.firstOrNull { it.stage == Stage.IN_PROGRESS.apiValue } }
    override fun getTaskById(taskId: Long): Flow<TaskEntity?> =
        state.map { list -> list.firstOrNull { it.id == taskId } }
    override suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? =
        state.value.firstOrNull { it.id == taskId }
    override suspend fun getUnsyncedTasks(): List<TaskEntity> =
        state.value.filter { !it.isSynced }
    override fun getUnsyncedCount(): Flow<Int> =
        state.map { list -> list.count { !it.isSynced } }
    override suspend fun countTasks(): Int = state.value.size
    override suspend fun upsertTask(task: TaskEntity) {
        state.value = state.value.filterNot { it.id == task.id } + task
    }
    override suspend fun upsertTasks(tasks: List<TaskEntity>) {
        val ids = tasks.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in ids } + tasks
    }
    override suspend fun updateChecklist(taskId: Long, checklistJson: String, isSynced: Boolean, updatedAtMillis: Long) = Unit
    override suspend fun updateTaskTimer(taskId: Long, seconds: Long, isRunning: Boolean, timerStartedAtMillis: Long?, isSynced: Boolean, updatedAtMillis: Long) = Unit
    override suspend fun pauseAllOtherTimers(excludeTaskId: Long, updatedAtMillis: Long) = Unit
    override suspend fun updateTaskStage(taskId: Long, stage: String, isRunning: Boolean, timerStartedAtMillis: Long?, isSynced: Boolean, updatedAtMillis: Long) = Unit
    override suspend fun markTaskSynced(taskId: Long) {
        state.value = state.value.map { if (it.id == taskId) it.copy(isSynced = true) else it }
    }
    override suspend fun getTaskByClientSyncId(clientSyncId: String): TaskEntity? =
        state.value.firstOrNull { it.clientSyncId == clientSyncId }
    override suspend fun deleteTaskById(taskId: Long) {
        state.value = state.value.filterNot { it.id == taskId }
    }
    override suspend fun deletePendingByClientSyncId(clientSyncId: String) {
        state.value = state.value.filterNot { it.id < 0L && it.clientSyncId == clientSyncId }
    }
    override suspend fun deleteAllSyncedTasks() {
        state.value = state.value.filterNot { it.isSynced }
    }
    override suspend fun deleteStaleSyncedTasks(activeIds: List<Long>) {
        val set = activeIds.toSet()
        state.value = state.value.filterNot { it.isSynced && it.id > 0L && it.id !in set }
    }
}

private class Patch8FakeApiService(
    private val loginHandler: suspend (LoginRequestDto) -> Response<LoginResponseDto> = {
        Response.success(LoginResponseDto(token = "jwt"))
    },
    private val getMyProfileHandler: suspend () -> Response<AuthMeResponseDto> = {
        Response.success(AuthMeResponseDto(success = true))
    },
    private val uploadAvatarHandler: suspend (Long, MultipartBody.Part) -> Response<AvatarUploadResponseDto> = { _, _ ->
        Response.success(AvatarUploadResponseDto(success = true, avatarUrl = "/api/avatars/test.png"))
    },
    private val deleteAvatarHandler: suspend (Long) -> Response<UserMutationResponseDto> = { _ ->
        Response.success(UserMutationResponseDto(success = true))
    },
    private val updateProfileHandler: suspend (Long, UpdateUserRequestDto) -> Response<UserMutationResponseDto> = { _, _ ->
        Response.success(UserMutationResponseDto(success = true))
    }
) : PingDuluApiService {
    private val jsonMedia = "application/json".toMediaType()
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> = loginHandler(body)
    override suspend fun getMyProfile(): Response<AuthMeResponseDto> = getMyProfileHandler()
    override suspend fun uploadUserAvatar(userId: Long, file: MultipartBody.Part): Response<AvatarUploadResponseDto> =
        uploadAvatarHandler(userId, file)
    override suspend fun deleteUserAvatar(userId: Long): Response<UserMutationResponseDto> =
        deleteAvatarHandler(userId)
    override suspend fun updateUserProfile(userId: Long, request: UpdateUserRequestDto): Response<UserMutationResponseDto> =
        updateProfileHandler(userId, request)
    override suspend fun getUsersRaw(): Response<ResponseBody> = Response.success("[]".toResponseBody(jsonMedia))
    override suspend fun getLabelsRaw(): Response<ResponseBody> = Response.success("[]".toResponseBody(jsonMedia))
    override suspend fun getTasks(): Response<TaskResponseDto> = Response.success(TaskResponseDto(data = emptyList()))
    override suspend fun getTaskDetailRaw(taskId: Long): Response<ResponseBody> = Response.success("{}".toResponseBody(jsonMedia))
    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> = Response.success(CreateTaskResponseDto(success = true))
    override suspend fun patchTask(id: Long, body: TaskPatchRequestDto): Response<ResponseBody> = Response.success("{}".toResponseBody(jsonMedia))
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = Response.success(Unit)
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = Response.success(Unit)
    override suspend fun addChecklistStep(taskId: Long, body: AddChecklistStepRequestDto): Response<ResponseBody> = Response.success("{}".toResponseBody(jsonMedia))
    override suspend fun toggleChecklistStep(id: Long, stepId: Long, body: ChecklistStepToggleDto): Response<Unit> = Response.success(Unit)
    override suspend fun getAttachmentsRaw(taskId: Long): Response<ResponseBody> = Response.success("[]".toResponseBody(jsonMedia))
    override suspend fun uploadAttachment(taskId: Long, file: MultipartBody.Part): Response<ResponseBody> = Response.success("{}".toResponseBody(jsonMedia))
    override suspend fun getCommentsRaw(taskId: Long): Response<ResponseBody> = Response.success("[]".toResponseBody(jsonMedia))
    override suspend fun postComment(taskId: Long, body: AddCommentRequestDto): Response<ResponseBody> = Response.success("{}".toResponseBody(jsonMedia))
    override suspend fun getActivitiesRaw(taskId: Long): Response<ResponseBody> = Response.success("[]".toResponseBody(jsonMedia))
    override suspend fun getActivitySingularRaw(taskId: Long): Response<ResponseBody> = Response.success("[]".toResponseBody(jsonMedia))
    override suspend fun getTaskLogsRaw(taskId: Long): Response<ResponseBody> = Response.success("[]".toResponseBody(jsonMedia))
    override suspend fun workTask(taskId: Long): Response<WorkTaskResponseDto> = Response.success(WorkTaskResponseDto(success = true))
    override suspend fun claimTask(taskId: Long): Response<ClaimTaskResponseDto> = Response.success(ClaimTaskResponseDto(success = true))
    override suspend fun deleteTask(taskId: Long): Response<ResponseBody> = Response.success("{}".toResponseBody(jsonMedia))
    override suspend fun getHolidays(year: Int): Response<HolidayResponseDto> = Response.success(HolidayResponseDto(year = year))
    override suspend fun getCalendar(year: Int, month: Int): Response<CalendarAgendaResponseDto> = Response.success(CalendarAgendaResponseDto(year = year, month = month))
    override suspend fun postSpeedTestRecord(body: SpeedTestRecordRequestDto): Response<ResponseBody> = Response.success("{}".toResponseBody(jsonMedia))
    override suspend fun getSpeedTestRecordsRaw(): Response<ResponseBody> = Response.success("[]".toResponseBody(jsonMedia))
}
