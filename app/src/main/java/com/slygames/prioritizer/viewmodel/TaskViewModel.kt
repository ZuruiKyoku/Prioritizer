package com.slygames.prioritizer.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.slygames.prioritizer.data.ConnectivityObserver
import com.slygames.prioritizer.data.list.TaskListEntity
import com.slygames.prioritizer.data.sync.SyncManager
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.data.task.TaskFilter
import com.slygames.prioritizer.data.task.TaskRepository
import com.slygames.prioritizer.data.task.createTaskExport
import com.slygames.prioritizer.data.user.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns the task list screen: lists, tasks, filter, selection, and everyday task/list actions.
 * Firestore writes are delegated to [SyncManager] and always fire-and-forget from the UI's
 * perspective -- Room updates immediately, sync happens after (spec: UI should not feel like
 * it's waiting for Firebase). */
@HiltViewModel
class TaskViewModel
@Inject
constructor(
        private val repository: TaskRepository,
        private val syncManager: SyncManager,
        private val prefs: UserPreferences,
        connectivityObserver: ConnectivityObserver
) : ViewModel() {

    /** Spec: show a subtle offline indicator, but never block editing while offline. */
    val isOnline: StateFlow<Boolean> =
            connectivityObserver.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _selectedListId = MutableStateFlow<Long?>(null)
    val selectedListId: StateFlow<Long?> = _selectedListId

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val taskLists: StateFlow<List<TaskListEntity>> =
            repository.getTaskLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedListName: StateFlow<String> =
            _selectedListId.filterNotNull()
                    .flatMapLatest { id -> repository.getTaskListFlowById(id).map { it?.name ?: "" } }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Tasks for the selected list, filtered (All/Active/Completed) and then narrowed by
     * [searchQuery] against title + description (spec: search must respect the active list and
     * filter, not replace them). */
    val tasks: StateFlow<List<TaskEntity>> =
            combine(_selectedListId, _filter) { id, filter -> id to filter }
                    .flatMapLatest { (id, filter) ->
                        if (id == null) flowOf(emptyList())
                        else repository.getTasksForList(id, filter)
                    }
                    .combine(_searchQuery) { tasks, query ->
                        val trimmed = query.trim()
                        if (trimmed.isEmpty()) tasks
                        else tasks.filter { it.description.contains(trimmed, ignoreCase = true) }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val saved = prefs.lastSelectedListIdFlow.first()
            val exists = saved?.let { repository.getTaskListById(it) } != null
            _selectedListId.value = if (exists) saved else null
            ensureAtLeastOneList()
        }
        viewModelScope.launch {
            selectedListId.filterNotNull().distinctUntilChanged().collect { prefs.setLastSelectedListId(it) }
        }
    }

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Ensures the user always lands on at least one list, without ever creating a second default
     * list on repeated launches (spec: no duplicate default lists). By the time this runs, any
     * cloud restore/seed has already completed (see AuthViewModel.finishSignIn), so this is only
     * reached for guest mode or the rare case restore produced zero lists. */
    private suspend fun ensureAtLeastOneList() {
        if (_selectedListId.value != null) return
        val currentLists = repository.getTaskLists().first()
        if (currentLists.isNotEmpty()) {
            _selectedListId.value = currentLists.first().id
            return
        }
        val ownerUid = FirebaseAuth.getInstance().currentUser?.uid
        val newListId = repository.addTaskList(name = "My Tasks", ownerId = ownerUid, uuid = UUID.randomUUID().toString())
        _selectedListId.value = newListId
        repository.getTaskListById(newListId)?.let { syncManager.syncListToFirestore(it) }
    }

    fun loadList(listId: Long) {
        _selectedListId.value = listId
    }

    fun createNewList(name: String) {
        viewModelScope.launch {
            val owner = FirebaseAuth.getInstance().currentUser?.uid
            val newListId = repository.addTaskList(name = name, ownerId = owner)
            val newList = repository.getTaskListById(newListId) ?: return@launch
            _selectedListId.value = newListId
            syncManager.syncListToFirestore(newList)
        }
    }

    fun addTask(description: String, priority: Int, listId: Long) {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val newUuid = repository.addTask(description, priority, listId, createdBy = uid)
            if (newUuid == null) {
                _events.emit("Task title can't be empty.")
                return@launch
            }
            val list = repository.getTaskListById(listId) ?: return@launch
            val updatedList = list.copy(todoCount = list.todoCount + 1, totalCount = list.totalCount + 1)
            repository.updateTaskList(updatedList)

            val task = repository.getTaskByUuid(newUuid) ?: return@launch
            syncManager.syncTaskToFirestore(task)
            syncManager.syncListToFirestore(updatedList)
        }
    }

    fun toggleTaskDone(task: TaskEntity) {
        viewModelScope.launch {
            val updated = task.copy(done = !task.done, updatedAt = System.currentTimeMillis())
            repository.updateTask(updated)
            syncManager.syncTaskToFirestore(updated)

            val list = repository.getTaskListById(task.listId) ?: return@launch
            val updatedList =
                    if (updated.done) list.copy(todoCount = list.todoCount - 1, completedCount = list.completedCount + 1)
                    else list.copy(todoCount = list.todoCount + 1, completedCount = list.completedCount - 1)
            repository.updateTaskList(updatedList)
            syncManager.syncListToFirestore(updatedList)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            val list = repository.getTaskListById(task.listId) ?: return@launch
            syncManager.deleteTaskFromFirestore(list, task)
            repository.deleteTask(task)

            val updated =
                    list.copy(
                            todoCount = repository.countTodo(list.id),
                            completedCount = repository.countCompleted(list.id),
                            totalCount = repository.countTotal(list.id)
                    )
            repository.updateTaskList(updated)
            syncManager.syncListToFirestore(updated)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            val touched = task.copy(updatedAt = System.currentTimeMillis())
            repository.updateTask(touched)
            syncManager.syncTaskToFirestore(touched)
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            val listId = _selectedListId.value ?: return@launch
            val list = repository.getTaskListById(listId) ?: return@launch
            val completedTasks = repository.getCompletedTasksForListOnce(listId)

            syncManager.deleteTasksFromFirestoreBatch(list, completedTasks)
            repository.clearCompletedTasks(listId)

            val updated =
                    list.copy(
                            todoCount = repository.countTodo(listId),
                            completedCount = repository.countCompleted(listId),
                            totalCount = repository.countTotal(listId)
                    )
            repository.updateTaskList(updated)
            syncManager.syncListToFirestore(updated)
        }
    }

    fun renameList(id: Long, name: String) {
        viewModelScope.launch {
            if (!repository.renameList(id, name)) {
                _events.emit("List name can't be empty.")
                return@launch
            }
            repository.getTaskListById(id)?.let { syncManager.syncListToFirestore(it) }
        }
    }

    fun deleteList(list: TaskListEntity) {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            try {
                if (list.isShared && !list.uuid.isNullOrBlank()) {
                    if (uid != null && uid == list.ownerId) {
                        syncManager.stopSharingForEveryone(list.uuid)
                    } else {
                        syncManager.leaveSharedList(list.id)
                    }
                } else {
                    repository.deleteAllTasksInList(list.id)
                    repository.deleteTaskList(list.id)
                    // Firestore private-list cleanup happens best-effort via SyncManager on next
                    // sync pass; local removal is immediate so the UI never waits on it.
                }
            } catch (e: Exception) {
                _events.emit("Couldn't delete \"${list.name}\": ${e.localizedMessage ?: "unknown error"}")
            }

            if (_selectedListId.value == list.id) {
                _selectedListId.value = repository.getTaskLists().first().firstOrNull { it.id != list.id }?.id
            }
        }
    }

    /** Explicit "Leave" action for a non-owner member (distinct from [deleteList], which an owner
     * uses to tear the shared list down for everyone). */
    fun leaveSharedList(listId: Long) {
        viewModelScope.launch {
            syncManager.leaveSharedList(listId).onFailure {
                _events.emit("Left locally, but couldn't reach the server: ${it.localizedMessage ?: "unknown error"}")
            }
            if (_selectedListId.value == listId) {
                _selectedListId.value = repository.getTaskLists().first().firstOrNull { it.id != listId }?.id
            }
        }
    }

    fun startShareList(context: Context, listId: Long, friendUids: List<String>, fromDisplayName: String) {
        viewModelScope.launch {
            try {
                syncManager.startShareList(listId, friendUids, fromDisplayName)
                _events.emit("List shared!")
            } catch (e: Exception) {
                _events.emit("Couldn't share list: ${e.localizedMessage ?: "unknown error"}")
            }
        }
    }

    suspend fun getSharedListMemberStatuses(listUuid: String): Map<String, String> =
            try {
                syncManager.getSharedListMemberStatuses(listUuid)
            } catch (e: Exception) {
                emptyMap()
            }

    fun applySharingChanges(listUuid: String, added: List<String>, removed: List<String>) {
        viewModelScope.launch {
            try {
                syncManager.applyOwnerSharingChanges(listUuid, added, removed)
            } catch (e: Exception) {
                _events.emit("Couldn't update sharing: ${e.localizedMessage ?: "unknown error"}")
            }
        }
    }

    fun acceptSharedListInvite(uuid: String) {
        viewModelScope.launch {
            syncManager.acceptSharedListInvite(uuid).onFailure {
                _events.emit("Invite not ready or expired. Ask the owner to re-share.")
            }
        }
    }

    fun declineSharedListInvite(uuid: String) {
        viewModelScope.launch { syncManager.declineSharedListInvite(uuid) }
    }

    fun onSwipeDeleteList(listUuid: String, isOwner: Boolean) {
        viewModelScope.launch { syncManager.onSwipeDeleteList(listUuid, isOwner) }
    }

    fun shareListAsFile(context: Context, id: Long) {
        viewModelScope.launch {
            val list = repository.getTaskListById(id) ?: return@launch
            val exportTasks = repository.getTasksForList(id).first()
            val json = createTaskExport(exportTasks)
            val file = File(context.cacheDir, "${list.name}.prioritizer")
            file.writeText(json)

            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            val intent =
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
            context.startActivity(Intent.createChooser(intent, "Share List"))
        }
    }
}
