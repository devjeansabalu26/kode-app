package com.kode.app.kode_app.data.remote

import com.kode.app.kode_app.data.remote.dto.RegistrationEventRow
import com.kode.app.kode_app.data.remote.dto.RegistrationQrRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Inscripciones. Las lecturas van a `registrations` (RLS: solo las tuyas); inscribirse va por una función de la BD. */
class RegistrationRemoteDataSource(private val client: SupabaseClient = SupabaseProvider.client) {
    /**
     * Llama a la función `register_for_event`, que en UNA transacción bloquea el evento, valida
     * (creador, duplicado, aforo) e inserta. Devuelve el código QR. Así dos usuarios simultáneos no pueden superar el aforo.
     */
    suspend fun register(eventId: Long): String {
        return client.postgrest.rpc("register_for_event", buildJsonObject { put("p_event_id", eventId) }).decodeAs<String>()
    }

    suspend fun qrFor(eventId: Long, userId: String): String? {
        return client.postgrest.from("registrations").select(Columns.list("qr_code")) {
            filter {
                eq("event_id", eventId)
                eq("user_id", userId)
            }
        }.decodeSingleOrNull<RegistrationQrRow>()?.qrCode
    }

    suspend fun eventIdsFor(userId: String): List<Long> {
        return client.postgrest.from("registrations").select(Columns.list("event_id")) { filter { eq("user_id", userId) } }.decodeList<RegistrationEventRow>().map { it.eventId }
    }
}
