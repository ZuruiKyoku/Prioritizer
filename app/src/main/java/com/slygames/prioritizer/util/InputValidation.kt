package com.slygames.prioritizer.util

/**
 * Centralized user-input validation (spec: validate all user input; trim whitespace; enforce
 * reasonable max lengths). Kept as small pure functions so they're trivially unit-testable and
 * reusable from any ViewModel/dialog without duplicating rules.
 */
object InputValidation {
    const val MAX_TASK_TITLE_LENGTH = 200
    const val MAX_TASK_DESCRIPTION_LENGTH = 2000
    const val MAX_LIST_NAME_LENGTH = 80
    const val FRIEND_CODE_LENGTH = 6

    sealed class Result {
        data class Valid(val value: String) : Result()
        data class Invalid(val reason: String) : Result()
    }

    fun validateTaskTitle(raw: String): Result {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> Result.Invalid("Task title can't be empty.")
            trimmed.length > MAX_TASK_TITLE_LENGTH ->
                    Result.Invalid("Task title must be $MAX_TASK_TITLE_LENGTH characters or fewer.")
            else -> Result.Valid(trimmed)
        }
    }

    fun validateTaskDescription(raw: String?): Result {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.length > MAX_TASK_DESCRIPTION_LENGTH) {
            Result.Invalid("Description must be $MAX_TASK_DESCRIPTION_LENGTH characters or fewer.")
        } else {
            Result.Valid(trimmed)
        }
    }

    fun validateListName(raw: String): Result {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> Result.Invalid("List name can't be empty.")
            trimmed.length > MAX_LIST_NAME_LENGTH ->
                    Result.Invalid("List name must be $MAX_LIST_NAME_LENGTH characters or fewer.")
            else -> Result.Valid(trimmed)
        }
    }

    /** Friend codes are case-insensitive (spec). Codes are stored/displayed in "AA-BB-CC" form
     * (see [com.slygames.prioritizer.util.generateFriendCode]) -- only trim/case-fold here, don't
     * strip the dashes, or normalized input would stop matching the dashed value in Firestore. */
    fun normalizeFriendCode(raw: String): String = raw.trim().uppercase()

    // A deliberately simple RFC-5322-ish check (not exhaustive) -- good enough to catch typos
    // without dragging in an Android-framework dependency, which would force every caller
    // (including plain JUnit tests) through Robolectric just to validate an email string.
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValidEmail(raw: String): Boolean = EMAIL_REGEX.matches(raw.trim())

    fun validatePassword(raw: String): Result {
        return if (raw.length < 6) {
            Result.Invalid("Password must be at least 6 characters.")
        } else {
            Result.Valid(raw)
        }
    }
}
