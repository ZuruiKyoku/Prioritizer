package com.slygames.prioritizer.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.slygames.prioritizer.MainActivity
import com.slygames.prioritizer.R

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]

        val intent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    when (type) {
                        "friend_request" -> {
                            action = "com.slygames.prioritizer.SHOW_FRIEND_REQUESTS"
                            putExtra("navigateTo", "friends")
                        }
                        // These all fire on an existing sharedLists/{listUuid} doc that the
                        // recipient is already a member of, so we can deep-link straight to it.
                        // (Cloud Functions send the list identifier as "listUuid" - see
                        // functions/index.js - not "listId".)
                        "TASK_ADDED",
                        "TASK_COMPLETED",
                        "TASK_MARKED_INCOMPLETE",
                        "TASK_DELETED",
                        "TASK_PRIORITY_CHANGED",
                        "MEMBER_JOINED",
                        "MEMBER_LEFT",
                        "CLEAR_COMPLETED" -> {
                            val listUuid = message.data["listUuid"]
                            if (!listUuid.isNullOrBlank()) {
                                action = "com.slygames.prioritizer.OPEN_SHARED_LIST"
                                putExtra("listUuid", listUuid)
                            }
                        }
                        // LIST_SHARED/LIST_INVITE point at an invite the user hasn't accepted
                        // yet (no local list to open), and LIST_DELETED's target no longer
                        // exists - for these, and any unrecognized type, just open the app
                        // normally rather than pretend to deep-link somewhere.
                        else -> {}
                    }
                }

        val pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        val title = message.notification?.title ?: "Notification"
        val body = message.notification?.body ?: ""

        val notification =
                NotificationCompat.Builder(this, "shared_list_channel")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

        if (Build.VERSION.SDK_INT < 33 ||
                        ActivityCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this)
                    .notify(System.currentTimeMillis().toInt(), notification)
        } else {
            Toast.makeText(
                            this,
                            "Notification permission not granted. Unable to show alert.",
                            Toast.LENGTH_SHORT
                    )
                    .show()
        }
    }
}
