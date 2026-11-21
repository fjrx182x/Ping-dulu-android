package com.example

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.presentation.components.HeroActiveJobCard
import com.example.presentation.components.QuickAddTaskSheet
import com.example.presentation.components.TaskDetailBottomSheet
import com.example.presentation.components.TaskGridCard
import com.example.presentation.components.TaskListCard
import com.example.presentation.kanban.KanbanBoardScreen
import com.example.presentation.schedule.ScheduleUiState
import com.example.ui.theme.PingDuluFieldOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlannedWorkUiAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val todayEpochDay: Long = DateEpochUtil.todayEpochDay()

    private fun sampleTask(
        id: Long,
        scheduledDateEpochDay: Long,
        scheduledTimeMinutes: Int?,
        stage: Stage = Stage.BACKLOG
    ): Task = Task(
        id = id,
        ticketCode = "NET-$id",
        title = if (scheduledTimeMinutes != null) "Maintenance Switch Core Lt. 2" else "AP Lobby Down",
        category = TicketCategory.NET,
        scheduledDateEpochDay = scheduledDateEpochDay,
        scheduledTimeMinutes = scheduledTimeMinutes,
        timeSlotLabel = DateEpochUtil.formatMinutesToTimeSlot(scheduledTimeMinutes),
        priority = Priority.P2_HIGH,
        stage = stage,
        technicalNotes = "Catatan teknis audit",
        trackedSeconds = 300L,
        isTimerRunning = stage == Stage.IN_PROGRESS,
        timerStartedAtMillis = if (stage == Stage.IN_PROGRESS) System.currentTimeMillis() else null,
        location = "Ruang Server Utama",
        slaTargetTime = "",
        checklist = listOf(ChecklistItem(1L, "Cek perangkat", false)),
        assignees = listOf(Assignee(3L, "Teknisi Lapangan", "technician", isLead = true)),
        isSynced = true,
        createdAtMillis = System.currentTimeMillis() - 15 * 60 * 1_000L,
        dueDateMillis = null,
        assignedToId = 3L,
        labels = listOf("Jaringan", "Maintenance")
    )

    @Test
    fun quickAddTaskSheet_todayIncidentMode_submitsTodayAndNullClock() {
        var capturedEpochDay: Long? = null
        var capturedClockMinutes: Int? = -1
        var capturedTitle: String? = null

        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                QuickAddTaskSheet(
                    todayEpochDay = todayEpochDay,
                    onDismiss = {},
                    onSubmit = { title, _, _, _, timeMinutes, _, _, targetEpochDay ->
                        capturedTitle = title
                        capturedClockMinutes = timeMinutes
                        capturedEpochDay = targetEpochDay
                    }
                )
            }
        }

        // Planned panel must be hidden by default ("Kerjakan Hari Ini")
        composeRule.onNodeWithTag("quick_add_planned_panel").assertDoesNotExist()

        composeRule.onNodeWithTag("quick_add_title_input").performScrollTo().performTextInput("Router Cabang Down")
        composeRule.onNodeWithTag("quick_add_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("Router Cabang Down", capturedTitle)
        assertEquals(todayEpochDay, capturedEpochDay)
        assertNull(capturedClockMinutes)
    }

    @Test
    fun quickAddTaskSheet_plannedMode_opensPickersAndSubmitsFutureEpochDayAndClock() {
        var capturedEpochDay: Long? = null
        var capturedClockMinutes: Int? = null
        var capturedTitle: String? = null

        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                QuickAddTaskSheet(
                    todayEpochDay = todayEpochDay,
                    onDismiss = {},
                    onSubmit = { title, _, _, _, timeMinutes, _, _, targetEpochDay ->
                        capturedTitle = title
                        capturedClockMinutes = timeMinutes
                        capturedEpochDay = targetEpochDay
                    }
                )
            }
        }

        // Switch to "Jadwalkan Nanti" -> planned panel appears
        composeRule.onNodeWithTag("quick_add_mode_planned").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_add_planned_panel").performScrollTo().assertIsDisplayed()

        // Open and confirm Material 3 DatePickerDialog without crash
        composeRule.onNodeWithTag("quick_add_date_picker_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_add_date_dialog_confirm").performClick()
        composeRule.waitForIdle()

        // Open and confirm Material 3 TimePicker dialog without crash
        composeRule.onNodeWithTag("quick_add_time_picker_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_add_time_dialog_confirm").performClick()
        composeRule.waitForIdle()

        // Tap "+2 Hari" quick preset
        composeRule.onNodeWithTag("quick_add_preset_plus2").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("quick_add_title_input").performScrollTo().performTextInput("Upgrade Firmware Core")
        composeRule.onNodeWithTag("quick_add_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("Upgrade Firmware Core", capturedTitle)
        assertEquals(todayEpochDay + 2L, capturedEpochDay)
        assertEquals(540, capturedClockMinutes) // Default 09:00 = 540 minutes
    }

    @Test
    fun taskCards_showPlannedBadgeOnlyForPlannedWorkAndKeepIncidentClean() {
        val plannedTask = sampleTask(
            id = 801L,
            scheduledDateEpochDay = todayEpochDay + 1L,
            scheduledTimeMinutes = 600 // 10:00
        )
        val incidentTask = sampleTask(
            id = 802L,
            scheduledDateEpochDay = todayEpochDay,
            scheduledTimeMinutes = null
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    TaskGridCard(task = plannedTask, onClick = {})
                    TaskGridCard(task = incidentTask, onClick = {})
                    TaskListCard(task = plannedTask.copy(id = 803L), onClick = {})
                }
            }
        }

        // Planned grid card displays compact "Terjadwal · 10:00" badge
        composeRule.onNodeWithTag("planned_badge_801", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("planned_badge_801")
            .assertIsDisplayed()
            .assertTextContains("Terjadwal · 10:00", substring = true)

        // Incident grid card MUST NOT show "Terjadwal" badge
        composeRule.onNodeWithTag("planned_badge_802", useUnmergedTree = true).assertDoesNotExist()

        // Planned list card displays full "Terjadwal · 10:00 ..." badge
        composeRule.onNodeWithTag("planned_badge_803")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Terjadwal · 10:00", substring = true)
    }

    @Test
    fun heroActiveJobCard_rendersOnDarkPlannedWorkBadgeWithFutureDateAndClock() {
        val plannedHero = sampleTask(
            id = 901L,
            scheduledDateEpochDay = todayEpochDay + 2L,
            scheduledTimeMinutes = 630, // 10:30
            stage = Stage.IN_PROGRESS
        )
        val expectedShortDate = DateEpochUtil.formatShortDayMonth(todayEpochDay + 2L)

        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                HeroActiveJobCard(
                    task = plannedHero,
                    liveSeconds = 300L,
                    onToggleTimer = {},
                    onOpenMaps = {},
                    onOpenWorksheet = {}
                )
            }
        }

        composeRule.onNodeWithTag("planned_badge_901")
            .assertIsDisplayed()
            .assertTextContains("Terjadwal", substring = true)
            .assertTextContains(expectedShortDate, substring = true)
            .assertTextContains("10:30", substring = true)
    }

    @Test
    fun kanbanBoardScreen_rendersPlannedWorkBadgeWithFutureDateAndClock() {
        val plannedKanban = sampleTask(
            id = 902L,
            scheduledDateEpochDay = todayEpochDay + 2L,
            scheduledTimeMinutes = 630, // 10:30
            stage = Stage.IN_PROGRESS
        )
        val expectedShortDate = DateEpochUtil.formatShortDayMonth(todayEpochDay + 2L)

        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                KanbanBoardScreen(
                    uiState = ScheduleUiState(
                        allTasks = listOf(plannedKanban)
                    ),
                    onTaskClicked = {},
                    onMoveStage = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("planned_badge_902")
            .assertIsDisplayed()
            .assertTextContains("Terjadwal", substring = true)
            .assertTextContains(expectedShortDate, substring = true)
            .assertTextContains("10:30", substring = true)
    }

    @Test
    fun taskDetailBottomSheet_showsDedicatedPlannedScheduleBannerAboveWaktuLapor() {
        val plannedTask = sampleTask(
            id = 950L,
            scheduledDateEpochDay = todayEpochDay + 1L,
            scheduledTimeMinutes = 600 // 10:00
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                TaskDetailBottomSheet(
                    task = plannedTask,
                    onDismiss = {},
                    onToggleChecklistItem = { _, _ -> },
                    onToggleTimer = {},
                    onMarkCompleted = {},
                    onReopenTask = {},
                    onOpenMaps = {}
                )
            }
        }

        composeRule.onNodeWithTag("sheet_planned_schedule_banner")
            .assertIsDisplayed()
            .assertTextContains("Terjadwal", substring = true)
            .assertTextContains("10:00", substring = true)

        composeRule.onNodeWithTag("sheet_reported_time_text", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Waktu Lapor:", substring = true)
    }
}
