package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
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
            notificationDispatcher = appContainer.notificationHelper
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
        scheduleViewModel.syncTodayHorizon()
    }

    override fun onStop() {
        super.onStop()
        scheduleViewModel.flushActiveTimerOnBackground()
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
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.AGENDA) }
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
            currentDestination = AppDestination.AGENDA
        }
    }

    // Return smoothly from secondary tabs to Agenda on system Back press
    BackHandler(enabled = currentDestination != AppDestination.AGENDA) {
        currentDestination = AppDestination.AGENDA
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
                            onDestinationSelected = { currentDestination = it },
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
                            onDestinationSelected = { currentDestination = it },
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
                                    onToggleHeroTimer = viewModel::toggleTaskTimer,
                                    onOpenMaps = { location -> openLocationInMaps(context, location) },
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
                                    onMoveStage = viewModel::updateTaskStage
                                )
                            }

                            AppDestination.SPK -> {
                                SpkHistoryScreen(
                                    uiState = uiState,
                                    onTaskClicked = viewModel::openTaskDetail
                                )
                            }

                            AppDestination.SETTINGS -> {
                                SettingsSyncScreen(
                                    uiState = uiState,
                                    onTriggerSync = viewModel::triggerSync,
                                    onLogout = viewModel::logoutFromServer,
                                    onToggleTheme = viewModel::toggleTheme
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Global Interactive Work Order Bottom Sheet
    val selectedTask = uiState.selectedTaskForSheet
    if (selectedTask != null) {
        TaskDetailBottomSheet(
            task = selectedTask,
            onDismiss = viewModel::dismissTaskDetail,
            onToggleChecklistItem = viewModel::toggleChecklistItem,
            onToggleTimer = viewModel::toggleTaskTimer,
            onMarkCompleted = viewModel::markTaskCompleted,
            onReopenTask = { taskId -> viewModel.updateTaskStage(taskId, Stage.IN_PROGRESS) },
            onOpenMaps = { location -> openLocationInMaps(context, location) }
        )
    }

    // Global Quick Add Ticket Bottom Sheet
    if (showQuickAddSheet) {
        val todayLabel = uiState.scheduleDays.firstOrNull { it.isToday }?.fullDateLabel
            ?: uiState.selectedFullDateLabel
        QuickAddTaskSheet(
            todayDateLabel = todayLabel,
            onDismiss = { showQuickAddSheet = false },
            onSubmit = { title, category, priority, location, timeMinutes, notes, labels ->
                viewModel.createQuickTask(
                    title = title,
                    category = category,
                    priority = priority,
                    location = location,
                    scheduledTimeMinutes = timeMinutes,
                    technicalNotes = notes,
                    labels = labels
                )
            }
        )
    }

    // Offline Field Ops Network Toolkit Sheet
    activeToolkitTab?.let { initialTab ->
        FieldToolkitBottomSheet(
            initialTab = initialTab,
            serverHostHint = uiState.serverBaseUrl,
            onDismiss = { activeToolkitTab = null }
        )
    }
}

private fun openLocationInMaps(context: Context, location: String) {
    val encodedQuery = Uri.encode(location.ifBlank { "Lokasi Lapangan" })
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery"))
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
    )
    runCatching {
        if (geoIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(geoIntent)
        } else {
            context.startActivity(webIntent)
        }
    }
}
