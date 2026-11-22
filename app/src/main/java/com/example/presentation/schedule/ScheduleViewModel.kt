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
import com.example.domain.model.ActiveTaskLimitExceededException
import com.example.domain.model.ActiveTaskLimitPolicy
import com.example.domain.model.ActiveTaskLimitWarning
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.FieldTechnician
import com.example.domain.model.IndonesianHolidayCatalog
import com.example.domain.model.NationalHoliday
import com.example.domain.model.OfficialLabel
import com.example.domain.model.Priority
import com.example.domain.model.QuickTaskDraft
import com.example.domain.model.SnoozeOption
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TaskDetailWorkspaceState
import com.example.domain.model.TicketCategory
import com.example.domain.repository.SyncResult
import com.example.domain.repository.TaskRepository
import com.example.util.ConnectivityMonitor
import com.example.util.TaskNotificationDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val connectivityMonitor: ConnectivityMonitor? = null,
    private val baseEpochDayProvider: () -> Long = { DateEpochUtil.todayEpochDay() },
    private val hourOfDayProvider: () -> Int = { DateEpochUtil.currentHourOfDay() },
    private val enableBackgroundTicker: Boolean = true,
    private val enableSseStream: Boolean = true,
    private val enableAutoSyncRetry: Boolean = false,
    private val initialUserIdForTesting: Long = 0L,
    private val initialTechnicianNameForTesting: String = "Fajar",
    private val initialTechnicianRoleForTesting: String = "admin",
    private val clockMillisProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val initialTodayEpochDay: Long = baseEpochDayProvider()
    private val baseEpochDayFlow = MutableStateFlow(initialTodayEpochDay)

    private val selectedEpochDayFlow = MutableStateFlow(initialTodayEpochDay)
    private val selectedStatusFilterFlow = MutableStateFlow(StatusFilter.ALL)
    private val layoutModeFlow = MutableStateFlow(LayoutMode.GRID_2_COLUMN)
    private val selectedSheetTaskIdFlow = MutableStateFlow<Long?>(null)
    private val activeTaskLimitWarningFlow = MutableStateFlow<ActiveTaskLimitWarning?>(null)
    private val pendingNotificationTaskIdFlow = MutableStateFlow<Long?>(null)
    private val isSyncingFlow = MutableStateFlow(false)
    private val isLoggingInFlow = MutableStateFlow(false)
    private val syncMessageFlow = MutableStateFlow<String?>(null)
    private val loginErrorMessageFlow = MutableStateFlow<String?>(null)
    private val sseStateFlow = MutableStateFlow(SseConnectionState.OFFLINE)
    private val isNetworkOnlineFlow = MutableStateFlow(true)
    private val isAppInForegroundFlow = MutableStateFlow(true)
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
    private var sseRefreshJob: Job? = null
    private var connectivityJob: Job? = null
    private var autoPushJob: Job? = null
    private var fallbackPollingJob: Job? = null
    private var delegationJob: Job? = null
    private var deepLinkJob: Job? = null
    private var detailWorkspaceJob: Job? = null
    private var lastResumeSyncAtMillis: Long = 0L
    @Volatile
    private var lastLocalMutationAtMillis: Long = 0L

    private val detailWorkspaceFlow = MutableStateFlow(TaskDetailWorkspaceState())
    val detailWorkspaceState: StateFlow<TaskDetailWorkspaceState> = detailWorkspaceFlow.asStateFlow()

    private val initialYearMonth = DateEpochUtil.yearMonthFromEpochDay(initialTodayEpochDay)
    private val calendarYearMonthFlow = MutableStateFlow(initialYearMonth)
    private val calendarSelectedEpochDayFlow = MutableStateFlow(initialTodayEpochDay)
    private val isLoadingCalendarFlow = MutableStateFlow(false)
    private val holidaysByEpochDayFlow = MutableStateFlow<Map<Long, NationalHoliday>>(
        IndonesianHolidayCatalog.holidaysForYear(initialYearMonth.first).associateBy { it.epochDay }
    )
    private var calendarFetchJob: Job? = null

    private data class CalendarControlBundle(
        val year: Int,
        val month: Int,
        val selectedEpochDay: Long,
        val isLoading: Boolean,
        val holidaysByEpochDay: Map<Long, NationalHoliday>
    )

    private val calendarControlFlow = combine(
        calendarYearMonthFlow,
        calendarSelectedEpochDayFlow,
        isLoadingCalendarFlow,
        holidaysByEpochDayFlow
    ) { (year, month), selectedDay, loading, holidays ->
        CalendarControlBundle(
            year = year,
            month = month,
            selectedEpochDay = selectedDay,
            isLoading = loading,
            holidaysByEpochDay = holidays
        )
    }

    private data class LoadedSession(
        val isLoaded: Boolean,
        val prefs: UserSessionPrefs
    )

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
                    prefs = UserSessionPrefs(
                        userId = initialUserIdForTesting,
                        technicianName = initialTechnicianNameForTesting,
                        technicianRole = initialTechnicianRoleForTesting
                    )
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
        val drawerFilters: DrawerFilterBundle,
        val activeTaskLimitWarning: ActiveTaskLimitWarning?
    )

    private data class SyncAndSessionBundle(
        val isSyncing: Boolean,
        val isLoggingIn: Boolean,
        val syncMessage: String?,
        val lastSyncError: String?,
        val isNetworkOnline: Boolean,
        val loginErrorMessage: String?,
        val sseState: SseConnectionState,
        val unsyncedCount: Int,
        val session: UserSessionPrefs,
        val isSessionLoaded: Boolean,
        val localDarkOverride: Boolean?,
        val availableTechnicians: List<FieldTechnician>,
        val officialLabels: List<OfficialLabel>
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
        notificationStateFlow,
        activeTaskLimitWarningFlow
    ) { core, drawer, notificationState, limitWarning ->
        UiControlBundle(
            baseEpochDay = core.baseEpochDay,
            selectedEpochDay = core.selectedEpochDay,
            statusFilter = core.statusFilter,
            layoutMode = core.layoutMode,
            sheetTaskId = core.sheetTaskId,
            notificationState = notificationState,
            drawerFilters = drawer,
            activeTaskLimitWarning = limitWarning
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

    private val syncDiagnosticsFlow = combine(
        repository.observeUnsyncedCount(),
        repository.observeLastSyncError(),
        isNetworkOnlineFlow
    ) { unsyncedCount, lastSyncError, isOnline ->
        Triple(unsyncedCount, lastSyncError, isOnline)
    }

    private val catalogsFlow = combine(
        repository.observeTechnicians(),
        repository.observeOfficialLabels()
    ) { technicians, labels ->
        technicians to labels
    }

    private val syncAndSessionFlow = combine(
        syncFlagsFlow,
        syncDiagnosticsFlow,
        sessionStateFlow,
        localDarkOverrideFlow,
        catalogsFlow
    ) { flags, diagnostics, loadedSession, localDark, catalogs ->
        val (unsyncedCount, lastSyncError, isOnline) = diagnostics
        val (technicians, labels) = catalogs
        SyncAndSessionBundle(
            isSyncing = flags.isSyncing,
            isLoggingIn = flags.isLoggingIn,
            syncMessage = flags.syncMessage,
            lastSyncError = lastSyncError,
            isNetworkOnline = isOnline,
            loginErrorMessage = flags.loginErrorMessage,
            sseState = flags.sseState,
            unsyncedCount = unsyncedCount,
            session = loadedSession.prefs,
            isSessionLoaded = loadedSession.isLoaded,
            localDarkOverride = localDark,
            availableTechnicians = technicians,
            officialLabels = labels
        )
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.observeAllTasks(),
        repository.observeActiveHeroTask(),
        calendarControlFlow,
        uiControlFlow,
        syncAndSessionFlow
    ) { allTasks, activeHero, calendarBundle, controls, syncBundle ->
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
            horizonEpochDays = horizonEpochDays,
            holidaysByEpochDay = calendarBundle.holidaysByEpochDay
        )
        val dayTasks = scopedTasks.filter { task ->
            if (controls.selectedEpochDay == currentBaseEpochDay) {
                task.scheduledDateEpochDay == currentBaseEpochDay ||
                    (task.scheduledDateEpochDay < currentBaseEpochDay && task.stage != Stage.DONE)
            } else {
                task.scheduledDateEpochDay == controls.selectedEpochDay
            }
        }
            .sortedWith(
                compareBy<Task> { it.scheduledDateEpochDay }
                    .thenBy { it.scheduledTimeMinutes ?: 9999 }
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

        val sheetTask = controls.sheetTaskId?.let { id ->
            allTasks.firstOrNull { it.id == id }
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
            unsyncedTasks = unsyncedTasks,
            syncBadge = syncBadge,
            todayEpochDay = currentBaseEpochDay,
            activityEvents = controls.notificationState.activityEvents,
            readIds = controls.notificationState.readIds
        )
        val unreadNotificationCount = notifications.count { !it.isRead }

        val activeHeroTasks = ActiveTaskLimitPolicy.selectActiveHeroTasks(
            scopedTasks = scopedTasks,
            currentUserId = currentUserId,
            currentUserName = techName
        )
        val resolvedActiveHero = activeHeroTasks.firstOrNull() ?: activeHero

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
            activeHeroTask = resolvedActiveHero,
            activeHeroTasks = activeHeroTasks,
            activeTaskLimitWarning = controls.activeTaskLimitWarning,
            liveHeroSeconds = resolvedActiveHero?.trackedSeconds ?: 0L,
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
            lastSyncError = syncBundle.lastSyncError,
            isNetworkOnline = syncBundle.isNetworkOnline,
            loginErrorMessage = syncBundle.loginErrorMessage,
            sseState = syncBundle.sseState,
            serverBaseUrl = syncBundle.session.serverBaseUrl,
            authToken = syncBundle.session.authToken.ifBlank { null },
            isSessionLoaded = syncBundle.isSessionLoaded,
            isDarkTheme = isDark,
            lastSyncedAtMillis = syncBundle.session.lastSyncedAtMillis,
            notifications = notifications,
            unreadNotificationCount = unreadNotificationCount,
            isLoggingIn = syncBundle.isLoggingIn,
            availableTechnicians = syncBundle.availableTechnicians,
            officialLabels = syncBundle.officialLabels,
            holidaysByEpochDay = calendarBundle.holidaysByEpochDay,
            calendarYear = calendarBundle.year,
            calendarMonth = calendarBundle.month,
            calendarSelectedEpochDay = calendarBundle.selectedEpochDay,
            isLoadingCalendar = calendarBundle.isLoading
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
            isSessionLoaded = appPreferences == null,
            holidaysByEpochDay = IndonesianHolidayCatalog.holidaysForYear(initialYearMonth.first)
                .associateBy { it.epochDay },
            calendarYear = initialYearMonth.first,
            calendarMonth = initialYearMonth.second,
            calendarSelectedEpochDay = initialTodayEpochDay
        )
    )

    init {
        notificationDispatcher?.ensureChannelCreated()

        viewModelScope.launch {
            repository.initializeOfflineCache()
            val hasStoredToken = appPreferences != null && !appPreferences.getToken().isNullOrBlank()
            if (hasStoredToken) {
                // Record startup sync timestamp so immediate onResume() right after cold launch
                // does not fire a duplicate full GET /api/tasks sync.
                lastResumeSyncAtMillis = clockMillisProvider()
                launch { repository.refreshCatalogsFromServer() }
                launch { loadHolidaysForYear(initialYearMonth.first) }
                triggerSyncInternal(isInitialBaselineSync = true, silentOnSuccess = true)
            } else {
                suppressDelegationDuringInitialSync = false
            }
        }

        observeAssignedTaskDelegations()
        observePendingNotificationDeepLink()

        if (enableSseStream && sseEventClient != null) {
            observeSseEvents()
        }

        if (connectivityMonitor != null) {
            observeNetworkConnectivity()
        }

        if (enableAutoSyncRetry) {
            observePendingAutoPush()
            if (enableSseStream) {
                observeLiveFallbackPolling()
            }
        }
    }

    private fun SyncAndSessionBundle.lastSyncedMillisOrZero(): Long = session.lastSyncedAtMillis

    fun loadHolidaysForYear(year: Int) {
        viewModelScope.launch {
            val holidays = repository.fetchHolidaysForYear(year)
            if (holidays.isNotEmpty()) {
                val merged = LinkedHashMap(holidaysByEpochDayFlow.value)
                holidays.forEach { merged[it.epochDay] = it }
                holidaysByEpochDayFlow.value = merged
            }
        }
    }

    fun onCalendarMonthChanged(year: Int, month: Int) {
        val normalized = normalizeYearMonth(year, month)
        calendarYearMonthFlow.value = normalized
        calendarFetchJob?.cancel()
        calendarFetchJob = viewModelScope.launch {
            isLoadingCalendarFlow.value = true
            try {
                // Ensure offline catalog for target year is immediately visible
                val merged = LinkedHashMap(holidaysByEpochDayFlow.value)
                IndonesianHolidayCatalog.holidaysForYear(normalized.first).forEach {
                    merged.putIfAbsent(it.epochDay, it)
                }
                holidaysByEpochDayFlow.value = merged

                val res = repository.fetchCalendarMonth(normalized.first, normalized.second)
                res.onSuccess { remoteHolidays ->
                    if (remoteHolidays.isNotEmpty()) {
                        val updated = LinkedHashMap(holidaysByEpochDayFlow.value)
                        remoteHolidays.forEach { updated[it.epochDay] = it }
                        holidaysByEpochDayFlow.value = updated
                    }
                }
            } finally {
                isLoadingCalendarFlow.value = false
            }
        }
    }

    fun onCalendarDaySelected(epochDay: Long) {
        calendarSelectedEpochDayFlow.value = epochDay
        val ym = DateEpochUtil.yearMonthFromEpochDay(epochDay)
        if (ym != calendarYearMonthFlow.value) {
            onCalendarMonthChanged(ym.first, ym.second)
        }
    }

    fun jumpCalendarToToday() {
        val today = baseEpochDayProvider()
        calendarSelectedEpochDayFlow.value = today
        val ym = DateEpochUtil.yearMonthFromEpochDay(today)
        onCalendarMonthChanged(ym.first, ym.second)
    }

    private fun normalizeYearMonth(year: Int, month: Int): Pair<Int, Int> {
        var y = year
        var m = month
        while (m < 1) {
            m += 12
            y -= 1
        }
        while (m > 12) {
            m -= 12
            y += 1
        }
        return y.coerceIn(2020, 2035) to m
    }

    private fun markLocalMutationForEchoSuppression() {
        lastLocalMutationAtMillis = clockMillisProvider()
    }

    private fun isRecentLocalMutationEcho(): Boolean {
        val last = lastLocalMutationAtMillis
        if (last <= 0L) return false
        val elapsed = clockMillisProvider() - last
        return elapsed in 0L until SELF_MUTATION_ECHO_SUPPRESS_MILLIS
    }

    private fun observeSseEvents() {
        val client = sseEventClient ?: return
        sseJob = viewModelScope.launch {
            combine(
                sessionStateFlow.map { appPreferences == null || it.prefs.authToken.isNotBlank() },
                isAppInForegroundFlow,
                isNetworkOnlineFlow
            ) { loggedIn, inForeground, isOnline ->
                loggedIn && inForeground && isOnline
            }.distinctUntilChanged().collectLatest { shouldStream ->
                if (!shouldStream) {
                    sseRefreshJob?.cancel()
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
                            // Ignore keep-alive / heartbeat / handshake banners and immediate self-mutation echoes
                            if (SseEventClient.isNoiseOrHeartbeatEvent(event.eventType, event.payload)) {
                                return@collect
                            }
                            if (isRecentLocalMutationEcho()) {
                                return@collect
                            }
                            // Coalesce rapid SSE bursts (3s debounce) and execute strict PULL-ONLY refresh.
                            // NEVER pushes unsynced local rows, eliminating SSE echo flooding loops.
                            scheduleDebouncedSsePull()
                        }
                    }
                }
            }
        }
    }

    private fun scheduleDebouncedSsePull() {
        sseRefreshJob?.cancel()
        sseRefreshJob = viewModelScope.launch {
            delay(SSE_PULL_DEBOUNCE_MILLIS)
            if (!isActive || !isAppInForegroundFlow.value || isSyncingFlow.value) return@launch
            isSyncingFlow.value = true
            try {
                // Silent pull-only refresh; avoids spamming the Notification Center with "LIVE SSE" entries.
                repository.pullTasksFromServerOnly()
            } finally {
                isSyncingFlow.value = false
            }
        }
    }

    private fun observeNetworkConnectivity() {
        val monitor = connectivityMonitor ?: return
        connectivityJob = viewModelScope.launch {
            var wasOffline = false
            monitor.isOnline.distinctUntilChanged().collect { online ->
                isNetworkOnlineFlow.value = online
                if (!online) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    val isAuthenticated = appPreferences == null ||
                        sessionStateFlow.value.prefs.authToken.isNotBlank()
                    if (isAuthenticated && isAppInForegroundFlow.value && !isSyncingFlow.value) {
                        triggerSyncInternal(isInitialBaselineSync = false, silentOnSuccess = true)
                    }
                }
            }
        }
    }

    private fun observePendingAutoPush() {
        autoPushJob = viewModelScope.launch {
            combine(
                repository.observeUnsyncedCount().map { it > 0 }.distinctUntilChanged(),
                isNetworkOnlineFlow,
                isAppInForegroundFlow,
                sessionStateFlow.map { appPreferences == null || it.prefs.authToken.isNotBlank() }
            ) { hasUnsynced, isOnline, inForeground, isAuthenticated ->
                hasUnsynced && isOnline && inForeground && isAuthenticated
            }.distinctUntilChanged().collectLatest { shouldAutoPush ->
                if (!shouldAutoPush) return@collectLatest
                // Safe exponential backoff (15s -> 30s -> 60s -> 120s) with circuit breaker on permanent 4xx errors.
                val retryDelays = AUTO_PUSH_EXPONENTIAL_BACKOFF_MS
                for (waitMs in retryDelays) {
                    delay(waitMs)
                    if (!isActive || !isAppInForegroundFlow.value) break
                    if (uiState.value.unsyncedCount <= 0 || !isNetworkOnlineFlow.value) break
                    if (isPermanentSyncError(repository.getLastSyncError())) break
                    if (!isSyncingFlow.value) {
                        triggerSyncInternal(isInitialBaselineSync = false, silentOnSuccess = true)
                    }
                }
            }
        }
    }

    private fun observeLiveFallbackPolling() {
        fallbackPollingJob = viewModelScope.launch {
            combine(
                sseStateFlow,
                isNetworkOnlineFlow,
                isAppInForegroundFlow,
                sessionStateFlow.map { appPreferences == null || it.prefs.authToken.isNotBlank() }
            ) { sseState, isOnline, inForeground, isAuthenticated ->
                isAuthenticated && isOnline && inForeground && sseState == SseConnectionState.OFFLINE
            }.distinctUntilChanged().collectLatest { shouldPoll ->
                if (!shouldPoll) return@collectLatest
                while (isActive) {
                    delay(FALLBACK_POLL_INTERVAL_MILLIS)
                    if (!isAppInForegroundFlow.value) break
                    if (!isSyncingFlow.value && isNetworkOnlineFlow.value) {
                        isSyncingFlow.value = true
                        try {
                            // Pull-only polling when SSE is offline so pending upload errors never flood POST /api/tasks
                            repository.pullTasksFromServerOnly()
                        } finally {
                            isSyncingFlow.value = false
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
        loadTaskDetailWorkspace(task.id)
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
        calendarFetchJob?.cancel()
        sseJob?.cancel()
        sseRefreshJob?.cancel()
        connectivityJob?.cancel()
        autoPushJob?.cancel()
        fallbackPollingJob?.cancel()
        delegationJob?.cancel()
        deepLinkJob?.cancel()
        detailWorkspaceJob?.cancel()
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
        loadTaskDetailWorkspace(taskId)
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
        detailWorkspaceJob?.cancel()
    }

    fun refreshTaskDetailWorkspace(taskId: Long) {
        loadTaskDetailWorkspace(taskId)
    }

    private fun loadTaskDetailWorkspace(taskId: Long) {
        detailWorkspaceJob?.cancel()
        if (taskId <= 0L) {
            detailWorkspaceFlow.value = TaskDetailWorkspaceState(
                taskId = taskId,
                statusNotice = "Tiket lokal — sinkronkan ke server untuk melihat/mengunggah lampiran & komentar."
            )
            return
        }
        val cachedAttachments = uiState.value.allTasks
            .firstOrNull { it.id == taskId }
            ?.attachments
            .orEmpty()
        val preserveExisting = detailWorkspaceFlow.value.taskId == taskId
        detailWorkspaceFlow.value = if (preserveExisting) {
            detailWorkspaceFlow.value.copy(
                attachments = detailWorkspaceFlow.value.attachments.ifEmpty { cachedAttachments },
                isLoadingAttachments = true,
                isLoadingComments = true,
                isLoadingActivities = true,
                statusNotice = null
            )
        } else {
            TaskDetailWorkspaceState(
                taskId = taskId,
                attachments = cachedAttachments,
                isLoadingAttachments = true,
                isLoadingComments = true,
                isLoadingActivities = true
            )
        }
        detailWorkspaceJob = viewModelScope.launch {
            launch {
                val res = repository.fetchTaskAttachments(taskId)
                if (detailWorkspaceFlow.value.taskId == taskId) {
                    res.fold(
                        onSuccess = { list ->
                            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                                attachments = list,
                                isLoadingAttachments = false
                            )
                        },
                        onFailure = { err ->
                            val current = detailWorkspaceFlow.value
                            detailWorkspaceFlow.value = current.copy(
                                isLoadingAttachments = false,
                                statusNotice = if (current.attachments.isEmpty()) {
                                    err.localizedMessage
                                } else {
                                    current.statusNotice
                                }
                            )
                        }
                    )
                }
            }
            launch {
                val res = repository.fetchTaskComments(taskId)
                if (detailWorkspaceFlow.value.taskId == taskId) {
                    res.fold(
                        onSuccess = { list ->
                            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                                comments = list,
                                isLoadingComments = false
                            )
                        },
                        onFailure = { err ->
                            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                                isLoadingComments = false,
                                statusNotice = detailWorkspaceFlow.value.statusNotice ?: err.localizedMessage
                            )
                        }
                    )
                }
            }
            launch {
                val res = repository.fetchTaskActivities(taskId)
                if (detailWorkspaceFlow.value.taskId == taskId) {
                    res.fold(
                        onSuccess = { feed ->
                            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                                activities = feed.items,
                                isActivitiesFromServer = feed.isFromServer,
                                isLoadingActivities = false
                            )
                        },
                        onFailure = {
                            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                                isLoadingActivities = false
                            )
                        }
                    )
                }
            }
        }
    }

    private fun refreshTaskActivitiesIfOpen(taskId: Long) {
        if (taskId <= 0L || detailWorkspaceFlow.value.taskId != taskId) return
        viewModelScope.launch {
            val res = repository.fetchTaskActivities(taskId)
            if (detailWorkspaceFlow.value.taskId == taskId) {
                res.onSuccess { feed ->
                    detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                        activities = feed.items,
                        isActivitiesFromServer = feed.isFromServer,
                        isLoadingActivities = false
                    )
                }
            }
        }
    }

    fun uploadTaskAttachment(
        taskId: Long,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        viewModelScope.launch {
            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                taskId = taskId,
                isUploadingAttachment = true,
                statusNotice = null
            )
            val result = repository.uploadTaskAttachment(
                taskId = taskId,
                fileName = fileName,
                mimeType = mimeType,
                bytes = bytes
            )
            result.fold(
                onSuccess = { uploaded ->
                    val current = detailWorkspaceFlow.value
                    val updatedList = (current.attachments + uploaded).distinctBy {
                        if (it.fileUrl.isNotBlank()) "url:${it.fileUrl}" else "id:${it.id}"
                    }
                    detailWorkspaceFlow.value = current.copy(
                        attachments = updatedList,
                        isUploadingAttachment = false,
                        statusNotice = null
                    )
                    syncMessageFlow.value = "Lampiran \"${uploaded.fileName}\" berhasil diunggah."
                    refreshTaskActivitiesIfOpen(taskId)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Gagal mengunggah lampiran."
                    detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                        isUploadingAttachment = false,
                        statusNotice = msg
                    )
                    syncMessageFlow.value = msg
                }
            )
        }
    }

    fun postTaskComment(taskId: Long, commentText: String) {
        val cleanText = commentText.trim()
        if (cleanText.isEmpty()) return
        viewModelScope.launch {
            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                taskId = taskId,
                isPostingComment = true,
                statusNotice = null
            )
            val result = repository.postTaskComment(taskId = taskId, commentText = cleanText)
            result.fold(
                onSuccess = { posted ->
                    val current = detailWorkspaceFlow.value
                    val updatedList = (current.comments + posted)
                        .distinctBy { it.id }
                        .sortedBy { it.createdAtMillis }
                    detailWorkspaceFlow.value = current.copy(
                        comments = updatedList,
                        isPostingComment = false,
                        statusNotice = null
                    )
                    syncMessageFlow.value = "Catatan progres ditambahkan ke diskusi tiket."
                    refreshTaskActivitiesIfOpen(taskId)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Gagal mengirim komentar."
                    detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(
                        isPostingComment = false,
                        statusNotice = msg
                    )
                    syncMessageFlow.value = msg
                }
            )
        }
    }

    fun snoozeTask(taskId: Long, option: SnoozeOption) {
        viewModelScope.launch {
            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = true)
            val nowMillis = System.currentTimeMillis()
            val cal = Calendar.getInstance(DateEpochUtil.JAKARTA_TIME_ZONE).apply {
                timeInMillis = nowMillis
            }
            var targetEpochDay: Long? = null
            var targetTimeMinutes: Int? = null
            val snoozeUntilMillis = when (option) {
                SnoozeOption.ONE_HOUR -> {
                    cal.add(Calendar.HOUR_OF_DAY, 1)
                    targetEpochDay = DateEpochUtil.epochDayFromMillis(cal.timeInMillis)
                    targetTimeMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    cal.timeInMillis
                }
                SnoozeOption.THREE_HOURS -> {
                    cal.add(Calendar.HOUR_OF_DAY, 3)
                    targetEpochDay = DateEpochUtil.epochDayFromMillis(cal.timeInMillis)
                    targetTimeMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    cal.timeInMillis
                }
                SnoozeOption.TOMORROW_MORNING -> {
                    val tomorrowEpoch = baseEpochDayProvider() + 1L
                    targetEpochDay = tomorrowEpoch
                    targetTimeMinutes = 9 * 60
                    val ymdCal = Calendar.getInstance(DateEpochUtil.JAKARTA_TIME_ZONE).apply {
                        timeInMillis = nowMillis
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    ymdCal.timeInMillis
                }
            }
            repository.snoozeTask(
                taskId = taskId,
                snoozeUntilMillis = snoozeUntilMillis,
                newEpochDay = targetEpochDay,
                newTimeMinutes = targetTimeMinutes
            )
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = false)
            syncMessageFlow.value = "Tiket ditunda: ${option.label}."
            refreshTaskActivitiesIfOpen(taskId)
        }
    }

    fun setTaskBlocked(taskId: Long, isBlocked: Boolean, reason: String? = null) {
        viewModelScope.launch {
            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = true)
            if (isBlocked) {
                flushCurrentRunningTimer()
            }
            repository.setTaskBlocked(
                taskId = taskId,
                isBlocked = isBlocked,
                reason = reason
            )
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = false)
            syncMessageFlow.value = if (isBlocked) {
                val label = reason?.trim()?.takeIf { it.isNotEmpty() } ?: "Menunggu vendor / sparepart"
                "Tiket ditandai TERBLOKIR ($label)."
            } else {
                "Status terblokir dilepas — tiket siap dilanjutkan."
            }
            refreshTaskActivitiesIfOpen(taskId)
        }
    }

    fun toggleChecklistItem(taskId: Long, checklistId: Long) {
        viewModelScope.launch {
            markLocalMutationForEchoSuppression()
            repository.toggleChecklistItem(taskId, checklistId)
            refreshTaskActivitiesIfOpen(taskId)
        }
    }

    fun addChecklistStep(taskId: Long, label: String) {
        val cleanLabel = label.trim()
        if (cleanLabel.isEmpty()) return
        viewModelScope.launch {
            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = true)
            val result = repository.addChecklistStep(taskId, cleanLabel)
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = false)
            result.fold(
                onSuccess = { step ->
                    syncMessageFlow.value = "Langkah checklist \"${step.label}\" ditambahkan."
                    refreshTaskActivitiesIfOpen(taskId)
                },
                onFailure = { err ->
                    syncMessageFlow.value = err.localizedMessage
                        ?: "Gagal menambahkan langkah checklist."
                }
            )
        }
    }

    fun delegateTechnicians(taskId: Long, assigneeIds: List<Long>) {
        val cleanIds = assigneeIds.filter { it > 0L }.distinct()
        if (cleanIds.isEmpty()) return
        viewModelScope.launch {
            val allTasks = uiState.value.allTasks.ifEmpty {
                runCatching { repository.observeAllTasks().first() }.getOrDefault(emptyList())
            }
            val currentTask = allTasks.firstOrNull { it.id == taskId }
            val existingAssigneeIds = currentTask?.assignees?.map { it.id }?.toSet().orEmpty()
            val newlyAddedIds = cleanIds.filter { it !in existingAssigneeIds }
            val session = sessionStateFlow.value.prefs

            // Enforce 5 active task WIP limit on newly delegated technicians (excluding this task)
            if (newlyAddedIds.isNotEmpty() && currentTask?.stage != Stage.DONE) {
                val limitWarning = ActiveTaskLimitPolicy.checkDelegationLimit(
                    allTasks = allTasks,
                    targetAssigneeIds = newlyAddedIds,
                    availableTechnicians = uiState.value.availableTechnicians,
                    currentUserId = session.userId,
                    currentUserName = session.technicianName.ifBlank { "Fajar" },
                    currentUserRole = session.technicianRole.ifBlank { "admin" },
                    excludeTaskId = taskId
                )
                if (limitWarning != null) {
                    activeTaskLimitWarningFlow.value = limitWarning
                    return@launch
                }
            }

            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = true)
            val result = repository.delegateTechnicians(taskId, cleanIds)
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = false)
            result.fold(
                onSuccess = { updated ->
                    val names = updated.assignees.joinToString(", ") { it.name }
                    syncMessageFlow.value = "Tim teknisi diperbarui: $names"
                    refreshTaskActivitiesIfOpen(taskId)
                },
                onFailure = { err ->
                    if (err is ActiveTaskLimitExceededException) {
                        activeTaskLimitWarningFlow.value = ActiveTaskLimitPolicy.warningFromException(err)
                    } else {
                        val errMsg = err.localizedMessage ?: "Gagal mendelegasikan teknisi."
                        if (ActiveTaskLimitPolicy.isClaimLimitExceededPayload(errMsg)) {
                            activeTaskLimitWarningFlow.value = ActiveTaskLimitPolicy.warningFromException(
                                ActiveTaskLimitPolicy.parseServerLimitException(errMsg, isDelegationContext = true)
                            )
                        } else {
                            syncMessageFlow.value = errMsg
                        }
                    }
                }
            )
        }
    }

    fun toggleTaskTimer(taskId: Long) {
        workTask(taskId)
    }

    fun dismissActiveTaskLimitWarning() {
        activeTaskLimitWarningFlow.value = null
    }

    fun workTask(taskId: Long) {
        viewModelScope.launch {
            val allTasks = uiState.value.allTasks.ifEmpty {
                runCatching { repository.observeAllTasks().first() }.getOrDefault(emptyList())
            }
            val session = sessionStateFlow.value.prefs
            val limitWarning = ActiveTaskLimitPolicy.checkSelfTakeOrWorkLimit(
                allTasks = allTasks,
                currentUserId = session.userId,
                currentUserName = session.technicianName.ifBlank { "Fajar" },
                targetTaskId = taskId
            )
            if (limitWarning != null) {
                activeTaskLimitWarningFlow.value = limitWarning
                return@launch
            }

            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = true)
            val result = repository.workTask(taskId)
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = false)
            result.fold(
                onSuccess = { msg ->
                    syncMessageFlow.value = msg
                    refreshTaskActivitiesIfOpen(taskId)
                },
                onFailure = { err ->
                    if (err is ActiveTaskLimitExceededException) {
                        activeTaskLimitWarningFlow.value = ActiveTaskLimitPolicy.warningFromException(err)
                    } else {
                        val errMsg = err.localizedMessage ?: "Gagal memulai pengerjaan tiket."
                        if (ActiveTaskLimitPolicy.isClaimLimitExceededPayload(errMsg)) {
                            activeTaskLimitWarningFlow.value = ActiveTaskLimitPolicy.warningFromException(
                                ActiveTaskLimitPolicy.parseServerLimitException(errMsg, isDelegationContext = false)
                            )
                        } else {
                            syncMessageFlow.value = errMsg
                        }
                    }
                }
            )
        }
    }

    fun claimTask(taskId: Long) {
        viewModelScope.launch {
            val allTasks = uiState.value.allTasks.ifEmpty {
                runCatching { repository.observeAllTasks().first() }.getOrDefault(emptyList())
            }
            val session = sessionStateFlow.value.prefs
            val limitWarning = ActiveTaskLimitPolicy.checkSelfTakeOrWorkLimit(
                allTasks = allTasks,
                currentUserId = session.userId,
                currentUserName = session.technicianName.ifBlank { "Fajar" },
                targetTaskId = taskId
            )
            if (limitWarning != null) {
                activeTaskLimitWarningFlow.value = limitWarning
                return@launch
            }

            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = true)
            val result = repository.claimTask(taskId)
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = false)
            result.fold(
                onSuccess = { claimed ->
                    syncMessageFlow.value = "📌 Tiket ${claimed.ticketCode} berhasil diambil."
                    refreshTaskActivitiesIfOpen(taskId)
                },
                onFailure = { err ->
                    if (err is ActiveTaskLimitExceededException) {
                        activeTaskLimitWarningFlow.value = ActiveTaskLimitPolicy.warningFromException(err)
                    } else {
                        val errMsg = err.localizedMessage ?: "Gagal mengambil tiket."
                        if (ActiveTaskLimitPolicy.isClaimLimitExceededPayload(errMsg)) {
                            activeTaskLimitWarningFlow.value = ActiveTaskLimitPolicy.warningFromException(
                                ActiveTaskLimitPolicy.parseServerLimitException(errMsg, isDelegationContext = false)
                            )
                        } else {
                            syncMessageFlow.value = errMsg
                        }
                    }
                }
            )
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = true)
            val result = repository.deleteTask(taskId)
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = false)
            result.fold(
                onSuccess = {
                    if (selectedSheetTaskIdFlow.value == taskId) {
                        dismissTaskDetail()
                    }
                    syncMessageFlow.value = "Tiket berhasil dihapus."
                },
                onFailure = { err ->
                    syncMessageFlow.value = err.localizedMessage
                        ?: "Anda tidak memiliki izin untuk menghapus tiket ini."
                }
            )
        }
    }

    fun updateTaskStage(taskId: Long, newStage: Stage) {
        viewModelScope.launch {
            if (newStage != Stage.DONE) {
                val allTasks = uiState.value.allTasks.ifEmpty {
                    runCatching { repository.observeAllTasks().first() }.getOrDefault(emptyList())
                }
                val session = sessionStateFlow.value.prefs
                val limitWarning = ActiveTaskLimitPolicy.checkSelfTakeOrWorkLimit(
                    allTasks = allTasks,
                    currentUserId = session.userId,
                    currentUserName = session.technicianName.ifBlank { "Fajar" },
                    targetTaskId = taskId
                )
                if (limitWarning != null) {
                    activeTaskLimitWarningFlow.value = limitWarning
                    return@launch
                }
            }
            markLocalMutationForEchoSuppression()
            repository.updateTaskStage(taskId, newStage)
            refreshTaskActivitiesIfOpen(taskId)
        }
    }

    fun markTaskCompleted(taskId: Long) {
        viewModelScope.launch {
            markLocalMutationForEchoSuppression()
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = true)
            val result = repository.completeTaskWithPolicy(taskId)
            detailWorkspaceFlow.value = detailWorkspaceFlow.value.copy(isUpdatingAction = false)
            result.fold(
                onSuccess = {
                    if (selectedSheetTaskIdFlow.value == taskId) {
                        dismissTaskDetail()
                    }
                    syncMessageFlow.value = "Tiket ditandai SELESAI."
                },
                onFailure = { err ->
                    syncMessageFlow.value = err.localizedMessage
                        ?: "Anda tidak memiliki izin untuk menyelesaikan tiket ini."
                }
            )
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

    fun onAppResumed() {
        isAppInForegroundFlow.value = true
        syncTodayHorizon(selectToday = false)
        val now = clockMillisProvider()
        if (lastResumeSyncAtMillis > 0L && now - lastResumeSyncAtMillis < RESUME_SYNC_COOLDOWN_MILLIS) return
        val isAuthenticated = appPreferences == null || sessionStateFlow.value.prefs.authToken.isNotBlank()
        if (isAuthenticated && isNetworkOnlineFlow.value && !isSyncingFlow.value) {
            lastResumeSyncAtMillis = now
            triggerSyncInternal(isInitialBaselineSync = false, silentOnSuccess = true)
        }
    }

    fun onAppBackgrounded() {
        isAppInForegroundFlow.value = false
        sseRefreshJob?.cancel()
        flushActiveTimerOnBackground()
    }

    fun refreshCatalogs() {
        val isAuthenticated = appPreferences == null || sessionStateFlow.value.prefs.authToken.isNotBlank()
        if (!isAuthenticated || !isNetworkOnlineFlow.value) return
        viewModelScope.launch {
            repository.refreshCatalogsFromServer()
        }
    }

    fun createQuickTask(draft: QuickTaskDraft) {
        createQuickTask(
            title = draft.title,
            category = draft.category,
            priority = draft.priority,
            location = draft.location,
            scheduledTimeMinutes = draft.scheduledTimeMinutes,
            technicalNotes = draft.technicalNotes,
            labels = draft.labels,
            targetEpochDay = draft.targetEpochDay,
            assigneeIds = draft.assigneeIds,
            labelIds = draft.labelIds,
            checklistSteps = draft.checklistSteps,
            isRecurring = draft.isRecurring,
            recurrenceInterval = draft.recurrenceInterval?.apiValue
        )
    }

    fun createQuickTask(
        title: String,
        category: TicketCategory,
        priority: Priority,
        location: String,
        scheduledTimeMinutes: Int? = null,
        technicalNotes: String = "",
        labels: List<String> = emptyList(),
        targetEpochDay: Long? = null,
        assigneeIds: List<Long> = emptyList(),
        labelIds: List<Long> = emptyList(),
        checklistSteps: List<String> = emptyList(),
        isRecurring: Boolean = false,
        recurrenceInterval: String? = null
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val todayEpochDay = baseEpochDayProvider()
        val resolvedEpochDay = (targetEpochDay ?: todayEpochDay).coerceAtLeast(todayEpochDay)
        val isPlannedFuture = resolvedEpochDay > todayEpochDay
        val cleanScheduledMinutes = scheduledTimeMinutes?.coerceIn(0, 23 * 60 + 59)

        viewModelScope.launch {
            val allTasks = uiState.value.allTasks.ifEmpty {
                runCatching { repository.observeAllTasks().first() }.getOrDefault(emptyList())
            }
            val session = sessionStateFlow.value.prefs
            val limitWarning = ActiveTaskLimitPolicy.checkDelegationLimit(
                allTasks = allTasks,
                targetAssigneeIds = assigneeIds,
                availableTechnicians = uiState.value.availableTechnicians,
                currentUserId = session.userId,
                currentUserName = session.technicianName.ifBlank { "Fajar" },
                currentUserRole = session.technicianRole.ifBlank { "admin" }
            )
            if (limitWarning != null) {
                activeTaskLimitWarningFlow.value = limitWarning
                return@launch
            }

            baseEpochDayFlow.value = todayEpochDay
            if (!isPlannedFuture) {
                selectedEpochDayFlow.value = todayEpochDay
            }
            pendingSelfCreatedTitles.add(cleanTitle)
            markLocalMutationForEchoSuppression()
            try {
                val created = repository.createQuickTask(
                    title = cleanTitle,
                    category = category,
                    priority = priority,
                    epochDay = resolvedEpochDay,
                    location = location,
                    scheduledTimeMinutes = cleanScheduledMinutes,
                    technicalNotes = technicalNotes,
                    labels = labels,
                    assigneeIds = assigneeIds,
                    labelIds = labelIds,
                    checklistSteps = checklistSteps,
                    isRecurring = isRecurring,
                    recurrenceInterval = recurrenceInterval
                )
                if (created.id > 0L) {
                    knownAssignedTaskIds.add(created.id)
                }
                val baseSummary = if (isPlannedFuture) {
                    val clockSuffix = cleanScheduledMinutes?.let {
                        " pukul ${DateEpochUtil.formatScheduledClock(it)}"
                    }.orEmpty()
                    "Tiket ${created.ticketCode} dijadwalkan untuk ${DateEpochUtil.formatFullDate(resolvedEpochDay)}$clockSuffix"
                } else if (cleanScheduledMinutes != null) {
                    "Tiket ${created.ticketCode} dijadwalkan hari ini pukul ${DateEpochUtil.formatScheduledClock(cleanScheduledMinutes)}"
                } else {
                    "Tiket ${created.ticketCode} ditambahkan ke jadwal hari ini"
                }
                syncMessageFlow.value = if (created.isSynced) {
                    "$baseSummary & langsung tersinkron ke server."
                } else {
                    val errorHint = repository.getLastSyncError()?.take(90)
                    if (!errorHint.isNullOrBlank()) {
                        "$baseSummary (antre auto-sync: $errorHint)."
                    } else {
                        "$baseSummary (tersimpan lokal, akan sync otomatis saat online)."
                    }
                }
            } catch (limitEx: ActiveTaskLimitExceededException) {
                activeTaskLimitWarningFlow.value = ActiveTaskLimitPolicy.warningFromException(limitEx)
            } finally {
                pendingSelfCreatedTitles.remove(cleanTitle)
            }
        }
    }

    fun triggerSync() {
        triggerSyncInternal(isInitialBaselineSync = false, silentOnSuccess = false)
    }

    private fun triggerSyncInternal(
        isInitialBaselineSync: Boolean,
        silentOnSuccess: Boolean = false
    ) {
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
                        if (result.failedUploadCount > 0) {
                            val detail = result.lastUploadError?.take(95) ?: "periksa koneksi server"
                            syncMessageFlow.value =
                                "Unduh ${result.downloadedCount} tiket, namun ${result.failedUploadCount} gagal unggah ($detail)."
                            recordActivityNotification(
                                severity = NotificationSeverity.WARNING,
                                badgeText = "UNGGAH GAGAL",
                                title = "${result.failedUploadCount} tiket belum terunggah ke server",
                                subtitle = detail
                            )
                        } else {
                            if (!silentOnSuccess || result.uploadedCount > 0) {
                                syncMessageFlow.value =
                                    "Tersinkron ke server PingDulu (${result.downloadedCount} tiket, ${result.uploadedCount} unggah)."
                            }
                            // Only record SYNC SUKSES in Notification Center for explicit manual syncs,
                            // preventing unread badge spam on silent resume / reconnect / startup syncs.
                            if (!silentOnSuccess && !isInitialBaselineSync &&
                                (result.downloadedCount > 0 || result.uploadedCount > 0)
                            ) {
                                recordActivityNotification(
                                    severity = NotificationSeverity.INFO,
                                    badgeText = "SYNC SUKSES",
                                    title = "Sinkronisasi server selesai",
                                    subtitle = "${result.downloadedCount} tiket diunduh, ${result.uploadedCount} perubahan lokal terunggah."
                                )
                            }
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
                        if (!silentOnSuccess) {
                            val pendingInfo = if (result.pendingUploadCount > 0) {
                                "${result.pendingUploadCount} perubahan antre di Room lokal (${result.reason.take(80)})."
                            } else {
                                "Data Room lokal siap digunakan offline."
                            }
                            syncMessageFlow.value = "Mode Offline: $pendingInfo"
                        }
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
                        launch { repository.refreshCatalogsFromServer() }
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
            selectedSheetTaskIdFlow.value = null
            detailWorkspaceJob?.cancel()
            detailWorkspaceFlow.value = TaskDetailWorkspaceState()
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
        // Stopwatch removed in Patch Ke-2; retained as no-op for lifecycle compatibility.
    }

    private suspend fun flushCurrentRunningTimer() {
        // Stopwatch removed in Patch Ke-2.
    }

    private fun buildScheduleDays(
        allTasks: List<Task>,
        baseEpochDay: Long,
        horizonEpochDays: List<Long>,
        holidaysByEpochDay: Map<Long, NationalHoliday>
    ): List<DayHorizonModel> {
        val groupedByDay = allTasks.groupBy { it.scheduledDateEpochDay }
        val overdueActiveTasks = allTasks.filter {
            it.scheduledDateEpochDay < baseEpochDay && it.stage != Stage.DONE
        }
        return horizonEpochDays.map { epochDay ->
            val dayTasks = if (epochDay == baseEpochDay) {
                groupedByDay[epochDay].orEmpty() + overdueActiveTasks
            } else {
                groupedByDay[epochDay].orEmpty()
            }
            val holiday = holidaysByEpochDay[epochDay]
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
                indicatorType = indicator,
                isHoliday = holiday != null,
                holidayName = holiday?.name
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
        val nowMillis = clockMillisProvider()
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
        // Deduplicate repeating transient sync status badges so they replace instead of piling up
        val existingFiltered = if (category == NotificationCategory.SYNC && taskId == null) {
            activityNotificationsFlow.value.filterNot { it.badgeText == badgeText }
        } else {
            activityNotificationsFlow.value
        }
        activityNotificationsFlow.value = (listOf(entry) + existingFiltered).take(8)
    }

    private fun buildOperationalNotifications(
        allTasks: List<Task>,
        unsyncedTasks: List<Task>,
        syncBadge: SyncStatusBadge,
        todayEpochDay: Long,
        activityEvents: List<FieldNotificationItem>,
        readIds: Set<String>
    ): List<FieldNotificationItem> {
        val items = mutableListOf<FieldNotificationItem>()

        // 1. Active P1 Critical tickets (unfinished)
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

    companion object {
        const val SSE_PULL_DEBOUNCE_MILLIS = 3_000L
        const val SELF_MUTATION_ECHO_SUPPRESS_MILLIS = 4_000L
        const val FALLBACK_POLL_INTERVAL_MILLIS = 90_000L
        const val RESUME_SYNC_COOLDOWN_MILLIS = 45_000L
        val AUTO_PUSH_EXPONENTIAL_BACKOFF_MS = longArrayOf(15_000L, 30_000L, 60_000L, 120_000L)

        internal fun isPermanentSyncError(error: String?): Boolean {
            if (error.isNullOrBlank()) return false
            return error.contains("HTTP 400") ||
                error.contains("HTTP 401") ||
                error.contains("HTTP 403") ||
                error.contains("HTTP 404") ||
                error.contains("HTTP 422")
        }
    }
}

class ScheduleViewModelFactory(
    private val repository: TaskRepository,
    private val appPreferences: AppPreferences,
    private val sseEventClient: SseEventClient,
    private val notificationDispatcher: TaskNotificationDispatcher? = null,
    private val connectivityMonitor: ConnectivityMonitor? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            return ScheduleViewModel(
                repository = repository,
                appPreferences = appPreferences,
                sseEventClient = sseEventClient,
                notificationDispatcher = notificationDispatcher,
                connectivityMonitor = connectivityMonitor,
                enableAutoSyncRetry = true
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
