package com.kode.app.kode_app.data

import com.google.firebase.firestore.FieldValue
import com.kode.app.kode_app.model.AttendeeItem
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RegistrationRepository {
    private val db = FirestoreDb.db
    private val registrations = db.collection(FirestoreDb.REGISTRATIONS)
    private val eventsRef = db.collection(FirestoreDb.EVENTS)
    private val users = UserRepository()
    private val events = EventRepository()

    private fun userRegistrationRef(eventId: Int, userId: Long) =
        registrations.document("E${eventId}_U$userId")

    suspend fun registerUserInEvent(eventId: Int, userId: Long): String? = firestoreCall(null) {
        if (eventId <= 0 || userId <= 0) return@firestoreCall null
        if (users.getUserById(userId) == null) return@firestoreCall null
        val event = events.getEventById(eventId) ?: return@firestoreCall null
        if (event.creatorId == userId) return@firestoreCall null

        val qrCode = generateQrCode(eventId, userId)
        val registrationRef = userRegistrationRef(eventId, userId)
        val eventRef = eventsRef.document(eventId.toString())

        db.runTransaction<String?> { transaction ->
            val existing = transaction.get(registrationRef)
            if (existing.exists()) return@runTransaction existing.getString("qrCode")

            val eventSnapshot = transaction.get(eventRef)
            val registered = eventSnapshot.getLong("registeredCount") ?: 0L
            val capacity = eventSnapshot.getLong("capacity") ?: 0L
            if (registered >= capacity) return@runTransaction null

            transaction.set(
                registrationRef, mapOf(
                    "eventId" to eventId.toLong(),
                    "userId" to userId,
                    "userDni" to "",
                    "userName" to "",
                    "qrCode" to qrCode,
                    "status" to STATUS_PRE_REGISTERED,
                    "checkedIn" to false,
                    "registeredAt" to FirestoreDb.currentDateTime(),
                    "checkedInAt" to null
                )
            )
            transaction.update(eventRef, "registeredCount", FieldValue.increment(1))
            qrCode
        }.await()
    }

    suspend fun getRegistrationQr(eventId: Int, userId: Long): String? = firestoreCall(null) {
        userRegistrationRef(eventId, userId).get().await()
            .takeIf { it.exists() }?.getString("qrCode")
    }

    suspend fun isUserRegisteredInEvent(eventId: Int, userId: Long): Boolean {
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

    suspend fun processQrScan(eventId: Int, qrCode: String): ScanResult =
        firestoreCall(ScanResult.Error("No se pudo actualizar la asistencia.")) {
            val registrationRef = registrations
                .whereEqualTo("eventId", eventId.toLong())
                .whereEqualTo("qrCode", qrCode)
                .limit(1)
                .get().await()
                .documents.firstOrNull()?.reference
                ?: return@firestoreCall ScanResult.NotFound(qrCode)
            val eventRef = eventsRef.document(eventId.toString())

            db.runTransaction<ScanResult> { transaction ->
                val registration = transaction.get(registrationRef)
                val name = registration.getString("userName").orEmpty().ifEmpty { "Asistente" }
                if (registration.getString("status") == STATUS_ATTENDED) {
                    return@runTransaction ScanResult.AlreadyCheckedIn(name, registration.getString("checkedInAt").orEmpty())
                }
                transaction.update(
                    registrationRef, mapOf(
                        "status" to STATUS_ATTENDED,
                        "checkedIn" to true,
                        "checkedInAt" to FirestoreDb.currentDateTime()
                    )
                )
                transaction.update(eventRef, "attendedCount", FieldValue.increment(1))
                ScanResult.Success(name, registration.getString("userDni").orEmpty())
            }.await()
        }

    suspend fun registerExpressUser(eventId: Int, name: String, dni: String): Boolean = firestoreCall(false) {
        val now = FirestoreDb.currentDateTime()
        val qrCode = "KODE-E$eventId-EXPRESS-$dni"
        val registrationRef = registrations.document(qrCode)
        val eventRef = eventsRef.document(eventId.toString())

        db.runTransaction { transaction ->
            if (transaction.get(registrationRef).exists()) return@runTransaction false
            transaction.set(
                registrationRef, mapOf(
                    "eventId" to eventId.toLong(),
                    "userId" to null,
                    "userDni" to dni,
                    "userName" to name,
                    "qrCode" to qrCode,
                    "status" to STATUS_ATTENDED,
                    "checkedIn" to true,
                    "registeredAt" to now,
                    "checkedInAt" to now
                )
            )
            transaction.update(
                eventRef, mapOf(
                    "registeredCount" to FieldValue.increment(1),
                    "attendedCount" to FieldValue.increment(1)
                )
            )
            true
        }.await()
    }

    suspend fun getAttendanceCount(eventId: Int): Pair<Int, Int> {
        val event = events.getEventById(eventId) ?: return Pair(0, 0)
        return Pair(event.attendedCount, event.registeredCount)
    }

    suspend fun updateAttendanceStatus(eventId: Int, userId: Long, newStatus: String) = firestoreCall(Unit) {
        val registrationRef = userRegistrationRef(eventId, userId)
        val eventRef = eventsRef.document(eventId.toString())
        val (status, checkedIn) = when (newStatus) {
            "ATTENDED" -> STATUS_ATTENDED to true
            "ABSENT" -> "ABSENT" to false
            else -> STATUS_PRE_REGISTERED to false
        }

        db.runTransaction { transaction ->
            val registration = transaction.get(registrationRef)
            if (!registration.exists()) return@runTransaction
            val wasCheckedIn = registration.getBoolean("checkedIn") ?: false

            transaction.update(
                registrationRef, mapOf(
                    "status" to status,
                    "checkedIn" to checkedIn,
                    "checkedInAt" to if (checkedIn) FirestoreDb.currentDateTime() else null
                )
            )
            if (wasCheckedIn != checkedIn) {
                transaction.update(eventRef, "attendedCount", FieldValue.increment(if (checkedIn) 1 else -1))
            }
        }.await()
    }

    suspend fun getAttendeesForEvent(eventId: Int): List<AttendeeItem> = firestoreCall(emptyList()) {
        val rows = registrations.whereEqualTo("eventId", eventId.toLong()).get().await()
            .documents.filter { it.getLong("userId") != null }
        val usersById = FirestoreDb.getByIds(FirestoreDb.USERS, rows.mapNotNull { it.getLong("userId") })
            .associate { it.getLong("id") to it.toUser() }

        rows.mapNotNull { row ->
            val user = usersById[row.getLong("userId")] ?: return@mapNotNull null
            AttendeeItem(
                user = user,
                ticketCode = row.getString("qrCode").orEmpty(),
                status = row.getString("status").orEmpty()
            )
        }
    }

    companion object {
        private const val STATUS_PRE_REGISTERED = "PREINSCRITO"
        private const val STATUS_ATTENDED = "ASISTIO_REAL"
    }
}
