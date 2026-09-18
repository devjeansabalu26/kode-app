package com.kode.app.kode_app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.kode.app.kode_app.data.AppDatabaseHelper.Companion.TABLE_EVENTS
import com.kode.app.kode_app.data.AppDatabaseHelper.Companion.TABLE_REGISTRATIONS
import com.kode.app.kode_app.model.Event

class EventRepository(
    context: Context
) {

    private val helper =
        AppDatabaseHelper.getInstance(
            context
        )

    private val users =
        UserRepository(
            context
        )

    fun getEvents(): List<Event> {

        return queryEvents()
    }

    fun getEventById(
        eventId: Int
    ): Event? {

        return queryEvents(
            where = "e.id = ?",
            args = arrayOf(
                eventId.toString()
            )
        ).firstOrNull()
    }

    fun getEventsByCategory(
        category: String
    ): List<Event> {

        return queryEvents(
            where = "e.category = ?",
            args = arrayOf(
                category
            )
        )
    }

    fun getEventsCreatedByUser(
        userId: Long
    ): List<Event> {

        return queryEvents(
            where = "e.creator_id = ?",
            args = arrayOf(
                userId.toString()
            )
        )
    }

    fun getEventsRegisteredByUser(
        userId: Long
    ): List<Event> {

        return queryEvents(
            where = """
                e.id IN (
                    SELECT event_id
                    FROM $TABLE_REGISTRATIONS
                    WHERE user_id = ?
                )
            """.trimIndent(),
            args = arrayOf(
                userId.toString()
            )
        )
    }

    fun getCategories(): List<String> {

        val categories =
            mutableListOf<String>()

        helper
            .readableDatabase
            .rawQuery(
                """
                SELECT DISTINCT category
                FROM $TABLE_EVENTS
                WHERE status = 'PUBLISHED'
                AND category IS NOT NULL
                AND TRIM(category) <> ''
                ORDER BY category ASC
                """.trimIndent(),
                null
            )
            .use {

                while (it.moveToNext()) {

                    categories.add(
                        it.getString(0)
                    )
                }
            }

        return categories
    }

    fun eventExists(
        eventId: Int
    ): Boolean {

        helper
            .readableDatabase
            .rawQuery(
                """
                SELECT id
                FROM $TABLE_EVENTS
                WHERE id = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(
                    eventId.toString()
                )
            )
            .use {

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
        capacity: Int
    ): Long {

        if (creatorId <= 0) {
            return -1L
        }

        if (users.getUserById(creatorId) == null) {
            return -1L
        }

        val values =
            ContentValues().apply {

                put("creator_id", creatorId)
                put("title", title.trim())
                put("description", description.trim())

                put("date", date)
                put("location", location.trim())
                put("category", category.trim())
                put("capacity", capacity)
                put("status", "PUBLISHED")
                put("created_at", currentDateTime())
            }

        return helper
            .writableDatabase
            .insert(
                TABLE_EVENTS,
                null,
                values
            )
    }

    fun updateEvent(
        eventId: Int,
        creatorId: Long,
        title: String,
        description: String,
        date: String,
        location: String,
        category: String,
        capacity: Int
    ): Boolean {

        val event =
            getEventById(
                eventId
            ) ?: return false

        if (event.creatorId != creatorId) {
            return false
        }

        if (capacity < event.registeredCount) {
            return false
        }

        val values =
            ContentValues().apply {

                put("title", title.trim())
                put("description", description.trim())
                put("date", date)
                put("location", location.trim())
                put("category", category.trim())
                put("capacity", capacity)
            }

        val rows =
            helper
                .writableDatabase
                .update(
                    TABLE_EVENTS,
                    values,
                    "id = ? AND creator_id = ?",
                    arrayOf(
                        eventId.toString(),
                        creatorId.toString()
                    )
                )

        return rows > 0
    }

    private fun queryEvents(
        where: String? = null,
        args: Array<String>? = null
    ): List<Event> {

        val events =
            mutableListOf<Event>()

        val extraFilter =
            if (where != null) {
                "AND ($where)"
            } else {
                ""
            }

        helper
            .readableDatabase
            .rawQuery(
                """
                SELECT
                    e.id,
                    e.creator_id,
                    e.title,
                    e.description,
                    e.date,
                    e.location,
                    e.category,
                    e.capacity,

                    (
                        SELECT COUNT(*)
                        FROM $TABLE_REGISTRATIONS r
                        WHERE r.event_id = e.id
                    ) AS registered_count,

                    (
                        SELECT COUNT(*)
                        FROM $TABLE_REGISTRATIONS r
                        WHERE r.event_id = e.id
                        AND r.checked_in = 1
                    ) AS attended_count

                FROM $TABLE_EVENTS e

                WHERE e.status = 'PUBLISHED'
                $extraFilter

                ORDER BY e.date ASC
                """.trimIndent(),
                args
            )
            .use {

                while (it.moveToNext()) {

                    events.add(
                        readEvent(it)
                    )
                }
            }

        return events
    }

    private fun readEvent(
        cursor: Cursor
    ): Event {

        val creatorIndex =
            cursor.getColumnIndexOrThrow(
                "creator_id"
            )

        return Event(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),

            creatorId =
                if (cursor.isNull(creatorIndex)) {
                    null
                } else {
                    cursor.getLong(creatorIndex)
                },

            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
            location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
            capacity = cursor.getInt(cursor.getColumnIndexOrThrow("capacity")),
            registeredCount = cursor.getInt(cursor.getColumnIndexOrThrow("registered_count")),
            attendedCount = cursor.getInt(cursor.getColumnIndexOrThrow("attended_count"))
        )
    }
}
