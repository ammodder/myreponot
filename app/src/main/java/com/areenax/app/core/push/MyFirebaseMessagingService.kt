package com.areenax.app.core.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.areenax.app.AreenaxApplication
import com.areenax.app.MainActivity
import com.areenax.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM Push Notification Receiver for AREENAX.
 *
 * UNIFIED PAYLOAD CONTRACT:
 * All FCM push payloads sent by the backend MUST be FCM Data Messages containing
 * the following key-value pairs in `remoteMessage.data`:
 *  - "title"    : String (Notification header, e.g. "Tournament Starting")
 *  - "body"     : String (Notification body text, e.g. "Your match is live now")
 *  - "deepLink" : String (Deep link URL formatted as "areenax://<screen>?<param>=<value>")
 *
 * Example JSON Payload:
 * {
 *   "data": {
 *     "title": "Tournament Joined",
 *     "body": "You have successfully joined the BGMI Solo match.",
 *     "deepLink": "areenax://tournament?id=cm12345"
 *   }
 * }
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM_TOKEN: $token")
        val app = applicationContext as? AreenaxApplication ?: return
        app.session.setFcmToken(token)
        PushManager.registerTokenWithBackend(app.api, app.session, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val title = remoteMessage.data["title"]
            ?: remoteMessage.notification?.title
            ?: getString(R.string.app_name)

        val body = remoteMessage.data["body"]
            ?: remoteMessage.notification?.body
            ?: ""

        val deepLink = remoteMessage.data["deepLink"]

        showNotification(title, body, deepLink)
    }

    private fun showNotification(title: String, body: String, deepLink: String?) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        PushManager.createNotificationChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (!deepLink.isNullOrBlank()) {
                putExtra(EXTRA_DEEP_LINK, deepLink)
            }
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, PushManager.CHANNEL_ID)
            // A5: monochrome alpha-mask vector (the coloured logo PNG rendered
            // as a solid white blob in the status bar on Android 5+).
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(PushManager.ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TAG = "FCMService"
        const val EXTRA_DEEP_LINK = "extra_deep_link"
    }
}
