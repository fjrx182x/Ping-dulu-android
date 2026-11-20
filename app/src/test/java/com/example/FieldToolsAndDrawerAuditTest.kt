package com.example

import com.example.data.local.SeedTaskData
import com.example.data.local.ShiftStatus
import com.example.data.local.toDomain
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.domain.tools.FieldNetworkTools
import com.example.presentation.schedule.ScheduleViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

@OptIn(ExperimentalCoroutinesApi::class)
class FieldToolsAndDrawerAuditTest {

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
    fun subnetCalculator_computesStandardSlash24AndSlash30AndSpecialPrefixes() {
        val slash24 = FieldNetworkTools.calculateSubnet("192.168.10.42", 24)
        assertNotNull(slash24)
        assertEquals("192.168.10.0/24", slash24!!.cidrNotation)
        assertEquals("192.168.10.0", slash24.networkAddress)
        assertEquals("192.168.10.255", slash24.broadcastAddress)
        assertEquals("255.255.255.0", slash24.subnetMask)
        assertEquals("0.0.0.255", slash24.wildcardMask)
        assertEquals("192.168.10.1", slash24.firstUsableHost)
        assertEquals("192.168.10.254", slash24.lastUsableHost)
        assertEquals(254L, slash24.usableHostCount)
        assertTrue(slash24.addressType.contains("RFC 1918"))

        val slash30 = FieldNetworkTools.calculateSubnet("10.20.30.5", 30)
        assertNotNull(slash30)
        assertEquals("10.20.30.4/30", slash30!!.cidrNotation)
        assertEquals("10.20.30.7", slash30.broadcastAddress)
        assertEquals("255.255.255.252", slash30.subnetMask)
        assertEquals("10.20.30.5", slash30.firstUsableHost)
        assertEquals("10.20.30.6", slash30.lastUsableHost)
        assertEquals(2L, slash30.usableHostCount)

        val slash31 = FieldNetworkTools.calculateSubnet("172.16.0.1", 31)
        assertNotNull(slash31)
        assertEquals(2L, slash31!!.usableHostCount)
        assertEquals("172.16.0.0", slash31.firstUsableHost)
        assertEquals("172.16.0.1", slash31.lastUsableHost)

        val slash32 = FieldNetworkTools.calculateSubnet("8.8.8.8", 32)
        assertNotNull(slash32)
        assertEquals(1L, slash32!!.usableHostCount)
        assertEquals("8.8.8.8", slash32.networkAddress)
        assertTrue(slash32.addressType.contains("Public"))

        val slash0 = FieldNetworkTools.calculateSubnet("0.0.0.0", 0)
        assertNotNull(slash0)
        assertEquals("0.0.0.0", slash0!!.subnetMask)
        assertEquals("255.255.255.255", slash0.broadcastAddress)
    }

    @Test
    fun subnetCalculator_rejectsInvalidIpsAndPrefixes() {
        assertNull(FieldNetworkTools.calculateSubnet("999.1.1.1", 24))
        assertNull(FieldNetworkTools.calculateSubnet("192.168.1", 24))
        assertNull(FieldNetworkTools.calculateSubnet("", 24))
        assertNull(FieldNetworkTools.calculateSubnet("192.168.1.1", -1))
        assertNull(FieldNetworkTools.calculateSubnet("192.168.1.1", 33))
    }

    @Test
    fun pingProbe_rejectsBlankHostAndSucceedsAgainstLocalTcpSocket() = runTest {
        val blankResult = FieldNetworkTools.probeHost("   ", 443)
        assertFalse(blankResult.reachable)
        assertNull(blankResult.latencyMs)

        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val acceptThread = Thread {
                runCatching { server.accept().close() }
            }.apply { start() }

            val result = FieldNetworkTools.probeHost("http://127.0.0.1:$port/health", port = port)
            acceptThread.join(1_500L)

            assertTrue(result.reachable)
            assertEquals("127.0.0.1", result.resolvedIp)
            assertNotNull(result.latencyMs)
            assertTrue(result.latencyMs!! >= 1L)
        }
    }

    @Test
    fun drawerFiltersAndShiftStatus_updateScheduleUiStateCorrectly() = runTest(testDispatcher) {
        val seeded = SeedTaskData.createInitialTasks(baseEpochDay = baseEpochDay).map { it.toDomain() }
        val repo = DrawerAuditFakeRepository(seeded)
        val vm = ScheduleViewModel(
            repository = repo,
            baseEpochDayProvider = { baseEpochDay },
            hourOfDayProvider = { 10 },
            enableBackgroundTicker = false,
            enableSseStream = false
        )

        val collector = backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        // Default state: no drawer filters, 2 tasks today (NET-1 P1, SRV-3 P2)
        assertEquals(0, vm.uiState.value.activeDrawerFilterCount)
        assertEquals(2, vm.uiState.value.filteredTasks.size)
        assertEquals(ShiftStatus.ON_DUTY, vm.uiState.value.shiftStatus)

        // Toggle P1 critical only
        vm.toggleOnlyCriticalP1()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeDrawerFilterCount)
        assertEquals(1, vm.uiState.value.filteredTasks.size)
        assertEquals(Priority.P1_CRITICAL, vm.uiState.value.filteredTasks.first().priority)

        // Add Category filter SRV (day 0 has no P1 SRV -> empty)
        vm.selectCategoryFilter(TicketCategory.SRV)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.activeDrawerFilterCount)
        assertEquals(0, vm.uiState.value.filteredTasks.size)

        // Reset drawer filters -> back to 2 tasks
        vm.resetDrawerFilters()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.activeDrawerFilterCount)
        assertEquals(2, vm.uiState.value.filteredTasks.size)

        // Toggle My Tickets filter (Fajar) -> includes NET-1 & SRV-3 where Fajar is assigned
        vm.toggleOnlyMyTickets()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeDrawerFilterCount)
        assertTrue(vm.uiState.value.filteredTasks.all { task ->
            task.assignees.any { it.name.contains("Fajar", ignoreCase = true) }
        })

        // Update shift status
        vm.setShiftStatus(ShiftStatus.STANDBY)
        advanceUntilIdle()
        assertEquals(ShiftStatus.STANDBY, vm.uiState.value.shiftStatus)
        assertTrue(vm.uiState.value.syncMessage?.contains("Standby") == true)

        // Trigger mutation -> unsyncedTasks list populates
        vm.toggleChecklistItem(59L, 1L)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.unsyncedTasks.any { it.id == 59L })

        vm.stopBackgroundJobs()
        collector.cancel()
    }
}

private class DrawerAuditFakeRepository(initial: List<Task>) : TaskRepository {
    private val tasksFlow = MutableStateFlow(initial)

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
        SyncResult.OfflineFallback(pendingUploadCount = tasksFlow.value.count { !it.isSynced }, reason = "Offline")

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
        scheduledTimeMinutes: Int,
        technicalNotes: String
    ): Task = tasksFlow.value.first()

    override suspend fun login(email: String, password: String): Result<String> = Result.success("token")
}
