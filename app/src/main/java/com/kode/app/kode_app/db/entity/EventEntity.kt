package com.kode.app.kode_app.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["creator_id"])
    ],
    indices = [Index(value = ["creator_id"])]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "creator_id")
    val creatorId: Long?,
    val title: String,
    val description: String,
    val date: String,
    @ColumnInfo(name = "end_time")
    val endTime: String?,
    val location: String,
    val category: String,
    val capacity: Int,
    @ColumnInfo(name = "event_type")
    val eventType: String = "Presencial",
    @ColumnInfo(name = "is_private")
    val isPrivate: Boolean = false,
    @ColumnInfo(name = "require_dni")
    val requireDni: Boolean = false,
    @ColumnInfo(name = "is_untransferable")
    val isUntransferable: Boolean = false,
    val status: String = "PUBLISHED",
    @ColumnInfo(name = "access_code")
    val accessCode: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: String
)
