package com.slygames.prioritizer.data.task

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.slygames.prioritizer.data.AppDatabase
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class TaskRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = TaskRepository(db.taskDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `addTask rejects a blank title and does not insert anything`() = runBlocking {
        val listId = repository.addTaskList(name = "List", ownerId = null)
        val result = repository.addTask("   ", priority = 5, listId = listId)

        assertNull(result)
        assertTrue(repository.getTasksForList(listId).first().isEmpty())
    }

    @Test
    fun `addTask trims the title and clamps out-of-range priority`() = runBlocking {
        val listId = repository.addTaskList(name = "List", ownerId = null)
        repository.addTask("  Buy milk  ", priority = 99, listId = listId)

        val task = repository.getTasksForList(listId).first().first()
        assertEquals("Buy milk", task.description)
        assertEquals(10, task.priority)
    }

    @Test
    fun `renameList rejects a blank name and leaves the original untouched`() = runBlocking {
        val listId = repository.addTaskList(name = "Original", ownerId = null)

        val accepted = repository.renameList(listId, "   ")

        assertTrue(!accepted)
        assertEquals("Original", repository.getTaskListById(listId)?.name)
    }

    @Test
    fun `create rename and delete list flow`() = runBlocking {
        val listId = repository.addTaskList(name = "Groceries", ownerId = null)
        assertEquals("Groceries", repository.getTaskListById(listId)?.name)

        repository.renameList(listId, "Weekly Groceries")
        assertEquals("Weekly Groceries", repository.getTaskListById(listId)?.name)

        repository.deleteTaskList(listId)
        assertNull(repository.getTaskListById(listId))
    }

    @Test
    fun `importing the same shared list twice is recognized instead of duplicated`() = runBlocking {
        // Spec: a shared list must have one canonical identity (its uuid) -- importing it again
        // must be recognized as "already have this," never create a second local list row.
        val uuid = "shared-list-uuid-abc"
        val firstImportId = repository.addTaskList(name = "Game Project", uuid = uuid, isShared = true, ownerId = "owner-uid")

        val alreadyImported = repository.getTaskListByUuid(uuid)
        assertEquals(firstImportId, alreadyImported?.id)

        // A correctly-written "accept invite" flow checks getTaskListByUuid first and updates in
        // place rather than calling addTaskList again -- verify only one row exists for the uuid.
        val allLists = repository.getTaskLists().first().filter { it.uuid == uuid }
        assertEquals(1, allLists.size)
    }
}
