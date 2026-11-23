package com.example.presentation.schedule

import androidx.compose.runtime.Immutable
import com.example.data.local.ShiftStatus
import com.example.data.remote.NetworkModule
import com.example.data.remote.SseConnectionState
import com.example.domain.model.ActiveTaskLimitWarning
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.FieldTechnician
import com.example.domain.model.NationalHoliday
import com.example.domain.model.OfficialDefaultLabels
import com.example.domain.model.OfficialLabel
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory

enum class StatusFilter(val label: String) {
    ALL("Semua"),
    BACKLOG("Antrean"),
    IN_PROGRESS("Proses"),
    DONE("Selesai")
}

enum class LayoutMode {
    GRID_2_COLUMN,
    LIST_1_COLUMN
}

enum class DayIndicatorType {
    NONE,
    CRITICAL_P1,
    HIGH_P2,
    ALL_DONE,
    NORMAL
}

enum class SyncStatusBadge(val label: String) {
    SYNCED_LIVE("Tersinkron (Live SSE)"),
    SYNCED_LOCAL("Tersinkron"),
    SYNCING("Menyinkronkan..."),
    PENDING_UPLOAD("Menunggu Sync"),
    OFFLINE_CACHED("Mode Offline")
}

@Immutable
data class DayHorizonModel(
    val epochDay: Long,
    val dayShortName: String,
    val dateNumber: Int,
    val fullDateLabel: String,
    val isToday: Boolean,
    val taskCount: Int,
    val indicatorType: DayIndicatorType,
    val isHoliday: Boolean = false,
    val holidayName: String? = null
)

enum class NotificationCategory(val label: String) {
    ALL("Semua"),
    CRITICAL("Penting"),
    SCHEDULE("Jadwal"),
    SYNC("Sinkronisasi")
}

enum class NotificationSeverity {
    CRITICAL,
    WARNING,
    ACTIVE,
    INFO
}

@Immutable
data class FieldNotificationItem(
    val id: String,
    val category: NotificationCategory,
    val severity: NotificationSeverity,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val timestampLabel: String,
    val taskId: Long? = null,
    val isSyncAction: Boolean = false,
    val isRead: Boolean = false
)

@Immutable
data class StatusFilterCounts(
    val all: Int = 0,
    val backlog: Int = 0,
    val inProgress: Int = 0,
    val done: Int = 0
)

@Immutable
data class ScheduleUiState(
    val currentUserId: Long = 0L,
    val technicianUsername: String = "",
    val technicianName: String = "Fajar",
    val technicianRole: String = "admin",
    val technicianAvatarUrl: String? = null,
    val technicianDepartment: String? = null,
    val technicianSubDepartment: String? = null,
    val technicianIsActive: Boolean = true,
    val isUploadingAvatar: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val profileErrorMessage: String? = null,
    val shiftStatus: ShiftStatus = ShiftStatus.ON_DUTY,
    val greetingTitle: String = "Selamat Pagi, Fajar",
    val selectedEpochDay: Long = DateEpochUtil.todayEpochDay(),
    val horizonSpanLabel: String = "",
    val selectedFullDateLabel: String = "",
    val scheduleDays: List<DayHorizonModel> = emptyList(),
    val activeHeroTask: Task? = null,
    val activeHeroTasks: List<Task> = emptyList(),
    val activeTaskLimitWarning: ActiveTaskLimitWarning? = null,
    val liveHeroSeconds: Long = 0L,
    val allTasks: List<Task> = emptyList(),
    val filteredTasks: List<Task> = emptyList(),
    val unsyncedTasks: List<Task> = emptyList(),
    val filterCounts: StatusFilterCounts = StatusFilterCounts(),
    val selectedStatusFilter: StatusFilter = StatusFilter.ALL,
    val layoutMode: LayoutMode = LayoutMode.GRID_2_COLUMN,
    val onlyMyTickets: Boolean = false,
    val onlyCriticalP1: Boolean = false,
    val selectedCategoryFilter: TicketCategory? = null,
    val selectedTaskForSheet: Task? = null,
    val isSyncing: Boolean = false,
    val unsyncedCount: Int = 0,
    val syncStatusBadge: SyncStatusBadge = SyncStatusBadge.SYNCED_LOCAL,
    val syncMessage: String? = null,
    val lastSyncError: String? = null,
    val isNetworkOnline: Boolean = true,
    val loginErrorMessage: String? = null,
    val sseState: SseConnectionState = SseConnectionState.OFFLINE,
    val serverBaseUrl: String = NetworkModule.DEFAULT_BASE_URL,
    val authToken: String? = null,
    val isSessionLoaded: Boolean = false,
    val isDarkTheme: Boolean = false,
    val lastSyncedAtMillis: Long = 0L,
    val notifications: List<FieldNotificationItem> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val isLoggingIn: Boolean = false,
    val availableTechnicians: List<FieldTechnician> = emptyList(),
    val officialLabels: List<OfficialLabel> = OfficialDefaultLabels,
    val holidaysByEpochDay: Map<Long, NationalHoliday> = emptyMap(),
    val calendarYear: Int = DateEpochUtil.yearMonthFromEpochDay(DateEpochUtil.todayEpochDay()).first,
    val calendarMonth: Int = DateEpochUtil.yearMonthFromEpochDay(DateEpochUtil.todayEpochDay()).second,
    val calendarSelectedEpochDay: Long = DateEpochUtil.todayEpochDay(),
    val isLoadingCalendar: Boolean = false
) {
    val isLoggedIn: Boolean
        get() = !authToken.isNullOrBlank()

    val isAuthenticated: Boolean
        get() = isLoggedIn

    val resolvedAvatarUrl: String?
        get() = com.example.domain.model.resolveAvatarUrl(serverBaseUrl, technicianAvatarUrl)

    val technicianDepartmentDisplay: String?
        get() = com.example.domain.model.formatDepartmentDisplay(technicianDepartment, technicianSubDepartment)

    val activeDrawerFilterCount: Int
        get() = (if (onlyMyTickets) 1 else 0) +
            (if (onlyCriticalP1) 1 else 0) +
            (if (selectedCategoryFilter != null) 1 else 0)
}
