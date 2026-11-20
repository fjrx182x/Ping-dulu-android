package com.example

import com.example.data.local.SeedTaskData
import com.example.data.local.toDomain
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.presentation.schedule.DayIndicatorType
import com.example.presentation.schedule.LayoutMode
import com.example.presentation.schedule.ScheduleViewModel
import com.example.presentation.schedule.StatusFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelAuditTest {

    private val testDispatcher = StandardTestDispatcher()
    private val baseEpochDay = DateEpochUtil.toEpochDay(2026, 10, 20)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun viewModel_builds7DayRollingHorizonAndFiltersByDayAndStatus() = runTest(testDispatcher) {
        val repo = FakeTaskRepository(
            SeedTaskData.createInitialTasks(baseEpochDay = baseEpochDay).map { it.toDomain() }
        )
        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { baseEpochDay },
            hourOfDayProvider = { 8 },
            enableBackgroundTicker = false,
            enableSseStream = false
        )

        val collector = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(7, state.scheduleDays.size)
        assertEquals("Selamat Pagi, Fajar", state.greetingTitle)
        assertEquals(2, state.filteredTasks.size) // NET-1 & SRV-3 on baseEpochDay
        assertEquals(DayIndicatorType.CRITICAL_P1, state.scheduleDays[0].indicatorType)
        assertEquals(DayIndicatorType.ALL_DONE, state.scheduleDays[6].indicatorType)
        assertEquals(2, state.filterCounts.all)
        assertEquals(1, state.filterCounts.backlog)
        assertEquals(1, state.filterCounts.inProgress)
        assertEquals(0, state.filterCounts.done)

        // Filter to BACKLOG
        vm.onStatusFilterSelected(StatusFilter.BACKLOG)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.filteredTasks.size)
        assertEquals("SRV-3", vm.uiState.value.filteredTasks.first().ticketCode)

        // Switch to Day + 1 and ALL
        vm.onDaySelected(baseEpochDay + 1L)
        vm.onStatusFilterSelected(StatusFilter.ALL)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.filteredTasks.size)
        assertEquals(1, vm.uiState.value.filterCounts.done)

        // Toggle layout mode
        vm.onToggleLayoutMode(LayoutMode.LIST_1_COLUMN)
        advanceUntilIdle()
        assertEquals(LayoutMode.LIST_1_COLUMN, vm.uiState.value.layoutMode)

        vm.stopBackgroundJobs()
        collector.cancel()
    }

    @Test
    fun viewModel_liveStopwatchTicksEverySecondAndFlushesOnPauseWithoutLosingSeconds() = runTest(testDispatcher) {
        val repo = FakeTaskRepository(
            SeedTaskData.createInitialTasks(baseEpochDay = baseEpochDay).map { it.toDomain() }
        )
        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { baseEpochDay },
            hourOfDayProvider = { 9 },
            enableBackgroundTicker = true,
            enableSseStream = false
        )

        val collector = backgroundScope.launch { vm.uiState.collect() }
        runCurrent()

        val initialSeconds = vm.uiState.value.liveHeroSeconds
        assertEquals(2712L, initialSeconds)

        // Advance 4 seconds
        advanceTimeBy(4_001L)
        runCurrent()
        assertEquals(2716L, vm.uiState.value.liveHeroSeconds)

        // Pause timer -> must persist 2716s before pausing (zero lost seconds!)
        vm.toggleTaskTimer(59L)
        runCurrent()

        assertFalse(vm.uiState.value.activeHeroTask!!.isTimerRunning)
        assertEquals(2716L, repo.getTaskSnapshot(59L)!!.trackedSeconds)

        // Advance another 5 seconds while paused -> must NOT tick!
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(2716L, vm.uiState.value.liveHeroSeconds)

        // Resume task 59, tick 3 more seconds (2719s), then start timer on task 60 directly
        vm.toggleTaskTimer(59L)
        runCurrent()
        advanceTimeBy(3_001L)
        runCurrent()
        assertEquals(2719L, vm.uiState.value.liveHeroSeconds)

        vm.toggleTaskTimer(60L)
        runCurrent()
        assertFalse(repo.getTaskSnapshot(59L)!!.isTimerRunning)
        assertEquals(2719L, repo.getTaskSnapshot(59L)!!.trackedSeconds)
        assertTrue(repo.getTaskSnapshot(60L)!!.isTimerRunning)

        vm.stopBackgroundJobs()
        collector.cancel()
    }

    @Test
    fun viewModel_createQuickTaskAndMarkCompletedUpdatesStateReactively() = runTest(testDispatcher) {
        val repo = FakeTaskRepository(
            SeedTaskData.createInitialTasks(baseEpochDay = baseEpochDay).map { it.toDomain() }
        )
        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { baseEpochDay },
            enableBackgroundTicker = false,
            enableSseStream = false
        )

        val collector = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.createQuickTask(
            title = "Cek Fiber Optic Lt. 3",
            category = TicketCategory.NET,
            priority = Priority.P1_CRITICAL,
            location = "Gedung Utama Lt. 3"
        )
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.filteredTasks.size)
        assertNotNull(vm.uiState.value.syncMessage)

        vm.markTaskCompleted(59L)
        advanceUntilIdle()
        assertEquals(Stage.DONE, repo.getTaskSnapshot(59L)!!.stage)
        assertFalse(repo.getTaskSnapshot(59L)!!.isTimerRunning)
        assertTrue(vm.uiState.value.filterCounts.done == 1)

        vm.stopBackgroundJobs()
        collector.cancel()
    }
}

private class FakeTaskRepository(initial: List<Task>) : TaskRepository {
    private val tasksFlow = MutableStateFlow(initial)

    fun getTaskSnapshot(taskId: Long): Task? = tasksFlow.value.firstOrNull { it.id == taskId }

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay == epochDay } }

    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow

    override fun observeActiveHeroTask(): Flow<Task?> =
        tasksFlow.map { list ->
            list.filter { it.stage == Stage.IN_PROGRESS }
                .sortedWith(compareByDescending<Task> { it.isTimerRunning }.thenBy { it.priority.sortRank })
                .firstOrNull()
        }

    override fun observeTaskById(taskId: Long): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.id == taskId } }

    override fun observeUnsyncedCount(): Flow<Int> =
        tasksFlow.map { list -> list.count { !it.isSynced } }

    override suspend fun initializeOfflineCache() = Unit

    override suspend fun refreshTasksFromServer(): SyncResult =
        SyncResult.OfflineFallback(pendingUploadCount = tasksFlow.value.count { !it.isSynced }, reason = "Test offline")

    override suspend fun toggleChecklistItem(taskId: Long, checklistId: Long) {
        tasksFlow.value = tasksFlow.value.map { task ->
            if (task.id == taskId) {
                task.copy(
                    checklist = task.checklist.map {
                        if (it.id == checklistId) it.copy(isDone = !it.isDone) else it
                    },
                    isSynced = false
                )
            } else {
                task
            }
        }
    }

    override suspend fun toggleTaskTimer(taskId: Long) {
        tasksFlow.value = tasksFlow.value.map { task ->
            if (task.id == taskId) {
                val nextRun = !task.isTimerRunning
                task.copy(
                    isTimerRunning = nextRun,
                    stage = if (nextRun) Stage.IN_PROGRESS else task.stage,
                    isSynced = false
                )
            } else {
                task.copy(isTimerRunning = false)
            }
        }
    }

    override suspend fun persistTimerTick(taskId: Long, newTrackedSeconds: Long) {
        tasksFlow.value = tasksFlow.value.map { task ->
            if (task.id == taskId && task.isTimerRunning) {
                task.copy(trackedSeconds = newTrackedSeconds, isSynced = false)
            } else {
                task
            }
        }
    }

    override suspend fun updateTaskStage(taskId: Long, newStage: Stage) {
        tasksFlow.value = tasksFlow.value.map { task ->
            if (task.id == taskId) {
                task.copy(stage = newStage, isTimerRunning = false, isSynced = false)
            } else {
                task
            }
        }
    }

    override suspend fun markTaskCompleted(taskId: Long) {
        tasksFlow.value = tasksFlow.value.map { task ->
            if (task.id == taskId) {
                task.copy(stage = Stage.DONE, isTimerRunning = false, isSynced = false)
            } else {
                task
            }
        }
    }

    override suspend fun createQuickTask(
        title: String,
        category: TicketCategory,
        priority: Priority,
        epochDay: Long,
        location: String,
        scheduledTimeMinutes: Int,
        technicalNotes: String
    ): Task {
        val created = Task(
            id = -(tasksFlow.value.size + 100L),
            ticketCode = "${category.code}-${tasksFlow.value.size + 1}",
            title = title,
            category = category,
            scheduledDateEpochDay = epochDay,
            scheduledTimeMinutes = scheduledTimeMinutes,
            timeSlotLabel = DateEpochUtil.formatMinutesToTimeSlot(scheduledTimeMinutes),
            priority = priority,
            stage = Stage.BACKLOG,
            technicalNotes = technicalNotes,
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = location,
            slaTargetTime = "18:00 WIB",
            checklist = emptyList(),
            assignees = emptyList(),
            isSynced = false
        )
        tasksFlow.value = tasksFlow.value + created
        return created
    }

    override suspend fun login(email: String, password: String): Result<String> = Result.success("fake_token")
}
