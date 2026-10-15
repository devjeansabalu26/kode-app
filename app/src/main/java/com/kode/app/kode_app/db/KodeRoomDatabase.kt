package com.kode.app.kode_app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kode.app.kode_app.db.dao.EventDao
import com.kode.app.kode_app.db.dao.RegistrationDao
import com.kode.app.kode_app.db.dao.UserDao
import com.kode.app.kode_app.db.entity.EventEntity
import com.kode.app.kode_app.db.entity.RegistrationEntity
import com.kode.app.kode_app.db.entity.UserEntity

@Database(
    entities = [UserEntity::class, EventEntity::class, RegistrationEntity::class],
    version = 2,
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
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}
