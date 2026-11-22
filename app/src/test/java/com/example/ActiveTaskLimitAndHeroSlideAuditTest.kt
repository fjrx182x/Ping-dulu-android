package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.domain.model.ActiveTaskLimitPolicy
import com.example.domain.model.ActiveTaskLimitWarning
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.FieldTechnician
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.presentation.components.ActiveTaskLimitWarningDialog
import com.example.presentation.components.HeroActiveJobCarousel
import com.example.presentation.components.TaskDetailBottomSheet
import com.example.presentation.schedule.ScheduleViewModel
import com.example.ui.theme.PingDuluFieldOpsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ActiveTaskLimitAndHeroSlideAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private val todayEpochDay = DateEpochUtil.toEpochDay(2026, 11, 22)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testTask(
        id: Long,
        code: String = "NET-$id",
        title: String = "Tugas Lapangan #$id",
        stage: Stage = Stage.IN_PROGRESS,
        assigneeId: Long = 10L,
        assigneeName: String = "Budi",
        unassigned: Boolean = false
    ): Task = Task(
        id = id,
        ticketCode = code,
        title = title,
        category = TicketCategory.NET,
        scheduledDateEpochDay = todayEpochDay,
        timeSlotLabel = "09:00",
        priority = Priority.P2_HIGH,
        stage = stage,
        technicalNotes = "Catatan uji terisolasi",
        trackedSeconds = 0L,
        isTimerRunning = false,
        timerStartedAtMillis = null,
        location = "Rack Core Lt.2",
        slaTargetTime = "",
        checklist = listOf(
            ChecklistItem(1L, "Cek fisik", isDone = true),
            ChecklistItem(2L, "Uji koneksi", isDone = false)
        ),
        assignees = if (unassigned) {
            emptyList()
        } else {
            listOf(Assignee(id = assigneeId, name = assigneeName, role = "technician", isLead = true))
        },
        assignedToId = if (unassigned) null else assigneeId,
        isSynced = true,
        updatedAtMillis = 1_700_000_000_000L + id
    )

    @Test
    fun heroCarousel_removesRuteLokasi_showsCompleteConfirmation_andSlidesWith1Dari2Label() {
        val task1 = testTask(id = 101L, code = "NET-101", title = "Perbaikan Switch Core A")
        val task2 = testTask(id = 102L, code = "SRV-102", title = "Audit UPS Ruang Server")
        var completedTaskId: Long? = null

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                HeroActiveJobCarousel(
                    tasks = listOf(task1, task2),
                    currentUserId = 10L,
                    currentUserName = "Budi",
                    currentUserRole = "technician",
                    onCompleteTask = { completedTaskId = it },
                    onOpenWorksheet = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Rute Lokasi button is completely removed
        composeRule.onNodeWithTag("hero_maps_button").assertDoesNotExist()
        composeRule.onNodeWithText("Rute Lokasi", substring = true).assertDoesNotExist()

        // 2. Slide 1 displays "SEDANG DIKERJAKAN 1 DARI 2" & Complete button
        composeRule.onNodeWithTag("hero_slide_status_badge_1")
            .assertIsDisplayed()
            .assertTextContains("SEDANG DIKERJAKAN 1 DARI 2")
        composeRule.onNodeWithTag("hero_pager_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("hero_complete_button_101").assertIsDisplayed()

        // 3. Swipe left to slide 2 -> displays "SEDANG DIKERJAKAN 2 DARI 2" & SRV-102
        composeRule.onNodeWithTag("hero_active_job_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_slide_status_badge_2")
            .assertIsDisplayed()
            .assertTextContains("SEDANG DIKERJAKAN 2 DARI 2")
        composeRule.onNodeWithText("SRV-102").assertIsDisplayed()

        // 4. Tap Selesaikan on slide 2 -> opens confirmation dialog -> confirm completes task2
        composeRule.onNodeWithTag("hero_complete_button_102").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hero_complete_confirm_dialog").assertIsDisplayed()
        assertNull("Should not complete before confirmation", completedTaskId)

        composeRule.onNodeWithTag("hero_complete_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals(102L, completedTaskId)
    }

    @Test
    fun taskDetailBottomSheet_removesMapsChipCompletely() {
        val task = testTask(id = 201L, code = "NET-201")
        composeRule.setContent {
            PingDuluFieldOpsTheme {
                TaskDetailBottomSheet(
                    task = task,
                    onDismiss = {},
                    onToggleChecklistItem = { _, _ -> },
                    onToggleTimer = {},
                    onMarkCompleted = {},
                    onReopenTask = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sheet_maps_chip").assertDoesNotExist()
        composeRule.onNodeWithText("Maps").assertDoesNotExist()
    }

    @Test
    fun warningDialog_rendersExactTechnicianAndAdminDelegationCopy() {
        var dismissed = false
        val warning = ActiveTaskLimitWarning(
            title = "Kalem Dulu!",
            message = ActiveTaskLimitPolicy.DELEGATION_LIMIT_MESSAGE,
            detailSubtitle = "Teknisi sudah mencapai batas aktif: Budi (5/5).",
            isDelegationContext = true
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                ActiveTaskLimitWarningDialog(
                    warning = warning,
                    onDismiss = { dismissed = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("active_task_limit_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Kalem Dulu!").assertIsDisplayed()
        composeRule.onNodeWithText("BATAS MAKSIMAL 5 TASK AKTIF").assertIsDisplayed()
        composeRule.onNodeWithTag("active_task_limit_message")
            .assertIsDisplayed()
            .assertTextContains("Kalem Dulu! Ada 5 task yang aktif. selesaikan dulu minimal 1 task")
        composeRule.onNodeWithText("Siap, Mengerti").assertIsDisplayed()
        composeRule.onNodeWithTag("active_task_limit_dismiss_button").performClick()
        composeRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun viewModel_enforcesFiveNonDoneLimitSyncedWithServerAndUnblocksAfterCompletingOne() = runTest(testDispatcher) {
        // Patch Ke-7 server-synced rule (stage != DONE):
        // Budi holds 3 IN_PROGRESS + 2 BACKLOG = 5 active tasks + 1 unassigned BACKLOG + 1 DONE task
        val initialTasks = (1L..3L).map { id ->
            testTask(id = id, stage = Stage.IN_PROGRESS, assigneeId = 10L, assigneeName = "Budi")
        } + (4L..5L).map { id ->
            testTask(id = id, stage = Stage.BACKLOG, assigneeId = 10L, assigneeName = "Budi")
        } + listOf(
            testTask(id = 7L, stage = Stage.BACKLOG, unassigned = true),
            testTask(id = 8L, stage = Stage.DONE, assigneeId = 10L, assigneeName = "Budi")
        )
        val repo = IsolatedWipFakeRepository(initialTasks)
        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { todayEpochDay },
            hourOfDayProvider = { 10 },
            enableBackgroundTicker = false,
            enableSseStream = false,
            initialUserIdForTesting = 10L,
            initialTechnicianNameForTesting = "Budi",
            initialTechnicianRoleForTesting = "technician"
        )

        val collector = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        // Hero Carousel stays strictly IN_PROGRESS (3 tasks), while active WIP count is 5 (3 Proses + 2 Antrean)
        assertEquals(3, vm.uiState.value.activeHeroTasks.size)

        // Starting work on Budi's OWN already-assigned BACKLOG task #4 does NOT double-count -> ALLOWED!
        vm.workTask(4L)
        advanceUntilIdle()
        assertNull(vm.uiState.value.activeTaskLimitWarning)
        assertEquals(1, repo.workTaskCalls)

        // Attempt 1: claimTask on unassigned 6th active task #7 -> BLOCKED with exact technician message
        vm.claimTask(7L)
        advanceUntilIdle()
        assertEquals(
            "Kalem dulu! Kamu punya 5 task aktif nih. Selesaikan dulu minimal 1 task",
            vm.uiState.value.activeTaskLimitWarning?.message
        )
        assertEquals(0, repo.claimTaskCalls)
        vm.dismissActiveTaskLimitWarning()
        advanceUntilIdle()
        assertNull(vm.uiState.value.activeTaskLimitWarning)

        // Attempt 2: workTask on unassigned 6th active task #7 -> BLOCKED
        vm.workTask(7L)
        advanceUntilIdle()
        assertEquals(
            ActiveTaskLimitPolicy.SELF_CLAIM_LIMIT_MESSAGE,
            vm.uiState.value.activeTaskLimitWarning?.message
        )
        assertEquals(1, repo.workTaskCalls)
        vm.dismissActiveTaskLimitWarning()
        advanceUntilIdle()

        // Attempt 3: Reopen DONE task #8 -> IN_PROGRESS or BACKLOG -> BLOCKED (closes reopen bypass!)
        vm.updateTaskStage(8L, Stage.IN_PROGRESS)
        advanceUntilIdle()
        assertEquals(
            ActiveTaskLimitPolicy.SELF_CLAIM_LIMIT_MESSAGE,
            vm.uiState.value.activeTaskLimitWarning?.message
        )
        assertEquals(0, repo.stageUpdateCalls)
        vm.dismissActiveTaskLimitWarning()
        advanceUntilIdle()

        // Complete task #1 (drops non-DONE count from 5 -> 4)
        vm.markTaskCompleted(1L)
        advanceUntilIdle()

        // Now claiming unassigned task #7 MUST SUCCEED!
        vm.claimTask(7L)
        advanceUntilIdle()
        assertNull(vm.uiState.value.activeTaskLimitWarning)
        assertEquals(1, repo.claimTaskCalls)

        vm.stopBackgroundJobs()
        collector.cancel()
    }

    @Test
    fun viewModel_enforcesFiveNonDoneLimitWhenAdminOrTechDelegatesToOverloadedTechnician() = runTest(testDispatcher) {
        // Budi has 3 IN_PROGRESS + 2 BACKLOG = 5 active (!= DONE) -> OVERLOADED!
        // Andi has 2 IN_PROGRESS + 2 BACKLOG + 3 DONE = 4 active (!= DONE) -> ALLOWED!
        val initialTasks = (1L..3L).map { id ->
            testTask(id = id, stage = Stage.IN_PROGRESS, assigneeId = 20L, assigneeName = "Budi")
        } + (4L..5L).map { id ->
            testTask(id = id, stage = Stage.BACKLOG, assigneeId = 20L, assigneeName = "Budi")
        } + (11L..12L).map { id ->
            testTask(id = id, stage = Stage.IN_PROGRESS, assigneeId = 30L, assigneeName = "Andi")
        } + (13L..14L).map { id ->
            testTask(id = id, stage = Stage.BACKLOG, assigneeId = 30L, assigneeName = "Andi")
        } + (21L..23L).map { id ->
            testTask(id = id, stage = Stage.DONE, assigneeId = 30L, assigneeName = "Andi")
        }
        val repo = IsolatedWipFakeRepository(
            initial = initialTasks,
            technicians = listOf(
                FieldTechnician(id = 1L, username = "fajar", displayName = "Fajar", role = "admin"),
                FieldTechnician(id = 20L, username = "budi", displayName = "Budi", role = "technician"),
                FieldTechnician(id = 30L, username = "andi", displayName = "Andi", role = "technician")
            )
        )
        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { todayEpochDay },
            hourOfDayProvider = { 10 },
            enableBackgroundTicker = false,
            enableSseStream = false,
            initialUserIdForTesting = 1L,
            initialTechnicianNameForTesting = "Fajar",
            initialTechnicianRoleForTesting = "admin"
        )

        val collector = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        // Delegating a new task to Budi (3 Proses + 2 Antrean = 5 aktif) -> BLOCKED with exact delegation copy
        vm.createQuickTask(
            title = "Cek ODP Cluster Baru",
            category = TicketCategory.NET,
            priority = Priority.P2_HIGH,
            location = "Cluster Melati",
            assigneeIds = listOf(20L)
        )
        advanceUntilIdle()
        val warning = vm.uiState.value.activeTaskLimitWarning
        assertNotNull(warning)
        assertEquals(
            "Kalem Dulu! Ada 5 task yang aktif. selesaikan dulu minimal 1 task",
            warning?.message
        )
        assertEquals(0, repo.createTaskCalls)
        vm.dismissActiveTaskLimitWarning()
        advanceUntilIdle()

        // Delegating a new task to Andi (2 Proses + 2 Antrean + 3 DONE -> 4 aktif!) -> ALLOWED!
        vm.createQuickTask(
            title = "Ganti Patch Cord Rack B",
            category = TicketCategory.NET,
            priority = Priority.P3_NORMAL,
            location = "Ruang Server",
            assigneeIds = listOf(30L)
        )
        advanceUntilIdle()
        assertNull(vm.uiState.value.activeTaskLimitWarning)
        assertEquals(1, repo.createTaskCalls)

        vm.stopBackgroundJobs()
        collector.cancel()
    }
}

private class IsolatedWipFakeRepository(
    initial: List<Task>,
    technicians: List<FieldTechnician> = emptyList()
) : TaskRepository {
    private val tasksFlow = MutableStateFlow(initial)
    private val techFlow = MutableStateFlow(technicians)

    var workTaskCalls = 0
    var claimTaskCalls = 0
    var stageUpdateCalls = 0
    var createTaskCalls = 0

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay == epochDay } }

    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow

    override fun observeActiveHeroTask(): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.stage == Stage.IN_PROGRESS } }

    override fun observeTaskById(taskId: Long): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.id == taskId } }

    override fun observeUnsyncedCount(): Flow<Int> = MutableStateFlow(0)
    override fun observeTechnicians(): Flow<List<FieldTechnician>> = techFlow
    override suspend fun initializeOfflineCache() = Unit
    override suspend fun refreshTasksFromServer(): SyncResult = SyncResult.OfflineFallback(0, "Test")
    override suspend fun toggleChecklistItem(taskId: Long, checklistId: Long) = Unit
    override suspend fun toggleTaskTimer(taskId: Long) = Unit
    override suspend fun persistTimerTick(taskId: Long, newTrackedSeconds: Long) = Unit

    override suspend fun updateTaskStage(taskId: Long, newStage: Stage) {
        stageUpdateCalls++
        tasksFlow.value = tasksFlow.value.map { if (it.id == taskId) it.copy(stage = newStage) else it }
    }

    override suspend fun markTaskCompleted(taskId: Long) {
        tasksFlow.value = tasksFlow.value.map { if (it.id == taskId) it.copy(stage = Stage.DONE) else it }
    }

    override suspend fun workTask(taskId: Long): Result<String> {
        workTaskCalls++
        tasksFlow.value = tasksFlow.value.map { if (it.id == taskId) it.copy(stage = Stage.IN_PROGRESS) else it }
        return Result.success("OK")
    }

    override suspend fun claimTask(taskId: Long): Result<Task> {
        claimTaskCalls++
        val target = tasksFlow.value.first { it.id == taskId }
        return Result.success(target)
    }

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
    ): Task {
        createTaskCalls++
        val created = tasksFlow.value.first().copy(id = 999L + createTaskCalls, title = title, stage = Stage.BACKLOG)
        tasksFlow.value = tasksFlow.value + created
        return created
    }

    override suspend fun login(username: String, password: String): Result<String> = Result.success("tok")
    override suspend fun clearSyncedCacheOnLogout() = Unit
}
