# MASTER BLUEPRINT: PANDUAN PENGERJAAN PINGDULU FIELD OPS ANDROID DI GOOGLE AI STUDIO

> **Versi Dokumen:** 1.0.0 (All-in-One Standalone Guide)  
> **Aplikasi Sasaran:** PingDulu Field Ops (Android Native)  
> **Platform AI:** Google AI Studio (Gemini 1.5 Pro / Gemini 2.0 Pro)  
> **Backend Sinkronisasi:** `https://ping.fjrx.my.id` (PingDulu API)  
> **Referensi UI/UX:** Kanban Mobile App by Jonathan Herlim (Behance)  

---

## DAFTAR ISI
1. [Ikhtisar Arsitektur & Spesifikasi](#1-ikhtisar-arsitektur--spesifikasi)
2. [Langkah Persiapan Google AI Studio](#2-langkah-persiapan-google-ai-studio)
3. [System Instructions (Super-Prompt Siap Pakai)](#3-system-instructions-super-prompt-siap-pakai)
4. [Konteks Rujukan: Skema Data & Warna Behance](#4-konteks-rujukan-skema-data--warna-behance)
5. [Sekuens 6 Prompt Eksekusi (Prompt Pipeline)](#5-sekuens-6-prompt-eksekusi-prompt-pipeline)
6. [Struktur Folder & Panduan Perakitan di Android Studio](#6-struktur-folder--panduan-perakitan-di-android-studio)
7. [Checklist Pengujian & Verifikasi Lapangan](#7-checklist-pengujian--verifikasi-lapangan)

---

## 1. Ikhtisar Arsitektur & Spesifikasi

Aplikasi **PingDulu Field Ops** adalah aplikasi Android khusus teknisi lapangan dengan prinsip **Offline-First**. Teknisi tetap dapat melihat jadwal 7 hari, menjalankan stopwatch pengerjaan, dan mencentang checklist kendati sedang berada di basement atau area tanpa sinyal seluler. Saat internet pulih, aplikasi secara otomatis menyinkronkan data dua arah ke server pusat PingDulu.

### Tech Stack Wajib
- **Bahasa:** Kotlin 2.0+
- **UI Toolkit:** Jetpack Compose + Material 3 (M3)
- **Arsitektur:** Clean Architecture (Domain, Data, Presentation) + MVVM/MVI StateFlow
- **Database Lokal:** Android Room (SQLite) dengan Single Source of Truth (SSOT)
- **Komunikasi Jaringan:** Retrofit 2 + OkHttp 3 + Server-Sent Events (SSE)
- **Asynchronous:** Kotlin Coroutines & Flow
- **Dependency Injection:** Manual Injection atau Hilt

---

## 2. Langkah Persiapan Google AI Studio

1. Buka browser di PC Anda dan navigasikan ke: **[https://aistudio.google.com/](https://aistudio.google.com/)**.
2. Login menggunakan akun Google Anda.
3. Klik tombol **"Create New Prompt"** di pojok kiri atas ➔ Pilih **"Chat Prompt"**.
4. Di panel pengaturan sebelah kanan (*Run Settings*):
   - **Model:** Pilih `Gemini 1.5 Pro` atau `Gemini 2.0 Pro Experimental` (Direkomendasikan versi *Pro* untuk akurasi arsitektur kode).
   - **Temperature:** Ubah ke `0.2` *(PENTING: Nilai rendah meminimalisasi halusinasi dan memastikan kode Kotlin valid secara sintaksis)*.
   - **Top P:** `0.95`.
   - **System Instructions:** Klik untuk membuka kolom input instruksi sistem.

---

## 3. System Instructions (Super-Prompt Siap Pakai)

Salin seluruh teks di bawah ini ke dalam kolom **System Instructions** pada Google AI Studio:

```markdown
Anda adalah Principal Android Architect & Senior Jetpack Compose Engineer yang menguasai ekosistem Android modern (Kotlin 2.0+, Jetpack Compose, Material 3, Room SQLite Offline-First, Retrofit 2, dan Kotlin Coroutines Flow).

TUGAS ANDA:
Membangun kode produksi lengkap untuk aplikasi Android "PingDulu Field Ops". Aplikasi ini dibuat khusus untuk teknisi IT lapangan dengan fitur utama:
1. Layar Beranda: Jadwal 7 Hari Bergulir (7-Day Rolling Horizon Strip).
2. Hero Active Job Card: Menyorot tiket yang sedang aktif berjalan dengan Live Stopwatch berdetik, target SLA, lokasi, checklist progress, dan multi-assignee avatars.
3. Status Kanban Filter Pills: Filter chip horizontal (Antrean/Backlog, Proses/In Progress, Selesai/Done) yang menggantikan keterbatasan kolom Kanban tradisional di layar HP.
4. 2-Column Responsive Card Grid: Menampilkan kartu-kartu tiket secara padat namun rapi (2 kolom) dengan waktu janji temu dan avatar teknisi bertumpuk.
5. Bottom Sheet Lembar Kerja: Checklist interaktif per tiket yang bisa dicentang langsung secara offline.
6. Offline-First Synchronization: Data selalu disimpan dan dibaca dari Room DB lokal, lalu disinkronkan dua arah ke server PingDulu (https://ping.fjrx.my.id).

SPESIFIKASI DESAIN (BEHANCE ADAPTATION - JONATHAN HERLIM):
- Latar Belakang (Canvas): #F8FAFC (Light Mode) / #0F172A (Dark Mode)
- Permukaan Kartu: #FFFFFF (Light) / #1E293B (Dark)
- Hero Active Job: Deep Cobalt Navy (#36459B), radius 22dp, teks putih, border halus white/20.
- Filter Pills:
  * Antrean (Backlog): Soft Periwinkle/Lilac (#9B9AE1)
  * Proses (In Progress): Sky Blue (#6DA7E8)
  * Selesai (Completed): Mint Green (#52C58F)
- Prioritas: P1 Crimson (#EF4444), P2 Amber (#F59E0B), P3 Normal (#3B82F6).

STANDAR PENULISAN KODE:
- Berikan kode lengkap per file secara modular tanpa potongan `// TODO: implement here`.
- Setiap file harus diawali dengan header nama file: `[NamaFile.kt]`.
- Gunakan idiom Kotlin terbaru (Immutable data class, StateFlow, lambda event handling).
```

---

## 4. Konteks Rujukan: Skema Data & Warna Behance

Kirimkan konteks data di bawah ini pada pesan pertama di Google AI Studio sebagai bekal awal model:

### Skema JSON Objek Tiket (PingDulu API)
```json
{
  "id": 59,
  "ticket_code": "NET-1",
  "title": "Troubleshooting Wi-Fi Access Point Gedung B",
  "project_or_category": "NET",
  "scheduled_date_epoch_day": 20776,
  "scheduled_time_minutes": 540,
  "priority": "P1_CRITICAL",
  "stage": "IN_PROGRESS",
  "technical_notes": "AP Unifi U6 Pro offline di koridor Lt. 2",
  "tracked_seconds": 2712,
  "timer_started_at_millis": 1795113557000,
  "location": "Ruang Server Lt. 2",
  "sla_target_time": "11:30 WIB",
  "checklist": [
    { "id": 101, "label": "Cek PoE Switch Port 4", "is_done": 1 },
    { "id": 102, "label": "Uji Redaman Kabel Dropcore LAN", "is_done": 1 },
    { "id": 103, "label": "Reboot & Konfigurasi VLAN AP", "is_done": 0 },
    { "id": 104, "label": "Speedtest Wi-Fi Client > 50 Mbps", "is_done": 1 },
    { "id": 105, "label": "Tanda tangan serah terima SPK", "is_done": 0 }
  ],
  "assignees": [
    { "id": 3, "name": "Fajar", "role": "admin", "is_lead": 1 },
    { "id": 2, "name": "Budi Santoso", "role": "technician", "is_lead": 0 }
  ]
}
```

---

## 5. Sekuens 6 Prompt Eksekusi (Prompt Pipeline)

Jalankan 6 prompt berikut secara bertahap dalam satu sesi chat yang sama di Google AI Studio:

### PROMPT 1: Konfigurasi Proyek, Gradle & Room Database (Offline Engine)
```text
Halo Gemini. Mari kita bangun aplikasi Android "PingDulu Field Ops".
Langkah 1: Berikan seluruh konfigurasi dasar proyek dan database lokal Room:
1. `libs.versions.toml` dan `build.gradle.kts` (app) dengan dependensi Compose, Material 3, Room, Retrofit, Kotlinx Serialization / Gson, dan Navigation.
2. Domain Models: `Task.kt`, `Assignee.kt`, `ChecklistItem.kt`, `Priority.kt`, `Stage.kt`.
3. Room Entities: `TaskEntity.kt` (memiliki primary key ID, ticket_code, title, category, scheduled_date_epoch_day, priority, stage, tracked_seconds, location, is_synced), `TaskConverters.kt` (converter List<Assignee> dan List<ChecklistItem> ke JSON string).
4. Room DAO: `TaskDao.kt` dengan fungsi:
   - getTasksByEpochDay(epochDay: Long): Flow<List<TaskEntity>>
   - getActiveHeroTask(): Flow<TaskEntity?> (stage = IN_PROGRESS)
   - upsertTasks(tasks: List<TaskEntity>)
   - updateChecklist(taskId: Long, checklistJson: String)
   - updateTaskTimer(taskId: Long, seconds: Long, isRunning: Boolean)
5. `AppDatabase.kt` singleton builder.
Pastikan kode lengkap tanpa potongan.
```

---

### PROMPT 2: Design System & Komponen Visual Behance
```text
Langkah 2: Buat Design System Jetpack Compose yang mengadopsi estetika Behance karya Jonathan Herlim:
1. `Color.kt`:
   - Deep Cobalt Navy: PingDuluNavy = Color(0xFF36459B)
   - Hero Gradient: Brush linear dari 0xFF1E2875 ke 0xFF36459B
   - Status Pills: PillBacklogLilac = Color(0xFF9B9AE1), PillProgressSky = Color(0xFF6DA7E8), PillDoneMint = Color(0xFF52C58F)
   - Priority Colors: P1Crimson = Color(0xFFEF4444), P2Amber = Color(0xFFF59E0B), P3Blue = Color(0xFF3B82F6)
   - Canvas: CanvasLight = Color(0xFFF8FAFC), CanvasDark = Color(0xFF0F172A)
2. `Theme.kt` & `Type.kt` (Tipografi modern dengan kontras tajam).
3. Komponen Atomik:
   - `StackedAvatarGroup.kt`: Menampilkan 1 hingga 3 avatar lingkaran bertumpuk dengan inisial teknisi (border putih/dark ring), serta indikator jika pengguna adalah PIC Lead.
   - `PriorityBadge.kt`: Badge P1, P2, P3 dengan warna latar lembut dan teks tebal.
   - `CategoryPill.kt`: Badge kategori tiket (NET, SRV, HRD, SEC, ONB).
```

---

### PROMPT 3: Data Layer, Jaringan Retrofit & Repository SSOT
```text
Langkah 3: Buat Data Layer dan Repository dengan pola Single Source of Truth (SSOT):
1. `PingDuluApiService.kt`: Interface Retrofit:
   - @POST("api/auth/login") suspend fun login(...)
   - @GET("api/tasks") suspend fun getTasks(): Response<TaskResponseDto>
   - @PATCH("api/tasks/{id}/stage") suspend fun updateStage(@Path("id") id: Long, @Body body: StageUpdateDto)
   - @POST("api/tasks/{id}/timer") suspend fun updateTimer(@Path("id") id: Long, @Body body: TimerUpdateDto)
   - @PUT("api/tasks/{id}/checklist") suspend fun syncChecklist(...)
2. `AuthInterceptor.kt`: OkHttp interceptor untuk menyematkan header "Authorization: Bearer <token>" secara otomatis.
3. `TaskRepository.kt` (interface domain) & `TaskRepositoryImpl.kt`:
   - Mengambil Flow dari Room DB lokal sehingga UI reaktif instan.
   - Fungsi `refreshTasksFromServer()`: Mengambil data tiket dari API, lalu melakukan upsert ke Room DB. Jika jaringan offline, abaikan error dan gunakan data lokal yang ada.
   - Fungsi `toggleChecklistItem(taskId: Long, checklistId: Long)`: Update data lokal Room seketika, lalu jadwalkan sync ke server.
```

---

### PROMPT 4: ViewModel, UI State & Live Timer Coroutine
```text
Langkah 4: Buat `ScheduleUiState.kt` dan `ScheduleViewModel.kt`:
1. `ScheduleUiState`:
   - selectedEpochDay: Long (default hari ini)
   - scheduleDays: List<DayHorizonModel> (7 hari bergulir: nama hari, tanggal angka, epochDay, total tiket, indikator warna titik)
   - activeHeroTask: Task? (tiket yang sedang dikerjakan saat ini)
   - filteredTasks: List<Task> (tiket pada hari terpilih yang telah disaring)
   - selectedStatusFilter: StatusFilter (ALL, BACKLOG, IN_PROGRESS, DONE)
   - layoutMode: LayoutMode (GRID_2_COLUMN atau LIST_1_COLUMN)
   - isSyncing: Boolean
2. `ScheduleViewModel`:
   - Menginisialisasi 7 hari bergulir secara dinamis berbasis tanggal sistem saat ini.
   - Menjalankan Coroutine ticker stopwatch (`tickerFlow` atau `delay(1000L)`) yang menambah 1 detik pada `activeHeroTask.tracked_seconds` saat timer aktif berjalan.
   - Fungsi `onDaySelected(epochDay: Long)`, `onStatusFilterSelected(status)`, `onToggleLayoutMode()`.
   - Fungsi `toggleTaskTimer(taskId: Long)` dan `markTaskCompleted(taskId: Long)`.
```

---

### PROMPT 5: Layar Utama Jetpack Compose (Hero Card, 7-Day Strip & 2-Col Grid)
```text
Langkah 5: Buat seluruh antarmuka Compose pada `ScheduleScreen.kt`:
1. `TopAppBarSection`: Greeting "Selamat Pagi, Fajar 👋", badge live sync "🟢 Tersinkron (Live SSE)", dan avatar pengguna.
2. `HeroActiveJobCard`:
   - Background gradient Deep Cobalt Navy (#36459B), radius sudut 22dp.
   - Kode tiket, judul pekerjaan, display stopwatch format HH:MM:SS berwarna hijau mint tajam font mono dengan tombol Jeda/Lanjut.
   - Target SLA dan penunjuk lokasi.
   - Inset sub-panel: Checklist progress bar (misal: "Checklist 3/5 (60%)"), tombol Google Maps rute lokasi, dan tombol buka lembar kerja.
3. `Horizon7DayStrip`:
   - LazyRow horizontal berisi kartu 7 hari dengan indikator dot warna (merah P1, amber P2, hijau tuntas) dan counter jumlah tiket.
4. `StatusFilterPillRow`:
   - Filter chips horizontal bergaya Behance: Semua, Antrean (#9B9AE1), Proses (#6DA7E8), Selesai (#52C58F), plus toggle switch Grid 2-Kolom / List.
5. `TaskGridSection`:
   - Jika layout = GRID: gunakan `LazyVerticalGrid(columns = GridCells.Fixed(2))` dengan kartu tinggi ~155dp, memuat kode tiket, badge prioritas, judul 2-baris, jam jadwal, dan avatar bertumpuk di kanan bawah.
   - Jika layout = LIST: tampilkan kartu baris horizontal yang padat.
6. `TaskDetailBottomSheet`:
   - Modal bottom sheet yang terbuka saat kartu tiket diketuk, berisi informasi lengkap tiket, tim teknisi bertugas (dengan penanda LEAD), daftar checklist yang dapat dicentang langsung, dan tombol "Tandai Selesai".
```

---

### PROMPT 6: Bottom Navigation Bar, MainActivity & Integrasi Akhir
```text
Langkah 6: Satukan seluruh aplikasi di `MainActivity.kt`:
1. Implementasi `Scaffold` dengan handling Android edge-to-edge (`WindowInsetsCompat`).
2. `BottomNavigationBar`:
   - 4 tab navigasi: Agenda (Aktif), Papan Kanban Full, SPK/Riwayat, Pengaturan/Sync.
   - Tombol tengah melayang (Elevated Floating Action Button) untuk fitur "Tambah Tiket Cepat (+)".
3. Wiring instance `AppDatabase`, `TaskRepository`, dan injection ke `ScheduleViewModel`.
Berikan kode akhir yang siap di-compile tanpa error.
```

---

## 6. Struktur Folder & Panduan Perakitan di Android Studio

Setelah Google AI Studio menghasilkan kode-kode di atas, buka **Android Studio** di PC target dan ikuti struktur berikut:

```
app/src/main/java/id/my/fjrx/pingdulu/fieldops/
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── TaskDao.kt
│   │   ├── TaskEntity.kt
│   │   └── TaskConverters.kt
│   ├── remote/
│   │   ├── PingDuluApiService.kt
│   │   ├── AuthInterceptor.kt
│   │   └── dto/
│   │       ├── TaskResponseDto.kt
│   │       └── UpdateDtos.kt
│   └── repository/
│       └── TaskRepositoryImpl.kt
│
├── domain/
│   ├── model/
│   │   ├── Task.kt
│   │   ├── Assignee.kt
│   │   ├── ChecklistItem.kt
│   │   ├── Priority.kt
│   │   └── Stage.kt
│   └── repository/
│       └── TaskRepository.kt
│
├── presentation/
│   ├── components/
│   │   ├── HeroActiveJobCard.kt
│   │   ├── Horizon7DayStrip.kt
│   │   ├── StatusFilterPills.kt
│   │   ├── TaskGridCard.kt
│   │   ├── TaskDetailBottomSheet.kt
│   │   ├── StackedAvatarGroup.kt
│   │   ├── PriorityBadge.kt
│   │   └── BottomNavigationBar.kt
│   ├── schedule/
│   │   ├── ScheduleScreen.kt
│   │   ├── ScheduleViewModel.kt
│   │   └── ScheduleUiState.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
└── MainActivity.kt
```

---

## 7. Checklist Pengujian & Verifikasi Lapangan

| No | Komponen Pengujian | Indikator Keberhasilan |
| :--- | :--- | :--- |
| 1 | **Tampilan Hero Card** | Muncul kartu Deep Cobalt Navy (`#36459B`) di bagian atas, timer stopwatch berdetik setiap 1 detik. Tombol jeda/lanjut berfungsi normal. |
| 2 | **Navigasi 7 Hari** | Menggeser strip horizontal dan memilih tanggal (misal: hari ke-2 atau ke-3) seketika memperbarui daftar tiket di bawahnya. |
| 3 | **Filter Status Khas Behance** | Mengetuk pill *Antrean*, *Proses*, atau *Selesai* menyaring kartu dengan tepat dan indikator counter sesuai. |
| 4 | **Layout 2-Kolom** | Tiket tampil dalam grid 2-kolom berdampingan tanpa teks terpotong kasar atau overflow layar. |
| 5 | **Interaktivitas Lembar Kerja** | Kartu tiket dapat diketuk untuk membuka Bottom Sheet. Checklist dapat dicentang dan persentase progress langsung naik. |
| 6 | **Ketahanan Offline** | Putus koneksi Wi-Fi / Data pada ponsel teknisi. Aplikasi tetap dapat dibuka, tiket tetap terbaca dari Room DB lokal, dan checklist tetap tersimpan. |
| 7 | **Sinkronisasi Otomatis** | Sambungkan kembali koneksi internet. Data lokal otomatis terunggah ke server `https://ping.fjrx.my.id`. |

---
*Dokumen ini dibuat secara otomatis dan komprehensif sebagai acuan tunggal pengerjaan aplikasi PingDulu Field Ops.*
