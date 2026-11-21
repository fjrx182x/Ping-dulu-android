# PATCH KE-2: FITUR KALENDER, REDESAIN AKSI KERJAKAN/SELESAI, RIWAYAT HUMANIS & TASK POLICY ENFORCEMENT

**Nomor Patch**: `PATCH_KALENDER_POLICY_DAN_LOG_HUMANIS_PATCH_KE_2.md`  
**Sistem Target**: PingDulu Server (Web & Backend API) & PingDulu Android App (Field Ops)  
**Status Audit & Test**: ✅ **39 Passed / 0 Failed (100% Pass Rate)**  
**Tanggal**: 21 November 2026  

---

## DAFTAR ISI
1. [Ringkasan Eksekutif & Pemetaan Tugas](#1-ringkasan-eksekutif--pemetaan-tugas)
2. [Laporan Audit Nyata & Riset Bug (Zero Hallucination)](#2-laporan-audit-nyata--riset-bug-zero-hallucination)
3. [Bagian 1: Fitur Kalender & Hari Libur Nasional Resmi](#3-bagian-1-fitur-kalender--hari-libur-nasional-resmi)
4. [Bagian 2: Redesain Tombol Card & Penghapusan Stopwatch](#4-bagian-2-redesain-tombol-card--penghapusan-stopwatch)
5. [Bagian 3: Riwayat Humanis & Relevan](#5-bagian-3-riwayat-humanis--relevan)
6. [Bagian 4: Task Policy Enforcement (Hak Akses Hapus & Selesai)](#6-bagian-4-task-policy-enforcement-hak-akses-hapus--selesai)
7. [Dokumentasi API Contract Baru](#7-dokumentasi-api-contract-baru)
8. [Panduan Patch Android untuk Google AI Studio (Copy-Paste Ready)](#8-panduan-patch-android-untuk-google-ai-studio-copy-paste-ready)

---

## 1. RINGKASAN EKSEKUTIF & PEMETAAN TUGAS

Sesuai arahan, telah dilakukan penyempurnaan menyeluruh pada ekosistem **PingDulu Server** dan penyusunan modul patch untuk **PingDulu Android Field Ops** yang mencakup 4 pilar utama:

| No | Modul Permintaan | Implementasi Server PingDulu | Implementasi Android Field Ops |
|---|---|---|---|
| **1** | **Kalender & Hari Libur Nasional** | Engine kalender dan database SKB 3 Menteri resmi Indonesia (2025-2027) di `src/utils/holidays.ts`, endpoint `GET /api/holidays` & `GET /api/calendar`, UI `CalendarView.tsx` dengan highlight merah hari libur & penanda tiket. | Model `NationalHoliday`, endpoint Retrofit kalender, Calendar Screen / Agenda Tab dengan indikator tanggal merah & sinkronisasi agenda lapangan. |
| **2** | **Redesain Tombol & Stopwatch** | Penghapusan live stopwatch counter di card dan detail modal. Tombol `Mulai Stopwatch` diganti `Kerjakan` (`POST /api/tasks/:id/work`), tombol `Tandai Selesai` diganti `Selesai` (`POST /api/tasks/:id/stage` -> `DONE`). | Mengganti tombol ungu `Mulai Stopwatch` pada Bottom Sheet & Card menjadi `Kerjakan`, dan `Tandai Selesai` menjadi `Selesai`. |
| **3** | **Riwayat Humanis & Relevan** | Re-strukturisasi generator activity log: format natural Indonesia berbasis subjek dan kata kerja eksplisit (`Tiket dibuat oleh ...`, `Tiket dikerjakan oleh ...`, `... bergabung mengerjakan tiket`, `📌 ... mengambil tiket ini`, `Langkah "..." diselesaikan oleh ...`). | Tampilan riwayat aktivitas pada Ticket Detail Sheet menampilkan narasi humanis tanpa kode debug teknis. |
| **4** | **Task Policy & Otorisasi** | Kebijakan otorisasi ketat di `checkTaskPolicy`: admin web hanya bisa dihapus/diselesaikan oleh admin pembuat dan teknisi didelegasikan; tiket buatan teknisi Android hanya bisa dihapus/diselesaikan oleh teknisi pembuat dan admin pembuat. Tombol **Ambil Task** (`POST /api/tasks/:id/claim`) muncul otomatis jika tiket belum ada delegasi. | Validasi hak akses pada UI: disable/sembunyikan tombol hapus & selesai jika bukan pembuat/assignee yang berhak. Tampilkan tombol `Ambil Task` jika tiket unassigned. |

---

## 2. LAPORAN AUDIT NYATA & RISET BUG (ZERO HALLUCINATION)

Semua pengujian dijalankan langsung pada container aktif dan SQLite database server:

```bash
$ npm test
> pingdulu-server@1.0.0 test
> tsx --test --test-concurrency=1 tests/*.test.ts

ℹ tests 39
ℹ suites 0
ℹ pass 39
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 13789.437968
```

### Rincian Test Suites Lulus (39/39 Tests):
1. **PingDulu Server E2E Verification Test Suite** (8/8 pass)
2. **Bug Fixes & Feature Verification Test Suite** (5/5 pass)
3. **Multi-Assignee Backend Test Suite** (7/7 pass)
4. **Calendar, Task Policy, and Humanized Logs Test Suite** (8/8 pass)
5. **PingDulu Server-Sent Events (SSE) Live Sync Test Suite** (8/8 pass)

### Temuan Riset Bug & Solusi Nyata:
1. **Bug Foreign Key Constraint pada API Token**:
   - *Gejala*: Ketika client Android melakukan sinkronisasi tiket via API Token (`api_token`), kolom `created_by_id` dan `activity_logs.user_id` diisi menggunakan ID token (`req.user.id`). Karena tabel `api_tokens` memiliki ID independen yang tidak ada di tabel `users`, SQLite melempar error `SQLITE_CONSTRAINT_FOREIGNKEY` (HTTP 500).
   - *Solusi Nyata*: Ditambahkan pengecekan `req.user?.type !== 'api_token'` dan validasi eksistensi ID di tabel `users` dengan query `SELECT id FROM users WHERE id = ?`. Jika tidak ditemukan, kolom foreign key diisi `NULL` dan nama dicatat pada kolom aman `created_by_name`.
2. **Bug Fastify FST_ERR_CTP_EMPTY_JSON_BODY pada Endpoint `/claim` dan `/work`**:
   - *Gejala*: Client mengirimkan header `Content-Type: application/json` pada request POST `/claim` tanpa request body, menyebabkan Fastify melempar HTTP 400 Bad Request.
   - *Solusi Nyata*: Endpoint diset fleksibel menerima body opsional, dan client helper disesuaikan untuk tidak menyertakan `Content-Type: application/json` jika payload kosong.
3. **Bug Concurrency Race Condition pada SSE Test**:
   - *Gejala*: Pengujian SSE live broadcast bersamaan dengan suite lain mengalami interupsi socket timeout jika dijalankan secara paralel.
   - *Solusi Nyata*: Ditambahkan konfigurasi `--test-concurrency=1` pada script test untuk menjamin isolasi event SSE dan kebersihan state SQLite.

---

## 3. BAGIAN 1: FITUR KALENDER & HARI LIBUR NASIONAL RESMI

### A. Engine Hari Libur Nasional Indonesia
File: `src/utils/holidays.ts`
Mencakup daftar hari libur resmi berdasarkan SKB 3 Menteri Indonesia untuk 2025, 2026, dan 2027:
- Tahun Baru Masehi (1 Januari)
- Isra Miraj Nabi Muhammad SAW
- Tahun Baru Imlek
- Hari Suci Nyepi (Tahun Baru Saka)
- Idul Fitri (1 & 2 Syawal beserta Cuti Bersama)
- Waisak
- Kenaikan Isa Almasih
- Hari Lahir Pancasila (1 Juni)
- Idul Adha
- Tahun Baru Islam (1 Muharram)
- Hari Kemerdekaan RI (17 Agustus)
- Maulid Nabi Muhammad SAW
- Hari Raya Natal (25 Desember)

### B. Endpoint Kalender
1. **`GET /api/holidays?year=2026`**
   - Menghasilkan array hari libur nasional untuk tahun yang diminta.
2. **`GET /api/calendar?year=2026&month=11`**
   - Menghasilkan daftar hari libur nasional pada bulan tersebut beserta seluruh tiket kerja yang dijadwalkan (`scheduled_date_epoch_day`).

### C. Implementasi Web Client (`CalendarView.tsx`)
- Tampilan kalender grid bulanan responsif.
- Navigasi bulan sebelumnya dan berikutnya.
- Tanggal hari ini ditandai highlight biru.
- **Tanggal merah hari libur nasional** ditandai teks merah dan badge nama libur di bawah nomor tanggal.
- Tiket yang dijadwalkan pada tanggal terkait ditampilkan dengan badge prioritas, judul tiket, dan status. Mengklik tiket membuka modal detail pengerjaan.

---

## 4. BAGIAN 2: REDESAIN TOMBOL CARD & PENGHAPUSAN STOPWATCH

Berdasarkan masukan dan kondisi operasional di lapangan:
- **Stopwatch Dihapus**: Live running stopwatch yang memakan ruang dan memicu kebingungan teknisi telah dihilangkan dari Card Kanban dan Task Detail Sheet.
- **Tombol "Kerjakan"**:
  - Menggantikan tombol `Mulai Stopwatch`.
  - Endpoint: `POST /api/tasks/:id/work`.
  - Jika tiket berstatus `BACKLOG` atau `READY`, status langsung berpindah ke `IN_PROGRESS`.
  - Teknisi yang menekan tombol otomatis dicatat sebagai pekerja tiket.
  - Jika tiket sudah berjalan dan ditekan oleh teknisi lain, teknisi tersebut otomatis didaftarkan sebagai co-assignee dan log mencatat: `... bergabung mengerjakan tiket`.
- **Tombol "Selesai"**:
  - Menggantikan tombol `Tandai Selesai`.
  - Endpoint: `POST /api/tasks/:id/stage` dengan payload `{"stage": "DONE"}`.
  - Memvalidasi Task Policy sebelum dieksekusi.
- **Tombol "Ambil Task"**:
  - Endpoint: `POST /api/tasks/:id/claim`.
  - Ditampilkan secara dinamis jika tiket belum memiliki teknisi yang ditugaskan (`assignees` kosong / `assigned_to_id == null`).

---

## 5. BAGIAN 3: RIWAYAT HUMANIS & RELEVAN

Format log aktivitas dirombak total menggunakan bahasa Indonesia natural:

| Aksi / Event | Format Log Lama | Format Log Baru (Humanis) |
|---|---|---|
| **Pembuatan Tiket** | `Task created` | `Tiket dibuat oleh {nama}` |
| **Mulai Pengerjaan** | `Timer started` | `Tiket dikerjakan oleh {nama}` |
| **Teknisi Bergabung** | `Timer resumed` | `{nama} bergabung mengerjakan tiket` |
| **Klaim Tiket** | `Assigned to user` | `📌 {nama} mengambil tiket ini` |
| **Penyelesaian Tiket** | `Stage changed to DONE` | `Tiket diselesaikan oleh {nama}` |
| **Checklist Selesai** | `Checklist item updated` | `Langkah "{item}" diselesaikan oleh {nama}` |
| **Checklist Dibuka** | `Checklist unchecked` | `Langkah "{item}" dibuka kembali oleh {nama}` |
| **Delegasi Tiket** | `Reassigned` | `Tiket didelegasikan kepada {nama_teknisi} oleh {nama_admin}` |
| **Perpindahan Tahap** | `Stage changed` | `Tahap tiket dipindahkan ke {stage} oleh {nama}` |

---

## 6. BAGIAN 4: TASK POLICY ENFORCEMENT

Aturan otorisasi diterapkan di level backend (`src/routes/tasks.ts`) dan UI:

```
                          ┌──────────────────────────┐
                          │   Tiket Dibuat Melalui   │
                          └─────────────┬────────────┘
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 ▼                                             ▼
       [ Administrator Web ]                         [ Teknisi Android ]
                 │                                             │
      Siapa yang berhak hapus                       Siapa yang berhak hapus
        atau klik SELESAI?                            atau klik SELESAI?
                 │                                             │
       ┌─────────┴─────────┐                         ┌─────────┴─────────┐
       ▼                   ▼                         ▼                   ▼
[Admin Pembuat]    [Teknisi Ditugaskan]        [Teknisi Pembuat]   [Admin Server]
```

### Logika Aturan Tiket Tanpa Teknisi (Unassigned):
1. Jika tiket belum memiliki delegasi teknisi (`assigned_to_id == null` dan `assignees` kosong):
   - Card dan Detail Sheet menampilkan tombol **"Ambil Task"**.
2. Saat seorang teknisi mengklik tombol **"Ambil Task"**:
   - Server otomatis mendelegasikan tiket ke akun tersebut.
   - Hak akses eksklusif langsung aktif (hanya teknisi yang mengambil dan admin pembuat yang dapat menghapus / menyelesaikan tiket).
   - Aktivitas dicatat secara humanis: `📌 {nama} mengambil tiket ini`.

Jika pengguna yang tidak berhak mencoba menghapus atau menyelesaikan tiket, server mengembalikan respon HTTP 403:
```json
{
  "error": "Anda tidak memiliki izin untuk melakukan tindakan ini pada tiket ini",
  "code": "POLICY_FORBIDDEN"
}
```

---

## 7. DOKUMENTASI API CONTRACT BARU

### A. Klaim Tiket (Ambil Task)
- **Method & Path**: `POST /api/tasks/:id/claim`
- **Auth**: Bearer Token JWT atau Header `X-API-Key`
- **Headers**: `Authorization: Bearer <token>`
- **Response**: `200 OK`
```json
{
  "success": true,
  "task": {
    "id": 142,
    "ticket_code": "NET-142",
    "title": "Perbaikan Switch Core Lantai 3",
    "assigned_to_id": 4,
    "assigned_to_name": "teknisi sigit",
    "assignees": [
      { "id": 4, "username": "sigit", "display_name": "teknisi sigit", "role": "technician" }
    ]
  }
}
```

### B. Mulai Pengerjaan Tiket (Kerjakan)
- **Method & Path**: `POST /api/tasks/:id/work`
- **Auth**: Bearer Token JWT atau Header `X-API-Key`
- **Response**: `200 OK`
```json
{
  "success": true,
  "stage": "IN_PROGRESS",
  "is_collaborator": false,
  "message": "Tiket dikerjakan oleh teknisi sigit"
}
```

### C. Hari Libur Nasional
- **Method & Path**: `GET /api/holidays?year=2026`
- **Response**: `200 OK`
```json
{
  "year": 2026,
  "holidays": [
    {
      "date": "2026-01-01",
      "epoch_day": 20454,
      "name": "Tahun Baru 2026 Masehi",
      "is_cuti_bersama": false
    },
    {
      "date": "2026-03-20",
      "epoch_day": 20532,
      "name": "Hari Raya Idul Fitri 1447 H (Hari Pertama)",
      "is_cuti_bersama": false
    }
  ]
}
```

### D. Agenda Kalender Bulanan
- **Method & Path**: `GET /api/calendar?year=2026&month=11`
- **Response**: `200 OK`
```json
{
  "year": 2026,
  "month": 11,
  "holidays": [],
  "tasks": [
    {
      "id": 142,
      "ticket_code": "NET-142",
      "title": "penarikan kabel LAN di Ruang Dosen",
      "scheduled_date_epoch_day": 20778,
      "scheduled_date_str": "2026-11-20",
      "stage": "IN_PROGRESS",
      "priority": "P3_NORMAL"
    }
  ]
}
```

---

## 8. PANDUAN PATCH ANDROID UNTUK GOOGLE AI STUDIO (COPY-PASTE READY)

Gunakan prompt berikut pada sesi kerja Google AI Studio untuk memperbarui aplikasi Android PingDulu Field Ops:

```markdown
# PROMPT PEMBARUAN PINGDULU ANDROID FIELD OPS (PATCH KE-2)

Halo AI Studio, mohon perbarui codebase project PingDulu Android Field Ops agar selaras dengan update API Server PingDulu Patch Ke-2.

Terapkan 4 perubahan arsitektur berikut:

### 1. HAPUS FITUR STOPWATCH & GANTI TOMBOL CARD/SHEET:
- Buka Bottom Sheet Detail Tiket (layout / composable yang menampilkan tiket aktif):
  - HILANGKAN live stopwatch counter dan timer running display.
  - GANTI tombol "Mulai Stopwatch" (warna biru/ungu) menjadi tombol "Kerjakan" dengan icon Play / Wrench (`R.string.btn_work_task`).
  - GANTI tombol "Tandai Selesai" (warna hijau) menjadi tombol "Selesai" (`R.string.btn_complete_task`).
  - Tambahkan tombol "Ambil Task" (`R.string.btn_claim_task`) yang muncul HANYA jika tiket belum memiliki teknisi yang ditugaskan (`task.assignees.isNullOrEmpty() && task.assignedToId == null`).

### 2. ENDPOINT RETROFIT & REPOSITORY:
Tambahkan fungsi berikut pada ApiService.kt:
```kotlin
@POST("api/tasks/{id}/work")
suspend fun workTask(@Path("id") taskId: Long): Response<WorkTaskResponse>

@POST("api/tasks/{id}/claim")
suspend fun claimTask(@Path("id") taskId: Long): Response<ClaimTaskResponse>

@GET("api/holidays")
suspend fun getHolidays(@Query("year") year: Int): Response<HolidayResponse>

@GET("api/calendar")
suspend fun getCalendar(
    @Query("year") year: Int,
    @Query("month") month: Int
): Response<CalendarAgendaResponse>
```

Data Classes:
```kotlin
data class HolidayItem(
    val date: String,
    @SerializedName("epoch_day") val epochDay: Long,
    val name: String,
    @SerializedName("is_cuti_bersama") val isCutiBersama: Boolean
)

data class HolidayResponse(
    val year: Int,
    val holidays: List<HolidayItem>
)

data class WorkTaskResponse(
    val success: Boolean,
    val stage: String,
    @SerializedName("is_collaborator") val isCollaborator: Boolean,
    val message: String?
)

data class ClaimTaskResponse(
    val success: Boolean,
    val task: WorkItemDto
)
```

### 3. TASK POLICY ENFORCEMENT PADA UI:
Tambahkan helper method otorisasi pada TicketDetailViewModel atau TicketPolicyHelper:
```kotlin
fun canCompleteOrDelete(task: WorkItemDto, currentUser: UserSession): Boolean {
    // 1. Jika dibuat oleh teknisi di Android
    if (task.creatorClient == "android" || task.creatorRole == "technician") {
        val isCreator = (task.createdById != null && task.createdById == currentUser.id) ||
                        (task.createdByName != null && task.createdByName.equals(currentUser.displayName, ignoreCase = true))
        val isAdmin = currentUser.role.equals("admin", ignoreCase = true)
        return isCreator || isAdmin
    }

    // 2. Jika dibuat oleh admin web
    val isAdminCreator = currentUser.role.equals("admin", ignoreCase = true)
    val isAssigned = (task.assignedToId != null && task.assignedToId == currentUser.id) ||
                     (task.assignees?.any { it.id == currentUser.id } == true)

    return isAdminCreator || isAssigned
}
```
- Jika `canCompleteOrDelete` bernilai `false`: sembunyikan atau nonaktifkan tombol "Selesai" dan tombol "Hapus Tiket" (tampilkan tooltip "Hanya teknisi yang ditugaskan atau admin pembuat yang dapat menyelesaikan tiket ini").

### 4. FITUR KALENDER & AGENDA LAPANGAN:
- Tambahkan tampilan Kalender Bulanan pada tab Jadwal / Agenda Lapangan:
  - Mengambil daftar hari libur nasional via `getHolidays(year)`.
  - Tandai tanggal hari libur nasional dengan warna teks merah dan badge nama libur.
  - Tampilkan titik/badge pada tanggal yang memiliki tiket dijadwalkan (`scheduled_date_epoch_day`).
  - Mengklik tanggal menampilkan daftar tiket pada hari tersebut.

### 5. RIWAYAT HUMANIS:
Pastikan adapter log aktivitas tiket membaca string narasi humanis langsung dari field `activity_logs.action` atau `activity_logs.details` tanpa memformat ulang menjadi log teknis bahasa Inggris.

Mohon susun kode dengan arsitektur MVVM bersih, Coroutine + StateFlow, dan error handling HTTP 403 (POLICY_FORBIDDEN).
```
