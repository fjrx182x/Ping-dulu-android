package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.model.DateEpochUtil
import com.example.presentation.login.LoginScreen
import com.example.presentation.schedule.ScheduleScreen
import com.example.presentation.schedule.ScheduleUiState
import com.example.ui.theme.PingDuluFieldOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WibTimezoneAndBrandingAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dateEpochUtil_locksToAsiaJakartaWib_evenWhenDeviceTimezoneIsPst() {
        val originalTz = TimeZone.getDefault()
        try {
            // Simulate Android cloud emulator running in PST (UTC-8)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

            // 2026-11-21 02:30:00 UTC = 2026-11-21 09:30 WIB (Saturday)
            // In PST (UTC-8), the exact same instant is 2026-11-20 18:30 PST (Friday — 1 day behind!)
            val instantMillis = DateEpochUtil.parseTimestampMillis("2026-11-21T02:30:00Z")

            val expectedJakartaEpochDay = DateEpochUtil.dateToEpochDay("2026-11-21")
            assertEquals(expectedJakartaEpochDay, DateEpochUtil.todayEpochDay(nowMillis = instantMillis))
            assertEquals(expectedJakartaEpochDay, DateEpochUtil.epochDayFromMillis(instantMillis))
            assertEquals(9, DateEpochUtil.currentHourOfDay(nowMillis = instantMillis))

            val reportedFull = DateEpochUtil.formatReportedTime(
                createdAtMillis = instantMillis,
                nowMillis = instantMillis
            )
            assertEquals("Hari ini, 09:30 WIB", reportedFull)
            assertFalse("Label waktu tidak boleh tampil dalam PST!", reportedFull.contains("PST"))

            val reportedCompact = DateEpochUtil.formatReportedTimeCompact(
                createdAtMillis = instantMillis,
                nowMillis = instantMillis
            )
            assertEquals("Hari ini, 09:30", reportedCompact)

            val scheduledClock = DateEpochUtil.formatScheduledClock(
                minutesFromMidnight = 600,
                referenceMillis = instantMillis
            )
            assertEquals("10:00 WIB", scheduledClock)
            assertTrue(DateEpochUtil.formatFullDate(expectedJakartaEpochDay).contains("21 Nov 2026"))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun topBarAndLoginScreen_renderOfficialPingDuluLogo_andRemoveLegacyPdText() {
        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                ScheduleScreen(
                    uiState = ScheduleUiState(
                        selectedEpochDay = DateEpochUtil.dateToEpochDay("2026-11-21"),
                        selectedFullDateLabel = DateEpochUtil.formatFullDate(DateEpochUtil.dateToEpochDay("2026-11-21")),
                        horizonSpanLabel = DateEpochUtil.formatRangeSpan(
                            DateEpochUtil.dateToEpochDay("2026-11-21"),
                            DateEpochUtil.dateToEpochDay("2026-11-27")
                        )
                    ),
                    onDaySelected = {},
                    onStatusFilterSelected = {},
                    onLayoutModeSelected = {},
                    onTaskClicked = {},
                    onToggleHeroTimer = {},
                    onOpenMaps = {},
                    onTriggerSync = {},
                    onToggleTheme = {},
                    onMarkNotificationRead = {},
                    onMarkAllNotificationsRead = {},
                    onOpenDrawer = {},
                    onResetDrawerFilters = {}
                )
            }
        }

        composeRule.onNodeWithTag("topbar_pingdulu_logo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("PingDulu Logo").assertIsDisplayed()
        composeRule.onNodeWithText("PD").assertDoesNotExist()
    }

    @Test
    fun loginScreen_rendersOfficialPingDuluLogo_withoutLegacyPdText() {
        composeRule.setContent {
            PingDuluFieldOpsTheme(darkTheme = false) {
                LoginScreen(
                    isLoggingIn = false,
                    errorMessage = null,
                    onLogin = { _, _ -> },
                    onClearError = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_pingdulu_logo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("PingDulu Logo").assertIsDisplayed()
        composeRule.onNodeWithText("PD").assertDoesNotExist()
    }
}
