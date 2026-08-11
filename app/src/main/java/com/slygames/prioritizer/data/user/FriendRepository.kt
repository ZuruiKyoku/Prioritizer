package com.slygames.prioritizer.data.user

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.slygames.prioritizer.util.InputValidation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/** Friends + friend requests. Firestore-backed; friends are treated as always-online data (no
 * offline Room cache) since a stale friend list is low-stakes and this keeps the model simple. */
@Singleton
class FriendRepository
@Inject
constructor(
        private val firestore: FirebaseFirestore,
        private val auth: FirebaseAuth,
        private val userProfileDao: UserProfileDao
) {
    fun getFriendList(): Flow<List<FriendProfile>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            close()
            return@callbackFlow
        }
        val ref = firestore.collection("friends").whereEqualTo("userId", uid)
        val listener =
                ref.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val friends =
                            snapshot?.documents?.mapNotNull { doc ->
                                doc.toObject(FriendProfile::class.java)?.takeIf { it.uid.isNotBlank() }
                            }
                                    ?: emptyList()
                    trySend(friends)
                }
        awaitClose { listener.remove() }
    }

    fun getIncomingFriendRequests(): Flow<List<FriendProfile>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val ref = firestore.collection("friend_requests").document(uid).collection("incoming")
            val listener =
                    ref.addSnapshotListener { snapshot, _ ->
                        trySend(
                                snapshot?.documents?.mapNotNull {
                                    it.toObject(FriendProfile::class.java)?.copy(uid = it.id)
                                }
                                        ?: emptyList()
                        )
                    }
            awaitClose { listener.remove() }
        }
    }

    fun getPendingSentFriendRequests(): Flow<List<FriendProfile>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val ref = firestore.collection("friend_requests").document(uid).collection("outgoing")
            val listener =
                    ref.addSnapshotListener { snapshot, _ ->
                        trySend(
                                snapshot?.documents?.mapNotNull {
                                    it.toObject(FriendProfile::class.java)?.copy(uid = it.id)
                                }
                                        ?: emptyList()
                        )
                    }
            awaitClose { listener.remove() }
        }
    }

    /** Result: (success, errorMessage). Errors are already user-friendly, safe to show directly. */
    suspend fun sendFriendRequest(rawCode: String): Pair<Boolean, String?> {
        val currentUser = auth.currentUser ?: return false to "Not logged in"
        val code = InputValidation.normalizeFriendCode(rawCode)
        if (code.isBlank()) return false to "Enter a friend code"

        val senderProfile = userProfileDao.getProfile() ?: return false to "Local profile not found"

        return try {
            val result = firestore.collection("users").whereEqualTo("friendCode", code).limit(1).get().await()
            if (result.isEmpty) return false to "User not found"

            val recipientUid = result.documents.first().id
            if (recipientUid == currentUser.uid) return false to "Cannot add yourself"

            val existingFriend = firestore.collection("friends").document("${currentUser.uid}_$recipientUid").get().await()
            if (existingFriend.exists()) return false to "Already friends"

            val existingOutgoing =
                    firestore.collection("friend_requests").document(currentUser.uid).collection("outgoing")
                            .document(recipientUid).get().await()
            if (existingOutgoing.exists()) return false to "Request already pending"

            val request =
                    mapOf(
                            "uid" to currentUser.uid,
                            "displayName" to senderProfile.nickname,
                            "profileImageUrl" to senderProfile.profileImageRemoteUrl
                    )
            val batch = firestore.batch()
            batch.set(
                    firestore.collection("friend_requests").document(recipientUid).collection("incoming").document(currentUser.uid),
                    request
            )
            batch.set(
                    firestore.collection("friend_requests").document(currentUser.uid).collection("outgoing").document(recipientUid),
                    request
            )
            batch.commit().await()
            true to null
        } catch (e: Exception) {
            false to (e.message ?: "Failed to send request")
        }
    }

    suspend fun acceptFriendRequest(fromUid: String): Result<String> {
        val currentUser = auth.currentUser ?: return Result.failure(IllegalStateException("Not logged in"))
        if (fromUid.isBlank()) return Result.failure(IllegalArgumentException("Missing sender"))

        return try {
            val senderDoc = firestore.collection("users").document(fromUid).get().await()
            val senderDisplayName = senderDoc.getString("displayName") ?: "Friend"
            val senderImage = senderDoc.getString("profileImageUrl")

            firestore.collection("friends").document("${currentUser.uid}_$fromUid").set(
                    mapOf(
                            "userId" to currentUser.uid,
                            "friendUid" to fromUid,
                            "displayName" to senderDisplayName,
                            "profileImageUrl" to senderImage
                    )
            ).await()

            val myProfile = userProfileDao.getProfile()
            if (myProfile != null) {
                firestore.collection("friends").document("${fromUid}_${currentUser.uid}").set(
                        mapOf(
                                "userId" to fromUid,
                                "friendUid" to currentUser.uid,
                                "displayName" to myProfile.nickname,
                                "profileImageUrl" to myProfile.profileImageRemoteUrl
                        )
                ).await()
            }

            firestore.collection("friend_requests").document(currentUser.uid).collection("incoming").document(fromUid)
                    .delete().await()

            Result.success(senderDisplayName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun declineFriendRequest(fromUid: String) {
        val currentUser = auth.currentUser ?: return
        firestore.collection("friend_requests").document(currentUser.uid).collection("incoming").document(fromUid).delete()
    }

    suspend fun removeFriend(myUid: String, friend: FriendProfile) {
        val batch = firestore.batch()
        batch.delete(firestore.collection("friends").document("${myUid}_${friend.uid}"))
        batch.delete(firestore.collection("friends").document("${friend.uid}_$myUid"))
        batch.delete(firestore.collection("friend_requests").document(myUid).collection("incoming").document(friend.uid))
        batch.delete(firestore.collection("friend_requests").document(myUid).collection("outgoing").document(friend.uid))
        batch.delete(firestore.collection("friend_requests").document(friend.uid).collection("incoming").document(myUid))
        batch.delete(firestore.collection("friend_requests").document(friend.uid).collection("outgoing").document(myUid))
        runCatching { batch.commit().await() }

        runCatching {
            val notifs =
                    firestore.collection("notifications").document(friend.uid).collection("items")
                            .whereEqualTo("from", myUid).whereIn("type", listOf("LIST_SHARED", "LIST_INVITE")).get().await()
            val cleanupBatch = firestore.batch()
            notifs.documents.forEach { cleanupBatch.delete(it.reference) }
            cleanupBatch.commit().await()
        }
    }
}
