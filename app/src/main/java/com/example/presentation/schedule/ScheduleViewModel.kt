package com.example.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppPreferences
import com.example.data.local.ShiftStatus
import com.example.data.local.ThemePreference
import com.example.data.local.UserSessionPrefs
import com.example.data.remote.SseConnectionState
import com.example.data.remote.SseEvent
import com.example.data.remote.SseEventClient
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleViewModel(
    private val repository: TaskRepository,
    private val appPreferences: AppPreferences? = null,
    private val sseEventClient: SseEventClient? = null,
    private val baseEpochDayProvider: () -> Long = { DateEpochUtil.todayEpochDay() },
    private val hourOfDayProvider: () -> Int = { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) },
    private val enableBackgroundTicker: Boolean = true,
    private val enableSseStream: Boolean = true
) : ViewModel() {

    private val baseEpochDay: Long = baseEpochDayProvider()
    private val horizonEpochDays: List<Long> = (0L..6L).map { offset -> baseEpochDay + offset }

    private val selectedEpochDayFlow = MutableStateFlow(baseEpochDay)
    private val selectedStatusFilterFlow = MutableStateFlow(StatusFilter.ALL)
    private val layoutModeFlow = MutableStateFlow(LayoutMode.GRID_2_COLUMN)
    private val selectedSheetTaskIdFlow = MutableStateFlow<Long?>(null)
    private val isSyncingFlow = MutableStateFlow(false)
    private val isLoggingInFlow = MutableStateFlow(false)
    private val syncMessageFlow = MutableStateFlow<String?>(null)
    private val sseStateFlow = MutableStateFlow(SseConnectionState.OFFLINE)
    private val localDarkOverrideFlow = MutableStateFlow<Boolean?>(null)
    private val localShiftStatusOverrideFlow = MutableStateFlow<ShiftStatus?>(null)
    private val onlyMyTicketsFlow = MutableStateFlow(false)
    private val onlyCriticalP1Flow = MutableStateFlow(false)
    private val selectedCategoryFilterFlow = MutableStateFlow<TicketCategory?>(null)
    private val unreadNotificationsFlow = MutableStateFlow(2)

    private var heroSeedJob: Job? = null
    private var tickerJob: Job? = null
    private var sseJob: Job? = null

    // Smooth 1-second stopwatch state separated from Room writes to avoid 1Hz full-table invalidation
    private data class LiveTimerSnapshot(
        val taskId: Long = -1L,
        val seconds: Long = 0L
    )

    private val liveTimerFlow = MutableStateFlow(LiveTimerSnapshot())

    private val sessionFlow: StateFlow<UserSessionPrefs> =
        (appPreferences?.sessionFlow ?: MutableStateFlow(UserSessionPrefs()))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = UserSessionPrefs()
            )

    private data class CoreUiControlBundle(
        val selectedEpochDay: Long,
        val statusFilter: StatusFilter,
        val layoutMode: LayoutMode,
        val sheetTaskId: Long?,
        val unreadNotifications: Int
    )

    private data class DrawerFilterBundle(
        val onlyMyTickets: Boolean,
        val onlyCriticalP1: Boolean,
        val selectedCategory: TicketCategory?,
        val localShiftStatusOverride: ShiftStatus?
    )

    private data class UiControlBundle(
        val selectedEpochDay: Long,
        val statusFilter: StatusFilter,
        val layoutMode: LayoutMode,
        val sheetTaskId: Long?,
        val unreadNotifications: Int,
        val drawerFilters: DrawerFilterBundle
    )

    private data class SyncAndSessionBundle(
        val isSyncing: Boolean,
        val isLoggingIn: Boolean,
        val syncMessage: String?,
        val sseState: SseConnectionState,
        val unsyncedCount: Int,
        val session: UserSessionPrefs,
        val localDarkOverride: Boolean?
    )

    private val coreUiControlFlow = combine(
        selectedEpochDayFlow,
        selectedStatusFilterFlow,
        layoutModeFlow,
        selectedSheetTaskIdFlow,
        unreadNotificationsFlow
    ) { epochDay, filter, layout, sheetTaskId, unread ->
        CoreUiControlBundle(
            selectedEpochDay = epochDay,
            statusFilter = filter,
            layoutMode = layout,
            sheetTaskId = sheetTaskId,
            unreadNotifications = unread
        )
    }

    private val drawerFilterFlow = combine(
        onlyMyTicketsFlow,
        onlyCriticalP1Flow,
        selectedCategoryFilterFlow,
        localShiftStatusOverrideFlow
    ) { onlyMine, onlyP1, category, shiftOverride ->
        DrawerFilterBundle(
            onlyMyTickets = onlyMine,
            onlyCriticalP1 = onlyP1,
            selectedCategory = category,
            localShiftStatusOverride = shiftOverride
        )
    }

    private val uiControlFlow = combine(
        coreUiControlFlow,
        drawerFilterFlow
    ) { core, drawer ->
        UiControlBundle(
            selectedEpochDay = core.selectedEpochDay,
            statusFilter = core.statusFilter,
            layoutMode = core.layoutMode,
            sheetTaskId = core.sheetTaskId,
            unreadNotifications = core.unreadNotifications,
            drawerFilters = drawer
        )
    }

    private data class SyncFlags(
        val isSyncing: Boolean,
        val isLoggingIn: Boolean,
        val syncMessage: String?,
        val sseState: SseConnectionState
    )

    private val syncFlagsFlow = combine(
        isSyncingFlow,
        isLoggingInFlow,
        syncMessageFlow,
        sseStateFlow
    ) { syncing, loggingIn, msg, sse ->
        SyncFlags(
            isSyncing = syncing,
            isLoggingIn = loggingIn,
            syncMessage = msg,
            sseState = sse
        )
    }

    private val syncAndSessionFlow = combine(
        syncFlagsFlow,
        repository.observeUnsyncedCount(),
        sessionFlow,
        localDarkOverrideFlow
    ) { flags, unsyncedCount, session, localDark ->
        SyncAndSessionBundle(
            isSyncing = flags.isSyncing,
            isLoggingIn = flags.isLoggingIn,
            syncMessage = flags.syncMessage,
            sseState = flags.sseState,
            unsyncedCount = unsyncedCount,
            session = session,
            localDarkOverride = localDark
        )
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.observeAllTasks(),
        repository.observeActiveHeroTask(),
        liveTimerFlow,
        uiControlFlow,
        syncAndSessionFlow
    ) { allTasks, activeHero, liveTimer, controls, syncBundle ->
        val techName = syncBundle.session.technicianName.ifBlank { "Fajar" }
        val drawerFilters = controls.drawerFilters
        val resolvedShiftStatus = drawerFilters.localShiftStatusOverride ?: syncBundle.session.shiftStatus

        val scopedTasks = allTasks.filter { task ->
            val matchesMyTickets = !drawerFilters.onlyMyTickets ||
                task.assignees.any { assignee ->
                    val assigneeName = assignee.name.trim()
                    assigneeName.isNotEmpty() && (
                        assigneeName.contains(techName, ignoreCase = true) ||
                            techName.contains(assigneeName, ignoreCase = true)
                        )
                }
            val matchesCriticalP1 = !drawerFilters.onlyCriticalP1 ||
                task.priority == Priority.P1_CRITICAL
            val matchesCategory = drawerFilters.selectedCategory == null ||
                task.category == drawerFilters.selectedCategory
            matchesMyTickets && matchesCriticalP1 && matchesCategory
        }

        val scheduleDays = buildScheduleDays(scopedTasks)
        val dayTasks = scopedTasks.filter { it.scheduledDateEpochDay == controls.selectedEpochDay }
            .sortedWith(compareBy<Task>({ it.priority.sortRank }, { it.scheduledTimeMinutes }, { it.id }))

        val counts = StatusFilterCounts(
            all = dayTasks.size,
            backlog = dayTasks.count { it.stage == Stage.BACKLOG },
            inProgress = dayTasks.count { it.stage == Stage.IN_PROGRESS },
            done = dayTasks.count { it.stage == Stage.DONE }
        )

        val filtered = when (controls.statusFilter) {
            StatusFilter.ALL -> dayTasks
            StatusFilter.BACKLOG -> dayTasks.filter { it.stage == Stage.BACKLOG }
            StatusFilter.IN_PROGRESS -> dayTasks.filter { it.stage == Stage.IN_PROGRESS }
            StatusFilter.DONE -> dayTasks.filter { it.stage == Stage.DONE }
        }

        val unsyncedTasks = allTasks
            .filter { !it.isSynced }
            .sortedByDescending { it.updatedAtMillis }

        val resolvedLiveHeroSeconds = when {
            activeHero == null -> 0L
            liveTimer.taskId == activeHero.id && liveTimer.seconds >= activeHero.trackedSeconds -> liveTimer.seconds
            else -> activeHero.trackedSeconds
        }

        val sheetTask = controls.sheetTaskId?.let { id ->
            allTasks.firstOrNull { it.id == id }
        }?.let { task ->
            if (activeHero != null && task.id == activeHero.id) {
                task.copy(trackedSeconds = resolvedLiveHeroSeconds)
            } else {
                task
            }
        }

        val syncBadge = when {
            syncBundle.isSyncing -> SyncStatusBadge.SYNCING
            syncBundle.sseState == SseConnectionState.CONNECTED_LIVE && syncBundle.unsyncedCount == 0 ->
                SyncStatusBadge.SYNCED_LIVE
            syncBundle.unsyncedCount > 0 -> SyncStatusBadge.PENDING_UPLOAD
            syncBundle.lastSyncedMillisOrZero() > 0L -> SyncStatusBadge.SYNCED_LOCAL
            else -> SyncStatusBadge.OFFLINE_CACHED
        }

        val isDark = syncBundle.localDarkOverride ?: (syncBundle.session.themePreference == ThemePreference.DARK)

        ScheduleUiState(
            technicianName = techName,
            technicianRole = syncBundle.session.technicianRole.ifBlank { "admin" },
            shiftStatus = resolvedShiftStatus,
            greetingTitle = buildGreeting(techName, hourOfDayProvider()),
            selectedEpochDay = controls.selectedEpochDay,
            horizonSpanLabel = DateEpochUtil.formatRangeSpan(
                horizonEpochDays.first(),
                horizonEpochDays.last()
            ),
            selectedFullDateLabel = DateEpochUtil.formatFullDate(controls.selectedEpochDay),
            scheduleDays = scheduleDays,
            activeHeroTask = activeHero,
            liveHeroSeconds = resolvedLiveHeroSeconds,
            allTasks = allTasks,
            filteredTasks = filtered,
            unsyncedTasks = unsyncedTasks,
            filterCounts = counts,
            selectedStatusFilter = controls.statusFilter,
            layoutMode = controls.layoutMode,
            onlyMyTickets = drawerFilters.onlyMyTickets,
            onlyCriticalP1 = drawerFilters.onlyCriticalP1,
            selectedCategoryFilter = drawerFilters.selectedCategory,
            selectedTaskForSheet = sheetTask,
            isSyncing = syncBundle.isSyncing,
            unsyncedCount = syncBundle.unsyncedCount,
            syncStatusBadge = syncBadge,
            syncMessage = syncBundle.syncMessage,
            sseState = syncBundle.sseState,
            serverBaseUrl = syncBundle.session.serverBaseUrl,
            isAuthenticated = syncBundle.session.authToken.isNotBlank(),
            isDarkTheme = isDark,
            lastSyncedAtMillis = syncBundle.session.lastSyncedAtMillis,
            unreadNotificationCount = controls.unreadNotifications,
            isLoggingIn = syncBundle.isLoggingIn
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScheduleUiState(
            selectedEpochDay = baseEpochDay,
            horizonSpanLabel = DateEpochUtil.formatRangeSpan(
                horizonEpochDays.first(),
                horizonEpochDays.last()
            ),
            selectedFullDateLabel = DateEpochUtil.formatFullDate(baseEpochDay),
            greetingTitle = buildGreeting("Fajar", hourOfDayProvider())
        )
    )

    init {
        viewModelScope.launch {
            repository.initializeOfflineCache()
        }

        observeActiveHeroForTimerSeed()

        if (enableBackgroundTicker) {
            startLiveStopwatchTicker()
        }

        if (enableSseStream && sseEventClient != null) {
            observeSseEvents()
        }
    }

    private fun SyncAndSessionBundle.lastSyncedMillisOrZero(): Long = session.lastSyncedAtMillis

    private fun observeActiveHeroForTimerSeed() {
        heroSeedJob = viewModelScope.launch {
            repository.observeActiveHeroTask()
                .map { task -> task?.let { Triple(it.id, it.trackedSeconds, it.isTimerRunning) } }
                .distinctUntilChanged()
                .collect { triple ->
                    if (triple == null) {
                        liveTimerFlow.value = LiveTimerSnapshot()
                    } else {
                        val (id, dbSeconds, _) = triple
                        val current = liveTimerFlow.value
                        if (current.taskId != id || dbSeconds > current.seconds) {
                            liveTimerFlow.value = LiveTimerSnapshot(taskId = id, seconds = dbSeconds)
                        }
                    }
                }
        }
    }

    private fun startLiveStopwatchTicker() {
        tickerJob = viewModelScope.launch {
            repository.observeActiveHeroTask()
                .map { hero -> hero?.takeIf { it.isTimerRunning }?.id }
                .distinctUntilChanged()
                .collectLatest { runningHeroId ->
                    if (runningHeroId == null) return@collectLatest
                    var ticksSinceFlush = 0
                    while (isActive) {
                        delay(1_000L)
                        val hero = uiState.value.activeHeroTask
                        if (hero == null || hero.id != runningHeroId || !hero.isTimerRunning) {
                            break
                        }
                        val current = liveTimerFlow.value
                        val base = if (current.taskId == hero.id) {
                            maxOf(current.seconds, hero.trackedSeconds)
                        } else {
                            hero.trackedSeconds
                        }
                        val nextSeconds = base + 1L
                        liveTimerFlow.value = LiveTimerSnapshot(taskId = hero.id, seconds = nextSeconds)
                        ticksSinceFlush++
                        if (ticksSinceFlush >= 10) {
                            ticksSinceFlush = 0
                            repository.persistTimerTick(hero.id, nextSeconds)
                        }
                    }
                }
        }
    }

    private fun observeSseEvents() {
        val client = sseEventClient ?: return
        sseJob = viewModelScope.launch {
            client.observeEvents(
                baseUrlProvider = { sessionFlow.value.serverBaseUrl }
            ).collect { event ->
                when (event) {
                    is SseEvent.StateChanged -> {
                        sseStateFlow.value = event.state
                    }
                    is SseEvent.DataMessage -> {
                        if (!isSyncingFlow.value) {
                            repository.refreshTasksFromServer()
                        }
                    }
                }
            }
        }
    }

    internal fun stopBackgroundJobs() {
        heroSeedJob?.cancel()
        tickerJob?.cancel()
        sseJob?.cancel()
    }

    fun onDaySelected(epochDay: Long) {
        selectedEpochDayFlow.value = epochDay
    }

    fun onStatusFilterSelected(filter: StatusFilter) {
        selectedStatusFilterFlow.value = filter
    }

    fun onToggleLayoutMode(mode: LayoutMode) {
        layoutModeFlow.value = mode
    }

    fun openTaskDetail(taskId: Long) {
        selectedSheetTaskIdFlow.value = taskId
    }

    fun dismissTaskDetail() {
        selectedSheetTaskIdFlow.value = null
    }

    fun toggleChecklistItem(taskId: Long, checklistId: Long) {
        viewModelScope.launch {
            repository.toggleChecklistItem(taskId, checklistId)
        }
    }

    fun toggleTaskTimer(taskId: Long) {
        viewModelScope.launch {
            flushCurrentRunningTimer()
            repository.toggleTaskTimer(taskId)
        }
    }

    fun updateTaskStage(taskId: Long, newStage: Stage) {
        viewModelScope.launch {
            flushCurrentRunningTimer()
            repository.updateTaskStage(taskId, newStage)
        }
    }

    fun markTaskCompleted(taskId: Long) {
        viewModelScope.launch {
            flushCurrentRunningTimer()
            repository.markTaskCompleted(taskId)
            syncMessageFlow.value = "Tiket ditandai SELESAI & tersimpan di database lokal."
        }
    }

    fun createQuickTask(
        title: String,
        category: TicketCategory,
        priority: Priority,
        location: String,
        scheduledTimeMinutes: Int = 960,
        technicalNotes: String = ""
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            val created = repository.createQuickTask(
                title = cleanTitle,
                category = category,
                priority = priority,
                epochDay = selectedEpochDayFlow.value,
                location = location,
                scheduledTimeMinutes = scheduledTimeMinutes,
                technicalNotes = technicalNotes
            )
            syncMessageFlow.value = "Tiket ${created.ticketCode} ditambahkan ke jadwal."
        }
    }

    fun triggerSync() {
        if (isSyncingFlow.value) return
        viewModelScope.launch {
            isSyncingFlow.value = true
            try {
                flushCurrentRunningTimer()
                when (val result = repository.refreshTasksFromServer()) {
                    is SyncResult.Success -> {
                        syncMessageFlow.value =
                            "Tersinkron ke server PingDulu (${result.downloadedCount} tiket, ${result.uploadedCount} unggah)."
                    }
                    is SyncResult.AuthRequired -> {
                        syncMessageFlow.value = result.message
                    }
                    is SyncResult.OfflineFallback -> {
                        val pendingInfo = if (result.pendingUploadCount > 0) {
                            "${result.pendingUploadCount} perubahan antre di Room lokal."
                        } else {
                            "Data Room lokal siap digunakan offline."
                        }
                        syncMessageFlow.value = "Mode Offline: $pendingInfo"
                    }
                }
            } finally {
                isSyncingFlow.value = false
            }
        }
    }

    fun toggleTheme() {
        val nextDark = !uiState.value.isDarkTheme
        localDarkOverrideFlow.value = nextDark
        viewModelScope.launch {
            appPreferences?.setThemePreference(
                if (nextDark) ThemePreference.DARK else ThemePreference.LIGHT
            )
        }
    }

    fun setShiftStatus(status: ShiftStatus) {
        localShiftStatusOverrideFlow.value = status
        viewModelScope.launch {
            appPreferences?.setShiftStatus(status)
            syncMessageFlow.value = "Status shift diperbarui: ${status.label}"
        }
    }

    fun toggleOnlyMyTickets() {
        onlyMyTicketsFlow.value = !onlyMyTicketsFlow.value
    }

    fun toggleOnlyCriticalP1() {
        onlyCriticalP1Flow.value = !onlyCriticalP1Flow.value
    }

    fun selectCategoryFilter(category: TicketCategory?) {
        selectedCategoryFilterFlow.value = if (selectedCategoryFilterFlow.value == category) {
            null
        } else {
            category
        }
    }

    fun resetDrawerFilters() {
        onlyMyTicketsFlow.value = false
        onlyCriticalP1Flow.value = false
        selectedCategoryFilterFlow.value = null
    }

    fun updateServerUrl(rawUrl: String) {
        viewModelScope.launch {
            appPreferences?.updateServerBaseUrl(rawUrl)
            syncMessageFlow.value = "URL server diperbarui."
        }
    }

    fun loginToServer(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            syncMessageFlow.value = "Email dan kata sandi wajib diisi."
            return
        }
        viewModelScope.launch {
            isLoggingInFlow.value = true
            try {
                val result = repository.login(email, password)
                result.fold(
                    onSuccess = {
                        syncMessageFlow.value = "Login berhasil! Menyinkronkan jadwal..."
                        triggerSync()
                    },
                    onFailure = { err ->
                        syncMessageFlow.value = err.localizedMessage ?: "Gagal terhubung ke server."
                    }
                )
            } finally {
                isLoggingInFlow.value = false
            }
        }
    }

    fun logoutFromServer() {
        viewModelScope.launch {
            appPreferences?.clearAuthToken()
            syncMessageFlow.value = "Token sesi dihapus. Data offline lokal tetap tersimpan."
        }
    }

    fun clearSyncMessage() {
        syncMessageFlow.value = null
    }

    fun dismissNotifications() {
        unreadNotificationsFlow.value = 0
    }

    fun flushActiveTimerOnBackground() {
        viewModelScope.launch {
            flushCurrentRunningTimer()
        }
    }

    private suspend fun flushCurrentRunningTimer() {
        val snapshot = liveTimerFlow.value
        val activeHero = uiState.value.activeHeroTask
        if (activeHero != null && activeHero.isTimerRunning && snapshot.taskId == activeHero.id && snapshot.seconds > activeHero.trackedSeconds) {
            repository.persistTimerTick(activeHero.id, snapshot.seconds)
        }
    }

    private fun buildScheduleDays(allTasks: List<Task>): List<DayHorizonModel> {
        val groupedByDay = allTasks.groupBy { it.scheduledDateEpochDay }
        return horizonEpochDays.map { epochDay ->
            val dayTasks = groupedByDay[epochDay].orEmpty()
            val indicator = when {
                dayTasks.isEmpty() -> DayIndicatorType.NONE
                dayTasks.any { it.priority == Priority.P1_CRITICAL && it.stage != Stage.DONE } ->
                    DayIndicatorType.CRITICAL_P1
                dayTasks.any { it.priority == Priority.P2_HIGH && it.stage != Stage.DONE } ->
                    DayIndicatorType.HIGH_P2
                dayTasks.all { it.stage == Stage.DONE } -> DayIndicatorType.ALL_DONE
                else -> DayIndicatorType.NORMAL
            }
            DayHorizonModel(
                epochDay = epochDay,
                dayShortName = DateEpochUtil.shortDayName(epochDay),
                dateNumber = DateEpochUtil.dayOfMonth(epochDay),
                fullDateLabel = DateEpochUtil.formatFullDate(epochDay),
                isToday = epochDay == baseEpochDay,
                taskCount = dayTasks.size,
                indicatorType = indicator
            )
        }
    }

    private fun buildGreeting(name: String, hourOfDay: Int): String {
        val timeGreeting = when (hourOfDay) {
            in 4..10 -> "Selamat Pagi"
            in 11..14 -> "Selamat Siang"
            in 15..17 -> "Selamat Sore"
            else -> "Selamat Malam"
        }
        return "$timeGreeting, $name"
    }
}

class ScheduleViewModelFactory(
    private val repository: TaskRepository,
    private val appPreferences: AppPreferences,
    private val sseEventClient: SseEventClient
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            return ScheduleViewModel(
                repository = repository,
                appPreferences = appPreferences,
                sseEventClient = sseEventClient
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
