package com.example.notifications

data class PushNotificationPayload(
    val title: String,
    val body: String,
    val category: String,
    val department: String,
    val targetRoute: String,
    val notificationId: Int,
    val isEventUpdate: Boolean
)

object FcmPayloadParser {
    fun parse(
        notificationTitle: String?,
        notificationBody: String?,
        data: Map<String, String>,
        messageId: String? = null
    ): PushNotificationPayload {
        val rawType = data["type"]?.trim()?.lowercase().orEmpty()
        val rawCategory = data["category"]?.trim().orEmpty()
        val department = data["department"]?.trim()?.uppercase()?.ifBlank { "ALL" } ?: "ALL"

        val isEvent = rawType == "event" ||
            rawType == "event_update" ||
            rawCategory.equals("Event", ignoreCase = true) ||
            data.containsKey("eventId")

        val category = rawCategory.ifBlank {
            if (isEvent) "Event" else "Announcement"
        }

        val resolvedRoute = data["route"]?.trim()?.lowercase()?.takeIf {
            it in setOf("pulse", "chat", "events", "resources", "schedule", "profile")
        } ?: if (isEvent) "events" else "pulse"

        val title = notificationTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: data["title"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (isEvent) "MEC Campus Event Update" else "MEC Campus Announcement"

        val body = notificationBody?.trim()?.takeIf { it.isNotEmpty() }
            ?: data["body"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: data["message"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Tap to open MEC Connect for full campus details."

        val stableKey = messageId
            ?: data["alertId"]
            ?: data["eventId"]
            ?: "$category:$title:$body"

        return PushNotificationPayload(
            title = title,
            body = body,
            category = category,
            department = department,
            targetRoute = resolvedRoute,
            notificationId = stableKey.hashCode(),
            isEventUpdate = isEvent
        )
    }
}
