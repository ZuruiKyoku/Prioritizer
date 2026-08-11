package com.slygames.prioritizer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.slygames.prioritizer.data.auth.AuthRepository
import com.slygames.prioritizer.data.sync.SyncManager
import com.slygames.prioritizer.data.task.TaskRepository
import com.slygames.prioritizer.data.user.ProfileRepository
import com.slygames.prioritizer.data.user.UserPreferences
import com.slygames.prioritizer.data.user.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Profile screen: view/edit profile, and the two account-deletion flows (full account vs.
 * guest-local-only), including the re-authentication-required path Firebase enforces for
 * account deletion. */
@HiltViewModel
class ProfileViewModel
@Inject
constructor(
        private val profileRepository: ProfileRepository,
        private val authRepository: AuthRepository,
        private val syncManager: SyncManager,
        private val taskRepository: TaskRepository,
        private val userPreferences: UserPreferences,
        @ApplicationContext private val context: Context
) : ViewModel() {

    private fun signOutOfGoogleBestEffort() {
        runCatching {
            GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        }
    }

    val userProfile: StateFlow<UserProfile?> =
            profileRepository.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateProfile(nickname: String, imageBytes: ByteArray?) {
        viewModelScope.launch {
            profileRepository.updateProfile(nickname, imageBytes, authRepository.currentUserOrNull())
        }
    }

    fun saveFcmToken(token: String) {
        val uid = authRepository.currentUserOrNull()?.uid ?: return
        profileRepository.saveFcmToken(uid, token)
    }

    fun deleteAccount(deleteLocalData: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val user = authRepository.currentUserOrNull()
            if (user == null) {
                onError("No user signed in")
                return@launch
            }
            try {
                syncManager.deleteAccountData(user.uid)
                val authFailure = authRepository.deleteCurrentUser()
                if (authFailure != null) {
                    onError(authFailure.error.message)
                    return@launch
                }
                signOutOfGoogleBestEffort()

                if (deleteLocalData) {
                    profileRepository.clearLocalProfile()
                    taskRepository.clearAllLocalData()
                    userPreferences.clearForLogout()
                } else {
                    userPreferences.switchToGuestMode()
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error during deletion")
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            userPreferences.setLoggedIn(false)
            userPreferences.setLoginType("")
            authRepository.signOut()
            onDone()
        }
    }

    fun deleteGuestData(onSuccess: () -> Unit) {
        viewModelScope.launch {
            profileRepository.clearLocalProfile()
            taskRepository.clearAllLocalData()
            userPreferences.clearForLogout()
            signOutOfGoogleBestEffort()
            onSuccess()
        }
    }
}
