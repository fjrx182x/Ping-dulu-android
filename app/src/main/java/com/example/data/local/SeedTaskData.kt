package com.example.data.local

import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DateEpochUtil
import com.example.domain.model.Priority
import com.example.domain.model.Stage
import com.example.domain.model.TicketCategory

object SeedTaskData {

    fun createInitialTasks(
        baseEpochDay: Long = DateEpochUtil.todayEpochDay(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<TaskEntity> {
        val fajarLead = Assignee(id = 3L, name = "Fajar", role = "admin", isLead = true)
        val fajarMember = Assignee(id = 3L, name = "Fajar", role = "admin", isLead = false)
        val budiLead = Assignee(id = 2L, name = "Budi Santoso", role = "technician", isLead = true)
        val budiMember = Assignee(id = 2L, name = "Budi Santoso", role = "technician", isLead = false)
        val sitiMember = Assignee(id = 4L, name = "Siti Viewer", role = "viewer", isLead = false)

        return listOf(
            TaskEntity(
                id = 59L,
                ticketCode = "NET-1",
                title = "Troubleshooting Wi-Fi Access Point Gedung B",
                category = TicketCategory.NET.code,
                scheduledDateEpochDay = baseEpochDay,
                scheduledTimeMinutes = 540,
                timeSlotLabel = "09:00 - 10:30",
                priority = Priority.P1_CRITICAL.apiValue,
                stage = Stage.IN_PROGRESS.apiValue,
                technicalNotes = "AP Unifi U6 Pro offline di koridor Lt. 2. Cek PoE budget switch & redaman dropcore.",
                trackedSeconds = 2712L,
                isTimerRunning = true,
                timerStartedAtMillis = nowMillis,
                location = "Ruang Server Lt. 2",
                slaTargetTime = "11:30 WIB",
                checklist = listOf(
                    ChecklistItem(101L, "Cek PoE Switch Port 4", true),
                    ChecklistItem(102L, "Uji Redaman Kabel Dropcore LAN", true),
                    ChecklistItem(103L, "Reboot & Konfigurasi VLAN AP", false),
                    ChecklistItem(104L, "Speedtest Wi-Fi Client > 50 Mbps", true),
                    ChecklistItem(105L, "Tanda tangan serah terima SPK", false)
                ),
                assignees = listOf(fajarLead, budiMember),
                isSynced = true,
                createdAtMillis = nowMillis,
                dueDateMillis = null,
                assignedToId = fajarLead.id,
                labels = listOf("Wi-Fi", "Jaringan", "Urgent"),
                updatedAtMillis = nowMillis
            ),
            TaskEntity(
                id = 60L,
                ticketCode = "SRV-3",
                title = "Maintenance Switch Core & Backup Storage NAS",
                category = TicketCategory.SRV.code,
                scheduledDateEpochDay = baseEpochDay,
                scheduledTimeMinutes = 780,
                timeSlotLabel = "13:00 - 14:30",
                priority = Priority.P2_HIGH.apiValue,
                stage = Stage.BACKLOG.apiValue,
                technicalNotes = "Jadwal verifikasi ZFS pool RAID-Z2 dan cek suhu fan rack B-04.",
                trackedSeconds = 0L,
                isTimerRunning = false,
                timerStartedAtMillis = null,
                location = "Data Center Rak B-04",
                slaTargetTime = "15:00 WIB",
                checklist = listOf(
                    ChecklistItem(201L, "Verifikasi ZFS Pool RAID-Z2", false),
                    ChecklistItem(202L, "Cek rotasi snapshot remote cloud", false),
                    ChecklistItem(203L, "Bersihkan filter debu switch core", false)
                ),
                assignees = listOf(fajarLead),
                isSynced = true,
                createdAtMillis = nowMillis - 10_000L,
                dueDateMillis = null,
                assignedToId = fajarLead.id,
                labels = listOf("Server", "Storage"),
                updatedAtMillis = nowMillis - 10_000L
            ),
            TaskEntity(
                id = 61L,
                ticketCode = "HRD-4",
                title = "Ganti Power Supply PC Desain Marketing",
                category = TicketCategory.HRD.code,
                scheduledDateEpochDay = baseEpochDay + 1L,
                scheduledTimeMinutes = 600,
                timeSlotLabel = "10:00 - 11:30",
                priority = Priority.P2_HIGH.apiValue,
                stage = Stage.BACKLOG.apiValue,
                technicalNotes = "PC workstation sering restart saat render berat, bawa PSU 650W 80+ Bronze.",
                trackedSeconds = 0L,
                isTimerRunning = false,
                timerStartedAtMillis = null,
                location = "Gedung A, Lantai 1",
                slaTargetTime = "12:00 WIB",
                checklist = listOf(
                    ChecklistItem(301L, "Bongkar PSU lama 500W", false),
                    ChecklistItem(302L, "Pasang PSU baru 650W Bronze", false),
                    ChecklistItem(303L, "Stress test 15 menit & rapikan kabel", false)
                ),
                assignees = listOf(budiLead),
                isSynced = true,
                createdAtMillis = nowMillis - 20_000L,
                dueDateMillis = null,
                assignedToId = budiLead.id,
                labels = listOf("Hardware"),
                updatedAtMillis = nowMillis - 20_000L
            ),
            TaskEntity(
                id = 62L,
                ticketCode = "ONB-8",
                title = "Setup & Onboarding Laptop Karyawan Baru",
                category = TicketCategory.ONB.code,
                scheduledDateEpochDay = baseEpochDay + 1L,
                scheduledTimeMinutes = 840,
                timeSlotLabel = "14:00 - 15:30",
                priority = Priority.P3_NORMAL.apiValue,
                stage = Stage.DONE.apiValue,
                technicalNotes = "Unit ThinkPad T14 Gen 4 sudah join domain, VPN WireGuard aktif.",
                trackedSeconds = 3600L,
                isTimerRunning = false,
                timerStartedAtMillis = null,
                location = "Ruang Meeting Finance",
                slaTargetTime = "16:00 WIB",
                checklist = listOf(
                    ChecklistItem(401L, "Setup Akun Email & VPN Kantor", true),
                    ChecklistItem(402L, "Serah terima laptop ThinkPad & TTD", true)
                ),
                assignees = listOf(fajarLead, sitiMember),
                isSynced = true,
                createdAtMillis = nowMillis - 30_000L,
                dueDateMillis = null,
                assignedToId = fajarLead.id,
                labels = listOf("Onboarding"),
                updatedAtMillis = nowMillis - 30_000L
            ),
            TaskEntity(
                id = 63L,
                ticketCode = "SEC-2",
                title = "Audit Port Listening Mikrotik Core Gateway",
                category = TicketCategory.SEC.code,
                scheduledDateEpochDay = baseEpochDay + 4L,
                scheduledTimeMinutes = 570,
                timeSlotLabel = "09:30 - 11:00",
                priority = Priority.P1_CRITICAL.apiValue,
                stage = Stage.BACKLOG.apiValue,
                technicalNotes = "Pastikan Telnet/FTP mati, whitelist IP manajemen NOC, dan verifikasi rule anti-bruteforce.",
                trackedSeconds = 0L,
                isTimerRunning = false,
                timerStartedAtMillis = null,
                location = "Core Gateway NOC",
                slaTargetTime = "11:30 WIB",
                checklist = listOf(
                    ChecklistItem(501L, "Disable Telnet & Port SSH standar", false),
                    ChecklistItem(502L, "Verifikasi Drop rule brute-force", false),
                    ChecklistItem(503L, "Export backup konfigurasi .rsc", false)
                ),
                assignees = listOf(fajarLead),
                isSynced = true,
                createdAtMillis = nowMillis - 40_000L,
                dueDateMillis = null,
                assignedToId = fajarLead.id,
                labels = listOf("Keamanan", "Urgent"),
                updatedAtMillis = nowMillis - 40_000L
            ),
            TaskEntity(
                id = 64L,
                ticketCode = "NET-9",
                title = "Tarik Kabel LAN Belden Cat6 Ruang Rapat",
                category = TicketCategory.NET.code,
                scheduledDateEpochDay = baseEpochDay + 6L,
                scheduledTimeMinutes = 660,
                timeSlotLabel = "11:00 - 12:30",
                priority = Priority.P2_HIGH.apiValue,
                stage = Stage.DONE.apiValue,
                technicalNotes = "Jalur conduit sudah terpasang rapi, hasil Fluke test PASS 1 Gbps.",
                trackedSeconds = 4200L,
                isTimerRunning = false,
                timerStartedAtMillis = null,
                location = "Server Room Rack 2",
                slaTargetTime = "13:00 WIB",
                checklist = listOf(
                    ChecklistItem(601L, "Crimping kabel RJ-45 Belden Cat6", true),
                    ChecklistItem(602L, "Testing kabel dengan Fluke tester", true)
                ),
                assignees = listOf(budiLead, fajarMember),
                isSynced = true,
                createdAtMillis = nowMillis - 50_000L,
                dueDateMillis = null,
                assignedToId = budiLead.id,
                labels = listOf("Jaringan"),
                updatedAtMillis = nowMillis - 50_000L
            )
        )
    }
}
