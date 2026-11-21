package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [TaskEntity::class],
    version = 3,
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

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN due_date INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN assigned_to_id INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN labels_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("UPDATE tasks SET created_at = updated_at_millis WHERE created_at = 0")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tasks_v3` (
                        `id` INTEGER NOT NULL,
                        `ticket_code` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `scheduled_date_epoch_day` INTEGER NOT NULL,
                        `scheduled_time_minutes` INTEGER,
                        `time_slot_label` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `stage` TEXT NOT NULL,
                        `technical_notes` TEXT NOT NULL,
                        `tracked_seconds` INTEGER NOT NULL,
                        `is_timer_running` INTEGER NOT NULL,
                        `timer_started_at_millis` INTEGER,
                        `location` TEXT NOT NULL,
                        `sla_target_time` TEXT NOT NULL,
                        `checklist_json` TEXT NOT NULL,
                        `assignees_json` TEXT NOT NULL,
                        `is_synced` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `due_date` INTEGER,
                        `assigned_to_id` INTEGER,
                        `labels_json` TEXT NOT NULL DEFAULT '[]',
                        `updated_at_millis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `tasks_v3` (
                        `id`, `ticket_code`, `title`, `category`,
                        `scheduled_date_epoch_day`, `scheduled_time_minutes`, `time_slot_label`,
                        `priority`, `stage`, `technical_notes`, `tracked_seconds`,
                        `is_timer_running`, `timer_started_at_millis`, `location`, `sla_target_time`,
                        `checklist_json`, `assignees_json`, `is_synced`, `created_at`,
                        `due_date`, `assigned_to_id`, `labels_json`, `updated_at_millis`
                    )
                    SELECT
                        `id`, `ticket_code`, `title`, `category`,
                        `scheduled_date_epoch_day`, `scheduled_time_minutes`, `time_slot_label`,
                        `priority`, `stage`, `technical_notes`, `tracked_seconds`,
                        `is_timer_running`, `timer_started_at_millis`, `location`, `sla_target_time`,
                        `checklist_json`, `assignees_json`, `is_synced`, `created_at`,
                        `due_date`, `assigned_to_id`, `labels_json`, `updated_at_millis`
                    FROM `tasks`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_v3` RENAME TO `tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_scheduled_date_epoch_day` ON `tasks` (`scheduled_date_epoch_day`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_stage` ON `tasks` (`stage`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_is_synced` ON `tasks` (`is_synced`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
