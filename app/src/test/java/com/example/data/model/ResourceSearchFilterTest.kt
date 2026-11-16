package com.example.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceSearchFilterTest {

    private val pdfResource = DepartmentResource(
        id = "res_1",
        title = "CST301 Automata & Formal Languages PDF Pack",
        department = "CSE",
        semester = "S5",
        subjectCode = "CST301",
        resourceType = "PDF",
        description = "Complete DFA, NFA, CFG, and Turing Machine notes.",
        linkOrReference = "https://ktu.edu.in/cs-cst301-notes.pdf",
        uploaderName = "Aadinath"
    )

    private val linkResource = DepartmentResource(
        id = "res_2",
        title = "FOSSMEC & CSE GitHub Course Archive",
        department = "CSE",
        semester = "S5",
        subjectCode = "CST303",
        resourceType = "Link",
        description = "Computer Networks lab programs and syllabus links.",
        linkOrReference = "https://github.com/fossmec",
        uploaderName = "Kiran"
    )

    private val bmeSlides = DepartmentResource(
        id = "res_3",
        title = "BMT302 Biomedical Signal Processing Slides",
        department = "BME",
        semester = "S5",
        subjectCode = "BMT302",
        resourceType = "Slides",
        description = "ECG/EEG filtering and FFT spectral analysis decks.",
        linkOrReference = "BME Seminar Hall • Deck 2026",
        uploaderName = "Lakshmi"
    )

    @Test
    fun matchesResourceQuery_byTitleAndCategoryAndDepartment() {
        assertTrue(MecChannels.matchesResourceQuery(pdfResource, ""))
        assertTrue(MecChannels.matchesResourceQuery(pdfResource, "Automata"))
        assertTrue(MecChannels.matchesResourceQuery(pdfResource, "PDF"))
        assertTrue(MecChannels.matchesResourceQuery(pdfResource, "cse pdf"))
        assertTrue(MecChannels.matchesResourceQuery(linkResource, "github link"))
        assertTrue(MecChannels.matchesResourceQuery(bmeSlides, "Biomedical Engineering slides"))
        assertFalse(MecChannels.matchesResourceQuery(pdfResource, "github"))
        assertFalse(MecChannels.matchesResourceQuery(bmeSlides, "ECE PDF"))
    }
}
