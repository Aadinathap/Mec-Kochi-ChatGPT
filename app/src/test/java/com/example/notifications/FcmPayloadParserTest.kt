package com.example.notifications

import com.example.data.model.FcmTopicHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmPayloadParserTest {

    @Test
    fun parseEventUpdate_routesToEventsAndMarksEventChannel() {
        val payload = FcmPayloadParser.parse(
            notificationTitle = "Excel 2026 Hackathon",
            notificationBody = "Registrations close tonight at 11:59 PM.",
            data = mapOf(
                "type" to "event_update",
                "department" to "cse",
                "eventId" to "evt_excel_2026"
            ),
            messageId = "msg_evt_1"
        )

        assertTrue(payload.isEventUpdate)
        assertEquals("events", payload.targetRoute)
        assertEquals("CSE", payload.department)
        assertEquals("Event", payload.category)
        assertEquals("Excel 2026 Hackathon", payload.title)
    }

    @Test
    fun parseAnnouncement_fromDataOnlyPayload_routesToPulse() {
        val payload = FcmPayloadParser.parse(
            notificationTitle = null,
            notificationBody = null,
            data = mapOf(
                "type" to "announcement",
                "category" to "Exam",
                "department" to "ALL",
                "title" to "KTU Series Exam Hall Ticket",
                "message" to "Download hall tickets from the KTU student portal."
            ),
            messageId = "msg_alert_1"
        )

        assertFalse(payload.isEventUpdate)
        assertEquals("pulse", payload.targetRoute)
        assertEquals("Exam", payload.category)
        assertEquals("ALL", payload.department)
        assertEquals("KTU Series Exam Hall Ticket", payload.title)
        assertEquals("Download hall tickets from the KTU student portal.", payload.body)
    }

    @Test
    fun topicHelper_buildsValidDepartmentTopicsAndDocId() {
        val topics = FcmTopicHelper.topicsForDepartment("ECE")
        assertEquals(listOf("mec_announcements", "mec_events", "mec_dept_ece"), topics)

        val docId = FcmTopicHelper.tokenDocId("sample_fcm_token:with_colons_123456")
        assertTrue(docId.matches(Regex("^[a-zA-Z0-9_\\-]+$")))
    }
}
