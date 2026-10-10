package com.kode.app.kode_app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.kode.app.kode_app.data.AppDatabaseHelper.Companion.TABLE_EVENTS
import com.kode.app.kode_app.data.AppDatabaseHelper.Companion.TABLE_REGISTRATIONS
import com.kode.app.kode_app.model.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class EventRepository(context: Context) {
    private val helper = AppDatabaseHelper.getInstance(context)
    private val users = UserRepository(context)

    fun getEvents(): List<Event> = queryEvents()


    fun getPublicEvents(): List<Event> =
        queryEvents(where = "e.is_private = 0")

    fun getEventById(eventId: Int): Event? =
        queryEvents(where = "e.id = ?", args = arrayOf(eventId.toString())).firstOrNull()


    fun getEventByAccessCode(code: String): Event? =
        queryEvents(where = "e.access_code = ?", args = arrayOf(code.trim())).firstOrNull()


    fun getEventsByCategory(category: String): List<Event> =
        queryEvents(where = "e.category = ? AND e.is_private = 0", args = arrayOf(category))

    fun getEventsCreatedByUser(userId: Long): List<Event> =
        queryEvents(where = "e.creator_id = ?", args = arrayOf(userId.toString()))

    fun getEventsRegisteredByUser(userId: Long): List<Event> = queryEvents(
        where = """
            e.id IN (
                SELECT event_id
                FROM $TABLE_REGISTRATIONS
                WHERE user_id = ?
            )
        """.trimIndent(),
        args = arrayOf(userId.toString())
    )

    fun getCategories(): List<String> {
        val categories = mutableListOf<String>()
        helper.readableDatabase.rawQuery("""
            SELECT DISTINCT category
            FROM $TABLE_EVENTS
            WHERE status = 'PUBLISHED'
            AND is_private = 0
            AND category IS NOT NULL
            AND TRIM(category) <> ''
            ORDER BY category ASC
        """.trimIndent(), null).use {
            while (it.moveToNext()) categories.add(it.getString(0))
        }
        return categories
    }

    fun eventExists(eventId: Int): Boolean {
        helper.readableDatabase.rawQuery("""
            SELECT id FROM $TABLE_EVENTS WHERE id = ? LIMIT 1
        """.trimIndent(), arrayOf(eventId.toString())).use {
            return it.moveToFirst()
        }
    }

    fun createEvent(
        creatorId: Long,
        title: String,
        description: String,
        date: String,
        location: String,
        category: String,
        capacity: Int,
        endTime: String = "",
        eventType: String = "Presencial",
        isPrivate: Boolean = false,
        requireDni: Boolean = false,
        isUntransferable: Boolean = false,
        accessCode: String = ""
    ): Long {
        if (creatorId <= 0 || users.getUserById(creatorId) == null) return -1L

        // Generar código único even priv
        val finalAccessCode = if (isPrivate) {
            generateUniqueAccessCode(title)
        } else {
            ""
        }

        val values = ContentValues().apply {
            put("creator_id", creatorId)
            put("title", title.trim())
            put("description", description.trim())
            put("date", date)
            put("end_time", endTime)
            put("location", location.trim())
            put("category", category.trim())
            put("capacity", capacity)
            put("event_type", eventType)
            put("is_private", if (isPrivate) 1 else 0)
            put("require_dni", if (requireDni) 1 else 0)
            put("is_untransferable", if (isUntransferable) 1 else 0)
            put("access_code", finalAccessCode)
            put("status", "PUBLISHED")
            put("created_at", currentDateTime())
        }
        return helper.writableDatabase.insert(TABLE_EVENTS, null, values)
    }

    /**
     * Genera un código automático único basado en las 3 primeras letras del título + 3 dígitos aleatorios ojo acaaaaaaaa.
     * Ejemplo: "Cibercafe inaguracion" -> "CIB123"
     * Verifica que el código no exista previamente en la base de datos */
    private fun generateUniqueAccessCode(title: String): String {

        val cleanTitle = title.trim().replace(Regex("[^a-zA-Z0-9]"), "").uppercase(Locale.getDefault())
        val prefix = when {
            cleanTitle.length >= 3 -> cleanTitle.substring(0, 3)
            cleanTitle.isNotEmpty() -> cleanTitle.padEnd(3, 'X')
            else -> "KOD"
        }

        var candidateCode: String
        var isDuplicate: Boolean

        do {
            val randomNumber = Random.nextInt(100, 1000)
            candidateCode = "$prefix$randomNumber"


            helper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_EVENTS WHERE access_code = ?",
                arrayOf(candidateCode)
            ).use { cursor ->
                isDuplicate = cursor.moveToFirst() && cursor.getInt(0) > 0
            }
        } while (isDuplicate)

        return candidateCode
    }

    private fun queryEvents(where: String? = null, args: Array<String>? = null): List<Event> {
        val events = mutableListOf<Event>()
        val extraFilter = if (where != null) "AND ($where)" else ""

        helper.readableDatabase.rawQuery("""
            SELECT
                e.id, e.creator_id, e.title, e.description, e.date, e.end_time,
                e.location, e.category, e.capacity, e.event_type, e.is_private,
                e.require_dni, e.is_untransferable, e.access_code,
                (SELECT COUNT(*) FROM $TABLE_REGISTRATIONS r WHERE r.event_id = e.id) AS registered_count,
                (SELECT COUNT(*) FROM $TABLE_REGISTRATIONS r WHERE r.event_id = e.id AND r.checked_in = 1) AS attended_count
            FROM $TABLE_EVENTS e
            WHERE e.status = 'PUBLISHED'
            $extraFilter
            ORDER BY e.date ASC
        """.trimIndent(), args).use {
            while (it.moveToNext()) events.add(readEvent(it))
        }
        return events
    }

    private fun readEvent(cursor: Cursor): Event {
        val creatorIndex = cursor.getColumnIndexOrThrow("creator_id")
        return Event(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
            creatorId = if (cursor.isNull(creatorIndex)) null else cursor.getLong(creatorIndex),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
            endTime = cursor.getString(cursor.getColumnIndexOrThrow("end_time")),
            location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
            capacity = cursor.getInt(cursor.getColumnIndexOrThrow("capacity")),
            eventType = cursor.getString(cursor.getColumnIndexOrThrow("event_type")) ?: "Presencial",
            isPrivate = cursor.getInt(cursor.getColumnIndexOrThrow("is_private")) == 1,
            requireDni = cursor.getInt(cursor.getColumnIndexOrThrow("require_dni")) == 1,
            isUntransferable = cursor.getInt(cursor.getColumnIndexOrThrow("is_untransferable")) == 1,
            accessCode = cursor.getString(cursor.getColumnIndexOrThrow("access_code")),
            registeredCount = cursor.getInt(cursor.getColumnIndexOrThrow("registered_count")),
            attendedCount = cursor.getInt(cursor.getColumnIndexOrThrow("attended_count"))
        )
    }

    private fun currentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}