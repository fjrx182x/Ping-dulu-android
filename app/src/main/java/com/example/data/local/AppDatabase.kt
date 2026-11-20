package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(TaskConverters::class)
abstract class AppDatabase : RoomDatabase() {

    private val seedMutex = Mutex()

    abstract fun taskDao(): TaskDao

    suspend fun ensureSeeded() {
        seedMutex.withLock {
            if (taskDao().countTasks() == 0) {
                taskDao().upsertTasks(SeedTaskData.createInitialTasks())
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "pingdulu_field_ops.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
