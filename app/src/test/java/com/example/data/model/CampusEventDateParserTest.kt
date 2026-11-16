package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusEventDateParserTest {

    @Test
    fun parseEventDates_handlesSingleDatesAcrossFormats() {
        assertEquals(
            listOf(CalendarDayKey(2026, 11, 19)),
            CampusEventDateParser.parseEventDates("Nov 19, 2026")
        )
        assertEquals(
            listOf(CalendarDayKey(2026, 11, 24)),
            CampusEventDateParser.parseEventDates("2026-11-24")
        )
        assertEquals(
            listOf(CalendarDayKey(2026, 11, 21)),
            CampusEventDateParser.parseEventDates("21/11/2026")
        )
        assertEquals(
            listOf(CalendarDayKey(2026, 12, 5)),
            CampusEventDateParser.parseEventDates("December 5, 2026")
        )
    }

    @Test
    fun parseEventDates_expandsSameMonthAndCrossMonthRanges() {
        assertEquals(
            listOf(
                CalendarDayKey(2026, 11, 28),
                CalendarDayKey(2026, 11, 29),
                CalendarDayKey(2026, 11, 30)
            ),
            CampusEventDateParser.parseEventDates("Nov 28–30, 2026")
        )

        assertEquals(
            listOf(
                CalendarDayKey(2026, 11, 29),
                CalendarDayKey(2026, 11, 30),
                CalendarDayKey(2026, 12, 1)
            ),
            CampusEventDateParser.parseEventDates("Nov 29 - Dec 1, 2026")
        )
    }

    @Test
    fun eventsByDate_groupsEventsOnRespectiveDates() {
        val events = listOf(
            CampusEvent(id = "e1", title = "Excel 2026", dateLabel = "Nov 28–30, 2026"),
            CampusEvent(id = "e2", title = "FOSSMEC Sprint", dateLabel = "Nov 19, 2026")
        )

        val grouped = CampusEventDateParser.eventsByDate(events, defaultYear = 2026)

        assertEquals(1, grouped[CalendarDayKey(2026, 11, 19)]?.size)
        assertEquals("e2", grouped[CalendarDayKey(2026, 11, 19)]?.first()?.id)
        assertEquals("e1", grouped[CalendarDayKey(2026, 11, 28)]?.first()?.id)
        assertEquals("e1", grouped[CalendarDayKey(2026, 11, 29)]?.first()?.id)
        assertEquals("e1", grouped[CalendarDayKey(2026, 11, 30)]?.first()?.id)
    }

    @Test
    fun monthGridDays_november2026_startsOnSundayAndHas30Days() {
        val grid = CampusEventDateParser.monthGridDays(2026, 11)
        assertTrue(grid.size % 7 == 0)
        assertEquals(CalendarDayKey(2026, 11, 1), grid.first())
        assertEquals(30, grid.filterNotNull().size)
    }
}
