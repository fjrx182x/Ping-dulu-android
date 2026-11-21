# 📋 Laporan Forensik Flooding Sinkronisasi & Solusi Patch Android

## 1. Ringkasan Eksekutif
- **Status Database**: Seluruh kartu duplikat (**2.797 baris**) telah **dihapus bersih**.
- **Data Tersisa**: User (4 akun) dan Label (15 item) tetap utuh. `sqlite_sequence` direset sehingga tiket baru mulai dari nomor 1.
- **Kesimpulan Forensik**: **Terbukti 100% terjadi Infinite Sync Loop (Flooding)** dari aplikasi Android.

---

## 2. Bukti Forensik Data Database

Pengecekan log dan row database SQLite sebelum pembersihan:
| Judul Tiket Duplikat | Jumlah Baris | Rentang Waktu (WIB) | Frekuensi Pengiriman |
| :--- | :--- | :--- | :--- |
| `Penarikan Kabel lan` | **1.531 baris** | 13:23:29 - 13:59:09 | Setiap **1.1 - 1.4 detik** tanpa henti |
| `Narik Kabel Printer Ruang MTI` | **834 baris** | 12:39:03 - 12:44:16 | **2.6 request per detik** |
| `a` | **341 baris** | 13:14:22 - 13:20:47 | Setiap ~1.1 detik |
| `penarikan kabel LAN di Ruang Dosen` | **87 baris** | 13:18:55 - 13:20:47 | Setiap ~1.3 detik |

**Karakteristik Data:**
- Semua baris dibuat oleh akun **Fajar** (`user_id = 3`) via endpoint `POST /api/tasks`.
- Kolom `client_sync_id` bernilai `NULL` pada seluruh 2.797 baris.
- Terdapat indikator **`● Antre Sync (2)`** pada tangkapan layar Android, yang menandakan antrean lokal tidak pernah kosong.

---

## 3. Akar Masalah Teknis (Root Cause Analysis)

### A. Sisi Aplikasi Android (Pemicu Utama)
1. **Kegagalan Dequeue / Update Status Sync**:
   Ketika user membuat tiket di Android saat offline/online, tiket disimpan di database lokal (Room DB) dengan status `sync_status = "PENDING"` atau `is_synced = 0`.
2. **Infinite Sync Loop**:
   Background sync worker / Coroutine loop berjalan secara periodik (setiap ~1 detik) untuk mengecek item yang masih `PENDING` dan mengirimkannya via `POST /api/tasks`.
3. **Response Parsing Mismatch**:
   Server PingDulu merespons dengan format:
   ```json
   {
     "success": true,
     "task": {
       "id": 1,
       "title": "Penarikan Kabel lan",
       ...
     }
   }
   ```
   Jika Retrofit/Moshi/Gson di Android mengharapkan `TaskDto` langsung (bukan objek pembungkus `{ task, success }`), maka terjadi serialization error atau exception di baris callback success. Akibatnya:
   - Baris `taskDao.markAsSynced(taskId)` atau `syncQueueDao.delete(taskId)` **TIDAK PERNAH TERPANGGIL**.
   - Tiket tetap berstatus `PENDING` di HP.
   - Tampilan UI tetap menampilkan badge `● Antre Sync (2)`.
   - Pada tick berikutnya (1 detik kemudian), Android mengirim ulang payload yang sama persis. Hal ini berulang ribuan kali selama 35 menit.
4. **Tidak Mengirimkan Idempotency Key**:
   Android mengirim request tanpa parameter `client_sync_id` (UUID), sehingga server tidak dapat mendeteksi bahwa request tersebut adalah pengulangan dari tugas lokal yang sama.

### B. Sisi Server (Sebelum Perbaikan)
- Endpoint `POST /api/tasks` sebelumnya tidak melakukan pengecekan duplikasi waktu singkat (*time-window deduplication*), sehingga setiap request baru langsung diproses dengan `INSERT INTO work_items`.

---

## 4. Perlindungan yang Telah Diimplementasikan di Server

Kami telah menambahkan **Double-Layer Anti-Flooding Guard** di backend:

1. **`client_sync_id` Idempotency Guard**:
   Jika request menyertakan `client_sync_id` yang sudah tercatat di database, server langsung mengembalikan data tiket yang sudah ada (`200 OK`) tanpa membuat baris baru.
2. **Rapid-Duplicate Throttle (5 Detik)**:
   Jika ada request baru dengan `title` yang sama persis masuk dalam rentang waktu < 5 detik dari pembuatan tiket sebelumnya oleh user yang sama, server langsung mengembalikan tiket yang sudah dibuat (`200 OK, dedup: true`) dan memblokir duplikasi.
3. **Sync Fallback Matching**:
   Pada endpoint `POST /api/sync`, jika `client_sync_id` dan `id` kosong, server akan mencocokkan `title` dan `project_or_category` yang identik untuk melakukan `UPDATE`, bukan `INSERT`.

---

## 5. Prompt Patch untuk Google AI Studio (Perbaikan Android)

Salin prompt di bawah ini ke Google AI Studio untuk memperbaiki sinkronisasi di aplikasi Android:

```markdown
Perbaiki bug Infinite Loop Sync dan Flooding Duplikasi Tiket pada aplikasi Android Ping Dulu.

### MASALAH YANG DITEMUKAN:
1. Tiket yang dibuat di aplikasi Android mengirim POST /api/tasks ribuan kali (terjadi flooding hingga 2.797 duplikat di server).
2. Indikator di app tetap menunjukkan badge "● Antre Sync (2)", menandakan tiket di antrean lokal (Room DB) tidak pernah terhapus atau tidak berubah statusnya menjadi SYNCED.
3. Aplikasi tidak menyertakan `client_sync_id` (UUID) pada payload request.

### PERBAIKAN YANG HARUS DILAKUKAN:

1. Tambahkan `client_sync_id` unik berbasis UUID pada entity Room dan Request Body:
   ```kotlin
   // Saat membuat Task lokal:
   val clientSyncId = UUID.randomUUID().toString()
   val localTask = TaskEntity(
       ...,
       clientSyncId = clientSyncId,
       syncStatus = SyncStatus.PENDING
   )
   ```

2. Perbaiki Model Response Retrofit untuk `POST /api/tasks`:
   Server PingDulu mengembalikan JSON berformat:
   ```json
   {
       "success": true,
       "task": {
           "id": 12,
           "title": "...",
           "client_sync_id": "...",
           ...
       }
   }
   ```
   Pastikan Retrofit menggunakan data class pembungkus:
   ```kotlin
   data class CreateTaskResponse(
       val success: Boolean,
       val task: TaskDto
   )
   ```

3. Pada `TaskRepositoryImpl` / `SyncWorker`, tandai SYNCED segera setelah respons sukses diterima:
   ```kotlin
   try {
       val response = apiService.createTask(task.toCreateTaskRequest())
       if (response.isSuccessful && response.body()?.success == true) {
           val serverTask = response.body()!!.task
           // PENTING: Update ID dari server dan set syncStatus = SYNCED
           taskDao.updateAfterSync(
               localId = task.localId,
               serverId = serverTask.id,
               syncStatus = SyncStatus.SYNCED
           )
           // Hapus dari antrean sinkronisasi
           syncQueueDao.deleteByLocalId(task.localId)
       }
   } catch (e: Exception) {
       Log.e("SyncWorker", "Gagal sync task: ${e.message}")
   }
   ```

4. Pasang Debounce dan Backoff pada Sync Trigger:
   - Jangan menjalankan sync loop setiap 1 detik.
   - Gunakan `WorkManager` dengan `BackoffPolicy.EXPONENTIAL` (minimal backoff 15-30 detik jika gagal jaringan).
   - Pastikan Observer State/Flow tidak memanggil `triggerSync()` secara reaktif terus-menerus setiap kali Room DB mengalami update.
```
