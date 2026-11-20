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
    }
}
