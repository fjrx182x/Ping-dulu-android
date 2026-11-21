package com.example.domain.tools

import androidx.compose.runtime.Immutable

enum class Rj45WiringMode(
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val endALabel: String,
    val endBLabel: String,
    val usageNote: String
) {
    T568B(
        shortLabel = "T568B",
        title = "Straight-Through T568B",
        subtitle = "Standar utama ISP, kantor & datacenter Indonesia",
        endALabel = "Ujung A (T568B)",
        endBLabel = "Ujung B (T568B)",
        usageNote = "Digunakan untuk menghubungkan perangkat berbeda: Switch ↔ Router, Switch ↔ PC/AP/CCTV, Modem ↔ Router."
    ),
    T568A(
        shortLabel = "T568A",
        title = "Straight-Through T568A",
        subtitle = "Standar residensial & kompatibel telepon lama",
        endALabel = "Ujung A (T568A)",
        endBLabel = "Ujung B (T568A)",
        usageNote = "Pasangan Hijau dan Oranye ditukar dibanding T568B. Pastikan kedua ujung memakai standar yang sama agar menjadi kabel Straight."
    ),
    CROSSOVER(
        shortLabel = "Crossover",
        title = "Crossover (T568A ↔ T568B)",
        subtitle = "Koneksi langsung perangkat sejenis tanpa Auto-MDIX",
        endALabel = "Ujung A (T568A)",
        endBLabel = "Ujung B (T568B)",
        usageNote = "Menukar TX (1,2) dengan RX (3,6). Perangkat modern umumnya sudah mendukung Auto-MDIX, namun masih dibutuhkan untuk switch/router legacy."
    ),
    ROLLOVER(
        shortLabel = "Console",
        title = "Console Rollover (Yost / Cisco)",
        subtitle = "Akses serial RS-232 untuk konfigurasi Router & Switch",
        endALabel = "Ujung A (T568B)",
        endBLabel = "Ujung B (Terbalik 8→1)",
        usageNote = "Pin 1–8 dibalik sepenuhnya (1↔8, 2↔7, 3↔6, 4↔5). Hanya untuk port Console RS-232 (9600 8N1), BUKAN untuk port Ethernet LAN."
    )
}

enum class WireColorCode(
    val displayName: String,
    val shortCode: String,
    val primaryHex: Long,
    val isStriped: Boolean
) {
    WHITE_ORANGE("Putih-Oranye", "P-O", 0xFFF97316, true),
    ORANGE("Oranye", "O", 0xFFF97316, false),
    WHITE_GREEN("Putih-Hijau", "P-H", 0xFF16A34A, true),
    BLUE("Biru", "B", 0xFF2563EB, false),
    WHITE_BLUE("Putih-Biru", "P-B", 0xFF2563EB, true),
    GREEN("Hijau", "H", 0xFF16A34A, false),
    WHITE_BROWN("Putih-Coklat", "P-C", 0xFF854D0E, true),
    BROWN("Coklat", "C", 0xFF854D0E, false)
}

@Immutable
data class Rj45PinSpec(
    val pinNumber: Int,
    val wireColor: WireColorCode,
    val pairNumber: Int,
    val signal100M: String,
    val signal1000M: String,
    val poeModeA: String,
    val poeModeB: String,
    val consoleSignal: String
)

@Immutable
data class PoeStandardSpec(
    val id: String,
    val standardCode: String,
    val marketingName: String,
    val pseMaxWatts: Double,
    val pdUsableWatts: Double,
    val voltageRange: String,
    val cablePairs: String,
    val minCableCategory: String,
    val typicalDevices: String,
    val isPassiveWarning: Boolean = false
)

@Immutable
data class EthernetCableSpec(
    val category: String,
    val maxSpeed: String,
    val bandwidthMhz: String,
    val maxDistance: String,
    val poeSupport: String,
    val fieldNotes: String
)

enum class PoeBudgetStatus(val label: String) {
    SAFE("Aman (Headroom Cukup)"),
    CAUTION("Mendekati Batas (>80%)"),
    OVERLOAD("Overload — Melebihi Kapasitas Switch!")
}

@Immutable
data class PoeBudgetResult(
    val switchBudgetWatts: Int,
    val totalDemandWatts: Double,
    val remainingWatts: Double,
    val utilizationPercent: Int,
    val status: PoeBudgetStatus
)

object Rj45PoeReference {

    val t568BPins: List<Rj45PinSpec> = listOf(
        Rj45PinSpec(1, WireColorCode.WHITE_ORANGE, 2, "TX+", "BI_DA+", "DC+ (Mode A)", "Data saja", "RTS"),
        Rj45PinSpec(2, WireColorCode.ORANGE, 2, "TX-", "BI_DA-", "DC+ (Mode A)", "Data saja", "DTR"),
        Rj45PinSpec(3, WireColorCode.WHITE_GREEN, 3, "RX+", "BI_DB+", "DC- (Mode A)", "Data saja", "TXD"),
        Rj45PinSpec(4, WireColorCode.BLUE, 1, "Cadangan", "BI_DC+", "Data saja", "DC+ (Mode B / Passive)", "GND"),
        Rj45PinSpec(5, WireColorCode.WHITE_BLUE, 1, "Cadangan", "BI_DC-", "Data saja", "DC+ (Mode B / Passive)", "GND"),
        Rj45PinSpec(6, WireColorCode.GREEN, 3, "RX-", "BI_DB-", "DC- (Mode A)", "Data saja", "RXD"),
        Rj45PinSpec(7, WireColorCode.WHITE_BROWN, 4, "Cadangan", "BI_DD+", "Data saja", "DC- (Mode B / Passive)", "DSR"),
        Rj45PinSpec(8, WireColorCode.BROWN, 4, "Cadangan", "BI_DD-", "Data saja", "DC- (Mode B / Passive)", "CTS")
    )

    val t568APins: List<Rj45PinSpec> = listOf(
        Rj45PinSpec(1, WireColorCode.WHITE_GREEN, 3, "TX+", "BI_DA+", "DC+ (Mode A)", "Data saja", "RTS"),
        Rj45PinSpec(2, WireColorCode.GREEN, 3, "TX-", "BI_DA-", "DC+ (Mode A)", "Data saja", "DTR"),
        Rj45PinSpec(3, WireColorCode.WHITE_ORANGE, 2, "RX+", "BI_DB+", "DC- (Mode A)", "Data saja", "TXD"),
        Rj45PinSpec(4, WireColorCode.BLUE, 1, "Cadangan", "BI_DC+", "Data saja", "DC+ (Mode B / Passive)", "GND"),
        Rj45PinSpec(5, WireColorCode.WHITE_BLUE, 1, "Cadangan", "BI_DC-", "Data saja", "DC+ (Mode B / Passive)", "GND"),
        Rj45PinSpec(6, WireColorCode.ORANGE, 2, "RX-", "BI_DB-", "DC- (Mode A)", "Data saja", "RXD"),
        Rj45PinSpec(7, WireColorCode.WHITE_BROWN, 4, "Cadangan", "BI_DD+", "Data saja", "DC- (Mode B / Passive)", "DSR"),
        Rj45PinSpec(8, WireColorCode.BROWN, 4, "Cadangan", "BI_DD-", "Data saja", "DC- (Mode B / Passive)", "CTS")
    )

    val rolloverEndBPins: List<Rj45PinSpec> = t568BPins.reversed().mapIndexed { index, spec ->
        spec.copy(
            pinNumber = index + 1,
            consoleSignal = t568BPins[index].consoleSignal
        )
    }

    fun pinsForEndA(mode: Rj45WiringMode): List<Rj45PinSpec> = when (mode) {
        Rj45WiringMode.T568B -> t568BPins
        Rj45WiringMode.T568A -> t568APins
        Rj45WiringMode.CROSSOVER -> t568APins
        Rj45WiringMode.ROLLOVER -> t568BPins
    }

    fun pinsForEndB(mode: Rj45WiringMode): List<Rj45PinSpec> = when (mode) {
        Rj45WiringMode.T568B -> t568BPins
        Rj45WiringMode.T568A -> t568APins
        Rj45WiringMode.CROSSOVER -> t568BPins
        Rj45WiringMode.ROLLOVER -> rolloverEndBPins
    }

    val poeStandards: List<PoeStandardSpec> = listOf(
        PoeStandardSpec(
            id = "802.3af",
            standardCode = "IEEE 802.3af",
            marketingName = "PoE (Type 1)",
            pseMaxWatts = 15.4,
            pdUsableWatts = 12.95,
            voltageRange = "44 – 57 V DC",
            cablePairs = "2-Pair (Mode A: 1,2 & 3,6 / Mode B: 4,5 & 7,8)",
            minCableCategory = "Cat5 / Cat5e",
            typicalDevices = "IP Phone, Access Point standar, IP Camera indoor"
        ),
        PoeStandardSpec(
            id = "802.3at",
            standardCode = "IEEE 802.3at",
            marketingName = "PoE+ (Type 2)",
            pseMaxWatts = 30.0,
            pdUsableWatts = 25.5,
            voltageRange = "50 – 57 V DC",
            cablePairs = "2-Pair (Mode A atau Mode B)",
            minCableCategory = "Cat5e (disarankan Cat6)",
            typicalDevices = "AP Wi-Fi 6/Dual-Band, CCTV PTZ, Access Control"
        ),
        PoeStandardSpec(
            id = "802.3bt-t3",
            standardCode = "IEEE 802.3bt",
            marketingName = "PoE++ (Type 3)",
            pseMaxWatts = 60.0,
            pdUsableWatts = 51.0,
            voltageRange = "50 – 57 V DC",
            cablePairs = "4-Pair (Semua 8 pin: 1,2,3,6 + 4,5,7,8)",
            minCableCategory = "Cat5e / Cat6 Pure Copper",
            typicalDevices = "AP Wi-Fi 6E/7, Kamera PTZ Outdoor Heater, Thin Client"
        ),
        PoeStandardSpec(
            id = "802.3bt-t4",
            standardCode = "IEEE 802.3bt",
            marketingName = "PoE++ (Type 4)",
            pseMaxWatts = 90.0,
            pdUsableWatts = 71.3,
            voltageRange = "52 – 57 V DC",
            cablePairs = "4-Pair (Semua 8 pin)",
            minCableCategory = "Cat6 / Cat6a Pure Copper (23 AWG)",
            typicalDevices = "Switch PoE Passthrough, Digital Signage, Kiosk"
        ),
        PoeStandardSpec(
            id = "passive-poe",
            standardCode = "Passive PoE",
            marketingName = "24V / 48V (MikroTik & Ubiquiti)",
            pseMaxWatts = 24.0,
            pdUsableWatts = 18.0,
            voltageRange = "24 V atau 48 V DC (Always-On)",
            cablePairs = "Pin 4,5 (+) & Pin 7,8 (-) · Tanpa Negosiasi",
            minCableCategory = "Cat5e / Cat6 Outdoor STP",
            typicalDevices = "MikroTik LHG/SXT/hAP, Ubiquiti LiteBeam/NanoStation",
            isPassiveWarning = true
        )
    )

    val cableSpecs: List<EthernetCableSpec> = listOf(
        EthernetCableSpec(
            category = "Cat5e",
            maxSpeed = "1 Gbps (1000BASE-T)",
            bandwidthMhz = "100 MHz",
            maxDistance = "100 meter (90m permanen + 10m patch)",
            poeSupport = "802.3af / at (hindari kabel CCA untuk PoE jarak jauh)",
            fieldNotes = "Ekonomis untuk LAN kantor, CCTV & AP standar. Pastikan bahan Pure Copper (BC), bukan CCA."
        ),
        EthernetCableSpec(
            category = "Cat6",
            maxSpeed = "1 Gbps (100m) · 10 Gbps (≤ 55m)",
            bandwidthMhz = "250 MHz",
            maxDistance = "100 meter (1G) · 55 meter (10G)",
            poeSupport = "802.3af / at / bt Type 3 (disarankan 23 AWG)",
            fieldNotes = "Dilengkapi spline pemisah antar pair untuk menekan crosstalk. Pilihan terbaik untuk instalasi baru."
        ),
        EthernetCableSpec(
            category = "Cat6a",
            maxSpeed = "10 Gbps (10GBASE-T)",
            bandwidthMhz = "500 MHz",
            maxDistance = "100 meter penuh di 10 Gbps",
            poeSupport = "802.3bt Type 4 (90–100W) — paling dingin & aman",
            fieldNotes = "Diameter kabel lebih tebal; pakai konektor RJ45 Cat6a pass-through/shielded & grounding yang benar."
        ),
        EthernetCableSpec(
            category = "Cat7 (S/FTP)",
            maxSpeed = "10 Gbps+",
            bandwidthMhz = "600 MHz",
            maxDistance = "100 meter",
            poeSupport = "802.3bt Type 4",
            fieldNotes = "Shielding ganda per pair + braided shield untuk area interferensi listrik tinggi (panel listrik/pabrik)."
        )
    )

    val switchBudgetPresets: List<Int> = listOf(65, 120, 250, 370, 740)

    const val WATTS_AP_WIFI = 15.0
    const val WATTS_IP_CAMERA = 12.0
    const val WATTS_PTZ_CAMERA = 25.0
    const val WATTS_IP_PHONE = 7.0

    fun calculatePoeBudget(
        switchBudgetWatts: Int,
        apCount: Int,
        cameraCount: Int,
        ptzCount: Int,
        phoneCount: Int
    ): PoeBudgetResult {
        val safeBudget = switchBudgetWatts.coerceAtLeast(1)
        val totalDemand = (apCount.coerceAtLeast(0) * WATTS_AP_WIFI) +
            (cameraCount.coerceAtLeast(0) * WATTS_IP_CAMERA) +
            (ptzCount.coerceAtLeast(0) * WATTS_PTZ_CAMERA) +
            (phoneCount.coerceAtLeast(0) * WATTS_IP_PHONE)
        val remaining = safeBudget - totalDemand
        val utilization = ((totalDemand / safeBudget.toDouble()) * 100.0).toInt()
        val status = when {
            totalDemand > safeBudget -> PoeBudgetStatus.OVERLOAD
            utilization > 80 -> PoeBudgetStatus.CAUTION
            else -> PoeBudgetStatus.SAFE
        }
        return PoeBudgetResult(
            switchBudgetWatts = safeBudget,
            totalDemandWatts = totalDemand,
            remainingWatts = remaining,
            utilizationPercent = utilization,
            status = status
        )
    }
}
