# PROMPT PATCH GOOGLE AI STUDIO: NOTIFIKASI TUGAS, TANGGAL 7 HARI & WAKTU LAPOR

> **Target Aplikasi:** PingDulu Field Ops (Android Native Jetpack Compose)  
> **Server:** `https://ping.fjrx.my.id`  
> **Tujuan:** 
> 1. Menghilangkan field tenggat waktu (*due date*) dan menggantinya dengan waktu user melapor (*created_at*).
> 2. Menyelaraskan tanggal agenda 7 hari agar selalu tepat dengan tanggal riil hari ini (WIB / `LocalDate.now().toEpochDay()`), sehingga tugas langsung muncul di kolom Hari Ini.
> 3. Memunculkan notifikasi status bar Android bergetar & bersuara saat teknisi menerima delegasi tugas baru dari admin/rekan.

---

## 📋 Salin Prompt di Bawah Ini ke Google AI Studio

```text
Halo Gemini. Tolong perbarui kode aplikasi Android PingDulu Field Ops (Jetpack Compose) dengan 3 penyempurnaan utama berikut:

=======================================================
1. HAPUS TENGGAT WAKTU & GANTI DENGAN WAKTU USER MELAPOR
=======================================================
- Di seluruh tampilan Android (Card Tugas, Detail Dialog, Form Tambah Tugas):
  * Hapus input dan badge "Tenggat Waktu / Due Date".
  * Ganti dengan informasi "Waktu Lapor": format jam & tanggal dari `created_at` (contoh: "Hari ini, 02:15 WIB" atau "21 Nov 2026, 02:15").
  * Di Entity Room `TaskEntity` dan Model `Task`: buat properti `due_date: Long? = null` bersifat opsional (tidak wajib).
  * Pada tampilan kartu tiket di layar utama, tampilkan waktu lapor dan badge label berwarna (misal: "Wi-Fi", "Jaringan", "Urgent").

=======================================================
2. PENYELARASAN TANGGAL AGENDA 7 HARI (HARI INI / REAL DATE)
=======================================================
- Masalah sebelumnya: Tanggal tugas di agenda 7 hari tidak cocok dengan tanggal real di HP Android, sehingga tugas tidak muncul di jadwal depan/hari ini.
- Perbaikan di `ScheduleViewModel.kt` dan DAO:
  * Hitung epoch day hari ini secara presisi menggunakan zona waktu perangkat lokal:
    val todayEpochDay = java.time.LocalDate.now().toEpochDay()
  * Saat pertama kali buka aplikasi atau setelah login, strip 7 hari HARUS otomatis memilih `todayEpochDay` sebagai hari aktif default.
  * Query filter Room DAO untuk hari terpilih:
    SELECT * FROM tasks WHERE scheduled_date_epoch_day = :selectedEpochDay ORDER BY priority ASC, created_at DESC
  * Ketika teknisi membuat tugas baru dari Android: otomatis isi `scheduled_date_epoch_day = todayEpochDay`.

=======================================================
3. NOTIFIKASI ANDROID SAAT TEKNISI MENDAPAT TUGAS BARU
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
2. `app/src/main/java/com/example/presentation/schedule/ScheduleViewModel.kt` (Logika deteksi penugasan & set tanggal hari ini)
3. `app/src/main/java/com/example/presentation/schedule/components/TaskCardItem.kt` (Tampilan kartu tiket tanpa tenggat waktu, dengan waktu lapor & label)
4. `app/src/main/java/com/example/presentation/schedule/ScheduleScreen.kt` (Layar agenda 7 hari tersinkronisasi)
```
