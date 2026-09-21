package com.kode.app.kode_app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fila de `profiles`. El `email` lo copia el trigger desde Supabase Auth (la app nunca lo envía). */
@Serializable
data class ProfileDto(val id: String, val name: String, val dni: String, val phone: String, val gender: String, val age: Int, val email: String)

/** Fila de la vista `events_with_counts` (evento + inscritos y asistentes calculados en la BD). */
@Serializable
data class EventDto(
    val id: Long,
    @SerialName("creator_id") val creatorId: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("event_date") val eventDate: String,
    val location: String,
    val category: String,
    val capacity: Int,
    @SerialName("registered_count") val registeredCount: Int = 0,
    @SerialName("attended_count") val attendedCount: Int = 0
)

/** Lo que la app envía al crear/editar. `creator_id` y `status` NUNCA se envían: los pone la base de datos. */
@Serializable
data class EventWriteDto(
    val title: String,
    val description: String,
    @SerialName("event_date") val eventDate: String,
    val location: String,
    val category: String,
    val capacity: Int
)

@Serializable
data class CategoryRow(val category: String)

@Serializable
data class IdRow(val id: Long)

@Serializable
data class RegistrationQrRow(@SerialName("qr_code") val qrCode: String)

@Serializable
data class RegistrationEventRow(@SerialName("event_id") val eventId: Long)
