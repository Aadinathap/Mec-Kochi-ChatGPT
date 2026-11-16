package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class MecCampusRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveProfile_and_observeProfile_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = MecCampusRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveProfile(
                displayName = "Aadinath P",
                department = "CSE",
                semester = "S6",
                notificationsEnabled = true,
                darkThemeEnabled = true
            )
        }

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals("CSE", profile?.department)
        assertEquals("S6", profile?.semester)
    }

    @Test
    fun academicSchedule_crudAndCrossUserIsolation() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MecCampusRepository(firestore, auth)
        val uniqueCode = "CST${UUID.randomUUID().toString().take(4).uppercase()}"

        val slotId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addScheduleSlot(
                courseCode = uniqueCode,
                courseTitle = "Compiler Design",
                facultyName = "Dr. Anitha R",
                room = "CS-301",
                dayOfWeek = "MON",
                startTime = "09:30 AM",
                endTime = "10:25 AM",
                slotType = "Lecture",
                reminderEnabled = true
            )
        }
        assertTrue(slotId.isNotBlank())

        val afternoonSlotId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addScheduleSlot(
                courseCode = "CSL332",
                courseTitle = "Compiler Design Lab",
                facultyName = "CSE Lab Faculty",
                room = "SDPK Lab",
                dayOfWeek = "MON",
                startTime = "01:30 PM",
                endTime = "04:00 PM",
                slotType = "Lab",
                reminderEnabled = true
            )
        }
        assertTrue(afternoonSlotId.isNotBlank())

        val slots = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeAcademicSchedule(aliceUid).first { list ->
                list.any { it.id == slotId } && list.any { it.id == afternoonSlotId }
            }
        }
        val monSlots = slots.filter { it.dayOfWeek == "MON" }
        val morningIndex = monSlots.indexOfFirst { it.id == slotId }
        val afternoonIndex = monSlots.indexOfFirst { it.id == afternoonSlotId }
        assertTrue("Expected morning slot before afternoon slot", morningIndex in 0 until afternoonIndex)
        assertEquals(uniqueCode, monSlots[morningIndex].courseCode)

        // Cross-user read on Alice's schedule must fail with PERMISSION_DENIED
        signInTestUser(BOB_EMAIL)
        val bobRepo = MecCampusRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeAcademicSchedule(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob observes Alice's academic schedule")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    @Test
    fun chatMessages_sendObserveAndEnforceOwnerDelete() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = MecCampusRepository(firestore, auth)
        val uniqueMsg = "Hello MEC ${UUID.randomUUID()}"

        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendChatMessage(
                channelId = "general",
                senderName = "Aadinath",
                senderDepartment = "CSE",
                senderSemester = "S6",
                content = uniqueMsg,
                tag = "Discussion"
            )
        }

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChatMessages().first { list -> list.any { it.id == msgId } }
        }
        assertTrue(messages.any { it.content == uniqueMsg })

        val sampleImageUrl = "https://firebasestorage.googleapis.com/v0/b/mec-kochi.appspot.com/o/chat_images%2Fgeneral%2Fboard.jpg?alt=media"
        val imgMsgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendChatMessage(
                channelId = "general",
                senderName = "Aadinath",
                senderDepartment = "CSE",
                senderSemester = "S6",
                content = "",
                tag = "Note",
                imageUrl = sampleImageUrl
            )
        }
        val messagesWithPhoto = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChatMessages().first { list -> list.any { it.id == imgMsgId } }
        }
        assertEquals(sampleImageUrl, messagesWithPhoto.first { it.id == imgMsgId }.imageUrl)

        // Bob can read campus chat, but cannot delete Alice's message
        signInTestUser(BOB_EMAIL)
        val bobRepo = MecCampusRepository(firestore, auth)
        val bobMessages = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeChatMessages().first { list -> list.any { it.id == msgId } }
        }
        assertTrue(bobMessages.any { it.id == msgId })

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.deleteChatMessage(msgId)
            }
            fail("Expected PERMISSION_DENIED when Bob deletes Alice's chat message")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    @Test
    fun chatReadReceiptsAndTypingIndicators_syncAndEnforceSecurity() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MecCampusRepository(firestore, auth)
        val uniqueMsg = "Receipt check ${UUID.randomUUID()}"

        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendChatMessage(
                channelId = "cse",
                senderName = "Aadinath",
                senderDepartment = "CSE",
                senderSemester = "S6",
                content = uniqueMsg
            )
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.setTypingStatus(
                channelId = "cse",
                displayName = "Aadinath",
                department = "CSE",
                isTyping = true
            )
        }

        // Bob marks Alice's message read and observes Alice's typing indicator
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = MecCampusRepository(firestore, auth)
        val activeTypers = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeChatTypingStatuses().first { list ->
                list.any { it.userId == aliceUid && it.channelId == "cse" && it.isTyping }
            }
        }
        assertTrue(activeTypers.any { it.displayName == "Aadinath" && it.isTyping })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.markChatMessagesAsRead(listOf(msgId), "Abil Sajee")
        }

        val updatedMessages = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeChatMessages().first { list ->
                list.firstOrNull { it.id == msgId }?.readReceipts?.get(bobUid) == "Abil Sajee"
            }
        }
        assertEquals("Abil Sajee", updatedMessages.first { it.id == msgId }.readReceipts[bobUid])

        // Bob cannot spoof Alice's typing status document
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("chat_typing").document("cse_$aliceUid")
                    .update("isTyping", false)
                    .await()
            }
            fail("Expected PERMISSION_DENIED when Bob updates Alice's typing doc")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }

        // Alice clears her typing status
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.setTypingStatus(
                channelId = "cse",
                displayName = "Aadinath",
                department = "CSE",
                isTyping = false
            )
        }
        val cleared = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChatTypingStatuses().first { list ->
                list.firstOrNull { it.userId == aliceUid && it.channelId == "cse" }?.isTyping == false
            }
        }
        assertTrue(cleared.any { it.userId == aliceUid && !it.isTyping })
    }

    @Test
    fun campusEvents_resourcesAndAlerts_flowAndRsvpSucceeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = MecCampusRepository(firestore, auth)

        val eventTitle = "MEC Hack ${UUID.randomUUID().toString().take(5)}"
        val eventId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createCampusEvent(
                title = eventTitle,
                organizer = "FOSSMEC",
                category = "Hackathon",
                venue = "SDPK Lab",
                dateLabel = "Nov 25, 2026",
                timeLabel = "10:00 AM",
                description = "Campus open source sprint at MEC Kochi.",
                department = "CSE"
            )
        }

        val events = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeCampusEvents().first { list -> list.any { it.id == eventId } }
        }
        val event = events.first { it.id == eventId }
        assertEquals(1L, event.rsvpCount)

        // Bob RSVPs to Alice's event
        signInTestUser(BOB_EMAIL)
        val bobRepo = MecCampusRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.toggleEventRsvp(event, currentlyRsvped = false)
        }

        val updatedEvents = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeCampusEvents().first { list ->
                list.firstOrNull { it.id == eventId }?.rsvpCount == 2L
            }
        }
        assertEquals(2L, updatedEvents.first { it.id == eventId }.rsvpCount)

        // Bob posts an alert and adds a resource
        val resId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.addDepartmentResource(
                title = "ECT201 Signals Notes PDF",
                department = "ECE",
                semester = "S3",
                subjectCode = "ECT201",
                resourceType = "PDF",
                description = "Fourier and Laplace transform notes.",
                linkOrReference = "https://mec.ac.in/ece/ect201.pdf",
                uploaderName = "Bob"
            )
        }
        val linkResId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.addDepartmentResource(
                title = "CST301 KTU Archive Link",
                department = "CSE",
                semester = "S5",
                subjectCode = "CST301",
                resourceType = "Link",
                description = "Online lecture links and question banks.",
                linkOrReference = "https://github.com/fossmec",
                uploaderName = "Bob"
            )
        }
        val resources = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeDepartmentResources().first { list ->
                list.any { it.id == resId } && list.any { it.id == linkResId }
            }
        }
        assertTrue(resources.any { it.id == resId && it.resourceType == "PDF" })
        assertTrue(resources.any { it.id == linkResId && it.resourceType == "Link" })

        val alertId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.postCampusAlert(
                title = "Lab Submission Update",
                message = "Submit S5 records before Friday 4 PM.",
                category = "Academic",
                department = "ALL",
                senderName = "Bob"
            )
        }
        val alerts = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeCampusAlerts().first { list -> list.any { it.id == alertId } }
        }
        assertTrue(alerts.any { it.id == alertId })
    }

    @Test
    fun observeEvents_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = MecCampusRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCampusEvents().first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated campus_events query")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    @Test
    fun fcmTokens_upsertObserveAndCrossUserIsolation() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MecCampusRepository(firestore, auth)
        val sampleToken = "fcm_token_${UUID.randomUUID()}_sample_android_device"

        val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertFcmToken(
                token = sampleToken,
                department = "CSE",
                topics = listOf("mec_announcements", "mec_events", "mec_dept_cse"),
                enabled = true
            )
        }
        assertTrue(docId.startsWith("tok_"))

        val tokens = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeFcmTokens(aliceUid).first { list -> list.any { it.id == docId } }
        }
        val saved = tokens.first { it.id == docId }
        assertEquals(sampleToken, saved.token)
        assertTrue(saved.topics.contains("mec_events"))

        // Cross-user read on Alice's FCM tokens must fail
        signInTestUser(BOB_EMAIL)
        val bobRepo = MecCampusRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeFcmTokens(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob observes Alice's FCM tokens")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_mec@test.com"
        const val BOB_EMAIL = "bob_mec@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
