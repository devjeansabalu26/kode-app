package com.kode.app.kode_app.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "registrations",
    foreignKeys = [
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"]),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"])
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["user_id"]),
        Index(value = ["qr_code"], unique = true)
    ]
)
data class RegistrationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "event_id")
    val eventId: Int,
    @ColumnInfo(name = "user_id")
    val userId: Long?,
    @ColumnInfo(name = "user_dni")
    val userDni: String,
    @ColumnInfo(name = "user_name")
    val userName: String,
    @ColumnInfo(name = "qr_code")
    val qrCode: String,
    val status: String = "PREINSCRITO",
    @ColumnInfo(name = "checked_in")
    val checkedIn: Boolean = false,
    @ColumnInfo(name = "registered_at")
    val registeredAt: String,
    @ColumnInfo(name = "checked_in_at")
    val checkedInAt: String? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0
)
