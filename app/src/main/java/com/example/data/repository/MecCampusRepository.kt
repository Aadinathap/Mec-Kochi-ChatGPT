package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.CampusAlert
import com.example.data.model.CampusEvent
import com.example.data.model.ChatMessage
import com.example.data.model.ChatTypingStatus
import com.example.data.model.DepartmentResource
import com.example.data.model.EventRsvp
import com.example.data.model.FcmDeviceToken
import com.example.data.model.FcmTopicHelper
import com.example.data.model.MecChannels
import com.example.data.model.ScheduleSlot
import com.example.data.model.StudentProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MecCampusRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.logFirestoreErrors(
        operationType: OperationType,
        path: String,
        expectedUid: String? = auth.currentUser?.uid
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
        throw error
    }

    // --- Student Profile ---

    fun observeProfile(userId: String): Flow<StudentProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .logFirestoreErrors(OperationType.GET, "users/$userId", userId)

    suspend fun getProfile(userId: String): StudentProfile? {
        val docRef = db.collection("users").document(userId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            throw e
        }
    }

    suspend fun saveProfile(
        displayName: String,
        department: String,
        semester: String,
        notificationsEnabled: Boolean,
        darkThemeEnabled: Boolean
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mapOf(
                    "displayName" to displayName.trim().take(60),
                    "department" to department,
                    "semester" to semester,
                    "notificationsEnabled" to notificationsEnabled,
                    "darkThemeEnabled" to darkThemeEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.trim().take(60),
                    "department" to department,
                    "semester" to semester,
                    "notificationsEnabled" to notificationsEnabled,
                    "darkThemeEnabled" to darkThemeEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    // --- Real-time Campus Chat ---

    fun observeChatMessages(): Flow<List<ChatMessage>> =
        db.collection("chat_messages")
            .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(OperationType.LIST, "chat_messages")

    suspend fun sendChatMessage(
        channelId: String,
        senderName: String,
        senderDepartment: String,
        senderSemester: String,
        content: String,
        tag: String? = null,
        imageUrl: String? = null
    ): String {
        val uid = requireUserId()
        val cleanContent = content.trim().take(1000)
        val cleanImageUrl = imageUrl?.trim()?.takeIf { it.isNotBlank() }?.take(1200)
        require(cleanContent.isNotEmpty() || cleanImageUrl != null) {
            "Chat message must include text content or an image URL"
        }
        val docRef = db.collection("chat_messages").document()
        val payload = buildMap<String, Any> {
            put("campusId", MecChannels.CAMPUS_ID)
            put("channelId", channelId)
            put("senderId", uid)
            put("senderName", senderName.trim().ifBlank { "MEC Student" }.take(60))
            put("senderDepartment", senderDepartment)
            put("senderSemester", senderSemester)
            put("content", cleanContent)
            if (!tag.isNullOrBlank()) {
                put("tag", tag)
            }
            if (cleanImageUrl != null) {
                put("imageUrl", cleanImageUrl)
            }
            put("readReceipts", emptyMap<String, String>())
            put("createdAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun markChatMessagesAsRead(messageIds: List<String>, readerName: String) {
        val uid = requireUserId()
        val cleanIds = messageIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(50)
        if (cleanIds.isEmpty()) return
        val cleanReader = readerName.trim().ifBlank { "MEC Student" }.take(60)
        val batch = db.batch()
        cleanIds.forEach { id ->
            val docRef = db.collection("chat_messages").document(id)
            batch.update(docRef, "readReceipts.$uid", cleanReader)
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_messages", auth)
            throw e
        }
    }

    fun observeChatTypingStatuses(): Flow<List<ChatTypingStatus>> =
        db.collection("chat_typing")
            .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatTypingStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .logFirestoreErrors(OperationType.LIST, "chat_typing")

    suspend fun setTypingStatus(
        channelId: String,
        displayName: String,
        department: String,
        isTyping: Boolean
    ) {
        val uid = requireUserId()
        val cleanChannel = channelId.trim().ifBlank { "general" }
        val docId = "${cleanChannel}_$uid"
        val docRef = db.collection("chat_typing").document(docId)
        val payload = mapOf(
            "campusId" to MecChannels.CAMPUS_ID,
            "channelId" to cleanChannel,
            "userId" to uid,
            "displayName" to displayName.trim().ifBlank { "MEC Student" }.take(60),
            "department" to department,
            "isTyping" to isTyping,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteChatMessage(messageId: String) {
        requireUserId()
        val docRef = db.collection("chat_messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    // --- Campus Events & RSVPs ---

    fun observeCampusEvents(): Flow<List<CampusEvent>> =
        db.collection("campus_events")
            .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CampusEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(OperationType.LIST, "campus_events")

    fun observeUserRsvps(userId: String): Flow<List<EventRsvp>> =
        db.collection("users").document(userId).collection("event_rsvps")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(EventRsvp::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .logFirestoreErrors(OperationType.LIST, "users/$userId/event_rsvps", userId)

    suspend fun createCampusEvent(
        title: String,
        organizer: String,
        category: String,
        venue: String,
        dateLabel: String,
        timeLabel: String,
        description: String,
        department: String
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("campus_events").document()
        val payload = mapOf(
            "campusId" to MecChannels.CAMPUS_ID,
            "title" to title.trim().take(120),
            "organizer" to organizer.trim().take(80),
            "category" to category,
            "venue" to venue.trim().take(100),
            "dateLabel" to dateLabel.trim().take(40),
            "timeLabel" to timeLabel.trim().take(40),
            "description" to description.trim().take(1000),
            "department" to department,
            "creatorId" to uid,
            "rsvpCount" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            val rsvpRef = db.collection("users").document(uid).collection("event_rsvps").document(docRef.id)
            rsvpRef.set(
                mapOf(
                    "userId" to uid,
                    "eventId" to docRef.id,
                    "eventTitle" to title.trim().take(120),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun toggleEventRsvp(event: CampusEvent, currentlyRsvped: Boolean) {
        val uid = requireUserId()
        require(event.id.isNotBlank()) { "Event ID must not be blank" }
        val eventRef = db.collection("campus_events").document(event.id)
        val rsvpRef = db.collection("users").document(uid).collection("event_rsvps").document(event.id)
        try {
            val delta = if (currentlyRsvped) -1L else 1L
            val nextCount = (event.rsvpCount + delta).coerceAtLeast(0L)
            if (nextCount != event.rsvpCount) {
                eventRef.update(
                    mapOf(
                        "rsvpCount" to nextCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            if (currentlyRsvped) {
                rsvpRef.delete().await()
            } else {
                rsvpRef.set(
                    mapOf(
                        "userId" to uid,
                        "eventId" to event.id,
                        "eventTitle" to event.title.take(120),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, eventRef.path, auth)
            throw e
        }
    }

    // --- Department Resources ---

    fun observeDepartmentResources(): Flow<List<DepartmentResource>> =
        db.collection("department_resources")
            .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(DepartmentResource::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.upvotes }
            }
            .logFirestoreErrors(OperationType.LIST, "department_resources")

    suspend fun addDepartmentResource(
        title: String,
        department: String,
        semester: String,
        subjectCode: String,
        resourceType: String,
        description: String,
        linkOrReference: String,
        uploaderName: String
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("department_resources").document()
        val payload = mapOf(
            "campusId" to MecChannels.CAMPUS_ID,
            "title" to title.trim().take(120),
            "department" to department,
            "semester" to semester,
            "subjectCode" to subjectCode.trim().uppercase().take(20),
            "resourceType" to resourceType,
            "description" to description.trim().take(800),
            "linkOrReference" to linkOrReference.trim().take(300),
            "uploaderId" to uid,
            "uploaderName" to uploaderName.trim().ifBlank { "MEC Student" }.take(60),
            "upvotes" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun upvoteResource(resource: DepartmentResource) {
        requireUserId()
        require(resource.id.isNotBlank()) { "Resource ID must not be blank" }
        val docRef = db.collection("department_resources").document(resource.id)
        try {
            docRef.update(
                mapOf(
                    "upvotes" to (resource.upvotes + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    // --- Academic Schedule ---

    fun observeAcademicSchedule(userId: String): Flow<List<ScheduleSlot>> =
        db.collection("users").document(userId).collection("academic_schedule")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ScheduleSlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<ScheduleSlot> {
                            val idx = MecChannels.daysOfWeek.indexOf(it.dayOfWeek)
                            if (idx == -1) 99 else idx
                        }.thenBy { MecChannels.parseTimeToMinutes(it.startTime) }
                    )
            }
            .logFirestoreErrors(OperationType.LIST, "users/$userId/academic_schedule", userId)

    suspend fun addScheduleSlot(
        courseCode: String,
        courseTitle: String,
        facultyName: String,
        room: String,
        dayOfWeek: String,
        startTime: String,
        endTime: String,
        slotType: String,
        reminderEnabled: Boolean
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("academic_schedule").document()
        val payload = mapOf(
            "userId" to uid,
            "courseCode" to courseCode.trim().uppercase().take(20),
            "courseTitle" to courseTitle.trim().take(100),
            "facultyName" to facultyName.trim().take(80),
            "room" to room.trim().take(40),
            "dayOfWeek" to dayOfWeek,
            "startTime" to startTime.trim().take(20),
            "endTime" to endTime.trim().take(20),
            "slotType" to slotType,
            "reminderEnabled" to reminderEnabled,
            "isCompleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateScheduleSlot(slot: ScheduleSlot) {
        val uid = requireUserId()
        require(slot.id.isNotBlank()) { "Slot ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("academic_schedule").document(slot.id)
        try {
            docRef.update(
                mapOf(
                    "reminderEnabled" to slot.reminderEnabled,
                    "isCompleted" to slot.isCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteScheduleSlot(slotId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("academic_schedule").document(slotId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    // --- Real-time Campus Alerts / Notifications ---

    fun observeCampusAlerts(): Flow<List<CampusAlert>> =
        db.collection("campus_alerts")
            .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CampusAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .logFirestoreErrors(OperationType.LIST, "campus_alerts")

    suspend fun postCampusAlert(
        title: String,
        message: String,
        category: String,
        department: String,
        senderName: String
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("campus_alerts").document()
        val payload = mapOf(
            "campusId" to MecChannels.CAMPUS_ID,
            "title" to title.trim().take(120),
            "message" to message.trim().take(600),
            "category" to category,
            "department" to department,
            "senderId" to uid,
            "senderName" to senderName.trim().ifBlank { "MEC Student" }.take(60),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    // --- FCM Device Tokens ---

    fun observeFcmTokens(userId: String): Flow<List<FcmDeviceToken>> =
        db.collection("users").document(userId).collection("fcm_tokens")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FcmDeviceToken::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .logFirestoreErrors(OperationType.LIST, "users/$userId/fcm_tokens", userId)

    suspend fun upsertFcmToken(
        token: String,
        department: String,
        topics: List<String>,
        enabled: Boolean
    ): String {
        val uid = requireUserId()
        val cleanToken = token.trim()
        require(cleanToken.length >= 10) { "FCM token must be at least 10 characters" }
        val docId = FcmTopicHelper.tokenDocId(cleanToken)
        val docRef = db.collection("users").document(uid).collection("fcm_tokens").document(docId)
        try {
            val existing = docRef.get().await()
            val boundedTopics = topics.take(10).map { it.take(64) }
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "token" to cleanToken.take(512),
                        "department" to department,
                        "topics" to boundedTopics,
                        "enabled" to enabled,
                        "platform" to "android",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "token" to cleanToken.take(512),
                        "department" to department,
                        "topics" to boundedTopics,
                        "enabled" to enabled,
                        "platform" to "android",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    // --- Authentic MEC Kochi Initial Seeding (Only if collections are empty) ---

    suspend fun seedCampusIfEmpty(senderName: String, department: String, semester: String) {
        val uid = requireUserId()
        try {
            val eventsSnap = db.collection("campus_events")
                .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
                .limit(1)
                .get()
                .await()
            if (eventsSnap.isEmpty) {
                val starterEvents = listOf(
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "Excel 2026 • MEC Annual Tech Fest",
                        "organizer" to "Excel MEC Core Team",
                        "category" to "Tech Fest",
                        "venue" to "MEC Main Auditorium & Amphitheatre",
                        "dateLabel" to "Nov 28–30, 2026",
                        "timeLabel" to "09:00 AM",
                        "description" to "South India's flagship student-run techno-managerial fest at Govt. Model Engineering College, Thrikkakara. Featuring HackForKerala, RoboWars, and tech talks.",
                        "department" to "ALL",
                        "creatorId" to uid,
                        "rsvpCount" to 1L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "FOSSMEC Linux & Open Source Sprint",
                        "organizer" to "FOSSMEC",
                        "category" to "Workshop",
                        "venue" to "SDPK Lab, Main Block",
                        "dateLabel" to "Nov 19, 2026",
                        "timeLabel" to "04:15 PM",
                        "description" to "Hands-on Git workflows, kernel basics, and contributing to open-source Kerala tech projects with senior MEC maintainers.",
                        "department" to "CSE",
                        "creatorId" to uid,
                        "rsvpCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "IEEE MEC Hardware & Embedded Hack",
                        "organizer" to "IEEE Student Branch MEC",
                        "category" to "Hackathon",
                        "venue" to "ECE Project Lab • Block B",
                        "dateLabel" to "Nov 23, 2026",
                        "timeLabel" to "10:00 AM",
                        "description" to "Build ESP32, FPGA, and IoT prototypes over a 12-hour sprint judged by MEC alumni engineers.",
                        "department" to "ECE",
                        "creatorId" to uid,
                        "rsvpCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "MEC Placement Readiness & Mock DSA Drive",
                        "organizer" to "Training & Placement Cell",
                        "category" to "Placement",
                        "venue" to "Placement Hall, Ground Floor",
                        "dateLabel" to "Nov 21, 2026",
                        "timeLabel" to "01:30 PM",
                        "description" to "Resume clinics and timed coding rounds curated for S5–S7 students preparing for Tier-1 Kochi & Bengaluru recruiters.",
                        "department" to "ALL",
                        "creatorId" to uid,
                        "rsvpCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                starterEvents.forEach { db.collection("campus_events").document().set(it).await() }
            }

            val resourcesSnap = db.collection("department_resources")
                .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
                .limit(1)
                .get()
                .await()
            if (resourcesSnap.isEmpty) {
                val starterResources = listOf(
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "CST301 Automata & Formal Languages PDF Pack",
                        "department" to "CSE",
                        "semester" to "S5",
                        "subjectCode" to "CST301",
                        "resourceType" to "PDF",
                        "description" to "Complete module 1–5 PDF notes covering DFA/NFA minimization, CFG, PDA, and Turing Machines with solved KTU problems.",
                        "linkOrReference" to "https://ktu.edu.in/cs-cst301-notes.pdf",
                        "uploaderId" to uid,
                        "uploaderName" to senderName,
                        "upvotes" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "FOSSMEC & CSE GitHub Course Archive",
                        "department" to "CSE",
                        "semester" to "S5",
                        "subjectCode" to "CST303",
                        "resourceType" to "Link",
                        "description" to "Curated open-source Computer Networks lab programs, socket code, and KTU syllabus links.",
                        "linkOrReference" to "https://github.com/fossmec",
                        "uploaderId" to uid,
                        "uploaderName" to senderName,
                        "upvotes" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "ECT301 Linear Integrated Circuits Lab Manual",
                        "department" to "ECE",
                        "semester" to "S5",
                        "subjectCode" to "ECT301",
                        "resourceType" to "Lab Manual",
                        "description" to "Circuit schematics, Op-Amp wave shaping readings, and viva questions for MEC ECE Analog Lab.",
                        "linkOrReference" to "ECE Block B Lab Desk • Manual v4",
                        "uploaderId" to uid,
                        "uploaderName" to senderName,
                        "upvotes" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "EET303 Microprocessors & Embedded Systems PYQs",
                        "department" to "EEE",
                        "semester" to "S5",
                        "subjectCode" to "EET303",
                        "resourceType" to "PYQ",
                        "description" to "Last 5 years KTU university question papers PDF with marking scheme hints and 8086/ARM timing diagrams.",
                        "linkOrReference" to "https://mec.ac.in/library/eee-pyq.pdf",
                        "uploaderId" to uid,
                        "uploaderName" to senderName,
                        "upvotes" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "BMT302 Biomedical Signal Processing Slides",
                        "department" to "BME",
                        "semester" to "S5",
                        "subjectCode" to "BMT302",
                        "resourceType" to "Slides",
                        "description" to "ECG/EEG filtering, FFT spectral analysis, and adaptive noise cancellation decks.",
                        "linkOrReference" to "BME Dept Seminar Hall • Deck 2026",
                        "uploaderId" to uid,
                        "uploaderName" to senderName,
                        "upvotes" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "MET301 Mechanics of Machinery Formula PDF",
                        "department" to "ME",
                        "semester" to "S5",
                        "subjectCode" to "MET301",
                        "resourceType" to "PDF",
                        "description" to "Kinematic linkages, gear trains, cam profiles, and flywheel design equations for Series Exam prep.",
                        "linkOrReference" to "Mechanical Workshop • Study Pack PDF",
                        "uploaderId" to uid,
                        "uploaderName" to senderName,
                        "upvotes" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                starterResources.forEach { db.collection("department_resources").document().set(it).await() }
            }

            val alertsSnap = db.collection("campus_alerts")
                .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
                .limit(1)
                .get()
                .await()
            if (alertsSnap.isEmpty) {
                val starterAlerts = listOf(
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "KTU Odd Semester Series Exam Schedule Live",
                        "message" to "First internal series exams start next Monday at 09:30 AM. Check room allocations in your Schedule tab.",
                        "category" to "Exam",
                        "department" to "ALL",
                        "senderId" to uid,
                        "senderName" to "MEC Academic Cell",
                        "createdAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "title" to "Excel 2026 Core Registrations Open",
                        "message" to "Team leads and volunteers for Excel MEC 2026 can RSVP directly from the Events hub.",
                        "category" to "Event",
                        "department" to "ALL",
                        "senderId" to uid,
                        "senderName" to "Excel MEC",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                starterAlerts.forEach { db.collection("campus_alerts").document().set(it).await() }
            }

            val chatSnap = db.collection("chat_messages")
                .whereEqualTo("campusId", MecChannels.CAMPUS_ID)
                .limit(1)
                .get()
                .await()
            if (chatSnap.isEmpty) {
                val starterChat = listOf(
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "channelId" to "general",
                        "senderId" to uid,
                        "senderName" to senderName,
                        "senderDepartment" to department,
                        "senderSemester" to semester,
                        "content" to "Welcome to MEC Connect! Coordinate labs, Excel fest updates, and KTU prep across Thrikkakara campus here.",
                        "tag" to "Announcement",
                        "createdAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "campusId" to MecChannels.CAMPUS_ID,
                        "channelId" to "excel_fest",
                        "senderId" to uid,
                        "senderName" to senderName,
                        "senderDepartment" to department,
                        "senderSemester" to semester,
                        "content" to "Excited for Excel 2026! Who is teaming up for the 24-hour hackathon?",
                        "tag" to "Discussion",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                starterChat.forEach { db.collection("chat_messages").document().set(it).await() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "seedCampusIfEmpty", auth)
        }
    }

    suspend fun seedPersonalScheduleIfEmpty(department: String) {
        val uid = requireUserId()
        val schedRef = db.collection("users").document(uid).collection("academic_schedule")
        try {
            if (!schedRef.limit(1).get().await().isEmpty) return
            val slots = when (department) {
                "ECE" -> listOf(
                    ScheduleSlot(courseCode = "ECT301", courseTitle = "Linear Integrated Circuits", facultyName = "Prof. Asha Menon", room = "EC-204", dayOfWeek = "MON", startTime = "09:30 AM", endTime = "10:25 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "ECT305", courseTitle = "Control Systems", facultyName = "Prof. Bindu V", room = "EC-202", dayOfWeek = "MON", startTime = "11:30 AM", endTime = "12:25 PM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "ECT303", courseTitle = "Digital Signal Processing", facultyName = "Dr. Rajesh K", room = "EC-201", dayOfWeek = "TUE", startTime = "10:35 AM", endTime = "11:30 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "ECL331", courseTitle = "Analog Integrated Circuits Lab", facultyName = "ECE Lab Faculty", room = "Block B Lab 2", dayOfWeek = "WED", startTime = "01:30 PM", endTime = "04:00 PM", slotType = "Lab"),
                    ScheduleSlot(courseCode = "ECT307", courseTitle = "Microcontrollers Tutorial", facultyName = "Prof. Sreelatha T", room = "EC-203", dayOfWeek = "THU", startTime = "09:30 AM", endTime = "10:25 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "ECT303", courseTitle = "DSP Filter Design Assignment", facultyName = "Dr. Rajesh K", room = "MEC Moodle", dayOfWeek = "FRI", startTime = "02:30 PM", endTime = "03:30 PM", slotType = "Assignment"),
                    ScheduleSlot(courseCode = "ECT301", courseTitle = "Series Exam 1 • LIC", facultyName = "MEC Exam Cell", room = "Main Hall 1", dayOfWeek = "EXAM", startTime = "09:30 AM", endTime = "11:30 AM", slotType = "Exam")
                )
                "EEE" -> listOf(
                    ScheduleSlot(courseCode = "EET301", courseTitle = "Power Systems I", facultyName = "Dr. Meera S", room = "EE-102", dayOfWeek = "MON", startTime = "09:30 AM", endTime = "10:25 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "EET305", courseTitle = "Signals & Systems", facultyName = "Prof. Nisha K", room = "EE-104", dayOfWeek = "MON", startTime = "01:30 PM", endTime = "02:25 PM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "EET307", courseTitle = "Synchronous & Induction Machines", facultyName = "Dr. Vinod R", room = "EE-101", dayOfWeek = "TUE", startTime = "10:35 AM", endTime = "11:30 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "EET301", courseTitle = "Load Flow Problem Session", facultyName = "Dr. Meera S", room = "EE-102", dayOfWeek = "WED", startTime = "09:30 AM", endTime = "10:25 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "EET303", courseTitle = "Microprocessors & Microcontrollers", facultyName = "Prof. Arun V", room = "EE-105", dayOfWeek = "THU", startTime = "11:30 AM", endTime = "12:25 PM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "EEL331", courseTitle = "Power Electronics Lab", facultyName = "EEE Lab Faculty", room = "Machines Lab", dayOfWeek = "FRI", startTime = "01:30 PM", endTime = "04:00 PM", slotType = "Lab"),
                    ScheduleSlot(courseCode = "EET301", courseTitle = "Series Exam 1 • Power Systems", facultyName = "MEC Exam Cell", room = "Main Hall 2", dayOfWeek = "EXAM", startTime = "09:30 AM", endTime = "11:30 AM", slotType = "Exam")
                )
                "BME" -> listOf(
                    ScheduleSlot(courseCode = "BMT301", courseTitle = "Biomedical Instrumentation", facultyName = "Dr. Lakshmi P", room = "BM-301", dayOfWeek = "MON", startTime = "09:30 AM", endTime = "10:25 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "BMT305", courseTitle = "Medical Imaging Techniques", facultyName = "Prof. Anju S", room = "BM-303", dayOfWeek = "TUE", startTime = "10:35 AM", endTime = "11:30 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "BMT302", courseTitle = "Biomedical Signal Processing", facultyName = "Prof. Vivek N", room = "BM-302", dayOfWeek = "WED", startTime = "10:35 AM", endTime = "11:30 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "BML331", courseTitle = "Medical Sensor Lab", facultyName = "BME Lab Faculty", room = "MedTech Lab", dayOfWeek = "THU", startTime = "01:30 PM", endTime = "04:00 PM", slotType = "Lab"),
                    ScheduleSlot(courseCode = "BMT303", courseTitle = "Biomechanics Case Study", facultyName = "Dr. Lakshmi P", room = "BM-301", dayOfWeek = "FRI", startTime = "11:30 AM", endTime = "12:25 PM", slotType = "Assignment"),
                    ScheduleSlot(courseCode = "BMT301", courseTitle = "Series Exam 1 • Instrumentation", facultyName = "MEC Exam Cell", room = "Main Hall 3", dayOfWeek = "EXAM", startTime = "09:30 AM", endTime = "11:30 AM", slotType = "Exam")
                )
                "ME" -> listOf(
                    ScheduleSlot(courseCode = "MET301", courseTitle = "Mechanics of Machinery", facultyName = "Dr. Santhosh R", room = "ME-101", dayOfWeek = "MON", startTime = "09:30 AM", endTime = "10:25 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "MET305", courseTitle = "Industrial & Systems Engineering", facultyName = "Prof. Manoj P", room = "ME-102", dayOfWeek = "MON", startTime = "01:30 PM", endTime = "02:25 PM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "MET303", courseTitle = "Thermal Engineering", facultyName = "Prof. Harish K", room = "ME-104", dayOfWeek = "TUE", startTime = "11:30 AM", endTime = "12:25 PM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "MET307", courseTitle = "Machine Tools & Metrology", facultyName = "Prof. Biju T", room = "ME-103", dayOfWeek = "WED", startTime = "09:30 AM", endTime = "10:25 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "MET301", courseTitle = "Kinematics Linkage Sheet", facultyName = "Dr. Santhosh R", room = "Drawing Hall", dayOfWeek = "THU", startTime = "02:00 PM", endTime = "03:30 PM", slotType = "Assignment"),
                    ScheduleSlot(courseCode = "MEL331", courseTitle = "CAD / CAM Simulation Lab", facultyName = "ME Lab Faculty", room = "CAD Lab", dayOfWeek = "FRI", startTime = "01:30 PM", endTime = "04:00 PM", slotType = "Lab"),
                    ScheduleSlot(courseCode = "MET301", courseTitle = "Series Exam 1 • Kinematics", facultyName = "MEC Exam Cell", room = "Main Hall 4", dayOfWeek = "EXAM", startTime = "09:30 AM", endTime = "11:30 AM", slotType = "Exam")
                )
                else -> listOf(
                    ScheduleSlot(courseCode = "CST301", courseTitle = "Formal Languages & Automata Theory", facultyName = "Dr. Priya Nair", room = "CS-302", dayOfWeek = "MON", startTime = "09:30 AM", endTime = "10:25 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "CST305", courseTitle = "System Software", facultyName = "Prof. Deepa M", room = "CS-301", dayOfWeek = "MON", startTime = "11:30 AM", endTime = "12:25 PM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "CST303", courseTitle = "Computer Networks", facultyName = "Prof. Kiran Joseph", room = "CS-304", dayOfWeek = "TUE", startTime = "10:35 AM", endTime = "11:30 AM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "CST307", courseTitle = "Microprocessors & Microcontrollers", facultyName = "Prof. Sreeraj K", room = "CS-303", dayOfWeek = "TUE", startTime = "01:30 PM", endTime = "02:25 PM", slotType = "Lecture"),
                    ScheduleSlot(courseCode = "CSL331", courseTitle = "System Software & Microprocessors Lab", facultyName = "SDPK Lab Faculty", room = "SDPK Lab", dayOfWeek = "WED", startTime = "01:30 PM", endTime = "04:00 PM", slotType = "Lab"),
                    ScheduleSlot(courseCode = "CST305", courseTitle = "System Software Assignment 2", facultyName = "Prof. Deepa M", room = "KTU Portal", dayOfWeek = "THU", startTime = "05:00 PM", endTime = "06:00 PM", slotType = "Assignment"),
                    ScheduleSlot(courseCode = "CSL333", courseTitle = "Database Management Systems Lab", facultyName = "CSE Lab Faculty", room = "CS Project Lab", dayOfWeek = "FRI", startTime = "09:30 AM", endTime = "12:00 PM", slotType = "Lab"),
                    ScheduleSlot(courseCode = "CST301", courseTitle = "Series Exam 1 • FLAT", facultyName = "MEC Exam Cell", room = "Auditorium Hall A", dayOfWeek = "EXAM", startTime = "09:30 AM", endTime = "11:30 AM", slotType = "Exam")
                )
            }
            slots.forEach { slot ->
                addScheduleSlot(
                    courseCode = slot.courseCode,
                    courseTitle = slot.courseTitle,
                    facultyName = slot.facultyName,
                    room = slot.room,
                    dayOfWeek = slot.dayOfWeek,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    slotType = slot.slotType,
                    reminderEnabled = true
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, schedRef.path, auth)
        }
    }
}
