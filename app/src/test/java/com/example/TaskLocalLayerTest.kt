package com.example

import com.example.data.local.SeedTaskData
import com.example.data.local.TaskConverters
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLocalLayerTest {

    private val converters = TaskConverters()

    @Test
    fun taskConverters_roundTripAssigneesAndChecklist() {
        val assignees = listOf(
            Assignee(id = 3L, name = "Fajar", role = "admin", isLead = true),
            Assignee(id = 2L, name = "Budi Santoso", role = "technician", isLead = false)
        )
        val checklist = listOf(
            ChecklistItem(id = 101L, label = "Cek PoE Switch Port 4", isDone = true),
            ChecklistItem(id = 102L, label = "Reboot VLAN AP", isDone = false)
        )

        val assigneesJson = converters.fromAssigneeList(assignees)
        val checklistJson = converters.fromChecklistList(checklist)

        assertEquals(assignees, converters.toAssigneeList(assigneesJson))
        assertEquals(checklist, converters.toChecklistList(checklistJson))
    }

    @Test
    fun taskConverters_handlesMalformedOrBlankJsonSafely() {
        assertTrue(converters.toAssigneeList("").isEmpty())
        assertTrue(converters.toAssigneeList(null).isEmpty())
        assertTrue(converters.toAssigneeList("{invalid_json").isEmpty())
        assertTrue(converters.toChecklistList("not json").isEmpty())
    }

    @Test
    fun dateEpochUtil_formatsEpochDayAndStopwatchConsistently() {
        // 2026-11-20 is Friday (JUM)
        val epochDay = DateEpochUtil.toEpochDay(year = 2026, monthZeroBased = 10, dayOfMonth = 20)
        assertEquals("JUM", DateEpochUtil.shortDayName(epochDay))
        assertEquals(20, DateEpochUtil.dayOfMonth(epochDay))
        assertEquals("Jumat, 20 Nov 2026", DateEpochUtil.formatFullDate(epochDay))
        assertEquals("00:45:12", DateEpochUtil.formatStopwatch(2712L))
        assertEquals("00:00:00", DateEpochUtil.formatStopwatch(-50L))
    }

    @Test
    fun seedData_mapsCleanlyToDomainAndBack() {
        val baseDay = DateEpochUtil.toEpochDay(2026, 10, 20)
        val entities = SeedTaskData.createInitialTasks(baseEpochDay = baseDay, nowMillis = 1_000_000L)
        assertEquals(6, entities.size)

        val hero = entities.first().toDomain()
        assertEquals("NET-1", hero.ticketCode)
        assertEquals(Priority.P1_CRITICAL, hero.priority)
        assertEquals(Stage.IN_PROGRESS, hero.stage)
        assertEquals(3, hero.completedChecklistCount)
        assertEquals(5, hero.totalChecklistCount)
        assertEquals(60, hero.checklistProgressPercent)
        assertEquals(entities.first(), hero.toEntity())
        assertTrue(hero.isAssignedToUser(3L))
        assertTrue(hero.isAssignedToUser(2L))
        assertEquals(listOf("Wi-Fi", "Jaringan", "Urgent"), hero.resolvedLabels)
    }

    @Test
    fun taskConverters_roundTripStringLabelsSafely() {
        val labels = listOf("Wi-Fi", "Jaringan", "Urgent")
        val json = converters.fromStringList(labels)
        assertEquals(labels, converters.toStringList(json))
        assertTrue(converters.toStringList(null).isEmpty())
        assertTrue(converters.toStringList("{bad_json").isEmpty())
    }

    @Test
    fun dateEpochUtil_parsesIsoSqliteAndEpochTimestampsAndFormatsWibReportedTime() {
        val wib = java.util.TimeZone.getTimeZone("Asia/Jakarta")

        // 2026-11-20T19:15:00Z == 2026-11-21 02:15 WIB (crosses midnight in WIB!)
        val isoMicros = DateEpochUtil.parseTimestampMillisOrNull("2026-11-20T19:15:00.123456Z")!!
        val isoExact = DateEpochUtil.parseTimestampMillisOrNull("2026-11-20T19:15:00.123Z")!!
        val sqliteUtc = DateEpochUtil.parseTimestampMillisOrNull("2026-11-20 19:15:00")!!
        val offsetWib = DateEpochUtil.parseTimestampMillisOrNull("2026-11-21T02:15:00+07:00")!!
        val epochSec = DateEpochUtil.parseTimestampMillisOrNull(sqliteUtc / 1000L)!!

        // Microsecond normalization must NOT drift by minutes!
        assertEquals(isoExact, isoMicros)
        assertEquals(sqliteUtc, offsetWib)
        assertEquals(sqliteUtc, epochSec)

        // WIB epoch day MUST be 21 Nov 2026 (NOT 20 Nov UTC!)
        val expectedWibDay = DateEpochUtil.toEpochDay(2026, 10, 21)
        assertEquals(expectedWibDay, DateEpochUtil.epochDayFromMillis(sqliteUtc, wib))

        // Reported time formatted relative to same WIB day vs later WIB day
        val sameDayNow = DateEpochUtil.parseTimestampMillisOrNull("2026-11-21T09:00:00+07:00")!!
        val twoDaysLaterNow = DateEpochUtil.parseTimestampMillisOrNull("2026-11-23T09:00:00+07:00")!!
        assertEquals("Hari ini, 02:15 WIB", DateEpochUtil.formatReportedTime(sqliteUtc, sameDayNow, wib))
        assertEquals("21 Nov 2026, 02:15 WIB", DateEpochUtil.formatReportedTime(sqliteUtc, twoDaysLaterNow, wib))
        assertEquals("Hari ini, 02:15", DateEpochUtil.formatReportedTimeCompact(sqliteUtc, sameDayNow, wib))
        assertEquals("21 Nov, 02:15", DateEpochUtil.formatReportedTimeCompact(sqliteUtc, twoDaysLaterNow, wib))
    }
}
