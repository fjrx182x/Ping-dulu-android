# MASTER PROMPT PATCH: ZERO-CONFIG LOGIN & PENYELARASAN SERVER PINGDULU

> **Nama File:** `prompt_patch_api_pingdulu_android.md`  
> **Target Aplikasi:** PingDulu Field Ops (Android Native - Jetpack Compose)  
> **Target AI Platform:** Google AI Studio (Gemini 1.5 Pro / 2.0 Pro)  
> **Server Tujuan:** `https://ping.fjrx.my.id` (Default Hardcoded - Zero Configuration)  
> **Fitur Baru:** Gatekeeper Login Screen saat pertama buka, auto-sinkronisasi data server seketika setelah login, tanpa perlu input URL API manual, dan penyesuaian kontrak endpoint.

---

## DAFTAR ISI
1. [Alur Baru Pengguna: Login Pertama Kali & Auto-Sync](#1-alur-baru-pengguna-login-pertama-kali--auto-sync)
2. [Prompt Tahap 1: Login Gatekeeper Screen (Tampilan Awal Tanpa Konfigurasi)](#prompt-tahap-1-login-gatekeeper-screen-tampilan-awal-tanpa-konfigurasi)
3. [Prompt Tahap 2: DTO Autentikasi & Model Multi-Assignee Server](#prompt-tahap-2-dto-autentikasi--model-multi-assignee-server)
4. [Prompt Tahap 3: Kontrak API Service (Stage POST, Timer Action, Checklist PATCH)](#prompt-tahap-3-kontrak-api-service-stage-post-timer-action-checklist-patch)
5. [Prompt Tahap 4: Repository & Logika Auto-Sync Saat Login Berhasil](#prompt-tahap-4-repository--logika-auto-sync-saat-login-berhasil)
6. [Prompt Tahap 5: Pengaturan Sederhana & Audit Unit Test](#prompt-tahap-5-pengaturan-sederhana--audit-unit-test)
7. [BONUS: Mega Prompt All-in-One (Eksekusi 1 Kali di AI Studio)](#bonus-mega-prompt-all-in-one-eksekusi-1-kali-di-ai-studio)

---

## 1. Alur Baru Pengguna: Login Pertama Kali & Auto-Sync

```
[Buka Aplikasi Pertama Kali]
           │
           ▼
┌──────────────────────────────────────────────┐
│  Halaman Login PingDulu Field Ops            │
│  - Background: Deep Cobalt Navy (#36459B)    │
│  - Status Server: 🟢 ping.fjrx.my.id (Fixed) │
│  - Input: Username ("fajar", "admin")        │
│  - Input: Password ("fajarsigit182")         │
│  - Tombol: [ Masuk & Sinkronisasi Jadwal ]   │
└──────────────────────────────────────────────┘
           │ (Teknisi tekan Masuk)
           ▼
[POST /api/auth/login ke https://ping.fjrx.my.id]
           │
           ├─► Simpan JWT Token & Nama ("Fajar") ke DataStore
           ├─► Eksekusi AUTO-SYNC: GET /api/tasks & POST /api/sync
           ├─► Simpan semua tiket server ke Room DB lokal
           │
           ▼
┌──────────────────────────────────────────────┐
│  Layar Utama Agenda 7 Hari (ScheduleScreen)  │
│  - Tiket asli dari server langsung muncul!   │
│  - Live SSE aktif mendengarkan perubahan     │
└──────────────────────────────────────────────┘
           │
[Buka Aplikasi Berikutnya] ──► Token masih ada? ──► Langsung ke Agenda (Auto-Login)
```

---

## PROMPT TAHAP 1: Login Gatekeeper Screen (Tampilan Awal Tanpa Konfigurasi)

*Salin prompt ini ke Google AI Studio untuk membuat alur login awal:*

```text
Halo Gemini. Kita ingin menyempurnakan alur pengguna (user flow) aplikasi Android PingDulu Field Ops:
1. Saat aplikasi pertama kali dibuka (atau jika pengguna belum login), aplikasi HARUS langsung menampilkan halaman Login Gatekeeper terlebih dahulu, BUKAN langsung masuk ke layar Agenda dengan data dummy.
2. Tidak perlu meminta pengguna memasukkan URL API server atau token manual. Base URL server sudah otomatis dan permanen terkunci ke `https://ping.fjrx.my.id/`.
3. Form login hanya meminta `Username` (contoh: "fajar" atau "admin") dan `Password` (kata sandi akun teknisi di server).
4. Saat login berhasil, aplikasi otomatis menyimpan sesi dan langsung memicu sinkronisasi data dari server sebelum beralih ke layar utama Agenda.
5. Jika sudah login sebelumnya (token tersimpan di DataStore), aplikasi langsung membuka layar Agenda tanpa menampilkan form login lagi (Auto-Login).

Instruksi Pembuatan Kode:
1. Buat file baru `app/src/main/java/com/example/presentation/login/LoginScreen.kt`:
   - Desain modern bernuansa Behance: Latar gradasi Deep Cobalt Navy (#1E2875 ke #36459B), logo PD, judul "PingDulu Field Ops", subtitle "Portal Teknisi Lapangan IT & Jaringan".
   - Indikator server permanen di bawah logo: Badge hijau "🟢 Terhubung ke ping.fjrx.my.id" (tanpa input URL manual).
   - Form field: OutlinedTextField untuk `Username` (ikon Person, placeholder "fajar") dan OutlinedTextField untuk `Password` (ikon Lock, visual transformation password).
   - Tombol utama: "Masuk & Sinkronisasi Jadwal" dengan status loading spinner saat `isLoggingIn = true`.
   - Pesan error / status snackbar jika login gagal.

2. Di `app/src/main/java/com/example/presentation/schedule/ScheduleUiState.kt`:
   - Tambahkan properti: `val isLoggedIn: Boolean get() = !authToken.isNullOrBlank()`

3. Di `app/src/main/java/com/example/MainActivity.kt`:
   - Periksa status autentikasi:
     if (!uiState.isLoggedIn) {
         LoginScreen(
             isLoggingIn = uiState.isLoggingIn,
             errorMessage = uiState.syncMessage,
             onLogin = viewModel::loginToServer
         )
     } else {
         // Tampilkan Scaffold utama dengan Drawer, Agenda, Kanban, SPK, dan Pengaturan
     }

Berikan kode lengkap untuk `LoginScreen.kt`, pembaruan `ScheduleUiState.kt`, dan `MainActivity.kt`.
```

---

## PROMPT TAHAP 2: DTO Autentikasi & Model Multi-Assignee Server

*Salin prompt ini ke Google AI Studio untuk menyelaraskan struktur data dengan server:*

```text
Langkah 2: Sesuaikan model data DTO di Android dengan skema database server aktual:

1. Di file `app/src/main/java/com/example/data/remote/dto/AuthDtos.kt`:
   - Ubah `LoginRequestDto`: Server PingDulu membaca field `username`, bukan `email`. Ubah menjadi:
     @JsonClass(generateAdapter = true)
     data class LoginRequestDto(
         @Json(name = "username") val username: String,
         @Json(name = "password") val password: String
     )
   - Ubah `UserDto`: Server mengembalikan `{ "id": 3, "username": "fajar", "display_name": "Fajar", "role": "admin" }`.
     @JsonClass(generateAdapter = true)
     data class UserDto(
         @Json(name = "id") val id: Long = 0L,
         @Json(name = "username") val username: String? = null,
         @Json(name = "display_name") val displayName: String? = null,
         @Json(name = "role") val role: String = "technician"
     ) {
         val resolvedName: String
             get() = displayName?.takeIf { it.isNotBlank() } ?: username?.takeIf { it.isNotBlank() } ?: "Teknisi"
     }

2. Di file `app/src/main/java/com/example/data/remote/dto/TaskDtos.kt`:
   - Ubah `AssigneeDto`: Buat field `display_name` dan `name` fleksibel agar Moshi tidak melempar JsonDataException saat server mengirim kolom tabel users:
     @JsonClass(generateAdapter = true)
     data class AssigneeDto(
         @Json(name = "id") val id: Long,
         @Json(name = "username") val username: String? = null,
         @Json(name = "display_name") val displayName: String? = null,
         @Json(name = "name") val name: String? = null,
         @Json(name = "role") val role: String = "technician",
         @Json(name = "is_lead") val isLead: Int = 0
     ) {
         val resolvedName: String
             get() = displayName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: username ?: "Teknisi"
     }
   - Pastikan fungsi mapper `AssigneeDto.toDomain()` menggunakan `resolvedName`.

Berikan kode lengkap file `AuthDtos.kt` dan `TaskDtos.kt`.
```

---

## PROMPT TAHAP 3: Kontrak API Service (Stage POST, Timer Action, Checklist PATCH)

*Salin prompt ini ke Google AI Studio untuk menyelaraskan endpoint Retrofit:*

```text
Langkah 3: Perbaiki kontrak HTTP endpoint Retrofit dan request DTO agar cocok dengan rute server Fastify PingDulu:

1. Di file `app/src/main/java/com/example/data/remote/dto/UpdateDtos.kt`:
   - Ganti `TimerUpdateDto` menjadi perintah berbasis action string ("start", "pause", atau "reset") sesuai endpoint `POST /api/tasks/:id/timer`:
     @JsonClass(generateAdapter = true)
     data class TimerActionRequestDto(
         @Json(name = "action") val action: String // Nilai: "start" | "pause" | "reset"
     )
   - Buat DTO untuk centang checklist per butir (PATCH /api/tasks/:id/checklist/:stepId):
     @JsonClass(generateAdapter = true)
     data class ChecklistStepToggleDto(
         @Json(name = "is_done") val isDone: Int // 1 untuk selesai, 0 untuk belum
     )

2. Di file `app/src/main/java/com/example/data/remote/PingDuluApiService.kt`:
   - Kunci Base URL permanen ke `https://ping.fjrx.my.id/`.
   - Rute Stage: Ganti `@PATCH("api/tasks/{id}/stage")` menjadi `@POST("api/tasks/{id}/stage")`:
     @POST("api/tasks/{id}/stage")
     suspend fun updateStage(
         @Path("id") id: Long,
         @Body body: StageUpdateDto
     ): Response<Unit>
   - Rute Timer:
     @POST("api/tasks/{id}/timer")
     suspend fun updateTimer(
         @Path("id") id: Long,
         @Body body: TimerActionRequestDto
     ): Response<Unit>
   - Rute Checklist: Ganti PUT menjadi PATCH per-langkah:
     @PATCH("api/tasks/{id}/checklist/{stepId}")
     suspend fun toggleChecklistStep(
         @Path("id") id: Long,
         @Path("stepId") stepId: Long,
         @Body body: ChecklistStepToggleDto
     ): Response<Unit>

Berikan kode lengkap untuk `UpdateDtos.kt` dan `PingDuluApiService.kt`.
```

---

## PROMPT TAHAP 4: Repository & Logika Auto-Sync Saat Login Berhasil

*Salin prompt ini ke Google AI Studio untuk mengaktifkan sinkronisasi otomatis:*

```text
Langkah 4: Perbarui `TaskRepositoryImpl.kt` dan `ScheduleViewModel.kt` agar:
1. Begitu teknisi menekan tombol Masuk dan login berhasil (menerima token JWT valid), sistem LANGSUNG memicu sinkronisasi menyeluruh (`refreshTasksFromServer()` / `triggerSync()`).
2. Data tiket nyata dari server langsung diunduh dan disimpan ke Room DB lokal.

Pembaruan yang harus dilakukan:
1. Di `app/src/main/java/com/example/data/repository/TaskRepositoryImpl.kt`:
   - Fungsi `login(username: String, password: String)`:
     Kirim `LoginRequestDto(username = username.trim(), password = password)`.
     Saat respons sukses: Simpan token, nama teknisi (`body.user?.resolvedName ?: "Fajar"`), dan role ke DataStore preferences.
   - Fungsi `toggleTaskTimer(taskId: Long)`:
     Update Room lokal secara instan.
     Panggil remote server: `apiServiceProvider().updateTimer(taskId, TimerActionRequestDto(action = if (willRun) "start" else "pause"))`.
   - Fungsi `toggleChecklistItem(taskId: Long, checklistId: Long)`:
     Update Room lokal secara instan.
     Panggil remote server: `apiServiceProvider().toggleChecklistStep(taskId, checklistId, ChecklistStepToggleDto(isDone = if (item.isDone) 1 else 0))`.
   - Fungsi `pushLocalEntityToServer()`:
     Panggil `api.updateStage()` via POST dan `api.updateTimer()` via action string.

2. Di `app/src/main/java/com/example/presentation/schedule/ScheduleViewModel.kt`:
   - Fungsi `loginToServer(username: String, password: String)`:
     viewModelScope.launch {
         isLoggingInFlow.value = true
         val result = repository.login(username, password)
         result.fold(
             onSuccess = {
                 syncMessageFlow.value = "Login berhasil! Mengunduh jadwal tiket..."
                 // Langsung sinkronkan tiket riil dari server
                 triggerSync()
             },
             onFailure = { err ->
                 syncMessageFlow.value = err.localizedMessage ?: "Username atau kata sandi salah."
             }
         )
         isLoggingInFlow.value = false
     }

Berikan kode lengkap `TaskRepositoryImpl.kt` dan fungsi terkait di `ScheduleViewModel.kt`.
```

---

## PROMPT TAHAP 5: Pengaturan Sederhana & Audit Unit Test

*Salin prompt ini ke Google AI Studio untuk merapikan halaman Settings:*

```text
Langkah 5: 
1. Di `app/src/main/java/com/example/presentation/settings/SettingsSyncScreen.kt`:
   - Hapus input field teks URL server yang membingungkan.
   - Ganti dengan tampilan informasi status permanen:
     * Alamat Server: `https://ping.fjrx.my.id` (Terkoneksi)
     * Akun Sedang Login: Nama teknisi & role (misal: "Fajar (admin)")
   - Sediakan tombol "Sinkronisasi Sekarang" dan tombol "Keluar / Logout" berwarna merah yang membersihkan token dan mengembalikan teknisi ke halaman LoginScreen.

2. Di `app/src/test/java/com/example/TaskRepositoryAuditTest.kt`:
   - Sesuaikan mock test:
     * Request login menggunakan `{ "username": "fajar", "password": "..." }`.
     * Response login memuat `{ "display_name": "Fajar", "role": "admin" }`.
     * Panggilan stage menggunakan HTTP POST dan timer menggunakan action string.
   - Pastikan seluruh unit test lulus (100% assertions green).

Berikan kode lengkap untuk `SettingsSyncScreen.kt` dan `TaskRepositoryAuditTest.kt`.
```

---

## BONUS: Mega Prompt All-in-One (Eksekusi 1 Kali di AI Studio)

*Gunakan prompt ini jika ingin AI Studio langsung merombak seluruh alur login gatekeeper dan kontrak API dalam sekali kirim:*

```text
Halo Gemini. Tolong lakukan perombakan pada aplikasi Android PingDulu Field Ops agar memiliki alur Login Gatekeeper otomatis tanpa perlu konfigurasi URL manual:

1. ALUR LOGIN AWAL (Gatekeeper Flow):
   - Kunci Base URL server permanen ke `https://ping.fjrx.my.id/`. Hapus semua input manual URL API.
   - Jika pengguna belum login (`authToken` kosong), layar utama HARUS menampilkan `LoginScreen.kt` (latar Deep Cobalt Navy #36459B, logo PD, badge "Terhubung ke ping.fjrx.my.id", field Username & Password, dan tombol "Masuk & Sinkronisasi").
   - Jika sudah login, langsung buka layar Agenda 7 Hari (Auto-Login).
   - Di menu Pengaturan, sediakan tombol "Keluar / Logout" yang membersihkan token sesi dan mengembalikan tampilan ke LoginScreen.

2. PENYELARASAN DTO & AUTENTIKASI:
   - LoginRequestDto: Mengirim field `username` dan `password` (bukan email).
   - UserDto: Membaca field `username`, `display_name`, dan `role`.
   - AssigneeDto: Mendukung field `display_name` dari server users tabel tanpa crash saat field `name` kosong.

3. KONTRAK RETROFIT API (PingDuluApiService.kt):
   - Endpoint Stage: Menggunakan `@POST("api/tasks/{id}/stage")` dengan body StageUpdateDto(stage).
   - Endpoint Timer: Menggunakan `@POST("api/tasks/{id}/timer")` dengan body TimerActionRequestDto(action = "start" | "pause" | "reset").
   - Endpoint Checklist: Menggunakan `@PATCH("api/tasks/{id}/checklist/{stepId}")` dengan body ChecklistStepToggleDto(isDone = 1 | 0).

4. AUTO-SYNC REPOSITORY:
   - Di `ScheduleViewModel.loginToServer(username, password)`: Begitu respons login 200 OK diterima, LANGSUNG panggil `triggerSync()` untuk mengunduh seluruh tiket nyata dari server ke Room DB lokal.

Berikan seluruh file Kotlin yang dimodifikasi (`LoginScreen.kt`, `MainActivity.kt`, `ScheduleUiState.kt`, `AuthDtos.kt`, `TaskDtos.kt`, `UpdateDtos.kt`, `PingDuluApiService.kt`, `TaskRepositoryImpl.kt`, `ScheduleViewModel.kt`, dan `SettingsSyncScreen.kt`) secara lengkap dan siap salin tanpa placeholder TODO.
```

---
*Dokumen ini adalah spesifikasi resmi untuk alur Zero-Configuration Login & Auto-Sync aplikasi PingDulu Field Ops Android.*
