package com.example.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.DepartmentResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class DownloadedPdfInfo(
    val resourceId: String,
    val fileName: String,
    val localUri: Uri,
    val displayLocation: String,
    val byteCount: Long
)

object ResourcePdfDownloader {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5
    private const val BUFFER_SIZE = 8192

    fun isPdfResource(resource: DepartmentResource): Boolean {
        if (resource.resourceType.equals("PDF", ignoreCase = true)) return true
        val cleanedRef = resource.linkOrReference.trim().lowercase()
            .substringBefore('?')
            .substringBefore('#')
        return isDownloadableUrl(resource.linkOrReference) && cleanedRef.endsWith(".pdf")
    }

    fun isDownloadableUrl(linkOrReference: String): Boolean {
        val trimmed = linkOrReference.trim().lowercase()
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }

    fun buildPdfFileName(resource: DepartmentResource): String {
        val codePrefix = resource.subjectCode.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9_-]"), "")
            .take(16)
        val cleanTitle = resource.title.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .take(48)
        val combined = listOf(codePrefix, cleanTitle)
            .filter { it.isNotBlank() }
            .joinToString("_")
            .ifBlank { "mec_resource_${resource.id.ifBlank { "doc" }}" }
        val base = if (combined.lowercase().endsWith(".pdf")) {
            combined.dropLast(4)
        } else {
            combined
        }
        return "$base.pdf"
    }

    suspend fun downloadAndSavePdf(
        context: Context,
        resource: DepartmentResource,
        onProgress: (Float) -> Unit = {}
    ): DownloadedPdfInfo = withContext(Dispatchers.IO) {
        val rawUrl = resource.linkOrReference.trim()
        require(isDownloadableUrl(rawUrl)) {
            "Resource does not have an HTTP/HTTPS download URL."
        }

        val fileName = buildPdfFileName(resource)
        val cacheDir = File(context.cacheDir, "pdf_downloads").apply { mkdirs() }
        val tempFile = File(cacheDir, "tmp_${System.currentTimeMillis()}_$fileName")

        try {
            val byteCount = fetchUrlToFile(rawUrl, tempFile, onProgress)
            val (localUri, displayLocation) = saveTempFileToLocalStorage(
                context = context,
                tempFile = tempFile,
                fileName = fileName
            )
            onProgress(1f)
            DownloadedPdfInfo(
                resourceId = resource.id,
                fileName = fileName,
                localUri = localUri,
                displayLocation = displayLocation,
                byteCount = byteCount
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun openDownloadedPdf(context: Context, info: DownloadedPdfInfo): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(info.localUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun fetchUrlToFile(
        initialUrl: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Long {
        var currentUrl = initialUrl
        var connection: HttpURLConnection? = null

        try {
            for (redirectCount in 0..MAX_REDIRECTS) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/pdf,application/octet-stream,*/*")
                    setRequestProperty("User-Agent", "MECConnect-Android/1.0")
                }
                connection = conn
                val status = conn.responseCode

                if (status in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Redirect without Location header (HTTP $status)")
                    val nextUrl = URL(url, location).toString()
                    conn.disconnect()
                    currentUrl = nextUrl
                    if (redirectCount == MAX_REDIRECTS) {
                        throw IOException("Too many redirects while downloading PDF")
                    }
                    continue
                }

                if (status !in 200..299) {
                    throw IOException("Server returned HTTP $status")
                }

                val contentType = conn.contentType.orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    throw IOException("Link points to a web page instead of a PDF file")
                }

                val totalBytes = conn.contentLengthLong
                var downloadedBytes = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0L) {
                                onProgress(
                                    (downloadedBytes.toFloat() / totalBytes.toFloat())
                                        .coerceIn(0.05f, 0.95f)
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (downloadedBytes <= 0L) {
                    throw IOException("Downloaded PDF file is empty")
                }
                return downloadedBytes
            }
            throw IOException("Unable to complete PDF download")
        } finally {
            connection?.disconnect()
        }
    }

    internal fun saveTempFileToLocalStorage(
        context: Context,
        tempFile: File,
        fileName: String
    ): Pair<Uri, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val savedViaMediaStore = runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/MEC Connect"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore.Downloads insert returned null")
                try {
                    val outStream = resolver.openOutputStream(itemUri)
                        ?: error("Unable to open MediaStore output stream")
                    outStream.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                        output.flush()
                    }
                    val completedValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, completedValues, null, null)
                    itemUri to "Downloads/MEC Connect"
                } catch (e: Exception) {
                    runCatching { resolver.delete(itemUri, null, null) }
                    throw e
                }
            }.getOrNull()

            if (savedViaMediaStore != null) {
                return savedViaMediaStore
            }
        }

        // Scoped app-specific local storage fallback (no storage permissions required on API 24+)
        val externalDownloadsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = if (externalDownloadsRoot != null) {
            File(externalDownloadsRoot, "MEC Connect")
        } else {
            File(context.filesDir, "downloads")
        }.apply {
            if (!exists()) mkdirs()
        }

        val targetFile = File(targetDir, fileName)
        tempFile.inputStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
        val label = if (externalDownloadsRoot != null) {
            "App Downloads/MEC Connect"
        } else {
            "App Local Storage"
        }
        return uri to label
    }
}
