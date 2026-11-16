package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.data.model.ChatChannel
import com.example.data.model.ChatMessage
import com.example.data.model.ChatTransparencyHelper
import com.example.data.model.ChatTypingStatus
import com.example.data.model.MecChannels
import com.example.data.storage.ChatImageStorageManager
import com.example.ui.components.MecEmptyStateCard
import com.example.ui.components.MecLoadingState
import com.example.ui.components.MecTagPill
import com.example.ui.components.StudentAvatarBadge
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.departmentAccentColor
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay

@Composable
fun CampusChatScreen(
    currentUserId: String,
    selectedChannelId: String,
    chatState: UiState<List<ChatMessage>>,
    typingStatuses: List<ChatTypingStatus> = emptyList(),
    isUploadingImage: Boolean = false,
    imageUploadProgress: Float = 0f,
    onSelectChannel: (String) -> Unit,
    onSendMessage: (content: String, tag: String?) -> Unit,
    onSendCameraImage: (imageUri: Uri, caption: String, tag: String?, onSuccess: () -> Unit) -> Unit = { _, _, _, _ -> },
    onDeleteMessage: (String) -> Unit,
    onMarkMessagesRead: (List<ChatMessage>) -> Unit = {},
    onTypingInputChanged: (String) -> Unit = {},
    onClearTyping: () -> Unit = {},
    onShowError: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allMessages = (chatState as? UiState.Success)?.data.orEmpty()
    val channelMessages = remember(allMessages, selectedChannelId) {
        allMessages.filter { it.channelId == selectedChannelId }
    }
    val selectedChannel = remember(selectedChannelId) {
        MecChannels.all.firstOrNull { it.id == selectedChannelId } ?: MecChannels.all.first()
    }

    var messageText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var stagedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var lightboxMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var receiptsDialogMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var typingNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val listState = rememberLazyListState()

    val activeTypingUsers = remember(typingStatuses, selectedChannelId, currentUserId, typingNowMillis) {
        ChatTransparencyHelper.activeTypingUsers(
            statuses = typingStatuses,
            channelId = selectedChannelId,
            currentUserId = currentUserId,
            nowMillis = typingNowMillis
        )
    }

    LaunchedEffect(typingStatuses, selectedChannelId) {
        typingNowMillis = System.currentTimeMillis()
        while (true) {
            delay(2_500L)
            typingNowMillis = System.currentTimeMillis()
        }
    }

    LaunchedEffect(channelMessages, selectedChannelId, currentUserId) {
        val unread = channelMessages.filter { msg ->
            msg.id.isNotBlank() &&
                msg.senderId != currentUserId &&
                currentUserId !in msg.readReceipts.keys
        }
        if (unread.isNotEmpty()) {
            onMarkMessagesRead(unread)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onClearTyping()
        }
    }

    val cameraPermissionDeniedMsg = stringResource(R.string.chat_camera_permission_denied)

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCaptureUri
        if (success && captured != null) {
            stagedPhotoUri = captured
        }
        pendingCaptureUri = null
    }

    val launchCameraCapture = {
        try {
            val uri = ChatImageStorageManager.createCameraPhotoUri(context)
            pendingCaptureUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            pendingCaptureUri = null
            onShowError(e.localizedMessage ?: "Unable to launch device camera")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            onShowError(cameraPermissionDeniedMsg)
        }
    }

    val onCameraClicked: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val onSendAction: () -> Unit = {
        val staged = stagedPhotoUri
        if (staged != null) {
            onSendCameraImage(staged, messageText, selectedTag) {
                stagedPhotoUri = null
                messageText = ""
            }
        } else if (messageText.isNotBlank()) {
            onSendMessage(messageText, selectedTag)
            messageText = ""
        }
    }

    LaunchedEffect(channelMessages.size, selectedChannelId) {
        if (channelMessages.isNotEmpty()) {
            listState.animateScrollToItem(channelMessages.lastIndex)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            // Split-pane tablet/foldable layout: Channels rail on left, live chat on right
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "MEC CHANNELS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        items(MecChannels.all, key = { it.id }) { channel ->
                            val count = allMessages.count { it.channelId == channel.id }
                            ChannelListTile(
                                channel = channel,
                                count = count,
                                selected = channel.id == selectedChannelId,
                                onClick = { onSelectChannel(channel.id) }
                            )
                        }
                    }
                }

                ChatRoomColumn(
                    selectedChannel = selectedChannel,
                    chatState = chatState,
                    channelMessages = channelMessages,
                    activeTypingUsers = activeTypingUsers,
                    currentUserId = currentUserId,
                    listState = listState,
                    messageText = messageText,
                    onMessageTextChange = { updated ->
                        messageText = updated
                        onTypingInputChanged(updated)
                    },
                    selectedTag = selectedTag,
                    onSelectTag = { selectedTag = if (selectedTag == it) null else it },
                    stagedPhotoUri = stagedPhotoUri,
                    isUploadingImage = isUploadingImage,
                    imageUploadProgress = imageUploadProgress,
                    onCameraClick = onCameraClicked,
                    onClearStagedPhoto = { stagedPhotoUri = null },
                    onSend = onSendAction,
                    onDeleteMessage = onDeleteMessage,
                    onImageClick = { msg -> lightboxMessage = msg },
                    onInspectReceipts = { msg -> receiptsDialogMessage = msg },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            // Compact mobile layout: Horizontal channel pills + chat stream + composer
            Column(modifier = Modifier.fillMaxSize()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MecChannels.all, key = { it.id }) { channel ->
                        val isSelected = channel.id == selectedChannelId
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectChannel(channel.id) },
                            label = {
                                Text(
                                    text = "#${channel.title}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("chat_channel_${channel.id}")
                        )
                    }
                }

                ChatRoomColumn(
                    selectedChannel = selectedChannel,
                    chatState = chatState,
                    channelMessages = channelMessages,
                    activeTypingUsers = activeTypingUsers,
                    currentUserId = currentUserId,
                    listState = listState,
                    messageText = messageText,
                    onMessageTextChange = { updated ->
                        messageText = updated
                        onTypingInputChanged(updated)
                    },
                    selectedTag = selectedTag,
                    onSelectTag = { selectedTag = if (selectedTag == it) null else it },
                    stagedPhotoUri = stagedPhotoUri,
                    isUploadingImage = isUploadingImage,
                    imageUploadProgress = imageUploadProgress,
                    onCameraClick = onCameraClicked,
                    onClearStagedPhoto = { stagedPhotoUri = null },
                    onSend = onSendAction,
                    onDeleteMessage = onDeleteMessage,
                    onImageClick = { msg -> lightboxMessage = msg },
                    onInspectReceipts = { msg -> receiptsDialogMessage = msg },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }

    val inspectedMessage = lightboxMessage
    if (inspectedMessage != null && !inspectedMessage.imageUrl.isNullOrBlank()) {
        ChatImageLightboxDialog(
            message = inspectedMessage,
            onDismiss = { lightboxMessage = null }
        )
    }

    val receiptMessage = receiptsDialogMessage
    if (receiptMessage != null) {
        ChatReadReceiptsDialog(
            message = receiptMessage,
            onDismiss = { receiptsDialogMessage = null }
        )
    }
}

@Composable
private fun ChannelListTile(
    channel: ChatChannel,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Tag,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = channel.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (count > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = CircleShape
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRoomColumn(
    selectedChannel: ChatChannel,
    chatState: UiState<List<ChatMessage>>,
    channelMessages: List<ChatMessage>,
    activeTypingUsers: List<ChatTypingStatus>,
    currentUserId: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    selectedTag: String?,
    onSelectTag: (String) -> Unit,
    stagedPhotoUri: Uri?,
    isUploadingImage: Boolean,
    imageUploadProgress: Float,
    onCameraClick: () -> Unit,
    onClearStagedPhoto: () -> Unit,
    onSend: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onImageClick: (ChatMessage) -> Unit,
    onInspectReceipts: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = !isUploadingImage && (messageText.isNotBlank() || stagedPhotoUri != null)

    Column(modifier = modifier.imePadding()) {
        // Channel Header Strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "# ${selectedChannel.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedChannel.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    MecTagPill(
                        text = selectedChannel.badge,
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "LIVE • ${channelMessages.size} msgs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Messages Feed
        when (chatState) {
            is UiState.Loading -> {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MecLoadingState()
                }
            }
            is UiState.Error -> {
                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    MecEmptyStateCard(
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        title = "Unable to sync messages",
                        subtitle = chatState.message
                    )
                }
            }
            is UiState.Success -> {
                if (channelMessages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        MecEmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            title = "Start the conversation in #${selectedChannel.title}",
                            subtitle = "Ask a KTU doubt, snap whiteboard notes with the camera, or coordinate with MECians."
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("chat_message_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(channelMessages, key = { it.id }) { msg ->
                            val isMine = msg.senderId == currentUserId
                            ChatMessageBubble(
                                message = msg,
                                isMine = isMine,
                                onDelete = { onDeleteMessage(msg.id) },
                                onImageClick = { onImageClick(msg) },
                                onInspectReceipts = { onInspectReceipts(msg) }
                            )
                        }
                    }
                }
            }
        }

        // Real-time Typing Indicator + Optional Tag Selector + Staged Photo Preview + Message Composer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            AnimatedVisibility(visible = activeTypingUsers.isNotEmpty()) {
                if (activeTypingUsers.isNotEmpty()) {
                    TypingIndicatorBar(
                        typingUsers = activeTypingUsers,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("chat_typing_indicator")
                    )
                }
            }
            AnimatedVisibility(visible = stagedPhotoUri != null) {
                if (stagedPhotoUri != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("chat_staged_photo_preview")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = stagedPhotoUri,
                                    contentDescription = "Captured photo preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isUploadingImage) {
                                            stringResource(R.string.chat_uploading_photo)
                                        } else {
                                            "Camera photo ready"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isUploadingImage) {
                                            "${(imageUploadProgress * 100).toInt()}% uploaded"
                                        } else {
                                            "Add an optional caption or tap Send"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!isUploadingImage) {
                                    IconButton(
                                        onClick = onClearStagedPhoto,
                                        modifier = Modifier.testTag("chat_remove_staged_photo")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove captured photo"
                                        )
                                    }
                                }
                            }
                            if (isUploadingImage) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { imageUploadProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(listOf("Discussion", "Doubt", "Announcement", "Note")) { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { onSelectTag(tag) },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    onClick = onCameraClick,
                    enabled = !isUploadingImage,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = stringResource(R.string.chat_camera_capture),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    placeholder = {
                        Text(
                            text = if (stagedPhotoUri != null) {
                                "Add a caption for your photo..."
                            } else {
                                "Message #${selectedChannel.title}..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                onSend()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = onSend,
                    enabled = canSend,
                    color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("chat_send_button")
                ) {
                    if (isUploadingImage) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    onDelete: () -> Unit,
    onImageClick: () -> Unit,
    onInspectReceipts: () -> Unit
) {
    val deptColor = departmentAccentColor(message.senderDepartment)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            StudentAvatarBadge(
                name = message.senderName,
                accentColor = deptColor,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Surface(
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            border = BorderStroke(
                1.dp,
                if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isMine) "You" else message.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isMine) MaterialTheme.colorScheme.primary else deptColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${message.senderDepartment}•${message.senderSemester}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!message.tag.isNullOrBlank()) {
                        MecTagPill(
                            text = message.tag,
                            accentColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (!message.imageUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 240.dp)
                            .clickable(onClick = onImageClick)
                            .testTag("chat_message_image_${message.id}")
                    ) {
                        SubcomposeAsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Photo shared by ${message.senderName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            },
                            error = {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BrokenImage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap to view photo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }

                if (message.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                val otherReaders = remember(message.readReceipts, message.senderId) {
                    ChatTransparencyHelper.otherReaders(message)
                }
                val receiptLabel = remember(message.readReceipts, message.senderId) {
                    ChatTransparencyHelper.formatReadReceiptSummary(message)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = formatRelativeTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isMine || otherReaders.isNotEmpty()) {
                            Surface(
                                onClick = {
                                    if (otherReaders.isNotEmpty()) {
                                        onInspectReceipts()
                                    }
                                },
                                enabled = otherReaders.isNotEmpty(),
                                color = if (otherReaders.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("chat_read_receipt_${message.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (otherReaders.isNotEmpty()) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                                        contentDescription = receiptLabel,
                                        tint = if (otherReaders.isNotEmpty()) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isMine) receiptLabel else "Seen by ${otherReaders.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (otherReaders.isNotEmpty()) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (isMine) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete message",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicatorBar(
    typingUsers: List<ChatTypingStatus>,
    modifier: Modifier = Modifier
) {
    val label = remember(typingUsers) {
        ChatTransparencyHelper.formatTypingBanner(typingUsers)
    }
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = 0),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = 160),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = 320),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(dot1Alpha, dot2Alpha, dot3Alpha).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(alpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatReadReceiptsDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val readers = remember(message.readReceipts, message.senderId) {
        ChatTransparencyHelper.otherReaders(message)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("chat_receipts_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.chat_receipts_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (message.content.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                readers.forEach { (_, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StudentAvatarBadge(
                            name = name,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("chat_receipts_dialog_close")
            ) {
                Text(stringResource(R.string.chat_receipts_close))
            }
        }
    )
}

@Composable
private fun ChatImageLightboxDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .testTag("chat_image_lightbox_dialog")
        ) {
            SubcomposeAsyncImage(
                model = message.imageUrl,
                contentDescription = "Full screen photo from ${message.senderName}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 72.dp),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${message.senderDepartment} • ${message.senderSemester} • ${formatRelativeTime(message.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
                Surface(
                    onClick = onDismiss,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_image_lightbox_close")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close photo preview",
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (message.content.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}
