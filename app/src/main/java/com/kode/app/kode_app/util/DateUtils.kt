package com.kode.app.kode_app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val formatosEntrada = listOf(
    "yyyy-MM-dd HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm",
    "yyyy-MM-dd HH:mm",
    "HH:mm:ss",
    "HH:mm"
)

private fun parsear(raw: String): Date? {
    for (patron in formatosEntrada) {
        try {
            return SimpleDateFormat(patron, Locale.getDefault())
                .apply { isLenient = false }
                .parse(raw.trim())
        } catch (_: Exception) { }
    }
    return null
}

fun String.aFechaCorta(): String {
    val d = parsear(this) ?: return this
    return SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(d)
}

fun String.aHoraCorta(): String {
    val d = parsear(this) ?: return this
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
}