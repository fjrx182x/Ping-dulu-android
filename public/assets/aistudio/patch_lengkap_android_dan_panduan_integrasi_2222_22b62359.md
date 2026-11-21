# 📱 Master Patch & Panduan Integrasi Android PingDulu

Dokumen ini adalah spesifikasi teknis dan berkas instruksi patch lengkap untuk **Google AI Studio** / Android Studio guna menyelaraskan aplikasi Android PingDulu dengan PingDulu Server hasil audit terbaru.

---

## 1. Masalah yang Diselesaikan oleh Patch Ini
1. **Pencegahan Flooding (Infinite Sync Loop)**:
   - Badge `● Antre Sync` sering macet karena respons server `{ success: true, task: TaskDto }` tidak ter-deserialize dengan benar, sehingga callback `markAsSynced()` tidak terpanggil dan worker menembak server ribuan kali per menit.
2. **Sinkronisasi Tanggal Agenda Lapangan**:
   - Mengalihkan acuan tanggal dari `due_date` (tenggat waktu usang) ke **`scheduled_date_epoch_day`** (Hari Pelaksanaan Agenda Lapangan), sehingga tiket insiden yang dibuat hari ini langsung muncul di agenda 7 hari teknisi.
3. **Idempotency Key (`client_sync_id`)**:
   - Menambahkan UUID unik pada setiap tiket lokal agar request pengiriman ulang otomatis diserap oleh server tanpa menciptakan duplikat.
4. **Dukungan Multi-Assignee (Tim Teknisi)**:
   - Mendukung penugasan lebih dari 1 teknisi (`assignees: List<TaskAssigneeDto>`) dengan tetap menjaga `assigned_to_id` sebagai penanggung jawab utama (Lead).

---

## 2. Struktur Model DTO Retrofit (Kotlin)

Ganti atau sesuaikan DTO di modul jaringan (`data/model/` atau `data/remote/`):

```kotlin
package com.pingdulu.app.data.remote.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

// 1. Response Wrapper untuk POST /api/tasks
data class CreateTaskResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("task") val task: TaskDto,
    @SerializedName("dedup") val dedup: Boolean? = null,
    @SerializedName("message") val message: String? = null
)

// 2. Response Wrapper untuk POST /api/sync
data class SyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("server_time") val serverTime: Long,
    @SerializedName("tasks") val tasks: List<TaskDto>,
    @SerializedName("checklist") val checklist: List<ChecklistStepDto>,
    @SerializedName("logs") val logs: List<ActivityLogDto>
)

// 3. DTO Entitas Tiket Utama
data class TaskDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("client_sync_id") val clientSyncId: String? = null,
    @SerializedName("ticket_code") val ticketCode: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("project_or_category") val projectOrCategory: String,
    
    // KUNCI: Gunakan scheduled_date_epoch_day untuk agenda lapangan
    @SerializedName("scheduled_date_epoch_day") val scheduledDateEpochDay: Long,
    @SerializedName("scheduled_time_minutes") val scheduledTimeMinutes: Int? = null,
    
    @SerializedName("priority") val priority: String, // "P1_CRITICAL", "P2_HIGH", "P3_NORMAL"
    @SerializedName("stage") val stage: String,       // "BACKLOG", "IN_PROGRESS", "WAITING_USER", "DONE"
    @SerializedName("technical_notes") val technicalNotes: String = "",
    @SerializedName("tracked_seconds") val trackedSeconds: Long = 0,
    @SerializedName("timer_started_at_millis") val timerStartedAtMillis: Long? = null,
    @SerializedName("reminder_enabled") val reminderEnabled: Int = 0,
    @SerializedName("is_flagged") val isFlagged: Int = 0,
    
    // Backward compatibility: Tetap sertakan nullable due_date
    @SerializedName("due_date") val dueDate: Long? = null,
    
    // Assignee Tunggal & Multi-Assignee
    @SerializedName("assigned_to_id") val assignedToId: Long? = null,
    @SerializedName("assignee_name") val assigneeName: String? = null,
    @SerializedName("assignee_ids") val assigneeIds: List<Long>? = null,
    @SerializedName("assignees") val assignees: List<TaskAssigneeDto> = emptyList(),
    
    @SerializedName("labels") val labels: List<LabelDto> = emptyList(),
    @SerializedName("checklist") val checklist: List<ChecklistStepDto> = emptyList(),
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

// 4. DTO Teknisi Ditugaskan
data class TaskAssigneeDto(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("role") val role: String // "admin", "technician"
)

// 5. DTO Label / Kategori Tag
data class LabelDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("color_hex") val colorHex: String
)

// 6. DTO Checklist Sub-item
data class ChecklistStepDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("work_item_id") val workItemId: Long? = null,
    @SerializedName("work_item_client_sync_id") val workItemClientSyncId: String? = null,
    @SerializedName("label") val label: String,
    @SerializedName("is_done") val isDone: Int = 0,
    @SerializedName("sort_order") val sortOrder: Int = 0,
    @SerializedName("assigned_to_id") val assignedToId: Long? = null,
    @SerializedName("assigned_to_name") val assignedToName: String? = null
)

// 7. DTO Log Aktivitas
data class ActivityLogDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("work_item_id") val workItemId: Long? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("message") val message: String,
    @SerializedName("created_at") val createdAt: Long
)
```

---

## 3. Interface Retrofit API Service

Pastikan endpoint pada `PingDuluApiService.kt`:

```kotlin
package com.pingdulu.app.data.remote

import com.pingdulu.app.data.remote.model.*
import retrofit2.Response
import retrofit2.http.*

interface PingDuluApiService {

    // 1. Ambil Semua Tiket (Dengan filter)
    @GET("/api/tasks")
    suspend fun getTasks(
        @Query("stage") stage: String? = null,
        @Query("priority") priority: String? = null,
        @Query("assigned_to_id") assignedToId: String? = null,
        @Query("q") query: String? = null
    ): Response<Map<String, Any>>

    // 2. Buat Tiket Baru (Wajib menerima wrapper CreateTaskResponse)
    @POST("/api/tasks")
    suspend fun createTask(
        @Body task: TaskDto
    ): Response<CreateTaskResponse>

    // 3. Update Status / Stage Tiket Langsung
    @POST("/api/tasks/{id}/stage")
    suspend fun updateTaskStage(
        @Path("id") taskId: Long,
        @Body body: Map<String, String> // contoh: mapOf("stage" to "DONE")
    ): Response<Map<String, Any>>

    // 4. Kontrol Stopwatch / Timer Lapangan
    @POST("/api/tasks/{id}/timer")
    suspend fun controlTimer(
        @Path("id") taskId: Long,
        @Body body: Map<String, String> // mapOf("action" to "start" | "pause" | "reset")
    ): Response<Map<String, Any>>

    // 5. Toggle Checklist Selesai
    @PATCH("/api/tasks/{taskId}/checklist/{stepId}")
    suspend fun updateChecklistStep(
        @Path("taskId") taskId: Long,
        @Path("stepId") stepId: Long,
        @Body body: Map<String, Any> // mapOf("is_done" to 1)
    ): Response<Map<String, Any>>

    // 6. Sinkronisasi Penuh Offline/Online
    @POST("/api/sync")
    suspend fun syncAll(
        @Body body: Map<String, Any> // mapOf("tasks" to list, "checklist" to list, "logs" to list)
    ): Response<SyncResponse>
}
```

---

## 4. Logika Repository & Anti-Flooding Worker

Pada `TaskRepositoryImpl.kt` atau `SyncWorker.kt`, gunakan pola ini untuk menjamin antrean lokal terhapus seketika saat sinkronisasi sukses:

```kotlin
// Helper kalkulasi Epoch Day lokal HP:
fun getLocalTodayEpochDay(): Long {
    val zoneOffset = java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds * 1000L
    return (System.currentTimeMillis() + zoneOffset) / 86400000L
}

// Saat teknisi membuat tiket baru di aplikasi:
fun createLocalTask(title: String, category: String, priority: String, notes: String): TaskEntity {
    val clientSyncId = UUID.randomUUID().toString() // Idempotency Key wajib
    val epochDay = getLocalTodayEpochDay()           // Acuan hari agenda lapangan

    return TaskEntity(
        clientSyncId = clientSyncId,
        title = title.trim(),
        projectOrCategory = category,
        scheduledDateEpochDay = epochDay,
        priority = priority,
        stage = "BACKLOG",
        technicalNotes = notes,
        syncStatus = "PENDING", // Masuk antrean sync
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

// Saat SyncWorker berjalan:
suspend fun syncPendingTask(pendingTask: TaskEntity) {
    try {
        val dto = pendingTask.toDto()
        val response = apiService.createTask(dto)
        
        if (response.isSuccessful && response.body()?.success == true) {
            val serverTask = response.body()!!.task
            
            // PENTING: Update ID dari server dan ubah status ke SYNCED
            taskDao.updateServerSynced(
                localId = pendingTask.localId,
                serverId = serverTask.id ?: pendingTask.serverId,
                ticketCode = serverTask.ticketCode ?: pendingTask.ticketCode,
                syncStatus = "SYNCED"
            )
            
            // Hapus dari tabel antrean sync agar badge "● Antre Sync" berkurang
            syncQueueDao.deleteByLocalId(pendingTask.localId)
            Log.d("SyncWorker", "Tiket [${serverTask.ticketCode}] berhasil disinkronkan dan di-dequeue.")
        } else {
            Log.w("SyncWorker", "Gagal sinkron tiket, server merespons code: ${response.code()}")
        }
    } catch (e: Exception) {
        Log.e("SyncWorker", "Koneksi terputus saat sync: ${e.message}")
        // Jangan di-loop rapat! WorkManager akan otomatis retry dengan exponential backoff
    }
}
```

---

## 5. Salin Teks Prompt Ini ke Google AI Studio

```markdown
Perbaiki dan sinkronkan aplikasi Android Ping Dulu dengan pembaruan server:

1. Perbaiki Bug Infinite Loop & Badge "● Antre Sync" Menumpuk:
   - Pastikan DTO Retrofit untuk `POST /api/tasks` menggunakan class pembungkus:
     `data class CreateTaskResponse(val success: Boolean, val task: TaskDto)`
   - Begitu response.isSuccessful dan body.success == true, segera update status tiket lokal ke `SYNCED` dan hapus record dari SyncQueueDao agar badge antrean langsung hilang.
   - Tambahkan `client_sync_id = UUID.randomUUID().toString()` pada setiap tiket baru lokal.

2. Sinkronisasi Tanggal Agenda Lapangan:
   - Jangan gunakan `due_date` sebagai penentu tanggal di tampilan utama agenda 7 hari.
   - Gunakan `scheduled_date_epoch_day = (System.currentTimeMillis() + ZoneOffset) / 86400000L`. Tiket insiden hari ini harus memiliki epoch day hari ini agar langsung muncul di agenda aktif teknisi.
   - Pastikan field `due_date` pada TaskDto bersifat nullable (`Long? = null`).

3. Multi-Assignee & Role:
   - Di tampilan detail tiket Android, tampilkan `assignees` (List<TaskAssigneeDto>) jika tersedia, dengan fallback ke `assigned_to_id` / `assignee_name`.
```
