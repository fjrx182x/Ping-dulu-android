# MASTER PATCH KE-8: PROFIL TEKNISI, AVATAR UPLOAD (10MB), DEPARTEMEN & BAGIAN, STATUS AKUN (IS_ACTIVE), SERTA REAL DUAL-MODE SPEEDTEST (EDGE 100G & DETEKSI ISP RIIL) UNTUK PINGDULU ANDROID

**Tanggal Rilis**: 23 November 2026  
**Versi Sistem**: PingDulu Ops Server v1.0.0 & Android Field Ops (Patch Ke-8)  
**File Tunggal Desktop**: `/home/fajar/Desktop/PATCH_PROFILE_AVATAR_DAN_USER_MANAGEMENT_PATCH_KE_8.md`  

---

## 📌 PANDUAN RINGKAS: APA YANG HARUS ANDA LAKUKAN?

1. **Untuk Aplikasi Android (Field Ops)**:  
   👉 **Buka [BAGIAN 1](#bagian-1-prompt-siap-salin-ke-google-ai-studio-untuk-android-field-ops) di bawah ini**, lalu salin (*copy-paste*) blok prompt markdown ke **[Google AI Studio](https://aistudio.google.com/)** (Gunakan Model: **Gemini 1.5 Pro** atau **Gemini 2.0 Pro Experimental**, Temperature: **0.2**).
2. **Untuk Server & Web Dashboard**:  
   👉 **Sudah terpasang, teruji 100%, dan aktif di server**. Rincian API Contract dan perubahannya dapat dibaca di **[BAGIAN 2](#bagian-2-dokumentasi-arsitektur-dan-api-contract-server)**.

---

# BAGIAN 1: PROMPT SIAP SALIN KE GOOGLE AI STUDIO (UNTUK ANDROID FIELD OPS)

Salin seluruh teks di dalam blok di bawah ini ke **Google AI Studio**:

````markdown
Halo Gemini. Tolong perbarui dan lengkapi aplikasi Android PingDulu Field Ops (Kotlin + Jetpack Compose) agar mengadopsi spesifikasi **Patch Ke-8: Profil Teknisi, Upload Foto Avatar (Maks. 10 MB), Informasi Departemen & Bagian, Proteksi Status Akun (is_active), serta Fitur Real Dual-Mode Speedtest (Edge 100G & Deteksi ISP Riil Dinamis)** yang sudah aktif di PingDulu Server.

Berikut adalah spesifikasi arsitektur, data model, networking, dan implementasi Jetpack Compose lengkap yang harus dibuat:

================================================================================
1. DATA MODEL & ROOM / DATASTORE UPDATES
================================================================================

Perbarui model `User` dan `TaskAssignee` di paket data Android Anda:

```kotlin
package com.pingdulu.ops.data.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("role") val role: String, // "admin", "technician", "viewer"
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("sub_department") val subDepartment: String? = null,
    @SerializedName("is_active") val isActive: Int = 1,
    @SerializedName("created_at") val createdAt: Long? = null,
    @SerializedName("updated_at") val updatedAt: Long? = null
) {
    val isUserActive: Boolean get() = isActive == 1
    val isTechnician: Boolean get() = role == "technician" || role == "admin"
    val isAdmin: Boolean get() = role == "admin"
    
    // Helper format departemen & bagian (contoh: "Network Operations • Fiber Optic")
    val departmentDisplay: String?
        get() {
            val list = listOfNotNull(department?.trim(), subDepartment?.trim()).filter { it.isNotEmpty() }
            return if (list.isNotEmpty()) list.joinToString(" • ") else null
        }
}

data class TaskAssignee(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("role") val role: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("sub_department") val subDepartment: String? = null,
    @SerializedName("is_active") val isActive: Int = 1
)
```

Simpan seluruh field profil ini ke `UserPreferencesRepository` (DataStore Preferences) saat login berhasil, sehingga ketika aplikasi dibuka saat offline, nama teknisi, foto, dan departemen tetap dapat ditampilkan.

================================================================================
2. AUTHENTICATION & SOFT DEACTIVATION GUARD (IS_ACTIVE = 0)
================================================================================

Ketika teknisi mencoba login (`POST /api/auth/login`):
1. Jika akun dinonaktifkan oleh Administrator di Server (`is_active = 0`), server akan merespons dengan **HTTP 403 Forbidden** dan JSON:
   ```json
   {
     "error": "Akun Anda dinonaktifkan. Silakan hubungi administrator."
   }
   ```
2. Tangani HTTP 403 ini di `AuthRepository` dan `LoginViewModel`:
   - Jangan simpan token ke DataStore.
   - Tampilkan Dialog Peringatan atau Snackbar berwarna Rose/Merah yang jelas: `"Akun Anda telah dinonaktifkan oleh administrator. Silakan hubungi tim IT/Admin."`.
3. Jika saat aplikasi sedang berjalan teknisi menerima error 403 atau saat sync background mendeteksi `is_active == 0`:
   - Hapus session lokal secara aman.
   - Arahkan teknisi kembali ke `LoginScreen` dengan pesan akun dinonaktifkan.

================================================================================
3. RETROFIT API SERVICE & AVATAR UPLOAD ENGINE
================================================================================

Tambahkan endpoint berikut ke interface Retrofit `PingDuluApiService.kt`:

```kotlin
package com.pingdulu.ops.data.api

import com.pingdulu.ops.data.model.User
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface PingDuluApiService {

    // 1. Get My Profile (Ambil profil segar teknisi saat online)
    @GET("api/auth/me")
    suspend fun getMyProfile(): Response<AuthMeResponse>

    // 2. Upload Avatar Foto Profil (Maks. 10 MB)
    @Multipart
    @POST("api/users/{id}/avatar")
    suspend fun uploadAvatar(
        @Path("id") userId: Long,
        @Part file: MultipartBody.Part
    ): Response<AvatarUploadResponse>

    // 3. Hapus Avatar Foto Profil
    @DELETE("api/users/{id}/avatar")
    suspend fun deleteAvatar(
        @Path("id") userId: Long
    ): Response<UserResponse>

    // 4. Update Profil / Ubah Password Mandiri
    @PATCH("api/users/{id}")
    suspend fun updateProfile(
        @Path("id") userId: Long,
        @Body request: UpdateUserRequest
    ): Response<UserResponse>

    // 5. Daftar Seluruh Pengguna (Hanya teknisi aktif yang ditampilkan di dropdown tugas)
    @GET("api/users")
    suspend fun getUsers(): Response<UsersListResponse>
}

data class AuthMeResponse(
    val success: Boolean,
    val user: User
)

data class AvatarUploadResponse(
    val success: Boolean,
    val avatar_url: String,
    val message: String
)

data class UserResponse(
    val success: Boolean,
    val user: User,
    val message: String?
)

data class UsersListResponse(
    val success: Boolean,
    val users: List<User>
)

data class UpdateUserRequest(
    val display_name: String? = null,
    val password: String? = null,
    val department: String? = null,
    val sub_department: String? = null
)
```

Helper untuk mengonversi `avatar_url` relatif (contoh: `/api/avatars/avatar_1_1795372899.png`) menjadi URL lengkap:
```kotlin
fun resolveAvatarUrl(serverBaseUrl: String, avatarUrl: String?): String? {
    if (avatarUrl.isNullOrBlank()) return null
    if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) return avatarUrl
    val cleanBase = serverBaseUrl.trimEnd('/')
    val cleanPath = avatarUrl.trimStart('/')
    return "$cleanBase/$cleanPath"
}
```

================================================================================
4. ENGINE MULTIPART UPLOAD DENGAN VALIDASI MAKS. 10 MB
================================================================================

Buat helper fungsi Kotlin di `FileRepository` atau `ImageUploadHelper.kt`:

```kotlin
package com.pingdulu.ops.util

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

object ImageUploadHelper {

    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB

    fun prepareAvatarPart(context: Context, imageUri: Uri): Result<MultipartBody.Part> {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"

            // Validasi format
            if (!mimeType.startsWith("image/")) {
                return Result.failure(IllegalArgumentException("Hanya file gambar (JPEG, PNG, WebP) yang diperbolehkan."))
            }

            // Copy URI content ke cache file lokal
            val tempFile = File(context.cacheDir, "upload_avatar_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(IllegalStateException("Gagal membaca file gambar."))

            // Validasi ukuran maksimal 10 MB
            if (tempFile.length() > MAX_FILE_SIZE_BYTES) {
                val sizeMb = String.format("%.1f", tempFile.length() / (1024f * 1024f))
                tempFile.delete()
                return Result.failure(IllegalArgumentException("Ukuran gambar ($sizeMb MB) melebihi batas maksimal 10 MB."))
            }

            val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", tempFile.name, requestBody)
            Result.success(part)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

================================================================================
5. JETPACK COMPOSE UI: PROFILE SCREEN & AVATAR COMPONENT
================================================================================

Gunakan library **Coil** (`io.coil-kt:coil-compose:2.6.0`) untuk memuat gambar avatar secara efisien.

A. KOMPONEN AVATAR REUSABLE (`UserAvatar.kt`):
```kotlin
package com.pingdulu.ops.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String,
    size: Dp = 44.dp,
    isActive: Boolean = true,
    showActiveBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Foto $displayName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )
        } else {
            // Inisial Nama dengan latar belakang biru gradien
            val initial = displayName.trim().take(1).uppercase().ifEmpty { "U" }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF4F46E5))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp
                )
            }
        }

        // Active status dot indicator
        if (showActiveBadge) {
            val badgeSize = (size.value * 0.28f).dp
            val dotColor = if (isActive) Color(0xFF10B981) else Color(0xFF94A3B8)
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(1.5.dp, Color(0xFF0F172A), CircleShape)
            )
        }
    }
}
```

B. LAYAR PROFILE & EDIT AVATAR (`ProfileScreen.kt`):
Implementasikan layar profil teknisi dengan spesifikasi visual berikut:
1. **Header Profil**:
   - Avatar besar ukuran 100-110dp di tengah.
   - Tombol overlay kamera / ikon `Upload` di bawah avatar untuk membuka Photo Picker Android:
     ```kotlin
     val photoPickerLauncher = rememberLauncherForActivityResult(
         contract = ActivityResultContracts.PickVisualMedia()
     ) { uri ->
         if (uri != null) {
             viewModel.uploadAvatar(context, uri)
         }
     }
     ```
   - Di bawah tombol upload terdapat keterangan *"Maksimal 10 MB (JPEG, PNG, WebP)"*.
   - Jika teknisi sudah memiliki foto avatar, sediakan tombol teks merah kecil *"Hapus Foto"*.
2. **Kartu Informasi Teknisi**:
   - Nama Lengkap (Font bold, text size 20sp).
   - `@username` (Font mono text-slate-400).
   - Pill status akun: `● Aktif` (hijau terang) atau `○ Nonaktif` (abu-abu).
   - Badge Role: `Administrator` (latar merah/rose) atau `technician` (latar biru kobalt).
   - Kartu Departemen & Bagian:
     * Menampilkan Departemen: contoh `"IT Network Operations"`.
     * Menampilkan Bagian (Sub Departemen): contoh `"Fiber Optic & OSP"`.
3. **Form Ubah Password Mandiri**:
   - Field password baru dengan tombol show/hide eye toggle.
   - Tombol *"Simpan Perubahan"* dengan loading indicator saat request berlangsung.

================================================================================
6. INTEGRASI PADA TOP BAR & TASK LIST (FIELD OPS)
================================================================================

1. **Top Bar Header**:
   - Di pojok kanan atas layar utama, gantikan inisial statis dengan `UserAvatar(avatarUrl = user.avatarUrl, displayName = user.displayName, size = 36.dp, isActive = true)`.
   - Mengklik avatar membuka `ProfileScreen`.
2. **Filter & Penugasan Teknisi (Multi-Assignee)**:
   - Pada dropdown/dialog penugasan tiket (`+ Tambah Teknisi`):
     * Filter daftar pengguna agar **hanya menampilkan teknisi yang aktif** (`user.isActive == 1`).
     * Tampilkan avatar kecil, nama lengkap, dan departemen di tiap baris pilihan teknisi.

================================================================================
7. FITUR DUAL-MODE REAL SPEEDTEST (EDGE 100G & SERVER LOKAL) DENGAN DETEKSI ISP RIIL
================================================================================

Agar teknisi yang sedang berada di kantor cabang, lapangan, atau lokasi mana pun (misal di jaringan Iconnet, IndiHome, Biznet, Amikom, kampus, wifi publik) dapat mengukur **kecepatan riil internet broadband** mereka tanpa terhambat oleh batas bandwidth uplink server PingDulu lokal, buatkan modul Speedtest Dual-Mode di aplikasi Android:

### A. Deteksi ISP Riil, Lokasi ("Dimana"), dan IP (Bukan Hardcoding)
Jangan pernah menggunakan string default hardcoded ("Iconnet", "82.158.129.243", dll.). Gunakan deteksi dinamis saat layar dibuka:

```kotlin
package com.pingdulu.ops.data.speedtest

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ClientNetworkInfo(
    val ip: String,
    val isp: String,
    val location: String
)

class RealIspDetector(private val client: OkHttpClient) {
    suspend fun detect(): ClientNetworkInfo = withContext(Dispatchers.IO) {
        // Strategi 1: ipwho.is (Dual-stack IPv4/IPv6, responsif, CORS & JSON lengkap)
        try {
            val req = Request.Builder().url("https://ipwho.is/").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    val json = org.json.JSONObject(body ?: "{}")
                    if (json.optBoolean("success", false)) {
                        val ip = json.optString("ip", "127.0.0.1")
                        val conn = json.optJSONObject("connection")
                        val isp = conn?.optString("isp") ?: conn?.optString("org") ?: "ISP Publik"
                        val city = json.optString("city", "")
                        val country = json.optString("country_code", json.optString("country", "ID"))
                        val loc = listOf(city, country).filter { it.isNotEmpty() }.joinToString(", ")
                        return@withContext ClientNetworkInfo(ip, isp, if (loc.isNotEmpty()) loc else "Indonesia")
                    }
                }
            }
        } catch (e: Exception) {
            // Lanjut ke fallback
        }

        // Strategi 2: free.freeipapi.com (Fallback handal)
        try {
            val req = Request.Builder().url("https://free.freeipapi.com/api/v1/json").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    val json = org.json.JSONObject(body ?: "{}")
                    val ip = json.optString("ipAddress", "127.0.0.1")
                    val isp = json.optString("asnOrganization", "ISP Publik")
                    val city = json.optString("cityName", json.optString("regionName", ""))
                    val country = json.optString("countryCode", "ID")
                    val loc = listOf(city, country).filter { it.isNotEmpty() }.joinToString(", ")
                    return@withContext ClientNetworkInfo(ip, isp, if (loc.isNotEmpty()) loc else "Indonesia")
                }
            }
        } catch (e: Exception) {
            // Fallback terakhir
        }

        ClientNetworkInfo("127.0.0.1", "ISP Publik", "Indonesia")
    }
}
```

### B. Dual-Mode Server Target Selector
Sediakan pemilih target di bagian atas Speedtest:
1. **Mode `⚡ Internet Publik (Edge 100G - Riil)`** *(Default)*:
   - Terkoneksi langsung ke Cloudflare Anycast Edge Jakarta (peering langsung 100+ Gbps dengan OpenIXP, APJII, CDIX ke semua ISP).
   - Menghasilkan kecepatan unduh dan unggah riil sesuai paket internet di lokasi testing.
   - 0% beban bandwidth dan CPU pada server PingDulu.
2. **Mode `🏢 Server PingDulu (Lokal LAN)`**:
   - Terkoneksi langsung ke server lokal PingDulu (`/api/speedtest/garbage` & `/api/speedtest/empty`) untuk uji link LAN/intranet.

### C. Multi-Stream Speedtest Engine (OkHttp Coroutines)
```kotlin
package com.pingdulu.ops.data.speedtest

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.*
import java.io.IOException

enum class TestPhase { IDLE, STARTING, PING, DOWNLOAD, UPLOAD, DONE, ABORTED }

data class SpeedtestState(
    val phase: TestPhase = TestPhase.IDLE,
    val progress: Int = 0,
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val currentSpeedGauge: Double = 0.0,
    val dlHistory: List<Double> = emptyList(),
    val ulHistory: List<Double> = emptyList(),
    val clientInfo: ClientNetworkInfo? = null
)

class PublicEdgeSpeedtestEngine(private val client: OkHttpClient) {
    private var job: Job? = null

    fun startTest(
        scope: CoroutineScope,
        onUpdate: (SpeedtestState) -> Unit
    ) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            var state = SpeedtestState(phase = TestPhase.STARTING, progress = 5)
            onUpdate(state)

            // 1. Deteksi Klien & ISP Riil
            val info = RealIspDetector(client).detect()
            state = state.copy(clientInfo = info)
            onUpdate(state)

            // 2. Ping & Jitter Test (8 request)
            state = state.copy(phase = TestPhase.PING)
            val rtts = mutableListOf<Double>()
            for (i in 0 until 8) {
                if (!isActive) return@launch
                val t0 = System.nanoTime()
                try {
                    val req = Request.Builder()
                        .url("https://speed.cloudflare.com/__down?bytes=0&r=${System.currentTimeMillis()}")
                        .build()
                    client.newCall(req).execute().close()
                    val rtt = (System.nanoTime() - t0) / 1_000_000.0
                    rtts.add(rtt)
                    val avgPing = rtts.average()
                    var jitter = 0.0
                    for (j in 1 until rtts.size) {
                        jitter += Math.abs(rtts[j] - rtts[j - 1])
                    }
                    val avgJitter = if (rtts.size > 1) jitter / (rtts.size - 1) else 0.0
                    state = state.copy(
                        pingMs = avgPing,
                        jitterMs = avgJitter,
                        progress = 10 + (i + 1) * 2
                    )
                    onUpdate(state)
                } catch (e: Exception) {}
                delay(60)
            }

            // 3. Multi-Stream Download Test (4 Parallel Coroutines selama 7 detik)
            state = state.copy(phase = TestPhase.DOWNLOAD, progress = 25)
            onUpdate(state)

            val dlStartTime = System.currentTimeMillis()
            val dlDurationMs = 7000L
            var totalDlBytes = 0L
            val dlSamples = mutableListOf<Pair<Long, Long>>() // timestamp, totalBytes

            // Updater Flow tiap 100ms
            val dlTicker = launch {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val elapsed = now - dlStartTime
                    val windowThreshold = now - 1000
                    val recent = synchronized(dlSamples) {
                        dlSamples.filter { it.first >= windowThreshold }
                    }
                    var curMbps = 0.0
                    if (recent.size >= 2) {
                        val dt = (recent.last().first - recent.first().first) / 1000.0
                        val dBytes = recent.last().second - recent.first().second
                        if (dt > 0.1) curMbps = (dBytes * 8.0) / (dt * 1_000_000.0)
                    } else if (elapsed > 200) {
                        curMbps = (totalDlBytes * 8.0) / ((elapsed / 1000.0) * 1_000_000.0)
                    }
                    val prog = 25 + ((elapsed.toFloat() / dlDurationMs) * 35).toInt().coerceIn(0, 35)
                    val history = (state.dlHistory + curMbps).takeLast(40)
                    state = state.copy(
                        downloadMbps = curMbps,
                        currentSpeedGauge = curMbps,
                        dlHistory = history,
                        progress = prog
                    )
                    onUpdate(state)
                    delay(100)
                }
            }

            // 4 parallel stream downloads
            val dlJobs = (0 until 4).map {
                launch {
                    val buffer = ByteArray(32 * 1024)
                    while (isActive && System.currentTimeMillis() - dlStartTime < dlDurationMs) {
                        try {
                            val req = Request.Builder()
                                .url("https://speed.cloudflare.com/__down?bytes=20000000&r=${System.currentTimeMillis()}")
                                .build()
                            client.newCall(req).execute().use { resp ->
                                val stream = resp.body?.byteStream() ?: return@use
                                var read: Int
                                while (isActive && System.currentTimeMillis() - dlStartTime < dlDurationMs) {
                                    read = stream.read(buffer)
                                    if (read == -1) break
                                    synchronized(dlSamples) {
                                        totalDlBytes += read
                                        dlSamples.add(System.currentTimeMillis() to totalDlBytes)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            delay(100)
                        }
                    }
                }
            }
            delay(dlDurationMs)
            dlJobs.forEach { it.cancel() }
            dlTicker.cancel()

            // Hitung kecepatan akhir unduh yang stabil
            val steadyDl = dlSamples.filter { it.first >= dlStartTime + 1000 }
            val finalDl = if (steadyDl.size >= 2) {
                val dt = (steadyDl.last().first - steadyDl.first().first) / 1000.0
                val db = steadyDl.last().second - steadyDl.first().second
                if (dt > 0.4) (db * 8.0) / (dt * 1_000_000.0) else (totalDlBytes * 8.0) / 7_000_000.0
            } else (totalDlBytes * 8.0) / 7_000_000.0
            state = state.copy(downloadMbps = finalDl, currentSpeedGauge = 0.0)
            onUpdate(state)

            delay(200)

            // 4. Multi-Stream Upload Test (3 Parallel OkHttp streaming POST selama 7 detik)
            state = state.copy(phase = TestPhase.UPLOAD, progress = 60)
            onUpdate(state)

            val ulStartTime = System.currentTimeMillis()
            val ulDurationMs = 7000L
            var totalUlBytes = 0L
            val ulSamples = mutableListOf<Pair<Long, Long>>()
            val chunk512k = ByteArray(512 * 1024) { (it and 0xff xor 0x5a).toByte() }

            val ulTicker = launch {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val elapsed = now - ulStartTime
                    val windowThreshold = now - 1000
                    val recent = synchronized(ulSamples) {
                        ulSamples.filter { it.first >= windowThreshold }
                    }
                    var curMbps = 0.0
                    if (recent.size >= 2) {
                        val dt = (recent.last().first - recent.first().first) / 1000.0
                        val dBytes = recent.last().second - recent.first().second
                        if (dt > 0.1) curMbps = (dBytes * 8.0) / (dt * 1_000_000.0)
                    } else if (elapsed > 200) {
                        curMbps = (totalUlBytes * 8.0) / ((elapsed / 1000.0) * 1_000_000.0)
                    }
                    val prog = 60 + ((elapsed.toFloat() / ulDurationMs) * 40).toInt().coerceIn(0, 40)
                    val history = (state.ulHistory + curMbps).takeLast(40)
                    state = state.copy(
                        uploadMbps = curMbps,
                        currentSpeedGauge = curMbps,
                        ulHistory = history,
                        progress = prog
                    )
                    onUpdate(state)
                    delay(100)
                }
            }

            val ulJobs = (0 until 3).map {
                launch {
                    while (isActive && System.currentTimeMillis() - ulStartTime < ulDurationMs) {
                        try {
                            val requestBody = object : RequestBody() {
                                override fun contentType(): MediaType? = MediaType.parse("application/octet-stream")
                                override fun contentLength(): Long = chunk512k.size.toLong()
                                override fun writeTo(sink: BufferedSink) {
                                    val buffer = Buffer().write(chunk512k)
                                    var written = 0L
                                    while (isActive && written < chunk512k.size) {
                                        val toWrite = Math.min(16384L, chunk512k.size - written)
                                        sink.write(buffer, toWrite)
                                        written += toWrite
                                        synchronized(ulSamples) {
                                            totalUlBytes += toWrite
                                            ulSamples.add(System.currentTimeMillis() to totalUlBytes)
                                        }
                                    }
                                }
                            }
                            val req = Request.Builder()
                                .url("https://speed.cloudflare.com/__up")
                                .post(requestBody)
                                .build()
                            client.newCall(req).execute().close()
                        } catch (e: Exception) {
                            delay(100)
                        }
                    }
                }
            }
            delay(ulDurationMs)
            ulJobs.forEach { it.cancel() }
            ulTicker.cancel()

            val steadyUl = ulSamples.filter { it.first >= ulStartTime + 1000 }
            val finalUl = if (steadyUl.size >= 2) {
                val dt = (steadyUl.last().first - steadyUl.first().first) / 1000.0
                val db = steadyUl.last().second - steadyUl.first().second
                if (dt > 0.4) (db * 8.0) / (dt * 1_000_000.0) else (totalUlBytes * 8.0) / 7_000_000.0
            } else if (totalUlBytes > 0) (totalUlBytes * 8.0) / 7_000_000.0 else 0.0

            state = state.copy(
                phase = TestPhase.DONE,
                progress = 100,
                uploadMbps = finalUl,
                currentSpeedGauge = finalDl // Jarum berhenti di kecepatan unduh
            )
            onUpdate(state)
        }
    }

    fun stopTest() {
        job?.cancel()
        job = null
    }
}
```

### D. Tampilan Kartu Device Klien di Android (Jetpack Compose)
Pada kartu info di bawah gauge dial:
- **Icon**: HP / Laptop Android.
- **Baris 1**: Menampilkan Nama ISP Riil dan Lokasi Kota:
  `Text("${info?.isp ?: "Mendeteksi..."} • ${info?.location ?: ""}", fontWeight = FontWeight.Bold)`
- **Baris 2**: Menampilkan Alamat IP Riil:
  `Text(info?.ip ?: "127.0.0.1", fontFamily = FontFamily.Monospace)`

### E. Penyimpanan Riwayat Diagnostik ke Server
Saat teknisi menekan *"Simpan ke Riwayat"*:
Kirim ke `POST /api/speedtest/records` dengan format tagging otomatis:
```kotlin
val targetTag = if (isPublicMode) "[Target: Internet Publik 100G]" else "[Target: Server PingDulu Lokal]"
val ispTag = "[ISP: ${info.isp} • ${info.location}]"
val finalNotes = listOf(targetTag, ispTag, userNotesText.trim()).filter { it.isNotEmpty() }.joinToString(" ")

apiService.saveSpeedtestRecord(
    SpeedtestRecordRequest(
        pingMs = state.pingMs,
        jitterMs = state.jitterMs,
        downloadMbps = state.downloadMbps,
        uploadMbps = state.uploadMbps,
        clientIp = info.ip,
        notes = finalNotes
    )
)
```
Di halaman riwayat, parse `[Target: ...]` dan `[ISP: ...]` untuk menampilkan badge warna:
- Badge `⚡ Edge 100G` (Biru terang) untuk mode publik.
- Badge `🏢 Server Lokal` (Abu-abu) untuk mode lokal.
- Badge teks abu-abu yang menampilkan nama ISP riil dan lokasi kota.
````

---

# BAGIAN 2: DOKUMENTASI ARSITEKTUR DAN API CONTRACT SERVER

Seluruh endpoint di bawah ini telah diuji dan terpasang aktif di server PingDulu:

### 1. Endpoint Avatar Foto Profil
- **`POST /api/users/:id/avatar`**:
  - Tipe request: `multipart/form-data` (Field: `file`).
  - Batas ukuran: Maksimal **10 MB**.
  - MIME yang didukung: `image/jpeg`, `image/png`, `image/webp`, `image/gif`.
  - Penyimpanan: Persisten di folder `/app/data/avatars/` (Docker Volume).
  - Response sukses (HTTP 200):
    ```json
    {
      "success": true,
      "avatar_url": "/api/avatars/avatar_10_1795372899.png",
      "message": "Foto profil berhasil diunggah"
    }
    ```
- **`GET /api/avatars/:filename`**:
  - Melayani file gambar dengan header HTTP caching `Cache-Control: public, max-age=86400`.
  - Dikecualikan dari authentication hook agar dapat di-render langsung oleh library image loader Android (Coil/Glide).
- **`DELETE /api/users/:id/avatar`**:
  - Menghapus file fisik dari disk dan mereset `users.avatar_url = NULL`.

### 2. Guard Login & Soft Deactivation
- **`POST /api/auth/login`**:
  - Jika `users.is_active == 0`, server langsung menolak dengan status HTTP **403 Forbidden**:
    ```json
    {
      "error": "Akun Anda dinonaktifkan. Silakan hubungi administrator."
    }
    ```
- **`GET /api/auth/me`**:
  - Mengembalikan seluruh field profil lengkap teknisi:
    ```json
    {
      "user": {
        "id": 10,
        "username": "purnomo",
        "display_name": "teknisi purnomo",
        "role": "technician",
        "department": "Infrastruktur",
        "sub_department": "Fiber Optic",
        "is_active": 1,
        "avatar_url": "/api/avatars/avatar_10_1795372899.png"
      }
    }
    ```

### 3. Dual-Mode Speedtest Records & Badging
- **`POST /api/speedtest/records`**:
  - Menerima `ping_ms`, `jitter_ms`, `download_mbps`, `upload_mbps`, `client_ip`, dan `notes`.
  - Field `notes` mendukung tagging target: `[Target: Internet Publik 100G]` atau `[Target: Server PingDulu Lokal]`.
- **`GET /api/speedtest/records`**:
  - Mengembalikan 30 data riwayat pengujian terbaru, lengkap dengan info teknisi, IP klien, badge target, dan metrik latency & throughput.

---

Dokumen ini disimpan di desktop Anda di:
[PATCH_PROFILE_AVATAR_DAN_USER_MANAGEMENT_PATCH_KE_8.md](file:///home/fajar/Desktop/PATCH_PROFILE_AVATAR_DAN_USER_MANAGEMENT_PATCH_KE_8.md)
