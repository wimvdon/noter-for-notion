package com.impca201.noter.logic

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Notion's dates: "2026-10-15" (a day) or "2026-10-15T09:30:00.000+02:00" (a moment). Converts
 * them for display and for Material's date and time pickers.
 */
object NotionDates {
    fun hasTime(iso: String): Boolean = iso.contains('T')

    fun parseDate(iso: String, zone: ZoneId): LocalDate? = runCatching {
        if (hasTime(iso)) OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDate() else LocalDate.parse(iso)
    }.getOrNull()

    fun parseTime(iso: String, zone: ZoneId): LocalTime? =
        if (!hasTime(iso)) null else runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalTime() }.getOrNull()

    fun display(iso: String?, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
        iso ?: return ""
        val date = parseDate(iso, zone) ?: return iso
        val dateText = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        val time = parseTime(iso, zone) ?: return dateText
        return dateText + " " + time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }

    fun displayRange(start: String?, end: String?, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
        val from = display(start, locale, zone)
        return if (end != null) "$from → ${display(end, locale, zone)}" else from
    }

    /** The UTC midnight Material's DatePicker uses for [iso]'s day. */
    fun pickerMillis(iso: String?, zone: ZoneId = ZoneId.systemDefault()): Long? =
        iso?.let { parseDate(it, zone) }?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    /** The ISO value for a day picked in the DatePicker, with an optional time in [zone]. */
    fun fromPicker(utcMidnightMillis: Long, time: LocalTime?, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
        if (time == null) return date.toString()
        val local = LocalDateTime.of(date, time.withSecond(0).withNano(0))
        return local.atZone(zone).toOffsetDateTime().format(ISO_WITH_OFFSET)
    }

    /** [iso] with its time changed (or set). */
    fun withTime(iso: String, time: LocalTime?, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = parseDate(iso, zone) ?: return iso
        val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return fromPicker(millis, time, zone)
    }

    private val ISO_WITH_OFFSET: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
}
