package com.slygames.prioritizer.data.task

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.slygames.prioritizer.data.AppDatabase
import com.slygames.prioritizer.data.list.TaskListEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real Room database (in-memory) rather than a mocked comparator, so these tests
 * pin down the actual production sort order, filters, and list CRUD behavior described in the
 * spec -- not just a hand-written stand-in for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class TaskDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newList(name: String = "Test List"): Long =
            dao.insertTaskList(
                    TaskListEntity(name = name, todoCount = 0, completedCount = 0, totalCount = 0)
            )

    @Test
    fun `tasks sort by priority descending`() = runBlocking {
        val listId = newList()
        dao.insertTask(TaskEntity(description = "Medium", priority = 5, listId = listId))
        dao.insertTask(TaskEntity(description = "Critical", priority = 10, listId = listId))
        dao.insertTask(TaskEntity(description = "Low", priority = 2, listId = listId))

        val ordered = dao.getTasksForList(listId).first().map { it.priority }
        assertEquals(listOf(10, 5, 2), ordered)
    }

    @Test
    fun `equal priority ties break by creation order (oldest first)`() = runBlocking {
        val listId = newList()
        val now = System.currentTimeMillis()
        dao.insertTask(TaskEntity(description = "Second", priority = 5, listId = listId, createdAt = now + 1000))
        dao.insertTask(TaskEntity(description = "First", priority = 5, listId = listId, createdAt = now))

        val ordered = dao.getTasksForList(listId).first().map { it.description }
        assertEquals(listOf("First", "Second"), ordered)
    }

    @Test
    fun `active filter excludes completed tasks`() = runBlocking {
        val listId = newList()
        dao.insertTask(TaskEntity(description = "Todo", priority = 5, listId = listId, done = false))
        dao.insertTask(TaskEntity(description = "Done", priority = 5, listId = listId, done = true))

        val active = dao.getActiveTasksForList(listId).first()
        assertEquals(1, active.size)
        assertEquals("Todo", active.first().description)
    }

    @Test
    fun `completed filter excludes active tasks`() = runBlocking {
        val listId = newList()
        dao.insertTask(TaskEntity(description = "Todo", priority = 5, listId = listId, done = false))
        dao.insertTask(TaskEntity(description = "Done", priority = 5, listId = listId, done = true))

        val completed = dao.getCompletedTasksForList(listId).first()
        assertEquals(1, completed.size)
        assertEquals("Done", completed.first().description)
    }

    @Test
    fun `completing and uncompleting a task round-trips correctly`() = runBlocking {
        val listId = newList()
        dao.insertTask(TaskEntity(description = "Task", priority = 5, listId = listId, done = false))
        val inserted = dao.getTasksForList(listId).first().first()

        dao.updateTask(inserted.copy(done = true))
        assertTrue(dao.getTasksForList(listId).first().first().done)

        dao.updateTask(inserted.copy(done = false))
        assertTrue(!dao.getTasksForList(listId).first().first().done)
    }

    @Test
    fun `deleting a list cascades and deletes its tasks`() = runBlocking {
        val listId = newList()
        dao.insertTask(TaskEntity(description = "Task", priority = 5, listId = listId))

        dao.deleteTaskList(listId)

        assertTrue(dao.getTasksForList(listId).first().isEmpty())
    }

    @Test
    fun `renaming a list persists the new name`() = runBlocking {
        val listId = newList("Old Name")
        val list = dao.getTaskListById(listId)!!
        dao.updateTaskList(list.copy(name = "New Name"))

        assertEquals("New Name", dao.getTaskListById(listId)?.name)
    }

    @Test
    fun `task uuid is globally unique and used for lookups`() = runBlocking {
        val listId = newList()
        val task = TaskEntity(description = "Task", priority = 5, listId = listId)
        dao.insertTask(task)

        val found = dao.getTaskByUuid(task.uuid)
        assertEquals(task.description, found?.description)
        assertNull(dao.getTaskByUuid("not-a-real-uuid"))
    }

    @Test
    fun `mirroring a shared task twice updates the same local row instead of duplicating it`() = runBlocking {
        // Regression test for the pre-fix bug where the local Room id was set equal to the
        // remote numeric task id: two mirror passes for the same remote task must never create
        // two local rows.
        val listId = newList()
        val remoteUuid = "remote-task-uuid-123"

        val firstPass = dao.getTaskByUuid(remoteUuid)
        dao.insertTask(TaskEntity(id = firstPass?.id ?: 0, uuid = remoteUuid, description = "Shared task", priority = 5, listId = listId))

        val secondPass = dao.getTaskByUuid(remoteUuid)
        dao.insertTask(
                TaskEntity(
                        id = secondPass?.id ?: 0,
                        uuid = remoteUuid,
                        description = "Shared task (edited)",
                        priority = 8,
                        listId = listId
                )
        )

        val all = dao.getTasksForList(listId).first()
        assertEquals(1, all.size)
        assertEquals("Shared task (edited)", all.first().description)
        assertEquals(8, all.first().priority)
    }
}
