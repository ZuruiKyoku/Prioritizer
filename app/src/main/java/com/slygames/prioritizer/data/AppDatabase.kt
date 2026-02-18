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
        version = 3,
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

        fun getInstance(context: Context): AppDatabase =
                INSTANCE
                        ?: synchronized(this) {
                            INSTANCE
                                    ?: Room.databaseBuilder(
                                                    context.applicationContext,
                                                    AppDatabase::class.java,
                                                    "app_database"
                                            )
                                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                                            .build()
                                            .also { INSTANCE = it }
                        }
    }
}
