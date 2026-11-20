package com.example.domain.repository

sealed interface SyncResult {
    data class Success(
        val downloadedCount: Int,
        val uploadedCount: Int
    ) : SyncResult

    data class OfflineFallback(
        val pendingUploadCount: Int,
        val reason: String
    ) : SyncResult

    data class AuthRequired(
        val message: String
    ) : SyncResult
}
