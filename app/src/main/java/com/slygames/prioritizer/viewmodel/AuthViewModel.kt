package com.slygames.prioritizer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slygames.prioritizer.data.auth.AuthError
import com.slygames.prioritizer.data.auth.AuthRepository
import com.slygames.prioritizer.data.auth.AuthResult
import com.slygames.prioritizer.data.sync.SyncManager
import com.slygames.prioritizer.data.user.ProfileRepository
import com.slygames.prioritizer.data.user.UserPreferences
import com.slygames.prioritizer.util.InputValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
    /** Sign-in/registration succeeded and the session is fully ready to show the main screen. */
    data object SignedIn : AuthUiState
    /** A reset email was sent; UI should show a confirmation, not navigate anywhere. */
    data object ResetEmailSent : AuthUiState
}

/** Handles Login / Register / Forgot Password / Google / Guest -- the only ViewModel that talks
 * to [AuthRepository]. Every successful sign-in path funnels through [finishSignIn] so profile
 * initialization and cloud restore always happen exactly once, regardless of which method was
 * used to authenticate (spec: profile init must be idempotent no matter the entry point). */
@HiltViewModel
class AuthViewModel
@Inject
constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val syncManager: SyncManager,
        private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    fun signInWithEmail(email: String, password: String) {
        if (!InputValidation.isValidEmail(email)) {
            _uiState.value = AuthUiState.Error(AuthError.INVALID_EMAIL.message)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.signInWithEmail(email, password)) {
                is AuthResult.Success -> {
                    userPreferences.setLoginType("email")
                    userPreferences.setLoggedIn(true)
                    finishSignIn(result.user.displayName)
                }
                is AuthResult.Failure -> _uiState.value = AuthUiState.Error(result.error.message)
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        if (!InputValidation.isValidEmail(email)) {
            _uiState.value = AuthUiState.Error(AuthError.INVALID_EMAIL.message)
            return
        }
        val passwordCheck = InputValidation.validatePassword(password)
        if (passwordCheck is InputValidation.Result.Invalid) {
            _uiState.value = AuthUiState.Error(passwordCheck.reason)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.registerWithEmail(email, password)) {
                is AuthResult.Success -> {
                    userPreferences.setLoginType("email")
                    userPreferences.setLoggedIn(true)
                    finishSignIn(displayName.ifBlank { null })
                }
                is AuthResult.Failure -> _uiState.value = AuthUiState.Error(result.error.message)
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (!InputValidation.isValidEmail(email)) {
            _uiState.value = AuthUiState.Error(AuthError.INVALID_EMAIL.message)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val failure = authRepository.sendPasswordResetEmail(email)
            _uiState.value =
                    if (failure == null) AuthUiState.ResetEmailSent
                    else AuthUiState.Error(failure.error.message)
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.signInWithGoogleIdToken(idToken)) {
                is AuthResult.Success -> {
                    userPreferences.setLoginType("google")
                    userPreferences.setLoggedIn(true)
                    finishSignIn(result.user.displayName)
                }
                is AuthResult.Failure -> _uiState.value = AuthUiState.Error(result.error.message)
            }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            userPreferences.setLoginType("guest")
            userPreferences.setLoggedIn(true)
            _uiState.value = AuthUiState.SignedIn
        }
    }

    /**
     * Re-entry path for a returning, already-authenticated user (splash screen). Returns the
     * launched [kotlinx.coroutines.Job] so the caller can `join()` it before deciding where to
     * navigate -- state observation alone races with this function's own first state change.
     */
    fun resumeExistingSession() =
            viewModelScope.launch {
                when (userPreferences.getLoginType()) {
                    "guest" -> _uiState.value = AuthUiState.SignedIn
                    "google", "email" -> {
                        if (authRepository.currentUserOrNull() != null) {
                            finishSignIn(null)
                        } else {
                            _uiState.value = AuthUiState.Idle
                        }
                    }
                    else -> _uiState.value = AuthUiState.Idle
                }
            }

    private suspend fun finishSignIn(displayNameHint: String?) {
        val user = authRepository.currentUserOrNull()
        if (user != null) {
            profileRepository.ensureProfileInitialized(user, displayNameHint)
            syncManager.restoreFromCloud { }.join()
            syncManager.ensureSharedMirrorsOnLaunch()
        }
        _uiState.value = AuthUiState.SignedIn
    }
}
