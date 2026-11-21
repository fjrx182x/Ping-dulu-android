# PROMPT PATCH GOOGLE AI STUDIO: NOTIFIKASI TUGAS, TANGGAL 7 HARI & PEKERJAAN TERENCANA

> **Target Aplikasi:** PingDulu Field Ops (Android Native Jetpack Compose)  
> **Server:** `https://ping.fjrx.my.id`  
> **Fitur Baru:** 
> 1. Penghapusan tenggat waktu (*due date*) & penggantian dengan Waktu Lapor (*created_at*).
> 2. Dukungan Tiket Terjadwal (*Pekerjaan Terencana*) vs Tiket Langsung (*Insiden Hari Ini*).
> 3. Penyelarasan tanggal agenda 7 hari (WIB / `LocalDate.now().toEpochDay()`) agar tiket muncul presisi di tanggal pelaksanaan kerja.
> 4. Notifikasi sistem status bar Android saat akun teknisi mendapat tugas baru.

---

## 📋 Salin Prompt di Bawah Ini ke Google AI Studio

```text
Halo Gemini. Tolong perbarui kode aplikasi Android PingDulu Field Ops (Jetpack Compose) dengan 4 penyempurnaan utama berikut:

=======================================================
1. HAPUS TENGGAT WAKTU & GANTI DENGAN WAKTU USER MELAPOR
=======================================================
- Di seluruh tampilan Android (Card Tugas, Detail Dialog, Form Tambah Tugas):
  * Hapus input dan badge "Tenggat Waktu / Due Date".
  * Ganti dengan informasi "Waktu Lapor": format jam & tanggal dari `created_at` (contoh: "Lapor: Hari ini, 09:30" atau "21 Nov 2026, 09:30").
  * Di Entity Room `TaskEntity` dan Model `Task`: buat properti `due_date: Long? = null` bersifat opsional (tidak wajib).
  * Pada tampilan kartu tiket di layar utama, tampilkan waktu lapor dan badge label berwarna (misal: "Wi-Fi", "Jaringan", "Urgent", "Server", "Maintenance").

=======================================================
2. DUKUNGAN TIKET TERENCANA (PEKERJAAN TERJADWAL UNTUK NANTI)
=======================================================
- Ada 2 jenis tiket di sistem:
  a. Tiket Insiden / Langsung: Dikerjakan hari ini (`scheduled_date_epoch_day = todayEpochDay`, `scheduled_time_minutes = null`).
  b. Pekerjaan Terencana: Dikerjakan pada tanggal masa depan tertentu (`scheduled_date_epoch_day = targetEpochDay`, `scheduled_time_minutes = minutesFromMidnight` misal 600 untuk 10:00 WIB).
- Di Form Tambah Tiket Cepat di Android:
  * Berikan pilihan: 🔘 "Kerjakan Hari Ini" atau 🔘 "Jadwalkan untuk Nanti".
  * Jika pilih "Jadwalkan untuk Nanti": Tampilkan pemilih tanggal (DatePicker) dan pemilih jam (TimePicker).
  * Simpan ke Room dan kirim ke server `scheduled_date_epoch_day` dan `scheduled_time_minutes`.

=======================================================
3. PENYELARASAN TANGGAL AGENDA 7 HARI (HARI INI & HARI TERENCANA)
=======================================================
- Di `ScheduleViewModel.kt` dan DAO:
  * Hitung epoch day hari ini secara presisi menggunakan zona waktu perangkat lokal:
    val todayEpochDay = java.time.LocalDate.now().toEpochDay()
  * Saat pertama kali buka aplikasi atau setelah login, strip 7 hari HARUS otomatis memilih `todayEpochDay` sebagai hari aktif default.
  * Query filter Room DAO untuk hari yang sedang diketuk/dipilih pada strip 7 hari:
    SELECT * FROM tasks WHERE scheduled_date_epoch_day = :selectedEpochDay ORDER BY 
      CASE WHEN scheduled_time_minutes IS NOT NULL THEN scheduled_time_minutes ELSE 9999 END ASC,
      CASE priority WHEN 'P1_CRITICAL' THEN 1 WHEN 'P2_HIGH' THEN 2 ELSE 3 END ASC,
      created_at DESC
  * Jika tiket memiliki `scheduled_time_minutes`: tampilkan slot jam di kartu (misal: "10:00 WIB").
  * Tiket terencana masa depan HANYA muncul ketika hari tersebut dipilih (tidak mengotori tab Hari Ini).

=======================================================
4. NOTIFIKASI ANDROID SAAT TEKNISI MENDAPAT TUGAS BARU
=======================================================
- Ketika aplikasi menerima data sinkronisasi dari server (via Auto-Sync / GET /api/tasks / SSE):
  * Dapatkan `currentUserId` dari akun teknisi yang sedang login (tersimpan di DataStore).
  * Periksa apakah ada tiket yang baru masuk di mana:
    task.assignedToId == currentUserId ATAU task.assignees.any { it.id == currentUserId }
  * Jika ditemukan tiket baru yang didelegasikan kepada teknisi ini, munculkan Notifikasi Android Sistem (NotificationManager):
    - Channel ID: "pingdulu_assigned_tasks"
    - Channel Name: "Penugasan Tiket IT"
    - Importance: NotificationManager.IMPORTANCE_HIGH
    - Judul: "🔔 Tugas Baru Didelegasikan!"
    - Pesan: "[${task.ticketCode}] ${task.title}"
    - Icon: Small icon notifikasi IT (ic_launcher / ic_notification)
    - Sound & Vibration: Aktifkan getar dan suara default
    - Intent: Buka MainActivity dan langsung scroll / buka detail tiket tersebut saat notifikasi diklik.

Berikan file kode lengkap yang diperbarui:
1. `app/src/main/java/com/example/util/NotificationHelper.kt` (Helper pengelola Notifikasi Android)
2. `app/src/main/java/com/example/presentation/schedule/ScheduleViewModel.kt` (Logika deteksi penugasan, sinkronisasi jadwal & set tanggal hari ini)
3. `app/src/main/java/com/example/presentation/schedule/components/TaskCardItem.kt` (Tampilan kartu tiket dengan badge Terjadwal, waktu lapor, dan slot jam)
4. `app/src/main/java/com/example/presentation/schedule/ScheduleScreen.kt` (Layar agenda 7 hari tersinkronisasi)
5. `app/src/main/java/com/example/presentation/schedule/components/QuickAddTaskBottomSheet.kt` (Form tambah tugas dengan pilihan Hari Ini vs Jadwalkan Nanti)
```
