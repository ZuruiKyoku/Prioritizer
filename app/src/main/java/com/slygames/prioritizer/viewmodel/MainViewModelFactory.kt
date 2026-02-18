package com.slygames.prioritizer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.slygames.prioritizer.data.AppDatabase
import com.slygames.prioritizer.data.task.TaskRepository
import com.slygames.prioritizer.data.user.UserPreferences

class MainViewModelFactory(
    private val appContext: Context,
    private val taskRepository: TaskRepository,
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val prefs = UserPreferences(appContext)
        return MainViewModel(
            repository = taskRepository,
            db = database,
            prefs = prefs
        ) as T
    }
}