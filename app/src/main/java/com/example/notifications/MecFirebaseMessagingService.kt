package com.example.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MecFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onRegistered(token: String) {
        super.onRegistered(token)
        handleTokenUpdate(token)
    }

    @Deprecated("Deprecated in Java")
    override fun onNewToken(token: String) {
        @Suppress("DEPRECATION")
        super.onNewToken(token)
        handleTokenUpdate(token)
    }

    private fun handleTokenUpdate(token: String) {
        Log.d(TAG, "FCM registration token updated")
        serviceScope.launch {
            FcmPushManager.syncRefreshedToken(applicationContext, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val payload = FcmPayloadParser.parse(
            notificationTitle = remoteMessage.notification?.title,
            notificationBody = remoteMessage.notification?.body,
            data = remoteMessage.data,
            messageId = remoteMessage.messageId
        )

        CampusNotificationHelper.showPushNotification(
            context = applicationContext,
            payload = payload
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MecFcmService"
    }
}
