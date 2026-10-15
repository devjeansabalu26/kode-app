package com.kode.app.kode_app.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kode.app.kode_app.db.entity.UserEntity
import com.kode.app.kode_app.model.User

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertar(user: UserEntity): Long

    @Query("SELECT id, name, dni, phone, gender, age, email FROM users WHERE id = :userId LIMIT 1")
    fun obtenerPorId(userId: Long): User?

    @Query("SELECT id, name, dni, phone, gender, age, email FROM users WHERE id = :userId LIMIT 1")
    fun observarPorId(userId: Long): LiveData<User?>

    @Query("SELECT id, name, dni, phone, gender, age, email FROM users WHERE email = :email AND password_hash = :passwordHash LIMIT 1")
    fun login(email: String, passwordHash: String): User?

    @Query("SELECT COUNT(*) > 0 FROM users WHERE email = :email")
    fun existeEmail(email: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM users WHERE dni = :dni")
    fun existeDni(dni: String): Boolean
}
