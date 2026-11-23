package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TaskConverters
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.remote.PingDuluApiService
import com.example.data.remote.dto.AddCommentRequestDto
import com.example.data.remote.dto.ChecklistStepToggleDto
import com.example.data.remote.dto.CreateTaskRequestDto
import com.example.data.remote.dto.CreateTaskResponseDto
import com.example.data.remote.dto.LoginRequestDto
import com.example.data.remote.dto.LoginResponseDto
import com.example.data.remote.dto.StageUpdateDto
import com.example.data.remote.dto.TaskDetailResponseParser
import com.example.data.remote.dto.TaskPatchRequestDto
import com.example.data.remote.dto.TaskResponseDto
import com.example.data.remote.dto.TimerActionRequestDto
import com.example.data.repository.TaskRepositoryImpl
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.Priority
import com.example.domain.model.SnoozeOption
import com.example.domain.model.Stage
import com.example.domain.model.Task
import com.example.domain.model.TaskAttachment
import com.example.domain.model.TaskComment
import com.example.domain.model.TaskDetailWorkspaceState
import com.example.domain.model.TicketCategory
import com.example.presentation.components.TaskDetailBottomSheet
import com.example.ui.theme.PingDuluFieldOpsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Paket3DetailWorkspaceAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailParsers_handleAttachmentsAndCommentsInBareAndWrappedFormats() {
        val wrappedAttachmentsJson = """
            {
              "attachments": [
                {
                  "id": 21,
                  "task_id": 88,
                  "original_name": "rak_switch_lt3.jpg",
                  "file_url": "/uploads/tasks/88/rak_switch_lt3.jpg",
                  "mime_type": "image/jpeg",
                  "file_size": 245760,
                  "uploaded_by_name": "Fajar",
                  "created_at": 1795113557000
                },
                {
                  "id": 22,
                  "task_id": 88,
                  "filename": "log_ping.pdf",
                  "url": "https://ping.fjrx.my.id/uploads/log_ping.pdf",
                  "mime_type": null,
                  "size": 102400
                }
              ]
            }
        """.trimIndent()

        val attachments = TaskDetailResponseParser.parseAttachmentsList(wrappedAttachmentsJson, fallbackTaskId = 88L)
        assertEquals(2, attachments.size)
        assertTrue(attachments[0].isImage)
        assertEquals(
            "https://ping.fjrx.my.id/uploads/tasks/88/rak_switch_lt3.jpg",
            attachments[0].resolvedFullUrl("https://ping.fjrx.my.id/")
        )
        assertEquals("240 KB", attachments[0].formattedSizeLabel)
        assertFalse(attachments[1].isImage)
        assertEquals("application/pdf", attachments[1].mimeType)

        val bareCommentsJson = """
            [
              {
                "id": 301,
                "task_id": 88,
                "comment_text": "Redaman fiber -24dBm, ganti patch cord LC-LC.",
                "user": {"display_name": "Budi Santoso", "role": "technician"},
                "created_at": 1795113600000
              },
              {
                "id": 302,
                "task_id": 88,
                "content": "Link sudah UP 1Gbps full duplex.",
                "author_name": "Fajar",
                "created_at": 1795114200000
              }
            ]
        """.trimIndent()

        val comments = TaskDetailResponseParser.parseCommentsList(bareCommentsJson, fallbackTaskId = 88L)
        assertEquals(2, comments.size)
        assertEquals("Budi Santoso", comments[0].authorName)
        assertEquals("Redaman fiber -24dBm, ganti patch cord LC-LC.", comments[0].commentText)
        assertEquals("Fajar", comments[1].authorName)
        assertEquals("Link sudah UP 1Gbps full duplex.", comments[1].commentText)
    }

    @Test
    fun repository_uploadMultipartEnforces10MbLimitPostsCommentsAndPatchesSnoozeBlocked() = runTest {
        val capturedParts = mutableListOf<MultipartBody.Part>()
        val capturedComments = mutableListOf<Pair<Long, AddCommentRequestDto>>()
        val capturedPatches = mutableListOf<Pair<Long, TaskPatchRequestDto>>()
        val capturedTimers = mutableListOf<Pair<Long, TimerActionRequestDto>>()

        val fakeApi = Paket3FakeApiService(
            onUploadAttachment = { _, part ->
                capturedParts += part
                Response.success(
                    """{"attachment":{"id":901,"task_id":88,"file_name":"panel.jpg","file_url":"/uploads/panel.jpg","mime_type":"image/jpeg","file_size":4}}"""
                        .toResponseBody()
                )
            },
            onPostComment = { taskId, dto ->
                capturedComments += (taskId to dto)
                Response.success(
                    """{"comment":{"id":501,"task_id":$taskId,"author_name":"Fajar","comment_text":"${dto.commentText}","created_at":1795114500000}}"""
                        .toResponseBody()
                )
            },
            onPatchTask = { taskId, dto ->
                capturedPatches += (taskId to dto)
                Response.success("{\"success\":true}".toResponseBody())
            },
            onTimer = { taskId, dto ->
                capturedTimers += (taskId to dto)
                Response.success(Unit)
            }
        )

        val initialEntity = TaskEntity(
            id = 88L,
            ticketCode = "NET-088",
            title = "Ganti SFP Core Switch",
            category = TicketCategory.NET.code,
            scheduledDateEpochDay = 20780L,
            scheduledTimeMinutes = 540,
            timeSlotLabel = "09:00",
            priority = Priority.P1_CRITICAL.apiValue,
            stage = Stage.IN_PROGRESS.apiValue,
            technicalNotes = "Cek redaman",
            trackedSeconds = 420L,
            isTimerRunning = true,
            timerStartedAtMillis = 1795113000000L,
            location = "Data Center",
            slaTargetTime = "12:00",
            checklist = emptyList(),
            assignees = emptyList(),
            isSynced = true,
            updatedAtMillis = 1795113000000L
        )
        val fakeDao = Paket3FakeDao(listOf(initialEntity))
        val repo = TaskRepositoryImpl(
            taskDao = fakeDao,
            apiServiceProvider = { fakeApi },
            appPreferences = null
        )

        // 1. Guard: >10MB upload must fail locally without hitting network
        val oversized = ByteArray((TaskDetailWorkspaceState.MAX_ATTACHMENT_BYTES + 1L).toInt())
        val oversizedRes = repo.uploadTaskAttachment(88L, "huge.jpg", "image/jpeg", oversized)
        assertTrue(oversizedRes.isFailure)
        assertEquals(0, capturedParts.size)

        // 2. Guard: unsynced local negative task ID rejected gracefully
        val unsyncedRes = repo.uploadTaskAttachment(-500L, "panel.jpg", "image/jpeg", byteArrayOf(1, 2, 3, 4))
        assertTrue(unsyncedRes.isFailure)
        assertEquals(0, capturedParts.size)

        // 3. Valid Multipart Upload uses field name "file"
        val validUpload = repo.uploadTaskAttachment(88L, "panel.jpg", "image/jpeg", byteArrayOf(1, 2, 3, 4))
        assertTrue(validUpload.isSuccess)
        assertEquals(1, capturedParts.size)
        val contentDisposition = capturedParts.first().headers?.get("Content-Disposition").orEmpty()
        assertTrue("Multipart field must be named 'file'", contentDisposition.contains("name=\"file\""))
        assertTrue(contentDisposition.contains("filename=\"panel.jpg\""))

        // 4. Post comment sends {"comment_text": "..."}
        val commentRes = repo.postTaskComment(88L, "Sudah ganti modul SFP port 24")
        assertTrue(commentRes.isSuccess)
        assertEquals(1, capturedComments.size)
        assertEquals("Sudah ganti modul SFP port 24", capturedComments.first().second.commentText)

        // 5. Snooze updates Room & calls PATCH /api/tasks/88
        val snoozeTarget = 1795200000000L
        val snoozeRes = repo.snoozeTask(88L, snoozeTarget, newEpochDay = 20781L, newTimeMinutes = 600)
        assertTrue(snoozeRes.isSuccess)
        val afterSnooze = fakeDao.getTaskByIdOnce(88L)!!
        assertEquals(snoozeTarget, afterSnooze.snoozedUntilMillis)
        assertEquals(20781L, afterSnooze.scheduledDateEpochDay)
        assertEquals(600, afterSnooze.scheduledTimeMinutes)
        assertTrue(afterSnooze.isSynced)
        assertEquals(snoozeTarget, capturedPatches.last().second.snoozedUntilMillis)

        // 6. Blocking a running task auto-pauses timer, persists blocked_reason, and sends PATCH is_blocked=1
        val blockRes = repo.setTaskBlocked(88L, isBlocked = true, reason = "Menunggu Vendor Telkom")
        assertTrue(blockRes.isSuccess)
        val afterBlock = fakeDao.getTaskByIdOnce(88L)!!
        assertTrue(afterBlock.isBlocked)
        assertEquals("Menunggu Vendor Telkom", afterBlock.blockedReason)
        assertFalse("Running timer must pause when task is blocked", afterBlock.isTimerRunning)
        assertEquals(TimerActionRequestDto.ACTION_PAUSE, capturedTimers.last().second.action)
        assertEquals(1, capturedPatches.last().second.isBlocked)
        assertEquals("Menunggu Vendor Telkom", capturedPatches.last().second.blockedReason)
        assertEquals(420L, capturedPatches.last().second.trackedSeconds)
    }

    @Test
    fun roomMigration_v5ToV6_preservesTasksAndPersistsBlockedAndSnoozeFields() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "pingdulu_v5_to_v6_audit_${System.nanoTime()}.db"
        context.deleteDatabase(dbName)

        // 1. Seed a genuine Room v5 table (before is_blocked, blocked_reason, snoozed_until_millis existed)
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `tasks` (
                                `id` INTEGER NOT NULL,
                                `ticket_code` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `category` TEXT NOT NULL,
                                `scheduled_date_epoch_day` INTEGER NOT NULL,
                                `scheduled_time_minutes` INTEGER,
                                `time_slot_label` TEXT NOT NULL,
                                `priority` TEXT NOT NULL,
                                `stage` TEXT NOT NULL,
                                `technical_notes` TEXT NOT NULL,
                                `tracked_seconds` INTEGER NOT NULL,
                                `is_timer_running` INTEGER NOT NULL,
                                `timer_started_at_millis` INTEGER,
                                `location` TEXT NOT NULL,
                                `sla_target_time` TEXT NOT NULL,
                                `checklist_json` TEXT NOT NULL,
                                `assignees_json` TEXT NOT NULL,
                                `is_synced` INTEGER NOT NULL,
                                `created_at` INTEGER NOT NULL DEFAULT 0,
                                `due_date` INTEGER,
                                `assigned_to_id` INTEGER,
                                `labels_json` TEXT NOT NULL DEFAULT '[]',
                                `updated_at_millis` INTEGER NOT NULL,
                                `client_sync_id` TEXT,
                                `is_recurring` INTEGER NOT NULL DEFAULT 0,
                                `recurrence_interval` TEXT,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_scheduled_date_epoch_day` ON `tasks` (`scheduled_date_epoch_day`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_stage` ON `tasks` (`stage`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_is_synced` ON `tasks` (`is_synced`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_client_sync_id` ON `tasks` (`client_sync_id`)")
                        db.execSQL(
                            """
                            INSERT INTO `tasks` VALUES (
                                55, 'SRV-055', 'Tiket V5 Eksisting', 'SRV', 20780, 600, '10:00',
                                'P2_HIGH', 'BACKLOG', 'Catatan V5', 0, 0, NULL, 'Ruang Server', '',
                                '[]', '[]', 1, 1795100000000, NULL, 3, '["Server"]', 1795100000000,
                                NULL, 1, 'WEEKLY'
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        openHelper.writableDatabase.close()
        openHelper.close()

        // 2. Open with Room v7 + MIGRATION_5_6 + MIGRATION_6_7
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = db.taskDao()
            val migrated = dao.getTaskByIdOnce(55L)
            assertNotNull(migrated)
            assertEquals("Tiket V5 Eksisting", migrated?.title)
            assertTrue(migrated!!.isRecurring)
            assertEquals("WEEKLY", migrated.recurrenceInterval)
            assertFalse(migrated.isBlocked)
            assertNull(migrated.blockedReason)
            assertNull(migrated.snoozedUntilMillis)

            dao.upsertTask(
                migrated.copy(
                    isBlocked = true,
                    blockedReason = "Menunggu Sparepart PSU",
                    snoozedUntilMillis = 1795200000000L
                )
            )
            val updated = dao.getTaskByIdOnce(55L)!!
            assertTrue(updated.isBlocked)
            assertEquals("Menunggu Sparepart PSU", updated.blockedReason)
            assertEquals(1795200000000L, updated.snoozedUntilMillis)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun taskDetailSheetUi_rendersAttachmentsCommentsAndTriggersSnoozeBlockedAndPostComment() {
        var postedCommentText: String? = null
        var chosenSnooze: SnoozeOption? = null
        var blockedCallback: Pair<Boolean, String?>? = null

        val task = Task(
            id = 88L,
            ticketCode = "NET-088",
            title = "Perbaikan Fiber Uplink Gedung Utama",
            category = TicketCategory.NET,
            scheduledDateEpochDay = 20780L,
            scheduledTimeMinutes = 540,
            timeSlotLabel = "09:00",
            priority = Priority.P1_CRITICAL,
            stage = Stage.IN_PROGRESS,
            technicalNotes = "Gunakan OTDR untuk ukur redaman",
            trackedSeconds = 300L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = "Ruang MDF Lt. 1",
            slaTargetTime = "12:00",
            checklist = listOf(ChecklistItem(1L, "Ukur redaman fiber", false)),
            assignees = listOf(Assignee(3L, "Fajar", "admin", isLead = true)),
            isSynced = true,
            isBlocked = false
        )

        val workspace = TaskDetailWorkspaceState(
            taskId = 88L,
            attachments = listOf(
                TaskAttachment(
                    id = 10L,
                    taskId = 88L,
                    fileName = "foto_otdr.jpg",
                    fileUrl = "/uploads/foto_otdr.jpg",
                    mimeType = "image/jpeg",
                    fileSizeBytes = 120_000L
                )
            ),
            comments = listOf(
                TaskComment(
                    id = 20L,
                    taskId = 88L,
                    authorName = "Budi",
                    commentText = "Redaman awal -28dBm di core 4."
                )
            ),
            activities = listOf(
                com.example.domain.model.TaskActivityItem(
                    id = 301L,
                    taskId = 88L,
                    actorName = "Fajar",
                    action = "Mengubah status",
                    detail = "Antrean → Proses",
                    category = com.example.domain.model.ActivityCategory.STAGE_CHANGED,
                    createdAtMillis = 1795256990000L
                )
            ),
            isActivitiesFromServer = true
        )

        composeRule.setContent {
            PingDuluFieldOpsTheme {
                TaskDetailBottomSheet(
                    task = task,
                    onDismiss = {},
                    onToggleChecklistItem = { _, _ -> },
                    onToggleTimer = {},
                    onMarkCompleted = {},
                    onReopenTask = {},
                    workspaceState = workspace,
                    onPostComment = { _, text -> postedCommentText = text },
                    onSnoozeTask = { _, opt -> chosenSnooze = opt },
                    onSetTaskBlocked = { _, blocked, reason -> blockedCallback = blocked to reason }
                )
            }
        }

        // Verify attachment & existing comment appear
        composeRule.onNodeWithTag("sheet_attachments_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("attachment_card_10").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_comments_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Redaman awal -28dBm di core 4.").assertIsDisplayed()

        // Post new technical comment
        composeRule.onNodeWithTag("sheet_comment_input")
            .performScrollTo()
            .performTextInput("Konektor sudah dibersihkan, sinyal normal.")
        composeRule.onNodeWithTag("sheet_send_comment_button")
            .performScrollTo()
            .performClick()
        assertEquals("Konektor sudah dibersihkan, sinyal normal.", postedCommentText)

        // Switch to Opsi A "Riwayat" segmented tab and verify server activity timeline renders
        composeRule.onNodeWithTag("sheet_tab_activity")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("sheet_activity_timeline").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_item_301").assertIsDisplayed()
        composeRule.onNodeWithText("Sinkron Server").assertIsDisplayed()

        // Open Snooze dialog & choose +1 Jam
        composeRule.onNodeWithTag("sheet_snooze_button")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("snooze_option_1h").performClick()
        assertEquals(SnoozeOption.ONE_HOUR, chosenSnooze)

        // Open Blocked dialog & confirm reason
        composeRule.onNodeWithTag("sheet_blocked_button")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("blocked_reason_input")
            .performTextInput("Menunggu Sparepart SFP")
        composeRule.onNodeWithTag("blocked_confirm_button").performClick()
        assertEquals(true to "Menunggu Sparepart SFP", blockedCallback)
    }

    @Test
    fun fetchTaskAttachments_whenGetAttachmentsReturns404_usesTaskDetailAndRoomCacheWithoutError() = runTest {
        val existingTask = TaskEntity(
            id = 88L,
            ticketCode = "NET-36931",
            title = "Cek OLT & Foto Bukti",
            category = TicketCategory.NET.code,
            scheduledDateEpochDay = 20780L,
            scheduledTimeMinutes = 540,
            timeSlotLabel = "09:00",
            priority = Priority.P2_HIGH.apiValue,
            stage = Stage.BACKLOG.apiValue,
            technicalNotes = "",
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = "POP Utama",
            slaTargetTime = "",
            checklist = emptyList(),
            assignees = listOf(Assignee(3L, "Fajar", "admin", isLead = true)),
            isSynced = true,
            updatedAtMillis = 1795200000000L
        )
        val dao = Paket3FakeDao(listOf(existingTask))
        val fakeApi = Paket3FakeApiService(
            onGetTaskDetail = { _ -> Response.error(404, """{"error":"Endpoint API tidak ditemukan"}""".toResponseBody()) },
            onGetAttachments = { _ -> Response.error(404, """{"error":"Endpoint API tidak ditemukan"}""".toResponseBody()) },
            onUploadAttachment = { _, _ ->
                Response.success(
                    """
                    {
                      "attachment": {
                        "id": 901,
                        "task_id": 88,
                        "original_name": "bukti_1795256971948.jpg",
                        "file_url": "/uploads/bukti_1795256971948.jpg",
                        "mime_type": "image/jpeg",
                        "file_size": 4096
                      }
                    }
                    """.trimIndent().toResponseBody()
                )
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { fakeApi })

        // 1. Before any upload: HTTP 404 on GET attachments must NOT fail with a red error
        val initialFetch = repo.fetchTaskAttachments(88L)
        assertTrue("404 on optional GET attachments must not surface as failure", initialFetch.isSuccess)
        assertTrue(initialFetch.getOrThrow().isEmpty())

        // 2. Upload photo -> persisted into Room TaskEntity.attachments
        val uploaded = repo.uploadTaskAttachment(
            taskId = 88L,
            fileName = "bukti_1795256971948.jpg",
            mimeType = "image/jpeg",
            bytes = ByteArray(1024) { 7 }
        )
        assertTrue(uploaded.isSuccess)

        // 3. Re-fetch when sheet reopens: even with HTTP 404 on GET attachments, cached photo returns cleanly!
        val afterUploadFetch = repo.fetchTaskAttachments(88L)
        assertTrue(afterUploadFetch.isSuccess)
        val list = afterUploadFetch.getOrThrow()
        assertEquals(1, list.size)
        assertEquals("bukti_1795256971948.jpg", list.first().fileName)
        assertEquals("/uploads/bukti_1795256971948.jpg", list.first().fileUrl)
    }

    @Test
    fun fetchAndPostComments_resolveAuthorNameFromUserIdDirectoryInsteadOfGenericTeknisi() = runTest {
        val existingTask = TaskEntity(
            id = 88L,
            ticketCode = "NET-36931",
            title = "Audit Diskusi Teknisi",
            category = TicketCategory.NET.code,
            scheduledDateEpochDay = 20780L,
            scheduledTimeMinutes = 540,
            timeSlotLabel = "09:00",
            priority = Priority.P2_HIGH.apiValue,
            stage = Stage.BACKLOG.apiValue,
            technicalNotes = "",
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = "Site A",
            slaTargetTime = "",
            checklist = emptyList(),
            assignees = listOf(
                Assignee(3L, "Fajar", "admin", isLead = true),
                Assignee(7L, "teknisi purnomo", "technician", isLead = false)
            ),
            isSynced = true,
            updatedAtMillis = 1795200000000L
        )
        val dao = Paket3FakeDao(listOf(existingTask))
        val fakeApi = Paket3FakeApiService(
            onGetUsers = {
                Response.success(
                    """
                    [
                      {"id": 3, "username": "fajar", "display_name": "Fajar", "role": "admin"},
                      {"id": 7, "username": "purnomo", "display_name": "teknisi purnomo", "role": "technician"},
                      {"id": 12, "username": "rizky_noc", "display_name": "Rizky NOC", "role": "technician"}
                    ]
                    """.trimIndent().toResponseBody()
                )
            },
            onGetComments = { _ ->
                Response.success(
                    """
                    [
                      {"id": 501, "task_id": 88, "user_id": 3, "comment_text": "gimana", "created_at": 1795256940000},
                      {"id": 502, "task_id": 88, "created_by": 12, "comment_text": "link OLT sudah up", "created_at": 1795256990000},
                      {"id": 504, "task_id": 88, "username": "purnomo", "comment_text": "otw lokasi", "created_at": 1795257020000}
                    ]
                    """.trimIndent().toResponseBody()
                )
            },
            onPostComment = { _, body ->
                Response.success(
                    """{"comment": {"id": 503, "task_id": 88, "user_id": 3, "comment_text": "${body.commentText}"}}""".toResponseBody()
                )
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { fakeApi })

        val fetched = repo.fetchTaskComments(88L).getOrThrow()
        assertEquals(3, fetched.size)
        assertEquals("Fajar", fetched[0].authorName)
        assertEquals("Rizky NOC", fetched[1].authorName)
        assertEquals("teknisi purnomo", fetched[2].authorName)

        val posted = repo.postTaskComment(88L, "sudah normal").getOrThrow()
        assertEquals("Fajar", posted.authorName)
        assertEquals("sudah normal", posted.commentText)
    }

    @Test
    fun fetchTaskActivities_syncsServerActivityLogResolvesActorsAndCachesWorkingRoute() = runTest {
        var detailProbeCalls = 0
        var activitiesCalls = 0
        val existingTask = TaskEntity(
            id = 88L,
            ticketCode = "NET-36931",
            title = "Cek Server Activity Feed",
            category = TicketCategory.NET.code,
            scheduledDateEpochDay = 20780L,
            scheduledTimeMinutes = 540,
            timeSlotLabel = "09:00",
            priority = Priority.P2_HIGH.apiValue,
            stage = Stage.IN_PROGRESS.apiValue,
            technicalNotes = "",
            trackedSeconds = 0L,
            isTimerRunning = false,
            timerStartedAtMillis = null,
            location = "POP Utama",
            slaTargetTime = "",
            checklist = emptyList(),
            assignees = listOf(Assignee(3L, "Fajar", "admin", isLead = true)),
            isSynced = true,
            updatedAtMillis = 1795200000000L
        )
        val dao = Paket3FakeDao(listOf(existingTask))
        val fakeApi = Paket3FakeApiService(
            onGetUsers = {
                Response.success(
                    """[{"id": 3, "username": "fajar", "display_name": "Fajar", "role": "admin"}]""".toResponseBody()
                )
            },
            onGetTaskDetail = { _ ->
                detailProbeCalls++
                Response.error(404, "{}".toResponseBody())
            },
            onGetActivities = { _ ->
                activitiesCalls++
                Response.success(
                    """
                    {
                      "activities": [
                        {"id": 1, "task_id": 88, "user_id": 3, "action": "task_created", "created_at": 1795256000000},
                        {"id": 2, "task_id": 88, "user_id": 3, "action": "stage_changed", "old_value": "BACKLOG", "new_value": "IN_PROGRESS", "created_at": 1795256900000}
                      ]
                    }
                    """.trimIndent().toResponseBody()
                )
            }
        )
        val repo = TaskRepositoryImpl(taskDao = dao, apiServiceProvider = { fakeApi })

        // First call probes detail (404), then succeeds on /activities and caches that route
        val firstFeed = repo.fetchTaskActivities(88L).getOrThrow()
        assertTrue(firstFeed.isFromServer)
        assertEquals(2, firstFeed.items.size)
        // Newest-first order
        assertEquals("Fajar", firstFeed.items[0].actorName)
        assertEquals("Mengubah status — Antrean → Proses", firstFeed.items[0].summaryText)
        assertEquals("Membuat tiket", firstFeed.items[1].summaryText)

        // Second call hits cached /activities directly without re-probing 404 detail endpoint
        val secondFeed = repo.fetchTaskActivities(88L).getOrThrow()
        assertTrue(secondFeed.isFromServer)
        assertEquals(1, detailProbeCalls)
        assertEquals(2, activitiesCalls)
    }
}

private class Paket3FakeApiService(
    private val onGetUsers: () -> Response<ResponseBody> = {
        Response.success("[]".toResponseBody())
    },
    private val onGetTaskDetail: (Long) -> Response<ResponseBody> = { _ ->
        Response.error(404, "{}".toResponseBody())
    },
    private val onGetAttachments: (Long) -> Response<ResponseBody> = { _ ->
        Response.success("[]".toResponseBody())
    },
    private val onUploadAttachment: (Long, MultipartBody.Part) -> Response<ResponseBody> = { _, _ ->
        Response.success("{}".toResponseBody())
    },
    private val onGetComments: (Long) -> Response<ResponseBody> = { _ ->
        Response.success("[]".toResponseBody())
    },
    private val onPostComment: (Long, AddCommentRequestDto) -> Response<ResponseBody> = { _, _ ->
        Response.success("{}".toResponseBody())
    },
    private val onGetActivities: (Long) -> Response<ResponseBody> = { _ ->
        Response.error(404, "{}".toResponseBody())
    },
    private val onGetActivitySingular: (Long) -> Response<ResponseBody> = { _ ->
        Response.error(404, "{}".toResponseBody())
    },
    private val onGetLogs: (Long) -> Response<ResponseBody> = { _ ->
        Response.error(404, "{}".toResponseBody())
    },
    private val onPatchTask: (Long, TaskPatchRequestDto) -> Response<ResponseBody> = { _, _ ->
        Response.success("{}".toResponseBody())
    },
    private val onTimer: (Long, TimerActionRequestDto) -> Response<Unit> = { _, _ ->
        Response.success(Unit)
    }
) : PingDuluApiService {
    override suspend fun login(body: LoginRequestDto): Response<LoginResponseDto> =
        Response.success(LoginResponseDto(token = "token"))
    override suspend fun getUsersRaw(): Response<ResponseBody> = onGetUsers()
    override suspend fun getLabelsRaw(): Response<ResponseBody> = Response.success("[]".toResponseBody())
    override suspend fun getTasks(): Response<TaskResponseDto> = Response.success(TaskResponseDto())
    override suspend fun getTaskDetailRaw(taskId: Long): Response<ResponseBody> = onGetTaskDetail(taskId)
    override suspend fun createTask(body: CreateTaskRequestDto): Response<CreateTaskResponseDto> =
        Response.success(CreateTaskResponseDto(id = 1L))
    override suspend fun patchTask(id: Long, body: TaskPatchRequestDto): Response<ResponseBody> =
        onPatchTask(id, body)
    override suspend fun updateStage(id: Long, body: StageUpdateDto): Response<Unit> = Response.success(Unit)
    override suspend fun updateTimer(id: Long, body: TimerActionRequestDto): Response<Unit> = onTimer(id, body)
    override suspend fun toggleChecklistStep(id: Long, stepId: Long, body: ChecklistStepToggleDto): Response<Unit> =
        Response.success(Unit)
    override suspend fun getAttachmentsRaw(taskId: Long): Response<ResponseBody> = onGetAttachments(taskId)
    override suspend fun uploadAttachment(taskId: Long, file: MultipartBody.Part): Response<ResponseBody> =
        onUploadAttachment(taskId, file)
    override suspend fun getCommentsRaw(taskId: Long): Response<ResponseBody> = onGetComments(taskId)
    override suspend fun postComment(taskId: Long, body: AddCommentRequestDto): Response<ResponseBody> =
        onPostComment(taskId, body)
    override suspend fun getActivitiesRaw(taskId: Long): Response<ResponseBody> = onGetActivities(taskId)
    override suspend fun getActivitySingularRaw(taskId: Long): Response<ResponseBody> = onGetActivitySingular(taskId)
    override suspend fun getTaskLogsRaw(taskId: Long): Response<ResponseBody> = onGetLogs(taskId)
    override suspend fun workTask(taskId: Long): Response<com.example.data.remote.dto.WorkTaskResponseDto> =
        Response.success(com.example.data.remote.dto.WorkTaskResponseDto(success = true, stage = "IN_PROGRESS"))
    override suspend fun claimTask(taskId: Long): Response<com.example.data.remote.dto.ClaimTaskResponseDto> =
        Response.success(com.example.data.remote.dto.ClaimTaskResponseDto(success = true))
    override suspend fun deleteTask(taskId: Long): Response<ResponseBody> =
        Response.success("{}".toResponseBody())
    override suspend fun getHolidays(year: Int): Response<com.example.data.remote.dto.HolidayResponseDto> =
        Response.success(com.example.data.remote.dto.HolidayResponseDto(year = year))
    override suspend fun getCalendar(year: Int, month: Int): Response<com.example.data.remote.dto.CalendarAgendaResponseDto> =
        Response.success(com.example.data.remote.dto.CalendarAgendaResponseDto(year = year, month = month))
    override suspend fun addChecklistStep(taskId: Long, body: com.example.data.remote.dto.AddChecklistStepRequestDto): Response<ResponseBody> =
        Response.success("{}".toResponseBody())
    override suspend fun postSpeedTestRecord(body: com.example.data.remote.dto.SpeedTestRecordRequestDto): Response<ResponseBody> =
        Response.success("{}".toResponseBody())
    override suspend fun getSpeedTestRecordsRaw(): Response<ResponseBody> =
        Response.success("[]".toResponseBody())
    override suspend fun getMyProfile(): Response<com.example.data.remote.dto.AuthMeResponseDto> =
        Response.success(com.example.data.remote.dto.AuthMeResponseDto(success = true))
    override suspend fun uploadUserAvatar(userId: Long, file: MultipartBody.Part): Response<com.example.data.remote.dto.AvatarUploadResponseDto> =
        Response.success(com.example.data.remote.dto.AvatarUploadResponseDto(success = true))
    override suspend fun deleteUserAvatar(userId: Long): Response<com.example.data.remote.dto.UserMutationResponseDto> =
        Response.success(com.example.data.remote.dto.UserMutationResponseDto(success = true))
    override suspend fun updateUserProfile(userId: Long, request: com.example.data.remote.dto.UpdateUserRequestDto): Response<com.example.data.remote.dto.UserMutationResponseDto> =
        Response.success(com.example.data.remote.dto.UserMutationResponseDto(success = true))
}

private class Paket3FakeDao(initial: List<TaskEntity>) : TaskDao {
    private val state = MutableStateFlow(initial.associateBy { it.id })
    private val converters = TaskConverters()

    override fun getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>> =
        state.map { map -> map.values.filter { it.scheduledDateEpochDay == epochDay } }
    override fun getTasksInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TaskEntity>> =
        state.map { map -> map.values.filter { it.scheduledDateEpochDay in startEpochDay..endEpochDay } }
    override fun getAllTasks(): Flow<List<TaskEntity>> = state.map { it.values.toList() }
    override fun getActiveHeroTask(): Flow<TaskEntity?> =
        state.map { map -> map.values.firstOrNull { it.stage == Stage.IN_PROGRESS.apiValue } }
    override fun getTaskById(taskId: Long): Flow<TaskEntity?> = state.map { it[taskId] }
    override suspend fun getTaskByIdOnce(taskId: Long): TaskEntity? = state.value[taskId]
    override suspend fun getUnsyncedTasks(): List<TaskEntity> = state.value.values.filter { !it.isSynced }
    override fun getUnsyncedCount(): Flow<Int> = state.map { map -> map.values.count { !it.isSynced } }
    override suspend fun countTasks(): Int = state.value.size
    override suspend fun upsertTasks(tasks: List<TaskEntity>) {
        state.value = state.value + tasks.associateBy { it.id }
    }
    override suspend fun upsertTask(task: TaskEntity) {
        state.value = state.value + (task.id to task)
    }
    override suspend fun updateChecklist(taskId: Long, checklistJson: String, isSynced: Boolean, updatedAtMillis: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(checklist = converters.toChecklistList(checklistJson), isSynced = isSynced, updatedAtMillis = updatedAtMillis))
    }
    override suspend fun updateTaskTimer(taskId: Long, seconds: Long, isRunning: Boolean, timerStartedAtMillis: Long?, isSynced: Boolean, updatedAtMillis: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(trackedSeconds = seconds, isTimerRunning = isRunning, timerStartedAtMillis = timerStartedAtMillis, isSynced = isSynced, updatedAtMillis = updatedAtMillis))
    }
    override suspend fun pauseAllOtherTimers(excludeTaskId: Long, updatedAtMillis: Long) = Unit
    override suspend fun updateTaskStage(taskId: Long, stage: String, isRunning: Boolean, timerStartedAtMillis: Long?, isSynced: Boolean, updatedAtMillis: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(stage = stage, isTimerRunning = isRunning, timerStartedAtMillis = timerStartedAtMillis, isSynced = isSynced, updatedAtMillis = updatedAtMillis))
    }
    override suspend fun markTaskSynced(taskId: Long) {
        val existing = state.value[taskId] ?: return
        state.value = state.value + (taskId to existing.copy(isSynced = true))
    }
    override suspend fun deleteTaskById(taskId: Long) {
        state.value = state.value - taskId
    }
    override suspend fun getTaskByClientSyncId(clientSyncId: String): TaskEntity? =
        state.value.values.firstOrNull { it.clientSyncId == clientSyncId }
    override suspend fun deletePendingByClientSyncId(clientSyncId: String) {
        state.value = state.value.filterValues { !(it.id < 0L && it.clientSyncId == clientSyncId) }
    }
    override suspend fun deleteStaleSyncedTasks(activeIds: List<Long>) {
        state.value = state.value.filterValues { !it.isSynced || it.id in activeIds }
    }
    override suspend fun deleteAllSyncedTasks() {
        state.value = state.value.filterValues { !it.isSynced }
    }
}
