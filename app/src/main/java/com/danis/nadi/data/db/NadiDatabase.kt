package com.danis.nadi.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.danis.nadi.data.db.dao.ChatMessageDao
import com.danis.nadi.data.db.dao.SharedFileDao
import com.danis.nadi.data.db.entity.ChatMessageEntity
import com.danis.nadi.data.db.entity.SharedFileEntity

@Database(
    entities = [ChatMessageEntity::class, SharedFileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NadiDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun sharedFileDao(): SharedFileDao

    companion object {
        @Volatile
        private var INSTANCE: NadiDatabase? = null

        fun getInstance(context: Context): NadiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NadiDatabase::class.java,
                    "nadi_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
