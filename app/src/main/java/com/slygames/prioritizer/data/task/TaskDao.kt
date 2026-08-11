package com.slygames.prioritizer.data.task

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.slygames.prioritizer.data.list.TaskListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_lists ORDER BY name ASC")
    fun getTaskLists(): Flow<List<TaskListEntity>>

    // Default ordering rule (spec): priority descending, ties broken by creation order.
    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY priority DESC, createdAt ASC")
    fun getTasksForList(listId: Long): Flow<List<TaskEntity>>

    @Query(
            "SELECT * FROM tasks WHERE listId = :listId AND done = 0 ORDER BY priority DESC, createdAt ASC"
    )
    fun getActiveTasksForList(listId: Long): Flow<List<TaskEntity>>

    @Query(
            "SELECT * FROM tasks WHERE listId = :listId AND done = 1 ORDER BY priority DESC, createdAt ASC"
    )
    fun getCompletedTasksForList(listId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE uuid = :uuid LIMIT 1") suspend fun getTaskByUuid(uuid: String): TaskEntity?

    /**
     * Bridges rows mirrored under the pre-uuid scheme, where the local Room [TaskEntity.id] was
     * (buggily) set equal to the remote numeric task id. Used only to adopt/heal those legacy
     * rows the first time a shared list is mirrored after the uuid migration -- see SyncManager.
     */
    @Query("SELECT * FROM tasks WHERE id = :legacyId AND listId = :listId LIMIT 1")
    suspend fun getTaskByLegacyRoomId(legacyId: Long, listId: Long): TaskEntity?

    @Insert suspend fun insertTaskList(taskList: TaskListEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTask(task: TaskEntity)

    @Update suspend fun updateTask(task: TaskEntity)

    @Delete suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE done = 1 AND listId = :listId")
    suspend fun clearCompletedTasks(listId: Long)

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getTaskListById(id: Long): TaskListEntity?

    @Query("SELECT * FROM task_lists WHERE id = :id")
    fun observeTaskListById(id: Long): Flow<TaskListEntity?>

    @Query("DELETE FROM task_lists WHERE id = :id") suspend fun deleteTaskList(id: Long)

    @Update suspend fun updateTaskList(taskList: TaskListEntity)

    @Query(
            "SELECT * FROM task_lists WHERE importedFromUid = :uid AND importedListId = :listId LIMIT 1"
    )
    suspend fun getListByImportedSource(uid: String, listId: Long): TaskListEntity?

    @Query("SELECT * FROM task_lists") suspend fun getAllRawLists(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE uuid = :uuid LIMIT 1")
    suspend fun getTaskListByUuid(uuid: String): TaskListEntity?

    @Query("DELETE FROM tasks WHERE listId = :listId")
    suspend fun deleteAllTasksInList(listId: Long)

    @Query("SELECT * FROM tasks WHERE listId = :listId AND done = 1")
    suspend fun getCompletedTasksForListOnce(listId: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE listId = :listId AND done = 0")
    suspend fun countTodo(listId: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE listId = :listId AND done = 1")
    suspend fun countCompleted(listId: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE listId = :listId")
    suspend fun countTotal(listId: Long): Int

    @Query("DELETE FROM tasks") suspend fun deleteAllTasks()

    @Query("DELETE FROM task_lists") suspend fun deleteAllTaskLists()
}
