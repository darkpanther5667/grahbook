package com.aistudio.sharmakhata.pqmzvk.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CacheEntry::class, PendingOperation::class, ItemEntity::class, ExpenseEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingDao(): PendingDao
    abstract fun itemDao(): ItemDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "grahbook.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Failed to initialize database", e)
                    // Fallback: try with a new database name
                    try {
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "grahbook_fallback.db"
                        )
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { INSTANCE = it }
                    } catch (fallbackException: Exception) {
                        android.util.Log.e("AppDatabase", "Fallback database initialization also failed", fallbackException)
                        throw RuntimeException("Failed to initialize database", fallbackException)
                    }
                }
            }
        }
    }
}
