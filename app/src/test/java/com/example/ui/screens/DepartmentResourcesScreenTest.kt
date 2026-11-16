package com.example.ui.screens

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.DepartmentResource
import com.example.data.storage.DownloadedPdfInfo
import com.example.ui.theme.MecConnectTheme
import com.example.ui.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class DepartmentResourcesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pdfResourceShowsDownloadButton_andNonPdfLinkDoesNot() {
        var downloadedClickedResource: DepartmentResource? = null
        val resources = listOf(
            DepartmentResource(
                id = "res_pdf_1",
                title = "CST301 Automata PDF Pack",
                department = "CSE",
                semester = "S5",
                subjectCode = "CST301",
                resourceType = "PDF",
                description = "Solved KTU automata notes.",
                linkOrReference = "https://ktu.edu.in/cs-cst301-notes.pdf",
                uploaderName = "Aadinath AP",
                upvotes = 4L
            ),
            DepartmentResource(
                id = "res_link_1",
                title = "FOSSMEC GitHub Archive",
                department = "CSE",
                semester = "S5",
                subjectCode = "CST303",
                resourceType = "Link",
                description = "Networks lab programs repo.",
                linkOrReference = "https://github.com/fossmec",
                uploaderName = "Abil Sajee",
                upvotes = 2L
            )
        )

        composeRule.setContent {
            MecConnectTheme(darkTheme = false) {
                DepartmentResourcesScreen(
                    resourcesState = UiState.Success(resources),
                    selectedDept = "ALL",
                    selectedSemester = "ALL",
                    selectedType = "ALL",
                    searchQuery = "",
                    onSelectDept = {},
                    onSelectSemester = {},
                    onSelectType = {},
                    onSearchQueryChange = {},
                    onUpvote = {},
                    onDownloadResource = { downloadedClickedResource = it },
                    onOpenAddResource = {}
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("download_resource_res_pdf_1")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("res_pdf_1", downloadedClickedResource?.id)
        composeRule.onNodeWithTag("download_resource_res_link_1").assertDoesNotExist()
    }

    @Test
    fun pdfResourceShowsProgressWhileDownloading_andOpenPdfWhenSaved() {
        val pdfResource = DepartmentResource(
            id = "res_pdf_2",
            title = "EET303 Embedded Systems PDF",
            department = "EEE",
            semester = "S5",
            subjectCode = "EET303",
            resourceType = "PDF",
            description = "Question papers with solution hints.",
            linkOrReference = "https://mec.ac.in/library/eee-pyq.pdf",
            uploaderName = "MEC Library"
        )

        composeRule.setContent {
            MecConnectTheme(darkTheme = false) {
                DepartmentResourcesScreen(
                    resourcesState = UiState.Success(listOf(pdfResource)),
                    selectedDept = "ALL",
                    selectedSemester = "ALL",
                    selectedType = "ALL",
                    searchQuery = "",
                    downloadingResourceIds = setOf("res_pdf_2"),
                    resourceDownloadProgress = mapOf("res_pdf_2" to 0.65f),
                    onSelectDept = {},
                    onSelectSemester = {},
                    onSelectType = {},
                    onSearchQueryChange = {},
                    onUpvote = {},
                    onOpenAddResource = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Downloading… 65%")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("download_progress_res_pdf_2")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun savedPdfResourceShowsLocationBadgeAndOpenPdfButton() {
        var openedInfo: DownloadedPdfInfo? = null
        val pdfResource = DepartmentResource(
            id = "res_pdf_3",
            title = "MET301 Mechanics Formula PDF",
            department = "ME",
            semester = "S5",
            subjectCode = "MET301",
            resourceType = "PDF",
            description = "Formula booklet for series exams.",
            linkOrReference = "https://mec.ac.in/library/met301.pdf",
            uploaderName = "ME Dept"
        )
        val savedInfo = DownloadedPdfInfo(
            resourceId = "res_pdf_3",
            fileName = "MET301_Mechanics_Formula_PDF.pdf",
            localUri = Uri.parse("content://downloads/public_downloads/42"),
            displayLocation = "Downloads/MEC Connect",
            byteCount = 2048L
        )

        composeRule.setContent {
            MecConnectTheme(darkTheme = false) {
                DepartmentResourcesScreen(
                    resourcesState = UiState.Success(listOf(pdfResource)),
                    selectedDept = "ALL",
                    selectedSemester = "ALL",
                    selectedType = "ALL",
                    searchQuery = "",
                    downloadedResources = mapOf("res_pdf_3" to savedInfo),
                    onSelectDept = {},
                    onSelectSemester = {},
                    onSelectType = {},
                    onSearchQueryChange = {},
                    onUpvote = {},
                    onOpenDownloadedPdf = { openedInfo = it },
                    onOpenAddResource = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("downloaded_badge_res_pdf_3")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("open_pdf_resource_res_pdf_3")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(savedInfo, openedInfo)
    }
}
