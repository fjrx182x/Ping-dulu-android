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
import com.example.data.remote.dto.ChecklistSyncDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerUpdateDto
import com.example.data.remote.dto.toEntity
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.Stage
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
                AssigneeDto(3L, "Fajar", "admin", isLead = 1),
                AssigneeDto(2L, "Budi", "technician", isLead = 0)
            )
        )

        val entity = dto.toEntity(nowMillis = 1000L)
        assertTrue(entity.isTimerRunning)
        assertTrue(entity.checklist[0].isDone)
        assertFalse(entity.checklist[1].isDone)
        assertTrue(entity.assignees[0].isLead)
        assertFalse(entity.assignees[1].isLead)
        assertEquals("09:00 - 10:30", entity.timeSlotLabel)
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
}

private class FailingPingDuluApiService : PingDuluApiService {
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> = throw IOException("Offline")
    override suspend fun getTasks(): Response<TaskResponseDto> = throw IOException("Offline")
    override suspend fun createTask(body: CreateTaskRequestDto): Response<TaskDto> = throw IOException("Offline")
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = throw IOException("Offline")
    override suspend fun updateTimer(id: Long, body: TimerUpdateDto): Response<Unit> = throw IOException("Offline")
    override suspend fun syncChecklist(id: Long, body: ChecklistSyncDto): Response<Unit> = throw IOException("Offline")
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

    override suspend fun deleteTaskById(taskId: Long) {
        state.value = state.value - taskId
    }
}
