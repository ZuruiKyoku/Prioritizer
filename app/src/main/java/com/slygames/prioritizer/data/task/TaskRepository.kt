package com.slygames.prioritizer.data.task

import com.slygames.prioritizer.data.list.TaskListEntity
import com.slygames.prioritizer.util.InputValidation
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Local source of truth for tasks and lists (Room). Firestore synchronization is handled one
 * layer up by SyncManager -- this class never talks to Firebase, so it stays trivially testable
 * and reusable offline.
 */
@Singleton
class TaskRepository @Inject constructor(private val taskDao: TaskDao) {

    fun getTaskLists(): Flow<List<TaskListEntity>> = taskDao.getTaskLists()

    fun getTasksForList(listId: Long, filter: TaskFilter = TaskFilter.ALL): Flow<List<TaskEntity>> =
            when (filter) {
                TaskFilter.ALL -> taskDao.getTasksForList(listId)
                TaskFilter.ACTIVE -> taskDao.getActiveTasksForList(listId)
                TaskFilter.COMPLETED -> taskDao.getCompletedTasksForList(listId)
            }

    suspend fun taskListExistsFromSource(uid: String, listId: Long): Boolean {
        return taskDao.getListByImportedSource(uid, listId) != null
    }

    suspend fun getTaskByUuid(uuid: String): TaskEntity? = taskDao.getTaskByUuid(uuid)

    suspend fun getTaskByLegacyRoomId(legacyId: Long, listId: Long): TaskEntity? =
            taskDao.getTaskByLegacyRoomId(legacyId, listId)

    /**
     * Adds a task. [description] is trimmed and validated; blank titles are rejected rather than
     * silently inserted (spec: validate all user input). Returns the inserted task's uuid, or
     * null if [description] failed validation.
     */
    suspend fun addTask(
            description: String,
            priority: Int,
            listId: Long,
            done: Boolean = false,
            createdBy: String? = null
    ): String? {
        val validated = InputValidation.validateTaskTitle(description)
        if (validated !is InputValidation.Result.Valid) return null

        val uuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        taskDao.insertTask(
                TaskEntity(
                        id = 0,
                        uuid = uuid,
                        description = validated.value,
                        priority = priority.coerceToPriorityRange(),
                        listId = listId,
                        done = done,
                        createdAt = now,
                        updatedAt = now,
                        createdBy = createdBy,
                        updatedBy = createdBy
                )
        )
        return uuid
    }

    /** Upserts a task as-is (used when mirroring a remote/shared task into Room). */
    suspend fun upsertTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task.copy(priority = task.priority.coerceToPriorityRange()))
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }

    suspend fun clearCompletedTasks(listId: Long) {
        taskDao.clearCompletedTasks(listId)
    }

    suspend fun addTaskList(
            name: String,
            uuid: String = UUID.randomUUID().toString(),
            isShared: Boolean = false,
            importedFromUid: String? = null,
            importedListId: Long? = null,
            ownerId: String?,
    ): Long {
        val now = System.currentTimeMillis()
        return taskDao.insertTaskList(
                TaskListEntity(
                        id = 0,
                        uuid = uuid,
                        name = name.trim(),
                        todoCount = 0,
                        completedCount = 0,
                        totalCount = 0,
                        isShared = isShared,
                        importedFromUid = importedFromUid,
                        importedListId = importedListId,
                        ownerId = ownerId,
                        createdAt = now,
                        updatedAt = now
                )
        )
    }

    suspend fun getTaskListById(id: Long): TaskListEntity? {
        return taskDao.getTaskListById(id)
    }

    fun getTaskListFlowById(id: Long): Flow<TaskListEntity?> {
        return taskDao.observeTaskListById(id)
    }

    suspend fun getTaskListByUuid(uuid: String): TaskListEntity? {
        return taskDao.getTaskListByUuid(uuid)
    }

    suspend fun deleteTaskList(id: Long) {
        taskDao.deleteTaskList(id)
    }

    suspend fun updateTaskList(taskList: TaskListEntity) {
        taskDao.updateTaskList(taskList.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Returns false (and makes no change) if [newName] is blank -- spec: no empty list names. */
    suspend fun renameList(listId: Long, newName: String): Boolean {
        val validated = InputValidation.validateListName(newName)
        if (validated !is InputValidation.Result.Valid) return false
        val existing = taskDao.getTaskListById(listId) ?: return false
        taskDao.updateTaskList(
                existing.copy(name = validated.value, updatedAt = System.currentTimeMillis())
        )
        return true
    }

    suspend fun deleteAllTasksInList(listId: Long) {
        taskDao.deleteAllTasksInList(listId)
    }

    suspend fun getCompletedTasksForListOnce(listId: Long): List<TaskEntity> =
            taskDao.getCompletedTasksForListOnce(listId)
    suspend fun countTodo(listId: Long): Int = taskDao.countTodo(listId)
    suspend fun countCompleted(listId: Long): Int = taskDao.countCompleted(listId)
    suspend fun countTotal(listId: Long): Int = taskDao.countTotal(listId)

    suspend fun clearAllLocalData() {
        taskDao.deleteAllTasks()
        taskDao.deleteAllTaskLists()
    }
}
