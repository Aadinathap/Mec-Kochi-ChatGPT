package com.example.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.CalendarDayKey
import com.example.data.model.CampusEvent
import com.example.data.model.EventRsvp
import com.example.ui.screens.CampusEventsScreen
import com.example.ui.theme.MecConnectTheme
import com.example.ui.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CampusEventCalendarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calendarView_selectsDateAndDisplaysEventDetailsOnSelection() {
        val events = listOf(
            CampusEvent(
                id = "evt_fossmec",
                title = "FOSSMEC Open Source Sprint",
                organizer = "FOSSMEC",
                category = "Workshop",
                venue = "SDPK Lab, Main Block",
                dateLabel = "Nov 19, 2026",
                timeLabel = "04:15 PM",
                description = "Hands-on Git and Linux kernel workshop for MEC students.",
                department = "CSE",
                rsvpCount = 12L
            ),
            CampusEvent(
                id = "evt_excel",
                title = "Excel 2026 Tech Fest",
                organizer = "Excel Core",
                category = "Tech Fest",
                venue = "MEC Auditorium",
                dateLabel = "Nov 28–30, 2026",
                timeLabel = "09:00 AM",
                description = "Annual flagship techno-managerial fest at MEC Kochi.",
                department = "ALL",
                rsvpCount = 150L
            )
        )

        var selectedDate by mutableStateOf(CalendarDayKey(2026, 11, 16))
        var selectedEventId by mutableStateOf<String?>(null)
        var rsvpClickedEventId: String? = null

        composeRule.setContent {
            MecConnectTheme(darkTheme = false) {
                CampusEventsScreen(
                    eventsState = UiState.Success(events),
                    rsvpsState = UiState.Success(emptyList<EventRsvp>()),
                    selectedCategory = "ALL",
                    viewMode = "CALENDAR",
                    calendarYear = 2026,
                    calendarMonth = 11,
                    selectedCalendarDate = selectedDate,
                    selectedEventId = selectedEventId,
                    onSelectCategory = {},
                    onSelectViewMode = {},
                    onSelectCalendarDate = { selectedDate = it },
                    onChangeCalendarMonth = { _, _ -> },
                    onJumpToToday = {},
                    onSelectEvent = { selectedEventId = it },
                    onToggleRsvp = { ev, _ -> rsvpClickedEventId = ev.id },
                    onOpenCreateEvent = {},
                    modifier = Modifier
                )
            }
        }

        // Calendar month title & initially empty selected date
        composeRule.onNodeWithTag("calendar_month_title")
            .assertIsDisplayed()
            .assertTextContains("November 2026")
        composeRule.onNodeWithTag("calendar_empty_day_card")
            .performScrollTo()
            .assertIsDisplayed()

        // Tap Nov 19, 2026 on calendar grid -> shows FOSSMEC event in agenda
        composeRule.onNodeWithTag("calendar_day_2026-11-19")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("calendar_event_item_evt_fossmec")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        // Event details sheet opens with full venue, description, and RSVP action
        composeRule.onNodeWithTag("event_detail_title")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("FOSSMEC Open Source Sprint")
        composeRule.onNodeWithTag("event_detail_description")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Hands-on Git and Linux kernel workshop for MEC students.")
        composeRule.onNodeWithText("SDPK Lab, Main Block")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("event_detail_rsvp_button")
            .performScrollTo()
            .performClick()
        assertEquals("evt_fossmec", rsvpClickedEventId)
    }
}
