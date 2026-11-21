package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TicketCategory
import com.example.presentation.components.HeroActiveJobCard
import com.example.presentation.components.LabelColorTone
import com.example.presentation.components.TaskGridCard
import com.example.presentation.components.TaskListCard
import com.example.presentation.components.resolveLabelColorTone
import com.example.ui.theme.PingDuluFieldOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Tahap2WaktuLaporAndLabelsAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleTask(
        createdAtMillis: Long = System.currentTimeMillis(),
        labels: List<String> = listOf("Wi-Fi", "Jaringan", "Urgent")
    ): Task = Task(
        id = 77L,
        ticketCode = "NET-77",
        title = "Perbaikan Access Point Wi-Fi Lobby Utama",
        category = TicketCategory.NET,
        scheduledDateEpochDay = DateEpochUtil.todayEpochDay(),
        scheduledTimeMinutes = 540,
        timeSlotLabel = "09:00 - 10:30",
        priority = Priority.P1_CRITICAL,
        stage = Stage.IN_PROGRESS,
        technicalNotes = "Cek PoE injector dan uplink switch lantai 1.",
        trackedSeconds = 125L,
        isTimerRunning = true,
        timerStartedAtMillis = createdAtMillis,
        location = "Gedung Utama Lt. 1",
        slaTargetTime = "",
        checklist = listOf(
            ChecklistItem(id = 1L, label = "Cek lampu indikator AP", isDone = true),
            ChecklistItem(id = 2L, label = "Ping gateway", isDone = false)
        ),
        assignees = listOf(
            Assignee(id = 3L, name = "Fajar", role = "admin", isLead = true)
        ),
        isSynced = true,
        createdAtMillis = createdAtMillis,
        dueDateMillis = null,
        assignedToId = 3L,
        labels = labels
    )

    @Test
    fun labelToneResolution_mapsSemanticKeywordsAndUnknownLabelsSafely() {
        assertEquals(LabelColorTone.URGENT_CRIMSON, resolveLabelColorTone("Urgent"))
        assertEquals(LabelColorTone.URGENT_CRIMSON, resolveLabelColorTone("P1"))
        assertEquals(LabelColorTone.NETWORK_INDIGO, resolveLabelColorTone("Wi-Fi"))
        assertEquals(LabelColorTone.NETWORK_INDIGO, resolveLabelColorTone("Jaringan"))
        assertEquals(LabelColorTone.SERVER_VIOLET, resolveLabelColorTone("Server"))
        assertEquals(LabelColorTone.HARDWARE_AMBER, resolveLabelColorTone("Hardware"))
        assertEquals(LabelColorTone.SECURITY_ROSE, resolveLabelColorTone("Keamanan"))
        assertEquals(LabelColorTone.ONBOARDING_TEAL, resolveLabelColorTone("Onboarding"))
        assertEquals(LabelColorTone.STATUS_MINT, resolveLabelColorTone("Maintenance"))

        // Deterministic fallback for arbitrary custom server labels
        val customTone1 = resolveLabelColorTone("Ruang Rapat Direksi")
        val customTone2 = resolveLabelColorTone("Ruang Rapat Direksi")
        assertEquals(customTone1, customTone2)
    }

    @Test
    fun taskGridCard_displaysWaktuLaporAndColoredLabelBadges_withoutTimeSlotOrSla() {
        val nowMillis = System.currentTimeMillis()
        val task = sampleTask(
            createdAtMillis = nowMillis,
            labels = listOf("Wi-Fi", "Urgent", "Jaringan")
        )
        val expectedCompactTime = DateEpochUtil.formatReportedTimeCompact(nowMillis)

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                TaskGridCard(task = task, onClick = {})
            }
        }

        composeRule.onNodeWithText(expectedCompactTime, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("task_label_chip_wi_fi", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("task_label_chip_urgent", useUnmergedTree = true).assertIsDisplayed()
        // 3rd label should collapse cleanly into +1 overflow pill on compact 2-column grid card
        composeRule.onNodeWithText("+1", useUnmergedTree = true).assertIsDisplayed()
        // Old fake timeSlotLabel must not appear
        composeRule.onNodeWithText("09:00 - 10:30", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun taskListCard_displaysFullWaktuLaporAndLabels_withoutTimeSlot() {
        val nowMillis = System.currentTimeMillis()
        val task = sampleTask(
            createdAtMillis = nowMillis,
            labels = listOf("Wi-Fi", "Jaringan")
        )
        val expectedFullTime = DateEpochUtil.formatReportedTime(nowMillis)

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                TaskListCard(task = task, onClick = {})
            }
        }

        composeRule.onNodeWithText(expectedFullTime, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("task_label_chip_wi_fi", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("task_label_chip_jaringan", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("09:00 - 10:30", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun heroActiveJobCard_displaysWaktuLaporAndLabels_andNoTargetSla() {
        val nowMillis = System.currentTimeMillis()
        val task = sampleTask(
            createdAtMillis = nowMillis,
            labels = listOf("Wi-Fi", "Urgent")
        )
        val expectedText = "Dilaporkan: ${DateEpochUtil.formatReportedTime(nowMillis)}"

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                HeroActiveJobCard(
                    task = task,
                    liveSeconds = 125L,
                    onToggleTimer = {},
                    onOpenMaps = {},
                    onOpenWorksheet = {}
                )
            }
        }

        composeRule.onNodeWithTag("hero_reported_time_text").assertIsDisplayed()
        composeRule.onNodeWithText(expectedText).assertIsDisplayed()
        composeRule.onNodeWithTag("task_label_chip_wi_fi").assertIsDisplayed()
        composeRule.onNodeWithTag("task_label_chip_urgent").assertIsDisplayed()
        composeRule.onNodeWithText("Target SLA", substring = true).assertDoesNotExist()
    }

    @Test
    fun taskResolvedLabels_deduplicatesAndTrimsCustomLabels() {
        val task = sampleTask(labels = listOf("  Wi-Fi ", "Wi-Fi", "", "  Urgent  "))
        assertEquals(listOf("Wi-Fi", "Urgent"), task.resolvedLabels)

        val fallbackTask = sampleTask(labels = emptyList())
        assertTrue(fallbackTask.resolvedLabels.contains("Wi-Fi"))
        assertTrue(fallbackTask.resolvedLabels.contains("Jaringan"))
        assertTrue(fallbackTask.resolvedLabels.contains("Urgent"))
    }
}
