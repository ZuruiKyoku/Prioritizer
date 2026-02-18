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
    @Query("SELECT * FROM task_lists") fun getTaskLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM tasks WHERE listId = :listId")
    fun getTasksForList(listId: Long): Flow<List<TaskEntity>>

    @Insert suspend fun insertTaskList(taskList: TaskListEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTask(task: TaskEntity)

    @Update suspend fun updateTask(task: TaskEntity)

    @Delete suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE done = 1 AND listId = :listId")
    suspend fun clearCompletedTasks(listId: Long)

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getTaskListById(id: Long): TaskListEntity?

    // ✅ Flow version for observing the name
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
