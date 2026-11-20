# Analisis UI/UX Behance & Blueprint Adopsi ke Android PingDulu Field Ops

**Sumber Referensi Desain:** [Kanban - Task Management App UI by Jonathan Herlim (Behance)](https://www.behance.net/gallery/137617549/Kanban-Task-Management-App-UI)  
**Aplikasi Sasaran:** **PingDulu Field Ops (Android)**  
**Fokus:** Menggabungkan estetika modern Behance dengan kebutuhan riil teknisi lapangan (7-Day Schedule, Offline-First, Multi-Assignee).

---

## 1. Visual Showcase Referensi Behance

````carousel
![Hero Card & Workspace Overview - Behance Reference](/home/fajar/.gemini/antigravity/brain/9d7cdc4f-c6fa-43dc-aadc-a625d0c5eddb/verification/behance_module_0.png)
<!-- slide -->
![2-Column Kanban Grid & Status Pills - Behance Reference](/home/fajar/.gemini/antigravity/brain/9d7cdc4f-c6fa-43dc-aadc-a625d0c5eddb/verification/behance_module_1.png)
<!-- slide -->
![Typography, Color System & Mobile Ergonomics - Behance Reference](/home/fajar/.gemini/antigravity/brain/9d7cdc4f-c6fa-43dc-aadc-a625d0c5eddb/verification/behance_module_2.png)
````

---

## 2. Bedah DNA UI/UX Behance: Solusi Cerdas untuk Mobile Kanban

Desain karya Jonathan Herlim ini memberikan solusi brilian untuk **masalah fundamental Kanban di perangkat mobile**:

| Masalah Klasik Mobile Kanban | Solusi Inovatif pada Desain Behance |
| :--- | :--- |
| **Kolom horizontal menyempit** atau harus di-scroll menyamping terus-menerus sehingga tidak nyaman. | **Status kolom diubah menjadi *Filter Pills / Chips*** di bagian atas (`To Do`, `In Progress`, `Completed`), lalu kartu di bawahnya mengalir secara vertikal dalam **Grid 2-Kolom**. |
| **Kurang fokus pada pekerjaan yang sedang berlangsung.** | Disediakan **Hero Project Banner Card** besar di bagian paling atas dengan kontras warna kuat untuk menyorot tugas/pekerjaan yang sedang aktif. |
| **Kepadatan informasi rendah** (kartu mobile standar hanya muat 2-3 tugas per layar). | **2-Column Responsive Card Grid** memungkinkan **4 hingga 6 kartu tugas terlihat sekaligus** dalam satu layar HP tanpa terasa padat/berantakan. |
| **Jangkauan satu tangan (Thumb Zone) buruk.** | Aksi filter dan navigasi berada di area jangkauan jempol bawah dan tengah layar. |

---

## 3. Blueprint Penerapan ke Aplikasi Android PingDulu Field Ops

Berikut adalah pemetaan langsung bagaimana setiap elemen desain Behance kita adopsi dan sempurnakan untuk teknisi lapangan PingDulu:

```
+-------------------------------------------------------------------+
| [≡ Menu]   PingDulu Field Ops        [🔍]  [🔴 2]  [🟢 Fajar]     |
| "Selamat Pagi, Fajar" · 3 Tiket Hari Ini                          |
+-------------------------------------------------------------------+
| ┌───────────────────────────────────────────────────────────────┐ |
| │ ⭐ HERO ACTIVE JOB (TIKET AKTIF SAAT INI)                     │ |
| │ Background: Deep Cobalt Navy (#36459B), Radius 20dp           │ |
| │ [NET-1] Troubleshooting Wi-Fi Gedung B                        │ |
| │ ⏱️ Live Timer: 00:45:12  [ ⏸️ Jeda ]                          │ |
| │ 👥 Tim: [✓ Fajar] [B Budi] (Multi-Assignee Stacked Avatar)    │ |
| │ 🕒 Target SLA: 11:30 WIB | 📍 Ruang Server Gedung B          │ |
| │ ┌───────────────────────────────────────────────────────────┐ │ |
| │ │ Sub-panel Inset: ☑️ Checklist 3/5 Selesai · Rx -19 dBm    │ │ |
| │ └───────────────────────────────────────────────────────────┘ │ |
| └───────────────────────────────────────────────────────────────┘ |
+-------------------------------------------------------------------+
| 📅 JADWAL 7 HARI BERGULIR (7-DAY ROLLING HORIZON STRIP)           |
|  <<< [SEN 20]  [SEL 21]  >[RAB 22]<  [KAM 23]  [JUM 24]  [SAB 25]     |
|       🔴 1      🟡 2       🟢 3        ⚪ 0      🟡 1      ⚪ 0      |
+-------------------------------------------------------------------+
| 🏷️ STATUS KANBAN FILTER PILLS                                     |
|  [ Semua (4) ]  [ Backlog (1) ]  [ In Progress (1) ]  [ Done (2) ]|
+-------------------------------------------------------------------+
| 📦 GRID 2-KOLOM TIKET LAPANGAN (2-Column Kanban Grid)             |
| ┌───────────────────────────┐   ┌───────────────────────────┐     |
| │ [SRV-2]        Prioritas P1│   │ [HRD-5]        Prioritas P2│    |
| │ Maintenance Switch Core   │   │ Ganti Power Supply PC     │    |
| │ 🕒 13:00 WIB              │   │ 🕒 15:30 WIB              │    |
| │ 👥 [Fajar] +1             │   │ 👥 [Budi]                 │    |
| └───────────────────────────┘   └───────────────────────────┘     |
| ┌───────────────────────────┐   ┌───────────────────────────┐     |
| │ [ONB-7]        Prioritas P3│   │ [NET-9]            Selesai│    |
| │ Setup Laptop Karyawan     │   │ Tarik Kabel LAN Belden    │    |
| │ 🕒 16:30 WIB              │   │ ✅ Selesai 10:15 WIB      │    |
| │ 👥 [Siti]                 │   │ 👥 [Fajar]                │    |
| └───────────────────────────┘   └───────────────────────────┘     |
+-------------------------------------------------------------------+
| [Beranda / Agenda]   [Papan Tiket]   [Riwayat/SPK]   [Akun/Sync]  |
+-------------------------------------------------------------------+
```

---

## 4. Rincian Adaptasi Komponen

### A. Hero Card: "Tiket Aktif / Sedang Dikerjakan"
- Diambil dari konsep *Hero Project Card* Behance (warna biru gelap `#36459B` dengan teks putih tajam).
- **Fungsi untuk Teknisi:**  
  Begitu teknisi membuka aplikasi, mereka langsung melihat pekerjaan yang sedang berlangsung tanpa perlu mencari di daftar. Terdapat tombol play/pause timer langsung di kartu, penunjuk peta rute GPS, dan nama rekan teknisi (*multi-assignee*).

### B. Integrasi Strip Kalender 7 Hari + Status Filter Pills
- Tepat di bawah Hero Card, strip kalender 7 hari bergulir horizontal (Senin s/d Minggu) menampilkan beban kerja per hari dengan dot merah/amber/hijau.
- Di bawahnya terdapat pill status kanban bergaya Behance:
  - **Backlog / Antrean**: Soft Periwinkle `#9B9AE1`
  - **In Progress**: Sky Blue `#6DA7E8`
  - **Done / Selesai**: Mint Green `#52C58F`
- Memilih tanggal otomatis memfilter tugas pada hari itu, dan memilih pill status mempersempit tampilan statusnya.

### C. Kartu Grid 2-Kolom (Compact Field Card)
- Berbeda dengan tampilan desktop yang melebar, grid 2-kolom menghemat tinggi layar ponsel:
  - Sudut rounded halus (`16dp`) dan bayangan lembut (`elevation 2dp`).
  - Baris atas: Kode tiket (`NET-1`, `SRV-2`) dengan warna kategori + badge prioritas (`P1`, `P2`, `P3`).
  - Baris tengah: Judul tugas (2 baris dengan ellipsis jika panjang).
  - Baris bawah: Waktu janji temu / scheduled time di kiri bawah (`13:00 WIB`) dan avatar stacked teknisi di kanan bawah.
  - Menekan lama (*long press*) atau menekan tombol `≡` pada kartu membuka **Quick Action Bottom Sheet** (*Mulai Pengerjaan*, *Buka Rute Maps*, *WhatsApp Pelapor*, *Centang Cepat Checklist*).

### D. Palet Warna Khusus Lapangan (High-Contrast & Outdoor-Proof)

| Elemen UI | Kode Warna | Filosofi & Ergonomi |
| :--- | :--- | :--- |
| **Kanvas Latar Belakang** | `#F8FAFC` (Light Slate) / `#0F172A` (Dark Mode) | Bersih, mengurangi kelelahan mata di bawah terik matahari. |
| **Permukaan Kartu Tugas** | `#FFFFFF` (Light) / `#1E293B` (Dark) | Kontras tinggi agar teks mudah dibaca saat berjalan. |
| **Hero Active Job Card** | `#36459B` (Deep Cobalt Navy) | Memberikan hierarki visual terkuat: pekerjaan utama saat ini. |
| **Pill In Progress** | `#6DA7E8` (Sky Cerulean) | Menandakan status aktif yang bersahabat dan modern. |
| **Pill Completed** | `#52C58F` (Fresh Mint Green) | Konfirmasi kepuasan visual saat tugas telah tuntas. |
| **Badge Alert / P1** | `#EF4444` (Vibrant Crimson) | Menarik perhatian seketika jika ada insiden kritis yang perlu eskalasi. |

---

## 5. Keuntungan Signifikan untuk Teknisi Lapangan

1. **Efisiensi Penglihatan Sekilas (Under 2 Seconds Glancability):**  
   Teknisi cukup melirik layar ponsel selama 2 detik untuk mengetahui tiket yang sedang aktif, tugas berikutnya, dan beban jadwal hari-hari ke depan.
2. **Kerapian Layar Ponsel:**  
   Format grid 2-kolom memadatkan informasi tanpa membuat teks bertumpuk atau terpotong jelek.
3. **Ergonomi Operasional Satu Tangan:**  
   Semua kontrol penting (ganti hari, filter status, mulai timer, buka peta) berada di paruh bawah layar yang mudah dijangkau jempol.
