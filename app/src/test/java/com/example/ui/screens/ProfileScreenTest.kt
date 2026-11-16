package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.StudentProfile
import com.example.ui.theme.MecConnectTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysUsernameEmailAndSignsOutOnConfirmation() {
        var signedOut = false
        var edited = false
        val profile = StudentProfile(
            userId = "uid_1",
            displayName = "Ananya Menon",
            department = "CSE",
            semester = "S5",
            notificationsEnabled = true,
            darkThemeEnabled = false
        )

        composeRule.setContent {
            MecConnectTheme(darkTheme = false) {
                ProfileScreen(
                    profile = profile,
                    userEmail = "ananya.cs@mec.ac.in",
                    isDarkTheme = false,
                    onToggleTheme = {},
                    onToggleNotifications = {},
                    onEditProfile = { edited = true },
                    onSignOut = { signedOut = true }
                )
            }
        }

        composeRule.onNodeWithTag("profile_list")
            .performScrollToNode(hasTestTag("profile_account_details_card"))

        composeRule.onNodeWithTag("profile_username_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Ananya Menon")

        composeRule.onNodeWithTag("profile_email_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("ananya.cs@mec.ac.in")

        composeRule.onNodeWithTag("profile_edit_button")
            .performScrollTo()
            .performClick()
        assertTrue(edited)

        composeRule.onNodeWithTag("profile_list")
            .performScrollToNode(hasTestTag("profile_sign_out_button"))
        composeRule.onNodeWithTag("profile_sign_out_button")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag("confirm_sign_out_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(signedOut)
    }
}
