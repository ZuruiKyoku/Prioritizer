package com.slygames.prioritizer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slygames.prioritizer.data.user.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Appearance/theme settings, backed by DataStore so preferences survive app restarts (spec:
 * theme must persist across launches, not live only in memory). */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val userPreferences: UserPreferences) : ViewModel() {

    val isDarkMode: Flow<Boolean> = userPreferences.isDarkModeFlow
    val isAutoSwitch: Flow<Boolean> = userPreferences.isAutoSwitch

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDarkMode(enabled) }
    }

    fun setAutoSwitch(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAutoSwitch(enabled) }
    }
}
