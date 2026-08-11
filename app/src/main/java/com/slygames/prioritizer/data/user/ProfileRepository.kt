package com.slygames.prioritizer.data.user

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.slygames.prioritizer.util.deleteOldProfileImage
import com.slygames.prioritizer.util.generateFriendCode
import com.slygames.prioritizer.util.saveProfileImageToInternalStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Owns the user's profile: the local Room copy (fast, offline-first) and the Firestore
 * `users/{uid}` document (source of truth once online). Also owns friend-code minting, which must
 * be globally unique -- see [ensureProfileInitialized].
 */
@Singleton
class ProfileRepository
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val userProfileDao: UserProfileDao,
        private val firestore: FirebaseFirestore,
        private val storage: FirebaseStorage
) {
    fun observeProfile(): Flow<UserProfile?> = userProfileDao.observeProfile()

    suspend fun getLocalProfile(): UserProfile? = userProfileDao.getProfile()

    /**
     * Idempotent first-run initialization (spec: profile init must not create duplicate
     * profiles/friend codes on repeated launches). Safe to call every time a user signs in --
     * it's a no-op if `users/{uid}` already exists.
     */
    suspend fun ensureProfileInitialized(user: FirebaseUser, displayNameHint: String? = null) {
        val userDocRef = firestore.collection("users").document(user.uid)
        val existing = userDocRef.get().await()

        if (existing.exists()) {
            // Make sure the local copy matches what's already in the cloud.
            val friendCode = existing.getString("friendCode") ?: mintUniqueFriendCode()
            val displayName = existing.getString("displayName") ?: (displayNameHint ?: "User")
            val local = userProfileDao.getProfile()
            userProfileDao.saveProfile(
                    UserProfile(
                            id = 0,
                            nickname = displayName,
                            profileImagePath = local?.profileImagePath,
                            profileImageRemoteUrl = existing.getString("profileImageUrl"),
                            friendCode = friendCode
                    )
            )
            if (existing.getString("friendCode") == null) {
                userDocRef.set(mapOf("friendCode" to friendCode), SetOptions.merge()).await()
            }
            return
        }

        // Brand new account: mint a unique friend code and create the profile doc + local row.
        val friendCode = mintUniqueFriendCode()
        val displayName = displayNameHint?.takeIf { it.isNotBlank() } ?: (user.email?.substringBefore("@") ?: "User")

        userDocRef.set(
                        mapOf(
                                "uid" to user.uid,
                                "displayName" to displayName,
                                "email" to user.email,
                                "friendCode" to friendCode,
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                )
                .await()

        userProfileDao.saveProfile(
                UserProfile(
                        id = 0,
                        nickname = displayName,
                        profileImagePath = null,
                        profileImageRemoteUrl = null,
                        friendCode = friendCode
                )
        )
    }

    /** Generates a candidate friend code and retries on collision (spec: check uniqueness). */
    private suspend fun mintUniqueFriendCode(maxAttempts: Int = 8): String {
        repeat(maxAttempts) {
            val candidate = generateFriendCode()
            val clash =
                    firestore
                            .collection("users")
                            .whereEqualTo("friendCode", candidate)
                            .limit(1)
                            .get()
                            .await()
            if (clash.isEmpty) return candidate
        }
        // Astronomically unlikely, but never loop forever -- fall back to a longer code.
        return generateFriendCode() + generateFriendCode().takeLast(2)
    }

    suspend fun updateProfile(nickname: String, imageBytes: ByteArray?, currentUser: FirebaseUser?) {
        val oldProfile = userProfileDao.getProfile()
        var firebaseImageUrl: String? = oldProfile?.profileImageRemoteUrl
        var localPath: String? = oldProfile?.profileImagePath

        if (imageBytes != null) {
            if (currentUser != null && firebaseImageUrl?.contains("firebase") == true) {
                runCatching { storage.getReferenceFromUrl(firebaseImageUrl!!).delete() }
            }

            localPath = saveProfileImageToInternalStorage(context, imageBytes)

            if (currentUser != null) {
                // Keep the existing "profile_images/profile_{uid}_{timestamp}.jpg" layout rather
                // than a freshly scoped "users/{uid}/profile/avatar.jpg" path: this project's
                // Storage security rules aren't checked into this repo, and they may already
                // allowlist this exact pattern. Switching paths blind could silently break
                // uploads for existing users until the rules are reviewed against it.
                val filename = "profile_${currentUser.uid}_${System.currentTimeMillis()}.jpg"
                val ref = storage.reference.child("profile_images/$filename")
                firebaseImageUrl =
                        runCatching {
                                    ref.putBytes(imageBytes)
                                            .continueWithTask { task ->
                                                if (!task.isSuccessful) {
                                                    throw task.exception ?: Exception("Upload failed")
                                                }
                                                ref.downloadUrl
                                            }
                                            .await()
                                            .toString()
                                }
                                .getOrNull()
            } else {
                firebaseImageUrl = null
            }
        } else {
            deleteOldProfileImage(localPath)
            // Delete via the stored download URL rather than guessing a path -- the upload path
            // includes a timestamp, so there's no fixed filename to reconstruct here.
            if (currentUser != null && firebaseImageUrl?.contains("firebase") == true) {
                runCatching { storage.getReferenceFromUrl(firebaseImageUrl!!).delete() }
            }
            localPath = null
            firebaseImageUrl = null
        }

        val updated =
                UserProfile(
                        id = 0,
                        nickname = nickname,
                        profileImagePath = localPath,
                        profileImageRemoteUrl = firebaseImageUrl,
                        friendCode = oldProfile?.friendCode ?: generateFriendCode()
                )
        userProfileDao.saveProfile(updated)

        if (currentUser != null) {
            firestore.collection("users")
                    .document(currentUser.uid)
                    .set(
                            mapOf(
                                    "displayName" to nickname,
                                    "profileImageUrl" to firebaseImageUrl,
                                    "friendCode" to updated.friendCode,
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                    )
        }
    }

    suspend fun restoreProfileFromCloud(uid: String) {
        val snapshot =
                firestore.collection("users")
                        .document(uid)
                        .get(com.google.firebase.firestore.Source.SERVER)
                        .await()
        if (!snapshot.exists()) return

        val nickname = snapshot.getString("displayName") ?: "User"
        val friendCode = snapshot.getString("friendCode") ?: mintUniqueFriendCode()
        val profileImageUrl = snapshot.getString("profileImageUrl")

        var profile =
                UserProfile(
                        id = 0,
                        nickname = nickname,
                        friendCode = friendCode,
                        profileImageRemoteUrl = profileImageUrl,
                        profileImagePath = null
                )

        if (!profileImageUrl.isNullOrBlank()) {
            runCatching {
                val bytes =
                        withContext(Dispatchers.IO) {
                            URL(profileImageUrl).openStream().use { it.readBytes() }
                        }
                profile = profile.copy(profileImagePath = saveProfileImageToInternalStorage(context, bytes))
            }
        }

        userProfileDao.saveProfile(profile)
    }

    /** Saves this device's FCM token to the user's profile doc so Cloud Functions can push to it. */
    fun saveFcmToken(uid: String, token: String) {
        firestore.collection("users").document(uid).update("fcmToken", token)
    }

    suspend fun clearLocalProfile() {
        val current = userProfileDao.getProfile()
        deleteOldProfileImage(current?.profileImagePath)
        userProfileDao.clearProfile()
    }

    suspend fun deleteRemoteProfile(uid: String) {
        val userDocRef = firestore.collection("users").document(uid)
        val snapshot = runCatching { userDocRef.get().await() }.getOrNull()
        val imageUrl = snapshot?.getString("profileImageUrl")
        if (!imageUrl.isNullOrBlank() && imageUrl.contains("firebase")) {
            runCatching { storage.getReferenceFromUrl(imageUrl).delete().await() }
        }
        runCatching { userDocRef.delete().await() }
    }
}
