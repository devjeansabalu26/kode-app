package com.kode.app.kode_app.data.remote

import com.kode.app.kode_app.data.remote.dto.CategoryRow
import com.kode.app.kode_app.data.remote.dto.EventDto
import com.kode.app.kode_app.data.remote.dto.EventWriteDto
import com.kode.app.kode_app.data.remote.dto.IdRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * Consultas de eventos. Las lecturas van a la vista `events_with_counts` (evento + inscritos + asistentes);
 * las escrituras van a la tabla `events`, donde RLS decide quién puede crear o editar.
 */
class EventRemoteDataSource(private val client: SupabaseClient = SupabaseProvider.client) {
    private val counts get() = client.postgrest.from("events_with_counts")

    suspend fun list(): List<EventDto> {
        return counts.select { order("event_date", Order.ASCENDING) }.decodeList<EventDto>()
    }

    suspend fun byId(eventId: Long): EventDto? {
        return counts.select { filter { eq("id", eventId) } }.decodeSingleOrNull<EventDto>()
    }

    suspend fun byCategory(category: String): List<EventDto> {
        return counts.select {
            filter { eq("category", category) }
            order("event_date", Order.ASCENDING)
        }.decodeList<EventDto>()
    }

    suspend fun createdBy(userId: String): List<EventDto> {
        return counts.select {
            filter { eq("creator_id", userId) }
            order("event_date", Order.ASCENDING)
        }.decodeList<EventDto>()
    }

    suspend fun byIds(ids: List<Long>): List<EventDto> {
        if (ids.isEmpty()) return emptyList()
        return counts.select {
            filter { isIn("id", ids) }
            order("event_date", Order.ASCENDING)
        }.decodeList<EventDto>()
    }

    suspend fun categories(): List<String> {
        return counts.select(Columns.list("category")).decodeList<CategoryRow>().map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    /** `creator_id` lo pone la BD con `auth.uid()`; RLS rechaza cualquier otro valor. */
    suspend fun create(event: EventWriteDto) {
        client.postgrest.from("events").insert(event)
    }

    /** Devuelve cuántas filas se actualizaron (0 = el evento no existe o no eres su creador). */
    suspend fun update(eventId: Long, event: EventWriteDto): Int {
        return client.postgrest.from("events").update(event) {
            select(Columns.list("id"))
            filter { eq("id", eventId) }
        }.decodeList<IdRow>().size
    }
}
