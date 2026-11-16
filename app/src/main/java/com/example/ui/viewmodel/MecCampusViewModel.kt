package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CalendarDayKey
import com.example.data.model.CampusAlert
import com.example.data.model.CampusEvent
import com.example.data.model.CampusEventDateParser
import com.example.data.model.ChatMessage
import com.example.data.model.ChatTypingStatus
import com.example.data.model.DepartmentResource
import com.example.data.model.EventRsvp
import com.example.data.model.ScheduleSlot
import com.example.data.model.StudentProfile
import com.example.data.repository.MecCampusRepository
import com.example.data.storage.DownloadedPdfInfo
import com.example.data.storage.ResourcePdfDownloader
import com.example.notifications.FcmPushManager
import com.example.notifications.FcmSyncState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MecCampusViewModel(
    private val repository: MecCampusRepository,
    val currentUserId: String,
    initialDisplayName: String,
    private val fcmSyncHandler: suspend (department: String, notificationsEnabled: Boolean) -> FcmSyncState = { dept, enabled ->
        FcmPushManager.syncPushSubscription(dept, enabled, repository)
    },
    private val chatImageUploader: (suspend (channelId: String, userId: String, imageUri: Uri, onProgress: (Float) -> Unit) -> String)? = null,
    private val resourcePdfDownloader: (suspend (resource: DepartmentResource, onProgress: (Float) -> Unit) -> DownloadedPdfInfo)? = null
) : ViewModel() {

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val profileState: StateFlow<UiState<StudentProfile?>> =
        repository.observeProfile(currentUserId).toUiState("student profile")

    val chatMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        repository.observeChatMessages().toUiState("chat messages")

    val typingStatusesState: StateFlow<UiState<List<ChatTypingStatus>>> =
        repository.observeChatTypingStatuses().toUiState("typing indicators")

    val eventsState: StateFlow<UiState<List<CampusEvent>>> =
        repository.observeCampusEvents().toUiState("campus events")

    val rsvpsState: StateFlow<UiState<List<EventRsvp>>> =
        repository.observeUserRsvps(currentUserId).toUiState("event RSVPs")

    val resourcesState: StateFlow<UiState<List<DepartmentResource>>> =
        repository.observeDepartmentResources().toUiState("department resources")

    val scheduleState: StateFlow<UiState<List<ScheduleSlot>>> =
        repository.observeAcademicSchedule(currentUserId).toUiState("academic schedule")

    val alertsState: StateFlow<UiState<List<CampusAlert>>> =
        repository.observeCampusAlerts().toUiState("campus alerts")

    private val _selectedChannelId = MutableStateFlow("general")
    val selectedChannelId: StateFlow<String> = _selectedChannelId.asStateFlow()

    private val _selectedEventCategory = MutableStateFlow("ALL")
    val selectedEventCategory: StateFlow<String> = _selectedEventCategory.asStateFlow()

    private val initialCalendarToday = CampusEventDateParser.today()

    private val _eventsViewMode = MutableStateFlow("CALENDAR")
    val eventsViewMode: StateFlow<String> = _eventsViewMode.asStateFlow()

    private val _calendarYear = MutableStateFlow(initialCalendarToday.year)
    val calendarYear: StateFlow<Int> = _calendarYear.asStateFlow()

    private val _calendarMonth = MutableStateFlow(initialCalendarToday.month)
    val calendarMonth: StateFlow<Int> = _calendarMonth.asStateFlow()

    private val _selectedCalendarDate = MutableStateFlow(initialCalendarToday)
    val selectedCalendarDate: StateFlow<CalendarDayKey> = _selectedCalendarDate.asStateFlow()

    private val _selectedEventId = MutableStateFlow<String?>(null)
    val selectedEventId: StateFlow<String?> = _selectedEventId.asStateFlow()

    private val _selectedResourceDept = MutableStateFlow("ALL")
    val selectedResourceDept: StateFlow<String> = _selectedResourceDept.asStateFlow()

    private val _selectedResourceSemester = MutableStateFlow("ALL")
    val selectedResourceSemester: StateFlow<String> = _selectedResourceSemester.asStateFlow()

    private val _selectedResourceType = MutableStateFlow("ALL")
    val selectedResourceType: StateFlow<String> = _selectedResourceType.asStateFlow()

    private val _resourceSearchQuery = MutableStateFlow("")
    val resourceSearchQuery: StateFlow<String> = _resourceSearchQuery.asStateFlow()

    private val _selectedScheduleDay = MutableStateFlow(currentAcademicDayCode())
    val selectedScheduleDay: StateFlow<String> = _selectedScheduleDay.asStateFlow()

    private val _scheduleViewMode = MutableStateFlow("DAILY")
    val scheduleViewMode: StateFlow<String> = _scheduleViewMode.asStateFlow()

    private val _unreadAlertCount = MutableStateFlow(0)
    val unreadAlertCount: StateFlow<Int> = _unreadAlertCount.asStateFlow()

    private val _newLiveAlertEvents = MutableSharedFlow<CampusAlert>(extraBufferCapacity = 8)
    val newLiveAlertEvents: SharedFlow<CampusAlert> = _newLiveAlertEvents.asSharedFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isUploadingChatImage = MutableStateFlow(false)
    val isUploadingChatImage: StateFlow<Boolean> = _isUploadingChatImage.asStateFlow()

    private val _chatImageUploadProgress = MutableStateFlow(0f)
    val chatImageUploadProgress: StateFlow<Float> = _chatImageUploadProgress.asStateFlow()

    private val _downloadingResourceIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingResourceIds: StateFlow<Set<String>> = _downloadingResourceIds.asStateFlow()

    private val _resourceDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val resourceDownloadProgress: StateFlow<Map<String, Float>> = _resourceDownloadProgress.asStateFlow()

    private val _downloadedResources = MutableStateFlow<Map<String, DownloadedPdfInfo>>(emptyMap())
    val downloadedResources: StateFlow<Map<String, DownloadedPdfInfo>> = _downloadedResources.asStateFlow()

    private val _fcmSyncState = MutableStateFlow(FcmSyncState())
    val fcmSyncState: StateFlow<FcmSyncState> = _fcmSyncState.asStateFlow()

    private var knownAlertIds: Set<String>? = null
    private var lastSyncedFcmConfig: Pair<String, Boolean>? = null
    private val defaultDisplayName = initialDisplayName.ifBlank { "MEC Student" }
    private val inFlightReadMessageIds = mutableSetOf<String>()
    private var typingTimeoutJob: Job? = null
    private var isCurrentlyTyping = false
    private var activeTypingChannelId: String? = null
    private var lastTypingHeartbeatMillis = 0L

    init {
        viewModelScope.launch {
            alertsState.collect { state ->
                if (state is UiState.Success) {
                    val currentAlerts = state.data
                    val currentIds = currentAlerts.map { it.id }.toSet()
                    val previous = knownAlertIds
                    if (previous == null) {
                        knownAlertIds = currentIds
                        _unreadAlertCount.value = currentAlerts.size.coerceAtMost(9)
                    } else {
                        val added = currentAlerts.filter { it.id !in previous }
                        if (added.isNotEmpty()) {
                            _unreadAlertCount.value = _unreadAlertCount.value + added.size
                            val notificationsEnabled =
                                (profileState.value as? UiState.Success)?.data?.notificationsEnabled ?: true
                            if (notificationsEnabled) {
                                added.forEach { _newLiveAlertEvents.tryEmit(it) }
                            }
                        }
                        knownAlertIds = currentIds
                    }
                }
            }
        }

        viewModelScope.launch {
            profileState.collect { state ->
                val profile = (state as? UiState.Success)?.data ?: return@collect
                val config = profile.department to profile.notificationsEnabled
                if (lastSyncedFcmConfig != config) {
                    lastSyncedFcmConfig = config
                    refreshFcmSync(profile.department, profile.notificationsEnabled)
                }
            }
        }
    }

    fun refreshFcmSync(department: String, notificationsEnabled: Boolean) {
        viewModelScope.launch {
            _fcmSyncState.value = _fcmSyncState.value.copy(
                isSyncing = true,
                notificationsEnabled = notificationsEnabled
            )
            lastSyncedFcmConfig = department to notificationsEnabled
            _fcmSyncState.value = fcmSyncHandler(department, notificationsEnabled)
        }
    }

    fun selectChannel(channelId: String) {
        val previousChannel = _selectedChannelId.value
        if (previousChannel != channelId) {
            clearTypingStatus(previousChannel)
        }
        _selectedChannelId.value = channelId
    }

    fun selectEventCategory(category: String) {
        _selectedEventCategory.value = category
    }

    fun selectEventsViewMode(mode: String) {
        _eventsViewMode.value = mode
    }

    fun selectCalendarDate(date: CalendarDayKey) {
        _selectedCalendarDate.value = date
        _calendarYear.value = date.year
        _calendarMonth.value = date.month
    }

    fun changeCalendarMonth(year: Int, month: Int) {
        _calendarYear.value = year
        _calendarMonth.value = month
    }

    fun jumpCalendarToToday() {
        val today = CampusEventDateParser.today()
        _calendarYear.value = today.year
        _calendarMonth.value = today.month
        _selectedCalendarDate.value = today
    }

    fun selectEventForDetails(eventId: String?) {
        _selectedEventId.value = eventId
    }

    fun selectResourceDepartment(dept: String) {
        _selectedResourceDept.value = dept
    }

    fun selectResourceSemester(sem: String) {
        _selectedResourceSemester.value = sem
    }

    fun selectResourceType(type: String) {
        _selectedResourceType.value = type
    }

    fun updateResourceSearchQuery(query: String) {
        _resourceSearchQuery.value = query
    }

    fun selectScheduleDay(day: String) {
        _selectedScheduleDay.value = day
    }

    fun selectScheduleViewMode(mode: String) {
        _scheduleViewMode.value = mode
    }

    fun markAlertsSeen() {
        _unreadAlertCount.value = 0
    }

    fun saveStudentOnboarding(
        displayName: String,
        department: String,
        semester: String,
        notificationsEnabled: Boolean,
        darkThemeEnabled: Boolean
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val cleanName = displayName.trim().ifBlank { defaultDisplayName }
                repository.saveProfile(
                    displayName = cleanName,
                    department = department,
                    semester = semester,
                    notificationsEnabled = notificationsEnabled,
                    darkThemeEnabled = darkThemeEnabled
                )
                repository.seedCampusIfEmpty(cleanName, department, semester)
                repository.seedPersonalScheduleIfEmpty(department)
                refreshFcmSync(department, notificationsEnabled)
                _snackbarMessage.tryEmit("Welcome to MEC Connect, $cleanName ($department $semester)!")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Failed to save profile")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleThemePreference(profile: StudentProfile) {
        setThemePreference(profile, !profile.darkThemeEnabled)
    }

    fun setThemePreference(profile: StudentProfile, darkThemeEnabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.saveProfile(
                    displayName = profile.displayName,
                    department = profile.department,
                    semester = profile.semester,
                    notificationsEnabled = profile.notificationsEnabled,
                    darkThemeEnabled = darkThemeEnabled
                )
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Unable to update theme")
            }
        }
    }

    fun toggleNotificationsPreference(profile: StudentProfile) {
        viewModelScope.launch {
            try {
                val next = !profile.notificationsEnabled
                repository.saveProfile(
                    displayName = profile.displayName,
                    department = profile.department,
                    semester = profile.semester,
                    notificationsEnabled = next,
                    darkThemeEnabled = profile.darkThemeEnabled
                )
                refreshFcmSync(profile.department, next)
                _snackbarMessage.tryEmit(
                    if (next) "Real-time FCM & campus alerts enabled" else "Campus push notifications muted"
                )
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Unable to update notification settings")
            }
        }
    }

    fun emitMessage(message: String) {
        _snackbarMessage.tryEmit(message)
    }

    fun sendChatMessage(content: String, tag: String?) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        val profile = (profileState.value as? UiState.Success)?.data
        val senderName = profile?.displayName ?: defaultDisplayName
        val dept = profile?.department ?: "CSE"
        val sem = profile?.semester ?: "S5"
        val channelId = _selectedChannelId.value
        clearTypingStatus(channelId)

        viewModelScope.launch {
            try {
                repository.sendChatMessage(
                    channelId = channelId,
                    senderName = senderName,
                    senderDepartment = dept,
                    senderSemester = sem,
                    content = trimmed,
                    tag = tag
                )
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Failed to send message")
            }
        }
    }

    fun markChannelMessagesAsRead(messages: List<ChatMessage>) {
        val unreadIds = messages
            .filter { msg ->
                msg.id.isNotBlank() &&
                    msg.senderId != currentUserId &&
                    currentUserId !in msg.readReceipts.keys &&
                    msg.id !in inFlightReadMessageIds
            }
            .map { it.id }
        if (unreadIds.isEmpty()) return

        val profile = (profileState.value as? UiState.Success)?.data
        val readerName = profile?.displayName ?: defaultDisplayName
        inFlightReadMessageIds.addAll(unreadIds)

        viewModelScope.launch {
            try {
                repository.markChatMessagesAsRead(unreadIds, readerName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update message read receipts", e)
            } finally {
                inFlightReadMessageIds.removeAll(unreadIds.toSet())
            }
        }
    }

    fun onChatComposerTextChanged(text: String) {
        val channelId = _selectedChannelId.value
        if (text.isBlank()) {
            clearTypingStatus(channelId)
            return
        }

        val now = System.currentTimeMillis()
        val shouldPublishHeartbeat = !isCurrentlyTyping ||
            activeTypingChannelId != channelId ||
            (now - lastTypingHeartbeatMillis) >= TYPING_HEARTBEAT_INTERVAL_MS

        if (shouldPublishHeartbeat) {
            isCurrentlyTyping = true
            activeTypingChannelId = channelId
            lastTypingHeartbeatMillis = now
            publishTypingState(channelId = channelId, isTyping = true)
        }

        typingTimeoutJob?.cancel()
        typingTimeoutJob = viewModelScope.launch {
            delay(TYPING_IDLE_TIMEOUT_MS)
            clearTypingStatus(channelId)
        }
    }

    fun clearTypingStatus(channelId: String = _selectedChannelId.value) {
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        if (!isCurrentlyTyping && activeTypingChannelId == null) return
        val targetChannel = activeTypingChannelId ?: channelId
        isCurrentlyTyping = false
        activeTypingChannelId = null
        lastTypingHeartbeatMillis = 0L
        publishTypingState(channelId = targetChannel, isTyping = false)
    }

    private fun publishTypingState(channelId: String, isTyping: Boolean) {
        val profile = (profileState.value as? UiState.Success)?.data
        val displayName = profile?.displayName ?: defaultDisplayName
        val department = profile?.department ?: "CSE"
        viewModelScope.launch {
            try {
                repository.setTypingStatus(
                    channelId = channelId,
                    displayName = displayName,
                    department = department,
                    isTyping = isTyping
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update typing status", e)
            }
        }
    }

    fun sendCameraImageMessage(
        imageUri: Uri,
        caption: String,
        tag: String?,
        onSuccess: () -> Unit = {}
    ) {
        val uploader = chatImageUploader ?: run {
            _snackbarMessage.tryEmit("Image upload is not configured")
            return
        }
        if (_isUploadingChatImage.value) return
        val profile = (profileState.value as? UiState.Success)?.data
        val senderName = profile?.displayName ?: defaultDisplayName
        val dept = profile?.department ?: "CSE"
        val sem = profile?.semester ?: "S5"
        val channelId = _selectedChannelId.value
        val trimmedCaption = caption.trim()
        clearTypingStatus(channelId)

        viewModelScope.launch {
            _isUploadingChatImage.value = true
            _chatImageUploadProgress.value = 0.05f
            try {
                val downloadUrl = uploader(channelId, currentUserId, imageUri) { progress ->
                    _chatImageUploadProgress.value = progress.coerceIn(0.05f, 0.95f)
                }
                repository.sendChatMessage(
                    channelId = channelId,
                    senderName = senderName,
                    senderDepartment = dept,
                    senderSemester = sem,
                    content = trimmedCaption,
                    tag = tag,
                    imageUrl = downloadUrl
                )
                onSuccess()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload/send chat photo", e)
                _snackbarMessage.tryEmit(
                    e.localizedMessage ?: "Failed to upload photo to Firebase Storage"
                )
            } finally {
                _isUploadingChatImage.value = false
                _chatImageUploadProgress.value = 0f
            }
        }
    }

    fun deleteChatMessage(messageId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChatMessage(messageId)
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Could not delete message")
            }
        }
    }

    fun createCampusEvent(
        title: String,
        organizer: String,
        category: String,
        venue: String,
        dateLabel: String,
        timeLabel: String,
        description: String,
        department: String
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.createCampusEvent(
                    title = title,
                    organizer = organizer,
                    category = category,
                    venue = venue,
                    dateLabel = dateLabel,
                    timeLabel = timeLabel,
                    description = description,
                    department = department
                )
                CampusEventDateParser.parseEventDates(dateLabel, _calendarYear.value)
                    .firstOrNull()
                    ?.let { firstDate ->
                        _selectedCalendarDate.value = firstDate
                        _calendarYear.value = firstDate.year
                        _calendarMonth.value = firstDate.month
                    }
                _snackbarMessage.tryEmit("Event published to MEC campus feed!")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Could not create event")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleEventRsvp(event: CampusEvent, currentlyRsvped: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleEventRsvp(event, currentlyRsvped)
                _snackbarMessage.tryEmit(
                    if (currentlyRsvped) "Removed RSVP for ${event.title}" else "RSVP confirmed for ${event.title}"
                )
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "RSVP update failed")
            }
        }
    }

    fun addDepartmentResource(
        title: String,
        department: String,
        semester: String,
        subjectCode: String,
        resourceType: String,
        description: String,
        linkOrReference: String
    ) {
        val profile = (profileState.value as? UiState.Success)?.data
        val uploaderName = profile?.displayName ?: defaultDisplayName

        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.addDepartmentResource(
                    title = title,
                    department = department,
                    semester = semester,
                    subjectCode = subjectCode,
                    resourceType = resourceType,
                    description = description,
                    linkOrReference = linkOrReference,
                    uploaderName = uploaderName
                )
                _snackbarMessage.tryEmit("Resource shared with $department $semester!")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Could not share resource")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun upvoteResource(resource: DepartmentResource) {
        viewModelScope.launch {
            try {
                repository.upvoteResource(resource)
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Failed to upvote resource")
            }
        }
    }

    fun downloadDepartmentResource(resource: DepartmentResource) {
        val resourceKey = resource.id.ifBlank { resource.title }
        if (resourceKey in _downloadingResourceIds.value) return

        if (!ResourcePdfDownloader.isDownloadableUrl(resource.linkOrReference)) {
            _snackbarMessage.tryEmit(
                "Cannot download: '${resource.linkOrReference}' is a campus reference without a web PDF link."
            )
            return
        }

        val downloader = resourcePdfDownloader ?: run {
            _snackbarMessage.tryEmit("PDF downloader is not configured")
            return
        }

        viewModelScope.launch {
            _downloadingResourceIds.value = _downloadingResourceIds.value + resourceKey
            _resourceDownloadProgress.value = _resourceDownloadProgress.value + (resourceKey to 0.05f)
            try {
                val info = downloader(resource) { progress ->
                    _resourceDownloadProgress.value =
                        _resourceDownloadProgress.value + (resourceKey to progress.coerceIn(0.05f, 0.98f))
                }
                _downloadedResources.value = _downloadedResources.value + (resourceKey to info)
                _snackbarMessage.tryEmit("Saved ${info.fileName} to ${info.displayLocation}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download PDF resource ${resource.id}", e)
                _snackbarMessage.tryEmit(
                    "Download failed: ${e.localizedMessage ?: "Unable to save PDF"}"
                )
            } finally {
                _downloadingResourceIds.value = _downloadingResourceIds.value - resourceKey
                _resourceDownloadProgress.value = _resourceDownloadProgress.value - resourceKey
            }
        }
    }

    fun addScheduleSlot(
        courseCode: String,
        courseTitle: String,
        facultyName: String,
        room: String,
        dayOfWeek: String,
        startTime: String,
        endTime: String,
        slotType: String,
        reminderEnabled: Boolean
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.addScheduleSlot(
                    courseCode = courseCode,
                    courseTitle = courseTitle,
                    facultyName = facultyName,
                    room = room,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    slotType = slotType,
                    reminderEnabled = reminderEnabled
                )
                _selectedScheduleDay.value = dayOfWeek
                _snackbarMessage.tryEmit("Added $courseCode to $dayOfWeek timetable")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Could not add timetable slot")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleSlotReminder(slot: ScheduleSlot) {
        viewModelScope.launch {
            try {
                repository.updateScheduleSlot(slot.copy(reminderEnabled = !slot.reminderEnabled))
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Could not update reminder")
            }
        }
    }

    fun toggleSlotCompleted(slot: ScheduleSlot) {
        viewModelScope.launch {
            try {
                repository.updateScheduleSlot(slot.copy(isCompleted = !slot.isCompleted))
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Could not update status")
            }
        }
    }

    fun deleteScheduleSlot(slotId: String) {
        viewModelScope.launch {
            try {
                repository.deleteScheduleSlot(slotId)
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Could not remove slot")
            }
        }
    }

    fun postCampusAlert(
        title: String,
        message: String,
        category: String,
        department: String
    ) {
        val profile = (profileState.value as? UiState.Success)?.data
        val senderName = profile?.displayName ?: defaultDisplayName

        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.postCampusAlert(
                    title = title,
                    message = message,
                    category = category,
                    department = department,
                    senderName = senderName
                )
                _snackbarMessage.tryEmit("Live campus alert broadcast!")
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.localizedMessage ?: "Failed to broadcast alert")
            } finally {
                _isSaving.value = false
            }
        }
    }

    private companion object {
        const val TAG = "MecCampusVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val TYPING_HEARTBEAT_INTERVAL_MS = 3500L
        const val TYPING_IDLE_TIMEOUT_MS = 4500L

        fun currentAcademicDayCode(): String = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            else -> "MON"
        }
    }
}
