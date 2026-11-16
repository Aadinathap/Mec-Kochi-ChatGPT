package com.example.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DepartmentResource
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResourcePdfDownloaderTest {

    @Test
    fun isPdfResource_identifiesPdfTypeAndPdfUrls() {
        val pdfType = DepartmentResource(
            id = "1",
            resourceType = "PDF",
            linkOrReference = "https://example.org/notes.pdf"
        )
        val pyqWithPdfUrl = DepartmentResource(
            id = "2",
            resourceType = "PYQ",
            linkOrReference = "https://mec.ac.in/library/eee-pyq.pdf?download=1"
        )
        val plainLink = DepartmentResource(
            id = "3",
            resourceType = "Link",
            linkOrReference = "https://github.com/fossmec"
        )

        assertTrue(ResourcePdfDownloader.isPdfResource(pdfType))
        assertTrue(ResourcePdfDownloader.isPdfResource(pyqWithPdfUrl))
        assertFalse(ResourcePdfDownloader.isPdfResource(plainLink))
    }

    @Test
    fun buildPdfFileName_sanitizesSubjectAndTitleWithPdfSuffix() {
        val resource = DepartmentResource(
            id = "res1",
            subjectCode = "cst301",
            title = "Automata & Formal Languages: Module 1/2"
        )
        val fileName = ResourcePdfDownloader.buildPdfFileName(resource)
        assertEquals("CST301_Automata_Formal_Languages_Module_1_2.pdf", fileName)
    }

    @Test
    fun downloadAndSavePdf_fetchesOverHttpAndSavesBytesLocally() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samplePdfBytes = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".toByteArray()

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cst301-notes.pdf") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/pdf")
            exchange.sendResponseHeaders(200, samplePdfBytes.size.toLong())
            exchange.responseBody.use { it.write(samplePdfBytes) }
        }
        server.start()

        try {
            val url = "http://127.0.0.1:${server.address.port}/cst301-notes.pdf"
            val resource = DepartmentResource(
                id = "pdf_test_1",
                title = "CST301 Module 1 Notes",
                department = "CSE",
                semester = "S5",
                subjectCode = "CST301",
                resourceType = "PDF",
                linkOrReference = url
            )

            val progressUpdates = mutableListOf<Float>()
            val info = ResourcePdfDownloader.downloadAndSavePdf(
                context = context,
                resource = resource,
                onProgress = { progressUpdates.add(it) }
            )

            assertEquals("pdf_test_1", info.resourceId)
            assertEquals("CST301_CST301_Module_1_Notes.pdf", info.fileName)
            assertEquals(samplePdfBytes.size.toLong(), info.byteCount)
            assertTrue(progressUpdates.last() == 1f)

            val savedBytes = context.contentResolver.openInputStream(info.localUri)?.use { it.readBytes() }
            assertArrayEquals(samplePdfBytes, savedBytes)
        } finally {
            server.stop(0)
        }
    }
}
