package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CampusAlert
import com.example.data.model.CampusEventDateParser
import com.example.data.model.MecChannels
import com.example.data.model.MecDepartment
import com.example.data.model.StudentProfile
import com.example.notifications.FcmSyncState
import com.example.ui.components.MecEmptyStateCard
import com.example.ui.components.MecTagPill
import com.example.ui.components.formatRelativeTime
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentOnboardingSheet(
    initialName: String,
    existingProfile: StudentProfile?,
    isSaving: Boolean,
    isDarkTheme: Boolean,
    onSave: (name: String, dept: String, sem: String, notifications: Boolean, darkTheme: Boolean) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    var name by remember(initialName, existingProfile) {
        mutableStateOf(existingProfile?.displayName?.ifBlank { initialName } ?: initialName)
    }
    var dept by remember(existingProfile) { mutableStateOf(existingProfile?.department ?: "CSE") }
    var sem by remember(existingProfile) { mutableStateOf(existingProfile?.semester ?: "S5") }
    var notifications by remember(existingProfile) {
        mutableStateOf(existingProfile?.notificationsEnabled ?: true)
    }
    var darkTheme by remember(existingProfile, isDarkTheme) {
        mutableStateOf(existingProfile?.darkThemeEnabled ?: isDarkTheme)
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss?.invoke() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existingProfile == null) "Complete Your MEC Profile" else "Edit MEC Profile",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Personalize your KTU timetable, department channels, and real-time student alerts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_name_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MEC Department", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MecDepartment.entries.forEach { entry ->
                        FilterChip(
                            selected = dept == entry.code,
                            onClick = { dept = entry.code },
                            label = { Text(entry.code) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("KTU Semester", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MecChannels.semesters.forEach { s ->
                        FilterChip(
                            selected = sem == s,
                            onClick = { sem = s },
                            label = { Text(s) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Real-time Student Notifications", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Receive live alerts for exams, placements & classes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = notifications, onCheckedChange = { notifications = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Midnight Kochi Dark Theme", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Switch between dark and daylight campus themes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = darkTheme, onCheckedChange = { darkTheme = it })
            }

            Button(
                onClick = { onSave(name, dept, sem, notifications, darkTheme) },
                enabled = !isSaving && name.trim().length >= 2,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_profile_button")
            ) {
                Text(if (isSaving) "Saving..." else "Save & Sync Campus")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationCenterSheet(
    alertsState: UiState<List<CampusAlert>>,
    studentDepartment: String,
    onBroadcastAlert: (title: String, message: String, category: String, dept: String) -> Unit,
    onDismiss: () -> Unit,
    fcmSyncState: FcmSyncState = FcmSyncState(),
    onRefreshFcm: () -> Unit = {}
) {
    val alerts = (alertsState as? UiState.Success)?.data.orEmpty()
    var showComposer by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Academic") }
    var dept by remember { mutableStateOf("ALL") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MEC Notification Center", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Live alerts & FCM push for $studentDepartment",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showComposer = !showComposer }) {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showComposer) "Close" else "New Alert")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fcmSyncState.statusMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (fcmSyncState.subscribedTopics.isNotEmpty()) {
                            Text(
                                text = fcmSyncState.subscribedTopics.joinToString(" • ") { "#$it" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onRefreshFcm, enabled = !fcmSyncState.isSyncing) {
                        Text(if (fcmSyncState.isSyncing) "..." else "Sync")
                    }
                }
            }

            if (showComposer) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Alert Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("alert_title_input")
                        )
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Details for MEC students") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("alert_message_input")
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MecChannels.alertCategories.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("ALL") + MecDepartment.entries.map { it.code }).forEach { d ->
                                FilterChip(
                                    selected = dept == d,
                                    onClick = { dept = d },
                                    label = { Text(d) }
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onBroadcastAlert(title, message, category, dept)
                                title = ""
                                message = ""
                                showComposer = false
                            },
                            enabled = title.trim().length >= 3 && message.trim().length >= 3,
                            modifier = Modifier.fillMaxWidth().testTag("send_alert_button")
                        ) {
                            Text("Broadcast Live Notification")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (alerts.isEmpty()) {
                MecEmptyStateCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "No campus alerts yet",
                    subtitle = "Broadcast an announcement or wait for updates from MEC cells."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MecTagPill(alert.category, MaterialTheme.colorScheme.tertiary)
                                        MecTagPill(alert.department, MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        text = formatRelativeTime(alert.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    alert.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "From ${alert.senderName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateEventSheet(
    initialDateLabel: String = CampusEventDateParser.today().toDisplayDate(),
    onCreate: (title: String, organizer: String, category: String, venue: String, date: String, time: String, desc: String, dept: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var organizer by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Workshop") }
    var venue by remember { mutableStateOf("") }
    var dateLabel by remember(initialDateLabel) { mutableStateOf(initialDateLabel) }
    var timeLabel by remember { mutableStateOf("04:15 PM") }
    var description by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("ALL") }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Host MEC Campus Event", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Event Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = organizer, onValueChange = { organizer = it }, label = { Text("Club / Organizer (e.g. IEEE MEC)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MecChannels.eventCategories.forEach { cat ->
                    FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                }
            }
            OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("Campus Venue") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = dateLabel,
                    onValueChange = { dateLabel = it },
                    label = { Text("Date") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.testTag("pick_event_date_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = "Pick event date"
                            )
                        }
                    },
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(value = timeLabel, onValueChange = { timeLabel = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("ALL") + MecDepartment.entries.map { it.code }).forEach { d ->
                    FilterChip(selected = dept == d, onClick = { dept = d }, label = { Text(d) })
                }
            }
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Event Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    onCreate(title, organizer, category, venue, dateLabel, timeLabel, description, dept)
                    onDismiss()
                },
                enabled = title.trim().length >= 3 && organizer.trim().length >= 2 && venue.trim().length >= 2 && description.trim().length >= 5,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("publish_event_button")
            ) {
                Text("Publish Event")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dateLabel = CampusEventDateParser.formatDateMillisToLabel(millis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Use Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateResourceSheet(
    defaultDept: String,
    defaultSem: String,
    onCreate: (title: String, dept: String, sem: String, code: String, type: String, desc: String, link: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf(defaultDept) }
    var sem by remember { mutableStateOf(defaultSem) }
    var code by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("PDF") }
    var desc by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Share Department Resource", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Resource Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("KTU Subject Code (e.g. CST301)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MecDepartment.entries.forEach { d ->
                    FilterChip(selected = dept == d.code, onClick = { dept = d.code }, label = { Text(d.code) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MecChannels.semesters.forEach { s ->
                    FilterChip(selected = sem == s, onClick = { sem = s }, label = { Text(s) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MecChannels.resourceTypes.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                }
            }
            OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("PDF URL / Link / Shelf Reference") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Modules / Topics Covered") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    onCreate(title, dept, sem, code, type, desc, link)
                    onDismiss()
                },
                enabled = title.trim().length >= 3 && code.trim().length >= 2 && desc.trim().length >= 3 && link.trim().length >= 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("publish_resource_button")
            ) {
                Text("Share with Department")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddScheduleSlotSheet(
    defaultDay: String,
    onAdd: (code: String, title: String, faculty: String, room: String, day: String, start: String, end: String, type: String, reminder: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var faculty by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(defaultDay) }
    var start by remember { mutableStateOf("09:30 AM") }
    var end by remember { mutableStateOf("10:25 AM") }
    var type by remember { mutableStateOf("Lecture") }
    var reminder by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Timetable / Exam Slot", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Course Code") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room / Hall") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Course / Exam Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = faculty, onValueChange = { faculty = it }, label = { Text("Faculty Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MecChannels.daysOfWeek.forEach { d ->
                    FilterChip(selected = day == d, onClick = { day = d }, label = { Text(d) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MecChannels.slotTypes.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Reminder Notification", style = MaterialTheme.typography.titleSmall)
                Switch(checked = reminder, onCheckedChange = { reminder = it })
            }
            Button(
                onClick = {
                    onAdd(code, title, faculty, room, day, start, end, type, reminder)
                    onDismiss()
                },
                enabled = code.trim().isNotEmpty() && title.trim().length >= 2 && start.trim().isNotEmpty() && end.trim().isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_schedule_slot_button")
            ) {
                Text("Add to Schedule")
            }
        }
    }
}
