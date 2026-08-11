package com.slygames.prioritizer.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.slygames.prioritizer.data.list.TaskListEntity
import com.slygames.prioritizer.data.task.TaskDao
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.data.user.UserProfile
import com.slygames.prioritizer.data.user.UserProfileDao

@Database(
        entities = [TaskEntity::class, TaskListEntity::class, UserProfile::class],
        version = 4,
        exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE task_lists ADD COLUMN ownerId TEXT")
                    }
                }

        val MIGRATION_2_3 =
                object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // TaskEntity indices
                        db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_task_list_done` ON `tasks` (`listId`, `done`)"
                        )

                        // TaskListEntity indices
                        db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_task_list_uuid` ON `task_lists` (`uuid`)"
                        )
                        db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_task_list_import_source` ON `task_lists` (`importedFromUid`, `importedListId`)"
                        )
                    }
                }

        /**
         * Adds:
         * - tasks.uuid: stable client-generated identity (replaces the per-device Room `id` as
         *   the Firestore document key for synced tasks -- see TaskEntity docs).
         * - tasks.createdAt / updatedAt: secondary sort key + future conflict resolution.
         * - tasks.createdBy / updatedBy: actor tracking, mirrors what was already sent to
         *   Firestore but never stored locally.
         * - task_lists.createdAt / updatedAt.
         *
         * Existing rows get a randomly generated v4-ish uuid and a createdAt/updatedAt backfilled
         * from (migration time + row id) so relative creation order among legacy rows is
         * preserved even though the exact original timestamp is unknown.
         */
        val MIGRATION_3_4 =
                object : Migration(3, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        val baseMillis = System.currentTimeMillis()

                        db.execSQL("ALTER TABLE tasks ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                        db.execSQL("ALTER TABLE tasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE tasks ADD COLUMN createdBy TEXT")
                        db.execSQL("ALTER TABLE tasks ADD COLUMN updatedBy TEXT")

                        // Backfill a unique-enough random UUID per existing row.
                        db.execSQL(
                                """
                                UPDATE tasks SET uuid =
                                    lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' ||
                                    substr(lower(hex(randomblob(2))), 2) || '-' ||
                                    substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' ||
                                    lower(hex(randomblob(6)))
                                WHERE uuid = ''
                                """.trimIndent()
                        )

                        // Preserve relative insertion order for legacy rows.
                        db.execSQL(
                                "UPDATE tasks SET createdAt = $baseMillis + id, updatedAt = $baseMillis + id WHERE createdAt = 0"
                        )

                        db.execSQL(
                                "CREATE UNIQUE INDEX IF NOT EXISTS `index_task_uuid` ON `tasks` (`uuid`)"
                        )

                        db.execSQL(
                                "ALTER TABLE task_lists ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0"
                        )
                        db.execSQL(
                                "ALTER TABLE task_lists ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                        )
                        db.execSQL(
                                "UPDATE task_lists SET createdAt = $baseMillis + id, updatedAt = $baseMillis + id WHERE createdAt = 0"
                        )
                    }
                }

        fun getInstance(context: Context): AppDatabase =
                INSTANCE
                        ?: synchronized(this) {
                            INSTANCE
                                    ?: Room.databaseBuilder(
                                                    context.applicationContext,
                                                    AppDatabase::class.java,
                                                    "app_database"
                                            )
                                            .addMigrations(
                                                    MIGRATION_1_2,
                                                    MIGRATION_2_3,
                                                    MIGRATION_3_4
                                            )
                                            .build()
                                            .also { INSTANCE = it }
                        }
    }
}
