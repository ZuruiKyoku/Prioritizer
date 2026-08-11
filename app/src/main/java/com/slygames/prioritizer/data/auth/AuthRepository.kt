package com.slygames.prioritizer.data.auth

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Wraps FirebaseAuth. This is the only place in the app that talks to FirebaseAuth directly --
 * ViewModels never call it themselves (spec: repository layer separates data access from UI).
 *
 * Every suspend function here returns [AuthResult] instead of throwing, so callers get a
 * friendly [AuthError] instead of a raw Firebase exception.
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Failure(val error: AuthError) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(private val auth: FirebaseAuth) {

    /** Emits the current FirebaseUser (or null) immediately, then on every auth state change. */
    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUserOrNull(): FirebaseUser? = auth.currentUser

    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthResult.Failure(AuthError.UNKNOWN)
            AuthResult.Success(user)
        } catch (t: Throwable) {
            AuthResult.Failure(AuthError.fromThrowable(t))
        }
    }

    suspend fun registerWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthResult.Failure(AuthError.UNKNOWN)
            AuthResult.Success(user)
        } catch (t: Throwable) {
            AuthResult.Failure(AuthError.fromThrowable(t))
        }
    }

    suspend fun sendPasswordResetEmail(email: String): AuthResult.Failure? {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            null
        } catch (t: Throwable) {
            AuthResult.Failure(AuthError.fromThrowable(t))
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        val credential: AuthCredential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return AuthResult.Failure(AuthError.UNKNOWN)
            AuthResult.Success(user)
        } catch (t: Throwable) {
            AuthResult.Failure(AuthError.fromThrowable(t))
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun deleteCurrentUser(): AuthResult.Failure? {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.USER_NOT_FOUND)
        return try {
            user.delete().await()
            null
        } catch (t: Throwable) {
            AuthResult.Failure(AuthError.fromThrowable(t))
        }
    }
}
