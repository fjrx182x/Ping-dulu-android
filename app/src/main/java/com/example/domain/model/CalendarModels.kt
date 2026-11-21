package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class NationalHoliday(
    val date: String,
    val epochDay: Long,
    val name: String,
    val isCutiBersama: Boolean = false
)

/**
 * Offline fallback catalog of official Indonesian National Holidays & Cuti Bersama
 * (SKB 3 Menteri 2025–2027) so the Monthly Calendar & 7-Day Agenda always highlight
 * red dates even when operating offline in basements/server rooms.
 */
object IndonesianHolidayCatalog {

    private fun h(date: String, name: String, cutiBersama: Boolean = false): NationalHoliday {
        return NationalHoliday(
            date = date,
            epochDay = DateEpochUtil.dateToEpochDay(date),
            name = name,
            isCutiBersama = cutiBersama
        )
    }

    private val holidays2025: List<NationalHoliday> by lazy {
        listOf(
            h("2025-01-01", "Tahun Baru 2025 Masehi"),
            h("2025-01-27", "Isra Mikraj Nabi Muhammad SAW"),
            h("2025-01-28", "Cuti Bersama Tahun Baru Imlek 2576 Kongzili", true),
            h("2025-01-29", "Tahun Baru Imlek 2576 Kongzili"),
            h("2025-03-28", "Cuti Bersama Hari Suci Nyepi", true),
            h("2025-03-29", "Hari Suci Nyepi (Tahun Baru Saka 1947)"),
            h("2025-03-31", "Hari Raya Idul Fitri 1446 H (Hari Pertama)"),
            h("2025-04-01", "Hari Raya Idul Fitri 1446 H (Hari Kedua)"),
            h("2025-04-02", "Cuti Bersama Idul Fitri 1446 H", true),
            h("2025-04-03", "Cuti Bersama Idul Fitri 1446 H", true),
            h("2025-04-04", "Cuti Bersama Idul Fitri 1446 H", true),
            h("2025-04-07", "Cuti Bersama Idul Fitri 1446 H", true),
            h("2025-04-18", "Wafat Yesus Kristus"),
            h("2025-04-20", "Kebangkitan Yesus Kristus (Paskah)"),
            h("2025-05-01", "Hari Buruh Internasional"),
            h("2025-05-12", "Hari Raya Waisak 2569 BE"),
            h("2025-05-13", "Cuti Bersama Hari Raya Waisak 2569 BE", true),
            h("2025-05-29", "Kenaikan Yesus Kristus"),
            h("2025-05-30", "Cuti Bersama Kenaikan Yesus Kristus", true),
            h("2025-06-01", "Hari Lahir Pancasila"),
            h("2025-06-06", "Hari Raya Idul Adha 1446 H"),
            h("2025-06-09", "Cuti Bersama Idul Adha 1446 H", true),
            h("2025-06-27", "Tahun Baru Islam 1447 H"),
            h("2025-08-17", "Hari Kemerdekaan Republik Indonesia"),
            h("2025-09-05", "Maulid Nabi Muhammad SAW"),
            h("2025-12-25", "Hari Raya Natal"),
            h("2025-12-26", "Cuti Bersama Hari Raya Natal", true)
        )
    }

    private val holidays2026: List<NationalHoliday> by lazy {
        listOf(
            h("2026-01-01", "Tahun Baru 2026 Masehi"),
            h("2026-01-16", "Isra Mikraj Nabi Muhammad SAW"),
            h("2026-02-16", "Cuti Bersama Tahun Baru Imlek 2577 Kongzili", true),
            h("2026-02-17", "Tahun Baru Imlek 2577 Kongzili"),
            h("2026-03-18", "Cuti Bersama Hari Suci Nyepi", true),
            h("2026-03-19", "Hari Suci Nyepi (Tahun Baru Saka 1948)"),
            h("2026-03-20", "Hari Raya Idul Fitri 1447 H (Hari Pertama)"),
            h("2026-03-21", "Hari Raya Idul Fitri 1447 H (Hari Kedua)"),
            h("2026-03-23", "Cuti Bersama Idul Fitri 1447 H", true),
            h("2026-03-24", "Cuti Bersama Idul Fitri 1447 H", true),
            h("2026-04-03", "Wafat Yesus Kristus"),
            h("2026-04-05", "Kebangkitan Yesus Kristus (Paskah)"),
            h("2026-05-01", "Hari Buruh Internasional"),
            h("2026-05-14", "Kenaikan Yesus Kristus"),
            h("2026-05-15", "Cuti Bersama Kenaikan Yesus Kristus", true),
            h("2026-05-27", "Hari Raya Idul Adha 1447 H"),
            h("2026-05-28", "Cuti Bersama Idul Adha 1447 H", true),
            h("2026-05-31", "Hari Raya Waisak 2570 BE"),
            h("2026-06-01", "Hari Lahir Pancasila"),
            h("2026-06-16", "Tahun Baru Islam 1448 H"),
            h("2026-08-17", "Hari Kemerdekaan Republik Indonesia"),
            h("2026-08-25", "Maulid Nabi Muhammad SAW"),
            h("2026-12-24", "Cuti Bersama Hari Raya Natal", true),
            h("2026-12-25", "Hari Raya Natal")
        )
    }

    private val holidays2027: List<NationalHoliday> by lazy {
        listOf(
            h("2027-01-01", "Tahun Baru 2027 Masehi"),
            h("2027-01-05", "Isra Mikraj Nabi Muhammad SAW"),
            h("2027-02-06", "Tahun Baru Imlek 2578 Kongzili"),
            h("2027-03-08", "Hari Suci Nyepi (Tahun Baru Saka 1949)"),
            h("2027-03-10", "Hari Raya Idul Fitri 1448 H (Hari Pertama)"),
            h("2027-03-11", "Hari Raya Idul Fitri 1448 H (Hari Kedua)"),
            h("2027-03-26", "Wafat Yesus Kristus"),
            h("2027-03-28", "Kebangkitan Yesus Kristus (Paskah)"),
            h("2027-05-01", "Hari Buruh Internasional"),
            h("2027-05-06", "Kenaikan Yesus Kristus"),
            h("2027-05-17", "Hari Raya Idul Adha 1448 H"),
            h("2027-05-20", "Hari Raya Waisak 2571 BE"),
            h("2027-06-01", "Hari Lahir Pancasila"),
            h("2027-06-06", "Tahun Baru Islam 1449 H"),
            h("2027-08-14", "Maulid Nabi Muhammad SAW"),
            h("2027-08-17", "Hari Kemerdekaan Republik Indonesia"),
            h("2027-12-25", "Hari Raya Natal")
        )
    }

    fun holidaysForYear(year: Int): List<NationalHoliday> {
        return when (year) {
            2025 -> holidays2025
            2026 -> holidays2026
            2027 -> holidays2027
            else -> listOf(
                h("$year-01-01", "Tahun Baru $year Masehi"),
                h("$year-05-01", "Hari Buruh Internasional"),
                h("$year-06-01", "Hari Lahir Pancasila"),
                h("$year-08-17", "Hari Kemerdekaan Republik Indonesia"),
                h("$year-12-25", "Hari Raya Natal")
            )
        }
    }
}
