package com.slygames.prioritizer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

/**
 * All framework/singleton wiring (Room, Firebase, repositories) now lives in Hilt modules under
 * `di/` -- this class no longer manually constructs a shared ViewModel or database instance (that
 * used to cause duplicate Firestore listeners whenever a screen built its own separate
 * MainViewModel instead of reusing this one).
 */
@HiltAndroidApp
class PrioritizerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    // AppFirebaseMessagingService posts every push to "shared_list_channel". On API 26+, posting
    // to a channel that was never registered is silently dropped by the OS -- the notification
    // never shows and no exception is thrown, so this went unnoticed until tested on a real
    // device. The channel must exist before any notification targets it, so create it here at
    // app startup rather than lazily in the messaging service.
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
                "shared_list_channel",
                "Shared List Updates",
                NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifications about shared lists: tasks, invites, and members" }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
