package com.slygames.prioritizer.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * Friendly, UI-safe auth failure reasons. Never surface raw Firebase exception text to users
 * (spec: avoid exposing raw Firebase exception text) -- callers map a [Throwable] to one of these
 * via [fromThrowable] and show [message].
 */
enum class AuthError(val message: String) {
    INVALID_CREDENTIALS("Incorrect email or password."),
    USER_NOT_FOUND("No account found with that email."),
    ALREADY_EXISTS("An account with that email already exists."),
    WEAK_PASSWORD("Password must be at least 6 characters."),
    NETWORK_ERROR("No internet connection. Please try again."),
    TOO_MANY_REQUESTS("Too many attempts. Please wait a moment and try again."),
    INVALID_EMAIL("That email address doesn't look right."),
    REQUIRES_RECENT_LOGIN("For security, please log in again before continuing."),
    UNKNOWN("Something went wrong. Please try again.");

    companion object {
        fun fromThrowable(t: Throwable): AuthError =
                when (t) {
                    is FirebaseAuthInvalidCredentialsException -> INVALID_CREDENTIALS
                    is FirebaseAuthInvalidUserException -> USER_NOT_FOUND
                    is FirebaseAuthUserCollisionException -> ALREADY_EXISTS
                    is FirebaseAuthWeakPasswordException -> WEAK_PASSWORD
                    is FirebaseNetworkException -> NETWORK_ERROR
                    is FirebaseTooManyRequestsException -> TOO_MANY_REQUESTS
                    is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException ->
                            REQUIRES_RECENT_LOGIN
                    else -> UNKNOWN
                }
    }
}
