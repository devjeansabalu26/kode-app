package com.kode.app.kode_app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.kode.app.kode_app.data.AppDatabaseHelper.Companion.TABLE_USERS
import com.kode.app.kode_app.model.User
import java.security.MessageDigest

class UserRepository(
    context: Context
) {

    private val helper =
        AppDatabaseHelper.getInstance(
            context
        )

    private val userColumns =
        "id, name, dni, phone, gender, age, email"

    fun registerUser(
        name: String,
        dni: String,
        phone: String,
        gender: String,
        age: Int,
        email: String,
        password: String
    ): Long {

        val values =
            ContentValues().apply {

                put("name", name.trim())
                put("dni", dni.trim())
                put("phone", phone.trim())
                put("gender", gender.trim())
                put("age", age)
                put("email", email.trim().lowercase())
                put("password_hash", hashPassword(password))
                put("created_at", currentDateTime())
            }

        return helper
            .writableDatabase
            .insert(
                TABLE_USERS,
                null,
                values
            )
    }

    fun userExistsByEmail(
        email: String
    ): Boolean {

        return exists(
            column = "email",
            value = email.trim().lowercase()
        )
    }

    fun userExistsByDni(
        dni: String
    ): Boolean {

        return exists(
            column = "dni",
            value = dni.trim()
        )
    }

    fun login(
        email: String,
        password: String
    ): User? {

        return findUser(
            where = "email = ? AND password_hash = ?",
            args = arrayOf(
                email.trim().lowercase(),
                hashPassword(password)
            )
        )
    }

    fun getUserById(
        userId: Long
    ): User? {

        return findUser(
            where = "id = ?",
            args = arrayOf(
                userId.toString()
            )
        )
    }

    private fun findUser(
        where: String,
        args: Array<String>
    ): User? {

        helper
            .readableDatabase
            .rawQuery(
                """
                SELECT $userColumns
                FROM $TABLE_USERS
                WHERE $where
                LIMIT 1
                """.trimIndent(),
                args
            )
            .use {

                return if (it.moveToFirst()) {
                    readUser(it)
                } else {
                    null
                }
            }
    }

    private fun exists(
        column: String,
        value: String
    ): Boolean {

        helper
            .readableDatabase
            .rawQuery(
                """
                SELECT id
                FROM $TABLE_USERS
                WHERE $column = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(value)
            )
            .use {

                return it.moveToFirst()
            }
    }

    private fun readUser(
        cursor: Cursor
    ): User {

        return User(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            dni = cursor.getString(cursor.getColumnIndexOrThrow("dni")),
            phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
            gender = cursor.getString(cursor.getColumnIndexOrThrow("gender")),
            age = cursor.getInt(cursor.getColumnIndexOrThrow("age")),
            email = cursor.getString(cursor.getColumnIndexOrThrow("email"))
        )
    }

    private fun hashPassword(
        password: String
    ): String {

        return MessageDigest
            .getInstance("SHA-256")
            .digest(password.toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }
    }
}
