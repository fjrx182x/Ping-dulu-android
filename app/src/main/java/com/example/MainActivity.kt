package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.Stage
import com.example.presentation.calendar.CalendarScreen
import com.example.presentation.components.ActiveTaskLimitWarningDialog
import com.example.presentation.components.AppDestination
import com.example.presentation.components.FieldOpsDrawerSheet
import com.example.presentation.components.FieldToolkitBottomSheet
import com.example.presentation.components.FieldToolkitTab
import com.example.presentation.components.PingDuluBottomNavigationBar
import com.example.presentation.components.PingDuluNavigationRail
import com.example.presentation.components.QuickAddTaskSheet
import com.example.presentation.components.TaskDetailBottomSheet
import com.example.presentation.kanban.KanbanBoardScreen
import com.example.presentation.login.LoginScreen
import com.example.presentation.login.SessionLoadingSplash
import com.example.presentation.schedule.ScheduleScreen
import com.example.presentation.schedule.ScheduleUiState
import com.example.presentation.schedule.ScheduleViewModel
import com.example.presentation.schedule.ScheduleViewModelFactory
import com.example.presentation.settings.SettingsSyncScreen
import com.example.presentation.spk.SpkHistoryScreen
import com.example.ui.theme.PingDuluFieldOpsTheme
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val appContainer by lazy { AppContainer.getInstance(applicationContext) }
    private val deepLinkAgendaNonce = MutableStateFlow(0)

    private val scheduleViewModel: ScheduleViewModel by viewModels {
        ScheduleViewModelFactory(
            repository = appContainer.taskRepository,
            appPreferences = appContainer.appPreferences,
            sseEventClient = appContainer.sseEventClient,
            notificationDispatcher = appContainer.notificationHelper,
            connectivityMonitor = appContainer.connectivityMonitor
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationDeepLinkIntent(intent)

        setContent {
            val uiState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
            val agendaDeepLinkNonce by deepLinkAgendaNonce.collectAsStateWithLifecycle()
            val useDarkSystemBars = uiState.isDarkTheme || !uiState.isSessionLoaded || !uiState.isLoggedIn

            LaunchedEffect(useDarkSystemBars) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkSystemBars) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (useDarkSystemBars) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
            }

            PingDuluFieldOpsTheme(darkTheme = uiState.isDarkTheme) {
                when {
                    !uiState.isSessionLoaded -> {
                        SessionLoadingSplash()
                    }
                    !uiState.isLoggedIn -> {
                        LoginScreen(
                            isLoggingIn = uiState.isLoggingIn,
                            errorMessage = uiState.loginErrorMessage,
                            onClearError = scheduleViewModel::clearLoginError,
                            onLogin = scheduleViewModel::loginToServer
                        )
                    }
                    else -> {
                        PingDuluAppRoot(
                            uiState = uiState,
                            viewModel = scheduleViewModel,
                            agendaDeepLinkNonce = agendaDeepLinkNonce
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationDeepLinkIntent(intent)
    }

    private fun handleNotificationDeepLinkIntent(targetIntent: Intent?) {
        val taskId = targetIntent?.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L) ?: -1L
        if (taskId > 0L) {
            targetIntent?.removeExtra(NotificationHelper.EXTRA_TASK_ID)
            deepLinkAgendaNonce.value += 1
            scheduleViewModel.openTaskFromNotification(taskId)
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleViewModel.onAppResumed()
    }

    override fun onStop() {
        super.onStop()
        scheduleViewModel.onAppBackgrounded()
    }
}

@Composable
private fun PingDuluAppRoot(
    uiState: ScheduleUiState,
    viewModel: ScheduleViewModel,
    agendaDeepLinkNonce: Int
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val detailWorkspaceState by viewModel.detailWorkspaceState.collectAsStateWithLifecycle()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.AGENDA) }
    var showSpkHistoryFromSettings by rememberSaveable { mutableStateOf(false) }
    var showQuickAddSheet by rememberSaveable { mutableStateOf(false) }
    var activeToolkitTab by rememberSaveable { mutableStateOf<FieldToolkitTab?>(null) }
    var hasPromptedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled gracefully; in-app Notification Center always remains active.
    }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn &&
            !hasPromptedNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                hasPromptedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(agendaDeepLinkNonce) {
        if (agendaDeepLinkNonce > 0) {
            showSpkHistoryFromSettings = false
            currentDestination = AppDestination.AGENDA
        }
    }

    // Return smoothly from secondary tabs to Agenda on system Back press
    BackHandler(enabled = currentDestination != AppDestination.AGENDA) {
        showSpkHistoryFromSettings = false
        currentDestination = AppDestination.AGENDA
    }

    // Return from SPK History sub-screen back to Settings (higher priority than tab BackHandler)
    BackHandler(enabled = currentDestination == AppDestination.SETTINGS && showSpkHistoryFromSettings) {
        showSpkHistoryFromSettings = false
    }

    // Close navigation drawer first when Back is pressed (last-composed enabled BackHandler has highest priority)
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    LaunchedEffect(uiState.syncMessage) {
        val message = uiState.syncMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSyncMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentDestination == AppDestination.AGENDA || drawerState.isOpen,
        drawerContent = {
            FieldOpsDrawerSheet(
                uiState = uiState,
                onSelectShiftStatus = viewModel::setShiftStatus,
                onToggleOnlyMyTickets = viewModel::toggleOnlyMyTickets,
                onToggleOnlyCriticalP1 = viewModel::toggleOnlyCriticalP1,
                onSelectCategory = viewModel::selectCategoryFilter,
                onResetFilters = viewModel::resetDrawerFilters,
                onOpenToolkit = { tab ->
                    coroutineScope.launch {
                        drawerState.close()
                        activeToolkitTab = tab
                    }
                },
                onOpenTask = { taskId ->
                    coroutineScope.launch {
                        drawerState.close()
                        viewModel.openTaskDetail(taskId)
                    }
                },
                onTriggerSync = {
                    coroutineScope.launch {
                        drawerState.close()
                        viewModel.triggerSync()
                    }
                }
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 720.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        PingDuluBottomNavigationBar(
                            currentDestination = currentDestination,
                            onDestinationSelected = { dest ->
                                currentDestination = dest
                                if (dest != AppDestination.SETTINGS) {
                                    showSpkHistoryFromSettings = false
                                }
                            },
                            onQuickAddClicked = { showQuickAddSheet = true }
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        PingDuluNavigationRail(
                            currentDestination = currentDestination,
                            onDestinationSelected = { dest ->
                                currentDestination = dest
                                if (dest != AppDestination.SETTINGS) {
                                    showSpkHistoryFromSettings = false
                                }
                            },
                            onQuickAddClicked = { showQuickAddSheet = true }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentDestination) {
                            AppDestination.AGENDA -> {
                                ScheduleScreen(
                                    uiState = uiState,
                                    onDaySelected = viewModel::onDaySelected,
                                    onStatusFilterSelected = viewModel::onStatusFilterSelected,
                                    onLayoutModeSelected = viewModel::onToggleLayoutMode,
                                    onTaskClicked = viewModel::openTaskDetail,
                                    onCompleteHeroTask = viewModel::markTaskCompleted,
                                    onTriggerSync = viewModel::triggerSync,
                                    onToggleTheme = viewModel::toggleTheme,
                                    onMarkNotificationRead = viewModel::markNotificationRead,
                                    onMarkAllNotificationsRead = viewModel::markAllNotificationsRead,
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                    onResetDrawerFilters = viewModel::resetDrawerFilters
                                )
                            }

                            AppDestination.KANBAN -> {
                                KanbanBoardScreen(
                                    uiState = uiState,
                                    onTaskClicked = viewModel::openTaskDetail,
                                    onMoveStage = viewModel::updateTaskStage,
                                    onWorkTask = viewModel::workTask,
                                    onClaimTask = viewModel::claimTask,
                                    onCompleteTask = viewModel::markTaskCompleted
                                )
                            }

                            AppDestination.CALENDAR -> {
                                CalendarScreen(
                                    uiState = uiState,
                                    onMonthChanged = viewModel::onCalendarMonthChanged,
                                    onDaySelected = viewModel::onCalendarDaySelected,
                                    onJumpToToday = viewModel::jumpCalendarToToday,
                                    onTaskClicked = viewModel::openTaskDetail
                                )
                            }

                            AppDestination.SETTINGS -> {
                                if (showSpkHistoryFromSettings) {
                                    SpkHistoryScreen(
                                        uiState = uiState,
                                        onTaskClicked = viewModel::openTaskDetail,
                                        onBackClick = { showSpkHistoryFromSettings = false }
                                    )
                                } else {
                                    SettingsSyncScreen(
                                        uiState = uiState,
                                        onTriggerSync = viewModel::triggerSync,
                                        onLogout = viewModel::logoutFromServer,
                                        onToggleTheme = viewModel::toggleTheme,
                                        onOpenSpkHistory = { showSpkHistoryFromSettings = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Global Interactive Work Order Bottom Sheet
    val selectedTask = uiState.selectedTaskForSheet
    LaunchedEffect(selectedTask?.id) {
        if (selectedTask != null && uiState.availableTechnicians.isEmpty()) {
            viewModel.refreshCatalogs()
        }
    }
    if (selectedTask != null) {
        TaskDetailBottomSheet(
            task = selectedTask,
            onDismiss = viewModel::dismissTaskDetail,
            onToggleChecklistItem = viewModel::toggleChecklistItem,
            onToggleTimer = viewModel::workTask,
            onMarkCompleted = viewModel::markTaskCompleted,
            onReopenTask = { taskId -> viewModel.updateTaskStage(taskId, Stage.IN_PROGRESS) },
            workspaceState = detailWorkspaceState,
            serverBaseUrl = uiState.serverBaseUrl,
            authToken = uiState.authToken,
            onUploadAttachment = viewModel::uploadTaskAttachment,
            onPostComment = viewModel::postTaskComment,
            onSnoozeTask = viewModel::snoozeTask,
            onSetTaskBlocked = viewModel::setTaskBlocked,
            onRefreshWorkspace = viewModel::refreshTaskDetailWorkspace,
            currentUserId = uiState.currentUserId,
            currentUserName = uiState.technicianName,
            currentUserRole = uiState.technicianRole,
            availableTechnicians = uiState.availableTechnicians,
            onWorkTask = viewModel::workTask,
            onClaimTask = viewModel::claimTask,
            onDeleteTask = viewModel::deleteTask,
            onAddChecklistStep = viewModel::addChecklistStep,
            onDelegateTechnicians = viewModel::delegateTechnicians
        )
    }

    // Refresh users & labels catalog whenever Quick Add Ticket sheet opens
    LaunchedEffect(showQuickAddSheet) {
        if (showQuickAddSheet) {
            viewModel.refreshCatalogs()
        }
    }

    // Global Quick Add Ticket Bottom Sheet
    if (showQuickAddSheet) {
        val todayHorizon = uiState.scheduleDays.firstOrNull { it.isToday }
        val todayEpochDay = todayHorizon?.epochDay ?: com.example.domain.model.DateEpochUtil.todayEpochDay()
        val todayLabel = todayHorizon?.fullDateLabel ?: uiState.selectedFullDateLabel
        QuickAddTaskSheet(
            todayEpochDay = todayEpochDay,
            todayDateLabel = todayLabel,
            availableTechnicians = uiState.availableTechnicians,
            officialLabels = uiState.officialLabels,
            currentUserId = uiState.currentUserId,
            currentTechnicianName = uiState.technicianName,
            onDismiss = { showQuickAddSheet = false },
            onSubmitDraft = viewModel::createQuickTask
        )
    }

    // Offline & Self-Hosted Field Ops Network Toolkit Sheet
    activeToolkitTab?.let { initialTab ->
        FieldToolkitBottomSheet(
            initialTab = initialTab,
            serverHostHint = uiState.serverBaseUrl,
            authToken = uiState.authToken,
            onDismiss = { activeToolkitTab = null }
        )
    }

    // Global 5-Active-Task WIP Limit Warning Box
    uiState.activeTaskLimitWarning?.let { warning ->
        ActiveTaskLimitWarningDialog(
            warning = warning,
            onDismiss = viewModel::dismissActiveTaskLimitWarning
        )
    }
}
