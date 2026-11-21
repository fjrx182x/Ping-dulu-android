package com.example.presentation.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.NationalHoliday
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.presentation.components.TaskListCard
import com.example.presentation.schedule.ScheduleUiState
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.P2Amber
import com.example.ui.theme.PillDoneMint
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme

private val WEEKDAY_LABELS = listOf("SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN")

@Composable
fun CalendarScreen(
    uiState: ScheduleUiState,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    onDaySelected: (epochDay: Long) -> Unit,
    onJumpToToday: () -> Unit,
    onTaskClicked: (taskId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val year = uiState.calendarYear
    val month = uiState.calendarMonth
    val selectedEpochDay = uiState.calendarSelectedEpochDay
    val todayEpochDay = remember(uiState.scheduleDays) {
        uiState.scheduleDays.firstOrNull { it.isToday }?.epochDay ?: DateEpochUtil.todayEpochDay()
    }

    LaunchedEffect(year, month) {
        onMonthChanged(year, month)
    }

    val tasksByEpochDay = remember(uiState.allTasks) {
        uiState.allTasks.groupBy { it.scheduledDateEpochDay }
    }

    val selectedDayTasks = remember(tasksByEpochDay, selectedEpochDay) {
        tasksByEpochDay[selectedEpochDay].orEmpty().sortedWith(
            compareBy<Task> { it.scheduledTimeMinutes ?: 9999 }
                .thenBy { it.priority.sortRank }
                .thenByDescending { it.createdAtMillis }
                .thenByDescending { it.id }
        )
    }

    val selectedHoliday = uiState.holidaysByEpochDay[selectedEpochDay]

    val monthHolidays = remember(uiState.holidaysByEpochDay, year, month) {
        uiState.holidaysByEpochDay.values
            .filter { DateEpochUtil.yearMonthFromEpochDay(it.epochDay) == (year to month) }
            .sortedBy { it.epochDay }
    }

    val daysInMonth = remember(year, month) { DateEpochUtil.daysInMonth(year, month) }
    val leadingBlanks = remember(year, month) { DateEpochUtil.firstDayOfWeekMondayBased(year, month) }
    val monthCells = remember(year, month, daysInMonth, leadingBlanks) {
        val cells = mutableListOf<Long?>()
        repeat(leadingBlanks) { cells += null }
        for (day in 1..daysInMonth) {
            cells += DateEpochUtil.toEpochDay(year, month - 1, day)
        }
        while (cells.size % 7 != 0) {
            cells += null
        }
        cells.chunked(7)
    }

    val extendedColors = PingDuluTheme.extendedColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("calendar_screen")
    ) {
        // Top Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kalender Lapangan",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hari Libur Nasional (SKB 3 Menteri) & Jadwal Tiket",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        onClick = onJumpToToday,
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("calendar_today_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Today,
                                contentDescription = "Lompat ke hari ini",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Hari Ini",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        if (uiState.isLoadingCalendar) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = PingDuluNavy
            )
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 104.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Monthly Calendar Grid Card
                item(key = "calendar_grid_card") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calendar_month_card"),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Month Navigation Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onMonthChanged(year, month - 1) },
                                    modifier = Modifier.testTag("calendar_prev_month_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronLeft,
                                        contentDescription = "Bulan sebelumnya",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = PingDuluNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = DateEpochUtil.formatMonthYearIndonesian(year, month),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("calendar_month_title")
                                    )
                                }

                                IconButton(
                                    onClick = { onMonthChanged(year, month + 1) },
                                    modifier = Modifier.testTag("calendar_next_month_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = "Bulan berikutnya",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Weekday Headers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                WEEKDAY_LABELS.forEachIndexed { idx, label ->
                                    val isSunday = idx == 6
                                    Text(
                                        text = label,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSunday) P1Crimson else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Calendar Weeks Grid
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                monthCells.forEach { weekRow ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        weekRow.forEach { cellEpochDay ->
                                            if (cellEpochDay == null) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                )
                                            } else {
                                                val cellTasks = tasksByEpochDay[cellEpochDay].orEmpty()
                                                val holiday = uiState.holidaysByEpochDay[cellEpochDay]
                                                CalendarDayCell(
                                                    epochDay = cellEpochDay,
                                                    isToday = cellEpochDay == todayEpochDay,
                                                    isSelected = cellEpochDay == selectedEpochDay,
                                                    holiday = holiday,
                                                    tasks = cellTasks,
                                                    onClick = { onDaySelected(cellEpochDay) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Compact Legend
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CalendarLegendItem(color = P1Crimson, label = "Libur / P1")
                                CalendarLegendItem(color = P2Amber, label = "P2 High")
                                CalendarLegendItem(color = PingDuluNavy, label = "Terjadwal")
                                CalendarLegendItem(color = PillDoneMint, label = "Selesai")
                            }
                        }
                    }
                }

                // 2. Selected Day National Holiday Banner (if selected date is a holiday)
                if (selectedHoliday != null) {
                    item(key = "selected_holiday_banner") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calendar_selected_holiday_banner"),
                            shape = RoundedCornerShape(16.dp),
                            color = extendedColors.priorityP1.container,
                            border = BorderStroke(1.dp, extendedColors.priorityP1.border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EventBusy,
                                    contentDescription = "Hari libur nasional",
                                    tint = extendedColors.priorityP1.content,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (selectedHoliday.isCutiBersama) {
                                            "CUTI BERSAMA NASIONAL"
                                        } else {
                                            "HARI LIBUR NASIONAL"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = extendedColors.priorityP1.content,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = selectedHoliday.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Selected Date Tasks Header
                item(key = "selected_day_tasks_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateEpochUtil.formatFullDate(selectedEpochDay),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("calendar_selected_date_label")
                        )
                        Text(
                            text = "${selectedDayTasks.size} tiket terjadwal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 4. Selected Date Tasks List or Empty State
                if (selectedDayTasks.isEmpty()) {
                    item(key = "calendar_empty_day") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calendar_empty_day_card"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EventAvailable,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Tidak ada tiket dijadwalkan pada tanggal ini",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Pilih tanggal bertanda titik untuk melihat jadwal lapangan atau tekan (+) untuk menjadwalkan tiket baru.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(items = selectedDayTasks, key = { "cal_task_${it.id}" }) { task ->
                        TaskListCard(
                            task = task,
                            onClick = { onTaskClicked(task.id) }
                        )
                    }
                }

                // 5. National Holidays List for Displayed Month
                if (monthHolidays.isNotEmpty()) {
                    item(key = "month_holidays_card") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calendar_month_holidays_list"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "DAFTAR HARI LIBUR · ${DateEpochUtil.formatMonthYearIndonesian(year, month).uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                                monthHolidays.forEach { holiday ->
                                    Surface(
                                        onClick = { onDaySelected(holiday.epochDay) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (holiday.epochDay == selectedEpochDay) {
                                            extendedColors.priorityP1.container
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = P1Crimson.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = DateEpochUtil.formatShortDayMonth(holiday.epochDay),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = P1Crimson
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = holiday.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (holiday.isCutiBersama) {
                                                    Text(
                                                        text = "Cuti Bersama",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = P1Crimson
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    epochDay: Long,
    isToday: Boolean,
    isSelected: Boolean,
    holiday: NationalHoliday?,
    tasks: List<Task>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSunday = remember(epochDay) { DateEpochUtil.isSunday(epochDay) }
    val isRedDay = holiday != null || isSunday
    val dayNumber = remember(epochDay) { DateEpochUtil.dayOfMonth(epochDay) }

    val containerColor = when {
        isSelected && holiday != null -> P1Crimson
        isSelected -> PingDuluNavy
        holiday != null -> P1Crimson.copy(alpha = 0.10f)
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> Color.White
        isRedDay -> P1Crimson
        isToday -> PingDuluNavy
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderStroke = when {
        isSelected -> null
        isToday -> BorderStroke(1.5.dp, PingDuluNavy)
        holiday != null -> BorderStroke(1.dp, P1Crimson.copy(alpha = 0.35f))
        else -> null
    }

    val taskDotColor = when {
        tasks.isEmpty() -> null
        tasks.any { it.priority == Priority.P1_CRITICAL && it.stage != Stage.DONE } -> P1Crimson
        tasks.any { it.priority == Priority.P2_HIGH && it.stage != Stage.DONE } -> P2Amber
        tasks.all { it.stage == Stage.DONE } -> PillDoneMint
        else -> if (isSelected) Color.White else PingDuluNavy
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                selected = isSelected
                contentDescription = buildString {
                    append(DateEpochUtil.formatFullDate(epochDay))
                    if (holiday != null) append(", ${holiday.name}")
                    if (tasks.isNotEmpty()) append(", ${tasks.size} tiket")
                }
            }
            .testTag("calendar_day_$epochDay"),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = borderStroke
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayNumber.toString(),
                fontSize = 13.sp,
                fontWeight = if (isSelected || isToday || isRedDay) FontWeight.ExtraBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (holiday != null) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else P1Crimson)
                    )
                }
                if (taskDotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else taskDotColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarLegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
