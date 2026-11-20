package com.example.domain.model

enum class Priority(
    val apiValue: String,
    val shortCode: String,
    val displayLabel: String,
    val sortRank: Int
) {
    P1_CRITICAL(
        apiValue = "P1_CRITICAL",
        shortCode = "P1",
        displayLabel = "P1 CRITICAL",
        sortRank = 1
    ),
    P2_HIGH(
        apiValue = "P2_HIGH",
        shortCode = "P2",
        displayLabel = "P2 HIGH",
        sortRank = 2
    ),
    P3_NORMAL(
        apiValue = "P3_NORMAL",
        shortCode = "P3",
        displayLabel = "P3 NORMAL",
        sortRank = 3
    );

    companion object {
        fun fromString(raw: String?): Priority {
            return when (raw?.trim()?.uppercase()) {
                "P1", "P1_CRITICAL", "CRITICAL", "URGENT" -> P1_CRITICAL
                "P2", "P2_HIGH", "HIGH" -> P2_HIGH
                else -> P3_NORMAL
            }
        }
    }
}
