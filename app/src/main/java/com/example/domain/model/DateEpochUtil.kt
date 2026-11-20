package com.example.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * API-24-compatible date/epoch-day utilities using UTC calendar math
 * so epoch-day values stay consistent across timezones and DST boundaries.
 */
object DateEpochUtil {
    private const val MILLIS_PER_DAY = 86_400_000L

    private val shortIndonesianDays = mapOf(
        Calendar.MONDAY to "SEN",
        Calendar.TUESDAY to "SEL",
        Calendar.WEDNESDAY to "RAB",
        Calendar.THURSDAY to "KAM",
        Calendar.FRIDAY to "JUM",
        Calendar.SATURDAY to "SAB",
        Calendar.SUNDAY to "MIN"
    )

    private val fullIndonesianDays = mapOf(
        Calendar.MONDAY to "Senin",
        Calendar.TUESDAY to "Selasa",
        Calendar.WEDNESDAY to "Rabu",
        Calendar.THURSDAY to "Kamis",
        Calendar.FRIDAY to "Jumat",
        Calendar.SATURDAY to "Sabtu",
        Calendar.SUNDAY to "Minggu"
    )

    private val shortIndonesianMonths = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    fun todayEpochDay(nowMillis: Long = System.currentTimeMillis()): Long {
        val localCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
        return toEpochDay(
            year = localCal.get(Calendar.YEAR),
            monthZeroBased = localCal.get(Calendar.MONTH),
            dayOfMonth = localCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun toEpochDay(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, monthZeroBased, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return utcCal.timeInMillis / MILLIS_PER_DAY
    }

    private fun utcCalendarForEpochDay(epochDay: Long): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochDay * MILLIS_PER_DAY
        }
    }

    fun shortDayName(epochDay: Long): String {
        val dow = utcCalendarForEpochDay(epochDay).get(Calendar.DAY_OF_WEEK)
        return shortIndonesianDays[dow] ?: "HAR"
    }

    fun dayOfMonth(epochDay: Long): Int {
        return utcCalendarForEpochDay(epochDay).get(Calendar.DAY_OF_MONTH)
    }

    fun formatFullDate(epochDay: Long): String {
        val cal = utcCalendarForEpochDay(epochDay)
        val dayName = fullIndonesianDays[cal.get(Calendar.DAY_OF_WEEK)] ?: ""
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = shortIndonesianMonths[cal.get(Calendar.MONTH)]
        val year = cal.get(Calendar.YEAR)
        return "$dayName, $day $month $year"
    }

    fun formatRangeSpan(startEpochDay: Long, endEpochDay: Long): String {
        val startCal = utcCalendarForEpochDay(startEpochDay)
        val endCal = utcCalendarForEpochDay(endEpochDay)
        val startDay = startCal.get(Calendar.DAY_OF_MONTH)
        val startMonth = shortIndonesianMonths[startCal.get(Calendar.MONTH)]
        val endDay = endCal.get(Calendar.DAY_OF_MONTH)
        val endMonth = shortIndonesianMonths[endCal.get(Calendar.MONTH)]
        val endYear = endCal.get(Calendar.YEAR)
        return "$startDay $startMonth - $endDay $endMonth $endYear"
    }

    fun formatMinutesToTimeSlot(startMinutes: Int, durationMinutes: Int = 90): String {
        val safeStart = startMinutes.coerceIn(0, 23 * 60 + 59)
        val safeEnd = (safeStart + durationMinutes).coerceAtMost(23 * 60 + 59)
        return String.format(
            java.util.Locale.US,
            "%02d:%02d - %02d:%02d",
            safeStart / 60,
            safeStart % 60,
            safeEnd / 60,
            safeEnd % 60
        )
    }

    fun formatStopwatch(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600L
        val minutes = (safeSeconds % 3600L) / 60L
        val seconds = safeSeconds % 60L
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
