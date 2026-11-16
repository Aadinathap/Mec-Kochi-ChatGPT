package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CalendarDayKey(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..31
) : Comparable<CalendarDayKey> {

    override fun compareTo(other: CalendarDayKey): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    fun toIsoString(): String = "%04d-%02d-%02d".format(Locale.US, year, month, day)

    fun toDisplayDate(): String {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }
        return SimpleDateFormat("MMM d, yyyy", Locale.US).format(cal.time)
    }

    fun toFullDisplayDate(): String {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }
        return SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(cal.time)
    }

    fun monthYearLabel(): String = CampusEventDateParser.monthYearLabel(year, month)
}

object CampusEventDateParser {
    private val monthLookup = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    fun today(): CalendarDayKey {
        val cal = Calendar.getInstance()
        return CalendarDayKey(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatDateMillisToLabel(utcMillis: Long): String {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        val key = CalendarDayKey(
            year = utcCal.get(Calendar.YEAR),
            month = utcCal.get(Calendar.MONTH) + 1,
            day = utcCal.get(Calendar.DAY_OF_MONTH)
        )
        return key.toDisplayDate()
    }

    fun monthYearLabel(year: Int, month: Int): String {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    fun shiftMonth(year: Int, month: Int, deltaMonths: Int): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    fun monthGridDays(year: Int, month: Int): List<CalendarDayKey?> {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }
        val leadingEmpty = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = ArrayList<CalendarDayKey?>(42)
        repeat(leadingEmpty) { cells.add(null) }
        for (d in 1..daysInMonth) {
            cells.add(CalendarDayKey(year, month, d))
        }
        while (cells.size % 7 != 0) {
            cells.add(null)
        }
        return cells
    }

    fun parseEventDates(
        dateLabel: String,
        defaultYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): List<CalendarDayKey> {
        val normalized = dateLabel.trim()
            .replace('\u2013', '-') // en-dash
            .replace('\u2014', '-') // em-dash
            .replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return emptyList()

        // 1. Cross-month range e.g. "Nov 28 - Dec 2, 2026"
        val crossMonthRange = Regex(
            """^([A-Za-z]+)\s+(\d{1,2})\s*-\s*([A-Za-z]+)\s+(\d{1,2})(?:,?\s*(\d{4}))?$"""
        ).find(normalized)
        if (crossMonthRange != null) {
            val m1 = monthLookup[crossMonthRange.groupValues[1].lowercase(Locale.US)]
            val d1 = crossMonthRange.groupValues[2].toIntOrNull()
            val m2 = monthLookup[crossMonthRange.groupValues[3].lowercase(Locale.US)]
            val d2 = crossMonthRange.groupValues[4].toIntOrNull()
            val y = crossMonthRange.groupValues[5].toIntOrNull() ?: defaultYear
            if (m1 != null && d1 != null && m2 != null && d2 != null) {
                val start = safeDayKey(y, m1, d1)
                val endYear = if (m2 < m1) y + 1 else y
                val end = safeDayKey(endYear, m2, d2)
                if (start != null && end != null) return expandRange(start, end)
            }
        }

        // 2. Same-month range e.g. "Nov 28-30, 2026" or "Nov 28 - 30"
        val sameMonthRange = Regex(
            """^([A-Za-z]+)\s+(\d{1,2})\s*-\s*(\d{1,2})(?:,?\s*(\d{4}))?$"""
        ).find(normalized)
        if (sameMonthRange != null) {
            val m = monthLookup[sameMonthRange.groupValues[1].lowercase(Locale.US)]
            val d1 = sameMonthRange.groupValues[2].toIntOrNull()
            val d2 = sameMonthRange.groupValues[3].toIntOrNull()
            val y = sameMonthRange.groupValues[4].toIntOrNull() ?: defaultYear
            if (m != null && d1 != null && d2 != null) {
                val start = safeDayKey(y, m, d1)
                val end = safeDayKey(y, m, d2)
                if (start != null && end != null) return expandRange(start, end)
            }
        }

        // 3. ISO range e.g. "2026-11-28 to 2026-11-30"
        val isoRange = Regex(
            """^(\d{4}-\d{2}-\d{2})\s*(?:-|to)\s*(\d{4}-\d{2}-\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(normalized)
        if (isoRange != null) {
            val start = parseSingleDate(isoRange.groupValues[1], defaultYear)
            val end = parseSingleDate(isoRange.groupValues[2], defaultYear)
            if (start != null && end != null) return expandRange(start, end)
        }

        // 4. Single date
        val single = parseSingleDate(normalized, defaultYear)
        return if (single != null) listOf(single) else emptyList()
    }

    private fun parseSingleDate(text: String, defaultYear: Int): CalendarDayKey? {
        val cleaned = text.trim()

        // ISO: yyyy-MM-dd
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").find(cleaned)?.let { m ->
            return safeDayKey(
                m.groupValues[1].toIntOrNull() ?: return null,
                m.groupValues[2].toIntOrNull() ?: return null,
                m.groupValues[3].toIntOrNull() ?: return null
            )
        }

        // Month Day, Year: "Nov 19, 2026" or "November 19 2026" or "Nov 19"
        Regex("""^([A-Za-z]+)\s+(\d{1,2})(?:,?\s*(\d{4}))?$""").find(cleaned)?.let { m ->
            val month = monthLookup[m.groupValues[1].lowercase(Locale.US)] ?: return null
            val day = m.groupValues[2].toIntOrNull() ?: return null
            val year = m.groupValues[3].toIntOrNull() ?: defaultYear
            return safeDayKey(year, month, day)
        }

        // Day Month Year: "19 Nov 2026" or "19 Nov"
        Regex("""^(\d{1,2})\s+([A-Za-z]+)(?:,?\s*(\d{4}))?$""").find(cleaned)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return null
            val month = monthLookup[m.groupValues[2].lowercase(Locale.US)] ?: return null
            val year = m.groupValues[3].toIntOrNull() ?: defaultYear
            return safeDayKey(year, month, day)
        }

        // Slash numeric: dd/MM/yyyy or dd-MM-yyyy
        Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$""").find(cleaned)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return null
            val month = m.groupValues[2].toIntOrNull() ?: return null
            val year = m.groupValues[3].toIntOrNull() ?: return null
            return safeDayKey(year, month, day)
        }

        return null
    }

    private fun safeDayKey(year: Int, month: Int, day: Int): CalendarDayKey? {
        if (year !in 1970..2100 || month !in 1..12) return null
        val cal = Calendar.getInstance().apply {
            clear()
            isLenient = false
            set(year, month - 1, day)
        }
        return try {
            cal.time // validates day-of-month
            CalendarDayKey(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    private fun expandRange(start: CalendarDayKey, end: CalendarDayKey): List<CalendarDayKey> {
        if (end < start) return listOf(start)
        val result = mutableListOf<CalendarDayKey>()
        val cal = Calendar.getInstance().apply {
            clear()
            set(start.year, start.month - 1, start.day)
        }
        repeat(14) {
            val current = CalendarDayKey(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
            result.add(current)
            if (current >= end) return result
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    fun eventsByDate(
        events: List<CampusEvent>,
        defaultYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): Map<CalendarDayKey, List<CampusEvent>> {
        val grouped = linkedMapOf<CalendarDayKey, MutableList<CampusEvent>>()
        events.forEach { event ->
            val days = parseEventDates(event.dateLabel, defaultYear)
            days.forEach { day ->
                grouped.getOrPut(day) { mutableListOf() }.add(event)
            }
        }
        return grouped
    }

    fun eventsForDate(
        events: List<CampusEvent>,
        date: CalendarDayKey,
        defaultYear: Int = date.year
    ): List<CampusEvent> = events.filter { event ->
        date in parseEventDates(event.dateLabel, defaultYear)
    }
}

enum class MecDepartment(val code: String, val fullName: String, val shortLabel: String) {
    CSE("CSE", "Computer Science & Engineering", "CS & Engg"),
    ECE("ECE", "Electronics & Communication", "Electronics"),
    EEE("EEE", "Electrical & Electronics", "Electrical"),
    BME("BME", "Biomedical Engineering", "Biomedical"),
    ME("ME", "Mechanical Engineering", "Mechanical")
}

data class ChatChannel(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String
)

object MecChannels {
    const val CAMPUS_ID = "mec_kochi"

    val all = listOf(
        ChatChannel("general", "MEC Quad", "Campus-wide student lounge & announcements", "ALL"),
        ChatChannel("excel_fest", "Excel MEC", "Tech fest organizing, hackathons & workshops", "FEST"),
        ChatChannel("cse", "CSE Hub", "Algorithms, KTU notes, labs & projects", "CSE"),
        ChatChannel("ece", "ECE Labs", "VLSI, embedded systems & signal processing", "ECE"),
        ChatChannel("eee", "EEE Power", "Power systems, drives & mini projects", "EEE"),
        ChatChannel("bme", "BME MedTech", "Biomedical instrumentation & healthcare tech", "BME"),
        ChatChannel("mech", "ME Works", "CAD, robotics, SAE Baja & fabrication", "ME"),
        ChatChannel("placement", "Placement Cell", "Internships, mock interviews & drive alerts", "CAREER")
    )

    val semesters = listOf("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8")
    val eventCategories = listOf("Tech Fest", "Workshop", "Hackathon", "Placement", "Cultural", "Club")
    val resourceTypes = listOf("PDF", "Link", "Notes", "PYQ", "Lab Manual", "Syllabus", "Slides")
    val alertCategories = listOf("Academic", "Exam", "Placement", "Event", "Urgent")
    val daysOfWeek = listOf("MON", "TUE", "WED", "THU", "FRI", "EXAM")
    val slotTypes = listOf("Lecture", "Lab", "Exam", "Assignment")

    fun dayFullName(dayCode: String): String = when (dayCode) {
        "MON" -> "Monday"
        "TUE" -> "Tuesday"
        "WED" -> "Wednesday"
        "THU" -> "Thursday"
        "FRI" -> "Friday"
        "EXAM" -> "KTU Series & End-Sem Exams"
        else -> dayCode
    }

    fun parseTimeToMinutes(timeStr: String): Int {
        val cleaned = timeStr.trim().uppercase()
        val match = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)?$""").find(cleaned) ?: return Int.MAX_VALUE
        val rawHour = match.groupValues[1].toIntOrNull() ?: return Int.MAX_VALUE
        val minute = match.groupValues[2].toIntOrNull() ?: return Int.MAX_VALUE
        val meridiem = match.groupValues[3]
        val hour24 = when (meridiem) {
            "AM" -> if (rawHour == 12) 0 else rawHour
            "PM" -> if (rawHour == 12) 12 else rawHour + 12
            else -> rawHour
        }
        return hour24 * 60 + minute
    }

    fun matchesResourceQuery(resource: DepartmentResource, query: String): Boolean {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        val deptFullName = MecDepartment.entries
            .firstOrNull { it.code.equals(resource.department, ignoreCase = true) }
            ?.fullName
            .orEmpty()
        val haystack = buildString {
            append(resource.title).append(' ')
            append(resource.resourceType).append(' ')
            append(resource.department).append(' ')
            append(deptFullName).append(' ')
            append(resource.semester).append(' ')
            append(resource.subjectCode).append(' ')
            append(resource.description).append(' ')
            append(resource.linkOrReference).append(' ')
            append(resource.uploaderName)
        }.lowercase()
        return tokens.all { token -> haystack.contains(token) }
    }
}

@IgnoreExtraProperties
data class StudentProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val department: String = "CSE",
    val semester: String = "S5",
    val notificationsEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ScheduleSlot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val courseCode: String = "",
    val courseTitle: String = "",
    val facultyName: String = "",
    val room: String = "",
    val dayOfWeek: String = "MON",
    val startTime: String = "",
    val endTime: String = "",
    val slotType: String = "Lecture",
    val reminderEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EventRsvp(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventId: String = "",
    val eventTitle: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val campusId: String = MecChannels.CAMPUS_ID,
    val channelId: String = "general",
    val senderId: String = "",
    val senderName: String = "",
    val senderDepartment: String = "CSE",
    val senderSemester: String = "S5",
    val content: String = "",
    val tag: String? = null,
    val imageUrl: String? = null,
    val readReceipts: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatTypingStatus(
    @DocumentId val id: String = "",
    val campusId: String = MecChannels.CAMPUS_ID,
    val channelId: String = "general",
    val userId: String = "",
    val displayName: String = "",
    val department: String = "CSE",
    val isTyping: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object ChatTransparencyHelper {
    const val TYPING_TTL_MS = 12_000L

    fun activeTypingUsers(
        statuses: List<ChatTypingStatus>,
        channelId: String,
        currentUserId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ChatTypingStatus> = statuses
        .filter { status ->
            val updatedMs = status.updatedAt?.toDate()?.time
            status.channelId == channelId &&
                status.userId != currentUserId &&
                status.isTyping &&
                status.displayName.isNotBlank() &&
                updatedMs != null &&
                (nowMillis - updatedMs) <= TYPING_TTL_MS
        }
        .sortedBy { it.displayName.lowercase(Locale.US) }

    fun formatTypingBanner(typingUsers: List<ChatTypingStatus>): String {
        val names = typingUsers.map { it.displayName.trim() }.filter { it.isNotBlank() }.distinct()
        return when (names.size) {
            0 -> ""
            1 -> "${names[0]} is typing…"
            2 -> "${names[0]} and ${names[1]} are typing…"
            else -> {
                val othersCount = names.size - 2
                val suffix = if (othersCount == 1) "other" else "others"
                "${names[0]}, ${names[1]} & $othersCount $suffix are typing…"
            }
        }
    }

    fun otherReaders(message: ChatMessage): List<Pair<String, String>> =
        message.readReceipts.entries
            .filter { (uid, name) -> uid != message.senderId && name.isNotBlank() }
            .map { it.key to it.value.trim() }
            .sortedBy { it.second.lowercase(Locale.US) }

    fun formatReadReceiptSummary(message: ChatMessage): String {
        val readers = otherReaders(message)
        return when (readers.size) {
            0 -> "Sent"
            1 -> "Seen by ${readers[0].second}"
            2 -> "Seen by ${readers[0].second}, ${readers[1].second}"
            else -> "Seen by ${readers.size} students"
        }
    }
}

@IgnoreExtraProperties
data class CampusEvent(
    @DocumentId val id: String = "",
    val campusId: String = MecChannels.CAMPUS_ID,
    val title: String = "",
    val organizer: String = "",
    val category: String = "Tech Fest",
    val venue: String = "",
    val dateLabel: String = "",
    val timeLabel: String = "",
    val description: String = "",
    val department: String = "ALL",
    val creatorId: String = "",
    val rsvpCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DepartmentResource(
    @DocumentId val id: String = "",
    val campusId: String = MecChannels.CAMPUS_ID,
    val title: String = "",
    val department: String = "CSE",
    val semester: String = "S5",
    val subjectCode: String = "",
    val resourceType: String = "Notes",
    val description: String = "",
    val linkOrReference: String = "",
    val uploaderId: String = "",
    val uploaderName: String = "",
    val upvotes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CampusAlert(
    @DocumentId val id: String = "",
    val campusId: String = MecChannels.CAMPUS_ID,
    val title: String = "",
    val message: String = "",
    val category: String = "Academic",
    val department: String = "ALL",
    val senderId: String = "",
    val senderName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FcmDeviceToken(
    @DocumentId val id: String = "",
    val userId: String = "",
    val token: String = "",
    val department: String = "CSE",
    val topics: List<String> = emptyList(),
    val enabled: Boolean = true,
    val platform: String = "android",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object FcmTopicHelper {
    const val TOPIC_ANNOUNCEMENTS = "mec_announcements"
    const val TOPIC_EVENTS = "mec_events"

    fun departmentTopic(departmentCode: String): String =
        "mec_dept_${departmentCode.trim().lowercase()}"

    fun topicsForDepartment(departmentCode: String): List<String> = listOf(
        TOPIC_ANNOUNCEMENTS,
        TOPIC_EVENTS,
        departmentTopic(departmentCode)
    )

    fun allDepartmentTopics(): List<String> =
        MecDepartment.entries.map { departmentTopic(it.code) }

    fun tokenDocId(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return "tok_" + digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
