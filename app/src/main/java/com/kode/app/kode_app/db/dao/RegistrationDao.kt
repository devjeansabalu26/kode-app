package com.kode.app.kode_app.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kode.app.kode_app.db.entity.RegistrationEntity

@Dao
interface RegistrationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertar(registration: RegistrationEntity): Long

    @Query("SELECT qr_code FROM registrations WHERE event_id = :eventId AND user_id = :userId LIMIT 1")
    fun obtenerQr(eventId: Int, userId: Long): String?

    @Query("SELECT * FROM registrations WHERE event_id = :eventId AND qr_code = :qrCode LIMIT 1")
    fun obtenerPorQr(eventId: Int, qrCode: String): RegistrationEntity?

    @Query("""
        UPDATE registrations
        SET status = 'ASISTIO_REAL', checked_in = 1, checked_in_at = :now, sync_status = 0
        WHERE event_id = :eventId AND qr_code = :qrCode
    """)
    fun marcarAsistencia(eventId: Int, qrCode: String, now: String): Int

    @Query("UPDATE registrations SET status = :newStatus WHERE event_id = :eventId AND user_id = :userId")
    fun actualizarEstado(eventId: Int, userId: Long, newStatus: String)

    @Query("""
        SELECT
            COUNT(*) AS registered,
            COALESCE(SUM(CASE WHEN status = 'ASISTIO_REAL' OR checked_in = 1 THEN 1 ELSE 0 END), 0) AS attended
        FROM registrations
        WHERE event_id = :eventId
    """)
    fun contarAsistencia(eventId: Int): AttendanceCount

    @Query("""
        SELECT u.id, u.name, u.email, u.dni, u.age, u.gender, u.phone, r.qr_code AS qrCode, r.status
        FROM registrations r
        INNER JOIN users u ON r.user_id = u.id
        WHERE r.event_id = :eventId
    """)
    fun listarAsistentes(eventId: Int): List<AttendeeRow>
}

data class AttendanceCount(
    val registered: Int,
    val attended: Int
)

data class AttendeeRow(
    val id: Long,
    val name: String,
    val email: String,
    val dni: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val qrCode: String,
    val status: String
)
