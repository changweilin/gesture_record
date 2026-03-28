package com.example.gesturerecord.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GestureCombination::class, GestureItem::class], version = 3, exportSchema = false)
abstract class GestureDatabase : RoomDatabase() {

    abstract fun gestureDao(): GestureDao

    companion object {
        @Volatile
        private var INSTANCE: GestureDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_gesture_items_combinationId_slotIndex " +
                    "ON gesture_items(combinationId, slotIndex)"
                )
            }
        }

        fun getDatabase(context: Context): GestureDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GestureDatabase::class.java,
                    "gesture_record_db"
                ).addMigrations(MIGRATION_2_3)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
