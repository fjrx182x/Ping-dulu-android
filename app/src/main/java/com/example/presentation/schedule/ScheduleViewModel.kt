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
import com.example.util.TaskNotificationDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleViewModel(
    private val repository: TaskRepository,
    private val appPreferences: AppPreferences? = null,
    private val sseEventClient: SseEventClient? = null,
    private val notificationDispatcher: TaskNotificationDispatcher? = null,
    private val baseEpochDayProvider: () -> Long = { DateEpochUtil.todayEpochDay() },
    private val hourOfDayProvider: () -> Int = { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) },
    private val enableBackgroundTicker: Boolean = true,
    private val enableSseStream: Boolean = true,
    private val initialUserIdForTesting: Long = 0L
) : ViewModel() {

    private val initialTodayEpochDay: Long = baseEpochDayProvider()
    private val baseEpochDayFlow = MutableStateFlow(initialTodayEpochDay)

    private val selectedEpochDayFlow = MutableStateFlow(initialTodayEpochDay)
    private val selectedStatusFilterFlow = MutableStateFlow(StatusFilter.ALL)
    private val layoutModeFlow = MutableStateFlow(LayoutMode.GRID_2_COLUMN)
    private val selectedSheetTaskIdFlow = MutableStateFlow<Long?>(null)
    private val pendingNotificationTaskIdFlow = MutableStateFlow<Long?>(null)
    private val isSyncingFlow = MutableStateFlow(false)
    private val isLoggingInFlow = MutableStateFlow(false)
    private val syncMessageFlow = MutableStateFlow<String?>(null)
    private val loginErrorMessageFlow = MutableStateFlow<String?>(null)
    private val sseStateFlow = MutableStateFlow(SseConnectionState.OFFLINE)
    private val localDarkOverrideFlow = MutableStateFlow<Boolean?>(null)
    private val localShiftStatusOverrideFlow = MutableStateFlow<ShiftStatus?>(null)
    private val onlyMyTicketsFlow = MutableStateFlow(false)
    private val onlyCriticalP1Flow = MutableStateFlow(false)
    private val selectedCategoryFilterFlow = MutableStateFlow<TicketCategory?>(null)
    private val readNotificationIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val activityNotificationsFlow = MutableStateFlow<List<FieldNotificationItem>>(emptyList())
    private var activityNotificationSeq = 0L

    // Delegation notification tracking (anti-spam first-login baseline & self-created guard)
    private var trackedDelegationUserId: Long = 0L
    private var delegationBaselineEstablished: Boolean = false
    @Volatile
    private var suppressDelegationDuringInitialSync: Boolean = appPreferences != null
    private val knownAssignedTaskIds = mutableSetOf<Long>()
    private val pendingSelfCreatedTitles = mutableSetOf<String>()

    private var heroSeedJob: Job? = null
    private var tickerJob: Job? = null
    private var sseJob: Job? = null
    private var delegationJob: Job? = null
    private var deepLinkJob: Job? = null

    // Smooth 1-second stopwatch state separated from Room writes to avoid 1Hz full-table invalidation
    private data class LiveTimerSnapshot(
        val taskId: Long = -1L,
        val seconds: Long = 0L
    )

    private data class LoadedSession(
        val isLoaded: Boolean,
        val prefs: UserSessionPrefs
    )

    private val liveTimerFlow = MutableStateFlow(LiveTimerSnapshot())

    private val sessionStateFlow: StateFlow<LoadedSession> =
        (appPreferences?.sessionFlow?.map { LoadedSession(isLoaded = true, prefs = it) }
            ?: MutableStateFlow(
                LoadedSession(
                    isLoaded = true,
                    prefs = UserSessionPrefs(userId = initialUserIdForTesting)
                )
            ))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = LoadedSession(
                    isLoaded = appPreferences == null,
                    prefs = UserSessionPrefs(userId = initialUserIdForTesting)
                )
            )

    private data class CoreUiControlBundle(
        val baseEpochDay: Long,
        val selectedEpochDay: Long,
        val statusFilter: StatusFilter,
        val layoutMode: LayoutMode,
        val sheetTaskId: Long?
    )

    private data class DrawerFilterBundle(
        val onlyMyTickets: Boolean,
        val onlyCriticalP1: Boolean,
        val selectedCategory: TicketCategory?,
        val localShiftStatusOverride: ShiftStatus?
    )

    private data class NotificationStateBundle(
        val readIds: Set<String>,
        val activityEvents: List<FieldNotificationItem>
    )

    private data class UiControlBundle(
        val baseEpochDay: Long,
        val selectedEpochDay: Long,
        val statusFilter: StatusFilter,
        val layoutMode: LayoutMode,
        val sheetTaskId: Long?,
        val notificationState: NotificationStateBundle,
        val drawerFilters: DrawerFilterBundle
    )

    private data class SyncAndSessionBundle(
        val isSyncing: Boolean,
        val isLoggingIn: Boolean,
        val syncMessage: String?,
        val loginErrorMessage: String?,
        val sseState: SseConnectionState,
        val unsyncedCount: Int,
        val session: UserSessionPrefs,
        val isSessionLoaded: Boolean,
        val localDarkOverride: Boolean?
    )

    private val coreUiControlFlow = combine(
        baseEpochDayFlow,
        selectedEpochDayFlow,
        selectedStatusFilterFlow,
        layoutModeFlow,
        selectedSheetTaskIdFlow
    ) { baseDay, epochDay, filter, layout, sheetTaskId ->
        CoreUiControlBundle(
            baseEpochDay = baseDay,
            selectedEpochDay = epochDay,
            statusFilter = filter,
            layoutMode = layout,
            sheetTaskId = sheetTaskId
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

    private val notificationStateFlow = combine(
        readNotificationIdsFlow,
        activityNotificationsFlow
    ) { readIds, activityEvents ->
        NotificationStateBundle(
            readIds = readIds,
            activityEvents = activityEvents
        )
    }

    private val uiControlFlow = combine(
        coreUiControlFlow,
        drawerFilterFlow,
        notificationStateFlow
    ) { core, drawer, notificationState ->
        UiControlBundle(
            baseEpochDay = core.baseEpochDay,
            selectedEpochDay = core.selectedEpochDay,
            statusFilter = core.statusFilter,
            layoutMode = core.layoutMode,
            sheetTaskId = core.sheetTaskId,
            notificationState = notificationState,
            drawerFilters = drawer
        )
    }

    private data class SyncFlags(
        val isSyncing: Boolean,
        val isLoggingIn: Boolean,
        val syncMessage: String?,
        val loginErrorMessage: String?,
        val sseState: SseConnectionState
    )

    private val syncFlagsFlow = combine(
        isSyncingFlow,
        isLoggingInFlow,
        syncMessageFlow,
        loginErrorMessageFlow,
        sseStateFlow
    ) { syncing, loggingIn, msg, loginErr, sse ->
        SyncFlags(
            isSyncing = syncing,
            isLoggingIn = loggingIn,
            syncMessage = msg,
            loginErrorMessage = loginErr,
            sseState = sse
        )
    }

    private val syncAndSessionFlow = combine(
        syncFlagsFlow,
        repository.observeUnsyncedCount(),
        sessionStateFlow,
        localDarkOverrideFlow
    ) { flags, unsyncedCount, loadedSession, localDark ->
        SyncAndSessionBundle(
            isSyncing = flags.isSyncing,
            isLoggingIn = flags.isLoggingIn,
            syncMessage = flags.syncMessage,
            loginErrorMessage = flags.loginErrorMessage,
            sseState = flags.sseState,
            unsyncedCount = unsyncedCount,
            session = loadedSession.prefs,
            isSessionLoaded = loadedSession.isLoaded,
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
        val currentUserId = syncBundle.session.userId
        val techName = syncBundle.session.technicianName.ifBlank { "Fajar" }
        val drawerFilters = controls.drawerFilters
        val resolvedShiftStatus = drawerFilters.localShiftStatusOverride ?: syncBundle.session.shiftStatus
        val currentBaseEpochDay = controls.baseEpochDay
        val horizonEpochDays = (0L..6L).map { offset -> currentBaseEpochDay + offset }

        val scopedTasks = allTasks.filter { task ->
            val matchesMyTickets = !drawerFilters.onlyMyTickets ||
                task.isAssignedToUser(currentUserId) ||
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

        val scheduleDays = buildScheduleDays(
            allTasks = scopedTasks,
            baseEpochDay = currentBaseEpochDay,
            horizonEpochDays = horizonEpochDays
        )
        val dayTasks = scopedTasks.filter { it.scheduledDateEpochDay == controls.selectedEpochDay }
            .sortedWith(
                compareBy<Task> { it.scheduledTimeMinutes ?: 9999 }
                    .thenBy { it.priority.sortRank }
                    .thenByDescending { it.createdAtMillis }
                    .thenByDescending { it.id }
            )

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
        val notifications = buildOperationalNotifications(
            allTasks = allTasks,
            activeHero = activeHero,
            unsyncedTasks = unsyncedTasks,
            syncBadge = syncBadge,
            todayEpochDay = currentBaseEpochDay,
            activityEvents = controls.notificationState.activityEvents,
            readIds = controls.notificationState.readIds
        )
        val unreadNotificationCount = notifications.count { !it.isRead }

        ScheduleUiState(
            currentUserId = currentUserId,
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
            loginErrorMessage = syncBundle.loginErrorMessage,
            sseState = syncBundle.sseState,
            serverBaseUrl = syncBundle.session.serverBaseUrl,
            authToken = syncBundle.session.authToken.ifBlank { null },
            isSessionLoaded = syncBundle.isSessionLoaded,
            isDarkTheme = isDark,
            lastSyncedAtMillis = syncBundle.session.lastSyncedAtMillis,
            notifications = notifications,
            unreadNotificationCount = unreadNotificationCount,
            isLoggingIn = syncBundle.isLoggingIn
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScheduleUiState(
            selectedEpochDay = initialTodayEpochDay,
            horizonSpanLabel = DateEpochUtil.formatRangeSpan(
                initialTodayEpochDay,
                initialTodayEpochDay + 6L
            ),
            selectedFullDateLabel = DateEpochUtil.formatFullDate(initialTodayEpochDay),
            greetingTitle = buildGreeting("Fajar", hourOfDayProvider()),
            isSessionLoaded = appPreferences == null
        )
    )

    init {
        notificationDispatcher?.ensureChannelCreated()

        viewModelScope.launch {
            repository.initializeOfflineCache()
            val hasStoredToken = appPreferences != null && !appPreferences.getToken().isNullOrBlank()
            if (hasStoredToken) {
                triggerSyncInternal(isInitialBaselineSync = true)
            } else {
                suppressDelegationDuringInitialSync = false
            }
        }

        observeActiveHeroForTimerSeed()
        observeAssignedTaskDelegations()
        observePendingNotificationDeepLink()

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
            sessionStateFlow
                .map { it.prefs.authToken.isNotBlank() }
                .distinctUntilChanged()
                .collectLatest { loggedIn ->
                    if (!loggedIn) {
                        sseStateFlow.value = SseConnectionState.OFFLINE
                        return@collectLatest
                    }
                    client.observeEvents(
                        baseUrlProvider = { sessionStateFlow.value.prefs.serverBaseUrl }
                    ).collect { event ->
                        when (event) {
                            is SseEvent.StateChanged -> {
                                sseStateFlow.value = event.state
                            }
                            is SseEvent.DataMessage -> {
                                if (!isSyncingFlow.value) {
                                    val result = repository.refreshTasksFromServer()
                                    if (result is SyncResult.Success) {
                                        recordActivityNotification(
                                            severity = NotificationSeverity.INFO,
                                            badgeText = "LIVE SSE",
                                            title = "Pembaruan jadwal real-time dari server",
                                            subtitle = "${result.downloadedCount} tiket aktif diperbarui dari ping.fjrx.my.id."
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun observeAssignedTaskDelegations() {
        delegationJob = viewModelScope.launch {
            combine(
                repository.observeAllTasks(),
                sessionStateFlow
            ) { allTasks, loadedSession ->
                allTasks to loadedSession
            }.collect { (allTasks, loadedSession) ->
                if (!loadedSession.isLoaded) return@collect

                val prefs = loadedSession.prefs
                val isAuthenticated = appPreferences == null || prefs.authToken.isNotBlank()
                val currentUserId = prefs.userId

                if (!isAuthenticated || currentUserId <= 0L) {
                    resetDelegationBaseline(suppressUntilInitialSync = false)
                    return@collect
                }

                if (trackedDelegationUserId != currentUserId) {
                    trackedDelegationUserId = currentUserId
                    knownAssignedTaskIds.clear()
                    delegationBaselineEstablished = false
                }

                val assignedServerTasks = allTasks.filter { task ->
                    task.id > 0L && task.isAssignedToUser(currentUserId)
                }

                // Absorb pre-existing tasks silently on startup / first login sync to prevent notification storms.
                if (suppressDelegationDuringInitialSync || !delegationBaselineEstablished) {
                    knownAssignedTaskIds.addAll(assignedServerTasks.map { it.id })
                    if (!suppressDelegationDuringInitialSync) {
                        delegationBaselineEstablished = true
                    }
                    return@collect
                }

                val newlyDelegated = assignedServerTasks.filter { task ->
                    val isSelfCreated = pendingSelfCreatedTitles.contains(task.title.trim())
                    task.id !in knownAssignedTaskIds &&
                        !isSelfCreated &&
                        task.stage != Stage.DONE
                }

                knownAssignedTaskIds.addAll(assignedServerTasks.map { it.id })

                for (task in newlyDelegated) {
                    notificationDispatcher?.showAssignedTaskNotification(task)
                    recordActivityNotification(
                        category = NotificationCategory.SCHEDULE,
                        severity = if (task.priority == Priority.P1_CRITICAL) {
                            NotificationSeverity.CRITICAL
                        } else {
                            NotificationSeverity.ACTIVE
                        },
                        badgeText = "DELEGASI BARU",
                        title = "Tugas baru didelegasikan · ${task.ticketCode}",
                        subtitle = "${task.title} (${task.location})",
                        taskId = task.id
                    )
                }
            }
        }
    }

    private fun observePendingNotificationDeepLink() {
        deepLinkJob = viewModelScope.launch {
            combine(
                repository.observeAllTasks(),
                pendingNotificationTaskIdFlow
            ) { allTasks, pendingTaskId ->
                allTasks to pendingTaskId
            }.collect { (allTasks, pendingTaskId) ->
                if (pendingTaskId == null || pendingTaskId <= 0L) return@collect
                val matchedTask = allTasks.firstOrNull { it.id == pendingTaskId } ?: return@collect
                focusAndOpenTask(matchedTask)
                pendingNotificationTaskIdFlow.value = null
            }
        }
    }

    private fun focusAndOpenTask(task: Task) {
        val baseDay = baseEpochDayFlow.value
        if (task.scheduledDateEpochDay in baseDay..(baseDay + 6L)) {
            selectedEpochDayFlow.value = task.scheduledDateEpochDay
        }
        selectedSheetTaskIdFlow.value = task.id
    }

    private fun resetDelegationBaseline(suppressUntilInitialSync: Boolean) {
        trackedDelegationUserId = 0L
        delegationBaselineEstablished = false
        suppressDelegationDuringInitialSync = suppressUntilInitialSync
        knownAssignedTaskIds.clear()
        pendingSelfCreatedTitles.clear()
    }

    internal fun stopBackgroundJobs() {
        heroSeedJob?.cancel()
        tickerJob?.cancel()
        sseJob?.cancel()
        delegationJob?.cancel()
        deepLinkJob?.cancel()
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

    fun openTaskFromNotification(taskId: Long) {
        if (taskId <= 0L) return
        syncTodayHorizon(selectToday = false)
        val immediateMatch = uiState.value.allTasks.firstOrNull { it.id == taskId }
        if (immediateMatch != null) {
            focusAndOpenTask(immediateMatch)
            pendingNotificationTaskIdFlow.value = null
        } else {
            pendingNotificationTaskIdFlow.value = taskId
        }
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

    fun syncTodayHorizon(selectToday: Boolean = false) {
        val today = baseEpochDayProvider()
        baseEpochDayFlow.value = today
        val selected = selectedEpochDayFlow.value
        if (selectToday || selected < today || selected > today + 6L) {
            selectedEpochDayFlow.value = today
        }
    }

    fun createQuickTask(
        title: String,
        category: TicketCategory,
        priority: Priority,
        location: String,
        scheduledTimeMinutes: Int? = null,
        technicalNotes: String = "",
        labels: List<String> = emptyList(),
        targetEpochDay: Long? = null
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val todayEpochDay = baseEpochDayProvider()
        val resolvedEpochDay = (targetEpochDay ?: todayEpochDay).coerceAtLeast(todayEpochDay)
        val isPlannedFuture = resolvedEpochDay > todayEpochDay
        val cleanScheduledMinutes = scheduledTimeMinutes?.coerceIn(0, 23 * 60 + 59)

        baseEpochDayFlow.value = todayEpochDay
        if (!isPlannedFuture) {
            selectedEpochDayFlow.value = todayEpochDay
        }
        pendingSelfCreatedTitles.add(cleanTitle)
        viewModelScope.launch {
            try {
                val created = repository.createQuickTask(
                    title = cleanTitle,
                    category = category,
                    priority = priority,
                    epochDay = resolvedEpochDay,
                    location = location,
                    scheduledTimeMinutes = cleanScheduledMinutes,
                    technicalNotes = technicalNotes,
                    labels = labels
                )
                if (created.id > 0L) {
                    knownAssignedTaskIds.add(created.id)
                }
                syncMessageFlow.value = if (isPlannedFuture) {
                    val clockSuffix = cleanScheduledMinutes?.let {
                        " pukul ${DateEpochUtil.formatScheduledClock(it)}"
                    }.orEmpty()
                    "Tiket ${created.ticketCode} dijadwalkan untuk ${DateEpochUtil.formatFullDate(resolvedEpochDay)}$clockSuffix."
                } else if (cleanScheduledMinutes != null) {
                    "Tiket ${created.ticketCode} dijadwalkan hari ini pukul ${DateEpochUtil.formatScheduledClock(cleanScheduledMinutes)}."
                } else {
                    "Tiket ${created.ticketCode} ditambahkan ke jadwal hari ini."
                }
            } finally {
                pendingSelfCreatedTitles.remove(cleanTitle)
            }
        }
    }

    fun triggerSync() {
        triggerSyncInternal(isInitialBaselineSync = false)
    }

    private fun triggerSyncInternal(isInitialBaselineSync: Boolean) {
        if (isSyncingFlow.value) return
        syncTodayHorizon(selectToday = false)
        if (isInitialBaselineSync) {
            suppressDelegationDuringInitialSync = true
        }
        viewModelScope.launch {
            isSyncingFlow.value = true
            try {
                flushCurrentRunningTimer()
                when (val result = repository.refreshTasksFromServer()) {
                    is SyncResult.Success -> {
                        syncMessageFlow.value =
                            "Tersinkron ke server PingDulu (${result.downloadedCount} tiket, ${result.uploadedCount} unggah)."
                        if (result.downloadedCount > 0 || result.uploadedCount > 0) {
                            recordActivityNotification(
                                severity = NotificationSeverity.INFO,
                                badgeText = "SYNC SUKSES",
                                title = "Sinkronisasi server selesai",
                                subtitle = "${result.downloadedCount} tiket diunduh, ${result.uploadedCount} perubahan lokal terunggah."
                            )
                        }
                    }
                    is SyncResult.AuthRequired -> {
                        syncMessageFlow.value = result.message
                        recordActivityNotification(
                            severity = NotificationSeverity.WARNING,
                            badgeText = "SESI TOKEN",
                            title = "Autentikasi perlu diperbarui",
                            subtitle = result.message
                        )
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
                if (isInitialBaselineSync) {
                    val userId = sessionStateFlow.value.prefs.userId.takeIf { it > 0L }
                        ?: appPreferences?.getUserId()
                        ?: 0L
                    if (userId > 0L) {
                        val snapshotTasks = runCatching { repository.observeAllTasks().first() }.getOrDefault(emptyList())
                        knownAssignedTaskIds.addAll(
                            snapshotTasks.filter { it.id > 0L && it.isAssignedToUser(userId) }.map { it.id }
                        )
                        trackedDelegationUserId = userId
                        delegationBaselineEstablished = true
                    }
                    suppressDelegationDuringInitialSync = false
                }
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

    fun loginToServer(username: String, password: String) {
        if (isLoggingInFlow.value) return
        if (username.isBlank() || password.isBlank()) {
            loginErrorMessageFlow.value = "Username dan kata sandi wajib diisi."
            return
        }
        viewModelScope.launch {
            isLoggingInFlow.value = true
            loginErrorMessageFlow.value = null
            resetDelegationBaseline(suppressUntilInitialSync = true)
            try {
                val result = repository.login(username.trim(), password)
                result.fold(
                    onSuccess = {
                        loginErrorMessageFlow.value = null
                        syncTodayHorizon(selectToday = true)
                        syncMessageFlow.value = "Login berhasil! Mengunduh jadwal tiket..."
                        triggerSyncInternal(isInitialBaselineSync = true)
                    },
                    onFailure = { err ->
                        suppressDelegationDuringInitialSync = false
                        loginErrorMessageFlow.value =
                            err.localizedMessage ?: "Username atau kata sandi salah."
                    }
                )
            } finally {
                isLoggingInFlow.value = false
            }
        }
    }

    fun logoutFromServer() {
        viewModelScope.launch {
            flushCurrentRunningTimer()
            liveTimerFlow.value = LiveTimerSnapshot()
            selectedSheetTaskIdFlow.value = null
            pendingNotificationTaskIdFlow.value = null
            loginErrorMessageFlow.value = null
            syncMessageFlow.value = null
            readNotificationIdsFlow.value = emptySet()
            activityNotificationsFlow.value = emptyList()
            resetDelegationBaseline(suppressUntilInitialSync = false)
            resetDrawerFilters()
            repository.clearSyncedCacheOnLogout()
            appPreferences?.clearAuthToken()
        }
    }

    fun clearSyncMessage() {
        syncMessageFlow.value = null
    }

    fun clearLoginError() {
        loginErrorMessageFlow.value = null
    }

    fun markNotificationRead(notificationId: String) {
        readNotificationIdsFlow.value = readNotificationIdsFlow.value + notificationId
    }

    fun markAllNotificationsRead() {
        val currentIds = uiState.value.notifications.map { it.id }
        readNotificationIdsFlow.value = readNotificationIdsFlow.value + currentIds
    }

    fun dismissNotifications() {
        markAllNotificationsRead()
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

    private fun buildScheduleDays(
        allTasks: List<Task>,
        baseEpochDay: Long,
        horizonEpochDays: List<Long>
    ): List<DayHorizonModel> {
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

    private fun recordActivityNotification(
        severity: NotificationSeverity,
        badgeText: String,
        title: String,
        subtitle: String,
        category: NotificationCategory = NotificationCategory.SYNC,
        taskId: Long? = null
    ) {
        val nowMillis = System.currentTimeMillis()
        val timeLabel = DateEpochUtil.formatReportedTimeCompact(nowMillis)
        val seq = ++activityNotificationSeq
        val entry = FieldNotificationItem(
            id = "activity_${seq}_$nowMillis",
            category = category,
            severity = severity,
            title = title,
            subtitle = subtitle,
            badgeText = badgeText,
            timestampLabel = timeLabel,
            taskId = taskId
        )
        activityNotificationsFlow.value = (listOf(entry) + activityNotificationsFlow.value).take(8)
    }

    private fun buildOperationalNotifications(
        allTasks: List<Task>,
        activeHero: Task?,
        unsyncedTasks: List<Task>,
        syncBadge: SyncStatusBadge,
        todayEpochDay: Long,
        activityEvents: List<FieldNotificationItem>,
        readIds: Set<String>
    ): List<FieldNotificationItem> {
        val items = mutableListOf<FieldNotificationItem>()

        // 1. Active running stopwatch reminder
        if (activeHero != null && activeHero.isTimerRunning) {
            items += FieldNotificationItem(
                id = "timer_running_${activeHero.id}",
                category = NotificationCategory.SCHEDULE,
                severity = NotificationSeverity.ACTIVE,
                title = "Stopwatch sedang aktif · ${activeHero.ticketCode}",
                subtitle = "${activeHero.title} — pastikan jeda atau selesaikan jika pengerjaan rampung.",
                badgeText = "TIMER AKTIF",
                timestampLabel = DateEpochUtil.formatReportedTimeCompact(activeHero.createdAtMillis),
                taskId = activeHero.id
            )
        }

        // 2. Active P1 Critical tickets (unfinished)
        val activeP1Tasks = allTasks
            .filter { it.priority == Priority.P1_CRITICAL && it.stage != Stage.DONE }
            .sortedWith(
                compareBy<Task> { it.scheduledDateEpochDay }
                    .thenByDescending { it.createdAtMillis }
                    .thenByDescending { it.id }
            )
        for (task in activeP1Tasks) {
            items += FieldNotificationItem(
                id = "p1_task_${task.id}",
                category = NotificationCategory.CRITICAL,
                severity = NotificationSeverity.CRITICAL,
                title = task.title,
                subtitle = "${task.location} · Status: ${task.stage.displayLabel}",
                badgeText = "P1 · ${task.ticketCode}",
                timestampLabel = DateEpochUtil.formatReportedTimeCompact(task.createdAtMillis),
                taskId = task.id
            )
        }

        // 3. Pending offline sync queue
        if (unsyncedTasks.isNotEmpty()) {
            val previewCodes = unsyncedTasks.take(3).joinToString(", ") { it.ticketCode }
            val extraSuffix = if (unsyncedTasks.size > 3) " +${unsyncedTasks.size - 3} lainnya" else ""
            items += FieldNotificationItem(
                id = "sync_pending_${unsyncedTasks.size}",
                category = NotificationCategory.SYNC,
                severity = NotificationSeverity.WARNING,
                title = "${unsyncedTasks.size} perubahan menunggu sinkronisasi",
                subtitle = "Tersimpan lokal di Room ($previewCodes$extraSuffix).",
                badgeText = "ANTRE SYNC",
                timestampLabel = syncBadge.label,
                isSyncAction = true
            )
        }

        // 4. Today's backlog tickets not yet started (excluding P1 already listed above)
        val todayBacklogTasks = allTasks
            .filter {
                it.scheduledDateEpochDay == todayEpochDay &&
                    it.stage == Stage.BACKLOG &&
                    it.priority != Priority.P1_CRITICAL
            }
            .sortedWith(compareByDescending<Task> { it.createdAtMillis }.thenByDescending { it.id })
        for (task in todayBacklogTasks) {
            items += FieldNotificationItem(
                id = "today_backlog_${task.id}",
                category = NotificationCategory.SCHEDULE,
                severity = NotificationSeverity.WARNING,
                title = task.title,
                subtitle = "Jadwal hari ini di ${task.location} masih berstatus Antrean.",
                badgeText = "HARI INI · ${task.ticketCode}",
                timestampLabel = DateEpochUtil.formatReportedTimeCompact(task.createdAtMillis),
                taskId = task.id
            )
        }

        // 5. Recent live SSE & sync events
        items += activityEvents

        return items.map { item ->
            item.copy(isRead = item.id in readIds)
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
    private val sseEventClient: SseEventClient,
    private val notificationDispatcher: TaskNotificationDispatcher? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            return ScheduleViewModel(
                repository = repository,
                appPreferences = appPreferences,
                sseEventClient = sseEventClient,
                notificationDispatcher = notificationDispatcher
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
