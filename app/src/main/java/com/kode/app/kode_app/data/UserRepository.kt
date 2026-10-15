package com.kode.app.kode_app.data

import android.content.Context
import androidx.lifecycle.LiveData
import com.kode.app.kode_app.db.KodeRoomDatabase
import com.kode.app.kode_app.db.entity.UserEntity
import com.kode.app.kode_app.model.User
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserRepository(context: Context) {
    private val userDao = KodeRoomDatabase.getDatabase(context).userDao()

    fun registerUser(
        name: String,
        dni: String,
        phone: String,
        gender: String,
        age: Int,
        email: String,
        password: String
    ): Long {
        val user = UserEntity(
            name = name.trim(),
            dni = dni.trim(),
            phone = phone.trim(),
            gender = gender.trim(),
            age = age,
            email = email.trim().lowercase(),
            passwordHash = hashPassword(password),
            createdAt = currentDateTime()
        )
        return userDao.insertar(user)
    }

    fun userExistsByEmail(email: String): Boolean {
        return userDao.existeEmail(email.trim().lowercase())
    }

    fun userExistsByDni(dni: String): Boolean {
        return userDao.existeDni(dni.trim())
    }

    fun login(email: String, password: String): User? {
        return userDao.login(email.trim().lowercase(), hashPassword(password))
    }

    fun getUserById(userId: Long): User? {
        return userDao.obtenerPorId(userId)
    }

    fun observeUser(userId: Long): LiveData<User?> {
        return userDao.observarPorId(userId)
    }

    private fun hashPassword(password: String): String {
        return MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }
    }

    private fun currentDateTime(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }
}
