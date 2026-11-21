package com.example.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * API-24-compatible date/epoch-day & timestamp utilities using Calendar/TimeZone math
 * so epoch-day values stay consistent with device local timezone (e.g. WIB/UTC+7)
 * without requiring java.time desugaring on Android 7.x (API 24-25).
 */
object DateEpochUtil {
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val MILLIS_PER_HOUR = 3_600_000
    private const val SECONDS_THRESHOLD = 10_000_000_000L

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

    fun todayEpochDay(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = epochDayFromMillis(nowMillis, timeZone)

    fun epochDayFromMillis(
        timestampMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val localCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = timestampMillis
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

    /**
     * Converts Material 3 DatePicker selectedDateMillis (always UTC midnight) into exact epochDay
     * without local timezone offset drift.
     */
    fun epochDayFromUtcPickerMillis(utcMidnightMillis: Long): Long {
        return Math.floorDiv(utcMidnightMillis, MILLIS_PER_DAY)
    }

    fun utcPickerMillisFromEpochDay(epochDay: Long): Long {
        return epochDay * MILLIS_PER_DAY
    }

    /**
     * Flexibly parses server timestamps returned as epoch millis, epoch seconds,
     * ISO-8601 strings (with Z or timezone offset), or SQLite UTC strings ("yyyy-MM-dd HH:mm:ss").
     */
    fun parseTimestampMillisOrNull(raw: Any?): Long? {
        if (raw == null) return null
        return when (raw) {
            is Number -> normalizeEpochNumber(raw.toLong())
            is String -> parseTimestampString(raw)
            else -> parseTimestampString(raw.toString())
        }
    }

    fun parseTimestampMillis(
        raw: Any?,
        fallbackMillis: Long = System.currentTimeMillis()
    ): Long = parseTimestampMillisOrNull(raw) ?: fallbackMillis

    private fun normalizeEpochNumber(value: Long): Long? {
        if (value <= 0L) return null
        return if (value < SECONDS_THRESHOLD) value * 1_000L else value
    }

    private fun parseTimestampString(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // 1. Numeric string (epoch seconds or millis)
        trimmed.toLongOrNull()?.let { return normalizeEpochNumber(it) }
        trimmed.toDoubleOrNull()?.let { num ->
            if (num > 0.0) return normalizeEpochNumber(num.toLong())
        }

        // Normalize fractional seconds to exact 3 digits (".5" -> ".500", ".123456" -> ".123")
        // to prevent SimpleDateFormat .SSS from misinterpreting microseconds as extra minutes.
        val normalizedFraction = trimmed.replace(Regex("\\.(\\d+)")) { match ->
            "." + match.groupValues[1].padEnd(3, '0').take(3)
        }
        // Normalize ISO-8601 colon timezone suffix e.g. "+07:00" -> "+0700" for API 24 SimpleDateFormat
        val normalizedOffset = normalizedFraction.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")

        val utcPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        val offsetPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ssZ"
        )

        for (pattern in offsetPatterns) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                fmt.parse(normalizedOffset)?.time
            }.getOrNull()?.let { return it }
        }

        for (pattern in utcPatterns) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.parse(normalizedFraction)?.time
            }.getOrNull()?.let { return it }
        }

        // Date-only string ("yyyy-MM-dd") interpreted in device local timezone midnight
        runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getDefault()
            }
            fmt.parse(trimmed)?.time
        }.getOrNull()?.let { return it }

        return null
    }

    fun formatReportedTime(
        createdAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val safeCreated = if (createdAtMillis > 0L) createdAtMillis else nowMillis
        val createdCal = Calendar.getInstance(timeZone).apply { timeInMillis = safeCreated }
        val createdDay = epochDayFromMillis(safeCreated, timeZone)
        val todayDay = todayEpochDay(nowMillis, timeZone)
        val timePart = String.format(
            Locale.US,
            "%02d:%02d",
            createdCal.get(Calendar.HOUR_OF_DAY),
            createdCal.get(Calendar.MINUTE)
        )
        val zoneTag = resolveTimezoneTag(timeZone, safeCreated)

        return when (createdDay) {
            todayDay -> "Hari ini, $timePart $zoneTag"
            todayDay - 1L -> "Kemarin, $timePart $zoneTag"
            else -> {
                val day = createdCal.get(Calendar.DAY_OF_MONTH)
                val month = shortIndonesianMonths[createdCal.get(Calendar.MONTH)]
                val year = createdCal.get(Calendar.YEAR)
                "$day $month $year, $timePart $zoneTag"
            }
        }
    }

    fun formatReportedTimeCompact(
        createdAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val safeCreated = if (createdAtMillis > 0L) createdAtMillis else nowMillis
        val createdCal = Calendar.getInstance(timeZone).apply { timeInMillis = safeCreated }
        val createdDay = epochDayFromMillis(safeCreated, timeZone)
        val todayDay = todayEpochDay(nowMillis, timeZone)
        val timePart = String.format(
            Locale.US,
            "%02d:%02d",
            createdCal.get(Calendar.HOUR_OF_DAY),
            createdCal.get(Calendar.MINUTE)
        )

        return when (createdDay) {
            todayDay -> "Hari ini, $timePart"
            todayDay - 1L -> "Kemarin, $timePart"
            else -> {
                val day = createdCal.get(Calendar.DAY_OF_MONTH)
                val month = shortIndonesianMonths[createdCal.get(Calendar.MONTH)]
                "$day $month, $timePart"
            }
        }
    }

    private fun resolveTimezoneTag(timeZone: TimeZone, atMillis: Long): String {
        return when (timeZone.getOffset(atMillis)) {
            7 * MILLIS_PER_HOUR -> "WIB"
            8 * MILLIS_PER_HOUR -> "WITA"
            9 * MILLIS_PER_HOUR -> "WIT"
            else -> timeZone.getDisplayName(
                timeZone.inDaylightTime(Date(atMillis)),
                TimeZone.SHORT,
                Locale.US
            ).ifBlank { "WIB" }
        }
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

    fun formatScheduledClock(
        minutesFromMidnight: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
        referenceMillis: Long = System.currentTimeMillis()
    ): String {
        val safe = minutesFromMidnight.coerceIn(0, 23 * 60 + 59)
        val zoneTag = resolveTimezoneTag(timeZone, referenceMillis)
        return String.format(Locale.US, "%02d:%02d %s", safe / 60, safe % 60, zoneTag)
    }

    fun formatScheduledClockCompact(minutesFromMidnight: Int): String {
        val safe = minutesFromMidnight.coerceIn(0, 23 * 60 + 59)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    fun formatMinutesToTimeSlot(startMinutes: Int?, durationMinutes: Int = 90): String {
        if (startMinutes == null) return ""
        val safeStart = startMinutes.coerceIn(0, 23 * 60 + 59)
        val safeEnd = (safeStart + durationMinutes).coerceAtMost(23 * 60 + 59)
        return String.format(
            Locale.US,
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
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
