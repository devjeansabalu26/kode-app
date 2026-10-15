package com.kode.app.kode_app.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["dni"], unique = true), Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dni: String,
    val phone: String,
    val gender: String,
    val age: Int,
    val email: String,
    @ColumnInfo(name = "password_hash")
    val passwordHash: String,
    val role: String = "ASISTENTE",
    @ColumnInfo(name = "created_at")
    val createdAt: String
)
