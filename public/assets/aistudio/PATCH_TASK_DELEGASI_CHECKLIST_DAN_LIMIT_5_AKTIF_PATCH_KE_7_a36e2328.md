# MASTER PATCH KE-7 (ALL-IN-ONE): TASK POLICY, LIMIT 5 AKTIF (KALEM DULU!), CHECKLIST KONSISTEN & SPEED TEST ANDROID CBN

**Tanggal Rilis**: 22 November 2026  
**Versi Sistem**: PingDulu Ops Server v1.0.0 & Android Field Ops (Patch Ke-7 All-in-One)  
**File Tunggal Desktop**: `/home/fajar/Desktop/PATCH_TASK_DELEGASI_CHECKLIST_DAN_LIMIT_5_AKTIF_PATCH_KE_7.md`  

---

## 📌 PANDUAN RINGKAS: APA YANG HARUS ANDA LAKUKAN?

Dokumen ini menggabungkan seluruh perubahan sistem ke dalam **1 file tunggal**:

1. **Untuk Aplikasi Android (Field Ops)**:  
   👉 **Buka [BAGIAN 1](#bagian-1-prompt-siap-salin-ke-google-ai-studio-untuk-android-field-ops) di bawah ini**, lalu salin (*copy-paste*) blok prompt markdown ke **Google AI Studio** untuk meng-generate kode Kotlin (Speed Test CBN/Ookla + Dialog Kalem Dulu).
2. **Untuk Server & Web Dashboard**:  
   👉 **Sudah terpasang dan aktif 100% di server**. Anda tidak perlu melakukan apa-apa lagi pada server. Rincian teknis dan hasil ujinya dapat dibaca di **[BAGIAN 2](#bagian-2-laporan-dokumentasi-server--web-dashboard-status-aktif)**.

---

# BAGIAN 1: PROMPT SIAP SALIN KE GOOGLE AI STUDIO (UNTUK ANDROID FIELD OPS)

Salin seluruh teks di dalam blok di bawah ini ke **[Google AI Studio](https://aistudio.google.com/)** (Model: **Gemini 1.5 Pro** atau **Gemini 2.0 Pro Experimental**, Temperature: **0.2**):

````markdown
Halo Gemini. Tolong perbarui dan lengkapi aplikasi Android PingDulu Field Ops (Jetpack Compose) agar memiliki fitur Speed Test Mandiri dengan UI/UX identik dengan CBN Speedtest / Ookla (seperti di server PingDulu Web) serta mengadopsi aturan Task Policy Patch Ke-7 (Limit 5 Task Aktif "Kalem Dulu!", Hak Delegasi, dan Checklist).

Berikut adalah spesifikasi arsitektur dan implementasi teknis lengkap yang harus dibuat:

================================================================================
1. UI/UX SPEED TEST CBN / OOKLA STYLE DI JETPACK COMPOSE (CANVAS GAUGE)
================================================================================

A. KARAKTERISTIK GAUGE SPEEDOMETER:
   - Menggunakan Jetpack Compose `Canvas` dengan rasio aspek proporsional (tinggi ~260-280dp).
   - Arc busur lingkaran melengkung 266°:
     * Dalam koordinat Android Canvas (0° = jam 3):
       - Start angle: `137f` (posisi kiri bawah, setara 227° dari jam 12).
       - Sweep angle total: `266f` (berputar searah jarum jam hingga kanan bawah).
       - Titik puncak (jam 12): tepat berada di sudut `270f` (nilai 200 Mbps).
   - Dua lapisan busur (Stroke Width 18-20dp dengan Cap `StrokeCap.Butt` agar ujungnya rata dan rapi sesuai CBN):
     a. Background Track: Warna gelap slate (`Color(0xFF1E293B)` atau `#0F172A`).
     b. Active Blue Track: Warna Electric Cobalt Blue (`Color(0xFF2563EB)`) yang mengikuti pergerakan jarum secara sinkron.
   - 9 Skala Ticks & Label:
     * Nilai: `0, 10, 50, 100, 200, 300, 500, 750, 1g`
     * Mapping non-linear/logaritmik dari kecepatan Mbps ke sudut busur (`speedToSweepAngle(speed: Float): Float`).
   - Jarum Speedometer Tapered (Tanpa Donut Tengah):
     * Jarum meruncing dari pangkal lebar ke ujung tipis dengan warna gradien (`Brush.linearGradient`) dari transparan di pivot ke solid putih/slate di ujung jarum.
     * Diberi efek bayangan halus (*motion shadow*).
     * Rotasi jarum dianimasikan secara halus menggunakan `Animatable` dengan kurva redaman eksponensial / spring responsif tanpa jitter.
   - Area Angka Utama di Tengah Bawah:
     * Angka Mbps raksasa (Font size 44-48sp, FontWeight.Black, format `0.00` atau `00.00`).
     * Sub-label teks "Mbps" (Font size 13sp, slate-400).
     * Status bar kontekstual: "Mengukur Ping...", "Mengukur Unduh...", "Mengukur Unggah...", "Pengujian Selesai".

B. KARTU METRIK GLASSMORPHIC (CBN STYLE):
   - Grid 2x2 atau baris vertikal rapi:
     * ↔️ PING (ms)
     * ∿ JITTER (ms)
     * ↓ DOWNLOAD (Mbps) + Live Waveform Mini Sparkline
     * ↑ UPLOAD (Mbps) + Live Waveform Mini Sparkline
   - Mini Sparkline Area Chart:
     * Dibuat menggunakan `Canvas` kecil (lebar ~100-120dp, tinggi ~36dp).
     * Garis dasar putus-putus abu-abu jika belum ada data.
     * Saat pengujian berlangsung, render kurva grafik throughput yang terisi warna biru (`Color(0xFF2563EB)`) dengan opasitas 0.85 dan outline garis biru tegas.

C. STATUS BAR TAHAPAN PENGUJIAN (STEP PROGRESS):
   - 4 lingkaran indikator status: Ping ➔ Jitter ➔ Download ➔ Upload.
   - Lingkaran aktif menyala biru dengan ikon animasi/pulse saat sedang menguji tahap tersebut.
   - Lingkaran yang telah selesai menampilkan ikon centang hijau/biru.

D. FOOTER INFORMASI JARINGAN & TOMBOL AKSI:
   - Menampilkan IP Klien & ISP: contoh "Iconnet (82.158.129.243)" dan "CBN / PingDulu Jakarta (LAN)".
   - Tombol utama di bagian bawah layar:
     * State Normal: Tombol Lebar Berwarna Biru Gradient "Mulai Pengujian" (dengan ikon Play).
     * State Menguji: Tombol Berwarna Merah "Hentikan Pengujian" (dengan ikon Stop).

================================================================================
2. SPEED TEST ENGINE (COROUTINES + OKHTTP RETROFIT)
================================================================================

Hubungkan ke backend self-hosted PingDulu Server:
1. Ping & Jitter:
   - Kirim 10 request ringan berturut-turut ke `GET /api/speedtest/empty`.
   - Hitung Round-Trip Time (RTT) per request untuk mendapatkan Ping rata-rata.
   - Hitung variansi selisih antar request untuk mendapatkan Jitter.
2. Unduh (Download Throughput):
   - Buka koneksi streaming ke `GET /api/speedtest/garbage?ckSize=100`.
   - Baca stream `ResponseBody.byteStream()` dalam chunk 64KB.
   - Hitung throughput Mbps secara berkala (tiap 100ms) menggunakan jendela geser (*sliding window* 1-2 detik) agar nilainya stabil.
   - Simpan titik-titik sampel throughput ke array history untuk menggambar mini sparkline.
3. Unggah (Upload Throughput):
   - Kirim chunk acak ukuran 1MB ke `POST /api/speedtest/empty`.
   - Pantau byte yang terkirim tiap 100ms dan hitung Mbps secara real-time.
4. Deteksi IP Klien:
   - Panggil `GET /api/speedtest/ip` untuk mendapatkan IP publik/lokal perangkat.
5. Simpan Riwayat:
   - Saat selesai, panggil `POST /api/speedtest/records` dengan payload:
     `{ ping_ms, jitter_ms, download_mbps, upload_mbps, client_ip, isp_name, notes }`.

================================================================================
3. TASK POLICY & LIMIT 5 TASK AKTIF (KALEM DULU!)
================================================================================

1. ENFORCEMENT LIMIT 5 TASK AKTIF:
   - Aturan: Teknisi dibatasi maksimal memegang 5 task aktif (`stage != 'DONE'`).
   - Ketika teknisi mengklik "Ambil Task" pada tiket, atau admin/teknisi meng-assign teknisi lain:
     * Jika server mengembalikan HTTP `409 Conflict`, tangkap response JSON:
       ```json
       {
         "error": "Kalem dulu! Kamu punya 5 task aktif nih. Selesaikan dulu minimal 1 task",
         "limit": 5,
         "active_count": 5,
         "type": "CLAIM_LIMIT_EXCEEDED"
       }
       ```
     * Tampilkan Composable `KalemDuluDialog` dengan spesifikasi:
       - Background gelap (`#0F172A`) dengan border amber menyala (`#F59E0B`).
       - Badge emoji ✋ dalam lingkaran amber.
       - Judul: "Kalem Dulu!"
       - Badge sub-judul: "BATAS MAKSIMAL 5 TASK AKTIF".
       - Kotak teks peringatan menampilkan teks eksak dari server:
         * Sisi delegasi: `"Kalem Dulu! Ada 5 task yang aktif. selesaikan dulu minimal 1 task"`
         * Sisi ambil sendiri: `"Kalem dulu! Kamu punya 5 task aktif nih. Selesaikan dulu minimal 1 task"`
       - Penjelasan singkat edukasi beban kerja.
       - Tombol "Siap, Mengerti" berwarna kuning/amber untuk menutup dialog.

2. KONSISTENSI CHECKLIST & HAK DELEGASI:
   - Di `TaskDetailViewModel` dan Room Database:
     * Saat status tiket diubah atau tiket diambil (`/claim`), jangan pernah menimpa checklist lokal jika payload server kosong.
     * Izinkan teknisi yang ditugaskan untuk menambahkan rekan teknisi lain melalui picker `+ Tambah Teknisi`.
     * Izinkan teknisi yang ditugaskan untuk menambah sub-langkah checklist baru di `TaskDetailScreen` via endpoint `POST /api/tasks/{id}/checklist`.

================================================================================
KODE LENGKAP YANG HARUS DIHASILKAN
================================================================================

Tolong tuliskan kode lengkap untuk file-file berikut:
1. `app/src/main/java/com/example/presentation/speedtest/SpeedtestScreen.kt` (UI Compose Canvas 270° & Sparkline)
2. `app/src/main/java/com/example/presentation/speedtest/SpeedtestViewModel.kt` (Coroutines Network Engine & Math)
3. `app/src/main/java/com/example/data/remote/SpeedtestApiService.kt` (Retrofit Interface)
4. `app/src/main/java/com/example/presentation/common/KalemDuluDialog.kt` (Dialog Amber Glow Kalem Dulu)
5. `app/src/main/java/com/example/presentation/taskdetail/TaskDetailScreen.kt` (Penanganan Delegasi & Checklist)
````

---

# BAGIAN 2: LAPORAN DOKUMENTASI SERVER & WEB DASHBOARD (STATUS: AKTIF)

Perubahan pada backend Fastify dan frontend Web Dashboard telah selesai diimplementasikan, diuji secara menyeluruh, dan aktif di container Docker `pingdulu-server`:

### 1. Konsistensi Checklist Saat Task Diambil
- **Masalah Sebelumnya**: Pada endpoint `/api/tasks/:id/claim` dan `/work`, server mengembalikan objek task tanpa menyertakan array relasi `checklist`, menyebabkan `TaskDetailModal` mereset checklist lokal menjadi kosong.
- **Solusi**: Dibuat helper backend `populateTaskDetails(task)` yang secara otomatis melampirkan `checklist`, `assignees`, `logs`, `attachments`, `links`, `comments`, dan `labels` pada setiap respon task dan siaran real-time SSE (`task:updated`, `task:stage_changed`).

### 2. Hak Delegasi Teknisi
- Teknisi yang telah didelegasikan atau mengambil suatu tiket kini diizinkan mendelegasikan/meng-assign teknisi lain melalui endpoint `PATCH /api/tasks/:id`.

### 3. Hak Menambah Checklist oleh Teknisi Tertugaskan
- Teknisi yang memegang tiket dapat menambah sub-langkah checklist (`POST /api/tasks/:id/checklist`), memperbarui status (`PATCH`), dan melakukan sinkronisasi batch (`PUT /api/tasks/:id/checklist`).

### 4. Limit 5 Task Aktif & Peringatan Kalem Dulu!
- Beban kerja aktif (`stage != 'DONE'`) dibatasi maksimal 5 task per teknisi.
- Jika limit terlampaui saat claim atau delegasi, server merespon dengan status HTTP `409 Conflict`.
- Frontend menampilkan modal interaktif berdesain amber glow: [KalemDuluModal.tsx](file:///home/fajar/pingdulu-server/client/src/components/KalemDuluModal.tsx).
  - Teks sisi delegasi: `"Kalem Dulu! Ada 5 task yang aktif. selesaikan dulu minimal 1 task"`
  - Teks sisi teknisi ambil sendiri: `"Kalem dulu! Kamu punya 5 task aktif nih. Selesaikan dulu minimal 1 task"`

### 5. Hasil Pengujian & Verifikasi
- **Automated Test Suite**: **57 dari 57 tes lulus 100% (0 fail, 0 error)** (`tests/active_limit_and_delegation.test.ts` dan seluruh test suite).
- **Verifikasi UI Browser**:
  - Modal Kalem Dulu: [kalem_dulu_modal_verified.png](file:///home/fajar/.gemini/antigravity/brain/9d7cdc4f-c6fa-43dc-aadc-a625d0c5eddb/verification/kalem_dulu_modal_verified.png)
  - Penambahan Checklist: [task_detail_checklist_verified.png](file:///home/fajar/.gemini/antigravity/brain/9d7cdc4f-c6fa-43dc-aadc-a625d0c5eddb/verification/task_detail_checklist_verified.png)
- **Status Database**: Bersih total (`work_items: 0`, `speedtest_records: 0`, `checklist_steps: 0`, `activity_logs: 0`).

---
*Dokumentasi All-in-One ini disiapkan untuk sinkronisasi menyeluruh antara PingDulu Server dan PingDulu Field Ops Android.*
