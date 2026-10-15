package com.kode.app.kode_app.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kode.app.kode_app.db.entity.EventEntity
import com.kode.app.kode_app.model.Event

private const val EVENT_SELECT = """
    SELECT
        e.id, e.creator_id AS creatorId, e.title, e.description, e.date, e.end_time AS endTime,
        e.location, e.category, e.capacity, e.event_type AS eventType, e.is_private AS isPrivate,
        e.require_dni AS requireDni, e.is_untransferable AS isUntransferable, e.access_code AS accessCode,
        e.latitude, e.longitude, e.image_url AS imageUrl,
        (SELECT COUNT(*) FROM registrations r WHERE r.event_id = e.id) AS registeredCount,
        (SELECT COUNT(*) FROM registrations r WHERE r.event_id = e.id AND r.checked_in = 1) AS attendedCount
    FROM events e
    WHERE e.status = 'PUBLISHED'
"""

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertar(event: EventEntity): Long

    @Query("$EVENT_SELECT ORDER BY e.date ASC")
    fun listarTodos(): List<Event>

    @Query("$EVENT_SELECT AND e.is_private = 0 ORDER BY e.date ASC")
    fun listarPublicos(): List<Event>

    @Query("$EVENT_SELECT AND e.id = :eventId LIMIT 1")
    fun obtenerPorId(eventId: Int): Event?

    @Query("$EVENT_SELECT AND e.access_code = :code LIMIT 1")
    fun obtenerPorCodigo(code: String): Event?

    @Query("$EVENT_SELECT AND e.category = :category AND e.is_private = 0 ORDER BY e.date ASC")
    fun listarPorCategoria(category: String): List<Event>

    @Query("$EVENT_SELECT AND e.creator_id = :userId ORDER BY e.date ASC")
    fun listarCreadosPor(userId: Long): List<Event>

    @Query("$EVENT_SELECT AND e.id IN (SELECT event_id FROM registrations WHERE user_id = :userId) ORDER BY e.date ASC")
    fun listarInscritosPor(userId: Long): List<Event>

    @Query("""
        SELECT DISTINCT category FROM events
        WHERE status = 'PUBLISHED' AND is_private = 0
        AND category IS NOT NULL AND TRIM(category) <> ''
        ORDER BY category ASC
    """)
    fun listarCategorias(): List<String>

    @Query("SELECT COUNT(*) > 0 FROM events WHERE id = :eventId")
    fun existe(eventId: Int): Boolean

    @Query("SELECT COUNT(*) > 0 FROM events WHERE access_code = :code")
    fun existeCodigo(code: String): Boolean
}
