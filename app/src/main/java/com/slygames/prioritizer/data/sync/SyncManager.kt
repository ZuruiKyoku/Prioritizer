package com.slygames.prioritizer.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.slygames.prioritizer.data.list.SharedListInvite
import com.slygames.prioritizer.data.list.TaskListEntity
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.data.task.TaskRepository
import com.slygames.prioritizer.data.user.ProfileRepository
import com.slygames.prioritizer.data.user.UserProfileDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The single controlled synchronization engine for the app (spec: "Repository -> SyncManager ->
 * Firestore", one owner for listener lifecycles, no per-composable listener creation).
 *
 * This is `@Singleton` with its own [scope] -- a structured, app-lifetime coroutine scope (never
 * `GlobalScope`; see spec on coroutine safety) -- so shared-list mirrors and in-flight writes
 * survive individual screens/ViewModels being torn down by navigation.
 *
 * Identity note: every Firestore task document is keyed by [TaskEntity.uuid], a client-generated
 * id, never by the local Room [TaskEntity.id]. The Room id is only a per-device autoincrement
 * counter; two devices can independently mint the same next id for two different tasks, which
 * would silently overwrite one with the other if it were ever used as a shared/Firestore key.
 */
@Singleton
class SyncManager
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val taskRepository: TaskRepository,
        private val userProfileDao: UserProfileDao,
        private val profileRepository: ProfileRepository,
        private val firestore: FirebaseFirestore,
        private val auth: FirebaseAuth
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sharedMirrors = mutableMapOf<String, Job>()

    // ------------------------------------------------------------------------------------
    // Task / list -> Firestore sync
    // ------------------------------------------------------------------------------------

    suspend fun syncListToFirestore(list: TaskListEntity) {
        val user = auth.currentUser ?: return

        val listData =
                mapOf(
                        "id" to list.id,
                        "uuid" to list.uuid,
                        "name" to list.name,
                        "todoCount" to list.todoCount,
                        "completedCount" to list.completedCount,
                        "totalCount" to list.totalCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                )

        val userDocId = resolveUserListDocId(user.uid, list)
        firestore.collection("users")
                .document(user.uid)
                .collection("lists")
                .document(userDocId)
                .set(listData, SetOptions.merge())

        if (list.isShared && !list.uuid.isNullOrBlank()) {
            runCatching {
                firestore.collection("sharedLists")
                        .document(list.uuid)
                        .set(listData, SetOptions.merge())
            }
        }
    }

    suspend fun syncTaskToFirestore(task: TaskEntity) {
        val user = auth.currentUser ?: return
        val list = taskRepository.getTaskListById(task.listId) ?: return

        val taskData =
                mapOf(
                        "id" to task.id,
                        "uuid" to task.uuid,
                        "description" to task.description,
                        "priority" to task.priority,
                        "done" to task.done,
                        "listId" to task.listId,
                        "updatedBy" to user.uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "createdBy" to (task.createdBy ?: user.uid)
                )

        taskDocRef(user.uid, list, task.uuid).set(taskData, SetOptions.merge()).await()
    }

    suspend fun deleteTaskFromFirestore(list: TaskListEntity, task: TaskEntity) {
        val user = auth.currentUser ?: return
        val ref = taskDocRef(user.uid, list, task.uuid)

        if (list.isShared && !list.uuid.isNullOrBlank()) {
            val batch = firestore.batch()
            batch.update(
                    ref,
                    mapOf("deletedBy" to user.uid, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.delete(ref)
            batch.commit().await()
        } else {
            ref.delete().await()
        }
    }

    suspend fun deleteTasksFromFirestoreBatch(list: TaskListEntity, tasks: List<TaskEntity>) {
        if (tasks.isEmpty()) return
        val uid = auth.currentUser?.uid ?: return

        var opRef: com.google.firebase.firestore.DocumentReference? = null
        if (list.isShared && !list.uuid.isNullOrBlank()) {
            opRef = firestore.collection("sharedLists").document(list.uuid).collection("ops").document("clearCompleted")
            val ids = tasks.map { it.uuid }
            runCatching {
                opRef.set(
                                mapOf(
                                        "startedAt" to FieldValue.serverTimestamp(),
                                        "actorUid" to uid,
                                        "taskIds" to ids
                                )
                        )
                        .await()
            }
        }

        try {
            for (chunk in tasks.chunked(500)) {
                val batch = firestore.batch()
                chunk.forEach { t -> batch.delete(taskDocRef(uid, list, t.uuid)) }
                batch.commit().await()
            }
        } finally {
            runCatching { opRef?.delete()?.await() }
        }
    }

    private fun taskDocRef(
            uid: String,
            list: TaskListEntity,
            taskUuid: String
    ): com.google.firebase.firestore.DocumentReference {
        return if (list.isShared && !list.uuid.isNullOrBlank()) {
            firestore.collection("sharedLists").document(list.uuid).collection("tasks").document(taskUuid)
        } else {
            // Falls back to list.id.toString() only when the list itself has no uuid yet
            // (shouldn't normally happen -- every list gets one on creation).
            val listKey = list.uuid?.takeIf { it.isNotBlank() } ?: list.id.toString()
            firestore.collection("users")
                    .document(uid)
                    .collection("lists")
                    .document(listKey)
                    .collection("tasks")
                    .document(taskUuid)
        }
    }

    private suspend fun deletePrivateListTasksFromFirestore(uid: String, listId: Long) {
        val list = taskRepository.getTaskListById(listId) ?: return
        val userDocId = resolveUserListDocId(uid, list)
        val tasksRef =
                firestore.collection("users").document(uid).collection("lists").document(userDocId).collection("tasks")
        val snap = tasksRef.get().await()
        if (snap.isEmpty) return
        for (chunk in snap.documents.chunked(500)) {
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    suspend fun resolveUserListDocId(uid: String, list: TaskListEntity): String {
        val uuid = list.uuid
        if (!uuid.isNullOrBlank()) {
            val byUuid =
                    firestore.collection("users").document(uid).collection("lists")
                            .whereEqualTo("uuid", uuid).limit(1).get().await()
            if (!byUuid.isEmpty) return byUuid.documents.first().id

            val byRef =
                    firestore.collection("users").document(uid).collection("lists")
                            .whereEqualTo("refUuid", uuid).limit(1).get().await()
            if (!byRef.isEmpty) return byRef.documents.first().id
        }
        return list.id.toString()
    }

    // ------------------------------------------------------------------------------------
    // Shared lists: sharing, invites, membership
    // ------------------------------------------------------------------------------------

    private fun ensureUuid(list: TaskListEntity): TaskListEntity {
        if (!list.uuid.isNullOrBlank()) return list
        val withUuid = list.copy(uuid = UUID.randomUUID().toString())
        scope.launch { taskRepository.updateTaskList(withUuid) }
        return withUuid
    }

    private data class SharedWithStatus(
            val uid: String = "",
            val status: String = "pending",
            val invitedAt: com.google.firebase.Timestamp? = null,
            val respondedAt: com.google.firebase.Timestamp? = null
    )

    suspend fun startShareList(listId: Long, friendUids: List<String>, fromDisplayName: String) {
        val me = auth.currentUser ?: return
        val local = taskRepository.getTaskListById(listId) ?: return
        val withUuid = ensureUuid(local)
        val uuid = withUuid.uuid

        val tasks = taskRepository.getTasksForList(listId).first()

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
        firestore.collection("sharedLists").document(uuid).set(listDoc, SetOptions.merge()).await()

        val tasksRef = firestore.collection("sharedLists").document(uuid).collection("tasks")
        val taskBatch = firestore.batch()
        tasks.forEach { t ->
            val td =
                    mapOf(
                            "id" to t.id,
                            "uuid" to t.uuid,
                            "description" to t.description,
                            "priority" to t.priority,
                            "done" to t.done,
                            "listId" to withUuid.id,
                            "createdBy" to (t.createdBy ?: me.uid)
                    )
            taskBatch.set(tasksRef.document(t.uuid), td, SetOptions.merge())
        }
        taskBatch.commit().await()

        val sharedWithRef = firestore.collection("sharedLists").document(uuid).collection("sharedWith")
        val membershipBatch = firestore.batch()
        membershipBatch.set(
                sharedWithRef.document(me.uid),
                SharedWithStatus(uid = me.uid, status = "accepted", respondedAt = com.google.firebase.Timestamp.now())
        )
        friendUids.forEach { invitee ->
            membershipBatch.set(
                    sharedWithRef.document(invitee),
                    SharedWithStatus(uid = invitee, status = "pending", invitedAt = com.google.firebase.Timestamp.now())
            )
        }
        membershipBatch.commit().await()

        friendUids.forEach { invitee ->
            firestore.collection("notifications").document(invitee).collection("items").add(
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

        taskRepository.updateTaskList(withUuid.copy(isShared = true, ownerId = me.uid))

        val userListRef = firestore.collection("users").document(me.uid).collection("lists").document(listId.toString())
        userListRef.delete()
        userListRef.collection("tasks").get().await().documents.forEach { it.reference.delete() }
        userListRef.set(mapOf("refUuid" to uuid, "isShared" to true, "name" to withUuid.name), SetOptions.merge())

        startSharedMirror(uuid, listId)
    }

    suspend fun inviteUserToSharedList(uuid: String, inviteeUid: String, ownerDisplayName: String, listName: String) {
        val me = auth.currentUser ?: return
        firestore.collection("sharedLists").document(uuid).collection("sharedWith").document(inviteeUid)
                .set(mapOf("uid" to inviteeUid, "status" to "pending", "invitedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .await()

        firestore.collection("notifications").document(inviteeUid).collection("items").add(
                mapOf(
                        "type" to "LIST_INVITE",
                        "from" to me.uid,
                        "fromName" to ownerDisplayName,
                        "uuid" to uuid,
                        "listName" to listName,
                        "timestamp" to FieldValue.serverTimestamp()
                )
        ).await()
    }

    suspend fun removeMemberFromSharedList(uuid: String, memberUid: String) {
        firestore.collection("sharedLists").document(uuid).collection("sharedWith").document(memberUid).delete().await()
    }

    /** Current pending/accepted membership for a shared list, keyed by uid -> status. Used to
     * pre-populate the owner's "manage sharing" checklist. */
    suspend fun getSharedListMemberStatuses(uuid: String): Map<String, String> {
        val snap =
                firestore.collection("sharedLists").document(uuid).collection("sharedWith")
                        .get(com.google.firebase.firestore.Source.SERVER).await()
        return snap.documents.mapNotNull { doc ->
            val status = doc.getString("status") ?: return@mapNotNull null
            if (status == "pending" || status == "accepted") doc.id to status else null
        }.toMap()
    }

    /**
     * Owner-only bulk membership edit from the "manage sharing" checklist: added members go
     * straight to "accepted" (no invite/notification round trip -- the owner is directly curating
     * their own list, not asking permission), removed members are dropped from the list entirely.
     */
    suspend fun applyOwnerSharingChanges(uuid: String, added: List<String>, removed: List<String>) {
        val sharedWithRef = firestore.collection("sharedLists").document(uuid).collection("sharedWith")
        added.forEach { uid ->
            sharedWithRef.document(uid)
                    .set(mapOf("status" to "accepted", "addedAt" to FieldValue.serverTimestamp()))
                    .await()
        }
        removed.forEach { uid -> sharedWithRef.document(uid).delete().await() }
    }

    suspend fun acceptSharedListInvite(uuid: String): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            firestore.collection("sharedLists").document(uuid).collection("sharedWith").document(me.uid)
                    .update(mapOf("status" to "accepted", "respondedAt" to FieldValue.serverTimestamp()))
                    .await()

            val listDoc = firestore.collection("sharedLists").document(uuid).get().await()
            if (!listDoc.exists()) return Result.failure(IllegalStateException("List no longer exists"))
            val name = listDoc.getString("name") ?: "Shared List"
            val ownerId = listDoc.getString("ownerId")
            val existing = taskRepository.getTaskListByUuid(uuid)

            val localListId =
                    if (existing != null) {
                        taskRepository.updateTaskList(existing.copy(isShared = true, name = name, ownerId = ownerId))
                        existing.id
                    } else {
                        taskRepository.addTaskList(name = name, isShared = true, uuid = uuid, ownerId = ownerId)
                    }

            firestore.collection("users").document(me.uid).collection("lists").document(localListId.toString())
                    .set(mapOf("refUuid" to uuid, "isShared" to true, "name" to name), SetOptions.merge())

            startSharedMirror(uuid, localListId)

            firestore.collection("notifications").document(me.uid).collection("items")
                    .whereEqualTo("type", "LIST_INVITE").whereEqualTo("uuid", uuid).get().await()
                    .documents.forEach { it.reference.delete() }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SyncManager", "Accept failed for $uuid", e)
            Result.failure(e)
        }
    }

    suspend fun declineSharedListInvite(uuid: String): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            firestore.collection("sharedLists").document(uuid).collection("sharedWith").document(me.uid)
                    .update(mapOf("status" to "declined", "respondedAt" to FieldValue.serverTimestamp()))
                    .await()
            firestore.collection("notifications").document(me.uid).collection("items")
                    .whereEqualTo("type", "LIST_INVITE").whereEqualTo("uuid", uuid).get().await()
                    .documents.forEach { it.reference.delete() }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SyncManager", "Decline failed for $uuid", e)
            Result.failure(e)
        }
    }

    /** Mirrors `sharedLists/{uuid}/tasks` into the local Room list [localListId] in real time. */
    fun startSharedMirror(uuid: String, localListId: Long) {
        sharedMirrors[uuid]?.cancel()

        val job =
                scope.launch {
                    val ref = firestore.collection("sharedLists").document(uuid).collection("tasks")
                    ref.addSnapshotListener { snap, e ->
                        if (e != null) {
                            if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                Log.w("SyncManager", "Permission denied for $uuid; stopping mirror")
                                sharedMirrors.remove(uuid)?.cancel()
                            }
                            return@addSnapshotListener
                        }

                        scope.launch {
                            val docs = snap?.documents ?: emptyList()
                            val remoteUuids = mutableSetOf<String>()

                            docs.forEach { d ->
                                val desc = d.getString("description") ?: return@forEach
                                val remoteUuid = d.getString("uuid") ?: d.id
                                remoteUuids += remoteUuid

                                val pri = (d.getLong("priority") ?: 5L).toInt()
                                val done = d.getBoolean("done") ?: false
                                val legacyRoomId = d.getLong("id")

                                // Adopt an existing local row if we've already mirrored this task
                                // (by uuid), or if it was mirrored under the pre-uuid scheme where
                                // the local Room id was set equal to the remote numeric id.
                                val existing =
                                        taskRepository.getTaskByUuid(remoteUuid)
                                                ?: legacyRoomId?.let {
                                                    taskRepository.getTaskByLegacyRoomId(it, localListId)
                                                }

                                taskRepository.upsertTask(
                                        TaskEntity(
                                                id = existing?.id ?: 0,
                                                uuid = remoteUuid,
                                                description = desc,
                                                priority = pri,
                                                done = done,
                                                listId = localListId,
                                                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                                updatedAt = System.currentTimeMillis(),
                                                createdBy = d.getString("createdBy"),
                                                updatedBy = d.getString("updatedBy")
                                        )
                                )

                                // Self-heal legacy docs (no uuid field yet) so future edits from
                                // this client target the same doc instead of forking a new one.
                                if (d.getString("uuid") == null) {
                                    runCatching {
                                        d.reference.set(mapOf("uuid" to remoteUuid), SetOptions.merge())
                                    }
                                }
                            }

                            val localTasks = taskRepository.getTasksForList(localListId).first()
                            localTasks.filter { it.uuid !in remoteUuids }.forEach { taskRepository.deleteTask(it) }

                            val updatedTasks = taskRepository.getTasksForList(localListId).first()
                            taskRepository.getTaskListById(localListId)?.let {
                                taskRepository.updateTaskList(
                                        it.copy(
                                                todoCount = updatedTasks.count { t -> !t.done },
                                                completedCount = updatedTasks.count { t -> t.done },
                                                totalCount = updatedTasks.size
                                        )
                                )
                            }
                        }
                    }
                }
        sharedMirrors[uuid] = job
    }

    private fun stopSharedMirror(uuid: String) {
        sharedMirrors.remove(uuid)?.cancel()
    }

    fun ensureSharedMirrorsOnLaunch() {
        scope.launch {
            taskRepository.getTaskLists().first().forEach { list ->
                if (list.isShared && !list.uuid.isNullOrBlank()) {
                    startSharedMirror(list.uuid, list.id)
                }
            }
        }
    }

    suspend fun leaveSharedList(listId: Long): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val list = taskRepository.getTaskListById(listId) ?: return Result.failure(IllegalStateException("List not found"))
        if (!list.isShared || list.uuid.isNullOrBlank()) return Result.failure(IllegalStateException("Not a shared list"))
        val uuid = list.uuid

        stopSharedMirror(uuid)

        val remoteResult = runCatching {
            leaveSharedListLogic(uuid, me.uid)
            firestore.collection("users").document(me.uid).collection("lists").document(listId.toString()).delete().await()
            Unit
        }

        taskRepository.deleteAllTasksInList(listId)
        taskRepository.deleteTaskList(listId)

        return remoteResult
    }

    private suspend fun deleteCollectionInBatches(
            colRef: com.google.firebase.firestore.CollectionReference,
            batchSize: Long = 300L
    ) {
        while (true) {
            val snap = colRef.limit(batchSize).get(com.google.firebase.firestore.Source.SERVER).await()
            if (snap.isEmpty) break
            val batch = colRef.firestore.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    suspend fun stopSharingForEveryone(uuid: String) {
        val me = auth.currentUser ?: return
        val listRef = firestore.collection("sharedLists").document(uuid)
        val doc = listRef.get(com.google.firebase.firestore.Source.SERVER).await()
        if (!doc.exists() || doc.getString("ownerId") != me.uid) return

        deleteCollectionInBatches(listRef.collection("tasks"))
        deleteCollectionInBatches(listRef.collection("sharedWith"))
        listRef.delete().await()
    }

    private suspend fun leaveSharedListLogic(uuid: String, uid: String) {
        firestore.collection("sharedLists").document(uuid).collection("sharedWith").document(uid)
                .update(mapOf("status" to "removed", "respondedAt" to com.google.firebase.Timestamp.now()))
                .await()
    }

    suspend fun onSwipeDeleteList(listUuid: String, isOwner: Boolean) {
        if (isOwner) {
            stopSharingForEveryone(listUuid)
            return
        }
        val local = taskRepository.getTaskLists().first().firstOrNull { it.uuid == listUuid }
        if (local != null) {
            leaveSharedList(local.id)
        } else {
            val me = auth.currentUser ?: return
            firestore.collection("sharedLists").document(listUuid).collection("sharedWith").document(me.uid)
                    .update(mapOf("status" to "removed", "respondedAt" to com.google.firebase.Timestamp.now()))
                    .await()
        }
    }

    fun getIncomingSharedLists(): Flow<List<SharedListInvite>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close()
            return@callbackFlow
        }
        val ref = firestore.collection("notifications").document(uid).collection("items").whereEqualTo("type", "LIST_INVITE")
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
        val me = auth.currentUser
        if (me == null) {
            close()
            return@callbackFlow
        }
        val listsRef = firestore.collection("sharedLists").whereEqualTo("ownerId", me.uid)
        val listener =
                listsRef.addSnapshotListener { snapshot, _ ->
                    scope.launch {
                        val result = mutableListOf<SharedListInvite>()
                        for (doc in snapshot?.documents ?: emptyList()) {
                            val uuid = doc.id
                            val listName = doc.getString("name") ?: "Shared List"
                            val pending =
                                    firestore.collection("sharedLists").document(uuid).collection("sharedWith")
                                            .whereEqualTo("status", "pending").get().await()
                            if (!pending.isEmpty) {
                                result += SharedListInvite(fromUid = me.uid, fromName = "", listId = 0L, listName = listName, uuid = uuid)
                            }
                        }
                        trySend(result)
                    }
                }
        awaitClose { listener.remove() }
    }

    // ------------------------------------------------------------------------------------
    // Cloud restore (on sign-in) + local dedupe
    // ------------------------------------------------------------------------------------

    private var restoreJob: Job? = null

    /** Idempotent: safe to call every time the user signs in, including repeated app launches. */
    fun restoreFromCloud(onSelectListFallback: suspend (Long?) -> Unit): Job {
        restoreJob?.let { if (it.isActive) return it }

        val user = auth.currentUser
        val job =
                scope.launch {
                    if (user == null) return@launch
                    try {
                        profileRepository.restoreProfileFromCloud(user.uid)
                        val toStartMirrors = mutableSetOf<String>()
                        var importedCount = 0

                        val listsSnap = firestore.collection("users").document(user.uid).collection("lists").get().await()
                        val localLists = taskRepository.getTaskLists().first()

                        for (doc in listsSnap) {
                            val isShared = (doc.getBoolean("isShared") ?: false) || !doc.getString("refUuid").isNullOrBlank()
                            val uuid = doc.getString("uuid") ?: doc.getString("refUuid")
                            if (uuid.isNullOrBlank()) continue

                            if (!isShared) {
                                importedCount += restorePrivateList(user.uid, doc, uuid, localLists)
                            } else {
                                val imported = restoreSharedListMeta(uuid)
                                if (imported) {
                                    toStartMirrors += uuid
                                    importedCount++
                                }
                            }
                        }

                        dedupeLocalLists()

                        for (uuid in toStartMirrors) {
                            taskRepository.getTaskListByUuid(uuid)?.let { startSharedMirror(uuid, it.id) }
                        }

                        if (importedCount == 0) {
                            adoptOrSeedFirstList(user.uid)
                        }

                        val current = taskRepository.getTaskLists().first()
                        onSelectListFallback(current.firstOrNull()?.id)
                    } catch (e: Exception) {
                        Log.e("SyncManager", "restoreFromCloud failed: ${e.message}", e)
                    }
                }
        restoreJob = job
        return job
    }

    private suspend fun restorePrivateList(
            uid: String,
            doc: com.google.firebase.firestore.DocumentSnapshot,
            uuid: String,
            localLists: List<TaskListEntity>
    ): Int {
        val name = doc.getString("name") ?: "My List"
        var existing = taskRepository.getTaskListByUuid(uuid)

        if (existing == null) {
            existing = localLists.firstOrNull { local -> !local.isShared && local.name == name && local.ownerId != uid }
                    ?.also { taskRepository.updateTaskList(it.copy(uuid = uuid, ownerId = uid)) }
        }
        if (existing == null) {
            existing = taskRepository.getTaskLists().first().firstOrNull { local ->
                !local.isShared && local.uuid.isNullOrBlank() && local.ownerId == uid && local.name == name
            }
        }

        val listId =
                if (existing != null) {
                    if (existing.uuid != uuid) taskRepository.updateTaskList(existing.copy(uuid = uuid))
                    taskRepository.deleteAllTasksInList(existing.id)
                    taskRepository.updateTaskList(existing.copy(isShared = false, name = name, ownerId = uid))
                    existing.id
                } else {
                    taskRepository.addTaskList(name = name, isShared = false, uuid = uuid, ownerId = uid)
                }

        val taskDocs =
                firestore.collection("users").document(uid).collection("lists").document(doc.id).collection("tasks").get().await()
        for (tDoc in taskDocs) {
            val desc = tDoc.getString("description") ?: continue
            val pri = (tDoc.getLong("priority") ?: 5L).toInt()
            val done = tDoc.getBoolean("done") ?: false
            taskRepository.addTask(desc, pri, listId, done, createdBy = uid)
        }
        return 1
    }

    private suspend fun restoreSharedListMeta(uuid: String): Boolean {
        val sharedSnap =
                try {
                    firestore.collection("sharedLists").document(uuid).get().await()
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.w("SyncManager", "No permission for sharedLists/$uuid, skipping")
                        return false
                    } else throw e
                }
        if (!sharedSnap.exists()) return false

        val sharedName = sharedSnap.getString("name") ?: "Shared List"
        val ownerId = sharedSnap.getString("ownerId")
        var existing = taskRepository.getTaskListByUuid(uuid)

        if (existing == null) {
            existing = taskRepository.getTaskLists().first().firstOrNull { local ->
                local.isShared && local.uuid.isNullOrBlank() && local.ownerId == ownerId && local.name == sharedName
            }
        }

        if (existing != null) {
            if (existing.uuid.isNullOrBlank()) {
                taskRepository.updateTaskList(existing.copy(uuid = uuid))
                existing = taskRepository.getTaskListById(existing.id)
            }
            existing?.let { taskRepository.updateTaskList(it.copy(isShared = true, name = sharedName, ownerId = ownerId)) }
        } else {
            taskRepository.addTaskList(name = sharedName, isShared = true, uuid = uuid, ownerId = ownerId)
        }
        return true
    }

    private suspend fun adoptOrSeedFirstList(uid: String) {
        val localLists = taskRepository.getTaskLists().first()
        if (localLists.isNotEmpty()) {
            val existing = localLists.first()
            val updated =
                    existing.copy(
                            ownerId = uid,
                            uuid = existing.uuid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                    )
            taskRepository.updateTaskList(updated)
            syncListToFirestore(updated)
        } else {
            val newUuid = UUID.randomUUID().toString()
            val newListId = taskRepository.addTaskList(name = "My Tasks", ownerId = uid, uuid = newUuid)
            taskRepository.getTaskListById(newListId)?.let { syncListToFirestore(it) }
        }
    }

    /**
     * Collapses duplicate local list rows that can accumulate from repeated restores/imports
     * (spec: never create "List" + "List Copy" duplicates). Groups by uuid first, then by
     * (name, owner, isShared) for legacy rows that predate uuids.
     */
    suspend fun dedupeLocalLists() {
        val all = taskRepository.getTaskLists().first()
        suspend fun taskCount(listId: Long) = taskRepository.getTasksForList(listId).first().size

        suspend fun pickKeep(group: List<TaskListEntity>): TaskListEntity =
                group.map { it to taskCount(it.id) }
                        .sortedWith(
                                compareByDescending<Pair<TaskListEntity, Int>> { it.second }
                                        .thenByDescending { it.first.isShared }
                                        .thenByDescending { !it.first.uuid.isNullOrBlank() }
                                        .thenBy { it.first.id }
                        )
                        .first()
                        .first

        suspend fun removeAllExcept(keep: TaskListEntity, group: List<TaskListEntity>) {
            val toDelete = group.filter { it.id != keep.id }
            if (toDelete.isEmpty()) return
            keep.uuid?.takeIf { it.isNotBlank() }?.let { stopSharedMirror(it); startSharedMirror(it, keep.id) }
            for (dup in toDelete) {
                runCatching {
                    taskRepository.deleteAllTasksInList(dup.id)
                    taskRepository.deleteTaskList(dup.id)
                }
            }
        }

        val byUuid = all.filter { !it.uuid.isNullOrBlank() }.groupBy { it.uuid!! }
        for ((_, group) in byUuid) {
            if (group.size <= 1) continue
            removeAllExcept(pickKeep(group), group)
        }

        val noUuid = taskRepository.getTaskLists().first().filter { it.uuid.isNullOrBlank() }
        val bySignature = noUuid.groupBy { Triple(it.name.trim(), it.ownerId ?: "", it.isShared) }
        for ((_, group) in bySignature) {
            if (group.size <= 1) continue
            removeAllExcept(pickKeep(group), group)
        }
    }

    // ------------------------------------------------------------------------------------
    // Account / guest deletion
    // ------------------------------------------------------------------------------------

    /** Full cascade delete for account removal: owned shared lists, memberships, private lists,
     * friends, requests, profile, and finally the Firebase Auth account itself. */
    suspend fun deleteAccountData(uid: String) {
        val mySharedLists = firestore.collection("sharedLists").whereEqualTo("ownerId", uid).get().await()
        for (doc in mySharedLists) stopSharingForEveryone(doc.id)

        val myListsRef = firestore.collection("users").document(uid).collection("lists")
        val myLists = myListsRef.get().await()
        for (doc in myLists) {
            val uuid = doc.getString("uuid") ?: doc.getString("refUuid")
            val isShared = (doc.getBoolean("isShared") ?: false) || !uuid.isNullOrBlank()
            if (isShared && !uuid.isNullOrBlank()) {
                runCatching { leaveSharedListLogic(uuid, uid) }
            } else {
                deleteCollectionInBatches(doc.reference.collection("tasks"))
            }
        }
        deleteCollectionInBatches(myListsRef)

        val friends1 = firestore.collection("friends").whereEqualTo("userId", uid).get().await()
        val friends2 = firestore.collection("friends").whereEqualTo("friendUid", uid).get().await()
        if (!friends1.isEmpty || !friends2.isEmpty) {
            val batch = firestore.batch()
            friends1.forEach { batch.delete(it.reference) }
            friends2.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        val requestsRef = firestore.collection("friend_requests").document(uid)
        deleteCollectionInBatches(requestsRef.collection("incoming"))
        deleteCollectionInBatches(requestsRef.collection("outgoing"))
        requestsRef.delete().await()

        runCatching {
            val sentToOthers = firestore.collectionGroup("incoming").whereEqualTo("uid", uid).get().await()
            if (!sentToOthers.isEmpty) {
                val batch = firestore.batch()
                sentToOthers.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }

        profileRepository.deleteRemoteProfile(uid)
    }
}
