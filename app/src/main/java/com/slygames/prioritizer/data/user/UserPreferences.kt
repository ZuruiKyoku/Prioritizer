package com.slygames.prioritizer.data.user

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Shared DataStore instance
val Context.userPrefs by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        private val IS_AUTO_SWITCH = booleanPreferencesKey("is_auto_switch")
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val LOGIN_TYPE = stringPreferencesKey("login_type")
        private val LAST_SELECTED_LIST_ID = longPreferencesKey("last_selected_list_id")
        private val NICKNAME_KEY = stringPreferencesKey("nickname")
        private val PROFILE_IMAGE_URI_KEY = stringPreferencesKey("profile_image_uri")
    }

    // --- DARK MODE ---
    val isDarkModeFlow: Flow<Boolean> = context.userPrefs.data
        .map { it[IS_DARK_MODE] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        context.userPrefs.edit { it[IS_DARK_MODE] = enabled }
    }

    // --- Auto Switch ---
    val isAutoSwitch: Flow<Boolean> = context.userPrefs.data
        .map { it[IS_AUTO_SWITCH] ?: false }

    suspend fun setAutoSwitch(enabled: Boolean) {
        context.userPrefs.edit { it[IS_AUTO_SWITCH] = enabled }
    }

    // --- LOGIN ---
    val isLoggedInFlow: Flow<Boolean> = context.userPrefs.data
        .map { it[IS_LOGGED_IN] ?: false }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        context.userPrefs.edit { it[IS_LOGGED_IN] = loggedIn }
    }

    suspend fun setLoginType(type: String) {
        context.userPrefs.edit { it[LOGIN_TYPE] = type }
    }

    suspend fun getLoginType(): String? {
        return context.userPrefs.data.map { it[LOGIN_TYPE] }.first()
    }

    suspend fun isLoggedIn(): Boolean {
        return getLoginType() != null
    }

    // UserPreferences.kt
    val loginTypeFlow: Flow<String?> = context.userPrefs.data
        .map { it[LOGIN_TYPE] }

    // --- LAST SELECTED LIST ---
    val lastSelectedListIdFlow: Flow<Long?> = context.userPrefs.data
        .map { it[LAST_SELECTED_LIST_ID] }

    suspend fun setLastSelectedListId(id: Long) {
        context.userPrefs.edit { it[LAST_SELECTED_LIST_ID] = id }
    }

    val nicknameFlow: Flow<String> = context.userPrefs.data
        .map { it[NICKNAME_KEY] ?: "User" }

    suspend fun setNickname(nickname: String) {
        context.userPrefs.edit { it[NICKNAME_KEY] = nickname }
    }

    val profileImageUriFlow: Flow<String?> = context.userPrefs.data
        .map { it[PROFILE_IMAGE_URI_KEY] }

    suspend fun setProfileImageUri(uri: String) {
        context.userPrefs.edit { it[PROFILE_IMAGE_URI_KEY] = uri }
    }
}