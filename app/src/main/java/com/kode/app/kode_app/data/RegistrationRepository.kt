package com.kode.app.kode_app.data

import android.content.ContentValues
import android.content.Context
import com.kode.app.kode_app.data.AppDatabaseHelper.Companion.TABLE_REGISTRATIONS
import com.kode.app.kode_app.model.AttendeeItem
import com.kode.app.kode_app.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RegistrationRepository(context: Context) {
    private val helper = AppDatabaseHelper.getInstance(context)
    private val users = UserRepository(context)
    private val events = EventRepository(context)

    private fun currentDateTime(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    fun registerUserInEvent(eventId: Int, userId: Long): String? {
        if (eventId <= 0 || userId <= 0) return null
        if (users.getUserById(userId) == null) return null
        val event = events.getEventById(eventId) ?: return null
        if (event.creatorId == userId) return null

        val existingQr = getRegistrationQr(eventId, userId)
        if (existingQr != null) return existingQr

        if (event.registeredCount >= event.capacity) return null

        val qrCode = generateQrCode(eventId, userId)
        val values = ContentValues().apply {
            put("event_id", eventId)
            put("user_id", userId)
            put("user_dni", "")
            put("user_name", "")
            put("qr_code", qrCode)
            put("status", "PREINSCRITO")
            put("checked_in", 0)
            put("registered_at", currentDateTime())
            put("sync_status", 0)
        }
        val result = helper.writableDatabase.insert(TABLE_REGISTRATIONS, null, values)
        return if (result != -1L) qrCode else null
    }

    fun getRegistrationQr(eventId: Int, userId: Long): String? {
        helper.readableDatabase.rawQuery("""
            SELECT qr_code
            FROM $TABLE_REGISTRATIONS
            WHERE event_id = ? AND user_id = ?
            LIMIT 1
        """.trimIndent(), arrayOf(eventId.toString(), userId.toString())).use {
            return if (it.moveToFirst()) it.getString(0) else null
        }
    }

    fun isUserRegisteredInEvent(eventId: Int, userId: Long): Boolean {
        return getRegistrationQr(eventId = eventId, userId = userId) != null
    }

    private fun generateQrCode(eventId: Int, userId: Long): String {
        val random = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        return "KODE-E$eventId-U$userId-$random"
    }

    sealed class ScanResult {
        data class Success(val userName: String, val dni: String) : ScanResult()
        data class AlreadyCheckedIn(val userName: String, val checkinTime: String) : ScanResult()
        data class NotFound(val qrCode: String) : ScanResult()
        data class Error(val message: String) : ScanResult()
    }

    fun processQrScan(eventId: Int, qrCode: String): ScanResult {
        val db = helper.readableDatabase
        val query = """
            SELECT user_name, user_dni, status, checked_in_at
            FROM $TABLE_REGISTRATIONS
            WHERE event_id = ? AND qr_code = ?
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(eventId.toString(), qrCode))

        return if (cursor.moveToFirst()) {
            val name = cursor.getString(0).ifEmpty { "Asistente" }
            val dni = cursor.getString(1)
            val status = cursor.getString(2)
            val checkinTime = cursor.getString(3) ?: ""
            cursor.close()

            if (status == "ASISTIO_REAL") {
                ScanResult.AlreadyCheckedIn(name, checkinTime)
            } else {
                val success = markAttendance(eventId, qrCode)
                if (success) {
                    ScanResult.Success(name, dni)
                } else {
                    ScanResult.Error("No se pudo actualizar la asistencia.")
                }
            }
        } else {
            cursor.close()
            ScanResult.NotFound(qrCode)
        }
    }

    private fun markAttendance(eventId: Int, qrCode: String): Boolean {
        val db = helper.writableDatabase
        val now = currentDateTime()

        val values = ContentValues().apply {
            put("status", "ASISTIO_REAL")
            put("checked_in", 1)
            put("checked_in_at", now)
            put("sync_status", 0)
        }

        val rows = db.update(
            TABLE_REGISTRATIONS,
            values,
            "event_id = ? AND qr_code = ?",
            arrayOf(eventId.toString(), qrCode)
        )
        return rows > 0
    }

    fun registerExpressUser(eventId: Int, name: String, dni: String): Boolean {
        val db = helper.writableDatabase
        val now = currentDateTime()
        val generatedQr = "KODE-E$eventId-EXPRESS-$dni"

        val values = ContentValues().apply {
            put("event_id", eventId)
            put("user_dni", dni)
            put("user_name", name)
            put("qr_code", generatedQr)
            put("status", "ASISTIO_REAL")
            put("checked_in", 1)
            put("registered_at", now)
            put("checked_in_at", now)
            put("sync_status", 0)
        }

        val id = db.insert(TABLE_REGISTRATIONS, null, values)
        return id != -1L
    }

    fun getAttendanceCount(eventId: Int): Pair<Int, Int> {
        val db = helper.readableDatabase
        var totalRegistered = 0
        var totalAttended = 0

        val query = """
            SELECT 
                COUNT(*),
                SUM(CASE WHEN status = 'ASISTIO_REAL' OR checked_in = 1 THEN 1 ELSE 0 END)
            FROM $TABLE_REGISTRATIONS
            WHERE event_id = ?
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(eventId.toString()))
        if (cursor.moveToFirst()) {
            totalRegistered = cursor.getInt(0)
            totalAttended = cursor.getInt(1)
        }
        cursor.close()

        return Pair(totalAttended, totalRegistered)
    }

    fun updateAttendanceStatus(eventId: Int, userId: Long, newStatus: String) {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("status", newStatus)
        }
        db.update(
            TABLE_REGISTRATIONS,
            values,
            "event_id = ? AND user_id = ?",
            arrayOf(eventId.toString(), userId.toString())
        )
    }

    fun getAttendeesForEvent(eventId: Int): List<AttendeeItem> {
        val attendeeList = mutableListOf<AttendeeItem>()
        val db = helper.readableDatabase

        val query = """
        SELECT 
            u.id, 
            u.name, 
            u.email, 
            u.dni, 
            u.age, 
            u.gender, 
            u.phone, 
            r.qr_code, 
            r.status 
        FROM $TABLE_REGISTRATIONS r
        INNER JOIN users u ON r.user_id = u.id
        WHERE r.event_id = ?
    """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(eventId.toString()))
        if (cursor.moveToFirst()) {
            do {
                val userId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: ""
                val email = cursor.getString(cursor.getColumnIndexOrThrow("email")) ?: ""

                val dniIndex = cursor.getColumnIndex("dni")
                val dni = if (dniIndex != -1) cursor.getString(dniIndex) ?: "" else ""

                val ageIndex = cursor.getColumnIndex("age")
                val age = if (ageIndex != -1) cursor.getInt(ageIndex) else 0

                val genderIndex = cursor.getColumnIndex("gender")
                val gender = if (genderIndex != -1) cursor.getString(genderIndex) ?: "" else ""

                val phoneIndex = cursor.getColumnIndex("phone")
                val phone = if (phoneIndex != -1) cursor.getString(phoneIndex) ?: "" else ""

                val qrCode = cursor.getString(cursor.getColumnIndexOrThrow("qr_code")) ?: ""
                val status = cursor.getString(cursor.getColumnIndexOrThrow("status")) ?: "PENDING"

                val user = User(
                    id = userId,
                    name = name,
                    email = email,
                    dni = dni,
                    age = age,
                    gender = gender,
                    phone = phone
                )

                attendeeList.add(AttendeeItem(user = user, ticketCode = qrCode, status = status))
            } while (cursor.moveToNext())
        }
        cursor.close()

        return attendeeList
    }
}