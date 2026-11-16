package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.model.ChatMessage
import com.example.data.model.ChatTypingStatus
import com.example.ui.theme.MecConnectTheme
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class CampusChatScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysCameraButtonAndRendersChatImageWithLightbox() {
        val messages = listOf(
            ChatMessage(
                id = "msg_photo_1",
                channelId = "general",
                senderId = "uid_other",
                senderName = "Abil Sajee",
                senderDepartment = "CSE",
                senderSemester = "S6",
                content = "KTU Automata board notes from CS-302",
                tag = "Note",
                imageUrl = "https://firebasestorage.googleapis.com/v0/b/mec/o/notes.jpg?alt=media"
            )
        )

        composeRule.setContent {
            MecConnectTheme(darkTheme = false) {
                CampusChatScreen(
                    currentUserId = "uid_me",
                    selectedChannelId = "general",
                    chatState = UiState.Success(messages),
                    onSelectChannel = {},
                    onSendMessage = { _, _ -> },
                    onDeleteMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_camera_button").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_send_button").assertIsDisplayed()
        composeRule.onNodeWithText("KTU Automata board notes from CS-302").assertIsDisplayed()

        // Tap image thumbnail in message stream -> opens full-screen lightbox
        composeRule.onNodeWithTag("chat_message_image_msg_photo_1")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag("chat_image_lightbox_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_image_lightbox_close")
            .assertIsDisplayed()
            .performClick()
    }

    @Test
    fun rendersReadReceiptsTypingIndicator_andMarksUnreadMessagesRead() {
        var markedUnreadIds: List<String> = emptyList()
        var lastTypingText = ""

        val messages = listOf(
            ChatMessage(
                id = "msg_mine_sent",
                channelId = "general",
                senderId = "uid_me",
                senderName = "Aadinath AP",
                senderDepartment = "CSE",
                senderSemester = "S6",
                content = "Who is joining the FOSSMEC sprint?",
                readReceipts = emptyMap()
            ),
            ChatMessage(
                id = "msg_mine_seen",
                channelId = "general",
                senderId = "uid_me",
                senderName = "Aadinath AP",
                senderDepartment = "CSE",
                senderSemester = "S6",
                content = "Lab submission is at 4 PM",
                readReceipts = mapOf("uid_abil" to "Abil Sajee")
            ),
            ChatMessage(
                id = "msg_peer_unread",
                channelId = "general",
                senderId = "uid_abil",
                senderName = "Abil Sajee",
                senderDepartment = "CSE",
                senderSemester = "S6",
                content = "Count me in for SDPK Lab!",
                readReceipts = emptyMap()
            )
        )

        val typingStatuses = listOf(
            ChatTypingStatus(
                id = "general_uid_abil",
                channelId = "general",
                userId = "uid_abil",
                displayName = "Abil Sajee",
                department = "CSE",
                isTyping = true,
                updatedAt = Timestamp.now()
            ),
            ChatTypingStatus(
                id = "general_uid_expired",
                channelId = "general",
                userId = "uid_old",
                displayName = "Stale User",
                department = "ECE",
                isTyping = true,
                updatedAt = Timestamp(Date(System.currentTimeMillis() - 60_000L))
            )
        )

        composeRule.setContent {
            MecConnectTheme(darkTheme = false) {
                CampusChatScreen(
                    currentUserId = "uid_me",
                    selectedChannelId = "general",
                    chatState = UiState.Success(messages),
                    typingStatuses = typingStatuses,
                    onSelectChannel = {},
                    onSendMessage = { _, _ -> },
                    onDeleteMessage = {},
                    onMarkMessagesRead = { list -> markedUnreadIds = list.map { it.id } },
                    onTypingInputChanged = { lastTypingText = it }
                )
            }
        }

        composeRule.waitForIdle()

        // Unread incoming message from Abil triggers read receipt sync
        assertEquals(listOf("msg_peer_unread"), markedUnreadIds)

        // Typing indicator displays active peer and ignores expired entry
        composeRule.onNodeWithTag("chat_typing_indicator").assertIsDisplayed()
        composeRule.onNodeWithText("Abil Sajee is typing…").assertIsDisplayed()
        composeRule.onNodeWithText("Stale User is typing…").assertDoesNotExist()

        // Read receipts render Sent vs Seen by <Name> and dialog opens on click
        composeRule.onNodeWithText("Sent").assertIsDisplayed()
        composeRule.onNodeWithText("Seen by Abil Sajee").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_read_receipt_msg_mine_seen").performClick()

        composeRule.onNodeWithTag("chat_receipts_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_receipts_dialog_close").performClick()

        // Typing in composer notifies callback
        composeRule.onNodeWithTag("chat_message_input").performTextInput("On my way")
        assertTrue(lastTypingText.contains("On my way"))
    }
}
