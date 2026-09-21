package com.kode.app.kode_app.model

import java.time.Instant

/** Evento tal como lo usa la UI. `registeredCount` y `attendedCount` son datos calculados, no columnas de la tabla. */
data class Event(
    val id: Long,
    val creatorId: String?,
    val title: String,
    val description: String,
    val date: Instant,
    val location: String,
    val category: String,
    val capacity: Int,
    val registeredCount: Int = 0,
    val attendedCount: Int = 0
)
