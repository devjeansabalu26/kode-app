package com.kode.app.kode_app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Único lugar donde se formatean y comparan fechas de eventos (en la BD son `timestamptz`, en Kotlin `Instant`). */
object DateFormats {
    fun display(date: Instant): String {
        return DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(date)
    }

    fun isThisWeek(date: Instant): Boolean {
        val zone = ZoneId.systemDefault()
        val startOfWeek = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()
        val endOfWeek = startOfWeek.atZone(zone).plusDays(7).toInstant()
        return !date.isBefore(startOfWeek) && date.isBefore(endOfWeek)
    }
}
