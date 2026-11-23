package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class PreparedAvatarPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    fun toMultipartPart(): MultipartBody.Part {
        val requestBody = bytes.toRequestBody(mimeType.toMediaType())
        return MultipartBody.Part.createFormData("file", fileName, requestBody)
    }
}

/**
 * Safe avatar validation & multipart preparation engine (max 10 MB).
 * Streams with a strict 10 MB cutoff so oversized files fail fast without OOM or temp file leaks.
 */
object AvatarUploadHelper {

    const val MAX_FILE_SIZE_BYTES: Int = 10 * 1024 * 1024 // 10 MB

    private val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif"
    )

    fun normalizeMimeType(rawMime: String?): String {
        val clean = rawMime?.trim()?.lowercase(Locale.US).orEmpty()
        return when (clean) {
            "image/jpg" -> "image/jpeg"
            else -> clean
        }
    }

    fun extensionForMime(mimeType: String): String = when (normalizeMimeType(mimeType)) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    fun prepareAvatarFromStream(
        inputStream: InputStream,
        rawMimeType: String?,
        declaredSizeBytes: Long? = null,
        suggestedFileName: String? = null
    ): Result<PreparedAvatarPayload> {
        val mimeType = normalizeMimeType(rawMimeType).ifEmpty { "image/jpeg" }
        if (mimeType !in ALLOWED_MIME_TYPES) {
            return Result.failure(
                IllegalArgumentException(
                    "Hanya file gambar (JPEG, PNG, WebP, GIF) yang diperbolehkan."
                )
            )
        }

        if (declaredSizeBytes != null && declaredSizeBytes > MAX_FILE_SIZE_BYTES.toLong()) {
            val sizeMb = String.format(Locale.US, "%.1f", declaredSizeBytes / (1024f * 1024f))
            return Result.failure(
                IllegalArgumentException(
                    "Ukuran gambar ($sizeMb MB) melebihi batas maksimal 10 MB."
                )
            )
        }

        return try {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var totalRead = 0
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > MAX_FILE_SIZE_BYTES) {
                    val sizeMb = String.format(Locale.US, "%.1f", totalRead / (1024f * 1024f))
                    return Result.failure(
                        IllegalArgumentException(
                            "Ukuran gambar (>$sizeMb MB) melebihi batas maksimal 10 MB."
                        )
                    )
                }
                out.write(buffer, 0, read)
            }
            val bytes = out.toByteArray()
            if (bytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("File gambar kosong atau tidak dapat dibaca."))
            }
            val ext = extensionForMime(mimeType)
            val safeName = suggestedFileName?.trim()?.takeIf { it.isNotEmpty() }
                ?: "avatar_${System.currentTimeMillis()}.$ext"
            Result.success(
                PreparedAvatarPayload(
                    fileName = safeName,
                    mimeType = mimeType,
                    bytes = bytes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun prepareAvatarFromUri(
        context: Context,
        imageUri: Uri
    ): Result<PreparedAvatarPayload> {
        return try {
            val contentResolver = context.contentResolver
            val rawMime = contentResolver.getType(imageUri)
            var displayName: String? = null
            var declaredSize: Long? = null

            runCatching {
                contentResolver.query(
                    imageUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx)
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            declaredSize = cursor.getLong(sizeIdx)
                        }
                    }
                }
            }

            val inferredMime = rawMime ?: inferMimeFromFileName(displayName)
            contentResolver.openInputStream(imageUri)?.use { stream ->
                prepareAvatarFromStream(
                    inputStream = stream,
                    rawMimeType = inferredMime,
                    declaredSizeBytes = declaredSize,
                    suggestedFileName = displayName
                )
            } ?: Result.failure(IllegalStateException("Gagal membaca file gambar dari perangkat."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun inferMimeFromFileName(fileName: String?): String {
        val lower = fileName?.trim()?.lowercase(Locale.US).orEmpty()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> "image/jpeg"
        }
    }
}
