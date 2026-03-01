package com.example.gesturerecord.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GestureCombination::class, GestureItem::class], version = 2, exportSchema = false)
abstract class GestureDatabase : RoomDatabase() {

    abstract fun gestureDao(): GestureDao

    companion object {
        @Volatile
        private var INSTANCE: GestureDatabase? = null

        fun getDatabase(context: Context): GestureDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GestureDatabase::class.java,
                    "gesture_record_db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
