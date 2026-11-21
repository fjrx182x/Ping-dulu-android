package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.Assignee
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.presentation.schedule.ScheduleViewModel
import com.example.util.NotificationHelper
import com.example.util.TaskNotificationDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Tahap3NotificationDelegationAuditTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedTodayEpochDay = 20776L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeTask(
        id: Long,
        ticketCode: String = "NET-$id",
        title: String = "Gangguan Switch Lantai $id",
        epochDay: Long = fixedTodayEpochDay,
        priority: Priority = Priority.P2_HIGH,
        stage: Stage = Stage.BACKLOG,
        assignedToId: Long? = null,
        assignees: List<Assignee> = emptyList()
    ): Task = Task(
        id = id,
        ticketCode = ticketCode,
        title = title,
        category = TicketCategory.NET,
        scheduledDateEpochDay = epochDay,
        scheduledTimeMinutes = 540,
        timeSlotLabel = "09:00 - 10:30",
        priority = priority,
        stage = stage,
        technicalNotes = "Catatan uji notifikasi",
        trackedSeconds = 0L,
        isTimerRunning = false,
        timerStartedAtMillis = null,
        location = "Gedung NOC Lt. 2",
        slaTargetTime = "",
        checklist = emptyList(),
        assignees = assignees,
        isSynced = true,
        createdAtMillis = 1_795_100_000_000L + id,
        dueDateMillis = null,
        assignedToId = assignedToId,
        labels = listOf("Wi-Fi", "Jaringan")
    )

    @Test
    fun notificationHelper_createsHighImportanceSoundAndVibrationChannel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val helper = NotificationHelper(app)

        helper.ensureChannelCreated()

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationHelper.CHANNEL_NAME, channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.shouldVibrate())
        assertNotNull(channel.sound)
    }

    @Test
    fun notificationHelper_respectsAndroid13RuntimePermissionAndBuildsDeepLinkNotification() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        val helper = NotificationHelper(app)
        val task = makeTask(
            id = 99L,
            ticketCode = "NET-99",
            title = "Router Lantai 2 Down",
            assignedToId = 3L
        )

        // 1. Denied permission -> gracefully returns false without SecurityException
        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(helper.hasPostNotificationPermission())
        assertFalse(helper.showAssignedTaskNotification(task))

        // 2. Granted permission -> posts notification with exact title, body & deep-link PendingIntent
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(helper.hasPostNotificationPermission())
        assertTrue(helper.showAssignedTaskNotification(task))

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(nm).allNotifications
        assertEquals(1, posted.size)

        val notification = posted.single()
        assertEquals(
            NotificationHelper.NOTIFICATION_TITLE,
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
        assertEquals(
            "[NET-99] Router Lantai 2 Down",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
        assertTrue((notification.flags and Notification.FLAG_AUTO_CANCEL) != 0)

        val contentIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, contentIntent.component?.className)
        assertEquals(99L, contentIntent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L))
    }

    @Test
    fun notificationHelper_generatesDistinctPositiveIdsForTasks() {
        val id1 = NotificationHelper.notificationIdForTask(1L)
        val id2 = NotificationHelper.notificationIdForTask(2L)
        val idNeg = NotificationHelper.notificationIdForTask(-500L)
        val idMin = NotificationHelper.notificationIdForTask(Long.MIN_VALUE)

        assertTrue(id1 >= 1000)
        assertTrue(id2 >= 1000)
        assertTrue(idNeg >= 1000)
        assertTrue(idMin >= 1000)
        assertTrue(id1 != id2)
    }

    @Test
    fun viewModel_antiSpamBaselineAndNewDelegationDetectionAcrossSyncAndReassignment() = runTest(testDispatcher) {
        val existingMine = makeTask(id = 10L, assignedToId = 3L)
        val existingOther = makeTask(id = 11L, assignedToId = 9L)
        val repo = DelegationFakeRepository(listOf(existingMine, existingOther))
        val dispatcher = RecordingNotificationDispatcher()

        val vm = ScheduleViewModel(
            repository = repo,
            notificationDispatcher = dispatcher,
            baseEpochDayProvider = { fixedTodayEpochDay },
            enableBackgroundTicker = false,
            enableSseStream = false,
            initialUserIdForTesting = 3L
        )
        val collector = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        // 1. Pre-existing assigned task (id = 10) must NOT spam on startup
        assertTrue(dispatcher.ensureChannelCalled)
        assertTrue(dispatcher.notifiedTasks.isEmpty())

        // 2. New task assigned via assignedToId = 3 -> fires notification once + logs in-app alert
        val newDirect = makeTask(id = 20L, ticketCode = "SRV-20", title = "Backup NAS Penuh", assignedToId = 3L, priority = Priority.P1_CRITICAL)
        repo.emitTasks(listOf(existingMine, existingOther, newDirect))
        advanceUntilIdle()

        assertEquals(listOf(20L), dispatcher.notifiedTasks.map { it.id })
        assertTrue(vm.uiState.value.notifications.any { it.badgeText == "DELEGASI BARU" && it.taskId == 20L })

        // 3. New task assigned via multi-assignee list (id = 3) -> fires notification
        val newMulti = makeTask(
            id = 21L,
            ticketCode = "NET-21",
            title = "Cek Switch Core",
            assignedToId = null,
            assignees = listOf(Assignee(id = 3L, name = "Fajar", role = "admin", isLead = false))
        )
        // Also add a task for another user (id = 9) and an already DONE task for user 3 -> both must be ignored
        val otherUserTask = makeTask(id = 22L, assignedToId = 9L)
        val alreadyDoneTask = makeTask(id = 23L, assignedToId = 3L, stage = Stage.DONE)
        repo.emitTasks(listOf(existingMine, existingOther, newDirect, newMulti, otherUserTask, alreadyDoneTask))
        advanceUntilIdle()

        assertEquals(listOf(20L, 21L), dispatcher.notifiedTasks.map { it.id })

        // 4. Existing task #11 previously assigned to user 9 gets reassigned to user 3 -> fires notification!
        val reassignedToMe = existingOther.copy(assignedToId = 3L)
        repo.emitTasks(listOf(existingMine, reassignedToMe, newDirect, newMulti, otherUserTask, alreadyDoneTask))
        advanceUntilIdle()

        assertEquals(listOf(20L, 21L, 11L), dispatcher.notifiedTasks.map { it.id })

        // 5. Re-emitting identical snapshot does NOT fire duplicate notifications
        repo.emitTasks(listOf(existingMine, reassignedToMe, newDirect, newMulti, otherUserTask, alreadyDoneTask))
        advanceUntilIdle()
        assertEquals(listOf(20L, 21L, 11L), dispatcher.notifiedTasks.map { it.id })

        // 6. Self-created task via Quick Add (even if server immediately assigns positive ID + currentUserId) must NOT self-notify
        vm.createQuickTask(
            title = "Tiket Buatan Sendiri",
            category = TicketCategory.NET,
            priority = Priority.P2_HIGH,
            location = "Gudang IT"
        )
        advanceUntilIdle()
        assertEquals(listOf(20L, 21L, 11L), dispatcher.notifiedTasks.map { it.id })

        vm.stopBackgroundJobs()
        collector.cancel()
    }

    @Test
    fun viewModel_openTaskFromNotification_supportsWarmStartAndDeferredColdStartDeepLink() = runTest(testDispatcher) {
        val dayPlus2 = fixedTodayEpochDay + 2L
        val existingDay2Task = makeTask(id = 55L, epochDay = dayPlus2, assignedToId = 3L)
        val repo = DelegationFakeRepository(listOf(existingDay2Task))

        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { fixedTodayEpochDay },
            enableBackgroundTicker = false,
            enableSseStream = false,
            initialUserIdForTesting = 3L
        )
        val collector = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        // 1. Warm start: task 55 already loaded on Day+2 -> switches selected day & opens detail sheet
        assertEquals(fixedTodayEpochDay, vm.uiState.value.selectedEpochDay)
        vm.openTaskFromNotification(55L)
        advanceUntilIdle()

        assertEquals(dayPlus2, vm.uiState.value.selectedEpochDay)
        assertEquals(55L, vm.uiState.value.selectedTaskForSheet?.id)

        vm.dismissTaskDetail()
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.selectedTaskForSheet)

        // 2. Cold start: notification tapped BEFORE task 888 finishes syncing from server/Room
        vm.openTaskFromNotification(888L)
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.selectedTaskForSheet)

        // Task 888 arrives on Day+4 -> pending deep-link auto-focuses Day+4 and opens detail sheet!
        val dayPlus4 = fixedTodayEpochDay + 4L
        val syncedTask = makeTask(id = 888L, epochDay = dayPlus4, assignedToId = 3L)
        repo.emitTasks(listOf(existingDay2Task, syncedTask))
        advanceUntilIdle()

        assertEquals(dayPlus4, vm.uiState.value.selectedEpochDay)
        assertEquals(888L, vm.uiState.value.selectedTaskForSheet?.id)

        vm.stopBackgroundJobs()
        collector.cancel()
    }
}

private class RecordingNotificationDispatcher : TaskNotificationDispatcher {
    var ensureChannelCalled = false
    val notifiedTasks = mutableListOf<Task>()

    override fun ensureChannelCreated() {
        ensureChannelCalled = true
    }

    override fun showAssignedTaskNotification(task: Task): Boolean {
        notifiedTasks += task
        return true
    }
}

private class DelegationFakeRepository(
    initialTasks: List<Task>
) : TaskRepository {

    private val tasksFlow = MutableStateFlow(initialTasks)

    fun emitTasks(tasks: List<Task>) {
        tasksFlow.value = tasks
    }

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay == epochDay } }

    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> =
        tasksFlow.map { list -> list.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow

    override fun observeActiveHeroTask(): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.stage == Stage.IN_PROGRESS } }

    override fun observeTaskById(taskId: Long): Flow<Task?> =
        tasksFlow.map { list -> list.firstOrNull { it.id == taskId } }

    override fun observeUnsyncedCount(): Flow<Int> =
        tasksFlow.map { list -> list.count { !it.isSynced } }

    override suspend fun initializeOfflineCache() = Unit

    override suspend fun refreshTasksFromServer(): SyncResult =
        SyncResult.Success(downloadedCount = tasksFlow.value.size, uploadedCount = 0)

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
    ): Task {
        // Simulate online server immediately assigning positive ID + currentUserId = 3L
        val created = Task(
            id = 500L + tasksFlow.value.size,
            ticketCode = "${category.code}-500",
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
            slaTargetTime = "",
            checklist = emptyList(),
            assignees = listOf(Assignee(id = 3L, name = "Fajar", role = "admin", isLead = true)),
            isSynced = true,
            dueDateMillis = null,
            assignedToId = 3L,
            labels = labels
        )
        tasksFlow.value = tasksFlow.value + created
        return created
    }

    override suspend fun login(username: String, password: String): Result<String> =
        Result.success("token")

    override suspend fun clearSyncedCacheOnLogout() {
        tasksFlow.value = emptyList()
    }
}
