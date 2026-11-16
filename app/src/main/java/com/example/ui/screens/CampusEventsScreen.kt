package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CalendarDayKey
import com.example.data.model.CampusEvent
import com.example.data.model.CampusEventDateParser
import com.example.data.model.EventRsvp
import com.example.data.model.MecChannels
import com.example.ui.components.CampusEventCalendar
import com.example.ui.components.CampusEventDetailPanel
import com.example.ui.components.CampusEventDetailSheet
import com.example.ui.components.MecEmptyStateCard
import com.example.ui.components.MecLoadingState
import com.example.ui.components.MecTagPill
import com.example.ui.theme.departmentAccentColor
import com.example.ui.viewmodel.UiState

@Composable
fun CampusEventsScreen(
    eventsState: UiState<List<CampusEvent>>,
    rsvpsState: UiState<List<EventRsvp>>,
    selectedCategory: String,
    viewMode: String = "CALENDAR",
    calendarYear: Int = CampusEventDateParser.today().year,
    calendarMonth: Int = CampusEventDateParser.today().month,
    selectedCalendarDate: CalendarDayKey = CampusEventDateParser.today(),
    selectedEventId: String? = null,
    onSelectCategory: (String) -> Unit,
    onSelectViewMode: (String) -> Unit = {},
    onSelectCalendarDate: (CalendarDayKey) -> Unit = {},
    onChangeCalendarMonth: (Int, Int) -> Unit = { _, _ -> },
    onJumpToToday: () -> Unit = {},
    onSelectEvent: (String?) -> Unit = {},
    onToggleRsvp: (CampusEvent, Boolean) -> Unit,
    onOpenCreateEvent: (initialDate: CalendarDayKey?) -> Unit,
    modifier: Modifier = Modifier
) {
    val events = (eventsState as? UiState.Success)?.data.orEmpty()
    val rsvpIds = remember(rsvpsState) {
        ((rsvpsState as? UiState.Success)?.data.orEmpty()).map { it.eventId }.toSet()
    }
    val filteredEvents = remember(events, selectedCategory) {
        if (selectedCategory == "ALL") events else events.filter { it.category == selectedCategory }
    }
    val selectedEvent = remember(events, selectedEventId) {
        selectedEventId?.let { id -> events.firstOrNull { it.id == id } }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedTwoPane = maxWidth >= 840.dp
            val columns = if (maxWidth >= 720.dp) 2 else 1

            if (eventsState is UiState.Loading) {
                MecLoadingState()
            } else if (eventsState is UiState.Error) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MecEmptyStateCard(
                        icon = Icons.Rounded.EventAvailable,
                        title = "Could not load campus events",
                        subtitle = eventsState.message
                    )
                }
            } else if (viewMode == "CALENDAR" && isExpandedTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                    ) {
                        item { EventsHeroHeader() }
                        item {
                            EventsModeAndCategoryControls(
                                viewMode = viewMode,
                                selectedCategory = selectedCategory,
                                onSelectViewMode = onSelectViewMode,
                                onSelectCategory = onSelectCategory
                            )
                        }
                        item {
                            CampusEventCalendar(
                                events = filteredEvents,
                                rsvpIds = rsvpIds,
                                selectedDate = selectedCalendarDate,
                                displayedYear = calendarYear,
                                displayedMonth = calendarMonth,
                                selectedEventId = selectedEventId,
                                onSelectDate = onSelectCalendarDate,
                                onChangeMonth = onChangeCalendarMonth,
                                onJumpToToday = onJumpToToday,
                                onSelectEvent = { ev -> onSelectEvent(ev.id) },
                                onHostEventOnDate = { date -> onOpenCreateEvent(date) }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 92.dp)
                    ) {
                        CampusEventDetailPanel(
                            event = selectedEvent,
                            isGoing = selectedEvent != null && selectedEvent.id in rsvpIds,
                            onToggleRsvp = onToggleRsvp
                        )
                    }
                }
            } else if (viewMode == "CALENDAR") {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item { EventsHeroHeader() }
                    item {
                        EventsModeAndCategoryControls(
                            viewMode = viewMode,
                            selectedCategory = selectedCategory,
                            onSelectViewMode = onSelectViewMode,
                            onSelectCategory = onSelectCategory
                        )
                    }
                    item {
                        CampusEventCalendar(
                            events = filteredEvents,
                            rsvpIds = rsvpIds,
                            selectedDate = selectedCalendarDate,
                            displayedYear = calendarYear,
                            displayedMonth = calendarMonth,
                            selectedEventId = selectedEventId,
                            onSelectDate = onSelectCalendarDate,
                            onChangeMonth = onChangeCalendarMonth,
                            onJumpToToday = onJumpToToday,
                            onSelectEvent = { ev -> onSelectEvent(ev.id) },
                            onHostEventOnDate = { date -> onOpenCreateEvent(date) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(columns) }) {
                        EventsHeroHeader()
                    }

                    item(span = { GridItemSpan(columns) }) {
                        EventsModeAndCategoryControls(
                            viewMode = viewMode,
                            selectedCategory = selectedCategory,
                            onSelectViewMode = onSelectViewMode,
                            onSelectCategory = onSelectCategory
                        )
                    }

                    if (filteredEvents.isEmpty()) {
                        item(span = { GridItemSpan(columns) }) {
                            MecEmptyStateCard(
                                icon = Icons.Rounded.EventAvailable,
                                title = "No events in this category",
                                subtitle = "Tap Host Event below to publish an MEC workshop, club meetup, or tech session."
                            )
                        }
                    } else {
                        items(filteredEvents, key = { it.id }) { event ->
                            val isGoing = event.id in rsvpIds
                            CampusEventCard(
                                event = event,
                                isGoing = isGoing,
                                onSelect = { onSelectEvent(event.id) },
                                onToggleRsvp = { onToggleRsvp(event, isGoing) }
                            )
                        }
                    }
                }
            }
            if (selectedEvent != null && (viewMode == "FEED" || !isExpandedTwoPane)) {
                val isGoing = selectedEvent.id in rsvpIds
                CampusEventDetailSheet(
                    event = selectedEvent,
                    isGoing = isGoing,
                    onToggleRsvp = { onToggleRsvp(selectedEvent, isGoing) },
                    onDismiss = { onSelectEvent(null) }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onOpenCreateEvent(if (viewMode == "CALENDAR") selectedCalendarDate else null) },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Host Event", style = MaterialTheme.typography.labelLarge) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("host_event_fab")
        )
    }
}

@Composable
private fun EventsModeAndCategoryControls(
    viewMode: String,
    selectedCategory: String,
    onSelectViewMode: (String) -> Unit,
    onSelectCategory: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("events_view_mode_toggle")
        ) {
            SegmentedButton(
                selected = viewMode == "CALENDAR",
                onClick = { onSelectViewMode("CALENDAR") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.events_view_calendar),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier.testTag("events_mode_calendar")
            )
            SegmentedButton(
                selected = viewMode == "FEED",
                onClick = { onSelectViewMode("FEED") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ViewList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.events_view_feed),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier.testTag("events_mode_feed")
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("ALL") + MecChannels.eventCategories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = {
                        Text(
                            text = if (cat == "ALL") "All Events" else cat,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun EventsHeroHeader() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_excel_fest_banner),
                contentDescription = "MEC Kochi Events & Excel Fest",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF060B18).copy(alpha = 0.92f),
                                Color(0xFF060B18).copy(alpha = 0.45f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp)
            ) {
                MecTagPill(
                    text = "EXCEL MEC • IEEE • FOSSMEC • IEDC",
                    accentColor = Color(0xFFFBBF24)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Campus Events & Calendar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "Firestore-synced dates & live RSVPs across Thrikkakara clubs",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun CampusEventCard(
    event: CampusEvent,
    isGoing: Boolean,
    onSelect: () -> Unit,
    onToggleRsvp: () -> Unit
) {
    val deptColor = departmentAccentColor(event.department)
    val buttonBg by animateColorAsState(
        targetValue = if (isGoing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        label = "rsvpBg"
    )

    Surface(
        onClick = onSelect,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            if (isGoing) MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("event_feed_card_${event.id}")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MecTagPill(text = event.category, accentColor = MaterialTheme.colorScheme.tertiary)
                    MecTagPill(text = event.department, accentColor = deptColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${event.rsvpCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "By ${event.organizer}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${event.dateLabel} • ${event.timeLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = event.venue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isGoing) {
                OutlinedButton(
                    onClick = onToggleRsvp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("rsvp_button_${event.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Going • Tap to Cancel RSVP",
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
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("rsvp_button_${event.id}")
                ) {
                    Text(
                        text = "RSVP Now",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
