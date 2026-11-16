package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.MecChannels
import com.example.data.model.ScheduleSlot
import com.example.ui.components.MecEmptyStateCard
import com.example.ui.components.MecLoadingState
import com.example.ui.components.MecTagPill
import com.example.ui.viewmodel.UiState

@Composable
fun AcademicScheduleScreen(
    scheduleState: UiState<List<ScheduleSlot>>,
    selectedDay: String,
    viewMode: String,
    department: String,
    semester: String,
    onSelectDay: (String) -> Unit,
    onSelectViewMode: (String) -> Unit,
    onToggleReminder: (ScheduleSlot) -> Unit,
    onSendInstantReminder: (ScheduleSlot) -> Unit,
    onToggleCompleted: (ScheduleSlot) -> Unit,
    onDeleteSlot: (String) -> Unit,
    onOpenAddSlot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSlots = (scheduleState as? UiState.Success)?.data.orEmpty()
    val daySlots = remember(allSlots, selectedDay) {
        allSlots.filter { it.dayOfWeek == selectedDay }
            .sortedBy { MecChannels.parseTimeToMinutes(it.startTime) }
    }
    val weeklyGrouped = remember(allSlots) {
        MecChannels.daysOfWeek.mapNotNull { day ->
            val slotsForDay = allSlots
                .filter { it.dayOfWeek == day }
                .sortedBy { MecChannels.parseTimeToMinutes(it.startTime) }
            if (slotsForDay.isEmpty()) null else day to slotsForDay
        }
    }
    val completedCount = remember(allSlots) { allSlots.count { it.isCompleted } }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 760.dp

            if (scheduleState is UiState.Loading) {
                MecLoadingState()
            } else if (scheduleState is UiState.Error) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MecEmptyStateCard(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "Unable to sync schedule",
                        subtitle = scheduleState.message
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        ScheduleHeroHeader(
                            department = department,
                            semester = semester,
                            totalSlots = allSlots.size,
                            completedCount = completedCount,
                            viewMode = viewMode,
                            onSelectViewMode = onSelectViewMode
                        )
                    }

                    if (viewMode == "DAILY") {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(MecChannels.daysOfWeek) { day ->
                                    val count = allSlots.count { it.dayOfWeek == day }
                                    FilterChip(
                                        selected = selectedDay == day,
                                        onClick = { onSelectDay(day) },
                                        label = {
                                            Text(
                                                text = if (day == "EXAM") "Exams ($count)" else "$day ($count)",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (day == "EXAM") {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            selectedLabelColor = if (day == "EXAM") {
                                                MaterialTheme.colorScheme.onTertiary
                                            } else {
                                                MaterialTheme.colorScheme.onPrimary
                                            }
                                        ),
                                        modifier = Modifier.testTag("schedule_day_$day")
                                    )
                                }
                            }
                        }

                        item {
                            DailyRoutineBanner(
                                dayCode = selectedDay,
                                slots = daySlots
                            )
                        }

                        if (daySlots.isEmpty()) {
                            item {
                                MecEmptyStateCard(
                                    icon = Icons.Rounded.CalendarMonth,
                                    title = "No classes scheduled for ${MecChannels.dayFullName(selectedDay)}",
                                    subtitle = "Tap Add Class / Exam below or switch to Full Week to view other days."
                                )
                            }
                        } else if (isWide) {
                            val rows = daySlots.chunked(2)
                            items(rows) { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    pair.forEach { slot ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            DailyTimelineSlotRow(
                                                slot = slot,
                                                isLast = true,
                                                onToggleReminder = { onToggleReminder(slot) },
                                                onSendInstantReminder = { onSendInstantReminder(slot) },
                                                onToggleCompleted = { onToggleCompleted(slot) },
                                                onDelete = { onDeleteSlot(slot.id) }
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = daySlots,
                                key = { _, slot -> slot.id }
                            ) { index, slot ->
                                DailyTimelineSlotRow(
                                    slot = slot,
                                    isLast = index == daySlots.lastIndex,
                                    onToggleReminder = { onToggleReminder(slot) },
                                    onSendInstantReminder = { onSendInstantReminder(slot) },
                                    onToggleCompleted = { onToggleCompleted(slot) },
                                    onDelete = { onDeleteSlot(slot.id) }
                                )
                            }
                        }
                    } else {
                        if (weeklyGrouped.isEmpty()) {
                            item {
                                MecEmptyStateCard(
                                    icon = Icons.Rounded.CalendarViewWeek,
                                    title = "Your weekly routine is empty",
                                    subtitle = "Add classes, labs, assignments, or KTU exam dates to build your routine."
                                )
                            }
                        } else {
                            weeklyGrouped.forEach { (dayCode, slotsForDay) ->
                                item(key = "week_header_$dayCode") {
                                    DailyRoutineBanner(
                                        dayCode = dayCode,
                                        slots = slotsForDay,
                                        onFocusDay = {
                                            onSelectDay(dayCode)
                                            onSelectViewMode("DAILY")
                                        }
                                    )
                                }

                                itemsIndexed(
                                    items = slotsForDay,
                                    key = { _, slot -> "week_${dayCode}_${slot.id}" }
                                ) { index, slot ->
                                    DailyTimelineSlotRow(
                                        slot = slot,
                                        isLast = index == slotsForDay.lastIndex,
                                        onToggleReminder = { onToggleReminder(slot) },
                                        onSendInstantReminder = { onSendInstantReminder(slot) },
                                        onToggleCompleted = { onToggleCompleted(slot) },
                                        onDelete = { onDeleteSlot(slot.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onOpenAddSlot,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Add Class / Exam", style = MaterialTheme.typography.labelLarge) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_schedule_fab")
        )
    }
}

@Composable
private fun ScheduleHeroHeader(
    department: String,
    semester: String,
    totalSlots: Int,
    completedCount: Int,
    viewMode: String,
    onSelectViewMode: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MecTagPill(
                        text = "KTU TIMETABLE • $department $semester",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Academic Schedule Viewer",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Live weekly routine & exam tracker synced via Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$totalSlots",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$completedCount DONE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ViewModeTogglePill(
                    label = "Daily Routine",
                    icon = Icons.Rounded.CalendarViewDay,
                    selected = viewMode == "DAILY",
                    onClick = { onSelectViewMode("DAILY") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("schedule_view_daily")
                )
                ViewModeTogglePill(
                    label = "Full Week",
                    icon = Icons.Rounded.CalendarViewWeek,
                    selected = viewMode == "WEEKLY",
                    onClick = { onSelectViewMode("WEEKLY") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("schedule_view_weekly")
                )
            }
        }
    }
}

@Composable
private fun ViewModeTogglePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DailyRoutineBanner(
    dayCode: String,
    slots: List<ScheduleSlot>,
    onFocusDay: (() -> Unit)? = null
) {
    val firstTime = slots.firstOrNull()?.startTime
    val lastTime = slots.lastOrNull()?.endTime
    val timeWindow = if (firstTime != null && lastTime != null) "$firstTime – $lastTime" else "No active hours"
    val accent = if (dayCode == "EXAM") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

    Surface(
        onClick = { onFocusDay?.invoke() },
        enabled = onFocusDay != null,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = MecChannels.dayFullName(dayCode),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeWindow,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            MecTagPill(
                text = "${slots.size} ${if (slots.size == 1) "session" else "sessions"}",
                accentColor = accent
            )
        }
    }
}

@Composable
private fun DailyTimelineSlotRow(
    slot: ScheduleSlot,
    isLast: Boolean,
    onToggleReminder: () -> Unit,
    onSendInstantReminder: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit
) {
    val accent: Color = when (slot.slotType) {
        "Exam" -> MaterialTheme.colorScheme.error
        "Lab" -> MaterialTheme.colorScheme.secondary
        "Assignment" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val typeIcon: ImageVector = when (slot.slotType) {
        "Exam" -> Icons.Rounded.Quiz
        "Lab" -> Icons.Rounded.Science
        "Assignment" -> Icons.AutoMirrored.Rounded.Assignment
        else -> Icons.Rounded.School
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (slot.isCompleted) 0.72f else 1f)
            .testTag("schedule_slot_card_${slot.id}")
    ) {
        // Formatted Left Time Rail + Timeline Connector
        Column(
            modifier = Modifier
                .width(82.dp)
                .fillMaxHeight()
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = slot.startTime,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = slot.endTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(top = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Right Formatted Routine Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MecTagPill(text = slot.courseCode, accentColor = accent)
                        Surface(
                            color = accent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = typeIcon,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = slot.slotType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(onClick = onToggleCompleted) {
                            Icon(
                                imageVector = if (slot.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = "Mark complete",
                                tint = if (slot.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete slot",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = slot.courseTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (slot.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (slot.room.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = slot.room,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (slot.facultyName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = slot.facultyName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            onToggleReminder()
                            if (!slot.reminderEnabled) {
                                onSendInstantReminder()
                            }
                        },
                        color = if (slot.reminderEnabled) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (slot.reminderEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                                contentDescription = "Toggle reminder",
                                tint = if (slot.reminderEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (slot.reminderEnabled) "Alert On" else "Muted",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (slot.reminderEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        onClick = onSendInstantReminder,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ping Alert",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
