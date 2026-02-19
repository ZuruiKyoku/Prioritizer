package com.slygames.prioritizer.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.slygames.prioritizer.data.AppDatabase
import com.slygames.prioritizer.data.list.SharedListInvite
import com.slygames.prioritizer.data.list.TaskListEntity
import com.slygames.prioritizer.data.task.*
import com.slygames.prioritizer.data.user.FriendProfile
import com.slygames.prioritizer.data.user.UserPreferences
import com.slygames.prioritizer.data.user.UserProfile
import com.slygames.prioritizer.util.deleteOldProfileImage
import com.slygames.prioritizer.util.generateFriendCode
import com.slygames.prioritizer.util.saveProfileImageToInternalStorage
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainViewModel(
        val repository: TaskRepository,
        private val db: AppDatabase,
        private val prefs: UserPreferences
) : ViewModel() {

    // <editor-fold desc="🧠 UI STATE">
    private val _showRequestsDialog = MutableStateFlow(true)
    val showRequestsDialog: StateFlow<Boolean> = _showRequestsDialog

    fun dismissRequestsDialog() = run { _showRequestsDialog.value = false }
    fun resetRequestsDialog() = run { _showRequestsDialog.value = true }

    private val _selectedListId = MutableStateFlow<Long?>(null)
    val selectedListId: StateFlow<Long?> = _selectedListId

    private val _emptyStateShown = MutableStateFlow(false)

    // One-shot flag signaling the initial restore decision is finished
    private val initialRestoreDone = MutableStateFlow(false)

    private fun markInitialRestoreDone() {
        if (!initialRestoreDone.value) initialRestoreDone.value = true
    }

    // Expose for the UI (Guest path)
    fun markInitialRestoreDoneFromUi() = markInitialRestoreDone()

    // Pending “sent” (badges for the Requests dialog)
    val pendingSentFriendRequests =
            getPendingSentFriendRequests()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingSentListShares =
            getPendingSentListShares()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalPendingSentCount =
            combine(pendingSentFriendRequests, pendingSentListShares) { friends, lists ->
                        friends.size + lists.size
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()
    // </editor-fold>

    // <editor-fold desc="📋 TASK + LIST MANAGEMENT">
    val selectedListName: StateFlow<String> =
            _selectedListId
                    .filterNotNull()
                    .flatMapLatest { id ->
                        repository.getTaskListFlowById(id).map { it?.name ?: "" }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val tasks: StateFlow<List<TaskEntity>> =
            _selectedListId
                    .filterNotNull()
                    .flatMapLatest { id -> repository.getTasksForList(id) }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskLists: StateFlow<List<TaskListEntity>> =
            repository
                    .getTaskLists()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Restore previously selected list (if it still exists)
        viewModelScope.launch {
            val saved = prefs.lastSelectedListIdFlow.firstOrNull()
            val exists = saved?.let { repository.getTaskListById(it) } != null
            if (exists) {
                _selectedListId.value = saved
            }
        }

        // ✅ Guarded, guest-only seeding AFTER the initial restore decision
        viewModelScope.launch {
            combine(
                            prefs.loginTypeFlow,
                            repository.getTaskLists().map { it.isEmpty() },
                            initialRestoreDone
                    ) { loginType, isLocalEmpty, restoreDone ->
                Triple(loginType, isLocalEmpty, restoreDone)
            }
                    .filter { (loginType, isLocalEmpty, restoreDone) ->
                        restoreDone && loginType == "guest" && isLocalEmpty
                    }
                    .take(1) // ensure at most once
                    .collect {
                        initializeList() // seeds only in this safe window
                    }
        }

        // Persist selection whenever it changes
        viewModelScope.launch {
            selectedListId.filterNotNull().distinctUntilChanged().collect { id ->
                prefs.setLastSelectedListId(id)
            }
        }
    }

    private var isInitializingList = false

    fun initializeList() {
        if (isInitializingList) return
        isInitializingList = true

        viewModelScope.launch {
            try {
                if (_selectedListId.value != null) return@launch

                val currentLists = repository.getTaskLists().first()
                if (currentLists.isEmpty()) {
                    val ownerUid = FirebaseAuth.getInstance().currentUser?.uid
                    // ensure lists that can sync have a UUID
                    val uuid = java.util.UUID.randomUUID().toString()
                    val newListId =
                            repository.addTaskList(
                                    name = "My First List",
                                    ownerId = ownerUid,
                                    uuid = uuid
                            )
                    _selectedListId.value = newListId

                    repository.getTaskListById(newListId)?.let { syncListToFirestore(it) }
                } else {
                    _selectedListId.value = currentLists.first().id
                }
            } finally {
                // Keep it true? Or allow retry?
                // Logic says we only want ONE init ever.
                // But if it failed?
                // For now, let's keep it true to prevent double-tap.
            }
        }
    }

    fun createNewList(name: String) {
        viewModelScope.launch {
            val owner = FirebaseAuth.getInstance().currentUser?.uid // 👈 define
            val newListId =
                    repository.addTaskList(
                            name = name,
                            ownerId = owner // 👈 store locally
                    )
            val newList = repository.getTaskListById(newListId) ?: return@launch
            _selectedListId.value = newListId
            syncListToFirestore(newList)
        }
    }

    fun addTask(description: String, priority: Int, listId: Long) {
        viewModelScope.launch {
            repository.addTask(description, priority, listId)
            val list = repository.getTaskListById(listId) ?: return@launch
            repository.updateTaskList(
                    list.copy(todoCount = list.todoCount + 1, totalCount = list.totalCount + 1)
            )

            val task = repository.getTasksForList(listId).first().lastOrNull() ?: return@launch
            syncTaskToFirestore(task)
            syncListToFirestore(
                    list.copy(todoCount = list.todoCount + 1, totalCount = list.totalCount + 1)
            )
        }
    }

    fun toggleTaskDone(task: TaskEntity) {
        viewModelScope.launch {
            val updated = task.copy(done = !task.done)
            repository.updateTask(updated)
            syncTaskToFirestore(updated)

            val list = repository.getTaskListById(task.listId) ?: return@launch
            val updatedList =
                    if (updated.done) {
                        list.copy(
                                todoCount = list.todoCount - 1,
                                completedCount = list.completedCount + 1
                        )
                    } else {
                        list.copy(
                                todoCount = list.todoCount + 1,
                                completedCount = list.completedCount - 1
                        )
                    }
            repository.updateTaskList(updatedList)
            syncListToFirestore(updatedList)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            val list = repository.getTaskListById(task.listId) ?: return@launch

            // Cloud: record actor then delete
            deleteTaskFromFirestore(list, task)

            // Local delete for snappy UI
            repository.deleteTask(task)

            // Recompute counts and sync list meta
            val newTodo = repository.countTodo(list.id)
            val newCompleted = repository.countCompleted(list.id)
            val newTotal = repository.countTotal(list.id)
            val updated =
                    list.copy(
                            todoCount = newTodo,
                            completedCount = newCompleted,
                            totalCount = newTotal
                    )
            repository.updateTaskList(updated)
            syncListToFirestore(updated)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
            syncTaskToFirestore(task)
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            val listId = _selectedListId.value ?: return@launch

            try {
                val list = repository.getTaskListById(listId) ?: return@launch

                // Collect completed tasks once for Firestore deletion
                val completedTasks = repository.getCompletedTasksForListOnce(listId)

                // Delete completed tasks from Firestore (batched & awaited)
                deleteTasksFromFirestoreBatch(list, completedTasks)

                // Delete completed tasks locally
                repository.clearCompletedTasks(listId)

                // Recompute counts from Room AFTER deletion
                val newTodo = repository.countTodo(listId)
                val newCompleted = repository.countCompleted(listId)
                val newTotal = repository.countTotal(listId)

                // Update list metadata everywhere
                val updated =
                        list.copy(
                                todoCount = newTodo,
                                completedCount = newCompleted,
                                totalCount = newTotal
                        )
                repository.updateTaskList(updated)
                syncListToFirestore(updated)
            } catch (t: Throwable) {
                // Log.e("clearCompletedTasks", "Failed", t)
            }
        }
    }

    private suspend fun deleteTasksFromFirestoreBatch(
            list: TaskListEntity,
            tasks: List<TaskEntity>
    ) {
        if (tasks.isEmpty()) return

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Helper to build the doc ref per path
        fun docRefFor(taskId: Long): com.google.firebase.firestore.DocumentReference {
            return if (list.isShared && !list.uuid.isNullOrBlank()) {
                db.collection("sharedLists")
                        .document(list.uuid!!)
                        .collection("tasks")
                        .document(taskId.toString())
            } else {
                db.collection("users")
                        .document(uid)
                        .collection("lists")
                        .document(list.id.toString())
                        .collection("tasks")
                        .document(taskId.toString())
            }
        }

        // If this is a SHARED list, set the clearCompleted op marker so Cloud Functions
        // (a) suppress per-task delete notifications and (b) send the single summary push.
        var opRef: com.google.firebase.firestore.DocumentReference? = null
        if (list.isShared && !list.uuid.isNullOrBlank()) {
            opRef =
                    db.collection("sharedLists")
                            .document(list.uuid!!)
                            .collection("ops")
                            .document("clearCompleted")

            val ids = tasks.map { it.id.toString() }
            try {
                opRef.set(
                                mapOf(
                                        "startedAt" to
                                                com.google.firebase.firestore.FieldValue
                                                        .serverTimestamp(),
                                        "actorUid" to uid,
                                        "taskIds" to ids
                                )
                        )
                        .await()
            } catch (_: Exception) {
                // Non-fatal: if marker fails, we still proceed with deletes.
            }
        }

        // Firestore batch limit = 500 writes → chunk if needed
        val chunks = tasks.chunked(500)
        try {
            for (chunk in chunks) {
                val batch = db.batch()
                chunk.forEach { t -> batch.delete(docRefFor(t.id)) }
                batch.commit().await()
            }
        } finally {
            // Optional cleanup of the op marker (creation event already fired).
            try {
                opRef?.delete()?.await()
            } catch (_: Exception) {
                /* ignore */
            }
        }
    }

    fun loadList(listId: Long) {
        _selectedListId.value = listId
    }

    fun renameList(id: Long, name: String) {
        viewModelScope.launch {
            val existing = repository.getTaskListById(id) ?: return@launch
            val updated = existing.copy(name = name)
            repository.updateTaskList(updated)
            syncListToFirestore(updated)
        }
    }

    fun deleteList(list: TaskListEntity) {
        viewModelScope.launch {
            val db = FirebaseFirestore.getInstance()
            val user = FirebaseAuth.getInstance().currentUser
            val uid = user?.uid
            val listId = list.id
            val uuid = list.uuid
            try {
                if (list.isShared && !uuid.isNullOrBlank()) {
                    // --- SHARED LIST ---
                    when {
                        // Owner: hard delete the shared list (triggers notifyOnSharedListDeleted
                        // CF)
                        uid != null && uid == list.ownerId -> {
                            stopSharingForEveryone(uuid) // implement if not already
                        }
                        // Member: leave the list (don’t delete shared data)
                        else -> {
                            // Mark as removed (your leaveSharedList should update
                            // sharedWith/{uid}.status='removed')
                            leaveSharedList(listId)

                            // Remove the user's lightweight reference doc
                            if (uid != null) {
                                db.collection("users")
                                        .document(uid)
                                        .collection("lists")
                                        .document(listId.toString())
                                        .delete()
                                        .await()
                            }
                        }
                    }
                } else {
                    // --- LOCAL/PRIVATE LIST ---
                    if (uid != null) {
                        // 1) Batch delete tasks under users/{uid}/lists/{listId}/tasks
                        deletePrivateListTasksFromFirestore(uid, listId)

                        // 2) Delete the parent list doc
                        db.collection("users")
                                .document(uid)
                                .collection("lists")
                                .document(listId.toString())
                                .delete()
                                .await()
                    }
                }

                // --- LOCAL (Room) cleanup for both cases ---
                repository.deleteAllTasksInList(listId)
                repository.deleteTaskList(listId)

                // (Optional) if this was the selected list, switch to another list & update UI
                // handleSelectedListAfterDeletion(listId)

            } catch (e: Exception) {
                // Log.e("deleteList", "Failed to delete list $listId", e)
            }
        }
    }

    private suspend fun deletePrivateListTasksFromFirestore(uid: String, listId: Long) {
        val db = FirebaseFirestore.getInstance()
        val list = repository.getTaskListById(listId) ?: return

        val userDocId = resolveUserListDocId(uid, list)

        val tasksRef =
                db.collection("users")
                        .document(uid)
                        .collection("lists")
                        .document(userDocId)
                        .collection("tasks")

        val snap = tasksRef.get().await()
        if (snap.isEmpty) return

        // Firestore batch limit = 500
        val docs = snap.documents
        for (chunk in docs.chunked(500)) {
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    fun syncAllTaskCounts() {
        viewModelScope.launch {
            val lists = repository.getTaskLists().first()
            for (list in lists) {
                val tasks = repository.getTasksForList(list.id).first()
                val todo = tasks.count { !it.done }
                val completed = tasks.count { it.done }
                val total = tasks.size

                val updated =
                        list.copy(todoCount = todo, completedCount = completed, totalCount = total)
                repository.updateTaskList(updated)
                syncListToFirestore(updated)
            }
        }
    }
    // </editor-fold>

    // <editor-fold desc="🌐 SHARED LISTS: owner-led model with invites + mirroring">
    private val sharedMirrors = mutableMapOf<String, Job>() // uuid -> mirror job

    private fun ensureUuid(list: TaskListEntity): TaskListEntity {
        if (!list.uuid.isNullOrBlank()) return list
        val withUuid = list.copy(uuid = java.util.UUID.randomUUID().toString())
        viewModelScope.launch { repository.updateTaskList(withUuid) }
        return withUuid
    }

    data class SharedWithStatus(
            val uid: String = "",
            val status: String = "pending", // pending | accepted | declined | removed
            val invitedAt: com.google.firebase.Timestamp? = null,
            val respondedAt: com.google.firebase.Timestamp? = null
    )

    /**
     * Starts sharing by moving the source of truth to sharedLists/{uuid}.
     * - Copies tasks
     * - Seeds sharedWith
     * - Converts the owner's local list to a reference to the shared uuid
     * - Starts mirroring shared tasks -> Room so UI keeps working
     */
    fun startShareList(
            context: Context,
            listId: Long,
            friendUids: List<String>,
            fromDisplayName: String
    ) {
        val me = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        viewModelScope.launch {
            val local = repository.getTaskListById(listId) ?: return@launch
            val withUuid = ensureUuid(local)
            val uuid = withUuid.uuid!!

            // 1) Create/merge sharedLists/{uuid}
            val tasks = repository.getTasksForList(listId).first()

            val listDoc =
                    mapOf(
                            "uuid" to uuid,
                            "ownerId" to me.uid,
                            "name" to withUuid.name,
                            "todoCount" to withUuid.todoCount,
                            "completedCount" to withUuid.completedCount,
                            "totalCount" to withUuid.totalCount,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                    )
            db.collection("sharedLists").document(uuid).set(listDoc, SetOptions.merge()).await()

            // 2) Copy tasks into sharedLists/{uuid}/tasks
            val batch = db.batch()
            val tasksRef = db.collection("sharedLists").document(uuid).collection("tasks")
            tasks.forEach { t ->
                val td =
                        mapOf(
                                "id" to t.id,
                                "description" to t.description,
                                "priority" to t.priority,
                                "done" to t.done,
                                "listId" to withUuid.id
                        )
                batch.set(tasksRef.document(t.id.toString()), td, SetOptions.merge())
            }
            batch.commit().await()

            // 3) Seed sharedWith subcollection (BATCH + await)
            val sharedWithRef = db.collection("sharedLists").document(uuid).collection("sharedWith")
            val membershipBatch = db.batch()

            // owner accepted
            membershipBatch.set(
                    sharedWithRef.document(me.uid),
                    SharedWithStatus(
                            uid = me.uid,
                            status = "accepted",
                            respondedAt = com.google.firebase.Timestamp.now()
                    )
            )

            // invitees pending
            friendUids.forEach { invitee ->
                membershipBatch.set(
                        sharedWithRef.document(invitee),
                        SharedWithStatus(
                                uid = invitee,
                                status = "pending",
                                invitedAt = com.google.firebase.Timestamp.now()
                        )
                )
            }

            membershipBatch.commit().await()

            // 3b) Now that membership docs exist, send notifications
            friendUids.forEach { invitee ->
                db.collection("notifications")
                        .document(invitee)
                        .collection("items")
                        .add(
                                mapOf(
                                        "type" to "LIST_INVITE",
                                        "from" to me.uid,
                                        "fromName" to fromDisplayName,
                                        "listName" to withUuid.name,
                                        "uuid" to uuid,
                                        "timestamp" to FieldValue.serverTimestamp()
                                )
                        )
            }

            // 4) Convert my local list → shared reference
            repository.updateTaskList(withUuid.copy(isShared = true, ownerId = me.uid))
            // Remove old private copy under users/{uid}/lists/{listId} (if present)
            db.collection("users")
                    .document(me.uid)
                    .collection("lists")
                    .document(listId.toString())
                    .delete()
            db.collection("users")
                    .document(me.uid)
                    .collection("lists")
                    .document(listId.toString())
                    .collection("tasks")
                    .get()
                    .await()
                    .documents
                    .forEach { it.reference.delete() }

            // Lightweight reference for convenience
            db.collection("users")
                    .document(me.uid)
                    .collection("lists")
                    .document(listId.toString())
                    .set(
                            mapOf("refUuid" to uuid, "isShared" to true, "name" to withUuid.name),
                            SetOptions.merge()
                    )

            // 5) Mirror shared tasks -> Room so UI keeps working
            startSharedMirror(uuid, listId)

            Toast.makeText(context, "List shared!", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun inviteUserToSharedList(
            db: FirebaseFirestore,
            uuid: String,
            inviteeUid: String,
            ownerDisplayName: String,
            listName: String
    ) {
        val me = FirebaseAuth.getInstance().currentUser ?: return
        val listRef = db.collection("sharedLists").document(uuid)

        // Create/refresh membership as pending (owner-only per rules)
        listRef.collection("sharedWith")
                .document(inviteeUid)
                .set(
                        mapOf(
                                "uid" to inviteeUid,
                                "status" to "pending",
                                "invitedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                )
                .await()

        // Optional: send notification (per your rules type = LIST_INVITE)
        db.collection("notifications")
                .document(inviteeUid)
                .collection("items")
                .add(
                        mapOf(
                                "type" to "LIST_INVITE",
                                "from" to me.uid,
                                "fromName" to ownerDisplayName,
                                "uuid" to uuid,
                                "listName" to listName,
                                "timestamp" to FieldValue.serverTimestamp()
                        )
                )
                .await()
    }

    suspend fun removeMemberFromSharedList(db: FirebaseFirestore, uuid: String, memberUid: String) {
        val listRef = db.collection("sharedLists").document(uuid)
        listRef.collection("sharedWith").document(memberUid).delete().await()
    }

    /** Accept an invite by uuid (we only trust sharedLists/{uuid} truth). */
    fun acceptSharedListInvite(uuid: String) {
        val me = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        viewModelScope.launch {
            try {
                // 1) Update membership (must already exist = 'pending')
                db.collection("sharedLists")
                        .document(uuid)
                        .collection("sharedWith")
                        .document(me.uid)
                        .update(
                                mapOf(
                                        "status" to "accepted",
                                        "respondedAt" to FieldValue.serverTimestamp()
                                )
                        )
                        .await()

                // 2) Read metadata (now allowed)
                val listDoc = db.collection("sharedLists").document(uuid).get().await()
                if (!listDoc.exists()) return@launch
                val name = listDoc.getString("name") ?: "Shared List"
                val ownerId = listDoc.getString("ownerId")
                val existing = repository.getTaskListByUuid(uuid)

                // 3) Ensure local list
                val localListId =
                        if (existing != null) {
                            repository.updateTaskList(
                                    existing.copy(isShared = true, name = name, ownerId = ownerId)
                            )
                            existing.id
                        } else {
                            repository.addTaskList(
                                    name = name,
                                    isShared = true,
                                    uuid = uuid,
                                    ownerId = ownerId
                            )
                        }

                // 4) Convenience ref (personal tree)
                db.collection("users")
                        .document(me.uid)
                        .collection("lists")
                        .document(localListId.toString())
                        .set(
                                mapOf("refUuid" to uuid, "isShared" to true, "name" to name),
                                SetOptions.merge()
                        )

                // 5) Start mirroring tasks
                startSharedMirror(uuid, localListId)

                // 6) Clean up invite
                db.collection("notifications")
                        .document(me.uid)
                        .collection("items")
                        .whereEqualTo("type", "LIST_INVITE")
                        .whereEqualTo("uuid", uuid)
                        .get()
                        .await()
                        .documents
                        .forEach { it.reference.delete() }
            } catch (e: Exception) {
                Log.e("acceptShared", "Accept failed for $uuid", e)
                // Show a toast via your events flow if you added it
                // _events.emit("Invite not ready or expired. Ask the owner to re-share.")
            }
        }
    }

    fun declineSharedListInvite(uuid: String) {
        val me = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        viewModelScope.launch {
            try {
                db.collection("sharedLists")
                        .document(uuid)
                        .collection("sharedWith")
                        .document(me.uid)
                        .update(
                                mapOf(
                                        "status" to "declined",
                                        "respondedAt" to FieldValue.serverTimestamp()
                                )
                        )
                        .await()

                db.collection("notifications")
                        .document(me.uid)
                        .collection("items")
                        .whereEqualTo("type", "LIST_INVITE")
                        .whereEqualTo("uuid", uuid)
                        .get()
                        .await()
                        .documents
                        .forEach { it.reference.delete() }
            } catch (e: Exception) {
                Log.e("declineShared", "Decline failed for $uuid", e)
            }
        }
    }

    /** Mirror sharedLists/{uuid}/tasks => local Room listId */
    private fun startSharedMirror(uuid: String, localListId: Long) {
        // avoid duplicates
        sharedMirrors[uuid]?.cancel()
        val db = FirebaseFirestore.getInstance()

        val job =
                viewModelScope.launch {
                    val ref = db.collection("sharedLists").document(uuid).collection("tasks")
                    ref.addSnapshotListener { snap, e ->
                        if (e != null) {
                            val code = (e as? FirebaseFirestoreException)?.code
                            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                Log.w("Mirror", "Permission denied for $uuid; stopping mirror")
                                sharedMirrors.remove(uuid)?.cancel()
                                return@addSnapshotListener
                            }
                        }
                        viewModelScope.launch {
                            val docs = snap?.documents ?: emptyList()
                            val remoteIds = docs.mapNotNull { it.getLong("id")?.toLong() }.toSet()

                            // Upserts
                            docs.forEach { d ->
                                val id = (d.getLong("id") ?: return@forEach).toLong()
                                val desc = d.getString("description") ?: return@forEach
                                val pri = (d.getLong("priority") ?: 5L).toInt()
                                val done = d.getBoolean("done") ?: false

                                val local = TaskEntity(id, desc, pri, done, localListId)
                                repository.upsertTask(local)
                            }

                            // Deletes (remove local tasks that no longer exist remotely)
                            val localTasks = repository.getTasksForList(localListId).first()
                            localTasks.filter { it.id !in remoteIds }.forEach {
                                repository.deleteTask(it)
                            }

                            // Recompute counts locally to keep UI badges correct
                            val updatedTasks = repository.getTasksForList(localListId).first()
                            val updatedList =
                                    repository
                                            .getTaskListById(localListId)
                                            ?.copy(
                                                    todoCount = updatedTasks.count { !it.done },
                                                    completedCount = updatedTasks.count { it.done },
                                                    totalCount = updatedTasks.size
                                            )
                            if (updatedList != null) repository.updateTaskList(updatedList)
                        }
                    }
                }
        sharedMirrors[uuid] = job
    }

    /**
     * Leave a shared list:
     * - mark removed in sharedWith
     * - remove user reference
     * - delete local mirror (Room)
     */
    fun leaveSharedList(listId: Long) {
        val me = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        viewModelScope.launch {
            val list = repository.getTaskListById(listId) ?: return@launch
            if (!list.isShared || list.uuid.isNullOrBlank()) return@launch
            val uuid = list.uuid!!

            stopSharedMirror(uuid)

            try {
                leaveSharedListLogic(uuid, me.uid, db)

                db.collection("users")
                        .document(me.uid)
                        .collection("lists")
                        .document(listId.toString())
                        .delete()
                        .await()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to leave shared list remotely: ${e.message}", e)
                _events.emit(
                        "Warning: Could not update server, but removed locally. Error: ${e.localizedMessage}"
                )
            }

            // Local cleanup
            repository.deleteAllTasksInList(listId)
            repository.deleteTaskList(listId)

            // fallback selection
            val remaining = repository.getTaskLists().first()
            _selectedListId.value =
                    remaining.firstOrNull()?.id
                            ?: run {
                                _emptyStateShown.value = true
                                null
                            }
        }
    }

    /** Owner-only hard delete for everyone */
    private suspend fun deleteCollectionInBatches(
            colRef: CollectionReference,
            batchSize: Long = 300L
    ) {
        while (true) {
            val snap =
                    colRef.limit(batchSize).get(com.google.firebase.firestore.Source.SERVER).await()
            if (snap.isEmpty) break
            val batch = colRef.firestore.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    /** Owner-only hard delete for everyone. */
    suspend fun stopSharingForEveryone(uuid: String) {
        val me = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        // Confirm ownership
        val listRef = db.collection("sharedLists").document(uuid)
        val doc = listRef.get(com.google.firebase.firestore.Source.SERVER).await()
        if (!doc.exists()) return
        if (doc.getString("ownerId") != me.uid) return

        // 1) delete tasks in batches
        deleteCollectionInBatches(listRef.collection("tasks"))

        // 2) delete sharedWith in batches
        deleteCollectionInBatches(listRef.collection("sharedWith"))

        // 3) delete parent list
        listRef.delete().await()
    }

    /**
     * Deletes the user account and all associated data.
     * 1. Deletes all shared lists owned by user.
     * 2. Removes user from other shared lists.
     * 3. Deletes user data (lists subcollection).
     * 4. Deletes user profile.
     * 5. Deletes Auth account.
     */
    fun deleteAccount(
            context: Context,
            deleteLocalData: Boolean,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                onError("No user signed in")
                return@launch
            }
            val uid = user.uid

            try {
                val db = FirebaseFirestore.getInstance()

                // A) Delete all Shared Lists owned by me
                val mySharedLists =
                        db.collection("sharedLists").whereEqualTo("ownerId", uid).get().await()

                for (doc in mySharedLists) {
                    stopSharingForEveryone(doc.id)
                }

                // B) Leave all other shared lists
                val myListsRef = db.collection("users").document(uid).collection("lists")
                val myLists = myListsRef.get().await()
                for (doc in myLists) {
                    val uuid = doc.getString("uuid") ?: doc.getString("refUuid")
                    val isShared = (doc.getBoolean("isShared") ?: false) || !uuid.isNullOrBlank()

                    if (isShared && !uuid.isNullOrBlank()) {
                        try {
                            leaveSharedListLogic(uuid, uid, db)
                        } catch (_: Exception) {}
                    } else {
                        // Private list: delete its 'tasks' subcollection first
                        val tasksRef = doc.reference.collection("tasks")
                        deleteCollectionInBatches(tasksRef)
                    }
                }

                // C) Delete private lists and the user's list references
                deleteCollectionInBatches(myListsRef)

                // D) Clean up Friends and Requests
                // 1. Delete friends connections (userId == uid OR friendUid == uid)
                val friends1 = db.collection("friends").whereEqualTo("userId", uid).get().await()
                val friends2 = db.collection("friends").whereEqualTo("friendUid", uid).get().await()
                val friendsBatch = db.batch()
                friends1.forEach { friendsBatch.delete(it.reference) }
                friends2.forEach { friendsBatch.delete(it.reference) }
                if (!friends1.isEmpty || !friends2.isEmpty) {
                    friendsBatch.commit().await()
                }

                // 2. Delete my request subcollections
                val requestsRef = db.collection("friend_requests").document(uid)
                deleteCollectionInBatches(requestsRef.collection("incoming"))
                deleteCollectionInBatches(requestsRef.collection("outgoing"))
                requestsRef.delete().await()

                // 3. Delete requests I sent to others (stored in their 'incoming')
                try {
                    val sentToOthers =
                            db.collectionGroup("incoming").whereEqualTo("uid", uid).get().await()
                    val sentBatch = db.batch()
                    sentToOthers.forEach { sentBatch.delete(it.reference) }
                    if (!sentToOthers.isEmpty) {
                        sentBatch.commit().await()
                    }
                } catch (e: Exception) {
                    Log.w(
                            "MainViewModel",
                            "Could not delete sent requests (missing index?): ${e.message}"
                    )
                }

                // E) Delete Profile Image (if exists)
                val userDocRef = db.collection("users").document(uid)
                val userSnapshot = userDocRef.get().await()
                if (userSnapshot.exists()) {
                    val imageUrl = userSnapshot.getString("profileImageUrl")
                    if (!imageUrl.isNullOrBlank() && imageUrl.contains("firebase")) {
                        try {
                            val storageRef =
                                    FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
                            storageRef.delete().await()
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "Failed to delete profile image: ${e.message}")
                        }
                    }
                }

                // F) Delete User Profile Document
                userDocRef.delete().await()

                // G) Delete Auth Account
                user.delete().await()

                // H) Handle Local Data & Logout Logic
                try {
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                                    context,
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions
                                            .DEFAULT_SIGN_IN
                            )
                            .signOut()
                } catch (e: Exception) {
                    // Ignore signer out errors
                }

                if (deleteLocalData) {
                    // Full Wipe
                    val currentProfile = this@MainViewModel.db.userProfileDao().getProfile()
                    deleteOldProfileImage(currentProfile?.profileImagePath)

                    repository.clearAllLocalData()
                    this@MainViewModel.db.userProfileDao().clearProfile()
                    prefs.clearForLogout()
                    _userProfile.value = null
                    _selectedListId.value = null
                } else {
                    // Keep Local -> Switch to Guest
                    val currentProfile = this@MainViewModel.db.userProfileDao().getProfile()
                    if (currentProfile != null) {
                        val updated = currentProfile.copy(profileImageRemoteUrl = null)
                        this@MainViewModel.db.userProfileDao().saveProfile(updated)
                        _userProfile.value = updated
                    }
                    prefs.switchToGuestMode()
                }

                onSuccess()
            } catch (e: Exception) {
                if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                    onError("Re-authentication required")
                } else {
                    Log.e("MainViewModel", "Delete account failed", e)
                    onError(e.message ?: "Unknown error during deletion")
                }
            }
        }
    }

    fun deleteGuestData(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Delete Profile Image
                val currentProfile = this@MainViewModel.db.userProfileDao().getProfile()
                deleteOldProfileImage(currentProfile?.profileImagePath)

                // 2. Clear DB
                repository.clearAllLocalData()
                this@MainViewModel.db.userProfileDao().clearProfile()

                // 3. Clear Prefs
                prefs.clearForLogout()

                // 4. Reset State
                _userProfile.value = null
                _selectedListId.value = null

                // 5. Ensure Google Sign Out (just in case)
                try {
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(
                                    context,
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions
                                            .DEFAULT_SIGN_IN
                            )
                            .signOut()
                } catch (e: Exception) {
                    // Ignore
                }

                onSuccess()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Guest wipe failed", e)
            }
        }
    }

    // Helper reused by leaveSharedList and deleteAccount
    private suspend fun leaveSharedListLogic(uuid: String, uid: String, db: FirebaseFirestore) {
        // Mark status removed
        db.collection("sharedLists")
                .document(uuid)
                .collection("sharedWith")
                .document(uid)
                .update(
                        mapOf(
                                "status" to "removed",
                                "respondedAt" to com.google.firebase.Timestamp.now()
                        )
                )
                .await()
    }

    // Incoming INVITES list
    fun getIncomingSharedLists(): Flow<List<SharedListInvite>> = callbackFlow {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            close()
            return@callbackFlow
        }

        val ref =
                FirebaseFirestore.getInstance()
                        .collection("notifications")
                        .document(uid)
                        .collection("items")
                        .whereEqualTo("type", "LIST_INVITE")

        val listener =
                ref.addSnapshotListener { snapshot, _ ->
                    val invites =
                            snapshot?.documents?.mapNotNull { doc ->
                                val data = doc.data ?: return@mapNotNull null
                                val fromUid = data["from"]?.toString() ?: return@mapNotNull null
                                val fromName = data["fromName"]?.toString() ?: "Unknown"
                                val listName = data["listName"]?.toString() ?: "Shared List"
                                val uuid = data["uuid"]?.toString() ?: return@mapNotNull null
                                SharedListInvite(fromUid, fromName, 0L, listName, uuid)
                            }
                                    ?: emptyList()
                    trySend(invites)
                }

        awaitClose { listener.remove() }
    }

    fun getPendingSentListShares(): Flow<List<SharedListInvite>> = callbackFlow {
        val me =
                FirebaseAuth.getInstance().currentUser
                        ?: run {
                            close()
                            return@callbackFlow
                        }
        val db = FirebaseFirestore.getInstance()

        // Listen to lists I own
        val listsRef = db.collection("sharedLists").whereEqualTo("ownerId", me.uid)
        val listsListener =
                listsRef.addSnapshotListener { snapshot, _ ->
                    viewModelScope.launch {
                        val result = mutableListOf<SharedListInvite>()

                        val docs = snapshot?.documents ?: emptyList()
                        for (doc in docs) {
                            val uuid = doc.id
                            val listName = doc.getString("name") ?: "Shared List"

                            // Check if any pending invitees remain
                            val pending =
                                    db.collection("sharedLists")
                                            .document(uuid)
                                            .collection("sharedWith")
                                            .whereEqualTo("status", "pending")
                                            .get()
                                            .await()

                            if (!pending.isEmpty) {
                                // We only need one row per list in the "Pending Sent" popup
                                result +=
                                        SharedListInvite(
                                                fromUid = me.uid,
                                                fromName =
                                                        "", // optional; not shown in Pending popup
                                                listId = 0L, // not used in the new flow
                                                listName = listName,
                                                uuid = uuid
                                        )
                            }
                        }
                        trySend(result)
                    }
                }

        awaitClose { listsListener.remove() }
    }

    suspend fun onSwipeDeleteList(db: FirebaseFirestore, listUuid: String, isOwner: Boolean) {
        if (isOwner) {
            stopSharingForEveryone(listUuid)
            return
        }
        val local = repository.getTaskLists().first().firstOrNull { it.uuid == listUuid }
        if (local != null) {
            leaveSharedList(local.id)
        } else {
            val me = FirebaseAuth.getInstance().currentUser ?: return
            FirebaseFirestore.getInstance()
                    .collection("sharedLists")
                    .document(listUuid)
                    .collection("sharedWith")
                    .document(me.uid)
                    .update(
                            mapOf(
                                    "status" to "removed",
                                    "respondedAt" to com.google.firebase.Timestamp.now()
                            )
                    )
                    .await()
        }
    }
    // </editor-fold>

    // <editor-fold desc="Export/Share as .prioritizer">
    fun shareList(context: Context, id: Long) {
        viewModelScope.launch {
            val list = repository.getTaskListById(id) ?: return@launch
            val tasks = repository.getTasksForList(id).first()
            val json = createTaskExport(tasks)
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
    // </editor-fold>

    // <editor-fold desc="FIRESTORE SYNC HELPERS">
    private suspend fun syncListToFirestore(list: TaskListEntity) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (!user.providerData.any { it.providerId == "google.com" }) return

        val listData =
                mapOf(
                        "id" to list.id,
                        "uuid" to list.uuid,
                        "name" to list.name,
                        "todoCount" to list.todoCount,
                        "completedCount" to list.completedCount,
                        "totalCount" to list.totalCount
                )

        val db = FirebaseFirestore.getInstance()

        val userDocId = resolveUserListDocId(user.uid, list)

        // Keep user's convenience copy up-to-date
        db.collection("users")
                .document(user.uid)
                .collection("lists")
                .document(userDocId)
                .set(listData, SetOptions.merge())

        // If shared, also patch the sharedLists doc metadata
        if (list.isShared && !list.uuid.isNullOrBlank()) {
            try {
                db.collection("sharedLists").document(list.uuid!!).set(listData, SetOptions.merge())
            } catch (_: Exception) {
                /* ignore */
            }
        }
    }

    private suspend fun syncTaskToFirestore(task: TaskEntity) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val list = repository.getTaskListById(task.listId) ?: return

        val taskData =
                mapOf(
                        "id" to task.id,
                        "description" to task.description,
                        "priority" to task.priority,
                        "done" to task.done,
                        "listId" to task.listId,
                        "updatedBy" to user.uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "createdBy" to user.uid
                )

        if (list.isShared && !list.uuid.isNullOrBlank()) {
            db.collection("sharedLists")
                    .document(list.uuid!!)
                    .collection("tasks")
                    .document(task.id.toString())
                    .set(taskData, SetOptions.merge())
                    .await()
        } else {
            val userDocId = resolveUserListDocId(user.uid, list)

            db.collection("users")
                    .document(user.uid)
                    .collection("lists")
                    .document(userDocId)
                    .collection("tasks")
                    .document(task.id.toString())
                    .set(taskData, SetOptions.merge())
                    .await()
        }
    }

    private suspend fun deleteTaskFromFirestore(list: TaskListEntity, task: TaskEntity) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        if (list.isShared && !list.uuid.isNullOrBlank()) {
            val ref =
                    db.collection("sharedLists")
                            .document(list.uuid!!)
                            .collection("tasks")
                            .document(task.id.toString())

            val batch = db.batch()
            batch.update(
                    ref,
                    mapOf("deletedBy" to user.uid, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.delete(ref)
            batch.commit().await()
        } else {
            val userDocId = resolveUserListDocId(user.uid, list)
            db.collection("users")
                    .document(user.uid)
                    .collection("lists")
                    .document(userDocId)
                    .collection("tasks")
                    .document(task.id.toString())
                    .delete()
                    .await()
        }
    }

    // </editor-fold>

    // <editor-fold desc="USER PROFILE MANAGEMENT">
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    fun loadUserProfile() {
        viewModelScope.launch {
            val profile = db.userProfileDao().getProfile()
            _userProfile.value = profile
        }
    }

    fun updateUserProfile(nickname: String, imageBytes: ByteArray?, context: Context) {
        viewModelScope.launch {
            val oldProfile = db.userProfileDao().getProfile()
            var firebaseImageUrl: String? = oldProfile?.profileImageRemoteUrl
            var localPath: String? = oldProfile?.profileImagePath

            val currentUser = FirebaseAuth.getInstance().currentUser

            if (imageBytes != null) {
                // Delete old Firebase image if applicable (only if logged in)
                if (currentUser != null && firebaseImageUrl?.contains("firebase") == true) {
                    try {
                        val previousRef =
                                FirebaseStorage.getInstance().getReferenceFromUrl(firebaseImageUrl)
                        previousRef.delete()
                    } catch (_: Exception) {}
                }

                // Save locally
                localPath = saveProfileImageToInternalStorage(context, imageBytes)

                // Upload new (only if logged in)
                if (currentUser != null) {
                    val filename = "profile_${currentUser.uid}_${System.currentTimeMillis()}.jpg"
                    val ref =
                            FirebaseStorage.getInstance()
                                    .reference
                                    .child("profile_images/$filename")
                    try {
                        val result =
                                ref.putBytes(imageBytes)
                                        .continueWithTask { task ->
                                            if (!task.isSuccessful)
                                                    throw task.exception
                                                            ?: Exception("Upload failed")
                                            ref.downloadUrl
                                        }
                                        .await()
                        firebaseImageUrl = result.toString()
                    } catch (_: Exception) {
                        firebaseImageUrl = null
                    }
                } else {
                    firebaseImageUrl = null
                }
            } else {
                deleteOldProfileImage(localPath)
                localPath = null
                firebaseImageUrl = null
                if (currentUser != null) {
                    deleteProfileImageFromFirebase()
                }
            }

            val updatedProfile =
                    UserProfile(
                            id = 0,
                            nickname = nickname,
                            profileImagePath = localPath,
                            profileImageRemoteUrl = firebaseImageUrl,
                            friendCode = oldProfile?.friendCode ?: generateFriendCode()
                    )

            _userProfile.value = updatedProfile
            db.userProfileDao().saveProfile(updatedProfile)

            // Push metadata to Firestore (only if logged in)
            if (currentUser != null) {
                FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUser.uid)
                        .set(
                                mapOf(
                                        "displayName" to nickname,
                                        "profileImageUrl" to firebaseImageUrl,
                                        "friendCode" to updatedProfile.friendCode
                                ),
                                SetOptions.merge()
                        )
            }

            loadUserProfile()
        }
    }

    private fun deleteProfileImageFromFirebase() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val ref = FirebaseStorage.getInstance().getReference("profileImages/${user.uid}.jpg")
        ref.delete().addOnSuccessListener {}.addOnFailureListener { it.printStackTrace() }
    }

    fun saveFcmToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userDoc = FirebaseFirestore.getInstance().collection("users").document(user.uid)
        userDoc.update("fcmToken", token)
    }

    private var restoreJob: Job? = null

    fun restoreFromCloud(context: Context): Job {
        if (restoreJob?.isActive == true) {
            Log.d("MainViewModel", "Restore already in progress, joining existing job")
            return restoreJob!!
        }

        val user = FirebaseAuth.getInstance().currentUser
        val firestore = FirebaseFirestore.getInstance()

        restoreJob =
                viewModelScope.launch {
                    // If not logged in (guest path), tell the guard we're done deciding so it can
                    // seed if needed.
                    if (user == null) {
                        markInitialRestoreDone()
                        return@launch
                    }

                    try {
                        val uid = user.uid

                        // --- 1) Restore profile ---
                        val userDoc = firestore.collection("users").document(uid)
                        val snapshot =
                                userDoc.get(com.google.firebase.firestore.Source.SERVER).await()
                        if (snapshot.exists()) {
                            val nickname = snapshot.getString("displayName") ?: "User"
                            val friendCode =
                                    snapshot.getString("friendCode") ?: generateFriendCode()
                            val profileImageUrl = snapshot.getString("profileImageUrl")

                            val dao = db.userProfileDao()
                            var profile =
                                    UserProfile(
                                            id = 0,
                                            nickname = nickname,
                                            friendCode = friendCode,
                                            profileImageRemoteUrl = profileImageUrl,
                                            profileImagePath = null
                                    )

                            if (!profileImageUrl.isNullOrBlank()) {
                                try {
                                    val imageBytes =
                                            withContext(Dispatchers.IO) {
                                                URL(profileImageUrl).openStream().use {
                                                    it.readBytes()
                                                }
                                            }
                                    val path =
                                            saveProfileImageToInternalStorage(context, imageBytes)
                                    profile = profile.copy(profileImagePath = path)
                                } catch (_: Exception) {
                                    /* ignore image failures */
                                }
                            }

                            dao.saveProfile(profile)
                            _userProfile.value = profile
                        }

                        // --- 2) Import lists ---
                        val toStartMirrors = mutableSetOf<String>()
                        var importedCount = 0
                        val listsSnap =
                                firestore
                                        .collection("users")
                                        .document(uid)
                                        .collection("lists")
                                        .get()
                                        .await()

                        // Load local lists once for matching
                        val localLists = repository.getTaskLists().first()

                        for (doc in listsSnap) {
                            var isShared =
                                    (doc.getBoolean("isShared")
                                            ?: false) || !doc.getString("refUuid").isNullOrBlank()
                            val uuid = doc.getString("uuid") ?: doc.getString("refUuid")
                            if (uuid.isNullOrBlank()) continue

                            if (!isShared) {
                                // Private list owned by this user
                                val name = doc.getString("name") ?: "My List"
                                var existing = repository.getTaskListByUuid(uuid)

                                if (existing == null) {
                                    // Try to match specific "orphaned" local list (Guest list) to
                                    // merge
                                    existing =
                                            localLists.firstOrNull { local ->
                                                !local.isShared &&
                                                        local.name == name &&
                                                        local.ownerId != uid
                                            }

                                    if (existing != null) {
                                        val updated = existing.copy(uuid = uuid, ownerId = uid)
                                        repository.updateTaskList(updated)
                                        existing = updated
                                    }
                                }

                                if (existing == null) {
                                    existing = findLocalPrivateListWithoutUuidByNameOwner(name, uid)
                                }

                                val listId =
                                        if (existing != null) {
                                            // If this local row had no uuid (or we just
                                            // merged), ensure it has the cloud uuid
                                            if (existing.uuid != uuid) {
                                                repository.updateTaskList(
                                                        existing.copy(uuid = uuid)
                                                )
                                            }
                                            // Clear and overwrite to keep idempotent
                                            repository.deleteAllTasksInList(existing.id)
                                            repository.updateTaskList(
                                                    existing.copy(
                                                            isShared = false,
                                                            name = name,
                                                            ownerId = uid
                                                    )
                                            )
                                            existing.id
                                        } else {
                                            repository.addTaskList(
                                                    name = name,
                                                    isShared = false,
                                                    uuid = uuid,
                                                    ownerId = uid
                                            )
                                        }

                                // Bring down tasks from
                                // /users/{uid}/lists/{listId}/tasks
                                val taskDocs =
                                        firestore
                                                .collection("users")
                                                .document(uid)
                                                .collection("lists")
                                                .document(doc.id)
                                                .collection("tasks")
                                                .get()
                                                .await()
                                for (tDoc in taskDocs) {
                                    val t = tDoc.data
                                    val desc = t["description"]?.toString() ?: continue
                                    val pri = (t["priority"] as? Long)?.toInt() ?: 5
                                    val done = (t["done"] as? Boolean) ?: false
                                    repository.addTask(desc, pri, listId, done)
                                }

                                importedCount++
                            } else {
                                // Shared list metadata from sharedLists/{uuid}
                                // restoreFromCloud shared branch
                                val sharedSnap =
                                        try {
                                            firestore
                                                    .collection("sharedLists")
                                                    .document(uuid)
                                                    .get()
                                                    .await()
                                        } catch (e: FirebaseFirestoreException) {
                                            if (e.code ==
                                                            FirebaseFirestoreException.Code
                                                                    .PERMISSION_DENIED
                                            ) {
                                                Log.w(
                                                        "Restore",
                                                        "No permission for sharedLists/$uuid, skipping"
                                                )
                                                null
                                            } else throw e
                                        }
                                if (sharedSnap == null || !sharedSnap.exists()) continue
                                if (!sharedSnap.exists()) continue
                                val sharedName = sharedSnap.getString("name") ?: "Shared List"
                                val ownerId = sharedSnap.getString("ownerId")

                                var existing = repository.getTaskListByUuid(uuid)

                                if (existing == null) {
                                    existing =
                                            findLocalSharedListWithoutUuidByNameOwner(
                                                    sharedName,
                                                    ownerId
                                            )
                                }

                                val listId =
                                        if (existing != null) {
                                            if (existing.uuid.isNullOrBlank()) {
                                                repository.updateTaskList(
                                                        existing.copy(uuid = uuid)
                                                )
                                                existing = repository.getTaskListById(existing.id)
                                            }
                                            repository.updateTaskList(
                                                    existing!!.copy(
                                                            isShared = true,
                                                            name = sharedName,
                                                            ownerId = ownerId
                                                    )
                                            )
                                            existing!!.id
                                        } else {
                                            repository.addTaskList(
                                                    name = sharedName,
                                                    isShared = true,
                                                    uuid = uuid,
                                                    ownerId = ownerId
                                            )
                                        }

                                toStartMirrors += uuid
                                importedCount++
                            }
                        }

                        dedupeLocalLists()

                        // Start mirrors after dedupe, targeting the kept local row for each uuid
                        for (uuid in toStartMirrors) {
                            val kept = repository.getTaskListByUuid(uuid)
                            if (kept != null) {
                                startSharedMirror(uuid, kept.id)
                            }
                        }

                        // --- 3) If Google user has ZERO lists after restore, try to adopt existing
                        // local list or seed new one ---
                        if (importedCount == 0 &&
                                        user.providerData.any { it.providerId == "google.com" }
                        ) {
                            val localLists = repository.getTaskLists().first()

                            if (localLists.isNotEmpty()) {
                                // Adopt the first local list (likely the guest list)
                                val existing = localLists.first()
                                val updated =
                                        existing.copy(
                                                ownerId = uid,
                                                uuid =
                                                        if (existing.uuid.isNullOrBlank())
                                                                java.util
                                                                        .UUID
                                                                        .randomUUID()
                                                                        .toString()
                                                        else existing.uuid
                                        )
                                repository.updateTaskList(updated)
                                _selectedListId.value = updated.id
                                syncListToFirestore(updated)
                            } else {
                                // Truly fresh start
                                val newUuid = java.util.UUID.randomUUID().toString()
                                val newListId =
                                        repository.addTaskList(
                                                name = "My First List",
                                                ownerId = uid,
                                                uuid = newUuid
                                        )
                                _selectedListId.value = newListId
                                repository.getTaskListById(newListId)?.let {
                                    syncListToFirestore(it)
                                }
                            }
                        }

                        // --- 4) Ensure we have a selection if any lists exist ---
                        if (_selectedListId.value == null) {
                            val localLists = repository.getTaskLists().first()
                            if (localLists.isNotEmpty())
                                    _selectedListId.value = localLists.first().id
                        }
                    } catch (e: Exception) {
                        Log.e("restoreFromCloud", "Failed to restore: ${e.message}", e)
                        Toast.makeText(
                                        context,
                                        "Cloud restore failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                    } finally {
                        // Signal to the guarded seeding collector that we're done deciding
                        markInitialRestoreDone()
                    }
                }
        return restoreJob!!
    }
    // </editor-fold>

    // <editor-fold desc="FRIEND MANAGEMENT (flat model)">
    fun getFriendList(): Flow<List<FriendProfile>> = callbackFlow {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            close()
            return@callbackFlow
        }

        val ref = FirebaseFirestore.getInstance().collection("friends").whereEqualTo("userId", uid)

        val listener =
                ref.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("getFriendList", "Error listening to friends", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val friends =
                                snapshot.documents.mapNotNull { doc ->
                                    val profile = doc.toObject(FriendProfile::class.java)
                                    if (profile?.uid.isNullOrBlank()) {
                                        Log.w(
                                                "getFriendList",
                                                "Skipped invalid friend profile with empty UID: ${doc.id}"
                                        )
                                        null
                                    } else profile
                                }
                        trySend(friends)
                    } else {
                        trySend(emptyList())
                    }
                }

        awaitClose { listener.remove() }
    }

    fun removeFriendFully(myUid: String, friend: FriendProfile, onComplete: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        val forwardId = "${myUid}_${friend.uid}"
        val reverseId = "${friend.uid}_$myUid"

        // 1. Delete friendship documents
        batch.delete(db.collection("friends").document(forwardId))
        batch.delete(db.collection("friends").document(reverseId))

        // 2. Delete friend requests (both directions)
        batch.delete(
                db.collection("friend_requests")
                        .document(myUid)
                        .collection("incoming")
                        .document(friend.uid)
        )
        batch.delete(
                db.collection("friend_requests")
                        .document(myUid)
                        .collection("outgoing")
                        .document(friend.uid)
        )
        batch.delete(
                db.collection("friend_requests")
                        .document(friend.uid)
                        .collection("incoming")
                        .document(myUid)
        )
        batch.delete(
                db.collection("friend_requests")
                        .document(friend.uid)
                        .collection("outgoing")
                        .document(myUid)
        )

        // Commit then clean up notifications/shared lists loosely
        batch.commit()
                .addOnSuccessListener {
                    db.collection("notifications")
                            .document(friend.uid)
                            .collection("items")
                            .whereEqualTo("from", myUid)
                            .whereIn("type", listOf("LIST_SHARED", "LIST_INVITE"))
                            .get()
                            .addOnSuccessListener { snapshot ->
                                val cleanupBatch = db.batch()
                                snapshot.documents.forEach { cleanupBatch.delete(it.reference) }
                                cleanupBatch.commit()
                            }
                    onComplete()
                }
                .addOnFailureListener {
                    it.printStackTrace()
                    onComplete()
                }
    }
    // </editor-fold>

    // <editor-fold desc="FRIEND REQUESTS">
    fun sendFriendRequest(toCode: String, onResult: (Boolean, String?) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onResult(false, "Not logged in")
            return
        }
        val firestore = FirebaseFirestore.getInstance()

        viewModelScope.launch {
            val senderProfile = db.userProfileDao().getProfile()
            if (senderProfile == null) {
                onResult(false, "Local profile not found")
                return@launch
            }

            firestore
                    .collection("users")
                    .whereEqualTo("friendCode", toCode)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        if (result.isEmpty) {
                            onResult(false, "User not found")
                        } else {
                            val recipientDoc = result.documents.first()
                            val recipientUid = recipientDoc.id
                            if (recipientUid == currentUser.uid) {
                                onResult(false, "Cannot add yourself")
                                return@addOnSuccessListener
                            }

                            val request =
                                    mapOf(
                                            "uid" to currentUser.uid,
                                            "displayName" to senderProfile.nickname,
                                            "profileImageUrl" to senderProfile.profileImageRemoteUrl
                                    )

                            val batch = firestore.batch()

                            // Incoming (recipient owns)
                            val incomingRef =
                                    firestore
                                            .collection("friend_requests")
                                            .document(recipientUid)
                                            .collection("incoming")
                                            .document(currentUser.uid)
                            batch.set(incomingRef, request)

                            // Outgoing (sender owns)
                            val outgoingRef =
                                    firestore
                                            .collection("friend_requests")
                                            .document(currentUser.uid)
                                            .collection("outgoing")
                                            .document(recipientUid)
                            batch.set(outgoingRef, request)

                            batch.commit()
                                    .addOnSuccessListener { onResult(true, null) }
                                    .addOnFailureListener { e ->
                                        onResult(false, e.message ?: "Failed to send request")
                                    }
                        }
                    }
                    .addOnFailureListener { e ->
                        onResult(false, e.message ?: "Error searching for user")
                    }
        }
    }

    fun acceptFriendRequest(fromUid: String, context: Context) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        if (fromUid.isBlank()) return

        val firestore = FirebaseFirestore.getInstance()

        viewModelScope.launch {
            try {
                val senderDoc = firestore.collection("users").document(fromUid).get().await()
                val senderDisplayName = senderDoc.getString("displayName") ?: "Friend"
                val senderImage = senderDoc.getString("profileImageUrl")

                val myEntry =
                        mapOf(
                                "userId" to currentUser.uid,
                                "friendUid" to fromUid,
                                "displayName" to senderDisplayName,
                                "profileImageUrl" to senderImage
                        )

                firestore
                        .collection("friends")
                        .document("${currentUser.uid}_$fromUid")
                        .set(myEntry)
                        .await()

                val myProfile = db.userProfileDao().getProfile() ?: return@launch
                val theirEntry =
                        mapOf(
                                "userId" to fromUid,
                                "friendUid" to currentUser.uid,
                                "displayName" to myProfile.nickname,
                                "profileImageUrl" to myProfile.profileImageRemoteUrl
                        )

                firestore
                        .collection("friends")
                        .document("${fromUid}_${currentUser.uid}")
                        .set(theirEntry)
                        .await()

                firestore
                        .collection("friend_requests")
                        .document(currentUser.uid)
                        .collection("incoming")
                        .document(fromUid)
                        .delete()
                        .await()

                Toast.makeText(
                                context,
                                "You're now friends with $senderDisplayName",
                                Toast.LENGTH_SHORT
                        )
                        .show()
            } catch (e: Exception) {
                Log.e("FriendRequest", "Error accepting request from $fromUid", e)
            }
        }
    }

    fun declineFriendRequest(fromUid: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val firestore = FirebaseFirestore.getInstance()
        firestore
                .collection("friend_requests")
                .document(currentUser.uid)
                .collection("incoming")
                .document(fromUid)
                .delete()
                .addOnSuccessListener { Log.d("FriendRequest", "Declined from $fromUid") }
                .addOnFailureListener { e -> Log.e("FriendRequest", "Error declining", e) }
    }

    fun getIncomingFriendRequests(): Flow<List<FriendProfile>> {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) return flowOf(emptyList())

        return callbackFlow {
            val ref =
                    FirebaseFirestore.getInstance()
                            .collection("friend_requests")
                            .document(currentUser.uid)
                            .collection("incoming")

            val listener =
                    ref.addSnapshotListener { snapshot, _ ->
                        val requests =
                                snapshot?.documents?.mapNotNull {
                                    it.toObject(FriendProfile::class.java)?.copy(uid = it.id)
                                }
                                        ?: emptyList()
                        trySend(requests)
                    }

            awaitClose { listener.remove() }
        }
    }

    fun getPendingSentFriendRequests(): Flow<List<FriendProfile>> {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return flowOf(emptyList())

        return callbackFlow {
            val ref =
                    FirebaseFirestore.getInstance()
                            .collection("friend_requests")
                            .document(currentUser.uid)
                            .collection("outgoing")

            val listener =
                    ref.addSnapshotListener { snapshot, _ ->
                        val requests =
                                snapshot?.documents?.mapNotNull {
                                    it.toObject(FriendProfile::class.java)?.copy(uid = it.id)
                                }
                                        ?: emptyList()
                        trySend(requests)
                    }
            awaitClose { listener.remove() }
        }
    }
    // </editor-fold>

    // <editor-fold desc="UTILITIES">
    private suspend fun downloadImageBytes(url: String): ByteArray =
            withContext(Dispatchers.IO) { URL(url).openStream().use { it.readBytes() } }

    fun ensureSharedMirrorsOnLaunch() {
        viewModelScope.launch {
            repository.getTaskLists().first().forEach { list ->
                if (list.isShared && !list.uuid.isNullOrBlank()) {
                    startSharedMirror(list.uuid!!, list.id)
                }
            }
        }
    }

    private suspend fun resolveUserListDocId(uid: String, list: TaskListEntity): String {
        val db = FirebaseFirestore.getInstance()

        val uuid = list.uuid
        if (!uuid.isNullOrBlank()) {
            val byUuid =
                    db.collection("users")
                            .document(uid)
                            .collection("lists")
                            .whereEqualTo("uuid", uuid)
                            .limit(1)
                            .get()
                            .await()
            if (!byUuid.isEmpty) return byUuid.documents.first().id

            val byRef =
                    db.collection("users")
                            .document(uid)
                            .collection("lists")
                            .whereEqualTo("refUuid", uuid)
                            .limit(1)
                            .get()
                            .await()
            if (!byRef.isEmpty) return byRef.documents.first().id
        }

        return list.id.toString()
    }

    private suspend fun findLocalPrivateListWithoutUuidByNameOwner(
            name: String,
            ownerId: String?
    ): TaskListEntity? {
        val all = repository.getTaskLists().first()
        return all.firstOrNull { local ->
            !local.isShared &&
                    local.uuid.isNullOrBlank() &&
                    local.ownerId == ownerId &&
                    local.name == name
        }
    }

    private suspend fun findLocalSharedListWithoutUuidByNameOwner(
            name: String,
            ownerId: String?
    ): TaskListEntity? {
        val all = repository.getTaskLists().first()
        return all.firstOrNull { local ->
            local.isShared &&
                    local.uuid.isNullOrBlank() &&
                    local.ownerId == ownerId &&
                    local.name == name
        }
    }

    private suspend fun dedupeLocalLists() {
        // Load once
        val all = repository.getTaskLists().first()

        suspend fun taskCount(listId: Long): Int = repository.getTasksForList(listId).first().size

        // restart the shared mirror (if one exists) to point at the kept row
        fun restartSharedMirrorIfNeeded(keep: TaskListEntity) {
            val uuid = keep.uuid
            if (!uuid.isNullOrBlank()) {
                // cancel any existing mirror job for this uuid
                sharedMirrors.remove(uuid)?.cancel()
                // start (or re-start) mirror targeting the kept listId
                startSharedMirror(uuid, keep.id)
            }
        }

        // select the single row to keep in a group
        suspend fun pickKeep(group: List<TaskListEntity>): TaskListEntity {
            val withCounts = group.map { it to taskCount(it.id) }
            return withCounts
                    .sortedWith(
                            compareByDescending<Pair<TaskListEntity, Int>> {
                                it.second
                            } // 1) more tasks wins
                                    .thenByDescending {
                                        it.first.isShared
                                    } // 2) shared beats private
                                    .thenByDescending {
                                        !it.first.uuid.isNullOrBlank()
                                    } // 3) has uuid beats none
                                    .thenBy { it.first.id } // 4) stable tiebreaker
                    )
                    .first()
                    .first
        }

        suspend fun removeAllExcept(keep: TaskListEntity, group: List<TaskListEntity>) {
            val toDelete = group.filter { it.id != keep.id }
            if (toDelete.isEmpty()) return

            // Make sure any shared mirror writes target the kept row
            restartSharedMirrorIfNeeded(keep)

            // If current selection points at a soon-to-be-deleted list, move it to the kept one
            val current = _selectedListId.value
            if (current != null && toDelete.any { it.id == current }) {
                _selectedListId.value = keep.id
            }

            // Delete duplicates (tasks + list rows)
            for (dup in toDelete) {
                try {
                    // local only—do not touch Firestore here
                    repository.deleteAllTasksInList(dup.id)
                    repository.deleteTaskList(dup.id)
                    Log.i(
                            "Dedupe",
                            "Deleted duplicate list id=${dup.id}, uuid=${dup.uuid}, name='${dup.name}', isShared=${dup.isShared}"
                    )
                } catch (e: Exception) {
                    Log.e("Dedupe", "Failed deleting duplicate list id=${dup.id}: ${e.message}", e)
                }
            }
        }

        // --- Pass 1: groups by UUID (non-blank) ---
        val byUuid = all.filter { !it.uuid.isNullOrBlank() }.groupBy { it.uuid!! }
        for ((uuid, group) in byUuid) {
            if (group.size <= 1) continue
            val keep = pickKeep(group)
            Log.w("Dedupe", "UUID=$uuid has ${group.size} local rows; keeping id=${keep.id}")
            removeAllExcept(keep, group)
        }

        // --- Pass 2: groups without UUID, by (normalized name, ownerId, isShared) ---
        val noUuid =
                repository.getTaskLists().first().filter {
                    it.uuid.isNullOrBlank()
                } // refresh after pass 1
        val bySignature = noUuid.groupBy { Triple(it.name.trim(), it.ownerId ?: "", it.isShared) }
        for ((sig, group) in bySignature) {
            if (group.size <= 1) continue
            val keep = pickKeep(group)
            Log.w(
                    "Dedupe",
                    "SIG=$sig has ${group.size} local rows (no uuid); keeping id=${keep.id}"
            )
            removeAllExcept(keep, group)
        }

        // Ensure a selection exists and still points at a real list
        val after = repository.getTaskLists().first()
        val current = _selectedListId.value
        val stillExists = current != null && after.any { it.id == current }
        if (!stillExists) {
            _selectedListId.value = after.firstOrNull()?.id
        }
    }

    private fun stopSharedMirror(uuid: String) {
        sharedMirrors.remove(uuid)?.cancel()
    }

    private fun restartSharedMirror(uuid: String, keepListId: Long) {
        stopSharedMirror(uuid)
        startSharedMirror(uuid, keepListId)
    }

    // </editor-fold>
}
