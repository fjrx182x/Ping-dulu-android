# MASTER PROMPT PATCH: PINGDULU FIELD OPS ANDROID (PAKET 1 s/d 4)

Dokumen ini memuat seluruh kode, model, arsitektur, dan instruksi teknis lengkap yang siap disalin ke **Google AI Studio** untuk memperbarui aplikasi **PingDulu Field Ops (Jetpack Compose)** agar sinkron 100% dengan Server dan Web Dashboard tanpa ada yang terlewat (*zero miss*).

---

## 🌐 Informasi Endpoint & Asset Server Resmi
- **Server URL:** `https://ping.fjrx.my.id` (atau lokal `http://10.0.2.2:3001` / `http://localhost:3001`)
- **Download Asset Icon Android (ZIP):**  
  `https://ping.fjrx.my.id/pingdulu-android-icons.zip`  
  *(Sudah terpasang di public server: berisi folder mipmap-mdpi s/d xxxhdpi, adaptive XML, dan playstore-icon)*

---

## 📋 Salin Prompt di Bawah Ini ke Google AI Studio

````markdown
Halo Gemini. Tolong lakukan pembaruan komprehensif pada arsitektur dan UI aplikasi Android PingDulu Field Ops (Jetpack Compose) mencakup 4 paket perbaikan teknis berikut:

======================================================================
PAKET 1: FIX TIMEZONE (WIB / ASIA/JAKARTA) & PERHITUNGAN TANGGAL 7 HARI
======================================================================
1. Kunci zona waktu seluruh aplikasi ke `Asia/Jakarta` (WIB = UTC+7):
   - Jangan gunakan `TimeZone.getDefault()` yang menghasilkan format PST (Pacific Standard Time) atau tanggal mundur 1 hari.
   - Buat file utilitas `app/src/main/java/com/example/util/DateTimeUtils.kt`:
   ```kotlin
   package com.example.util

   import java.time.Instant
   import java.time.LocalDate
   import java.time.ZoneId
   import java.time.format.DateTimeFormatter

   object DateTimeUtils {
       val JAKARTA_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

       // Hitung epoch day hari ini presisi WIB (contoh 20778 untuk 21 Nov 2026)
       fun getTodayEpochDay(): Long = LocalDate.now(JAKARTA_ZONE).toEpochDay()

       // Konversi tanggal string "YYYY-MM-DD" ke epoch day
       fun dateToEpochDay(dateStr: String): Long {
           return try {
               LocalDate.parse(dateStr).toEpochDay()
           } catch (e: Exception) {
               getTodayEpochDay()
           }
       }

       // Konversi epoch day ke format tanggal tampilan
       fun epochDayToDisplay(epochDay: Long): String {
           val date = LocalDate.ofEpochDay(epochDay)
           return date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy").withZone(JAKARTA_ZONE))
       }

       // Format Waktu Lapor dari timestamp created_at millis
       fun formatWaktuLapor(createdAtMillis: Long): String {
           val zdt = Instant.ofEpochMilli(createdAtMillis).atZone(JAKARTA_ZONE)
           val today = LocalDate.now(JAKARTA_ZONE)
           val timeFmt = DateTimeFormatter.ofPattern("HH:mm 'WIB'")
           val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'WIB'")
           return if (zdt.toLocalDate() == today) {
               "Hari ini, ${zdt.format(timeFmt)}"
           } else {
               zdt.format(dateFmt)
           }
       }

       // Format waktu jam terjadwal dari menit sejak tengah malam (0..1439)
       fun minutesToTimeDisplay(minutes: Int?): String {
           if (minutes == null) return ""
           val h = minutes / 60
           val m = minutes % 60
           return String.format("%02d:%02d WIB", h, m)
       }
   }
   ```

2. Di `ScheduleViewModel.kt`:
   - State `selectedEpochDay` saat inisialisasi HARUS default ke `DateTimeUtils.getTodayEpochDay()`.
   - Strip agenda 7 hari menampilkan hari ini di posisi pertama/tengah dan otomatis terpilih saat pertama kali layar dibuka.
   - Query DAO: Ambil tiket dengan `scheduled_date_epoch_day == selectedEpochDay`. Urutkan tiket yang memiliki `scheduled_time_minutes` lebih dulu secara kronologis, baru tiket tanpa jam berdasarkan prioritas.

======================================================================
PAKET 2: FORM TAMBAH TIKET CEPAT (PEKERJAAN TERENCANA & DELEGASI TEKNISI)
======================================================================
Perbarui `QuickAddTaskBottomSheet.kt`:
1. **Mode Pengerjaan (Hari Ini vs Terjadwal)**:
   - Sediakan tombol pemilih:
     - 🔘 **⚡ Hari Ini (Insiden Langsung)**: `scheduled_date_epoch_day = getTodayEpochDay()`, `scheduled_time_minutes = null`.
     - 🔘 **📅 Terencana (Pekerjaan Terjadwal)**: Tampilkan `DatePickerDialog` untuk memilih tanggal pelaksanaan, dan `TimePickerDialog` untuk jam pengerjaan (contoh: 10:00 WIB $\rightarrow$ 600 menit).
2. **Pilihan Teknisi Bertugas (Delegasi)**:
   - Fetch daftar teknisi dari API `GET /api/users`.
   - Sediakan dropdown / selector teknisi bertugas (menampilkan display_name & role).
   - Kirim `assignee_ids: listOf(selectedUserId)`.
3. **Katalog 15 Label IT Ops Resmi**:
   - Fetch dari `GET /api/labels` (menghasilkan nama & warna hex).
   - Tampilkan chip multi-select untuk label: *Jaringan, Server, Keamanan, Hardware, Wi-Fi, CCTV, Printer, Email/Akun, Urgent, Maintenance, Hotfix, Database, Cloud*.
   - Kirim `label_ids: listOf(...)`.
4. **Checklist Awal & Auto-Repeat**:
   - Input checklist awal pengerjaan (1 baris per langkah).
   - Checkbox opsi `Jadwalkan Tugas Rutin (Auto-Repeat)` dengan interval: `DAILY`, `WEEKDAYS`, `WEEKLY`, `MONTHLY`.

Payload JSON `POST /api/tasks`:
```json
{
  "title": "Pemasangan Access Point Lantai 3",
  "project_or_category": "NET",
  "scheduled_date_epoch_day": 20782,
  "scheduled_time_minutes": 600,
  "priority": "P2_HIGH",
  "stage": "BACKLOG",
  "technical_notes": "Lokasi Gedung Utama. Bawa tangga dan crimping tool.",
  "assignee_ids": [2],
  "label_ids": [1, 7],
  "checklist": ["Tarik kabel UTP", "Konfigurasi SSID VLAN 20", "Tes sinyal Wi-Fi"],
  "is_recurring": 0
}
```

======================================================================
PAKET 3: LEMBAR KERJA DETAIL TIKET (KAMERA, LAMPIRAN & KOMENTAR)
======================================================================
Perbarui `TaskDetailBottomSheet.kt`:
1. **Kamera & Lampiran Bukti Pengerjaan Lapangan**:
   - Tambahkan section **"Lampiran & Bukti Foto Troubleshooting"**:
   - Tombol `[📷 Ambil Foto]` dan `[📎 Pilih Berkas]`.
   - Gunakan `rememberLauncherForActivityResult(ActivityResultContracts.TakePicture())` dan `GetContent()`.
   - Unggah berkas ke server via endpoint `POST /api/tasks/{taskId}/attachments` menggunakan multipart form-data (field `file`, batas maks 10MB):
   ```kotlin
   // Retrofit Interface:
   @Multipart
   @POST("/api/tasks/{id}/attachments")
   suspend fun uploadAttachment(
       @Path("id") taskId: Long,
       @Part file: MultipartBody.Part
   ): Response<UploadResponse>
   ```
   - Tampilkan galeri foto mendatar (LazyRow) dengan preview thumbnail.
2. **Komentar & Catatan Progres Lapangan**:
   - Tambahkan section **"Diskusi & Update Teknis"**:
   - Fetch komentar via `GET /api/tasks/{taskId}/comments`.
   - Kolom teks + tombol kirim ke `POST /api/tasks/{taskId}/comments` dengan body `{"comment_text": "..."}`.
3. **Pencatat Waktu / Live Stopwatch**:
   - Tampilkan durasi berjalan `00:00:00` tersinkronisasi `timer_started_at_millis`.
   - Tombol aksi: `▶ Mulai` / `⏸ Jeda` / `⏹ Selesai`.
   - Simpan durasi ke `tracked_seconds` dan update via `PATCH /api/tasks/{taskId}`.
4. **Aksi Tiket Tambahan**:
   - Tombol `Snooze` (tunda 1 jam / besok).
   - Tombol `Tandai Terblokir` (jika menunggu vendor atau sparepart).

======================================================================
PAKET 4: ASSET & BRANDING RESMI SERVER PINGDULU
======================================================================
1. **Unduh Paket Icon Resmi**:
   - Seluruh icon telah disediakan langsung dari server:
     URL: `https://ping.fjrx.my.id/pingdulu-android-icons.zip`
   - Ekstrak isi ZIP ke folder `app/src/main/res/`:
     - `mipmap-mdpi/`, `mipmap-hdpi/`, `mipmap-xhdpi/`, `mipmap-xxhdpi/`, `mipmap-xxxhdpi/`
     - `mipmap-anydpi-v26/ic_launcher.xml` & `ic_launcher_round.xml`
     - `values/ic_launcher_background.xml` (warna `#1F5AE0`)
     - `playstore-icon.png`
2. **Perbarui TopBar Android (Ganti Teks [PD] dengan Logo Resmi)**:
   Di `TopAppBar` header layar utama, ganti kotak teks `[PD]` dengan komponen logo resmi squircle topi wizard:
   ```kotlin
   Box(
       modifier = Modifier
           .size(38.dp)
           .clip(RoundedCornerShape(10.dp))
           .background(
               brush = Brush.verticalGradient(
                   colors = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
               )
           ),
       contentAlignment = Alignment.Center
   ) {
       Image(
           painter = painterResource(id = R.mipmap.ic_launcher_foreground),
           contentDescription = "PingDulu Logo",
           modifier = Modifier.fillMaxSize(),
           contentScale = ContentScale.Fit
       )
   }
   ```
   Topi wizard putih akan tampil di tengah squircle biru dengan proporsi presisi 100% tanpa terpotong.

======================================================================
BERIKAN FILE KODE IMPLEMENTASI LENGKAP:
======================================================================
1. `app/src/main/java/com/example/util/DateTimeUtils.kt`
2. `app/src/main/java/com/example/data/remote/PingDuluApiService.kt` (Semua endpoint lengkap: tasks, sync, attachments, comments, labels, users)
3. `app/src/main/java/com/example/presentation/schedule/ScheduleViewModel.kt`
4. `app/src/main/java/com/example/presentation/schedule/components/QuickAddTaskBottomSheet.kt`
5. `app/src/main/java/com/example/presentation/schedule/components/TaskDetailBottomSheet.kt`
6. `app/src/main/java/com/example/presentation/schedule/components/ScheduleTopBar.kt`
````
