package com.kode.app.kode_app.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kode.app.kode_app.db.dao.EventDao
import com.kode.app.kode_app.db.dao.RegistrationDao
import com.kode.app.kode_app.db.dao.UserDao
import com.kode.app.kode_app.db.entity.EventEntity
import com.kode.app.kode_app.db.entity.RegistrationEntity
import com.kode.app.kode_app.db.entity.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [UserEntity::class, EventEntity::class, RegistrationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KodeRoomDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun registrationDao(): RegistrationDao

    companion object {
        @Volatile
        private var INSTANCE: KodeRoomDatabase? = null

        fun getDatabase(context: Context): KodeRoomDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KodeRoomDatabase::class.java,
                    "kode_room.db"
                )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration(true)
                    .addCallback(SeedCallback)
                    .build()
                INSTANCE = instance
                return instance
            }
        }

        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                val staffUserId = db.insert("users", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                    put("name", "Allison Ortiz")
                    put("dni", "76543210")
                    put("phone", "987654321")
                    put("gender", "Femenino")
                    put("age", 23)
                    put("email", "allison@kode.app")
                    put("password_hash", "123456")
                    put("role", "STAFF")
                    put("created_at", now)
                })

                val eventId = db.insert("events", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
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
                    put("latitude", -12.0874)
                    put("longitude", -77.0500)
                    put("image_url", "https://picsum.photos/seed/cibertec2026/800/400")
                    put("created_at", now)
                })

                db.insert("registrations", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                    put("event_id", eventId)
                    put("user_id", staffUserId)
                    put("user_dni", "76543210")
                    put("user_name", "Allison Ortiz")
                    put("qr_code", "KODE-1-76543210")
                    put("status", "PREINSCRITO")
                    put("checked_in", 0)
                    put("registered_at", now)
                    put("sync_status", 0)
                })
            }
        }
    }
}
