package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CalendarDayKey
import com.example.data.model.CampusEvent
import com.example.data.model.CampusEventDateParser
import com.example.ui.theme.departmentAccentColor

private val WeekdayLabels = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

@Composable
fun CampusEventCalendar(
    events: List<CampusEvent>,
    rsvpIds: Set<String>,
    selectedDate: CalendarDayKey,
    displayedYear: Int,
    displayedMonth: Int,
    selectedEventId: String?,
    onSelectDate: (CalendarDayKey) -> Unit,
    onChangeMonth: (year: Int, month: Int) -> Unit,
    onJumpToToday: () -> Unit,
    onSelectEvent: (CampusEvent) -> Unit,
    onHostEventOnDate: (CalendarDayKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { CampusEventDateParser.today() }
    val eventsByDay = remember(events, displayedYear) {
        CampusEventDateParser.eventsByDate(events, defaultYear = displayedYear)
    }
    val gridDays = remember(displayedYear, displayedMonth) {
        CampusEventDateParser.monthGridDays(displayedYear, displayedMonth)
    }
    val monthEventDates = remember(eventsByDay, displayedYear, displayedMonth) {
        eventsByDay.keys
            .filter { it.year == displayedYear && it.month == displayedMonth }
            .sorted()
    }
    val monthTotalEvents = remember(eventsByDay, monthEventDates) {
        monthEventDates
            .flatMap { eventsByDay[it].orEmpty() }
            .distinctBy { it.id.ifBlank { "${it.title}_${it.dateLabel}" } }
            .size
    }
    val selectedDayEvents = remember(eventsByDay, selectedDate) {
        eventsByDay[selectedDate].orEmpty()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Monthly Calendar Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("campus_events_calendar_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header: Month Title + Live Sync Pill + Month Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = CampusEventDateParser.monthYearLabel(displayedYear, displayedMonth),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("calendar_month_title")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = stringResource(R.string.calendar_live_sync),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                text = if (monthTotalEvents == 1) "1 event this month" else "$monthTotalEvents events this month",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onJumpToToday,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_today_button")
                        ) {
                            Text(
                                text = stringResource(R.string.calendar_today),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val (prevY, prevM) = CampusEventDateParser.shiftMonth(displayedYear, displayedMonth, -1)
                                onChangeMonth(prevY, prevM)
                            },
                            modifier = Modifier.testTag("calendar_prev_month")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronLeft,
                                contentDescription = "Previous month",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                val (nextY, nextM) = CampusEventDateParser.shiftMonth(displayedYear, displayedMonth, 1)
                                onChangeMonth(nextY, nextM)
                            },
                            modifier = Modifier.testTag("calendar_next_month")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Next month",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Weekday Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WeekdayLabels.forEachIndexed { idx, dayName ->
                        val isWeekend = idx == 0 || idx == 6
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isWeekend) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Month Days Grid (non-lazy rows so it nests cleanly inside LazyVerticalGrid / LazyColumn)
                AnimatedContent(
                    targetState = displayedYear to displayedMonth,
                    transitionSpec = { fadeIn(spring()) togetherWith fadeOut(spring()) },
                    label = "calendarMonthGrid"
                ) { (yearVal, monthVal) ->
                    val animatedGrid = remember(yearVal, monthVal) {
                        CampusEventDateParser.monthGridDays(yearVal, monthVal)
                    }
                    val weeks = remember(animatedGrid) { animatedGrid.chunked(7) }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        weeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                week.forEach { dayKey ->
                                    if (dayKey == null) {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        )
                                    } else {
                                        val dayEvents = eventsByDay[dayKey].orEmpty()
                                        CalendarDayCell(
                                            dayKey = dayKey,
                                            isSelected = dayKey == selectedDate,
                                            isToday = dayKey == today,
                                            dayEvents = dayEvents,
                                            onClick = { onSelectDate(dayKey) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Active-month Event Date Quick Jump Chips
                if (monthEventDates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ACTIVE EVENT DATES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(monthEventDates, key = { it.toIsoString() }) { activeDate ->
                            val count = eventsByDay[activeDate]?.size ?: 0
                            val isSelected = activeDate == selectedDate
                            Surface(
                                onClick = { onSelectDate(activeDate) },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.testTag("calendar_quick_date_${activeDate.toIsoString()}")
                            ) {
                                Text(
                                    text = "${activeDate.toDisplayDate().substringBefore(",")} • $count",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Date Agenda Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedDate.toFullDisplayDate(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("calendar_selected_date_header")
                )
                Text(
                    text = when (selectedDayEvents.size) {
                        0 -> "No campus events scheduled"
                        1 -> "1 campus event • Tap for details"
                        else -> "${selectedDayEvents.size} campus events • Tap any event for details"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = { onHostEventOnDate(selectedDate) },
                modifier = Modifier.testTag("calendar_host_on_date_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Host on Date", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Selected Date Event List
        if (selectedDayEvents.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calendar_empty_day_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open Day on MEC Calendar",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select a highlighted date above or tap Host on Date to publish an event.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                selectedDayEvents.forEach { event ->
                    val isGoing = event.id in rsvpIds
                    val isCurrentlyInspected = selectedEventId != null && selectedEventId == event.id
                    CalendarAgendaEventRow(
                        event = event,
                        isGoing = isGoing,
                        isInspected = isCurrentlyInspected,
                        onClick = { onSelectEvent(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayKey: CalendarDayKey,
    isSelected: Boolean,
    isToday: Boolean,
    dayEvents: List<CampusEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasEvents = dayEvents.isNotEmpty()
    val cellBg by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            hasEvents -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
            else -> Color.Transparent
        },
        label = "dayCellBg"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isToday -> MaterialTheme.colorScheme.primary
            hasEvents -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "dayTextColor"
    )
    val borderStroke = when {
        isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isToday -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
        hasEvents -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        else -> null
    }

    val accessibilityLabel = buildString {
        append(dayKey.toDisplayDate())
        if (isToday) append(", Today")
        if (hasEvents) append(", ${dayEvents.size} events")
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cellBg)
            .then(
                if (borderStroke != null) {
                    Modifier.border(borderStroke, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel }
            .testTag("calendar_day_${dayKey.toIsoString()}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = dayKey.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = when {
                    isSelected || isToday || hasEvents -> FontWeight.Bold
                    else -> FontWeight.Normal
                }
            )
            Spacer(modifier = Modifier.height(3.dp))
            if (hasEvents) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dayEvents.take(3).forEach { ev ->
                        val dotColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            departmentAccentColor(ev.department)
                        }
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun CalendarAgendaEventRow(
    event: CampusEvent,
    isGoing: Boolean,
    isInspected: Boolean,
    onClick: () -> Unit
) {
    val deptColor = departmentAccentColor(event.department)
    Surface(
        onClick = onClick,
        color = if (isInspected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (isInspected || isGoing) 1.5.dp else 1.dp,
            color = when {
                isInspected -> MaterialTheme.colorScheme.primary
                isGoing -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("calendar_event_item_${event.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left accent time pill
            Surface(
                color = deptColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, deptColor.copy(alpha = 0.3f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = event.timeLabel.substringBefore(" "),
                        style = MaterialTheme.typography.titleSmall,
                        color = deptColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = event.timeLabel.substringAfter(" ", ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = deptColor
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MecTagPill(text = event.category, accentColor = MaterialTheme.colorScheme.tertiary)
                    MecTagPill(text = event.department, accentColor = deptColor)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${event.venue} • ${event.rsvpCount} going",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (isGoing) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Going",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "View event details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusEventDetailSheet(
    event: CampusEvent,
    isGoing: Boolean,
    onToggleRsvp: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        CampusEventDetailContent(
            event = event,
            isGoing = isGoing,
            onToggleRsvp = onToggleRsvp,
            onClose = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
fun CampusEventDetailPanel(
    event: CampusEvent?,
    isGoing: Boolean,
    onToggleRsvp: (CampusEvent, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("campus_event_detail_panel")
    ) {
        if (event == null) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MecEmptyStateCard(
                    icon = Icons.Rounded.EventAvailable,
                    title = "Select an Event",
                    subtitle = "Choose any campus event from the calendar or feed to inspect schedule, venue, and RSVP details."
                )
            }
        } else {
            CampusEventDetailContent(
                event = event,
                isGoing = isGoing,
                onToggleRsvp = { onToggleRsvp(event, isGoing) },
                onClose = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            )
        }
    }
}

@Composable
fun CampusEventDetailContent(
    event: CampusEvent,
    isGoing: Boolean,
    onToggleRsvp: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deptColor = departmentAccentColor(event.department)
    val buttonBg by animateColorAsState(
        targetValue = if (isGoing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        label = "detailRsvpBg"
    )

    Column(
        modifier = modifier.testTag("event_detail_content_${event.id}"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MecTagPill(text = event.category, accentColor = MaterialTheme.colorScheme.tertiary)
                MecTagPill(text = event.department, accentColor = deptColor)
            }
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("event_detail_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close event details"
                    )
                }
            }
        }

        // Gradient Title Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("event_detail_title")
                )
                Text(
                    text = "Organized by ${event.organizer}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Structured Metadata Rows
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailInfoChip(
                    icon = Icons.Rounded.CalendarMonth,
                    label = "Date",
                    value = event.dateLabel,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                DetailInfoChip(
                    icon = Icons.Rounded.Schedule,
                    label = "Time",
                    value = event.timeLabel,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailInfoChip(
                    icon = Icons.Rounded.LocationOn,
                    label = "Venue",
                    value = event.venue,
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                DetailInfoChip(
                    icon = Icons.Rounded.Groups,
                    label = "Attendees",
                    value = "${event.rsvpCount} Going",
                    accent = deptColor,
                    modifier = Modifier.weight(1f)
                )
            }
            DetailInfoChip(
                icon = Icons.Rounded.School,
                label = "Campus Eligibility",
                value = if (event.department == "ALL") {
                    "Open to all MEC Kochi departments"
                } else {
                    "${event.department} Department & MEC Community"
                },
                accent = deptColor,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Description
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "About This Event",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("event_detail_description")
                )
            }
        }

        // Primary Actions: RSVP + Share
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isGoing) {
                OutlinedButton(
                    onClick = onToggleRsvp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("event_detail_rsvp_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Going • Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onToggleRsvp,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBg,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("event_detail_rsvp_button")
                ) {
                    Text(
                        text = "RSVP Now",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    val shareText = buildString {
                        append("${event.title}\n")
                        append("By ${event.organizer} (${event.category} • ${event.department})\n")
                        append("Date: ${event.dateLabel} at ${event.timeLabel}\n")
                        append("Venue: ${event.venue}\n\n")
                        append(event.description)
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share MEC Event"))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(50.dp)
                    .testTag("event_detail_share_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.event_share),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DetailInfoChip(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
