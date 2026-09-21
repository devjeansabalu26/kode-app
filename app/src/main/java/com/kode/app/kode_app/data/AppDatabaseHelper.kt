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
        private const val DATABASE_VERSION = 2
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
                location TEXT NOT NULL,
                category TEXT NOT NULL,
                capacity INTEGER NOT NULL DEFAULT 100,
                status TEXT NOT NULL DEFAULT 'PUBLISHED',
                created_at TEXT NOT NULL,

                FOREIGN KEY (creator_id)
                REFERENCES $TABLE_USERS(id)
            )
            """.trimIndent())
        db.execSQL("""
            CREATE TABLE $TABLE_REGISTRATIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                qr_code TEXT NOT NULL UNIQUE,
                checked_in INTEGER NOT NULL DEFAULT 0,
                registered_at TEXT NOT NULL,
                checked_in_at TEXT,

                UNIQUE(event_id, user_id),

                FOREIGN KEY (event_id)
                REFERENCES $TABLE_EVENTS(id),

                FOREIGN KEY (user_id)
                REFERENCES $TABLE_USERS(id)
            )
            """.trimIndent())
        insertInitialEvents(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_REGISTRATIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    private fun insertInitialEvents(db: SQLiteDatabase) {
        insertEventSeed(
            db,
            "Conferencia de Tecnología & IA 2026",
            "Innovación, inteligencia artificial y desarrollo de software.",
            "20 Sep 2026 · 18:00",
            "Centro de Convenciones, Lima",
            "Tecnología",
            400
        )
        insertEventSeed(
            db,
            "Festival de Diseño y Arquitectura",
            "Diseño, creatividad y nuevas tendencias.",
            "26 Sep 2026 · 16:00",
            "Miraflores, Lima",
            "Diseño",
            200
        )
        insertEventSeed(
            db,
            "Summit Fundadores & Startups",
            "Comunidad, emprendimiento e innovación.",
            "05 Oct 2026 · 19:30",
            "San Isidro, Lima",
            "Comunidad",
            150
        )
    }

    private fun insertEventSeed(
        db: SQLiteDatabase,
        title: String,
        description: String,
        date: String,
        location: String,
        category: String,
        capacity: Int
    ) {
        val values = ContentValues().apply {
                putNull("creator_id")
                put("title", title)
                put("description", description)
                put("date", date)
                put("location", location)
                put("category", category)
                put("capacity", capacity)
                put("status", "PUBLISHED")
                put("created_at", currentDateTime())
            }
        db.insert(TABLE_EVENTS, null, values)
    }
}

internal fun currentDateTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
}
