package com.example.domain.model

enum class Stage(
    val apiValue: String,
    val displayLabel: String
) {
    BACKLOG(
        apiValue = "BACKLOG",
        displayLabel = "Antrean"
    ),
    IN_PROGRESS(
        apiValue = "IN_PROGRESS",
        displayLabel = "Proses"
    ),
    DONE(
        apiValue = "DONE",
        displayLabel = "Selesai"
    );

    companion object {
        fun fromString(raw: String?): Stage {
            return when (raw?.trim()?.uppercase()) {
                "IN_PROGRESS", "INPROGRESS", "PROGRESS", "PROSES" -> IN_PROGRESS
                "DONE", "COMPLETED", "SELESAI" -> DONE
                else -> BACKLOG
            }
        }
    }
}
