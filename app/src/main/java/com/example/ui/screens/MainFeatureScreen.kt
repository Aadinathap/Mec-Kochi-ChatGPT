package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.model.StudentProfile
import com.example.data.repository.MecCampusRepository
import com.example.data.storage.ChatImageStorageManager
import com.example.data.storage.ResourcePdfDownloader
import com.example.notifications.CampusNotificationHelper
import com.example.ui.auth.GoogleSignInHelper
import com.example.ui.components.MecLoadingState
import com.example.ui.components.StudentAvatarBadge
import com.example.ui.theme.departmentAccentColor
import com.example.ui.viewmodel.MecCampusViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_PULSE = "pulse"
const val ROUTE_CHAT = "chat"
const val ROUTE_EVENTS = "events"
const val ROUTE_RESOURCES = "resources"
const val ROUTE_SCHEDULE = "schedule"
const val ROUTE_PROFILE = "profile"

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val campusTabs = listOf(
    NavTab(ROUTE_PULSE, "Pulse", Icons.Rounded.Home, Icons.Outlined.Home),
    NavTab(ROUTE_CHAT, "Chat", Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Outlined.Chat),
    NavTab(ROUTE_EVENTS, "Events", Icons.Rounded.EventAvailable, Icons.Outlined.EventAvailable),
    NavTab(ROUTE_RESOURCES, "Library", Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    NavTab(ROUTE_SCHEDULE, "Schedule", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeatureScreen(
    currentUserId: String,
    initialDisplayName: String,
    userEmail: String = "",
    isDarkTheme: Boolean = false,
    userOverrodeTheme: Boolean = false,
    pendingRoute: String? = null,
    onConsumePendingRoute: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onSetTheme: (enabled: Boolean, fromUser: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MecCampusViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MecCampusViewModel(
                    repository = MecCampusRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                    chatImageUploader = { channelId, uid, uri, onProgress ->
                        ChatImageStorageManager.compressAndUploadChatImage(
                            context = app,
                            channelId = channelId,
                            userId = uid,
                            imageUri = uri,
                            onProgress = onProgress
                        )
                    },
                    resourcePdfDownloader = { resource, onProgress ->
                        ResourcePdfDownloader.downloadAndSavePdf(
                            context = app,
                            resource = resource,
                            onProgress = onProgress
                        )
                    }
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val chatState by viewModel.chatMessagesState.collectAsStateWithLifecycle()
    val typingStatusesState by viewModel.typingStatusesState.collectAsStateWithLifecycle()
    val eventsState by viewModel.eventsState.collectAsStateWithLifecycle()
    val rsvpsState by viewModel.rsvpsState.collectAsStateWithLifecycle()
    val resourcesState by viewModel.resourcesState.collectAsStateWithLifecycle()
    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()

    val selectedChannelId by viewModel.selectedChannelId.collectAsStateWithLifecycle()
    val selectedEventCategory by viewModel.selectedEventCategory.collectAsStateWithLifecycle()
    val eventsViewMode by viewModel.eventsViewMode.collectAsStateWithLifecycle()
    val calendarYear by viewModel.calendarYear.collectAsStateWithLifecycle()
    val calendarMonth by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val selectedCalendarDate by viewModel.selectedCalendarDate.collectAsStateWithLifecycle()
    val selectedEventId by viewModel.selectedEventId.collectAsStateWithLifecycle()
    val selectedResourceDept by viewModel.selectedResourceDept.collectAsStateWithLifecycle()
    val selectedResourceSem by viewModel.selectedResourceSemester.collectAsStateWithLifecycle()
    val selectedResourceType by viewModel.selectedResourceType.collectAsStateWithLifecycle()
    val resourceSearchQuery by viewModel.resourceSearchQuery.collectAsStateWithLifecycle()
    val selectedScheduleDay by viewModel.selectedScheduleDay.collectAsStateWithLifecycle()
    val scheduleViewMode by viewModel.scheduleViewMode.collectAsStateWithLifecycle()
    val unreadAlertCount by viewModel.unreadAlertCount.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isUploadingChatImage by viewModel.isUploadingChatImage.collectAsStateWithLifecycle()
    val chatImageUploadProgress by viewModel.chatImageUploadProgress.collectAsStateWithLifecycle()
    val downloadingResourceIds by viewModel.downloadingResourceIds.collectAsStateWithLifecycle()
    val resourceDownloadProgress by viewModel.resourceDownloadProgress.collectAsStateWithLifecycle()
    val downloadedResources by viewModel.downloadedResources.collectAsStateWithLifecycle()
    val fcmSyncState by viewModel.fcmSyncState.collectAsStateWithLifecycle()

    var hasNotifPermission by remember {
        mutableStateOf(CampusNotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) {
            val profile = (profileState as? UiState.Success)?.data
            viewModel.refreshFcmSync(
                department = profile?.department ?: "CSE",
                notificationsEnabled = profile?.notificationsEnabled ?: true
            )
        }
    }

    LaunchedEffect(Unit) {
        CampusNotificationHelper.ensureChannel(context)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.newLiveAlertEvents.collect { alert ->
            CampusNotificationHelper.showCampusAlertNotification(context, alert)
        }
    }

    val loadedProfile = (profileState as? UiState.Success)?.data
    var hasSyncedInitialProfileTheme by remember(currentUserId) { mutableStateOf(false) }
    LaunchedEffect(loadedProfile?.darkThemeEnabled) {
        if (loadedProfile != null && !hasSyncedInitialProfileTheme) {
            hasSyncedInitialProfileTheme = true
            if (!userOverrodeTheme && loadedProfile.darkThemeEnabled != isDarkTheme) {
                onSetTheme(loadedProfile.darkThemeEnabled, false)
            }
        }
    }

    val activeProfile = (loadedProfile ?: StudentProfile(
        userId = currentUserId,
        displayName = initialDisplayName.ifBlank { "MEC Student" },
        department = "CSE",
        semester = "S5",
        notificationsEnabled = true,
        darkThemeEnabled = isDarkTheme
    )).copy(darkThemeEnabled = isDarkTheme)

    var showProfileSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showCreateEventSheet by remember { mutableStateOf(false) }
    var createEventInitialDateLabel by remember { mutableStateOf<String?>(null) }
    var showCreateResourceSheet by remember { mutableStateOf(false) }
    var showAddSlotSheet by remember { mutableStateOf(false) }

    val needsOnboarding = profileState is UiState.Success && loadedProfile == null

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_PULSE

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingRoute) {
        val target = pendingRoute ?: return@LaunchedEffect
        when (target) {
            ROUTE_EVENTS, ROUTE_CHAT, ROUTE_RESOURCES, ROUTE_SCHEDULE, ROUTE_PROFILE -> {
                navigateToTab(target)
            }
            ROUTE_PULSE -> {
                navigateToTab(ROUTE_PULSE)
                viewModel.markAlertsSeen()
                showNotificationsSheet = true
            }
        }
        onConsumePendingRoute()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.systemBars,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    val isProfileScreen = currentRoute == ROUTE_PROFILE
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        navigationIcon = {
                            if (isProfileScreen) {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.testTag("profile_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        },
                        title = {
                            if (isProfileScreen) {
                                Text(
                                    text = stringResource(R.string.profile_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "MEC Connect",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${activeProfile.department} • ${activeProfile.semester}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = departmentAccentColor(activeProfile.department)
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    viewModel.markAlertsSeen()
                                    showNotificationsSheet = true
                                },
                                modifier = Modifier.testTag("notification_bell_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadAlertCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            ) {
                                                Text(unreadAlertCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Notifications,
                                        contentDescription = "Campus Notifications"
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val nextDark = !isDarkTheme
                                    onToggleTheme()
                                    if (loadedProfile != null) {
                                        viewModel.setThemePreference(loadedProfile, nextDark)
                                    }
                                },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode"
                                )
                            }

                            if (!isProfileScreen) {
                                IconButton(
                                    onClick = {
                                        navController.navigate(ROUTE_PROFILE) {
                                            launchSingleTop = true
                                        }
                                    },
                                    modifier = Modifier.testTag("profile_menu_button")
                                ) {
                                    StudentAvatarBadge(
                                        name = activeProfile.displayName,
                                        accentColor = departmentAccentColor(activeProfile.department),
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useRail && currentRoute != ROUTE_PROFILE) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            campusTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                @OptIn(ExperimentalLayoutApi::class)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.size(12.dp))
                            campusTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("rail_tab_${tab.route}")
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            val profileSelected = currentRoute == ROUTE_PROFILE
                            NavigationRailItem(
                                selected = profileSelected,
                                onClick = { navigateToTab(ROUTE_PROFILE) },
                                icon = {
                                    Icon(
                                        imageVector = if (profileSelected) Icons.Rounded.Person else Icons.Outlined.Person,
                                        contentDescription = stringResource(R.string.profile_title)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.profile_title),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("rail_tab_profile")
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                        }
                    }

                    if (profileState is UiState.Loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            MecLoadingState()
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_PULSE,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            composable(ROUTE_PULSE) {
                                PulseHomeScreen(
                                    profile = activeProfile,
                                    chatState = chatState,
                                    eventsState = eventsState,
                                    resourcesState = resourcesState,
                                    scheduleState = scheduleState,
                                    alertsState = alertsState,
                                    hasSystemNotificationPermission = hasNotifPermission,
                                    onRequestNotificationPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    onOpenNotifications = {
                                        viewModel.markAlertsSeen()
                                        showNotificationsSheet = true
                                    },
                                    onOpenBroadcastAlert = { showNotificationsSheet = true },
                                    onNavigateToTab = navigateToTab
                                )
                            }

                            composable(ROUTE_CHAT) {
                                CampusChatScreen(
                                    currentUserId = currentUserId,
                                    selectedChannelId = selectedChannelId,
                                    chatState = chatState,
                                    typingStatuses = (typingStatusesState as? UiState.Success)?.data.orEmpty(),
                                    isUploadingImage = isUploadingChatImage,
                                    imageUploadProgress = chatImageUploadProgress,
                                    onSelectChannel = viewModel::selectChannel,
                                    onSendMessage = viewModel::sendChatMessage,
                                    onSendCameraImage = viewModel::sendCameraImageMessage,
                                    onDeleteMessage = viewModel::deleteChatMessage,
                                    onMarkMessagesRead = viewModel::markChannelMessagesAsRead,
                                    onTypingInputChanged = viewModel::onChatComposerTextChanged,
                                    onClearTyping = { viewModel.clearTypingStatus() },
                                    onShowError = viewModel::emitMessage
                                )
                            }

                            composable(ROUTE_EVENTS) {
                                CampusEventsScreen(
                                    eventsState = eventsState,
                                    rsvpsState = rsvpsState,
                                    selectedCategory = selectedEventCategory,
                                    viewMode = eventsViewMode,
                                    calendarYear = calendarYear,
                                    calendarMonth = calendarMonth,
                                    selectedCalendarDate = selectedCalendarDate,
                                    selectedEventId = selectedEventId,
                                    onSelectCategory = viewModel::selectEventCategory,
                                    onSelectViewMode = viewModel::selectEventsViewMode,
                                    onSelectCalendarDate = viewModel::selectCalendarDate,
                                    onChangeCalendarMonth = viewModel::changeCalendarMonth,
                                    onJumpToToday = viewModel::jumpCalendarToToday,
                                    onSelectEvent = viewModel::selectEventForDetails,
                                    onToggleRsvp = viewModel::toggleEventRsvp,
                                    onOpenCreateEvent = { initialDate ->
                                        createEventInitialDateLabel = initialDate?.toDisplayDate()
                                        showCreateEventSheet = true
                                    }
                                )
                            }

                            composable(ROUTE_RESOURCES) {
                                DepartmentResourcesScreen(
                                    resourcesState = resourcesState,
                                    selectedDept = selectedResourceDept,
                                    selectedSemester = selectedResourceSem,
                                    selectedType = selectedResourceType,
                                    searchQuery = resourceSearchQuery,
                                    downloadingResourceIds = downloadingResourceIds,
                                    resourceDownloadProgress = resourceDownloadProgress,
                                    downloadedResources = downloadedResources,
                                    onSelectDept = viewModel::selectResourceDepartment,
                                    onSelectSemester = viewModel::selectResourceSemester,
                                    onSelectType = viewModel::selectResourceType,
                                    onSearchQueryChange = viewModel::updateResourceSearchQuery,
                                    onUpvote = viewModel::upvoteResource,
                                    onDownloadResource = viewModel::downloadDepartmentResource,
                                    onOpenDownloadedPdf = { info ->
                                        if (!ResourcePdfDownloader.openDownloadedPdf(context, info)) {
                                            viewModel.emitMessage(
                                                context.getString(R.string.resource_no_pdf_viewer, info.displayLocation)
                                            )
                                        }
                                    },
                                    onOpenAddResource = { showCreateResourceSheet = true }
                                )
                            }

                            composable(ROUTE_SCHEDULE) {
                                AcademicScheduleScreen(
                                    scheduleState = scheduleState,
                                    selectedDay = selectedScheduleDay,
                                    viewMode = scheduleViewMode,
                                    department = activeProfile.department,
                                    semester = activeProfile.semester,
                                    onSelectDay = viewModel::selectScheduleDay,
                                    onSelectViewMode = viewModel::selectScheduleViewMode,
                                    onToggleReminder = viewModel::toggleSlotReminder,
                                    onSendInstantReminder = { slot ->
                                        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            CampusNotificationHelper.showScheduleReminderNotification(
                                                context = context,
                                                courseCode = slot.courseCode,
                                                title = slot.courseTitle,
                                                room = slot.room.ifBlank { "MEC Campus" },
                                                time = "${slot.startTime} – ${slot.endTime}"
                                            )
                                        }
                                    },
                                    onToggleCompleted = viewModel::toggleSlotCompleted,
                                    onDeleteSlot = viewModel::deleteScheduleSlot,
                                    onOpenAddSlot = { showAddSlotSheet = true }
                                )
                            }

                            composable(ROUTE_PROFILE) {
                                ProfileScreen(
                                    profile = activeProfile,
                                    userEmail = userEmail,
                                    isDarkTheme = isDarkTheme,
                                    fcmSyncState = fcmSyncState,
                                    onToggleTheme = {
                                        val nextDark = !isDarkTheme
                                        onToggleTheme()
                                        if (loadedProfile != null) {
                                            viewModel.setThemePreference(loadedProfile, nextDark)
                                        }
                                    },
                                    onToggleNotifications = {
                                        val targetProfile = loadedProfile ?: activeProfile
                                        viewModel.toggleNotificationsPreference(targetProfile)
                                    },
                                    onRefreshFcm = {
                                        viewModel.refreshFcmSync(
                                            department = activeProfile.department,
                                            notificationsEnabled = activeProfile.notificationsEnabled
                                        )
                                    },
                                    onEditProfile = { showProfileSheet = true },
                                    onSignOut = {
                                        GoogleSignInHelper.signOut(credentialManager, scope)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (needsOnboarding || showProfileSheet) {
                StudentOnboardingSheet(
                    initialName = initialDisplayName,
                    existingProfile = loadedProfile?.copy(darkThemeEnabled = isDarkTheme),
                    isSaving = isSaving,
                    isDarkTheme = isDarkTheme,
                    onSave = { name, dept, sem, notifs, dark ->
                        onSetTheme(dark, true)
                        viewModel.saveStudentOnboarding(name, dept, sem, notifs, dark)
                        showProfileSheet = false
                        if (notifs && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onDismiss = if (needsOnboarding) null else { { showProfileSheet = false } }
                )
            }

            if (showNotificationsSheet) {
                NotificationCenterSheet(
                    alertsState = alertsState,
                    studentDepartment = activeProfile.department,
                    fcmSyncState = fcmSyncState,
                    onRefreshFcm = {
                        viewModel.refreshFcmSync(
                            department = activeProfile.department,
                            notificationsEnabled = activeProfile.notificationsEnabled
                        )
                    },
                    onBroadcastAlert = { title, message, cat, dept ->
                        viewModel.postCampusAlert(title, message, cat, dept)
                    },
                    onDismiss = { showNotificationsSheet = false }
                )
            }

            if (showCreateEventSheet) {
                CreateEventSheet(
                    initialDateLabel = createEventInitialDateLabel ?: selectedCalendarDate.toDisplayDate(),
                    onCreate = viewModel::createCampusEvent,
                    onDismiss = {
                        showCreateEventSheet = false
                        createEventInitialDateLabel = null
                    }
                )
            }

            if (showCreateResourceSheet) {
                CreateResourceSheet(
                    defaultDept = activeProfile.department,
                    defaultSem = activeProfile.semester,
                    onCreate = viewModel::addDepartmentResource,
                    onDismiss = { showCreateResourceSheet = false }
                )
            }

            if (showAddSlotSheet) {
                AddScheduleSlotSheet(
                    defaultDay = selectedScheduleDay,
                    onAdd = viewModel::addScheduleSlot,
                    onDismiss = { showAddSlotSheet = false }
                )
            }
        }
}
