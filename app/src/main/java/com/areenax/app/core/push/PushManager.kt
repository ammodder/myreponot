package com.areenax.app.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.areenax.app.core.network.Api
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.session.SessionManager
import com.areenax.app.data.PushRegisterRequest
import com.areenax.app.data.PushUnregisterRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PushManager {

    const val CHANNEL_ID = "areenax_push_channel"
    const val CHANNEL_NAME = "AREENAX Notifications"

    /** Brand accent tinted onto the monochrome small icon (LightPrimary #004AC6). */
    const val ACCENT_COLOR: Int = 0xFF004AC6.toInt()

    private const val TAG = "PushManager"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AREENAX push notifications for tournaments, wallet, and messages"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun syncFcmToken(api: Api, session: SessionManager) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (!token.isNullOrBlank()) {
                    Log.d("FCMService", "FCM_TOKEN: $token")
                    session.setFcmToken(token)
                    registerTokenWithBackend(api, session, token)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing FCM Token sync", t)
        }
    }

    fun registerTokenWithBackend(api: Api, session: SessionManager, token: String) {
        if (session.user.value == null) return
        CoroutineScope(Dispatchers.IO).launch {
            val req = PushRegisterRequest(
                token = token,
                platform = "ANDROID",
                deviceId = Build.MODEL ?: "Android Device"
            )
            safeCall { api.pushRegister(req) }
        }
    }

    fun unregisterTokenWithBackend(api: Api, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            safeCall { api.pushUnregister(PushUnregisterRequest(token = token)) }
        }
    }
}
