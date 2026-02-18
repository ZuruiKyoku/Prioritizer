package com.slygames.prioritizer

import android.app.Application
import androidx.room.Room
import com.slygames.prioritizer.data.AppDatabase
import com.slygames.prioritizer.data.task.TaskRepository
import com.slygames.prioritizer.data.user.UserPreferences
import com.slygames.prioritizer.viewmodel.MainViewModel

class PrioritizerApp : Application() {
    lateinit var userPrefs: UserPreferences
    lateinit var database: AppDatabase
    lateinit var mainViewModel: MainViewModel

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        userPrefs = UserPreferences(this)
        mainViewModel = MainViewModel(TaskRepository(database.taskDao()), database, prefs = userPrefs)
    }
}