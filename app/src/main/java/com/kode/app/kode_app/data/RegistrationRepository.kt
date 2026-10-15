package com.kode.app.kode_app.data

import android.content.Context
import com.kode.app.kode_app.db.KodeRoomDatabase
import com.kode.app.kode_app.db.entity.RegistrationEntity
import com.kode.app.kode_app.model.AttendeeItem
import com.kode.app.kode_app.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RegistrationRepository(context: Context) {
    private val registrationDao = KodeRoomDatabase.getDatabase(context).registrationDao()
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
        val registration = RegistrationEntity(
            eventId = eventId,
            userId = userId,
            userDni = "",
            userName = "",
            qrCode = qrCode,
            registeredAt = currentDateTime()
        )
        val result = registrationDao.insertar(registration)
        return if (result != -1L) qrCode else null
    }

    fun getRegistrationQr(eventId: Int, userId: Long): String? {
        return registrationDao.obtenerQr(eventId, userId)
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
        val registration = registrationDao.obtenerPorQr(eventId, qrCode)
            ?: return ScanResult.NotFound(qrCode)

        val name = registration.userName.ifEmpty { "Asistente" }
        return if (registration.status == "ASISTIO_REAL") {
            ScanResult.AlreadyCheckedIn(name, registration.checkedInAt ?: "")
        } else {
            val success = registrationDao.marcarAsistencia(eventId, qrCode, currentDateTime()) > 0
            if (success) {
                ScanResult.Success(name, registration.userDni)
            } else {
                ScanResult.Error("No se pudo actualizar la asistencia.")
            }
        }
    }

    fun registerExpressUser(eventId: Int, name: String, dni: String): Boolean {
        val now = currentDateTime()
        val registration = RegistrationEntity(
            eventId = eventId,
            userId = null,
            userDni = dni,
            userName = name,
            qrCode = "KODE-E$eventId-EXPRESS-$dni",
            status = "ASISTIO_REAL",
            checkedIn = true,
            registeredAt = now,
            checkedInAt = now
        )
        return registrationDao.insertar(registration) != -1L
    }

    fun getAttendanceCount(eventId: Int): Pair<Int, Int> {
        val count = registrationDao.contarAsistencia(eventId)
        return Pair(count.attended, count.registered)
    }

    fun updateAttendanceStatus(eventId: Int, userId: Long, newStatus: String) {
        registrationDao.actualizarEstado(eventId, userId, newStatus)
    }

    fun getAttendeesForEvent(eventId: Int): List<AttendeeItem> {
        return registrationDao.listarAsistentes(eventId).map { row ->
            val user = User(
                id = row.id,
                name = row.name,
                email = row.email,
                dni = row.dni,
                age = row.age,
                gender = row.gender,
                phone = row.phone
            )
            AttendeeItem(user = user, ticketCode = row.qrCode, status = row.status)
        }
    }
}
