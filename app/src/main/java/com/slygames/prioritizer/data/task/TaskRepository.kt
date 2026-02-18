package com.slygames.prioritizer.data.task

import com.slygames.prioritizer.data.list.TaskListEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    fun getTaskLists(): Flow<List<TaskListEntity>> {
        return taskDao.getTaskLists()
    }

    fun getTasksForList(listId: Long): Flow<List<TaskEntity>> {
        return taskDao.getTasksForList(listId)
    }

    suspend fun taskListExistsFromSource(uid: String, listId: Long): Boolean {
        return taskDao.getListByImportedSource(uid, listId) != null
    }

    suspend fun addTask(description: String, priority: Int, listId: Long, done: Boolean = false) {
        taskDao.insertTask(
                TaskEntity(
                        id = 0,
                        description = description,
                        priority = priority,
                        listId = listId,
                        done = done
                )
        )
    }

    suspend fun upsertTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
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
        return taskDao.insertTaskList(
                TaskListEntity(
                        id = 0,
                        uuid = uuid,
                        name = name,
                        todoCount = 0,
                        completedCount = 0,
                        totalCount = 0,
                        isShared = isShared,
                        importedFromUid = importedFromUid,
                        importedListId = importedListId,
                        ownerId = ownerId
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
        taskDao.updateTaskList(taskList)
    }

    suspend fun renameList(listId: Long, newName: String) {
        val existing = taskDao.getTaskListById(listId)
        if (existing != null) {
            taskDao.updateTaskList(existing.copy(name = newName))
        }
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
