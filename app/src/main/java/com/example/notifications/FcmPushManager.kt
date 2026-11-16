package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.model.FcmTopicHelper
import com.example.data.repository.MecCampusRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class FcmSyncState(
    val isSynced: Boolean = false,
    val isSyncing: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val fullToken: String = "",
    val tokenPreview: String = "",
    val subscribedTopics: List<String> = emptyList(),
    val statusMessage: String = "Initializing FCM push registration..."
)

object FcmPushManager {
    private const val TAG = "MecFcmPush"

    suspend fun syncPushSubscription(
        department: String,
        notificationsEnabled: Boolean,
        repository: MecCampusRepository
    ): FcmSyncState {
        val targetTopics = if (notificationsEnabled) {
            FcmTopicHelper.topicsForDepartment(department)
        } else {
            emptyList()
        }

        return try {
            val messaging = Firebase.messaging
            messaging.isAutoInitEnabled = true

            // Keep FCM topic subscriptions aligned with the student's current department & opt-in preference
            if (notificationsEnabled) {
                messaging.subscribeToTopic(FcmTopicHelper.TOPIC_ANNOUNCEMENTS).await()
                messaging.subscribeToTopic(FcmTopicHelper.TOPIC_EVENTS).await()
                val activeDeptTopic = FcmTopicHelper.departmentTopic(department)
                messaging.subscribeToTopic(activeDeptTopic).await()
                FcmTopicHelper.allDepartmentTopics()
                    .filter { it != activeDeptTopic }
                    .forEach { staleTopic ->
                        runCatching { messaging.unsubscribeFromTopic(staleTopic).await() }
                    }
            } else {
                messaging.unsubscribeFromTopic(FcmTopicHelper.TOPIC_ANNOUNCEMENTS).await()
                messaging.unsubscribeFromTopic(FcmTopicHelper.TOPIC_EVENTS).await()
                FcmTopicHelper.allDepartmentTopics().forEach { topic ->
                    runCatching { messaging.unsubscribeFromTopic(topic).await() }
                }
            }

            runCatching { messaging.register().await() }
            @Suppress("DEPRECATION")
            val token = messaging.token.await().orEmpty()
            if (token.length >= 10) {
                repository.upsertFcmToken(
                    token = token,
                    department = department,
                    topics = targetTopics,
                    enabled = notificationsEnabled
                )
            }

            val preview = if (token.length > 18) {
                "${token.take(10)}…${token.takeLast(6)}"
            } else {
                token
            }

            FcmSyncState(
                isSynced = token.isNotBlank(),
                isSyncing = false,
                notificationsEnabled = notificationsEnabled,
                fullToken = token,
                tokenPreview = preview,
                subscribedTopics = targetTopics,
                statusMessage = if (notificationsEnabled) {
                    "FCM active • Subscribed to ${targetTopics.size} campus topics"
                } else {
                    "FCM topics paused (notifications muted)"
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM registration/topic sync unavailable: ${e.message}")
            FcmSyncState(
                isSynced = false,
                isSyncing = false,
                notificationsEnabled = notificationsEnabled,
                fullToken = "",
                tokenPreview = "",
                subscribedTopics = targetTopics,
                statusMessage = "FCM device token unavailable on this device (${e.localizedMessage ?: "Play Services required"})"
            )
        }
    }

    suspend fun syncRefreshedToken(context: Context, newToken: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        if (newToken.trim().length < 10) return
        try {
            val repository = MecCampusRepository(context.applicationContext)
            val profile = repository.getProfile(currentUser.uid)
            val department = profile?.department ?: "CSE"
            val enabled = profile?.notificationsEnabled ?: true
            val topics = if (enabled) FcmTopicHelper.topicsForDepartment(department) else emptyList()
            repository.upsertFcmToken(
                token = newToken,
                department = department,
                topics = topics,
                enabled = enabled
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist refreshed FCM token: ${e.message}")
        }
    }
}
