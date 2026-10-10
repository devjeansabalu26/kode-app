package com.kode.app.kode_app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "kode.db"

        private const val DATABASE_VERSION = 5

        const val TABLE_USERS = "users"
        const val TABLE_EVENTS = "events"
        const val TABLE_REGISTRATIONS = "registrations"

        @Volatile
        private var instance: AppDatabaseHelper? = null

        fun getInstance(context: Context): AppDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private fun currentDateTime(): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return dateFormat.format(Date())
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_USERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                dni TEXT NOT NULL UNIQUE,
                phone TEXT NOT NULL,
                gender TEXT NOT NULL,
                age INTEGER NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT DEFAULT 'ASISTENTE',
                created_at TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_EVENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                creator_id INTEGER,
                title TEXT NOT NULL,
                description TEXT,
                date TEXT NOT NULL,
                end_time TEXT,
                location TEXT NOT NULL,
                category TEXT NOT NULL,
                capacity INTEGER NOT NULL DEFAULT 100,
                event_type TEXT DEFAULT 'Presencial',
                is_private INTEGER DEFAULT 0,
                require_dni INTEGER DEFAULT 0,
                is_untransferable INTEGER DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'PUBLISHED',
                access_code TEXT,
                created_at TEXT NOT NULL,
                FOREIGN KEY (creator_id) REFERENCES $TABLE_USERS(id)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_REGISTRATIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id INTEGER NOT NULL,
                user_id INTEGER,
                user_dni TEXT NOT NULL,
                user_name TEXT NOT NULL,
                qr_code TEXT NOT NULL UNIQUE,
                status TEXT NOT NULL DEFAULT 'PREINSCRITO',
                checked_in INTEGER NOT NULL DEFAULT 0,
                registered_at TEXT NOT NULL,
                checked_in_at TEXT,
                sync_status INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (event_id) REFERENCES $TABLE_EVENTS(id),
                FOREIGN KEY (user_id) REFERENCES $TABLE_USERS(id)
            )
        """.trimIndent())

        insertInitialSeedData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_REGISTRATIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    private fun insertInitialSeedData(db: SQLiteDatabase) {
        val userValues = ContentValues().apply {
            put("name", "Allison Ortiz")
            put("dni", "76543210")
            put("phone", "987654321")
            put("gender", "Femenino")
            put("age", 23)
            put("email", "allison@kode.app")
            put("password_hash", "123456")
            put("role", "STAFF")
            put("created_at", currentDateTime())
        }
        val staffUserId = db.insert(TABLE_USERS, null, userValues)

        val eventValues = ContentValues().apply {
            put("creator_id", staffUserId)
            put("title", "Conferencia de Tecnología Cibertec 2026")
            put("description", "Evento sobre desarrollo móvil, Kotlin y arquitectura offline-first.")
            put("date", "2026-10-15 10:00:00")
            put("end_time", "13:00")
            put("location", "Auditorio Principal Cibertec")
            put("category", "Tecnología")
            put("capacity", 150)
            put("event_type", "Presencial")
            put("is_private", 0)
            put("require_dni", 1)
            put("is_untransferable", 0)
            put("status", "PUBLISHED")
            put("access_code", "123456")
            put("created_at", currentDateTime())
        }
        val eventId = db.insert(TABLE_EVENTS, null, eventValues)

        val registrationValues = ContentValues().apply {
            put("event_id", eventId)
            put("user_id", staffUserId)
            put("user_dni", "76543210")
            put("user_name", "Allison Ortiz")
            put("qr_code", "KODE-1-76543210")
            put("status", "PREINSCRITO")
            put("checked_in", 0)
            put("registered_at", currentDateTime())
            put("sync_status", 0)
        }
        db.insert(TABLE_REGISTRATIONS, null, registrationValues)
    }
}
