package com.kode.app.kode_app.data

import android.content.ContentValues
import android.content.Context
import com.kode.app.kode_app.data.AppDatabaseHelper.Companion.TABLE_REGISTRATIONS
import java.util.UUID

class RegistrationRepository(
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

    private val events =
        EventRepository(
            context
        )

    fun registerUserInEvent(
        eventId: Int,
        userId: Long
    ): String? {

        if (
            eventId <= 0 ||
            userId <= 0
        ) {
            return null
        }

        if (users.getUserById(userId) == null) {
            return null
        }

        val event =
            events.getEventById(
                eventId
            ) ?: return null

        if (event.creatorId == userId) {
            return null
        }

        val existingQr =
            getRegistrationQr(
                eventId,
                userId
            )

        if (existingQr != null) {
            return existingQr
        }

        if (event.registeredCount >= event.capacity) {
            return null
        }

        val qrCode =
            generateQrCode(
                eventId,
                userId
            )

        val values =
            ContentValues().apply {

                put("event_id", eventId)
                put("user_id", userId)
                put("qr_code", qrCode)
                put("checked_in", 0)
                put("registered_at", currentDateTime())
            }

        val result =
            helper
                .writableDatabase
                .insert(
                    TABLE_REGISTRATIONS,
                    null,
                    values
                )

        return if (result != -1L) {
            qrCode
        } else {
            null
        }
    }

    fun getRegistrationQr(
        eventId: Int,
        userId: Long
    ): String? {

        helper
            .readableDatabase
            .rawQuery(
                """
                SELECT qr_code
                FROM $TABLE_REGISTRATIONS
                WHERE event_id = ?
                AND user_id = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(
                    eventId.toString(),
                    userId.toString()
                )
            )
            .use {

                return if (it.moveToFirst()) {
                    it.getString(0)
                } else {
                    null
                }
            }
    }

    fun isUserRegisteredInEvent(
        eventId: Int,
        userId: Long
    ): Boolean {

        return getRegistrationQr(
            eventId = eventId,
            userId = userId
        ) != null
    }

    private fun generateQrCode(
        eventId: Int,
        userId: Long
    ): String {

        val random =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(8)
                .uppercase()

        return "KODE-E$eventId-U$userId-$random"
    }
}
