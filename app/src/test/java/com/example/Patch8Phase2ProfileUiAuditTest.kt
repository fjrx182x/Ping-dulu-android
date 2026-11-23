package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.domain.model.Assignee
import com.example.domain.model.FieldTechnician
import com.example.presentation.components.DelegateTechnicianDialog
import com.example.presentation.profile.ProfileScreen
import com.example.presentation.schedule.ScheduleScreen
import com.example.presentation.schedule.ScheduleUiState
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
class Patch8Phase2ProfileUiAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileScreen_displaysAvatarIdentityDepartmentAndConditionalDeleteButton() {
        var deleteClicked = false
        val state = ScheduleUiState(
            currentUserId = 10L,
            technicianUsername = "purnomo",
            technicianName = "Teknisi Purnomo",
            technicianRole = "technician",
            technicianAvatarUrl = "/api/avatars/avatar_10_1795372899.png",
            technicianDepartment = "IT Network Operations",
            technicianSubDepartment = "Fiber Optic & OSP",
            technicianIsActive = true
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                ProfileScreen(
                    uiState = state,
                    onBackClick = {},
                    onRefreshProfile = {},
                    onPickAvatarUri = {},
                    onDeleteAvatar = { deleteClicked = true },
                    onUpdateProfile = { _, _, _ -> },
                    onClearProfileError = {}
                )
            }
        }

        composeRule.onNodeWithTag("profile_large_avatar").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_upload_avatar_button").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_upload_limit_caption")
            .assertIsDisplayed()
            .assertTextContains("Maksimal 10 MB (JPEG, PNG, WebP)")
        composeRule.onNodeWithTag("profile_delete_avatar_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(deleteClicked)

        composeRule.onNodeWithTag("profile_display_name")
            .assertIsDisplayed()
            .assertTextContains("Teknisi Purnomo")
        composeRule.onNodeWithTag("profile_username")
            .assertIsDisplayed()
            .assertTextContains("@purnomo")
        composeRule.onNodeWithTag("profile_active_status_pill")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("● Aktif")
        composeRule.onNodeWithTag("profile_department_value")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("IT Network Operations")
        composeRule.onNodeWithTag("profile_sub_department_value")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Fiber Optic & OSP")
    }

    @Test
    fun profileScreen_hidesDeleteButtonWhenNoAvatarAndValidatesPasswordChange() {
        var submittedPassword: String? = null
        val state = ScheduleUiState(
            currentUserId = 1L,
            technicianUsername = "fajar",
            technicianName = "Fajar Admin",
            technicianRole = "admin",
            technicianAvatarUrl = null,
            technicianIsActive = true
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                ProfileScreen(
                    uiState = state,
                    onBackClick = {},
                    onRefreshProfile = {},
                    onPickAvatarUri = {},
                    onDeleteAvatar = {},
                    onUpdateProfile = { _, pwd, onSuccess ->
                        submittedPassword = pwd
                        onSuccess()
                    },
                    onClearProfileError = {}
                )
            }
        }

        // No avatar -> delete button must not exist
        composeRule.onNodeWithTag("profile_delete_avatar_button").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_role_badge").assertTextContains("Administrator")

        // Enter mismatched confirm password -> inline error blocks submission
        composeRule.onNodeWithTag("profile_new_password_input")
            .performScrollTo()
            .performTextInput("OpsPass2026")
        composeRule.onNodeWithTag("profile_confirm_password_input")
            .performScrollTo()
            .performTextInput("WrongConfirm")
        composeRule.onNodeWithTag("profile_save_changes_button")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("profile_error_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Konfirmasi kata sandi tidak cocok.")
        assertEquals(null, submittedPassword)
    }

    @Test
    fun scheduleTopBar_clickingProfileAvatarOpensProfileScreen() {
        var openedProfile = false
        composeRule.setContent {
            PingDuluFieldOpsTheme {
                ScheduleScreen(
                    uiState = ScheduleUiState(
                        technicianName = "Purnomo",
                        technicianAvatarUrl = "/api/avatars/avatar_10.png",
                        technicianIsActive = true
                    ),
                    onDaySelected = {},
                    onStatusFilterSelected = {},
                    onLayoutModeSelected = {},
                    onTaskClicked = {},
                    onCompleteHeroTask = {},
                    onTriggerSync = {},
                    onToggleTheme = {},
                    onMarkNotificationRead = {},
                    onMarkAllNotificationsRead = {},
                    onOpenDrawer = {},
                    onResetDrawerFilters = {},
                    onOpenProfile = { openedProfile = true }
                )
            }
        }

        composeRule.onNodeWithTag("top_profile_avatar_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(openedProfile)
    }

    @Test
    fun delegateTechnicianDialog_filtersOutInactiveUsersAndDisplaysDepartment() {
        val activeTech = FieldTechnician(
            id = 10L,
            username = "purnomo",
            displayName = "Teknisi Purnomo",
            role = "technician",
            avatarUrl = "/api/avatars/avatar_10.png",
            department = "Infrastruktur",
            subDepartment = "Fiber Optic",
            isActive = 1
        )
        val inactiveTech = FieldTechnician(
            id = 99L,
            username = "resigned",
            displayName = "Eks Teknisi Nonaktif",
            role = "technician",
            department = "NOC",
            isActive = 0
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                DelegateTechnicianDialog(
                    ticketCode = "NET-808",
                    currentAssignees = listOf(Assignee(id = 10L, name = "Teknisi Purnomo")),
                    availableTechnicians = listOf(activeTech, inactiveTech),
                    onDismiss = {},
                    onConfirmSelection = {}
                )
            }
        }

        composeRule.onNodeWithTag("delegate_tech_option_10").assertIsDisplayed()
        composeRule.onNodeWithText("Infrastruktur • Fiber Optic · Teknisi Lapangan").assertIsDisplayed()
        // Deactivated technician MUST NOT appear in delegation dialog!
        composeRule.onNodeWithTag("delegate_tech_option_99").assertDoesNotExist()
    }
}
