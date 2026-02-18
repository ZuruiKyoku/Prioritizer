// com.slygames.prioritizer.data.UserPreferences.kt
package com.slygames.prioritizer.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.userPrefs by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    private val LOGIN_TYPE = stringPreferencesKey("login_type")

    suspend fun setLoginType(type: String) {
        context.userPrefs.edit { it[LOGIN_TYPE] = type }
    }

    suspend fun getLoginType(): String? {
        return context.userPrefs.data.map { it[LOGIN_TYPE] }.first()
    }

    suspend fun isLoggedIn(): Boolean {
        return getLoginType() != null
    }
}