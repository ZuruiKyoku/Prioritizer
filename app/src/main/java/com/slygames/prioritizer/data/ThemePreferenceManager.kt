package com.slygames.prioritizer.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

private val Context.dataStore by preferencesDataStore(name = "settings")

class ThemePreferenceManager(private val context: Context) {
    companion object {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    }

    val isDarkModeFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[IS_DARK_MODE] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_DARK_MODE] = enabled
        }
    }

    val LAST_SELECTED_LIST_ID = longPreferencesKey("last_selected_list_id")

    val lastSelectedListIdFlow: Flow<Long?> = context.dataStore.data
        .map { it[LAST_SELECTED_LIST_ID] }

    suspend fun setLastSelectedListId(id: Long) {
        context.dataStore.edit { it[LAST_SELECTED_LIST_ID] = id }
    }

}