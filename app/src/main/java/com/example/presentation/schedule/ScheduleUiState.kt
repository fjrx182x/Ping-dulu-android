package com.example.presentation.schedule

import androidx.compose.runtime.Immutable
import com.example.data.local.ShiftStatus
import com.example.data.remote.NetworkModule
import com.example.data.remote.SseConnectionState
import com.example.domain.model.DateEpochUtil
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
    val indicatorType: DayIndicatorType
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
    val technicianName: String = "Fajar",
    val technicianRole: String = "admin",
    val shiftStatus: ShiftStatus = ShiftStatus.ON_DUTY,
    val greetingTitle: String = "Selamat Pagi, Fajar",
    val selectedEpochDay: Long = DateEpochUtil.todayEpochDay(),
    val horizonSpanLabel: String = "",
    val selectedFullDateLabel: String = "",
    val scheduleDays: List<DayHorizonModel> = emptyList(),
    val activeHeroTask: Task? = null,
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
    val sseState: SseConnectionState = SseConnectionState.OFFLINE,
    val serverBaseUrl: String = NetworkModule.DEFAULT_BASE_URL,
    val isAuthenticated: Boolean = false,
    val isDarkTheme: Boolean = false,
    val lastSyncedAtMillis: Long = 0L,
    val unreadNotificationCount: Int = 2,
    val isLoggingIn: Boolean = false
) {
    val activeDrawerFilterCount: Int
        get() = (if (onlyMyTickets) 1 else 0) +
            (if (onlyCriticalP1) 1 else 0) +
            (if (selectedCategoryFilter != null) 1 else 0)
}
