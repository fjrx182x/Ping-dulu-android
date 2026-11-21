package com.example

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TaskEntity
import com.example.data.local.toDomain
import com.example.data.remote.dto.TaskDto
import com.example.data.remote.dto.toEntity
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.presentation.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlannedWorkDataLayerAuditTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedToday = 20776L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun roomMigration2To3_preservesExistingTasksAndAllowsNullScheduledTimeMinutes() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "migration_audit_v2_v3_${System.nanoTime()}.db"
        val dbFile = app.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create an authentic Room v2 SQLite database with NOT NULL scheduled_time_minutes
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE `tasks` (
                    `id` INTEGER NOT NULL,
                    `ticket_code` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `scheduled_date_epoch_day` INTEGER NOT NULL,
                    `scheduled_time_minutes` INTEGER NOT NULL,
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
            sqlite.execSQL("CREATE INDEX `index_tasks_scheduled_date_epoch_day` ON `tasks` (`scheduled_date_epoch_day`)")
            sqlite.execSQL("CREATE INDEX `index_tasks_stage` ON `tasks` (`stage`)")
            sqlite.execSQL("CREATE INDEX `index_tasks_is_synced` ON `tasks` (`is_synced`)")
            sqlite.execSQL(
                """
                INSERT INTO `tasks` VALUES (
                    101, 'NET-101', 'Maintenance Planned v2', 'NET', 20776, 600, '10:00 - 11:30',
                    'P2_HIGH', 'BACKLOG', 'Catatan lama', 0, 0, NULL, 'Gedung A', '',
                    '[]', '[]', 1, 1795100000000, NULL, 3, '["Jaringan"]', 1795100000000
                )
                """.trimIndent()
            )
            sqlite.version = 2
        }

        // 2. Open via Room v3 with MIGRATION_2_3 (strict Room schema validation runs automatically!)
        val migratedDb = Room.databaseBuilder(app, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migratedDb.taskDao()
            val existing = dao.getTaskByIdOnce(101L)
            assertEquals("NET-101", existing?.ticketCode)
            assertEquals(600, existing?.scheduledTimeMinutes)

            // 3. Insert an immediate incident task with NULL scheduled_time_minutes
            val immediateIncident = TaskEntity(
                id = 102L,
                ticketCode = "NET-102",
                title = "Insiden Wi-Fi Langsung",
                category = TicketCategory.NET.code,
                scheduledDateEpochDay = fixedToday,
                scheduledTimeMinutes = null,
                timeSlotLabel = "",
                priority = Priority.P1_CRITICAL.apiValue,
                stage = Stage.BACKLOG.apiValue,
                technicalNotes = "",
                trackedSeconds = 0L,
                isTimerRunning = false,
                timerStartedAtMillis = null,
                location = "Lobby",
                slaTargetTime = "",
                checklist = emptyList(),
                assignees = emptyList(),
                isSynced = false,
                createdAtMillis = 1_795_200_000_000L,
                dueDateMillis = null,
                assignedToId = 3L,
                labels = listOf("Wi-Fi", "Urgent"),
                updatedAtMillis = 1_795_200_000_000L
            )
            dao.upsertTask(immediateIncident)

            val savedNullTask = dao.getTaskByIdOnce(102L)
            assertNull(savedNullTask?.scheduledTimeMinutes)
            assertFalse(savedNullTask!!.toDomain().hasScheduledClock)
            assertTrue(existing!!.toDomain().hasScheduledClock)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun roomDao_getTasksByEpochDay_ordersScheduledChronologicallyThenUnscheduledByPriorityAndCreatedAtDesc() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = db.taskDao()
            val task10amP3 = entityForSort(id = 1L, scheduledMinutes = 600, priority = Priority.P3_NORMAL, createdAt = 1_000L)
            val task09amP2 = entityForSort(id = 2L, scheduledMinutes = 540, priority = Priority.P2_HIGH, createdAt = 1_000L)
            val unscheduledP1Old = entityForSort(id = 3L, scheduledMinutes = null, priority = Priority.P1_CRITICAL, createdAt = 2_000L)
            val unscheduledP1New = entityForSort(id = 4L, scheduledMinutes = null, priority = Priority.P1_CRITICAL, createdAt = 5_000L)
            val unscheduledP2 = entityForSort(id = 5L, scheduledMinutes = null, priority = Priority.P2_HIGH, createdAt = 9_000L)
            val futureDayTask = entityForSort(id = 6L, epochDay = fixedToday + 2L, scheduledMinutes = 480, priority = Priority.P1_CRITICAL, createdAt = 9_999L)

            dao.upsertTasks(listOf(task10amP3, task09amP2, unscheduledP1Old, unscheduledP1New, unscheduledP2, futureDayTask))

            val todayOrdered = dao.getTasksByEpochDay(fixedToday).first()
            assertEquals(listOf(2L, 1L, 4L, 3L, 5L), todayOrdered.map { it.id })

            // Future planned task ONLY shows when its own date is queried
            val futureOrdered = dao.getTasksByEpochDay(fixedToday + 2L).first()
            assertEquals(listOf(6L), futureOrdered.map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun taskDto_preservesNullScheduledTimeMinutesForImmediateTickets() {
        val immediateDto = TaskDto(
            id = 500L,
            ticketCode = "NET-500",
            title = "Wi-Fi Mati",
            projectOrCategory = "NET",
            scheduledDateEpochDay = fixedToday,
            scheduledTimeMinutes = null,
            priority = "P1_CRITICAL",
            stage = "BACKLOG"
        )
        val plannedDto = immediateDto.copy(id = 501L, scheduledTimeMinutes = 600)

        val immediateEntity = immediateDto.toEntity()
        val plannedEntity = plannedDto.toEntity()

        assertNull(immediateEntity.scheduledTimeMinutes)
        assertEquals("", immediateEntity.timeSlotLabel)
        assertEquals(600, plannedEntity.scheduledTimeMinutes)
        assertEquals("10:00 - 11:30", plannedEntity.timeSlotLabel)
    }

    @Test
    fun dateEpochUtil_utcDatePickerConversionAndScheduledClockFormattingAreExact() {
        val wib = TimeZone.getTimeZone("Asia/Jakarta")
        val targetDay = DateEpochUtil.toEpochDay(year = 2026, monthZeroBased = 10, dayOfMonth = 25)
        val utcPickerMillis = DateEpochUtil.utcPickerMillisFromEpochDay(targetDay)

        assertEquals(targetDay, DateEpochUtil.epochDayFromUtcPickerMillis(utcPickerMillis))
        assertEquals("10:00 WIB", DateEpochUtil.formatScheduledClock(600, timeZone = wib))
        assertEquals("00:00 WIB", DateEpochUtil.formatScheduledClock(-10, timeZone = wib))
        assertEquals("23:59 WIB", DateEpochUtil.formatScheduledClock(9999, timeZone = wib))
        assertEquals("14:30", DateEpochUtil.formatScheduledClockCompact(870))
    }

    @Test
    fun viewModel_plannedFutureTaskDoesNotPolluteTodayAndSortsIdenticallyToDao() = runTest(testDispatcher) {
        val repo = PlannedWorkFakeRepo(emptyList())
        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { fixedToday },
            enableBackgroundTicker = false,
            enableSseStream = false
        )
        val collector = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        // 1. Create an immediate incident task for today (scheduledTimeMinutes = null)
        vm.createQuickTask(
            title = "Insiden Router Hari Ini",
            category = TicketCategory.NET,
            priority = Priority.P1_CRITICAL,
            location = "Lt. 1",
            scheduledTimeMinutes = null,
            targetEpochDay = null
        )
        // 2. Create a planned task for today at 10:00 (600 min) with lower priority -> must appear BEFORE unscheduled P1
        vm.createQuickTask(
            title = "Maintenance Terjadwal 10:00",
            category = TicketCategory.SRV,
            priority = Priority.P3_NORMAL,
            location = "Data Center",
            scheduledTimeMinutes = 600,
            targetEpochDay = fixedToday
        )
        // 3. Create a planned future task for today + 3 at 09:30 (570 min) -> must NOT pollute Today tab!
        val futureDay = fixedToday + 3L
        vm.createQuickTask(
            title = "Upgrade Switch Core Masa Depan",
            category = TicketCategory.NET,
            priority = Priority.P2_HIGH,
            location = "NOC",
            scheduledTimeMinutes = 570,
            targetEpochDay = futureDay
        )
        advanceUntilIdle()

        // Today remains selected and contains ONLY today's 2 tasks in chronological-first order
        assertEquals(fixedToday, vm.uiState.value.selectedEpochDay)
        assertEquals(
            listOf("Maintenance Terjadwal 10:00", "Insiden Router Hari Ini"),
            vm.uiState.value.filteredTasks.map { it.title }
        )

        // Switching 7-day strip to today + 3 reveals the planned future task with its clock!
        vm.onDaySelected(futureDay)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.filteredTasks.size)
        val futureTask = vm.uiState.value.filteredTasks.single()
        assertEquals("Upgrade Switch Core Masa Depan", futureTask.title)
        assertEquals(570, futureTask.scheduledTimeMinutes)
        assertTrue(futureTask.isPlannedWork(fixedToday))

        vm.stopBackgroundJobs()
        collector.cancel()
    }

    private fun entityForSort(
        id: Long,
        epochDay: Long = fixedToday,
        scheduledMinutes: Int?,
        priority: Priority,
        createdAt: Long
    ): TaskEntity = TaskEntity(
        id = id,
        ticketCode = "TST-$id",
        title = "Task #$id",
        category = TicketCategory.NET.code,
        scheduledDateEpochDay = epochDay,
        scheduledTimeMinutes = scheduledMinutes,
        timeSlotLabel = DateEpochUtil.formatMinutesToTimeSlot(scheduledMinutes),
        priority = priority.apiValue,
        stage = Stage.BACKLOG.apiValue,
        technicalNotes = "",
        trackedSeconds = 0L,
        isTimerRunning = false,
        timerStartedAtMillis = null,
        location = "Lab",
        slaTargetTime = "",
        checklist = emptyList(),
        assignees = emptyList(),
        isSynced = true,
        createdAtMillis = createdAt,
        dueDateMillis = null,
        assignedToId = 3L,
        labels = emptyList(),
        updatedAtMillis = createdAt
    )
}

private class PlannedWorkFakeRepo(initial: List<Task>) : TaskRepository {
    private val tasksFlow = MutableStateFlow(initial)
    private var seq = 1L

    override fun observeTasksByEpochDay(epochDay: Long): Flow<List<Task>> =
        tasksFlow.map { it.filter { t -> t.scheduledDateEpochDay == epochDay } }

    override fun observeTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<Task>> =
        tasksFlow.map { it.filter { t -> t.scheduledDateEpochDay in startEpochDay..endEpochDay } }

    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow
    override fun observeActiveHeroTask(): Flow<Task?> = tasksFlow.map { it.firstOrNull { t -> t.stage == Stage.IN_PROGRESS } }
    override fun observeTaskById(taskId: Long): Flow<Task?> = tasksFlow.map { it.firstOrNull { t -> t.id == taskId } }
    override fun observeUnsyncedCount(): Flow<Int> = tasksFlow.map { it.count { t -> !t.isSynced } }
    override suspend fun initializeOfflineCache() = Unit
    override suspend fun refreshTasksFromServer(): SyncResult = SyncResult.Success(0, 0)
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
        labels: List<String>
    ): Task {
        val nextId = -(seq++)
        val created = Task(
            id = nextId,
            ticketCode = "${category.code}-${-nextId}",
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
            assignees = emptyList(),
            isSynced = false,
            createdAtMillis = 1_795_000_000_000L + seq,
            dueDateMillis = null,
            labels = labels
        )
        tasksFlow.value = tasksFlow.value + created
        return created
    }

    override suspend fun login(username: String, password: String): Result<String> = Result.success("tok")
    override suspend fun clearSyncedCacheOnLogout() { tasksFlow.value = emptyList() }
}
