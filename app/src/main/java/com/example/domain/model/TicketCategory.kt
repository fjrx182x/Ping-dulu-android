package com.example.domain.model

enum class TicketCategory(
    val code: String,
    val displayName: String
) {
    NET(code = "NET", displayName = "Jaringan"),
    SRV(code = "SRV", displayName = "Server & Storage"),
    HRD(code = "HRD", displayName = "Hardware"),
    SEC(code = "SEC", displayName = "Keamanan"),
    ONB(code = "ONB", displayName = "Onboarding");

    companion object {
        fun fromString(raw: String?): TicketCategory {
            return when (raw?.trim()?.uppercase()) {
                "NET", "NETWORK" -> NET
                "SRV", "SERVER" -> SRV
                "HRD", "HARDWARE" -> HRD
                "SEC", "SECURITY" -> SEC
                "ONB", "ONBOARDING" -> ONB
                else -> NET
            }
        }
    }
}
