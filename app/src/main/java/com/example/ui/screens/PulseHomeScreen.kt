package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CampusAlert
import com.example.data.model.CampusEvent
import com.example.data.model.ChatMessage
import com.example.data.model.DepartmentResource
import com.example.data.model.ScheduleSlot
import com.example.data.model.StudentProfile
import com.example.ui.components.MecEmptyStateCard
import com.example.ui.components.MecTagPill
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StudentAvatarBadge
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.departmentAccentColor
import com.example.ui.viewmodel.UiState

@Composable
fun PulseHomeScreen(
    profile: StudentProfile,
    chatState: UiState<List<ChatMessage>>,
    eventsState: UiState<List<CampusEvent>>,
    resourcesState: UiState<List<DepartmentResource>>,
    scheduleState: UiState<List<ScheduleSlot>>,
    alertsState: UiState<List<CampusAlert>>,
    hasSystemNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBroadcastAlert: () -> Unit,
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val events = (eventsState as? UiState.Success)?.data.orEmpty()
    val alerts = (alertsState as? UiState.Success)?.data.orEmpty()
    val schedule = (scheduleState as? UiState.Success)?.data.orEmpty()
    val messages = (chatState as? UiState.Success)?.data.orEmpty()
    val resources = (resourcesState as? UiState.Success)?.data.orEmpty()

    val deptAccent = departmentAccentColor(profile.department)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Atmospheric MEC Hero Card
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_mec_hero_banner),
                            contentDescription = "MEC Kochi Campus",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(206.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(206.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF060B18).copy(alpha = 0.45f),
                                            Color(0xFF060B18).copy(alpha = 0.88f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MecTagPill(
                                    text = "${profile.department} • ${profile.semester} • MEC KOCHI",
                                    accentColor = deptAccent
                                )
                                Surface(
                                    color = Color.White.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "LIVE SYNC",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            Text(
                                text = "Hey ${profile.displayName.substringBefore(" ")},",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Thrikkakara campus pulse is live.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.82f)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                QuickStatChip(
                                    label = "Classes",
                                    value = schedule.size.toString(),
                                    accent = MaterialTheme.colorScheme.primary,
                                    onClick = { onNavigateToTab("schedule") },
                                    modifier = Modifier.weight(1f)
                                )
                                QuickStatChip(
                                    label = "Events",
                                    value = events.size.toString(),
                                    accent = MaterialTheme.colorScheme.tertiary,
                                    onClick = { onNavigateToTab("events") },
                                    modifier = Modifier.weight(1f)
                                )
                                QuickStatChip(
                                    label = "Notes",
                                    value = resources.size.toString(),
                                    accent = MaterialTheme.colorScheme.secondary,
                                    onClick = { onNavigateToTab("resources") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Real-time Notification Permission Banner (if not granted on Android 13+)
            if (!hasSystemNotificationPermission) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Real-time Push Alerts",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Get instant Android alerts for KTU exam updates, placement drives, and class reminders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRequestNotificationPermission,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                ),
                                modifier = Modifier.testTag("enable_notifications_button")
                            ) {
                                Text("Enable", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // 3. Quick Launch Action Strip
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        QuickActionButton(
                            icon = Icons.Rounded.Campaign,
                            label = "Broadcast Alert",
                            accent = MaterialTheme.colorScheme.tertiary,
                            onClick = onOpenBroadcastAlert,
                            tag = "quick_broadcast_alert"
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            label = "Open ${profile.department} Chat",
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToTab("chat") },
                            tag = "quick_open_chat"
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            label = "KTU Notes",
                            accent = MaterialTheme.colorScheme.secondary,
                            onClick = { onNavigateToTab("resources") },
                            tag = "quick_open_notes"
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.Rounded.CalendarMonth,
                            label = "My Timetable",
                            accent = departmentAccentColor(profile.department),
                            onClick = { onNavigateToTab("schedule") },
                            tag = "quick_open_schedule"
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.Rounded.Person,
                            label = "My Profile",
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToTab("profile") },
                            tag = "quick_open_profile"
                        )
                    }
                }
            }

            // 4. Responsive Grid for Alerts + Up Next Schedule
            item {
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeaderRow(
                                title = "Live Campus Alerts",
                                subtitle = "Real-time announcements",
                                actionLabel = "View All",
                                onActionClick = onOpenNotifications
                            )
                            AlertsPreviewBlock(alerts = alerts.take(3), onOpenNotifications = onOpenNotifications)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeaderRow(
                                title = "Upcoming Schedule",
                                subtitle = "Your ${profile.department} timetable",
                                actionLabel = "Timetable",
                                onActionClick = { onNavigateToTab("schedule") }
                            )
                            SchedulePreviewBlock(schedule = schedule.take(3), onOpenSchedule = { onNavigateToTab("schedule") })
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeaderRow(
                            title = "Live Campus Alerts",
                            subtitle = "Real-time notifications for MEC students",
                            actionLabel = "View All",
                            onActionClick = onOpenNotifications
                        )
                        AlertsPreviewBlock(alerts = alerts.take(2), onOpenNotifications = onOpenNotifications)
                    }
                }
            }

            if (!isWide) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeaderRow(
                            title = "Academic Radar",
                            subtitle = "Classes, labs & KTU deadlines",
                            actionLabel = "Full Schedule",
                            onActionClick = { onNavigateToTab("schedule") }
                        )
                        SchedulePreviewBlock(schedule = schedule.take(3), onOpenSchedule = { onNavigateToTab("schedule") })
                    }
                }
            }

            // 5. Featured Excel / Campus Event Spotlight
            item {
                SectionHeaderRow(
                    title = "Spotlight at MEC",
                    subtitle = "Clubs, Excel Fest & Placements",
                    actionLabel = "All Events",
                    onActionClick = { onNavigateToTab("events") }
                )
            }

            if (events.isEmpty()) {
                item {
                    MecEmptyStateCard(
                        icon = Icons.Rounded.EventAvailable,
                        title = "No campus events yet",
                        subtitle = "Host a hackathon, club session, or workshop from the Events tab."
                    )
                }
            } else {
                val featured = events.first()
                item {
                    Surface(
                        onClick = { onNavigateToTab("events") },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(132.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_excel_fest_banner),
                                    contentDescription = featured.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color(0xFF070B16).copy(alpha = 0.85f)
                                                )
                                            )
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MecTagPill(
                                        text = featured.category,
                                        accentColor = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${featured.rsvpCount} Going",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = featured.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${featured.venue} • ${featured.dateLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Latest Peer Chat Activity
            if (messages.isNotEmpty()) {
                item {
                    val latest = messages.last()
                    Surface(
                        onClick = { onNavigateToTab("chat") },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Latest in #${latest.channelId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${latest.senderName}: ${latest.content}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Go to Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 7. Creators Section
            item {
                CreatorsSectionCard()
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun CreatorsSectionCard() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeaderRow(
            title = stringResource(R.string.creators_section_title),
            subtitle = "Model Engineering College, Kochi"
        )
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("creators_section")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CreatorCreditRow(
                    badgeName = "AADINATH AP",
                    roleTitle = "CEO",
                    fullCredit = stringResource(R.string.creator_ceo_label),
                    accentColor = MaterialTheme.colorScheme.primary
                )
                CreatorCreditRow(
                    badgeName = "ABIL SAJEE",
                    roleTitle = "MANAGER",
                    fullCredit = stringResource(R.string.creator_manager_label),
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun CreatorCreditRow(
    badgeName: String,
    roleTitle: String,
    fullCredit: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            StudentAvatarBadge(
                name = badgeName,
                accentColor = accentColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = fullCredit,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "MEC Connect Leadership",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        MecTagPill(text = roleTitle, accentColor = accentColor)
    }
}

@Composable
private fun QuickStatChip(
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF0A1122).copy(alpha = 0.78f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AlertsPreviewBlock(
    alerts: List<CampusAlert>,
    onOpenNotifications: () -> Unit
) {
    if (alerts.isEmpty()) {
        MecEmptyStateCard(
            icon = Icons.Rounded.NotificationsActive,
            title = "All caught up",
            subtitle = "Real-time campus, exam, and placement alerts will appear here."
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            alerts.forEach { alert ->
                Surface(
                    onClick = onOpenNotifications,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${alert.category} • ${alert.department}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = formatRelativeTime(alert.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = alert.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedulePreviewBlock(
    schedule: List<ScheduleSlot>,
    onOpenSchedule: () -> Unit
) {
    if (schedule.isEmpty()) {
        MecEmptyStateCard(
            icon = Icons.Rounded.Schedule,
            title = "Timetable is clear",
            subtitle = "Add your KTU lectures, labs, or exam reminders in the Schedule tab."
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            schedule.forEach { slot ->
                Surface(
                    onClick = onOpenSchedule,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = slot.dayOfWeek,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = slot.courseCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = slot.courseTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${slot.startTime} – ${slot.endTime} • ${slot.room}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
